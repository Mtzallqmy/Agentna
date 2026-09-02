# Agentna Release Process

Agentna uses reproducible GitHub Actions builds with Gradle 9.3.1 and JDK 17. Production signing keys are never committed to the repository.

## Required repository secrets

Configure these GitHub Actions secrets before publishing an installable production release:

- `ANDROID_KEYSTORE_BASE64` — base64-encoded durable Android release keystore.
- `ANDROID_STORE_PASSWORD` — keystore password.
- `ANDROID_KEY_ALIAS` — signing-key alias.
- `ANDROID_KEY_PASSWORD` — key password.

The same signing identity must remain available for future APK updates distributed outside a store. If Google Play App Signing is used later, follow Google's upload-key/app-signing-key lifecycle requirements.

## Verification gate

Every change to `main` is expected to pass:

```bash
gradle :app:assembleDebug \
       :app:testDebugUnitTest \
       :app:lintDebug \
       :app:assembleRelease \
       :app:bundleRelease
```

The release workflow repeats tests/lint and the release build with signing enabled, verifies the final APK using Android `apksigner`, then produces SHA-256 checksums.

## Publish v1.0.0

`app/build.gradle.kts` must contain `versionName = "1.0.0"` and `versionCode = 1`.

The workflow can be started manually with tag `v1.0.0`. It can also run automatically when the final commit merged to `main` contains `[release v1.0.0]`.

After successful verification it creates the GitHub tag and Release, attaching:

- `Agentna-1.0.0.apk`
- `Agentna-1.0.0.aab`
- `SHA256SUMS.txt`

The workflow refuses to publish if any signing secret is missing or if the tag does not match `versionName`.
