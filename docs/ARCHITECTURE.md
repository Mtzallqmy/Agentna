# Agentna v1 Architecture

```text
Jetpack Compose UI
       │
       ▼
AgentViewModel
       │
       ├──────────────► Room
       │                agents / chats / approvals / logs / automations
       │
       ▼
AgentEngine (on device)
       │
       ├── permission + approval gate
       ├── LocalToolRegistry
       │    ├── workspace.list/read/write/delete
       │    ├── web.fetch (HTTPS + SSRF checks)
       │    ├── device.info
       │    └── device.open_url (approval)
       │
       └── ProviderClient ── HTTPS ──► OpenAI / Gemini / Anthropic / xAI

WorkManager ──► AutomationWorker ──► same AgentEngine
```

## Invariants

1. Agentna has no required application server or WebSocket gateway.
2. The model never directly executes Android tools; it proposes a protocol tool call and the local runtime validates permissions and approval requirements.
3. Tool results are the only source of truth for completed local actions.
4. File access is confined to the app-private workspace.
5. Background automations use the same runtime and safety policy as interactive runs.
6. Provider credentials are stored with Android Keystore and are never committed to source control.

## Automation timing

Version 1 supports daily schedules expressed as `minute hour * * *`. WorkManager is reliable deferred work, not an exact alarm mechanism: Android can defer execution because of battery, Doze, network, or scheduler constraints.
