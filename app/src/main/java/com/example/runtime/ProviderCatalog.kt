package com.mtzallqmy.agentna.runtime

data class ProviderDefinition(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val suggestedModels: List<String>
)

/**
 * Provider/model defaults verified against vendor documentation for the v1.0.0 release.
 * Users can override model IDs in Settings so model lifecycle changes do not require an app update.
 */
object ProviderCatalog {
    val providers = listOf(
        ProviderDefinition(
            id = "gemini",
            displayName = "Google Gemini",
            defaultModel = "gemini-3.7-flash",
            suggestedModels = listOf(
                "gemini-3.7-flash",
                "gemini-3.6-flash",
                "gemini-3.5-flash",
                "gemini-3.5-flash-lite"
            )
        ),
        ProviderDefinition(
            id = "openai",
            displayName = "OpenAI",
            defaultModel = "gpt-5.6",
            suggestedModels = listOf("gpt-5.6", "gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna")
        ),
        ProviderDefinition(
            id = "anthropic",
            displayName = "Anthropic Claude",
            defaultModel = "claude-sonnet-5",
            suggestedModels = listOf(
                "claude-sonnet-5",
                "claude-sonnet-4-6",
                "claude-opus-5",
                "claude-haiku-4-5-20251001"
            )
        ),
        ProviderDefinition(
            id = "xai",
            displayName = "xAI Grok",
            defaultModel = "grok-4.6",
            suggestedModels = listOf("grok-4.6", "grok-4.3", "grok-4.3-latest")
        )
    )

    fun definition(id: String): ProviderDefinition = providers.firstOrNull { it.id == id.lowercase() }
        ?: providers.first()
}
