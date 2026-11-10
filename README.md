# R3AKT UAS

An independent Android DJI flying application for the R3AKT ecosystem: existing Reticulum/LXMF libraries, RCH telemetry and mission services, REM-compatible markers, aircraft-centred radar, and separate MediaMTX video.

**Status: specification and scaffold (milestone 1).** The specification package and a buildable skeleton exist. No product feature is implemented: there is no flight control, radar, network publication or video yet. See [acceptance.md](docs/specification/acceptance.md) for what is proven and what is not.

What the scaffold does:

- `crates/r3akt-uas-app`: thin Rust integration. Encodes and decodes aircraft telemetry with the shared `r3akt-map-core` Telemeter codec (RCH `f5822210`) and provides a deterministic simulated aircraft.
- `apps/android`: Kotlin app `org.r3akt.uas` with a `sim` variant (no DJI SDK, no credentials) and a `dji4` variant (DJI SDK 4.18; registers and connects only when built with a `djiApiKey`; untested with an aircraft). Kotlin calls Rust through UniFFI-generated bindings.
- `tools/uas-sim`: writes and checks the telemetry fixtures in `fixtures/telemetry/`.
- Nothing connects to hardware or a network by default.

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

Additions to the planned tree: `tools/uniffi-bindgen` (pinned binding generator) and `docs/evidence/` (captured acceptance evidence).

Original code is EPL-2.0 ([LICENSE](LICENSE)); third-party notices are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
