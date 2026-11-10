# Milestone 1 reviews

Two independent reviewers ran on 10 November 2026 against `docs/goals/bootstrap/PLAN.md`, using the Krypton plan-review (PRE) and post-implementation (POST) templates. Both returned **partially aligned**. The findings and their resolutions are below.

## PRE: plan plus specification, before product implementation

| # | Severity | Finding | Resolution |
|---|---|---|---|
| 1 | blocker | Milestones 2–6 are goals, not tasks; milestone 2 depends on unmade decisions (LXMF-rs set, identity, aircraft marker binding, network gate) | **Open.** [Milestone 2 task plan](../goals/m2-vertical-slice/PLAN.md) drafted; decisions D-1…D-4 were made on 10 November 2026. It needs a focused PRE before milestone 2 starts. Milestones 3–6 stay gated outlines. |
| 2 | major | No wire carrier is defined for `uas` | Carrier table added to [uas-profile-v1.md](../specification/uas-profile-v1.md). The extension carrier is a pending decision (G-01, blocks M3). |
| 3 | major | Events mapped to field 13; clients send commands on field 9 | Remapped to the field-9 `mission.registry.log_entry.upsert` command. |
| 4 | major | Video mapping violates `SensorManifest::validate` (protocol set, URL form, `sensor_id`) | Mapping corrected; WebRTC reference added to G-02. |
| 5 | major | Observation altitude mapped to marker `Position`, which has only lat/lon | Marked **new** (G-12). |
| 6 | minor | Gimbal angle ranges differ from R3S | Normalization rules added. |
| 7 | minor | R3S origin `label` is hub-asserted only | Model now goes in the marker name and `uas.aircraft.model`. |
| 8 | minor | Battery `charging=false` is fabricated | Documented as encoder constraint G-11 (code comment + gap). |
| 9 | minor | Link to this file was broken | This file. |

## POST: scaffold against the plan

| # | Severity | Finding | Resolution |
|---|---|---|---|
| 1 | major | `check.sh` red (broken link); acceptance claims otherwise | Fixed; `check.sh` re-run green. |
| 2 | major | "Clean clone" claimed with nothing committed | Evidence restated as a clean `git init` copy; a clone build after commit is still pending. |
| 3 | minor | CI DJI-exclusion step could pass vacuously | `set -euo pipefail` + `test -f`. |
| 4 | minor | MediaMTX example open by default | Default-deny `authInternalUsers` (local API only), still credential-free. |
| 5 | minor | Battery charging made up | Same as PRE 8. |
| 6 | minor | UAS-SCF-005 overstated for dji4 | Scoped to sim; dji4 implemented but unproven. |
| 7 | minor | Gradle inputs incomplete; hard-coded target dir | Inputs extended; `CARGO_TARGET_DIR` honoured. |
| 8 | minor | Boundary check is dependency-level only | Evidence wording narrowed. |

## Focused PRE: milestone 2 task plan (10 November 2026)

Two independent reviewers used the Krypton PRE template on [the milestone 2 plan](../goals/m2-vertical-slice/PLAN.md) after decisions D-1…D-4: one on plan readiness, one on whether each task can be built on the shared crates at the pinned revisions (RCH `c2bf78a`, LXMF-rs `888355c`, R3AKTClient `e705533`). Both returned **partially aligned**. Nothing was built or run. "Checked" means the claim was re-read in the pinned source after the review; the rest are the reviewers' claims.

| # | Severity | Finding | Checked | Smallest correction |
|---|---|---|---|---|
| 1 | blocker | Task 4 cannot be built on public APIs. The aircraft position travels as the field-13 event `mission.registry.origin.telemetry.upsert`, not in field 3. `r3akt-map-core::sensor_wire::encode_origin_sensor_stream` returns only the field-3 value and rejects an object with no sensors. No public function builds the complete fields map, and the simulated mark has no slot in it. Assembling the fields here would be a wire codec. | yes (`sensor_wire.rs`) | Owner ruling: add one public encoder for the complete origin fields map to `r3akt-map-core` and pin that RCH revision (this amends D-1's "nothing is re-pinned"; LXMF-rs stays `888355c`), or publish the fix as a field-13 event through `r3akt_profile_rch::encode_events` and defer the REM evidence. Correct the carrier row in `uas-profile-v1.md` either way. |
| 2 | blocker | Transport bring-up has no owner, task or file. `lxmf-runtime` needs the host to build the Reticulum `Transport`, the identity and a tokio runtime. That needs a direct `reticulum-rs-transport` dependency, which `scripts/check_boundaries.py` rejects and which G-09 parks until milestone 5. | yes (`lxmf-runtime/src/config.rs`, `check_boundaries.py`) | Owner ruling: allow `reticulum-rs-transport` and `reticulum-rs-core` in `SHARED`, with this repository limited to construction calls (transport, one TCP client interface, the in-process backend), and amend G-09 for milestone 2's TCP-only use. |
| 3 | major | D-3's idempotency key does not exist on the LXMF path; `Idempotency-Key` is an HTTP header. Over field 9 the handle is a client-chosen `object_destination_hash` plus the `command_id`, and the writer is bound only when that hash is supplied for a new marker. A re-sent create with changed arguments is rejected. | yes (`marker_idempotency.rs`, `durable_broker/envelope.rs`) | The app generates the object id once, stores it with the identity, `command_id` and exact create arguments, and never re-sends a changed create. A reinstall gives a new identity and object id and leaves an orphan marker for the hub operator. Prove it in task 6, not with a local fixture. |
| 4 | major | Hub prerequisites are missing. The app identity needs `mission.content.write` on the hub for the create and for every fix, and team membership for REM push. The hub address and destination hash have no source. | yes (authorization check in `sqlite_commands/sensor_telemetry.rs`) | The app shows its destination hash; the hub grants are setup steps in task 6; hub hash, host and port come from Gradle properties outside the repository, like `djiApiKey`. |
| 5 | major | REM receives a push only when the incoming message carries field 11 with a team the sender belongs to; otherwise REM must pull with `telemetry.collect`. "One aircraft object in REM" is unproven and may need a REM-side change. | yes (`durable_broker/sensor_telemetry.rs`) | State which path task 6 proves. With the field-13-only route, defer the REM evidence explicitly. |
| 6 | major | There is no receive path, so the app cannot read the create result, and a refused fix is skipped silently on the hub. | no | Milestone 2 is send-only and verified on the hub. Record the inbound gap against LXMF-rs. |
| 7 | major | D-4's manifest gate does not cover the dji4 variant, which now carries `INTERNET` for DJI registration. Publisher code in `main` or in the shared library would leave only a runtime flag, the option D-4 rejected. | yes (dji4 manifest) | Publisher entry point only in a `simNet` source set; `simNet` becomes a third flavor reusing the simulator sources; CI asserts the sim manifest has no `INTERNET` and the dji4 APK has no publisher; CI never produces evidence from `simNet`. |
| 8 | major | The simulator cannot pass the restart test. Its sample time is a fixed epoch plus `seq`, `seq` restarts at 0, and the hub ignores older fixes and rejects conflicting equal-time ones. | yes (`sim.rs`, `sensor_store.rs`) | Live publication uses wall-clock time (the fixture is unchanged). Restart = force-stop and relaunch; replay = resend a byte-identical sample; expected = one marker and one origin for the id at the sample position. Name the verifiers. |
| 9 | major | Android build of the pinned set is unproven. `reticulum-rs-transport` brings bundled SQLite, `bzip2`, `tokio-serial` and `if-addrs`; `lxmf-sdk` defaults pull zeromq and rustls; the build script sets only a linker. | yes (the three `Cargo.toml` files) | Task 1 becomes a spike: `lxmf-sdk` with `default-features = false, features = ["std"]`, the build script in its allowed files, and both-ABI builds as evidence. |
| 10 | major | File lists and the task 6 environment are incomplete: the crate's own `Cargo.toml`, `THIRD_PARTY_NOTICES.md` and the closing docs are missing; "Android settings" conflicts with D-4; no one is named to run RCH `c2bf78a` and REM, and the REM revision is not stated. "C-RCH-4 if needed" would stop the milestone, since D-1 fixes `c2bf78a`. | no | Complete the lists, name test cases, name the RCH and REM revisions, who runs them and on which devices. |
| 11 | minor | UAS-PRD-012 is traced to milestone 2 without a task; UAS-PRF-006 needs fields that arrive in milestone 3; UAS-SCF-005 lacks milestone 2. `r3akt-mesh-delivery` and the foreground service are not needed for this slice. G-03 says R3S requires MSL for sensor 2, but `encode_packed` emits it for any datum once four slots are known. The plan header lacks expected-outcome, target-perspective and displaced-path lines. | no | Add a requirements column; move the remainder to milestone 3; drop the unused pin; correct G-03. |

Owner rulings (10 November 2026): finding 1 → **hub first, REM next** (the fix travels as the field-13 origin-fix event; no RCH change, no re-pin; the REM evidence moves to a step 2b with its own plan). Finding 2 → **allowed, construction only**. After the rulings two points were re-read in the pinned source: the hub applies a field-13 origin fix without field 3, and it de-duplicates a byte-identical command by `command_id`. The plan was rewritten as revision 2 with findings 3 to 11 addressed; gaps G-15 to G-17 were recorded and UAS-PRF-010 corrected.

Recommended next gate: a second focused PRE on revision 2. Task 1 runs first as a spike (the pinned set cross-compiles for both ABIs, and a host test sends the create and one fix to a local RCH at `c2bf78a` and the marker moves).

## Second focused PRE: milestone 2 plan, revision 2 (10 November 2026)

Two fresh reviewers repeated the PRE on revision 2. Both returned **partially aligned with no blocker**; every correction is plan text and needs no new owner ruling. Of the first pass, findings 3, 4, 8, 9 and 11 were judged resolved, 5 and 6 explicitly deferred to step 2b, and 7 and 10 still open (now N1, N4, N6 below). Nothing was built or run.

| # | Severity | Finding | Checked | Applied in revision 3 |
|---|---|---|---|---|
| N1 | major | The publish gate leaked: the native library is one unflavored module, so the feature would also reach the dji4 build, and `simNet` could not reuse a source set that defines `Variant`. | yes (`native-bridge/build.gradle.kts`, `build-android-native.sh`) | Feature passed as a script argument and a declared Gradle input; `beforeVariants` makes default variants and `simNet` mutually exclusive; shared simulator source set; CI symbol and manifest assertions. |
| N2 | major | Feature-gated code would escape `check.sh`. | yes (`check.sh`) | Task 1 adds clippy and test passes with the feature and a default-feature dependency assertion. |
| N3 | major | The riskiest assumption (a real send to the hub) was proven last. | — | Task 5 is now a host proof against RCH `c2bf78a`, before any Android work, followed by a POST review. |
| N4 | major | The hub proof had undefined steps: no way to resend a fix, grant command and store query unnamed. | — | Resend-last-fix function in task 5; task 5 records the grant command, the hash it takes and the query. |
| N5 | major | `send` blocks while the cockpit ticks on the main thread; no stop hook. | yes (`lxmf-runtime/src/backend.rs`) | One worker thread owns runtime, transport and backend; stop on leaving the foreground; one source-neutral hook allowed in `main`. |
| C1 | major | The plan had no announce step, and the runtime does not announce, so the hub would act on an unverifiable source. | yes (no announce in `lxmf-runtime`; `send_announce` is public) | Announce added to the wire path, task 5 and R-2; RCH-side observation recorded as G-18. |
| C2 | major | A failed create send would leave every fix skipped silently. | — | No fix until the create's send succeeded in this session; the same bytes are retried. |
| C3 | major | Re-encoding the create changes its bytes (timestamp), and the same `command_id` with different content is refused. | yes (`claim_logical_input`) | The encoded create bytes are stored once and re-sent. |
| N6–N9, C4–C7 | minor | Missing files in task lists; create position undefined; take-off-relative altitude must not be sent as `altM`; `uav` is stored as `drone` (origin kind `mobile`); 30 s clock limit; test should use `sensor_frame::decode`; unused `r3akt-protocol` pin; leftover doc lines tying milestone 2 to REM; hub results are received and dropped. | partly (`symbols.rs`, `sensor.rs`, `encode_commands`) | All applied; G-19 recorded; `acceptance.md` and `rch-rem-changes.md` corrected. |

Also confirmed by the second pass: `encode_commands` and `encode_events` return the complete one-entry fields map, so the application encodes no outer map; a fix-only message needs no field 2, 3 or 11; and nothing in tasks 1 to 5 forces protocol code into this repository.

Both reviewers recommended starting task 1 once the text corrections were applied, with a POST review of the task 5 hub evidence before the Android publisher work.

## POST: milestone 2a task 5 hub evidence (10 November 2026)

One fresh reviewer read tasks 2 to 5 (commits `1978e2c`, `37e4a78`, `9eb6a82`, `b93cddb`), the [hub proof](../evidence/m2a-task5-hub-host-proof.md) and the pinned shared sources, and ran the feature tests. Verdict: **partially aligned, no blocker.** No kill criterion is hit: no protocol code is written here (the only glue is a serde round trip of the shared `SensorEvent` into the shared `EventEnvelope`, as the hub's own producer does), no field 2 is sent, no path leads to a second aircraft object, nothing received reaches a flight command, and default builds carry no publisher. G-20 was confirmed in the pinned source, so the store and API output stands in for the map screenshot.

| # | Severity | Finding | Checked | Resolution |
|---|---|---|---|---|
| F1 | major | `publish_stop` joins the worker, which may be inside a send waiting up to 20 s for the hub's path; called from the UI thread it risks an ANR. | yes (`lxmf-runtime` `backend.rs`, `delivery.rs`, `config.rs`) | Each send now waits at most 5 s for the path (`SEND_PATH_TIMEOUT_MS`); task 6 calls `publish_stop` off the main thread. |
| F2 | major | The create is fire-and-forget: if its one packet is lost, every fix of the session is skipped silently. | yes (`delivery.rs` `ensure_sent`) | The stored create bytes are re-sent on every new connection (the hub de-duplicates by `command_id`); recorded as a known limit. |
| F3 | major | The proof ran with `[hub] announce_interval = 10`, not in the plan. | yes | Recorded as hub test configuration in task 6 (configuration, not an RCH change). |
| F4, F5 | minor | Movement, per-run order and counts, scripts and the API JSON were summarized, not shown. | yes | Evidence addendum: per-run ordered trace, positions 10 s apart, both scripts and the verbatim `/api/markers`. Counts were only taken after the last run (markers are never deleted in this flow). |
| F6 | minor | A panic in shared code would end the worker without setting `Failed`. | yes | Worker body runs under `catch_unwind` and sets `Failed`. |
| F7 | minor | A non-ASCII 32-byte hub hash panicked in the shared hex parser. | yes | Strict ASCII-hex check before use. |
| F8 | minor | A replay counted as a fix; `hub_connected` stayed true after stop. | yes | Replays are not counted; stop clears `hub_connected`. A reconnect inside one 1 s poll is still not seen (documented). |
| F9 | minor | The worker loop has no host test. | — | Accepted; the hub trace covers it. |
| F10 | minor | Speed, course and accuracy are always sent; the simulator has them, a DJI source may not. | yes (`telemetry.rs`) | Noted at G-07: `TelemetrySample` needs optional values before a DJI source publishes (M3). |

## Focused PRE: milestone 2 step 2b plan, revision 1 (10 November 2026)

Two fresh reviewers read [the step 2b plan](../goals/m2-vertical-slice/PLAN-2b.md): one on readiness, one on buildability against RCH `f5822210`, LXMF-rs `9b972f9a` and REM `0cfa422`. Both returned **partially aligned, no blocker**. Nothing was built or run by them. "Checked" means re-read in the pinned source after the review.

| # | Severity | Finding | Checked | Applied in revision 2 |
|---|---|---|---|---|
| B1, B2 | major | Membership is not matched by the app's identity hash: the hub records `reticulumd` announces with no identity hash, so both sender and REM recipients are matched by their `lxmf.delivery` hashes. The planned REM member (identity hash) would relay to nobody, and the identity-hash task is unnecessary. | yes (`r3akt-rch-server/src/lib.rs:2653`, `rem_team_directory.rs:81-110, 201-215`) | Members are added by delivery hash; the identity-hash task is dropped. |
| R1 | major | "Correct position" overstated what REM shows: it settles at the relayed metadata position and does not follow the orbit. | yes (`sensor_telemetry.rs:117-124`, `sensor_relay_metadata.rs`) | Intent, outcome and evidence reworded; kill criterion against presenting REM evidence as tracking; acceptance records "appearance only". |
| R2 | major | The riskiest assumption (REM applies a team-relayed object message) was proven last. | yes (REM `map_marker_receive.rs`, no R3S test in REM or RCH) | New task 0: REM relay spike with a stop rule, before any code. |
| R3, B7 | major | Relay dispatch drops a queued snapshot that is no longer current; at 1 Hz with a lagging dispatch REM may receive nothing. | yes (`outbound.rs:43-60`, `sensor_telemetry.rs:357-389`) | Known limit; the relay trace records dispatch rejections; an interval option only if the proof needs it. |
| R4 | major | Hub database and app store state for the proof were unspecified. | partly | D-8: fresh hub and store, plus one run with the 2a store. |
| R5 | major | Hub and REM setup steps, operator, REM device and address were not named. | yes (`rem_team_directory.rs:45-52, 201-214`) | Task 0 names revisions, emulator address, operator and the exact calls. |
| R6–R16, B3–B6, B8–B10 | minor | Upper-case team UIDs relay to nobody; `publish_start` signature change breaks simNet between tasks; incomplete task 1 file list (README, compatibility matrix); profile carrier table lacks field 11; mixed REM LXMF-rs revision; REM restart limit; UAS-PRF-008 belongs to the REM proof; further owner decisions; spike claims unrecorded; take-off-relative and unknown altitude cases; device clock behind the stored fix; LXMF-rs APIs unchanged (no action); Rust 1.89 compatible; no wire code forced here. | mostly | All applied as plan text (D-7 to D-9, file lists, tests, known limits). |

## Gate status

- Milestone 1 (specification and scaffold): complete. The `ci` workflow builds from a clone on GitHub and passed, and the dji4 variant runs on a Pixel 7 (see the records in [acceptance.md](../specification/acceptance.md)).
- PRE gate for milestone 2a: **passed on 10 November 2026.** The two blockers of the first focused PRE were ruled on by the owner, the second pass found no blocker, and its corrections are applied in plan revision 3. The POST review of the task 5 hub evidence (above) found no blocker; its corrections are applied and task 6 may start.
- Milestone 2b (REM): focused PRE on revision 1 found no blocker; revision 2 applies it. Starting needs the owner's rulings D-5 to D-9. Milestones 3 to 6 remain gated outlines; each needs its own task plan and PRE.
