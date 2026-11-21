# R3AKT UAS

An independent Android DJI flying application for the R3AKT ecosystem: existing Reticulum/LXMF libraries, RCH telemetry and mission services, REM-compatible markers, aircraft-centred radar, and separate MediaMTX video.

**Status: alpha (0.1.0-alpha.1).** The app has flown: take-off, landing and return home from the app on a Mavic Air 2 with a Pixel 7, with markers published to an RCH hub. Most other features are implemented but unproven, and no product milestone (2–6) has owner acceptance. Read the [tester notes and known limitations](docs/release/0.1.0-alpha.1.md) before flying; [acceptance.md](docs/specification/acceptance.md) records what is proven and what is not.

What is here:

- `crates/r3akt-uas-app`: thin Rust integration over the shared RCH and LXMF-rs crates: telemetry, markers (placed and received), camera aim point, publication decisions and the simulated aircraft.
- `apps/android`: Kotlin app `org.r3akt.uas` with four variants: `sim` (no DJI SDK, no credentials), `dji4` (DJI SDK 4.18), and the publishing test builds `simNet` and `dji4Net` (built with `-Pr3aktPublish=true`). Cockpit with live video, local flight controls, radar, map, markers, sessions and aircraft media. Kotlin calls Rust through UniFFI-generated bindings.
- `tools/uas-sim`: writes and checks the telemetry fixtures in `fixtures/telemetry/`.
- Nothing connects to hardware or a network by default: only the `dji4` builds talk to an aircraft, and only the `Net` builds publish.

## Build

Requires Rust 1.89.0 (from `rust-toolchain.toml`), JDK 17+, Android SDK 35 and NDK 27.0.12077973 (`ANDROID_HOME` or `~/Android/Sdk`).

```bash
scripts/check.sh
```

```bash
cd apps/android && ./gradlew :app:assembleSimDebug
```

The `dji4` variant (`:app:assembleDji4Debug`) needs a `djiApiKey` Gradle property only when you register with DJI; keep it in `~/.gradle/gradle.properties`.

## Documents

- Plan and handoff: [PLAN.md](docs/goals/bootstrap/PLAN.md), [GOAL.md](docs/goals/bootstrap/GOAL.md)
- Specification: [product](docs/specification/product.md), [architecture](docs/specification/architecture.md), [UAS profile v1](docs/specification/uas-profile-v1.md), [flight and radar](docs/specification/flight-and-radar.md), [video and offline](docs/specification/video-and-offline.md), [acceptance](docs/specification/acceptance.md)
- Integration: [existing system and gaps](docs/integration/existing-system-and-gaps.md), [reuse matrix](docs/integration/reuse-matrix.md), [RCH/REM changes](docs/integration/rch-rem-changes.md), [compatibility matrix](docs/integration/compatibility-matrix.md), [PRE review](docs/integration/pre-review.md)
- Decisions: [ADRs](docs/adr/README.md); repository rules: [AGENTS.md](AGENTS.md)

Additions to the planned tree: `tools/uniffi-bindgen` (pinned binding generator), `docs/evidence/` (captured acceptance evidence) and `docs/release/` (release notes and known limitations).

Original code is EPL-2.0 ([LICENSE](LICENSE)); third-party notices are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
