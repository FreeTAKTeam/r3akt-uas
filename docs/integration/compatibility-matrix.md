# Compatibility matrix

SDK-advertised support and physically tested support are separate columns. No aircraft or controller is physically tested yet; the app alone has run on one phone.

## Build baseline (scaffold)

| Item | Value | Qualified |
|---|---|---|
| Android min SDK | 26 | builds |
| compile/target SDK | 35 | builds; sim variant runs on API 35 emulator |
| Android Gradle Plugin / Gradle | 8.7.2 / 8.11.1 | builds |
| Kotlin | 1.9.24 | builds |
| NDK | 27.0.12077973 | builds |
| Rust | 1.89.0, edition 2024 (1.88.0 until milestone 2b re-pin) | builds, tests pass |
| RCH + REM (milestone 2b) | RCH `f5822210` with `reticulumd` LXMF-rs `9b972f9a`; REM `0cfa422` (LXMF-rs `8c27a200`) on the x86_64 API 35 emulator | tested together: REM shows a relayed object once (appearance only, REM #341) |
| RCH + REM (milestone 3 markers) | RCH `83b5d64a` with `reticulumd` LXMF-rs `9b972f9a`; REM `0cfa422` on the x86_64 API 35 emulator; publisher Pixel 7 `simNet` | tested together: operator markers created, moved and deleted on the Pixel appear, move and disappear in REM; changes made with the hub down are delivered after it returns |
| App RCH crates `d7ad6e17` + hub RCH `83b5d64a` + REM `0cfa422` (milestone 3 camera point) | app pinned to RCH `d7ad6e17`, hub binary at `83b5d64a` | tested together: a camera-point marker computed in the app is relayed and shown in REM; the hub change between the two revisions (canonical team create) was not needed |
| ABIs | arm64-v8a, x86_64 (Rust); DJI SDK 4.18 provides arm64-v8a, armeabi-v7a, x86 | sim: both ABIs; dji4: arm64-v8a only at runtime (G-10) |
| DJI Mobile SDK | 4.18 (`com.dji:dji-sdk`, `dji-sdk-provided`) | loads and runs its registration call on a Pixel 7 (Android 17, target SDK 35); never registered with a valid key |

## DJI SDK 4 requirements

| Requirement | Status |
|---|---|
| DJI developer account and app key for package `org.r3akt.uas` (`djiApiKey` Gradle property, kept out of the repository) | required; without it the cockpit shows `NO DJI KEY` and the SDK is not started |
| SDK activation/registration on first run (network needed once) | implemented; the registration call runs on Android 17 and the SDK's invalid-key error is shown. Whether that test reached DJI's servers is not established; success with a valid key is unproven |
| `Helper.install` in `Application.attachBaseContext` (`com.cySdkyc.clx.Helper` in 4.18) | runs on a Pixel 7. It must run before any class that mentions a DJI type is initialized (`Dji4Loader`), or the DJI callback classes stay broken for the process |
| `INTERNET`, `DJIAoaControllerActivity` with the SDK's `accessory_filter`, `org.apache.http.legacy` | in the dji4 manifest; USB accessory attach unproven (no controller) |
| Runtime permissions (location, storage, phone state) | none requested; whether registration or connection needs them is unknown |
| 16 KB memory pages | 19 of 36 DJI arm64 libraries are 4 KB aligned (G-14); runs on 4 KB-page devices only |
| AndroidX AppCompat and ConstraintLayout provided by the host app | provided |
| Unstripped, legacy-packaged DJI native libraries | configured |
| Aircraft firmware / RC firmware minimums | to be recorded per tested aircraft |

## Aircraft and controllers

| Aircraft | Controller | Android device | SDK-advertised (source) | Physically tested | Evidence |
|---|---|---|---|---|---|
| — | — | — | to be copied from DJI's SDK 4.18 release notes at milestone 4 | none | — |
| none | none | Pixel 7, Android 17 (API 37) | — | app only: start, registration call, cockpit and action refusal without an aircraft | [acceptance.md](../specification/acceptance.md) milestone 4 record |
