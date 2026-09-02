package com.mtzallqmy.agentna.data.repository

import android.content.Context
import com.mtzallqmy.agentna.data.local.AgentDatabase
import com.mtzallqmy.agentna.data.local.AgentEntity
import com.mtzallqmy.agentna.data.local.AgentStateEntity
import com.mtzallqmy.agentna.data.local.ApprovalEntity
import com.mtzallqmy.agentna.data.local.AutomationEntity
import com.mtzallqmy.agentna.data.local.ConversationEntity
import com.mtzallqmy.agentna.data.local.ExecutionLogEntity
import com.mtzallqmy.agentna.data.local.MessageEntity
import com.mtzallqmy.agentna.data.model.AgentModel
import com.mtzallqmy.agentna.data.model.ApprovalModel
import com.mtzallqmy.agentna.data.model.AutomationModel
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
    private val automationDao = database.automationDao()

    val agents: Flow<List<AgentModel>> = agentDao.getAllAgents().map { it.map { entity -> entity.toModel() } }
    val conversations: Flow<List<ConversationModel>> = convDao.getAllConversations().map { list -> list.map { ConversationModel(it.id, it.title, it.agentId, it.createdAt) } }
    val approvals: Flow<List<ApprovalModel>> = approvalDao.getAllApprovals().map { list -> list.map { it.toModel() } }
    val executionLogs: Flow<List<ExecutionLogEntity>> = logDao.getAllLogs()
    val automations: Flow<List<AutomationModel>> = automationDao.getAllAutomations().map { list -> list.map { it.toModel() } }

    fun getMessages(convId: String): Flow<List<MessageModel>> = msgDao.getMessagesForConversation(convId).map { it.map { entity -> entity.toModel() } }
    suspend fun getMessagesSnapshot(convId: String): List<MessageModel> = withContext(Dispatchers.IO) { msgDao.getMessagesSnapshot(convId).map { it.toModel() } }
    suspend fun getAgent(id: String): AgentModel? = withContext(Dispatchers.IO) { agentDao.getAgentById(id)?.toModel() }
    suspend fun getApproval(id: String): ApprovalModel? = withContext(Dispatchers.IO) { approvalDao.getApprovalById(id)?.toModel() }

    suspend fun seedDefaultDataIfEmpty() = withContext(Dispatchers.IO) {
        if (agentDao.getAgentById(DEFAULT_AGENT_ID) == null) {
            val defaultAgent = AgentModel(
                id = DEFAULT_AGENT_ID, name = "Agentna Executive",
                description = "وكيل محلي منسق للأبحاث والويب وإدارة ملفات مساحة العمل مع موافقات بشرية.",
                systemPrompt = "You are a reliable executive agent. Plan carefully, use only the provided local Android tools, verify tool results, and answer in the user's language.",
                primaryProvider = "gemini", primaryModel = "gemini-3.7-flash", maxSteps = 12,
                computerPermission = false, shellPermission = false, filesystemPermission = true,
                networkPermission = true, automationPermission = true, subagentPermission = false
            )
            val coder = AgentModel(
                id = "agent-coder-2", name = "Code Architect",
                description = "متخصص في تحليل وتوليد ومراجعة الملفات البرمجية داخل مساحة التطبيق الآمنة.", icon = "terminal",
                systemPrompt = "You are a principal software engineer. You can inspect and write source files in the app workspace. Never claim to execute a shell or compiler because no arbitrary shell tool exists on Android.",
                primaryProvider = "openai", primaryModel = "gpt-5.6", temperature = 0.2f, maxSteps = 14,
                computerPermission = false, shellPermission = false, filesystemPermission = true,
                networkPermission = true, automationPermission = true, subagentPermission = false
            )
            val researcher = AgentModel(
                id = RESEARCH_AGENT_ID, name = "Research Analyst",
                description = "باحث يجلب المصادر العامة عبر HTTPS ويجمع النتائج في تقارير محلية.", icon = "search",
                systemPrompt = "You are a research analyst. Fetch public web sources when needed, distinguish evidence from inference, and save useful reports to the local workspace.",
                primaryProvider = "gemini", primaryModel = "gemini-3.7-flash", temperature = 0.4f, maxSteps = 14,
                computerPermission = false, shellPermission = false, filesystemPermission = true,
                networkPermission = true, automationPermission = true, subagentPermission = false
            )
            agentDao.insertAgents(listOf(defaultAgent, coder, researcher).map { it.toEntity() })
            val now = nowString()
            val conversation = ConversationEntity("conv-welcome-1", "مرحباً بك في Agentna", defaultAgent.id, now)
            convDao.insertConversation(conversation)
            msgDao.insertMessage(MessageEntity(UUID.randomUUID().toString(), conversation.id, "assistant", "مرحباً بك في **Agentna**. محرك الوكيل والأدوات والسجل والموافقات تعمل محلياً على هاتفك. أضف مفتاح مزود واحد على الأقل من الإعدادات؛ بعدها يمكنني البحث عبر الويب العام وقراءة وكتابة ملفات مساحة العمل دون أي خادم وسيط.", now))
            logDao.insertLog(ExecutionLogEntity(UUID.randomUUID().toString(), "bootstrap", conversation.id, defaultAgent.id, "info", "runtime.ready", "Agentna on-device runtime initialized", "Room database + Android Keystore + direct provider APIs"))
        }
        if (automationDao.getAutomationById(DEFAULT_AUTOMATION_ID) == null) {
            automationDao.insertAutomation(
                AutomationModel(
                    id = DEFAULT_AUTOMATION_ID, name = "Morning research brief",
                    description = "Daily research briefing. Android may defer background execution for battery or system constraints.",
                    cronExpression = "0 8 * * *", agentId = RESEARCH_AGENT_ID,
                    prompt = "اجمع أهم المستجدات من مصادر ويب عامة واكتب تقريراً موجزاً في workspace/reports/daily-brief.md",
                    enabled = false
                ).toEntity()
            )
        }
    }

    suspend fun clearAndReseedDatabase() = withContext(Dispatchers.IO) {
        agentDao.deleteAllAgents(); convDao.deleteAllConversations(); msgDao.deleteAllMessages(); approvalDao.deleteAllApprovals();
        logDao.deleteAllLogs(); automationDao.deleteAllAutomations(); seedDefaultDataIfEmpty()
    }
    suspend fun saveAgent(agent: AgentModel) = withContext(Dispatchers.IO) { agentDao.insertAgent(agent.toEntity()) }
    suspend fun deleteAgent(id: String) = withContext(Dispatchers.IO) { agentDao.deleteAgent(id) }
    suspend fun createConversation(title: String, agentId: String): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString(); convDao.insertConversation(ConversationEntity(id, title, agentId, nowString())); id
    }
    suspend fun saveMessage(msg: MessageModel) = withContext(Dispatchers.IO) { msgDao.insertMessage(MessageEntity(msg.id, msg.conversationId, msg.role, msg.content, msg.createdAt.ifBlank(::nowString))) }
    suspend fun saveApproval(approval: ApprovalModel) = withContext(Dispatchers.IO) {
        approvalDao.insertApproval(ApprovalEntity(approval.id, approval.runId, approval.conversationId, approval.toolName, approval.argumentsJson, approval.sourcePrompt, approval.reason, approval.riskLevel, approval.status, approval.createdAt.ifBlank(::nowString)))
    }
    suspend fun resolveApproval(approvalId: String, decision: String) = withContext(Dispatchers.IO) { approvalDao.updateApprovalStatus(approvalId, decision) }
    suspend fun getAutomation(id: String): AutomationModel? = withContext(Dispatchers.IO) { automationDao.getAutomationById(id)?.toModel() }
    suspend fun saveAutomation(automation: AutomationModel) = withContext(Dispatchers.IO) { automationDao.insertAutomation(automation.toEntity()) }
    suspend fun setAutomationEnabled(id: String, enabled: Boolean, nextRunAt: String?) = withContext(Dispatchers.IO) { automationDao.setEnabled(id, enabled, nextRunAt) }
    suspend fun updateAutomationRunState(id: String, lastRunAt: String?, nextRunAt: String?, status: String?) = withContext(Dispatchers.IO) { automationDao.updateRunState(id, lastRunAt, nextRunAt, status) }
    suspend fun deleteAutomation(id: String) = withContext(Dispatchers.IO) { automationDao.deleteAutomation(id) }
    fun getLogsForConversation(convId: String): Flow<List<ExecutionLogEntity>> = logDao.getLogsForConversation(convId)
    suspend fun logExecutionEvent(runId: String, conversationId: String, agentId: String, level: String, event: String, message: String, details: String = "") = withContext(Dispatchers.IO) {
        logDao.insertLog(ExecutionLogEntity(UUID.randomUUID().toString(), runId, conversationId, agentId, level, event, message, details))
    }
    suspend fun updateAgentState(agentId: String, status: String, currentTask: String = "", activeUrl: String = "", lastRunId: String = "") = withContext(Dispatchers.IO) {
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
    private fun AgentModel.toEntity() = AgentEntity(id, name, description, icon, systemPrompt, primaryProvider, primaryModel, fallbackProvider, fallbackModel, temperature, maxSteps, approvalPolicy, computerPermission, shellPermission, filesystemPermission, networkPermission, automationPermission, subagentPermission)
    private fun MessageEntity.toModel() = MessageModel(id, conversationId, role, content, createdAt)
    private fun ApprovalEntity.toModel() = ApprovalModel(id = id, runId = runId, conversationId = conversationId, toolName = toolName, argumentsJson = argumentsJson, sourcePrompt = sourcePrompt, reason = reason, riskLevel = riskLevel, status = status, createdAt = createdAt)
    private fun AutomationEntity.toModel() = AutomationModel(id = id, name = name, description = description, type = type, cronExpression = cronExpression, agentId = agentId, prompt = prompt, enabled = enabled, lastRunAt = lastRunAt, nextRunAt = nextRunAt, lastStatus = lastStatus)
    private fun AutomationModel.toEntity() = AutomationEntity(id = id, name = name, description = description, type = type, cronExpression = cronExpression, agentId = agentId, prompt = prompt, enabled = enabled, lastRunAt = lastRunAt, nextRunAt = nextRunAt, lastStatus = lastStatus)
    private fun nowString(): String = System.currentTimeMillis().toString()

    companion object {
        private const val DEFAULT_AGENT_ID = "agent-default-1"
        private const val RESEARCH_AGENT_ID = "agent-research-3"
        private const val DEFAULT_AUTOMATION_ID = "auto-briefing"
    }
}
