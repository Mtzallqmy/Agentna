# Agentna v1.0.0

Agentna 1.0.0 is the first Android-first release of the project. The agent runtime, local tools, permissions, approvals, logs, workspace state and automations run on the phone; no Agentna gateway or WebSocket server is required. Model inference is sent directly over HTTPS to the provider configured by the user.

## Highlights

- On-device multi-step AgentEngine written in Kotlin.
- Direct OpenAI, Gemini, Anthropic Claude and xAI provider connections.
- Android Keystore encrypted provider API keys.
- Room-backed local conversations, agents, approvals, logs and automations.
- App-private workspace tools with path-confinement protections.
- Public-HTTPS web fetch tool with SSRF protections.
- Explicit approvals for destructive/sensitive actions.
- Provider failover with persisted configuration.
- WorkManager local daily automations using the same runtime and safety model.
- Arabic/English RTL-aware Jetpack Compose interface.
- Automated debug/release build, tests, lint, APK signature verification and release checksums.

## Important runtime boundary

Agentna intentionally does not provide arbitrary shell execution, hidden device control or fabricated browser screenshots. Background schedules use WorkManager and can be deferred by Android power/network constraints.

## Installation

For direct APK installation, download `Agentna-1.0.0.apk` and verify its SHA-256 hash against `SHA256SUMS.txt`. The AAB is provided for store distribution workflows.
