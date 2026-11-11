# DJI field-trial build (`dji4Net`) — task plan (revision 2)

Status: **the focused PRE on revision 1 returned blocked (D-4, shared identity). Every engineering finding is applied here ([PRE record](../../integration/pre-review.md)). The owner ruled on D-14 to D-18 on 10 November 2026, which clears both blockers; task 1 may start.** Owner direction (10 November 2026): be able to try the app with a real drone. Milestone 3's operator markers (merged in PR #6) are proven with the simulator only. Milestone 4 (DJI flight) has not passed: no aircraft or valid key has been used.

**Intent:** the same publisher and marker panel as `simNet`, fed by a real DJI SDK 4 aircraft. A pilot on a field trial then shows up in RCH and REM and places markers from the flight app.
**Expected outcome:**
- Built with the owner's DJI key and hub settings, a `dji4Net` debug APK on the Pixel 7 connects to the aircraft.
- The aircraft appears once in RCH and REM at its real position. Its altitude is take-off-relative, so it travels as datum unknown (UAS-PRF-004).
- Markers placed at the aircraft position appear in REM, and the camera point follows decision D-15.
- Nothing received reaches a flight command (UAS-PRD-012).

**Target-perspective output:** the pilot sees the same cockpit as `dji4`, plus the session log's START PUBLISH and MARKERS. REM and RCH see a real aircraft and its markers, never labelled simulated.
**Truth owner:**
- DJI SDK owns the aircraft values.
- RCH owns the canonical objects and markers.
- The shared crates own encoding, outbox and geometry.
- The owner owns the key, the aircraft and the hub.

**Contract boundary:** `Dji4Sdk.state()` → `normalize_dji` → a new Rust conversion to `TelemetrySample` → the unchanged milestone 2b/3 publisher (`publish_offer_fix`, `marker_*`).
**Cutover:** none. A new test flavour exists only in a `-Pr3aktPublish=true` build. The default `sim` and `dji4` builds are unchanged, and CI keeps asserting they contain no publisher.
**Displaced path:** none.
**Acceptance evidence:**
- A ground test with motors off and the aircraft powered and connected: RCH/REM show the aircraft at its real position, and a marker placed at it.
- Then one short flight within line of sight: the aircraft moves on RCH, and a marker is placed in flight.
- Each step is recorded with screenshots and the hub relay trace.
- The compatibility matrix gets the tested aircraft, remote controller, firmware and phone (UAS-FLT-006).

**Kill criteria:**
- an accuracy, course, altitude datum or gimbal angle that DJI does not report but the app fills in;
- a received message that reaches the DJI interface;
- publishing that blocks the cockpit or the DJI callbacks;
- a default build containing the publisher;
- the key or hub settings in the repository;
- a real aircraft or its markers labelled simulated, or one identity or store shared between flavours;
- a fix time later than when DJI reported the position (a stale position sent as fresh);
- publisher or marker code duplicated between `simNet` and `dji4Net`.

## Findings that shape the plan

- **`TelemetrySample` requires values DJI lacks.** It requires `horizontal_accuracy_m`, `course_deg` and `ground_speed_mps` as plain numbers. DJI SDK 4 reports no horizontal accuracy, only a satellite count and a GPS signal level. Its course is only defined while the aircraft moves (POST F10 of milestone 2a, G-07). These fields must become optional before a DJI fix is published. The shared `OriginPosition` already has them optional.
- **Altitude:** DJI altitude is barometric and relative to take-off. The milestone 2b publisher already sends it as datum unknown with no altitude (`origin_position`; tested). The standard Telemeter (field 3, MSL only) is not used on this path.
- **Camera point inputs:** the shared `ground_point` needs four inputs:
  1. the position accuracy, which DJI does not report;
  2. the absolute camera azimuth: DJI `GimbalState.attitudeInDegrees.yaw` with `yawRelativeToAircraftHeading`, whose meaning per product must be checked on hardware;
  3. error bounds for altitude, angles and ground height;
  4. a take-off ground height, which is 0 for a take-off-relative fix (`TakeoffRelative` model).

  Without (1) and (3) the shared function refuses (`MissingAccuracy`), and the app must not make the values up.
- **Build shape:** the native bridge is one unflavoured module, so a publish build holds only the publisher flavours. Today `beforeVariants` enables only `simNet` in a publish build; `dji4Net` joins it there. `PublishingSimulator` (simNet) holds the start/stop and marker-panel code, which `dji4Net` needs too. It moves to a shared `netCommon` source set instead of being copied.
- **Identity and store (PRE #2):**
  - Every flavour shares the application ID `org.r3akt.uas`; the DJI key is bound to it.
  - `simNet` keeps its identity, aircraft create and outbox in `noBackupFilesDir/publish`. The create bytes stored there name a simulated aircraft and are reused forever (`aircraft.rs`).
  - `dji4Net` therefore uses its own store directory, so it gets a separate identity, create, outbox and marker list. The hub object left by the simNet runs on the Pixel stays a separate, simulated object.
- **Freshness (PRE #3):** `Dji4Sdk` keeps the last flight state with no receive time. It must record when each DJI callback arrived. The fix time is that receive time, and a state older than 2 s is not offered.
- **Rate (PRE #7):** the DJI cockpit polls at 10 Hz. Publication follows UAS-VID-005 (1 Hz moving, 0.2 Hz stationary). The rate is decided in Rust (UAS-ARC-005), not by the poll rate.
- **Build shape (PRE #5):**
  - The DJI source moves from `src/dji4/Variant.kt` into a `dji4Common` source set.
  - The publisher control becomes a wrapper around any `AircraftSource` in `netCommon`; today it is a `SimulatorSource` subclass.
  - `dji4Net` gets the `dji4` manifest entries (key meta-data, USB accessory activity), the `djiApiKey` placeholder, the `HUB_*`/`TEAM_UID` build-config fields and `dji4NetImplementation(project(":dji4"))`.
  - `beforeVariants` enables {`simNet`, `dji4Net`} in a publish build and {`sim`, `dji4`} otherwise.
- **Lifecycle:** `simNet` stops publishing when the cockpit is paused (screen off, another app). On a field trial that would drop the aircraft from the network mid-flight, while the flight itself continues (UAS-FLT-003 holds either way). The hook fires on every activity's pause, and DJI's USB accessory activity opens when the remote controller is plugged in. A controller reconnect would therefore stop publishing silently (PRE #6). The stop must apply to the cockpit activity only.

## Decisions (owner, 10 November 2026)

Rulings: D-14 Mavic Air 2 or Air 2S with the RC-N1, key supplied by the owner; D-15 (a); D-16 as proposed; D-17 as proposed; D-18 approved.

| ID | Decision | Proposal (ruled as above) |
|---|---|---|
| D-14 | Aircraft and key | The owner names the aircraft and remote controller. They must be DJI Mobile SDK 4 products (for example the Mavic 2 or Phantom 4 series, or the Mavic Air 2). The owner also provides an app key for `org.r3akt.uas` in `~/.gradle/gradle.properties`. |
| D-15 | Camera point on DJI | **(a) Recommended for the first trial:** the camera point is refused on DJI with "no position accuracy from DJI"; markers go at the aircraft position. A DJI fix also needs a minimum GPS signal level, which DJI reports as level 0 to 5 (proposal: level 3 or better); below that the fix is not sent. Task 4 records DJI's gimbal yaw meaning and the values the aircraft reports. (b) Later: a reviewed per-model table of DJI's published accuracy figures, with sources cited, as the bound. |
| D-16 | Publishing while the cockpit is paused | **Stop when the cockpit activity pauses (not DJI's USB activity), and show it in the status bar**. A foreground service is milestone 6 work. |
| D-18 | Amend D-4 | D-4 allows only `simNet` to publish. **Proposal:** a `dji4Net` test configuration may publish too, in `-Pr3aktPublish=true` builds only. The default `sim` and `dji4` builds stay publisher-free, and `sim` keeps its manifest without network permission (UAS-SCF-005). |
| D-17 | Hub reachability in the field | TCP to a hub the phone reaches over mobile data or a hotspot (public address or VPN), baked in at build time as for `simNet`. RNode is milestone 5. |

## Tasks

| # | Task | Requirements | Files allowed | Evidence |
|---|---|---|---|---|
| 1 | **Optional accuracy and course; DJI sample and rate:**
- `TelemetrySample.horizontal_accuracy_m` and `course_deg` become `Option`. The R3S origin carries `None` when unknown. `encode_telemetry` refuses a sample without them through a new `UasError` variant, never a default, and the bindings are regenerated.
- New `dji_sample(pose, received_at_ms, now_ms)` in `dji.rs` turns a DJI pose into a `TelemetrySample`:
  - refused without a fix, or when the state is older than 2 s;
  - accuracy absent;
  - course only at 1 m/s or faster;
  - altitude take-off-relative.
- New publication-rate decision per UAS-VID-005: 1 Hz moving, 0.2 Hz stationary (below 1 m/s). | UAS-PRF-004, UAS-PRF-005, UAS-PRF-009, UAS-ARC-005 | `crates/r3akt-uas-app/src/`, `tools/uas-sim/`, `apps/android/app/src/simCommon/`, `docs/integration/existing-system-and-gaps.md` | host tests: DJI pose without fix or stale → no sample; hover → no course and the 0.2 Hz rate; take-off-relative → datum unknown on the wire; `encode_telemetry` refusal; sim output unchanged (fixture) |
| 2 | **Shared source sets:**
- `netCommon` holds the publisher control as a wrapper around any `AircraftSource`: start/stop on the cockpit activity's pause (D-16), report, MARKERS. The store directory is set per flavour.
- `MarkerPanel` moves into `netCommon` too, with an optional camera-pose provider. Without one, PLACE AT CAMERA shows the refusal reason.
- `dji4Common` holds the DJI source moved from `src/dji4`.
- `simNet` keeps only the simulator wiring. | UAS-ARC-005 | `apps/android/app/src/` (`simNet`, `netCommon`, `dji4`, `dji4Common`), `apps/android/app/build.gradle.kts` | `simNet` and `dji4` build and behave as before (Pixel smoke test) |
| 3 | **`dji4Net` flavour:**
- It combines `dji4Common` and `netCommon`, with its own manifest and store directory, enabled only in publish builds (D-18).
- `Dji4Sdk` records each callback's receive time and exposes gimbal yaw, yaw relative to heading and GPS signal level.
- Fixes come from `dji_sample` at the Rust rate. PLACE AT AIRCRAFT is offered; PLACE AT CAMERA follows D-15.
- CI keeps the default-build assertions and also assembles the publish variants, without keys. | UAS-PRD-006, UAS-PRD-012, UAS-VID-005 | `apps/android/app/`, `apps/android/dji4/`, `.github/workflows/ci.yml`, `packaging/android/README.md` | builds; without an aircraft the Pixel shows "aircraft position unknown" for markers, and no fix is sent (no fabricated position) |
| 4 | **Ground test (owner present, motors off):** register with the key and connect the aircraft. The aircraft and an aircraft-position marker appear in RCH/REM. Record the DJI values for D-15 (b): satellite count, GPS signal level, gimbal yaw and its relative-to-heading value. With the hub stopped, the cockpit keeps working and the aircraft reaches the hub after it returns (UAS-FLT-003). | UAS-PRD-006, UAS-FLT-003, UAS-FLT-006 | `docs/evidence/`, `docs/integration/compatibility-matrix.md`, `apps/android/dji4/` (only if a value has to be exposed) | screenshots and relay trace |
| 5 | **Flight test** (owner flies, line of sight): the aircraft moves on RCH and REM; one marker is placed in flight; publishing stops and resumes with the cockpit. **POST review.** | UAS-PRD-006, UAS-FLT-003 | `docs/evidence/`, `docs/specification/acceptance.md` | screenshots, relay trace, pilot notes |

## Known limits

- **Device-dependent:** tasks 4 and 5 need the owner, the aircraft and the key. Until then everything is "implemented but unproven" on hardware.
- **Altitude:** take-off-relative altitude reaches the network as unknown (G-07), so RCH and REM show no altitude for the aircraft.
- **Aircraft (D-14):** Mavic Air 2 and Air 2S use DJI's newer video and remote-controller paths, and SDK 4 supports them only in later 4.x releases. The pinned `dji-sdk:4.18` must show the aircraft and video in task 4 before any compatibility claim is made.
- **Not a release:** this is a debug test build, not the signed release of milestone 6. The hub settings are fixed at build time.
