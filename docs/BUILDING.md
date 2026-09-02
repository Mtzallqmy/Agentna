# Building Agentna

## Toolchain

Agentna 1.0.0 is verified with:

- JDK 17
- Gradle 9.3.1
- Android Gradle Plugin 9.1.1
- compileSdk 37 / targetSdk 36 / minSdk 26

The repository intentionally uses GitHub Actions `setup-gradle` to provision the pinned Gradle distribution. A partial or unverifiable Gradle Wrapper is not committed.

## Local verification

With Android SDK 37 installed and `ANDROID_HOME` configured:

```bash
gradle --no-daemon \
  :app:assembleDebug \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleRelease \
  :app:bundleRelease
```

An unsigned release build is useful for compile/R8 verification only. Installable production APKs must be signed with the durable release key described in `docs/RELEASE.md`.

## CI artifacts

The Android CI workflow retains debug/release build outputs plus lint and unit-test reports. The publishing workflow separately verifies the signed APK using Android `apksigner` before creating a GitHub Release.
