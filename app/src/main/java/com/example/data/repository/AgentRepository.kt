package com.mtzallqmy.agentna.data.repository

import android.content.Context
import com.mtzallqmy.agentna.data.local.AgentDatabase
import com.mtzallqmy.agentna.data.local.AgentEntity
import com.mtzallqmy.agentna.data.local.AgentStateEntity
import com.mtzallqmy.agentna.data.local.ApprovalEntity
import com.mtzallqmy.agentna.data.local.ConversationEntity
import com.mtzallqmy.agentna.data.local.ExecutionLogEntity
import com.mtzallqmy.agentna.data.local.MessageEntity
import com.mtzallqmy.agentna.data.model.AgentModel
import com.mtzallqmy.agentna.data.model.ApprovalModel
import com.mtzallqmy.agentna.data.model.ConversationModel
import com.mtzallqmy.agentna.data.model.MessageModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/** Local-only repository. Room is the source of truth; no gateway or application server is contacted. */
class AgentRepository(context: Context) {
    val database: AgentDatabase = AgentDatabase.getDatabase(context)
    private val agentDao = database.agentDao()
    private val convDao = database.conversationDao()
    private val msgDao = database.messageDao()
    private val approvalDao = database.approvalDao()
    private val logDao = database.executionLogDao()
    private val stateDao = database.agentStateDao()

    val agents: Flow<List<AgentModel>> = agentDao.getAllAgents().map { it.map { entity -> entity.toModel() } }
    val conversations: Flow<List<ConversationModel>> = convDao.getAllConversations().map { list ->
        list.map { ConversationModel(it.id, it.title, it.agentId, it.createdAt) }
    }
    val approvals: Flow<List<ApprovalModel>> = approvalDao.getAllApprovals().map { list -> list.map(ApprovalEntity::toModel) }
    val executionLogs: Flow<List<ExecutionLogEntity>> = logDao.getAllLogs()

    fun getMessages(convId: String): Flow<List<MessageModel>> =
        msgDao.getMessagesForConversation(convId).map { it.map { entity -> entity.toModel() } }

    suspend fun getMessagesSnapshot(convId: String): List<MessageModel> = withContext(Dispatchers.IO) {
        msgDao.getMessagesSnapshot(convId).map { entity -> entity.toModel() }
    }

    suspend fun getAgent(id: String): AgentModel? = withContext(Dispatchers.IO) {
        agentDao.getAgentById(id)?.toModel()
    }

    suspend fun getApproval(id: String): ApprovalModel? = withContext(Dispatchers.IO) {
        approvalDao.getApprovalById(id)?.toModel()
    }

    suspend fun seedDefaultDataIfEmpty() = withContext(Dispatchers.IO) {
        if (agentDao.getAgentById(DEFAULT_AGENT_ID) != null) return@withContext
        val defaultAgent = AgentModel(
            id = DEFAULT_AGENT_ID,
            name = "Agentna Executive",
            description = "وكيل محلي منسق للأبحاث والويب وإدارة ملفات مساحة العمل مع موافقات بشرية.",
            systemPrompt = "You are a reliable executive agent. Plan carefully, use only the provided local Android tools, verify tool results, and answer in the user's language.",
            primaryProvider = "gemini",
            primaryModel = "gemini-3.7-flash",
            maxSteps = 12,
            computerPermission = false,
            shellPermission = false,
            filesystemPermission = true,
            networkPermission = true,
            automationPermission = true,
            subagentPermission = false
        )
        val coder = AgentModel(
            id = "agent-coder-2",
            name = "Code Architect",
            description = "متخصص في تحليل وتوليد ومراجعة الملفات البرمجية داخل مساحة التطبيق الآمنة.",
            icon = "terminal",
            systemPrompt = "You are a principal software engineer. You can inspect and write source files in the app workspace. Never claim to execute a shell or compiler because no arbitrary shell tool exists on Android.",
            primaryProvider = "openai",
            primaryModel = "gpt-5.6",
            temperature = 0.2f,
            maxSteps = 14,
            computerPermission = false,
            shellPermission = false,
            filesystemPermission = true,
            networkPermission = true,
            automationPermission = true,
            subagentPermission = false
        )
        val researcher = AgentModel(
            id = "agent-research-3",
            name = "Research Analyst",
            description = "باحث يجلب المصادر العامة عبر HTTPS ويجمع النتائج في تقارير محلية.",
            icon = "search",
            systemPrompt = "You are a research analyst. Fetch public web sources when needed, distinguish evidence from inference, and save useful reports to the local workspace.",
            primaryProvider = "gemini",
            primaryModel = "gemini-3.7-flash",
            temperature = 0.4f,
            maxSteps = 14,
            computerPermission = false,
            shellPermission = false,
            filesystemPermission = true,
            networkPermission = true,
            automationPermission = true,
            subagentPermission = false
        )
        agentDao.insertAgents(listOf(defaultAgent, coder, researcher).map(AgentModel::toEntity))

        val now = nowString()
        val conversation = ConversationEntity(
            id = "conv-welcome-1",
            title = "مرحباً بك في Agentna",
            agentId = defaultAgent.id,
            createdAt = now
        )
        convDao.insertConversation(conversation)
        msgDao.insertMessage(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversation.id,
                role = "assistant",
                content = "مرحباً بك في **Agentna**. محرك الوكيل والأدوات والسجل والموافقات تعمل محلياً على هاتفك. أضف مفتاح مزود واحد على الأقل من الإعدادات؛ بعدها يمكنني البحث عبر الويب العام وقراءة وكتابة ملفات مساحة العمل دون أي خادم وسيط.",
                createdAt = now
            )
        )
        logDao.insertLog(
            ExecutionLogEntity(
                id = UUID.randomUUID().toString(),
                runId = "bootstrap",
                conversationId = conversation.id,
                agentId = defaultAgent.id,
                level = "info",
                event = "runtime.ready",
                message = "Agentna on-device runtime initialized",
                details = "Room database + Android Keystore + direct provider APIs"
            )
        )
    }

    suspend fun clearAndReseedDatabase() = withContext(Dispatchers.IO) {
        agentDao.deleteAllAgents()
        convDao.deleteAllConversations()
        msgDao.deleteAllMessages()
        approvalDao.deleteAllApprovals()
        logDao.deleteAllLogs()
        seedDefaultDataIfEmpty()
    }

    suspend fun saveAgent(agent: AgentModel) = withContext(Dispatchers.IO) { agentDao.insertAgent(agent.toEntity()) }
    suspend fun deleteAgent(id: String) = withContext(Dispatchers.IO) { agentDao.deleteAgent(id) }

    suspend fun createConversation(title: String, agentId: String): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        convDao.insertConversation(ConversationEntity(id, title, agentId, nowString()))
        id
    }

    suspend fun saveMessage(msg: MessageModel) = withContext(Dispatchers.IO) {
        msgDao.insertMessage(MessageEntity(msg.id, msg.conversationId, msg.role, msg.content, msg.createdAt.ifBlank(::nowString)))
    }

    suspend fun saveApproval(approval: ApprovalModel) = withContext(Dispatchers.IO) {
        approvalDao.insertApproval(
            ApprovalEntity(
                id = approval.id,
                runId = approval.runId,
                conversationId = approval.conversationId,
                toolName = approval.toolName,
                argumentsJson = approval.argumentsJson,
                sourcePrompt = approval.sourcePrompt,
                reason = approval.reason,
                riskLevel = approval.riskLevel,
                status = approval.status,
                createdAt = approval.createdAt.ifBlank(::nowString)
            )
        )
    }

    suspend fun resolveApproval(approvalId: String, decision: String) = withContext(Dispatchers.IO) {
        approvalDao.updateApprovalStatus(approvalId, decision)
    }

    fun getLogsForConversation(convId: String): Flow<List<ExecutionLogEntity>> = logDao.getLogsForConversation(convId)

    suspend fun logExecutionEvent(
        runId: String,
        conversationId: String,
        agentId: String,
        level: String,
        event: String,
        message: String,
        details: String = ""
    ) = withContext(Dispatchers.IO) {
        logDao.insertLog(
            ExecutionLogEntity(
                id = UUID.randomUUID().toString(),
                runId = runId,
                conversationId = conversationId,
                agentId = agentId,
                level = level,
                event = event,
                message = message,
                details = details
            )
        )
    }

    suspend fun updateAgentState(
        agentId: String,
        status: String,
        currentTask: String = "",
        activeUrl: String = "",
        lastRunId: String = ""
    ) = withContext(Dispatchers.IO) {
        stateDao.insertOrUpdateState(AgentStateEntity(agentId, status, currentTask, activeUrl, lastRunId))
    }

    fun getAgentState(agentId: String): Flow<AgentStateEntity?> = stateDao.getState(agentId)

    private fun AgentEntity.toModel() = AgentModel(
        id, name, description, icon, systemPrompt, primaryProvider, primaryModel,
        fallbackProvider = fallbackProvider, fallbackModel = fallbackModel,
        temperature = temperature, maxSteps = maxSteps, approvalPolicy = approvalPolicy,
        computerPermission = computerPermission, shellPermission = shellPermission,
        filesystemPermission = filesystemPermission, networkPermission = networkPermission,
        automationPermission = automationPermission, subagentPermission = subagentPermission
    )

    private fun AgentModel.toEntity() = AgentEntity(
        id, name, description, icon, systemPrompt, primaryProvider, primaryModel,
        fallbackProvider, fallbackModel, temperature, maxSteps, approvalPolicy,
        computerPermission, shellPermission, filesystemPermission, networkPermission,
        automationPermission, subagentPermission
    )

    private fun MessageEntity.toModel() = MessageModel(id, conversationId, role, content, createdAt)

    private fun ApprovalEntity.toModel() = ApprovalModel(
        id = id,
        runId = runId,
        conversationId = conversationId,
        toolName = toolName,
        argumentsJson = argumentsJson,
        sourcePrompt = sourcePrompt,
        reason = reason,
        riskLevel = riskLevel,
        status = status,
        createdAt = createdAt
    )

    private fun nowString(): String = System.currentTimeMillis().toString()

    companion object { private const val DEFAULT_AGENT_ID = "agent-default-1" }
}
