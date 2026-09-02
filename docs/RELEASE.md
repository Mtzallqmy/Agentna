# Release process

Agentna `1.0.0` uses Android Gradle Plugin 9.1.1, Gradle 9.3.1, JDK 17, Android API 37 and SDK Build Tools 36.0.0.

## Durable production signing

Never commit the signing keystore. Configure these GitHub Actions repository secrets before publishing the final production APK/AAB:

- `ANDROID_KEYSTORE_BASE64` — base64 encoding of the release `.jks` file
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The private key must be retained permanently. Android updates must be signed by the same signing identity (or follow an applicable platform-supported key-rotation process).

Example to prepare the keystore value locally:

```bash
base64 -w 0 agentna-release.jks
```

## CI gate

Every push/PR runs `assembleDebug`, unit tests and `lintDebug`. The final release commit contains `[release]`; when its Android CI run succeeds, the Release workflow builds `lintRelease`, `assembleRelease` and `bundleRelease`.

If all signing secrets are available, the workflow publishes a normal `v1.0.0` release with signed APK/AAB and SHA-256 checksums. If secrets are missing, it publishes a clearly marked pre-release with a debug-signed evaluation APK and unsigned AAB rather than pretending those artifacts have a durable production identity.
