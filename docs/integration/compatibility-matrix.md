# Compatibility matrix

SDK-advertised support and physically tested support are separate columns. Nothing is physically tested yet.

## Build baseline (scaffold)

| Item | Value | Qualified |
|---|---|---|
| Android min SDK | 26 | builds |
| compile/target SDK | 35 | builds; sim variant runs on API 35 emulator |
| Android Gradle Plugin / Gradle | 8.7.2 / 8.11.1 | builds |
| Kotlin | 1.9.24 | builds |
| NDK | 27.0.12077973 | builds |
| Rust | 1.88.0, edition 2024 | builds, tests pass |
| ABIs | arm64-v8a, x86_64 (Rust); DJI SDK 4.18 provides arm64-v8a, armeabi-v7a, x86 | sim: both ABIs; dji4: arm64-v8a only at runtime (G-10) |
| DJI Mobile SDK | 4.18 (`com.dji:dji-sdk`, `dji-sdk-provided`) | compiles and packages; never registered |

## DJI SDK 4 requirements

| Requirement | Status |
|---|---|
| DJI developer account and app key (`djiApiKey` Gradle property, kept out of the repository) | not used by the scaffold |
| SDK activation/registration on first run (network needed once) | milestone 4 |
| `Helper.install` in `Application.attachBaseContext` (`com.cySdkyc.clx.Helper` in 4.18) | implemented, runtime unproven on hardware |
| AndroidX AppCompat and ConstraintLayout provided by the host app | provided |
| Unstripped, legacy-packaged DJI native libraries | configured |
| Aircraft firmware / RC firmware minimums | to be recorded per tested aircraft |

## Aircraft and controllers

| Aircraft | Controller | Android device | SDK-advertised (source) | Physically tested | Evidence |
|---|---|---|---|---|---|
| — | — | — | to be copied from DJI's SDK 4.18 release notes at milestone 4 | none | — |
