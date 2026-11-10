# Acceptance and traceability

Every requirement ID defined in the specification appears exactly once in the table below. `scripts/check_docs.py` fails when an ID is defined but untraced, traced but undefined, or referenced in code but undefined.

Status values: **specified** (no implementation), **scaffold-proven** (shown by the evidence named, in the scope named), **implemented but unproven** (code exists, required evidence not yet captured). No product milestone (2–6) has passed. The PRE and POST reviews and their resolutions are recorded in [pre-review.md](../integration/pre-review.md); milestone 2 starts only after the focused PRE on [its task plan](../goals/m2-vertical-slice/PLAN.md).

## Milestones

1. Specification and scaffold (this milestone).
2. First vertical slice: simulator → existing LXMF telemetry/object contracts → RCH map → REM; one aircraft identity, correct position, no duplicate objects.
3. UAS and sensor integration: shared profile additions, R3S pose/FOV, observations, events, RCH/REM projections.
4. Android flight experience: DJI connection, local controls, video preview, radar, lifecycle recovery, capability gating.
5. Media, detection and offline qualification.
6. Release qualification: signed APK, model/controller matrix, sustained device tests, installation documentation, limitations.

Evidence is captured from both the operator side and the receiving client (RCH map, REM). Hardware evidence separates SDK-advertised compatibility from physically tested combinations.

## Scaffold requirements

| ID | Requirement |
|---|---|
| **UAS-SCF-001** | A clean checkout builds the simulator APK with the pinned toolchain (AGP 8.7.2, Gradle 8.11.1, NDK 27.0.12077973, compile/target SDK 35, min SDK 26). |
| **UAS-SCF-002** | The simulator variant needs neither the DJI SDK nor registration credentials. |
| **UAS-SCF-003** | A DJI SDK 4 variant exists and builds. |
| **UAS-SCF-004** | The generated native bridge exercises real shared-library codecs. |
| **UAS-SCF-005** | Nothing connects to hardware or publishes to a network by default. |
| **UAS-SCF-006** | A simulator produces deterministic standard aircraft telemetry fixtures. |
| **UAS-SCF-007** | Example configuration contains no credentials. |
| **UAS-SCF-008** | Android, Rust, documentation and dependency-boundary checks exist and run in CI. |
| **UAS-SCF-009** | A repository rule forbids duplicate Reticulum and shared-domain implementations. |

## Traceability

| ID | Milestone | Evidence | Status |
|---|---|---|---|
| UAS-PRD-001 | 1 | Application ID in `apps/android/app/build.gradle.kts`; installed APK | scaffold-proven |
| UAS-PRD-002 | 1 | Kotlin app + `r3akt-uas-app` crate build | scaffold-proven |
| UAS-PRD-003 | 4, 6 | Compatibility matrix with physically tested rows | specified |
| UAS-PRD-004 | 4 | Hardware flight evidence per action | specified |
| UAS-PRD-005 | 4 | Radar evidence (see Radar) | specified |
| UAS-PRD-006 | 2, 3 | RCH and REM recipient-side map evidence | specified |
| UAS-PRD-007 | 5 | TCP and RNode operation evidence | specified |
| UAS-PRD-008 | 5 | Detection promotion evidence | specified |
| UAS-PRD-009 | 5 | MediaMTX playback evidence | specified |
| UAS-PRD-010 | 5 | Offline/mesh evidence | specified |
| UAS-PRD-011 | 1, 6 | EPL-2.0 `LICENSE`, `THIRD_PARTY_NOTICES.md` (1); signed APK (6) | scaffold-proven (licensing); signing specified |
| UAS-PRD-012 | 2, 4 | No flight command path in RCH integration; review + tests | specified |
| UAS-ARC-001 | 1–5 | Architecture review at each milestone gate | specified |
| UAS-ARC-003 | 1–5 | Architecture review at each milestone gate | specified |
| UAS-ARC-004 | 1–5 | Architecture review at each milestone gate | specified |
| UAS-ARC-005 | 1–5 | Architecture review at each milestone gate | specified |
| UAS-ARC-006 | 1–5 | Architecture review at each milestone gate | specified |
| UAS-ARC-007 | 1–5 | Architecture review at each milestone gate | specified |
| UAS-ARC-008 | 1–5 | Architecture review at each milestone gate | specified |
| UAS-ARC-002 | 1 | `scripts/check_boundaries.py` passes (dependency-level guard; code review covers hand-written codecs) | scaffold-proven (dependencies) |
| UAS-ARC-009 | 1 | Git revisions in `Cargo.toml`; committed `Cargo.lock`, `libs.versions.toml` | scaffold-proven |
| UAS-ARC-010 | 1, 2 | Reuse matrix: inspected column filled (1); qualified column filled after integration tests (2) | scaffold-proven (inspected only) |
| UAS-ARC-011 | 1 | Generated `native-bridge` Kotlin; emulator round trip | scaffold-proven |
| UAS-PRF-001 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | specified |
| UAS-PRF-002 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | specified |
| UAS-PRF-003 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | specified |
| UAS-PRF-005 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | specified |
| UAS-PRF-006 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | specified |
| UAS-PRF-009 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | specified |
| UAS-PRF-010 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | specified |
| UAS-PRF-011 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | specified |
| UAS-PRF-004 | 1, 3 | Unit test `takeoff_relative_and_unknown_altitude_are_not_published` (1); R3S datum mapping (3) | scaffold-proven (Telemeter path) |
| UAS-PRF-007 | 1, 3 | Unit test speed km/h round trip; `fixtures/telemetry/sim-orbit-v1.jsonl` | scaffold-proven (Telemeter path) |
| UAS-PRF-008 | 1, 3 | Fixture decodes with the unmodified shared decoder (1); old-client fixtures (3) | scaffold-proven (Telemeter path) |
| UAS-FLT-001 | 4 | Hardware flight evidence; app-recreation and controller-disconnect tests | specified |
| UAS-FLT-002 | 4 | Hardware flight evidence; app-recreation and controller-disconnect tests | specified |
| UAS-FLT-003 | 4 | Hardware flight evidence; app-recreation and controller-disconnect tests | specified |
| UAS-FLT-004 | 4 | Hardware flight evidence; app-recreation and controller-disconnect tests | specified |
| UAS-FLT-005 | 4 | Hardware flight evidence; app-recreation and controller-disconnect tests | specified |
| UAS-FLT-006 | 4 | Hardware flight evidence; app-recreation and controller-disconnect tests | specified |
| UAS-FLT-007 | 4 | Hardware flight evidence; app-recreation and controller-disconnect tests | specified |
| UAS-RAD-001 | 4 | Radar evidence: cardinal directions, heading wrap, range boundaries, overlap selection, 50-contact limit, stale states, revocation, missing heading | specified |
| UAS-RAD-002 | 4 | Radar evidence: cardinal directions, heading wrap, range boundaries, overlap selection, 50-contact limit, stale states, revocation, missing heading | specified |
| UAS-RAD-003 | 4 | Radar evidence: cardinal directions, heading wrap, range boundaries, overlap selection, 50-contact limit, stale states, revocation, missing heading | specified |
| UAS-RAD-004 | 4 | Radar evidence: cardinal directions, heading wrap, range boundaries, overlap selection, 50-contact limit, stale states, revocation, missing heading | specified |
| UAS-RAD-005 | 4 | Radar evidence: cardinal directions, heading wrap, range boundaries, overlap selection, 50-contact limit, stale states, revocation, missing heading | specified |
| UAS-RAD-006 | 4 | Radar evidence: cardinal directions, heading wrap, range boundaries, overlap selection, 50-contact limit, stale states, revocation, missing heading | specified |
| UAS-RAD-007 | 4 | Radar evidence: cardinal directions, heading wrap, range boundaries, overlap selection, 50-contact limit, stale states, revocation, missing heading | specified |
| UAS-RAD-008 | 4 | Radar evidence: cardinal directions, heading wrap, range boundaries, overlap selection, 50-contact limit, stale states, revocation, missing heading | specified |
| UAS-RAD-009 | 1, 4 | Information sensor text in fixture and emulator screenshot | scaffold-proven (telemetry) |
| UAS-OBS-001 | 3, 5 | Sensor evidence: unit conversions, datum handling, orientation, pose/fix skew, near-nadir, unknown range, invalid geometry | specified |
| UAS-OBS-002 | 3, 5 | Sensor evidence: unit conversions, datum handling, orientation, pose/fix skew, near-nadir, unknown range, invalid geometry | specified |
| UAS-OBS-003 | 3, 5 | Sensor evidence: unit conversions, datum handling, orientation, pose/fix skew, near-nadir, unknown range, invalid geometry | specified |
| UAS-OBS-004 | 3, 5 | Sensor evidence: unit conversions, datum handling, orientation, pose/fix skew, near-nadir, unknown range, invalid geometry | specified |
| UAS-VID-001 | 5 | Media and offline/mesh evidence | specified |
| UAS-VID-002 | 5 | Media and offline/mesh evidence | specified |
| UAS-VID-003 | 5 | Media and offline/mesh evidence | specified |
| UAS-VID-004 | 5 | Media and offline/mesh evidence | specified |
| UAS-VID-005 | 5 | Media and offline/mesh evidence | specified |
| UAS-VID-006 | 5 | Media and offline/mesh evidence | specified |
| UAS-VID-007 | 5 | Media and offline/mesh evidence | specified |
| UAS-VID-008 | 5 | Media and offline/mesh evidence | specified |
| UAS-VID-009 | 5 | Media and offline/mesh evidence | specified |
| UAS-VID-010 | 5 | Media and offline/mesh evidence | specified |
| UAS-VID-011 | 5 | Media and offline/mesh evidence | specified |
| UAS-VID-012 | 5 | Media and offline/mesh evidence | specified |
| UAS-SCF-001 | 1 | Fresh `git init` copy of the working tree with an empty Cargo target: `scripts/check.sh` and both APKs build. Repeat from a `git clone` once committed. | scaffold-proven (clean copy; clone pending commit) |
| UAS-SCF-002 | 1 | Sim APK contains no `dji` entries; built with empty `djiApiKey` | scaffold-proven |
| UAS-SCF-003 | 1 | `:app:assembleDji4Debug` builds; runtime on arm64 hardware not yet shown | implemented but unproven (runtime) |
| UAS-SCF-004 | 1 | Emulator screenshot of shared encode/decode via generated bindings | scaffold-proven |
| UAS-SCF-005 | 1 | Sim merged manifest has no INTERNET/USB permission and runs offline on the emulator. The dji4 merged manifest carries DJI SDK USB features, network-state permissions and services; registration is never called, but dji4 runtime is unproven. | scaffold-proven (sim); dji4 implemented but unproven |
| UAS-SCF-006 | 1 | `uas-sim --check fixtures/telemetry/sim-orbit-v1.jsonl` | scaffold-proven |
| UAS-SCF-007 | 1 | `packaging/` examples contain no credentials | scaffold-proven |
| UAS-SCF-008 | 1 | `scripts/check.sh` passes locally; CI workflow not yet run on GitHub | implemented but unproven (CI) |
| UAS-SCF-009 | 1 | `AGENTS.md` rule + dependency-level boundary check; hand-written codecs are a review item | scaffold-proven |

## Scaffold evidence record (10 November 2026)

- Rust: `cargo fmt --check`, `cargo clippy --workspace --all-targets -D warnings`, `cargo test --workspace` (4 tests) pass with Rust 1.88.0.
- Fixture: `uas-sim --check fixtures/telemetry/sim-orbit-v1.jsonl` → `120 samples match`.
- Android: `:app:assembleSimDebug` (≈1.7 MB, `libr3akt_uas_app.so` for arm64-v8a and x86_64, no DJI entries) and `:app:assembleDji4Debug` (≈90 MB) build.
- Operator view: the simulator APK on an API 35 x86_64 emulator shows sample 0 encoded by the shared `r3akt-map-core` encoder (90 bytes) and decoded by the shared decoder: [screenshot](../evidence/scaffold-sim-emulator-api35.png).
- DJI 4 variant: starting it on the x86_64 emulator fails with `ClassNotFoundException: dji.sdk.sdkmanager.DJISDKManager`, because DJI SDK 4.18 ships no x86_64 native libraries for its protected class loader. Runtime on arm64 hardware: **implemented but unproven**.
- Receiving-client evidence (RCH map, REM) does not apply to the scaffold: nothing is published. It is the milestone 2 gate.
