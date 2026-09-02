package com.mtzallqmy.agentna.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtzallqmy.agentna.data.model.AgentModel
import com.mtzallqmy.agentna.data.model.ApprovalModel
import com.mtzallqmy.agentna.data.model.AutomationModel
import com.mtzallqmy.agentna.data.model.ChatItem
import com.mtzallqmy.agentna.data.model.ComputerSessionModel
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
import com.mtzallqmy.agentna.runtime.ToolResult
import com.mtzallqmy.agentna.security.SecureApiKeyStore
import com.mtzallqmy.agentna.ui.localization.AppLanguage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    val repository = AgentRepository(application)
    private val keyStore = SecureApiKeyStore(application)
    private val localTools = LocalToolRegistry(application)
    private val providerClient = ProviderClient(keyStore)
    private val engine = AgentEngine(providerClient, localTools) { provider, fallback ->
        keyStore.getModel(provider, fallback)
    }

    private val pendingRuns = ConcurrentHashMap<String, PendingAgentRun>()
    private var messagesJob: Job? = null

    private val _currentLanguage = MutableStateFlow(AppLanguage.ARABIC)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    fun toggleLanguage() {
        _currentLanguage.value = if (_currentLanguage.value == AppLanguage.ARABIC) AppLanguage.ENGLISH else AppLanguage.ARABIC
    }

    fun setLanguage(language: AppLanguage) { _currentLanguage.value = language }

    val agents: StateFlow<List<AgentModel>> = repository.agents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val conversations = repository.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val approvals: StateFlow<List<ApprovalModel>> = repository.approvals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val executionLogs = repository.executionLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedConversationId = MutableStateFlow<String?>(null)
    val selectedConversationId: StateFlow<String?> = _selectedConversationId.asStateFlow()
    private val _selectedAgentId = MutableStateFlow(DEFAULT_AGENT_ID)
    val selectedAgentId: StateFlow<String> = _selectedAgentId.asStateFlow()

    private val _chatItems = MutableStateFlow<List<ChatItem>>(emptyList())
    val chatItems: StateFlow<List<ChatItem>> = _chatItems.asStateFlow()
    private val _isAgentWorking = MutableStateFlow(false)
    val isAgentWorking: StateFlow<Boolean> = _isAgentWorking.asStateFlow()
    private val _currentWorkStatus = MutableStateFlow<String?>(null)
    val currentWorkStatus: StateFlow<String?> = _currentWorkStatus.asStateFlow()
    private val _runtimeStatus = MutableStateFlow("LOCAL_READY")
    val runtimeStatus: StateFlow<String> = _runtimeStatus.asStateFlow()

    private val _providerRevision = MutableStateFlow(0)
    val providerRevision: StateFlow<Int> = _providerRevision.asStateFlow()
    private val _providerTestStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val providerTestStatus: StateFlow<Map<String, String>> = _providerTestStatus.asStateFlow()

    private val _computerSession = MutableStateFlow(ComputerSessionModel())
    val computerSession: StateFlow<ComputerSessionModel> = _computerSession.asStateFlow()

    private val _files = MutableStateFlow<List<FileItemModel>>(emptyList())
    val files: StateFlow<List<FileItemModel>> = _files.asStateFlow()

    private val _automations = MutableStateFlow(
        listOf(
            AutomationModel(
                id = "auto-briefing",
                name = "Morning research brief",
                description = "تشغيل محلي عند الطلب؛ الجدولة الدورية ستستخدم WorkManager.",
                agentId = "agent-research-3",
                prompt = "اجمع أهم المستجدات من مصادر ويب عامة واكتب تقريراً موجزاً في workspace/reports/daily-brief.md",
                enabled = false
            )
        )
    )
    val automations: StateFlow<List<AutomationModel>> = _automations.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedDefaultDataIfEmpty()
            refreshFiles()
            val firstConversation = repository.conversations.first().firstOrNull()
            if (firstConversation != null) {
                _selectedAgentId.value = firstConversation.agentId
                selectConversation(firstConversation.id)
            }
        }
    }

    fun selectConversation(convId: String) {
        _selectedConversationId.value = convId
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.getMessages(convId).collect { messages ->
                if (!_isAgentWorking.value) {
                    _chatItems.value = messages.map { ChatItem(id = it.id, role = it.role, text = it.content) }
                }
            }
        }
    }

    fun createNewConversation(title: String, agentId: String = _selectedAgentId.value) {
        viewModelScope.launch {
            val id = repository.createConversation(title, agentId)
            _selectedAgentId.value = agentId
            selectConversation(id)
        }
    }

    fun selectAgent(agentId: String) { _selectedAgentId.value = agentId }

    fun sendMessage(userText: String) {
        val prompt = userText.trim()
        if (prompt.isBlank() || _isAgentWorking.value) return
        viewModelScope.launch {
            val agent = repository.getAgent(_selectedAgentId.value) ?: agents.value.firstOrNull()
            if (agent == null) {
                showFailure("No local agent is configured")
                return@launch
            }
            val conversationId = _selectedConversationId.value ?: repository.createConversation(
                title = prompt.take(48).ifBlank { "New task" },
                agentId = agent.id
            ).also { _selectedConversationId.value = it }

            val userMessage = MessageModel(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = "user",
                content = prompt
            )
            repository.saveMessage(userMessage)
            addChatItem(ChatItem(id = userMessage.id, role = "user", text = prompt))

            _isAgentWorking.value = true
            _runtimeStatus.value = "RUNNING"
            _currentWorkStatus.value = if (containsArabic(prompt)) "بدء محرك Agentna المحلي…" else "Starting Agentna local runtime…"
            repository.updateAgentState(agent.id, "running", prompt)
            val history = repository.getMessagesSnapshot(conversationId)
            val result = engine.run(conversationId, prompt, agent, history, ::handleEngineEvent)
            handleRunResult(result, agent.id, conversationId)
        }
    }

    private suspend fun handleRunResult(result: AgentRunResult, agentId: String, conversationId: String) {
        when (result) {
            is AgentRunResult.Completed -> {
                val message = MessageModel(conversationId = conversationId, role = "assistant", content = result.content)
                repository.saveMessage(message)
                addChatItem(ChatItem(id = message.id, role = "assistant", text = result.content))
                repository.updateAgentState(agentId, "idle")
                repository.logExecutionEvent("local", conversationId, agentId, "info", "run.completed", "Local agent run completed")
                finishRun()
            }
            is AgentRunResult.AwaitingApproval -> {
                repository.saveApproval(result.approval)
                pendingRuns[result.approval.id] = result.pendingRun
                addChatItem(ChatItem(role = "approval", approval = result.approval))
                repository.updateAgentState(agentId, "awaiting_approval", result.approval.reason, lastRunId = result.approval.runId)
                repository.logExecutionEvent(result.approval.runId, conversationId, agentId, "warn", "approval.requested", result.approval.reason, result.approval.argumentsJson)
                _isAgentWorking.value = false
                _runtimeStatus.value = "WAITING_APPROVAL"
                _currentWorkStatus.value = null
            }
            is AgentRunResult.Failed -> {
                val text = if (_currentLanguage.value == AppLanguage.ARABIC) "تعذر إكمال المهمة: ${result.message}" else "Task failed: ${result.message}"
                val message = MessageModel(conversationId = conversationId, role = "assistant", content = text)
                repository.saveMessage(message)
                addChatItem(ChatItem(id = message.id, role = "assistant", text = text))
                repository.updateAgentState(agentId, "error", result.message)
                repository.logExecutionEvent("local", conversationId, agentId, "error", "run.failed", result.message)
                finishRun(error = true)
            }
        }
        refreshFiles()
    }

    private suspend fun handleEngineEvent(event: AgentEvent) {
        val convId = _selectedConversationId.value.orEmpty()
        val agentId = _selectedAgentId.value
        when (event) {
            is AgentEvent.Step -> _currentWorkStatus.value = if (_currentLanguage.value == AppLanguage.ARABIC) {
                "خطوة ${event.index} من ${event.max}…"
            } else "Step ${event.index} of ${event.max}…"
            is AgentEvent.ToolStarted -> {
                _currentWorkStatus.value = "${event.name}…"
                addChatItem(ChatItem(role = "tool", toolName = event.name, text = event.arguments, toolStatus = "running"))
                repository.logExecutionEvent("local", convId, agentId, "tool", "tool.started", event.name, event.arguments)
            }
            is AgentEvent.ToolFinished -> {
                addChatItem(ChatItem(role = "tool", toolName = event.name, text = event.result.take(1_500), toolStatus = if (event.success) "success" else "failed"))
                repository.logExecutionEvent("local", convId, agentId, if (event.success) "tool" else "warn", "tool.completed", event.name, event.result.take(4_000))
            }
        }
    }

    fun approveAction(approval: ApprovalModel) = resolveApproval(approval, approved = true)
    fun rejectAction(approval: ApprovalModel) = resolveApproval(approval, approved = false)

    private fun resolveApproval(approval: ApprovalModel, approved: Boolean) {
        viewModelScope.launch {
            repository.resolveApproval(approval.id, if (approved) "approved" else "rejected")
            val pending = pendingRuns.remove(approval.id)
            val agent = repository.getAgent(_selectedAgentId.value) ?: repository.getAgent(DEFAULT_AGENT_ID)
            if (agent == null) return@launch
            _isAgentWorking.value = true
            _runtimeStatus.value = "RUNNING"
            _currentWorkStatus.value = if (approved) "Applying approved local action…" else "Continuing after rejection…"

            val result = if (pending != null) {
                engine.resume(pending, approved, ::handleEngineEvent)
            } else if (approved) {
                // Process death-safe fallback: execute the persisted approved tool, then continue with a truthful context note.
                val persisted = repository.getApproval(approval.id) ?: approval
                when (val toolResult = engine.executeApprovedTool(persisted.toolName, persisted.argumentsJson)) {
                    is ToolResult.Success -> {
                        val history = repository.getMessagesSnapshot(persisted.conversationId)
                        engine.run(
                            persisted.conversationId,
                            "Approved tool ${persisted.toolName} completed with this real result: ${toolResult.output}. Continue the original task: ${persisted.sourcePrompt}",
                            agent,
                            history,
                            ::handleEngineEvent
                        )
                    }
                    is ToolResult.Failure -> AgentRunResult.Failed(toolResult.error)
                    is ToolResult.RequiresApproval -> AgentRunResult.Failed("Approval could not be applied")
                }
            } else {
                val history = repository.getMessagesSnapshot(approval.conversationId)
                engine.run(
                    approval.conversationId,
                    "The user rejected the requested action ${approval.toolName}. Continue safely without it. Original task: ${approval.sourcePrompt}",
                    agent,
                    history,
                    ::handleEngineEvent
                )
            }
            handleRunResult(result, agent.id, approval.conversationId)
        }
    }

    fun createAgent(agent: AgentModel) { viewModelScope.launch { repository.saveAgent(agent) } }
    fun deleteAgent(id: String) { if (id != DEFAULT_AGENT_ID) viewModelScope.launch { repository.deleteAgent(id) } }

    fun saveProvider(provider: String, apiKey: String, model: String) {
        if (apiKey.isNotBlank() && !apiKey.contains('•')) keyStore.putApiKey(provider, apiKey)
        keyStore.setModel(provider, model)
        _providerRevision.value += 1
    }

    fun removeProviderKey(provider: String) {
        keyStore.removeApiKey(provider)
        _providerRevision.value += 1
    }

    fun providerHasKey(provider: String): Boolean = keyStore.hasApiKey(provider)
    fun providerMaskedKey(provider: String): String = keyStore.maskedApiKey(provider)
    fun providerModel(provider: String): String {
        val definition = ProviderCatalog.definition(provider)
        return keyStore.getModel(provider, definition.defaultModel)
    }

    fun testProvider(provider: String) {
        viewModelScope.launch {
            _providerTestStatus.value = _providerTestStatus.value + (provider to "testing")
            val definition = ProviderCatalog.definition(provider)
            val model = providerModel(provider)
            val status = runCatching {
                providerClient.complete(
                    provider = provider,
                    model = model,
                    systemPrompt = "Return exactly the word OK.",
                    messages = listOf(com.mtzallqmy.agentna.runtime.ProviderMessage("user", "Connectivity check"))
                )
                "ok"
            }.getOrElse { "error:${it.message.orEmpty().take(160)}" }
            _providerTestStatus.value = _providerTestStatus.value + (provider to status)
        }
    }

    fun refreshFiles() {
        _files.value = localTools.listFiles().map { file ->
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

    fun runAutomationNow(id: String) {
        _automations.value.firstOrNull { it.id == id }?.let { automation ->
            _selectedAgentId.value = automation.agentId
            sendMessage(automation.prompt)
        }
    }

    fun toggleAutomation(id: String) {
        _automations.value = _automations.value.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
    }

    fun updateComputerUrl(url: String) {
        _computerSession.value = _computerSession.value.copy(activeUrl = url, lastAction = "URL staged for explicit open approval")
    }
    fun restartComputer() { _computerSession.value = ComputerSessionModel(lastAction = "Local Android runtime refreshed") }
    fun stopComputer() { _computerSession.value = _computerSession.value.copy(status = "idle", lastAction = "No background device control is running") }

    fun resetDatabase() {
        viewModelScope.launch {
            repository.clearAndReseedDatabase()
            _selectedConversationId.value = null
            _chatItems.value = emptyList()
            refreshFiles()
        }
    }

    private fun finishRun(error: Boolean = false) {
        _isAgentWorking.value = false
        _runtimeStatus.value = if (error) "ERROR" else "LOCAL_READY"
        _currentWorkStatus.value = null
    }

    private fun showFailure(text: String) {
        addChatItem(ChatItem(role = "assistant", text = text))
        finishRun(error = true)
    }

    private fun addChatItem(item: ChatItem) { _chatItems.value = _chatItems.value + item }
    private fun containsArabic(text: String) = text.any { it in '\u0600'..'\u06FF' }
    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "md" -> "text/markdown"; "json" -> "application/json"; "kt" -> "text/x-kotlin"; "py" -> "text/x-python"; "js" -> "text/javascript"; "html" -> "text/html"; else -> "text/plain"
    }

    companion object { private const val DEFAULT_AGENT_ID = "agent-default-1" }
}
