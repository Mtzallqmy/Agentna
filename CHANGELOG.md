# Changelog

All notable changes to Agentna are documented here.

## 1.0.0 - 2026-09-03

### Added
- Fully on-device Kotlin agent orchestration loop for Android.
- Direct provider integration for OpenAI, Google Gemini, Anthropic Claude and xAI Grok.
- Android Keystore encrypted API-key storage and editable model IDs.
- Room persistence for agents, conversations, messages, approvals, execution logs, state and automations.
- App-private workspace tools and constrained HTTPS web fetching.
- Human approval flow for destructive or sensitive operations.
- WorkManager-backed daily local automations with manual run, enable/disable and execution status.
- Arabic/English RTL-aware Jetpack Compose UI with Chat, Agents, Files, Automations, Safety and Settings surfaces.
- CI build, unit-test, lint, R8 release verification and signed GitHub Release automation.

### Security
- Removed gateway/server dependency, fake computer worker, arbitrary shell execution and simulated screenshots.
- Enforced per-agent filesystem/network permissions.
- Added canonical path confinement and SSRF protections including IPv6 and carrier-grade NAT.
- Moved blocking provider/tool I/O off the Android main thread.
- Added provider failover persistence and safe approval restoration after process death.
