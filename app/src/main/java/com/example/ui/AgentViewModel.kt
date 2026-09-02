package com.mtzallqmy.agentna.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtzallqmy.agentna.automation.AutomationScheduler
import com.mtzallqmy.agentna.data.model.AgentModel
import com.mtzallqmy.agentna.data.model.ApprovalModel
import com.mtzallqmy.agentna.data.model.AutomationModel
import com.mtzallqmy.agentna.data.model.ChatItem
import com.mtzallqmy.agentna.data.model.FileItemModel
import com.mtzallqmy.agentna.data.model.MessageModel
import com.mtzallqmy.agentna.data.repository.AgentRepository
import com.mtzallqmy.agentna.runtime.AgentEngine
import com.mtzallqmy.agentna.runtime.AgentEvent
import com.mtzallqmy.agentna.runtime.AgentRunResult
import com.mtzallqmy.agentna.runtime.LocalToolRegistry
import com.mtzallqmy.agentna.runtime.PendingAgentRun
import com.mtzallqmy.agentna.runtime.ProviderCatalog
import com.mtzallqmy.agentna.runtime.ProviderClient
import com.mtzallqmy.agentna.runtime.ProviderMessage
import com.mtzallqmy.agentna.runtime.ToolResult
import com.mtzallqmy.agentna.security.SecureApiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AgentRepository(application)
    private val keyStore = SecureApiKeyStore(application)
    private val localTools = LocalToolRegistry(application)
    private val providerClient = ProviderClient(keyStore)
    private val engine = AgentEngine(providerClient, localTools) { provider, fallback ->
        keyStore.getModel(provider, fallback)
    }
    private val pendingRuns = ConcurrentHashMap<String, PendingAgentRun>()
    private var messagesJob: Job? = null

    private val _language = MutableStateFlow(AppLanguage.ARABIC)
    val currentLanguage: StateFlow<AppLanguage> = _language.asStateFlow()

    val agents = repository.agents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val approvals = repository.approvals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val executionLogs = repository.executionLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val automations = repository.automations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedAgentId = MutableStateFlow(DEFAULT_AGENT_ID)
    val selectedAgentId: StateFlow<String> = _selectedAgentId.asStateFlow()
    private val _selectedConversationId = MutableStateFlow<String?>(null)
    val selectedConversationId: StateFlow<String?> = _selectedConversationId.asStateFlow()

    private val _chatItems = MutableStateFlow<List<ChatItem>>(emptyList())
    val chatItems: StateFlow<List<ChatItem>> = _chatItems.asStateFlow()
    private val _working = MutableStateFlow(false)
    val isAgentWorking: StateFlow<Boolean> = _working.asStateFlow()
    private val _workStatus = MutableStateFlow<String?>(null)
    val currentWorkStatus: StateFlow<String?> = _workStatus.asStateFlow()
    private val _runtimeStatus = MutableStateFlow("LOCAL_READY")
    val runtimeStatus: StateFlow<String> = _runtimeStatus.asStateFlow()
    private val _files = MutableStateFlow<List<FileItemModel>>(emptyList())
    val files: StateFlow<List<FileItemModel>> = _files.asStateFlow()
    private val _providerRevision = MutableStateFlow(0)
    val providerRevision: StateFlow<Int> = _providerRevision.asStateFlow()
    private val _providerTests = MutableStateFlow<Map<String, String>>(emptyMap())
    val providerTestStatus: StateFlow<Map<String, String>> = _providerTests.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedDefaultDataIfEmpty()
            repository.automations.first().filter { it.enabled }.forEach { automation ->
                val next = AutomationScheduler.scheduleNext(application, automation)
                repository.setAutomationEnabled(automation.id, true, next?.toString())
            }
            refreshFiles()
            repository.conversations.first().firstOrNull()?.let { conversation ->
                _selectedAgentId.value = conversation.agentId
                selectConversation(conversation.id)
            }
        }
    }

    fun toggleLanguage() {
        _language.value = if (_language.value == AppLanguage.ARABIC) AppLanguage.ENGLISH else AppLanguage.ARABIC
    }

    fun selectAgent(agentId: String) {
        _selectedAgentId.value = agentId
    }

    private fun selectConversation(conversationId: String) {
        _selectedConversationId.value = conversationId
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.getMessages(conversationId).collect { messages ->
                if (!_working.value && _selectedConversationId.value == conversationId) {
                    _chatItems.value = messages.map { ChatItem(id = it.id, role = it.role, text = it.content) }
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val prompt = text.trim()
        if (prompt.isBlank() || _working.value) return
        viewModelScope.launch {
            val agent = repository.getAgent(_selectedAgentId.value) ?: repository.getAgent(DEFAULT_AGENT_ID)
            if (agent == null) return@launch
            if (!keyStore.hasApiKey(agent.primaryProvider) && agent.fallbackProvider?.let(keyStore::hasApiKey) != true) {
                addChat(ChatItem(role = "assistant", text = if (_language.value == AppLanguage.ARABIC) "أضف مفتاح API لمزود الوكيل من الإعدادات أولاً." else "Add an API key for this agent's provider in Settings first."))
                return@launch
            }
            val conversationId = ensureConversation(agent, prompt)
            val userMessage = MessageModel(conversationId = conversationId, role = "user", content = prompt)
            repository.saveMessage(userMessage)
            addChat(ChatItem(id = userMessage.id, role = "user", text = prompt))
            _working.value = true
            _runtimeStatus.value = "RUNNING"
            _workStatus.value = if (containsArabic(prompt)) "يعمل Agentna محلياً…" else "Agentna is running locally…"
            repository.updateAgentState(agent.id, "running", prompt)
            val history = repository.getMessagesSnapshot(conversationId)
            val result = engine.run(conversationId, prompt, agent, history) { event ->
                handleEngineEvent(event, conversationId, agent.id)
            }
            handleRunResult(result, agent.id, conversationId)
        }
    }

    private suspend fun ensureConversation(agent: AgentModel, prompt: String): String {
        val current = _selectedConversationId.value
        val existing = repository.conversations.first().firstOrNull { it.id == current && it.agentId == agent.id }
        if (existing != null) return existing.id
        val id = repository.createConversation(prompt.take(52).ifBlank { "Agentna" }, agent.id)
        selectConversation(id)
        return id
    }

    private suspend fun handleRunResult(result: AgentRunResult, agentId: String, conversationId: String) {
        when (result) {
            is AgentRunResult.Completed -> {
                val message = MessageModel(conversationId = conversationId, role = "assistant", content = result.content)
                repository.saveMessage(message)
                if (_selectedConversationId.value == conversationId) addChat(ChatItem(id = message.id, role = "assistant", text = result.content))
                repository.updateAgentState(agentId, "idle")
                repository.logExecutionEvent("local", conversationId, agentId, "info", "run.completed", "Local agent run completed")
                finishRun()
            }
            is AgentRunResult.AwaitingApproval -> {
                repository.saveApproval(result.approval)
                pendingRuns[result.approval.id] = result.pendingRun
                if (_selectedConversationId.value == conversationId) addChat(ChatItem(role = "approval", approval = result.approval))
                repository.updateAgentState(agentId, "awaiting_approval", result.approval.reason, lastRunId = result.approval.runId)
                repository.logExecutionEvent(result.approval.runId, conversationId, agentId, "warn", "approval.requested", result.approval.reason, result.approval.argumentsJson)
                _working.value = false
                _runtimeStatus.value = "WAITING_APPROVAL"
                _workStatus.value = null
            }
            is AgentRunResult.Failed -> {
                val text = if (_language.value == AppLanguage.ARABIC) "تعذر إكمال المهمة: ${result.message}" else "Task failed: ${result.message}"
                val message = MessageModel(conversationId = conversationId, role = "assistant", content = text)
                repository.saveMessage(message)
                if (_selectedConversationId.value == conversationId) addChat(ChatItem(id = message.id, role = "assistant", text = text))
                repository.updateAgentState(agentId, "error", result.message)
                repository.logExecutionEvent("local", conversationId, agentId, "error", "run.failed", result.message)
                finishRun(error = true)
            }
        }
        refreshFiles()
    }

    private suspend fun handleEngineEvent(event: AgentEvent, conversationId: String, agentId: String) {
        when (event) {
            is AgentEvent.Step -> _workStatus.value = if (_language.value == AppLanguage.ARABIC) "خطوة ${event.index} من ${event.max}…" else "Step ${event.index} of ${event.max}…"
            is AgentEvent.ToolStarted -> {
                _workStatus.value = "${event.name}…"
                if (_selectedConversationId.value == conversationId) addChat(ChatItem(role = "tool", toolName = event.name, text = event.arguments, toolStatus = "running"))
                repository.logExecutionEvent("local", conversationId, agentId, "tool", "tool.started", event.name, event.arguments)
            }
            is AgentEvent.ToolFinished -> {
                if (_selectedConversationId.value == conversationId) addChat(ChatItem(role = "tool", toolName = event.name, text = event.result.take(1_500), toolStatus = if (event.success) "success" else "failed"))
                repository.logExecutionEvent("local", conversationId, agentId, if (event.success) "tool" else "warn", "tool.completed", event.name, event.result.take(4_000))
            }
        }
    }

    fun approveAction(approval: ApprovalModel) = resolveApproval(approval, true)
    fun rejectAction(approval: ApprovalModel) = resolveApproval(approval, false)

    private fun resolveApproval(approval: ApprovalModel, approved: Boolean) {
        viewModelScope.launch {
            repository.resolveApproval(approval.id, if (approved) "approved" else "rejected")
            val pending = pendingRuns.remove(approval.id)
            val conversation = repository.conversations.first().firstOrNull { it.id == approval.conversationId }
            val agent = repository.getAgent(conversation?.agentId ?: _selectedAgentId.value) ?: repository.getAgent(DEFAULT_AGENT_ID) ?: return@launch
            _working.value = true
            _runtimeStatus.value = "RUNNING"
            val result = if (pending != null) {
                engine.resume(pending, approved) { event -> handleEngineEvent(event, approval.conversationId, agent.id) }
            } else if (approved) {
                val persisted = repository.getApproval(approval.id) ?: approval
                if (!isToolAllowed(agent, persisted.toolName)) {
                    AgentRunResult.Failed("Tool permission changed after approval request; action was blocked")
                } else when (val toolResult = engine.executeApprovedTool(persisted.toolName, persisted.argumentsJson)) {
                    is ToolResult.Success -> engine.run(
                        persisted.conversationId,
                        "Approved tool ${persisted.toolName} completed with this real result: ${toolResult.output}. Continue the original task: ${persisted.sourcePrompt}",
                        agent,
                        repository.getMessagesSnapshot(persisted.conversationId)
                    ) { event -> handleEngineEvent(event, persisted.conversationId, agent.id) }
                    is ToolResult.Failure -> AgentRunResult.Failed(toolResult.error)
                    is ToolResult.RequiresApproval -> AgentRunResult.Failed("Approval could not be applied safely")
                }
            } else {
                engine.run(
                    approval.conversationId,
                    "The user rejected ${approval.toolName}. Continue safely without that action. Original task: ${approval.sourcePrompt}",
                    agent,
                    repository.getMessagesSnapshot(approval.conversationId)
                ) { event -> handleEngineEvent(event, approval.conversationId, agent.id) }
            }
            handleRunResult(result, agent.id, approval.conversationId)
        }
    }

    private fun isToolAllowed(agent: AgentModel, toolName: String): Boolean = when {
        toolName.startsWith("workspace.") -> agent.filesystemPermission
        toolName == "web.fetch" || toolName == "device.open_url" -> agent.networkPermission
        toolName == "device.info" -> true
        else -> false
    }

    fun refreshFiles() {
        viewModelScope.launch {
            _files.value = withContext(Dispatchers.IO) {
                localTools.listFiles().map { file ->
                    FileItemModel(
                        id = file.absolutePath,
                        name = file.name,
                        path = file.relativeTo(getApplication<Application>().filesDir).invariantSeparatorsPath,
                        mimeType = guessMime(file.name),
                        size = file.length(),
                        contentPreview = localTools.readFilePreview(file)
                    )
                }
            }
        }
    }

    fun runAutomationNow(id: String) {
        viewModelScope.launch {
            val automation = repository.getAutomation(id) ?: return@launch
            val agent = repository.getAgent(automation.agentId) ?: return@launch
            if (!agent.automationPermission) return@launch
            AutomationScheduler.enqueueNow(getApplication(), id)
            repository.logExecutionEvent("automation:$id", "background", agent.id, "info", "automation.enqueued", "Manual automation run enqueued")
        }
    }

    fun toggleAutomation(id: String) {
        viewModelScope.launch {
            val automation = repository.getAutomation(id) ?: return@launch
            val enabled = !automation.enabled
            val updated = automation.copy(enabled = enabled)
            val next = if (enabled) AutomationScheduler.scheduleNext(getApplication(), updated) else {
                AutomationScheduler.cancel(getApplication(), id); null
            }
            repository.saveAutomation(updated.copy(nextRunAt = next?.toString(), lastStatus = if (enabled) "scheduled" else "disabled"))
        }
    }

    fun saveDailyAutomation(name: String, prompt: String, agentId: String, hour: Int, minute: Int) {
        if (name.isBlank() || prompt.isBlank() || hour !in 0..23 || minute !in 0..59) return
        viewModelScope.launch {
            val agent = repository.getAgent(agentId) ?: return@launch
            if (!agent.automationPermission) return@launch
            val automation = AutomationModel(
                name = name.trim(),
                description = "Daily on-device automation. Android may defer execution for battery/system constraints.",
                cronExpression = "$minute $hour * * *",
                agentId = agentId,
                prompt = prompt.trim(),
                enabled = true
            )
            val next = AutomationScheduler.scheduleNext(getApplication(), automation)
            repository.saveAutomation(automation.copy(nextRunAt = next?.toString(), lastStatus = if (next == null) "invalid_schedule" else "scheduled"))
        }
    }

    fun deleteAutomation(id: String) {
        viewModelScope.launch {
            AutomationScheduler.cancel(getApplication(), id)
            repository.deleteAutomation(id)
        }
    }

    fun providerHasKey(provider: String) = keyStore.hasApiKey(provider)
    fun providerMaskedKey(provider: String) = keyStore.maskedApiKey(provider)
    fun providerModel(provider: String): String {
        val definition = ProviderCatalog.definition(provider)
        return keyStore.getModel(provider, definition.defaultModel)
    }
    fun saveProvider(provider: String, apiKey: String, model: String) {
        if (apiKey.isNotBlank() && !apiKey.contains('•')) keyStore.putApiKey(provider, apiKey.trim())
        if (model.isNotBlank()) keyStore.setModel(provider, model.trim())
        _providerRevision.value += 1
    }
    fun testProvider(provider: String) {
        viewModelScope.launch {
            _providerTests.value = _providerTests.value + (provider to "testing")
            val status = runCatching {
                providerClient.complete(provider, providerModel(provider), "Return exactly OK.", listOf(ProviderMessage("user", "Connectivity check")))
                "ok"
            }.getOrElse { "error:${it.message.orEmpty().take(140)}" }
            _providerTests.value = _providerTests.value + (provider to status)
        }
    }

    fun resetDatabase() {
        viewModelScope.launch {
            automations.value.forEach { AutomationScheduler.cancel(getApplication(), it.id) }
            repository.clearAndReseedDatabase()
            _selectedConversationId.value = null
            _chatItems.value = emptyList()
            repository.conversations.first().firstOrNull()?.let { selectConversation(it.id) }
            refreshFiles()
        }
    }

    private fun finishRun(error: Boolean = false) {
        _working.value = false
        _runtimeStatus.value = if (error) "ERROR" else "LOCAL_READY"
        _workStatus.value = null
    }
    private fun addChat(item: ChatItem) { _chatItems.value = _chatItems.value + item }
    private fun containsArabic(text: String) = text.any { it in '\u0600'..'\u06FF' }
    private fun guessMime(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "md" -> "text/markdown"; "json" -> "application/json"; "kt" -> "text/x-kotlin"; "py" -> "text/x-python"; "js" -> "text/javascript"; "html" -> "text/html"; else -> "text/plain"
    }

    companion object { private const val DEFAULT_AGENT_ID = "agent-default-1" }
}
