package com.mtzallqmy.agentna.runtime

import com.mtzallqmy.agentna.data.model.AgentModel
import com.mtzallqmy.agentna.data.model.ApprovalModel
import com.mtzallqmy.agentna.data.model.MessageModel
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

sealed interface AgentRunResult {
    data class Completed(val content: String) : AgentRunResult
    data class AwaitingApproval(val approval: ApprovalModel, val pendingRun: PendingAgentRun) : AgentRunResult
    data class Failed(val message: String) : AgentRunResult
}

data class PendingAgentRun internal constructor(
    val runId: String,
    val conversationId: String,
    val sourcePrompt: String,
    val agent: AgentModel,
    internal val transcript: MutableList<ProviderMessage>,
    internal val step: Int,
    internal val toolName: String,
    internal val arguments: JSONObject
)

sealed interface AgentEvent {
    data class Step(val index: Int, val max: Int) : AgentEvent
    data class ToolStarted(val name: String, val arguments: String) : AgentEvent
    data class ToolFinished(val name: String, val result: String, val success: Boolean) : AgentEvent
}

class AgentEngine(
    private val providerClient: ProviderClient,
    private val tools: LocalToolRegistry,
    private val configuredModel: (provider: String, fallback: String) -> String
) {
    suspend fun run(
        conversationId: String,
        sourcePrompt: String,
        agent: AgentModel,
        history: List<MessageModel>,
        onEvent: suspend (AgentEvent) -> Unit = {}
    ): AgentRunResult {
        val transcript = history
            .filter { it.role == "user" || it.role == "assistant" }
            .takeLast(MAX_CONVERSATION_MESSAGES)
            .map { ProviderMessage(it.role, it.content) }
            .toMutableList()
        // The caller normally stores the newest user message before invoking run(). Avoid duplicating it.
        if (transcript.lastOrNull()?.role != "user" || transcript.lastOrNull()?.content != sourcePrompt) {
            transcript += ProviderMessage("user", sourcePrompt)
        }
        return continueRun(
            runId = UUID.randomUUID().toString(),
            conversationId = conversationId,
            sourcePrompt = sourcePrompt,
            agent = agent,
            transcript = transcript,
            startStep = 0,
            onEvent = onEvent
        )
    }

    suspend fun resume(
        pending: PendingAgentRun,
        approved: Boolean,
        onEvent: suspend (AgentEvent) -> Unit = {}
    ): AgentRunResult {
        val result = if (approved) tools.execute(pending.toolName, pending.arguments, approved = true)
        else ToolResult.Failure("User rejected this action")

        when (result) {
            is ToolResult.Success -> {
                onEvent(AgentEvent.ToolFinished(pending.toolName, result.output, true))
                pending.transcript += ProviderMessage(
                    "user",
                    toolResultMessage(pending.toolName, result.output, success = true)
                )
            }
            is ToolResult.Failure -> {
                onEvent(AgentEvent.ToolFinished(pending.toolName, result.error, false))
                pending.transcript += ProviderMessage(
                    "user",
                    toolResultMessage(pending.toolName, result.error, success = false)
                )
            }
            is ToolResult.RequiresApproval -> {
                return AgentRunResult.Failed("Approval state could not be resolved safely")
            }
        }

        return continueRun(
            runId = pending.runId,
            conversationId = pending.conversationId,
            sourcePrompt = pending.sourcePrompt,
            agent = pending.agent,
            transcript = pending.transcript,
            startStep = pending.step + 1,
            onEvent = onEvent
        )
    }

    fun executeApprovedTool(toolName: String, argumentsJson: String): ToolResult {
        val args = runCatching { JSONObject(argumentsJson) }.getOrElse { JSONObject() }
        return tools.execute(toolName, args, approved = true)
    }

    private suspend fun continueRun(
        runId: String,
        conversationId: String,
        sourcePrompt: String,
        agent: AgentModel,
        transcript: MutableList<ProviderMessage>,
        startStep: Int,
        onEvent: suspend (AgentEvent) -> Unit
    ): AgentRunResult {
        val maxSteps = agent.maxSteps.coerceIn(1, MAX_AGENT_STEPS)
        val provider = agent.primaryProvider.lowercase()
        val model = configuredModel(provider, agent.primaryModel)
        val systemPrompt = buildSystemPrompt(agent)

        for (step in startStep until maxSteps) {
            onEvent(AgentEvent.Step(step + 1, maxSteps))
            val raw = try {
                providerClient.complete(provider, model, systemPrompt, transcript)
            } catch (e: ProviderException) {
                return AgentRunResult.Failed(e.message ?: "Provider request failed")
            } catch (e: Exception) {
                return AgentRunResult.Failed(e.message ?: "Agent execution failed")
            }

            when (val decision = parseDecision(raw)) {
                is AgentDecision.Final -> return AgentRunResult.Completed(decision.content)
                is AgentDecision.Tool -> {
                    transcript += ProviderMessage("assistant", decision.raw)
                    onEvent(AgentEvent.ToolStarted(decision.name, decision.arguments.toString()))
                    when (val toolResult = tools.execute(decision.name, decision.arguments, approved = false)) {
                        is ToolResult.Success -> {
                            onEvent(AgentEvent.ToolFinished(decision.name, toolResult.output, true))
                            transcript += ProviderMessage(
                                "user",
                                toolResultMessage(decision.name, toolResult.output, success = true)
                            )
                        }
                        is ToolResult.Failure -> {
                            onEvent(AgentEvent.ToolFinished(decision.name, toolResult.error, false))
                            transcript += ProviderMessage(
                                "user",
                                toolResultMessage(decision.name, toolResult.error, success = false)
                            )
                        }
                        is ToolResult.RequiresApproval -> {
                            val approval = ApprovalModel(
                                runId = runId,
                                conversationId = conversationId,
                                toolName = decision.name,
                                arguments = jsonObjectToMap(decision.arguments),
                                argumentsJson = decision.arguments.toString(),
                                sourcePrompt = sourcePrompt,
                                reason = toolResult.reason,
                                riskLevel = toolResult.riskLevel,
                                status = "pending"
                            )
                            return AgentRunResult.AwaitingApproval(
                                approval = approval,
                                pendingRun = PendingAgentRun(
                                    runId = runId,
                                    conversationId = conversationId,
                                    sourcePrompt = sourcePrompt,
                                    agent = agent,
                                    transcript = transcript,
                                    step = step,
                                    toolName = decision.name,
                                    arguments = decision.arguments
                                )
                            )
                        }
                    }
                }
            }
        }
        return AgentRunResult.Failed("The agent reached its $maxSteps-step execution limit. Refine the task or increase the agent step limit.")
    }

    private fun buildSystemPrompt(agent: AgentModel): String = buildString {
        appendLine(agent.systemPrompt.trim())
        appendLine()
        appendLine("You are Agentna, an on-device Android agent orchestrator. The orchestration, state, tools and approvals run locally on the phone. Only model inference is sent to the selected AI provider API.")
        appendLine("Be truthful about tool execution and Android limitations. Do not invent screenshots, shell output, files, web results, or completed actions.")
        appendLine("Prefer concise tool calls, inspect results, and stop when the user's goal is met.")
        appendLine()
        appendLine(tools.toolInstructions())
    }

    private fun parseDecision(rawResponse: String): AgentDecision {
        val cleaned = rawResponse.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val candidate = runCatching { JSONObject(cleaned) }.getOrNull()
            ?: extractJSONObject(cleaned)
            ?: return AgentDecision.Final(rawResponse.trim())
        return when (candidate.optString("type").lowercase()) {
            "tool" -> {
                val name = candidate.optString("tool").trim()
                if (name.isBlank()) AgentDecision.Final(rawResponse.trim())
                else AgentDecision.Tool(
                    name = name,
                    arguments = candidate.optJSONObject("arguments") ?: JSONObject(),
                    raw = candidate.toString()
                )
            }
            "final" -> AgentDecision.Final(candidate.optString("content").ifBlank { rawResponse.trim() })
            else -> AgentDecision.Final(candidate.optString("content").ifBlank { rawResponse.trim() })
        }
    }

    private fun extractJSONObject(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    private fun toolResultMessage(toolName: String, result: String, success: Boolean): String =
        "TOOL RESULT for $toolName (success=$success):\n${result.take(MAX_TOOL_RESULT_CHARS)}\nContinue the task. Return exactly one protocol JSON object."

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = jsonValue(json.opt(key))
        }
        return result
    }

    private fun jsonValue(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> (0 until value.length()).map { jsonValue(value.opt(it)) }
        else -> value
    }

    private sealed interface AgentDecision {
        data class Tool(val name: String, val arguments: JSONObject, val raw: String) : AgentDecision
        data class Final(val content: String) : AgentDecision
    }

    companion object {
        private const val MAX_AGENT_STEPS = 20
        private const val MAX_CONVERSATION_MESSAGES = 30
        private const val MAX_TOOL_RESULT_CHARS = 120_000
    }
}
