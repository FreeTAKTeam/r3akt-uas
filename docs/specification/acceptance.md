# Acceptance and traceability

Every requirement ID defined in the specification appears exactly once in the table below. `scripts/check_docs.py` fails when an ID is defined but untraced, traced but undefined, or referenced in code but undefined.

Status values: **specified** (no implementation), **scaffold-proven** (shown by the evidence named, in the scope named), **proven in scope** (a product step's evidence captured, in the scope named; the milestone itself still needs owner acceptance), **implemented but unproven** (code exists, required evidence not yet captured). No product milestone (2–6) has passed. The PRE and POST reviews and their resolutions are recorded in [pre-review.md](../integration/pre-review.md); the focused PRE on the [milestone 2 task plan](../goals/m2-vertical-slice/PLAN.md) passed for step 2a on 10 November 2026.

## Milestones

1. Specification and scaffold (this milestone).
2. First vertical slice: simulator → existing LXMF telemetry/object contracts → RCH map → REM; one aircraft identity, correct position, no duplicate objects. Delivered in two steps by owner ruling of 10 November 2026: 2a the RCH map, 2b REM.
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
| UAS-PRD-004 | 4 | Hardware flight evidence per action | implemented but unproven (take-off, landing, RTH, photo, video preview); recording and gimbal control specified |
| UAS-PRD-005 | 4 | Radar evidence (see Radar) | specified |
| UAS-PRD-006 | 2, 3 | RCH map evidence (step 2a); REM recipient-side evidence (step 2b and milestone 3) | proven in scope (step 2a: RCH store and API, [host](../evidence/m2a-task5-hub-host-proof.md) and [Pixel 7](../evidence/m2a-task6-device-proof.md) hub evidence; the map view at `c2bf78a` is blocked by G-20 and shown against RCH `cc6ca164`, [map evidence](../evidence/m2a-rch-map-cc6ca164.md)); REM: proven in scope for step 2b, appearance only — the aircraft appears once in REM `0cfa422` at the relayed position and does not follow it until REM #341 ([host](../evidence/m2b-task3-host-proof-rem.md), [Pixel 7](../evidence/m2b-task4-device-proof.md)); operator markers (milestone 3): proven in scope by team relay — create, move and delete from the Pixel 7 appear, move and disappear in REM `0cfa422` through RCH `83b5d64a` ([evidence](../evidence/m3-task5-markers-rem-proof.md)) |
| UAS-PRD-007 | 5 | TCP and RNode operation evidence | specified |
| UAS-PRD-008 | 5 | Detection promotion evidence | specified |
| UAS-PRD-009 | 5 | MediaMTX playback evidence | specified |
| UAS-PRD-010 | 3, 5 | Offline/mesh evidence (operator markers through the LXMF-rs custody outbox in milestone 3; constrained mesh in 5) | proven in scope for milestone 3 (a create and a move made on the Pixel 7 with the hub down were delivered once each to RCH and REM after it came back, [evidence](../evidence/m3-task5-markers-rem-proof.md); a delete during an outage is host-proven only, [outbox spike](../evidence/m3-task1-outbox-spike.md)); constrained mesh: specified |
| UAS-PRD-011 | 1, 6 | EPL-2.0 `LICENSE`, `THIRD_PARTY_NOTICES.md` (1); signed APK (6) | scaffold-proven (licensing); signing specified |
| UAS-PRD-012 | 2, 4 | No flight command path in RCH integration; review + tests | proven in scope (step 2a: the publisher has no receive path, G-17; POST review) |
| UAS-ARC-001 | 1–5 | Architecture review at each milestone gate | specified |
| UAS-ARC-003 | 1–5 | Architecture review at each milestone gate | specified |
| UAS-ARC-004 | 1–5 | Architecture review at each milestone gate | specified |
| UAS-ARC-005 | 1–5 | Architecture review at each milestone gate | specified |
| UAS-ARC-006 | 1–5 | Architecture review at each milestone gate | specified |
| UAS-ARC-007 | 1–5 | Architecture review at each milestone gate | specified |
| UAS-ARC-008 | 1–5 | Architecture review at each milestone gate | specified |
| UAS-ARC-002 | 1 | `scripts/check_boundaries.py` passes (dependency-level guard; code review covers hand-written codecs) | scaffold-proven (dependencies) |
| UAS-ARC-009 | 1 | Git revisions in `Cargo.toml`; committed `Cargo.lock`, `libs.versions.toml` | scaffold-proven |
| UAS-ARC-010 | 1, 2 | Reuse matrix: inspected column filled (1); qualified column filled after integration tests (2) | proven in scope (RCH `c2bf78a` and LXMF-rs `888355c` qualified by the step 2a proofs); RCH `f5822210` and LXMF-rs `9b972f9a` qualified by the step 2b proofs |
| UAS-ARC-011 | 1 | Generated `native-bridge` Kotlin; emulator round trip | scaffold-proven; step 2a `simNet` calls the generated `publish_*` bindings on a Pixel 7 |
| UAS-PRF-001 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | specified |
| UAS-PRF-002 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | proven in scope (step 2a, RCH: app identity and object id persist across force-stop and relaunch; [host](../evidence/m2a-task5-hub-host-proof.md) and [Pixel 7](../evidence/m2a-task6-device-proof.md) hub evidence); REM specified |
| UAS-PRF-003 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | proven in scope (step 2a, RCH: one aircraft object per identity after restarts and a replayed fix; [host](../evidence/m2a-task5-hub-host-proof.md) and [Pixel 7](../evidence/m2a-task6-device-proof.md) hub evidence); REM specified |
| UAS-PRF-005 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | specified |
| UAS-PRF-006 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | specified |
| UAS-PRF-009 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | proven in scope (step 2a, RCH: position travels as the shared R3S origin fix; [host](../evidence/m2a-task5-hub-host-proof.md) and [Pixel 7](../evidence/m2a-task6-device-proof.md) hub evidence); REM specified |
| UAS-PRF-010 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | proven in scope (step 2a, RCH: origin fix on field 13 only, no field 2/3/11; [host](../evidence/m2a-task5-hub-host-proof.md) and [Pixel 7](../evidence/m2a-task6-device-proof.md) hub evidence); REM specified |
| UAS-PRF-011 | 2, 3 | Shared-crate fixtures, identity/ordering tests, RCH/REM recipient evidence | specified |
| UAS-PRF-004 | 1, 3 | Unit test `takeoff_relative_and_unknown_altitude_are_not_published` (1); R3S datum mapping (3) | scaffold-proven (Telemeter path); camera point: take-off-relative altitude uses the shared `TakeoffRelative` ground model and is never relabelled (host test, milestone 3 task 6) |
| UAS-PRF-007 | 1, 3 | Unit test speed km/h round trip; `fixtures/telemetry/sim-orbit-v1.jsonl` | scaffold-proven (Telemeter path) |
| UAS-PRF-008 | 1, 3 | Fixture decodes with the unmodified shared decoder (1); old-client fixtures (3) | scaffold-proven (Telemeter path) |
| UAS-FLT-001 | 4 | Hardware flight evidence; app-recreation and controller-disconnect tests | implemented but unproven (video and live values never run with an aircraft); link status and camera/gimbal controls specified |
| UAS-FLT-002 | 4 | Hardware flight evidence; app-recreation and controller-disconnect tests | implemented but unproven (take-off, landing, RTH, photo); record and gimbal specified |
| UAS-FLT-003 | 4 | Hardware flight evidence; app-recreation and controller-disconnect tests | specified |
| UAS-FLT-004 | 4 | Hardware flight evidence; app-recreation and controller-disconnect tests. Pixel 7 without an aircraft: a command is refused at once and nothing is sent after a resume | implemented but unproven (hardware) |
| UAS-FLT-005 | 4 | Hardware flight evidence; app-recreation and controller-disconnect tests. Pixel 7: DJI's registration error and a refused command are shown verbatim and not retried | implemented but unproven (DJI command rejections) |
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
| UAS-OBS-003 | 3, 5 | Sensor evidence: unit conversions, datum handling, orientation, pose/fix skew, near-nadir, unknown range, invalid geometry | partial (milestone 3 task 6): the camera-centre operator point gets coordinates only through the shared `camera_ground::ground_point`, which checks pose/fix skew, datum and staleness; host-tested and shown on the Pixel 7 with simulated values ([evidence](../evidence/m3-task6-camera-point.md)); image-space detections (5) not built |
| UAS-OBS-004 | 3, 5 | Sensor evidence: unit conversions, datum handling, orientation, pose/fix skew, near-nadir, unknown range, invalid geometry | partial (milestone 3 task 6): refusals for unknown altitude reference or ground height, horizon and range are host-tested, and the conditional bound is in the marker notes ([evidence](../evidence/m3-task6-camera-point.md)); display sectors and detections (5) not covered |
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
| UAS-SCF-003 | 1, 4 | `:app:assembleDji4Debug` builds and runs on a Pixel 7 (arm64, Android 17) | scaffold-proven |
| UAS-SCF-004 | 1 | Emulator screenshot of shared encode/decode via generated bindings | scaffold-proven |
| UAS-SCF-005 | 1, 2, 4 | Sim merged manifest has no INTERNET/USB permission and runs offline. Step 2a: the publisher exists only with `-Pr3aktPublish=true`, which enables `simNet` and disables `sim`/`dji4`; CI asserts the default APKs carry no `publish` symbol and the sim APK no INTERNET. The dji4 variant carries INTERNET and the DJI USB accessory activity; built without `djiApiKey` (the default) it never starts the SDK manager and shows `NO DJI KEY` on a Pixel 7. With a key it registers with DJI and listens for an aircraft; it publishes nothing. | scaffold-proven (sim; dji4 without a key) |
| UAS-SCF-006 | 1 | `uas-sim --check fixtures/telemetry/sim-orbit-v1.jsonl` | scaffold-proven |
| UAS-SCF-007 | 1 | `packaging/` examples contain no credentials | scaffold-proven |
| UAS-SCF-008 | 1 | `scripts/check.sh` passes locally; the `ci` workflow passed on GitHub for `bc5c54b`, `79c7c90` (main) and `d61c86b` | scaffold-proven |
| UAS-SCF-009 | 1 | `AGENTS.md` rule + dependency-level boundary check; hand-written codecs are a review item | scaffold-proven |

## Scaffold evidence record (10 November 2026)

- Rust: `cargo fmt --check`, `cargo clippy --workspace --all-targets -D warnings`, `cargo test --workspace` (4 tests) pass with Rust 1.88.0.
- Fixture: `uas-sim --check fixtures/telemetry/sim-orbit-v1.jsonl` → `120 samples match`.
- Android: `:app:assembleSimDebug` (≈1.7 MB, `libr3akt_uas_app.so` for arm64-v8a and x86_64, no DJI entries) and `:app:assembleDji4Debug` (≈90 MB) build.
- Operator view: the simulator APK on an API 35 x86_64 emulator shows sample 0 encoded by the shared `r3akt-map-core` encoder (90 bytes) and decoded by the shared decoder: [screenshot](../evidence/scaffold-sim-emulator-api35.png).
- DJI 4 variant: it failed to start with `ClassNotFoundException: dji.sdk.sdkmanager.DJISDKManager`. The cause recorded here at the time (no x86_64 native libraries) was wrong: a static initializer used a DJI class before the SDK's class loader was installed. Corrected in the milestone 4 record below.
- Receiving-client evidence (RCH map, REM) does not apply to the scaffold: nothing is published. It is the milestone 2 gate (RCH map in step 2a, REM in step 2b).

## Milestone 3 operator-marker evidence record (10 November 2026, partial)

Operator markers at the aircraft position (D-10 (a)) and at the camera point (D-10 (c), task 6, through the shared ground point of RCH #313, with simulated values) are captured; the radar pick (b) is deferred to the radar work of milestone 4. From the `simNet` build on a Pixel 7, a marker placed, moved and deleted in the adapted FreeTAKUAS panel is sent through the LXMF-rs custody outbox with the shared team encoder (RCH `83b5d64a`, fields 9 and 11). RCH relays it to team BLUE, and an unchanged REM `0cfa422` shows, moves and removes it. Changes made while the hub is down are delivered once each, in order, after it returns. Evidence: [relay spike](../evidence/m3-task0-marker-relay-spike.md), [outbox spike](../evidence/m3-task1-outbox-spike.md), [tasks 3 and 4](../evidence/m3-task3-4-markers.md), [re-pin and REM proof](../evidence/m3-task5-markers-rem-proof.md), [camera point](../evidence/m3-task6-camera-point.md); reviews in [pre-review.md](../integration/pre-review.md). Not covered: mission association (UAS-OBS-002), hub-time stamps (UAS-PRF-006), a DJI source.

## Milestone 2b evidence record (10 November 2026)

Step 2b's evidence is captured; the milestone awaits owner acceptance. The same simulated aircraft, published as the shared object message with RCH team BLUE (`encode_object_fields`, fields 3, 11 and 13) from the host tool and from the `simNet` build on a Pixel 7, is relayed by RCH `f5822210` to an unchanged REM `0cfa422` on the emulator. REM shows each aircraft exactly once, at the position relayed with the marker metadata, through publisher restarts, replays, a force-stop and a REM relaunch; it does not follow the orbit until REM #341 (appearance only, D-6). Evidence: [REM spike](../evidence/m2b-task0-rem-relay-spike.md), [re-pin](../evidence/m2b-task1-repin.md), [host proof](../evidence/m2b-task3-host-proof-rem.md), [Pixel 7 proof](../evidence/m2b-task4-device-proof.md); reviews in [pre-review.md](../integration/pre-review.md).

## Milestone 2a evidence record (10 November 2026)

Step 2a's evidence is captured; the milestone awaits owner acceptance. A simulated aircraft, from the host tool and from the `simNet` test build on a Pixel 7, reaches RCH `c2bf78a` through the shared LXMF-rs `888355c` runtime as one `mission.marker.create` (field 9) and R3S origin fixes (field 13). Each identity keeps exactly one marker and one origin after restarts, a force-stop and replayed fixes, and the origin follows the orbit: [host proof](../evidence/m2a-task5-hub-host-proof.md), [device proof](../evidence/m2a-task6-device-proof.md), POST review in [pre-review.md](../integration/pre-review.md). The RCH map view cannot show it at `c2bf78a` (G-20, RCH #305); against RCH `cc6ca164`, where #305 is fixed, the unchanged app's marker is drawn and moves on the map ([map evidence](../evidence/m2a-rch-map-cc6ca164.md)). REM is step 2b.

## Milestone 4 evidence record (10 November 2026, partial)

Update, DJI field-trial build ([evidence](../evidence/dji-net-tasks-1-3.md)): with the owner's app key, DJI registration succeeded on the Pixel 7 (Android 17): "registered, waiting for an aircraft". No aircraft has been connected yet, so everything below about the aircraft stays implemented but unproven.

Milestone 4 has **not** passed: no aircraft or remote controller has been connected, and no valid DJI app key has been used. Device: Pixel 7, Android 17 (API 37), arm64-v8a.

- Rust: `normalize_dji` turns raw DJI flight-controller values into cockpit values (heading 0-360, ground and vertical speed, take-off-relative altitude, position unknown without a fix); 2 unit tests, 6 in the workspace.
- DJI 4 variant, default build (no key): starts, shows `NO DJI KEY`, every value unknown, every action disabled, and the SDK manager is never started: [screenshot](../evidence/m4-dji4-pixel7-no-key.png). The same build also starts on the API 35 x86_64 emulator.
- Simulator variant: runs on the Pixel 7 with the `SIMULATED` badge: [screenshot](../evidence/m4-sim-pixel7.png).
- Test-only build (dummy app key, actions forced on; neither is in the repository):
  - The DJI registration call runs on Android 17 and the SDK's error is shown verbatim ("The app key submitted is invalid…"); the test did not establish whether the SDK reached DJI's servers: [screenshot](../evidence/m4-dji4-pixel7-log-testbuild.png).
  - A tap or a partial slide on the confirm control sends nothing; a full slide sends the command once.
  - Without an aircraft the command is refused at once and shown as `REJECTED · NOT RETRIED` until dismissed: [screenshot](../evidence/m4-dji4-pixel7-confirm-and-rejection-testbuild.png).
  - Nothing is sent after the app is backgrounded and resumed.
- Defect found by this test and fixed: initializing the DJI adapter before the SDK's class loader was installed left its DJI callback classes unusable for the whole process, so every flight command would have failed with `NoClassDefFoundError`, with or without an aircraft. The loader call now lives in `Dji4Loader`, which mentions no DJI type.
- `libr3akt_uas_app.so` is now linked for 16 KB pages; 19 of the 36 DJI arm64 libraries are not (G-14).

**Implemented but unproven** (needs a valid key, a remote controller and an aircraft): successful registration, USB accessory attach, product and component callbacks, live flight values, battery and gimbal values, video decode, take-off, landing, RTH, cancel RTH and photo on an aircraft, DJI command rejections, controller disconnection and activity recreation in flight. Whether DJI needs runtime permissions (location, storage, phone state) that the app does not request is unknown.

Not built: radar contacts (UAS-RAD-002 to UAS-RAD-007), video recording, camera mode, gimbal control, link-status cell, settings, map and observations screens.
