package com.mtzallqmy.agentna.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mtzallqmy.agentna.data.model.MessageModel
import com.mtzallqmy.agentna.data.repository.AgentRepository
import com.mtzallqmy.agentna.runtime.AgentEngine
import com.mtzallqmy.agentna.runtime.AgentRunResult
import com.mtzallqmy.agentna.runtime.LocalToolRegistry
import com.mtzallqmy.agentna.runtime.ProviderClient
import com.mtzallqmy.agentna.security.SecureApiKeyStore

class AutomationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val automationId = inputData.getString(KEY_AUTOMATION_ID) ?: return Result.failure()
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        val repository = AgentRepository(applicationContext)
        repository.seedDefaultDataIfEmpty()
        val automation = repository.getAutomation(automationId) ?: return Result.success()
        if (!manual && !automation.enabled) return Result.success()
        val agent = repository.getAgent(automation.agentId)
        if (agent == null) {
            repository.setAutomationEnabled(automation.id, false, null)
            repository.updateAutomationRunState(automation.id, now(), null, "agent_missing")
            AutomationScheduler.cancel(applicationContext, automation.id)
            return Result.failure()
        }
        if (!agent.automationPermission) {
            repository.setAutomationEnabled(automation.id, false, null)
            repository.updateAutomationRunState(automation.id, now(), null, "permission_denied")
            AutomationScheduler.cancel(applicationContext, automation.id)
            return Result.failure()
        }

        val conversationId = repository.createConversation("Automation • ${automation.name}", agent.id)
        repository.saveMessage(MessageModel(conversationId = conversationId, role = "user", content = automation.prompt))
        repository.logExecutionEvent("automation:${automation.id}", conversationId, agent.id, "info", "automation.started", automation.name, automation.cronExpression)

        val keyStore = SecureApiKeyStore(applicationContext)
        val engine = AgentEngine(ProviderClient(keyStore), LocalToolRegistry(applicationContext)) { provider, fallback ->
            keyStore.getModel(provider, fallback)
        }
        val backgroundAgent = agent.copy(maxSteps = agent.maxSteps.coerceIn(1, MAX_BACKGROUND_STEPS))
        val result = engine.run(conversationId, automation.prompt, backgroundAgent, repository.getMessagesSnapshot(conversationId))

        val status = when (result) {
            is AgentRunResult.Completed -> {
                repository.saveMessage(MessageModel(conversationId = conversationId, role = "assistant", content = result.content))
                repository.logExecutionEvent("automation:${automation.id}", conversationId, agent.id, "info", "automation.completed", automation.name)
                "success"
            }
            is AgentRunResult.AwaitingApproval -> {
                repository.saveApproval(result.approval)
                repository.logExecutionEvent(result.approval.runId, conversationId, agent.id, "warn", "automation.awaiting_approval", result.approval.reason, result.approval.argumentsJson)
                "waiting_approval"
            }
            is AgentRunResult.Failed -> {
                repository.saveMessage(MessageModel(conversationId = conversationId, role = "assistant", content = "Automation failed: ${result.message}"))
                repository.logExecutionEvent("automation:${automation.id}", conversationId, agent.id, "error", "automation.failed", result.message)
                "failed"
            }
        }

        val finishedAt = now()
        val fresh = repository.getAutomation(automation.id)
        val nextEpoch = if (fresh?.enabled == true) AutomationScheduler.scheduleNext(applicationContext, fresh, replaceExisting = false) else null
        repository.updateAutomationRunState(automation.id, finishedAt, nextEpoch?.toString(), status)
        return Result.success()
    }

    private fun now() = System.currentTimeMillis().toString()

    companion object {
        const val KEY_AUTOMATION_ID = "automation_id"
        const val KEY_MANUAL = "manual"
        private const val MAX_BACKGROUND_STEPS = 4
    }
}
