# Agentna — وكيل ذكاء اصطناعي محلي لنظام Android

Agentna is an Android-first AI agent runtime. The orchestration loop, local tools, approvals, state, logs and workspace run **on the phone**. Agentna does not require an application server, gateway or WebSocket service. Model inference is sent directly over HTTPS to the provider selected by the user.

## v1 architecture

```text
Jetpack Compose UI
       │
       ▼
AgentViewModel ── Room (agents, chats, approvals, logs)
       │
       ▼
AgentEngine (multi-step local loop)
       │
       ├── LocalToolRegistry
       │     ├── workspace.list/read/write/delete
       │     ├── web.fetch (public HTTPS only + SSRF protection)
       │     └── device.info / approved external URL open
       │
       └── ProviderClient ── HTTPS ──► OpenAI / Gemini / Anthropic / xAI
                 ▲
                 └── API keys encrypted with Android Keystore
```

## Security model

- API keys are encrypted with AES-GCM using a key generated and held by Android Keystore.
- Cleartext network traffic is disabled.
- `web.fetch` rejects localhost, private/link-local addresses and carrier-grade NAT ranges, and re-validates DNS at connection time.
- Local file tools are confined to the app workspace using canonical path checks and size limits.
- Deletion, overwrites, and opening an external URL require explicit user approval.
- There is deliberately **no arbitrary shell/container execution** in the Android app. Agentna never fabricates shell output or browser screenshots.
- Local app state uses Room. No Agentna backend receives conversations or keys.

> The selected model provider receives the prompt/context required to perform inference according to that provider's API and privacy terms.

## Providers

The app supports direct API calls to:

- Google Gemini (default: `gemini-3.7-flash`)
- OpenAI (default: `gpt-5.1`)
- Anthropic Claude (default: `claude-sonnet-5`)
- xAI Grok (default: `grok-4.6`)

Model IDs are editable in Settings so provider model migrations do not require an app release.

## Build

Requirements:

- Android SDK 37
- JDK 17
- Gradle 9.3.1

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
gradle :app:lintDebug
```

GitHub Actions pins the same JDK/Gradle versions and runs build + tests + lint on every push and pull request.

## Release signing

Production signing material is never committed. Release builds accept these environment variables:

- `KEYSTORE_PATH`
- `STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Without them, Gradle can compile an unsigned release bundle/APK, but the production release workflow intentionally requires signing secrets before publishing an installable release.

## License

MIT License. See [LICENSE](LICENSE).
