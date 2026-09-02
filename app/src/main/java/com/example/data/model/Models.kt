package com.mtzallqmy.agentna.data.model

import java.util.UUID

data class AgentModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val icon: String = "smart_toy",
    val systemPrompt: String,
    val primaryProvider: String = "gemini",
    val primaryModel: String = "gemini-3.7-flash",
    val fallbackProvider: String? = null,
    val fallbackModel: String? = null,
    val temperature: Float = 0.7f,
    val maxSteps: Int = 12,
    val approvalPolicy: String = "require_approval",
    val computerPermission: Boolean = false,
    val shellPermission: Boolean = false,
    val filesystemPermission: Boolean = true,
    val networkPermission: Boolean = true,
    val automationPermission: Boolean = true,
    val subagentPermission: Boolean = false
)

data class ConversationModel(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val agentId: String,
    val createdAt: String = ""
)

data class MessageModel(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: String = ""
)

data class ApprovalModel(
    val id: String = UUID.randomUUID().toString(),
    val runId: String = "",
    val conversationId: String = "",
    val toolName: String,
    val arguments: Map<String, Any?> = emptyMap(),
    val argumentsJson: String = "{}",
    val sourcePrompt: String = "",
    val reason: String,
    val riskLevel: String = "medium",
    val status: String = "pending",
    val createdAt: String = ""
)

data class AutomationModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val type: String = "recurring",
    val cronExpression: String = "0 8 * * *",
    val agentId: String,
    val prompt: String,
    val enabled: Boolean = true,
    val lastRunAt: String? = null,
    val nextRunAt: String? = null,
    val lastStatus: String? = null
)

data class ComputerSessionModel(
    val id: String = "local-device",
    val status: String = "ready",
    val activeUrl: String = "",
    val cursorX: Int = 0,
    val cursorY: Int = 0,
    val lastAction: String = "Agentna local runtime ready",
    val latestScreenshotBase64: String? = null
)

data class FileItemModel(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val mimeType: String = "text/plain",
    val size: Long = 0,
    val conversationId: String? = null,
    val runId: String? = null,
    val downloadUrl: String = "",
    val contentPreview: String = ""
)

data class ChatItem(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String = "",
    val toolName: String? = null,
    val toolStatus: String? = null,
    val isStreaming: Boolean = false,
    val approval: ApprovalModel? = null,
    val timestamp: Long = System.currentTimeMillis()
)
