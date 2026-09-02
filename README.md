# Agentna — وكيل ذكاء اصطناعي محلي لنظام Android

Agentna is an Android-first AI agent runtime. Orchestration, local tools, approvals, state, logs, workspace files and automations run **on the phone**. There is no Agentna gateway, application server or WebSocket dependency. Only model inference is sent directly over HTTPS to the provider selected by the user.

## v1 architecture

```text
Jetpack Compose UI
       │
       ▼
AgentViewModel ── Room (agents, chats, approvals, logs, automations)
       │
       ▼
AgentEngine (multi-step on-device loop)
       │
       ├── LocalToolRegistry
       │     ├── workspace.list/read/write/delete
       │     ├── web.fetch (public HTTPS only + SSRF protection)
       │     └── device.info / approved external URL open
       │
       └── ProviderClient ── HTTPS ──► OpenAI / Gemini / Anthropic / xAI

WorkManager ──► AutomationWorker ──► same AgentEngine
```

## What runs locally

- Agent loop and tool orchestration
- Agents and permission policies
- Conversations and messages
- Human approvals and execution logs
- App-private workspace
- Daily WorkManager automations
- API-key encryption via Android Keystore

The selected model provider receives only the prompt/context required for inference according to that provider's own API and privacy terms.

## Security model

- API keys are encrypted with AES-GCM using a key generated and held by Android Keystore.
- Cleartext network traffic is disabled.
- `web.fetch` blocks loopback/private/link-local/CGNAT targets and validates DNS results before connecting.
- Workspace tools use canonical path confinement and file-size limits.
- Deletion, overwrites and opening an external URL require explicit approval.
- Per-agent filesystem/network permissions are enforced by the runtime.
- There is deliberately **no arbitrary shell/container execution**. Agentna never fabricates shell output or browser screenshots.
- Background automations use the same safety and approval policy as interactive runs.

See [SECURITY.md](SECURITY.md) for the detailed threat boundary.

## Providers and current v1 defaults

- Google Gemini — `gemini-3.7-flash`
- OpenAI — `gpt-5.6` (alias of the GPT-5.6 Sol family)
- Anthropic Claude — `claude-sonnet-5`
- xAI Grok — `grok-4.6`

Model IDs remain editable in Settings so provider lifecycle changes do not require an app release.

## Automations

Version 1 supports local daily schedules (`minute hour * * *`) through WorkManager. Android may defer background execution due to Doze, battery, network or scheduler constraints; Agentna intentionally does not describe these jobs as exact alarms. Sensitive actions still create an approval rather than executing silently.

## Build

Pinned toolchain:

- Android API 37
- SDK Build Tools 36.0.0
- Android Gradle Plugin 9.1.1
- Gradle 9.3.1
- JDK 17

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
gradle :app:lintDebug
```

GitHub Actions provisions the pinned Android SDK/JDK/Gradle versions and runs all three checks on every push and pull request.

## Release

`versionCode = 1` and `versionName = 1.0.0`. Production signing material is never committed. See [docs/RELEASE.md](docs/RELEASE.md) for the four required Actions secrets and release pipeline. If durable signing secrets are absent, the workflow produces a transparent pre-release instead of mislabeling a debug/unsigned artifact as production-signed.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Release process](docs/RELEASE.md)
- [Security](SECURITY.md)
- [Changelog](CHANGELOG.md)

## License

MIT License. See [LICENSE](LICENSE).
