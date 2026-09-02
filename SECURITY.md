# Security Policy

## Supported version

Security fixes are currently provided for the latest `1.x` release line.

## Security model

Agentna is intentionally Android-first and has no Agentna application server. Agent orchestration, local tools, approvals, workspace files, execution logs, and automations stay on the device. Model prompts/context are sent directly to the provider selected by the user.

- API keys are encrypted at rest using an AES-GCM key held by Android Keystore.
- Cleartext HTTP is disabled at the Android network-security layer.
- Workspace tools resolve canonical paths and reject traversal outside the app workspace.
- `web.fetch` rejects loopback, private, link-local, multicast/unspecified and CGNAT targets and re-validates resolved addresses before connecting.
- Destructive file actions, overwrites and external URL opening require explicit approval.
- Agent permissions are enforced by the runtime, including when a persisted approval is resumed after process death.
- There is no arbitrary shell/container tool and Agentna must not fabricate shell output or screenshots.

## Threat boundary

Android Keystore materially improves key protection, but no app can promise secrecy on a fully compromised/rooted device. Provider APIs receive the context sent for inference and are governed by their own security/privacy policies. Web and tool output is treated as untrusted input.

## Reporting a vulnerability

Do not publish exploitable details in a public issue. Use GitHub's private vulnerability reporting feature for this repository when available. Include affected version, reproduction steps, impact, and any suggested mitigation.
