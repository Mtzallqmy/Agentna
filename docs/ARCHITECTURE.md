# Agentna Architecture

## Runtime boundary

Agentna v1 is an Android-native application. The orchestration loop, local state, approvals, workspace tools and automations run in the app process or WorkManager workers on the device. There is no Agentna gateway or application server.

```text
Jetpack Compose UI
       │
       ▼
AgentViewModel
       │
       ├── Room: agents, conversations, messages, approvals, logs, automations
       ├── Android Keystore: provider API keys
       │
       ▼
AgentEngine
       │
       ├── LocalToolRegistry
       │   ├── workspace.list/read/write/delete
       │   ├── web.fetch (public HTTPS only)
       │   ├── device.info
       │   └── device.open_url (approval required)
       │
       └── ProviderClient ── HTTPS ──► selected model provider

WorkManager ──► AutomationWorker ──► same AgentEngine and safety policy
```

## Agent execution

1. The user message is persisted locally.
2. `AgentEngine` builds bounded conversation context and the agent system policy.
3. `ProviderClient` sends inference directly to the selected provider.
4. The model returns either a final response or a structured tool decision.
5. The runtime checks the agent's permissions before every tool call.
6. Sensitive operations produce a persisted approval and pause the run.
7. Tool results are appended to the transcript and the loop continues up to the configured step limit.
8. Provider failures may use the persisted fallback provider/model when configured.

## Data and privacy

Room is the local source of truth. API keys are encrypted with an AES-GCM key generated in Android Keystore. Agentna does not proxy prompts, API keys or conversations through an Agentna-owned service. The selected inference provider necessarily receives the prompt/context sent for model inference.

## Tool security

Workspace paths are canonicalized and confined to the app-private workspace. Web fetching accepts HTTPS only and rejects private, loopback, link-local, CGNAT and other non-public destinations, including resolved DNS addresses. Tool output is treated as untrusted data. There is no arbitrary shell execution or simulated computer-control subsystem.

## Background automations

Daily local schedules are persisted in Room and scheduled with WorkManager. Android may defer work because of Doze, battery, network or scheduler constraints, so these automations are intentionally not presented as exact alarms. Background runs use the same permissions and approval rules as interactive runs.
