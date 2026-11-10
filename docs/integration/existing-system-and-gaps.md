# Existing system and gaps

Source inspection at the baselines in the [reuse matrix](reuse-matrix.md). "Existing" means present at that revision; it does not mean qualified together at runtime.

## Existing

| Capability | Where |
|---|---|
| Telemeter encode (`FIELD_TELEMETRY` 0x02, sensors 1/2/4/15) | `r3akt-map-core::telemetry_encode` — used by the scaffold |
| Telemeter checked decode and normalization | `r3akt-map-core::telemetry` — used by the scaffold |
| Markers, symbols, idempotent creation, replica convergence | `r3akt-map-core::{marker, symbols, idempotency, replica}` |
| R3S sensor states, manifests, origin position with altitude datum, geometry, expiry | `r3akt-map-core::sensor*`; [R3S/1 contract](https://github.com/FreeTAKTeam/Reticulum-Community-Hub/blob/c2bf78adc2795574a5fb8c39e6cdea7e523b7ef0/docs/r3s-telemetry.md) |
| Mission commands/results/events and LXMF field mapping | `r3akt-profile-rch` |
| Protocol envelope primitives | `r3akt-protocol` |
| Peer capability and delivery-route policy | `r3akt-mesh-delivery` (R3AKTClient) |
| LXMF/Reticulum transport, propagation, in-process runtime | LXMF-rs `lxmf-sdk`, `lxmf-runtime`, `rns-transport` |
| RTMP ingest, WebRTC/HLS/RTSP, recording, auth | MediaMTX |
| DJI flight UI and detection components | FreeTAKUAS (Java, SDK 4.16) |

## Partial

| Capability | State |
|---|---|
| Aircraft as an independent object | R3S origin model and field-3 object stream exist; no UAS-specific identity fields. |
| Mission events for flight milestones | Generic mission log/event commands exist; no UAS event vocabulary. |
| Video reference | R3S `VideoDescriptor` carries an approved, non-secret reference; no stream status or format. |

## Gaps

| ID | Gap | Owner | Kind | Blocks |
|---|---|---|---|---|
| G-01 | Wire carrier decision (proposed: an additional Telemeter sensor ID, ≤ 512 bytes) and typed `uas` extension (telemetry/sensor/observation/video fields marked **new** in [uas-profile-v1.md](../specification/uas-profile-v1.md)) with validation | `r3akt-map-core` | new | M3 |
| G-02 | UAS event vocabulary for `mission.registry.log_entry.upsert`, video status/format metadata, and a WebRTC playback reference (R3S `VideoDescriptor` accepts only rtsp/rtmp/http/https) | `r3akt-profile-rch` | new | M3 |
| G-03 | Standard Telemeter sensor 2 requires altitude, speed, bearing and accuracy and has no datum; unknown values cannot be expressed. Resolution: send aircraft position through the R3S origin position (optional slots, datum); emit sensor 2 only when all slots are known and altitude is MSL (the sender must enforce this: `encode_packed` emits sensor 2 for any datum once four slots are known). | `r3akt-map-core` (existing behaviour) | existing constraint | M2 |
| G-04 | RCH's REM telemetry forwarding builds a fixed argument list (`rem_telemetry_command_fields`, `crates/r3akt-rch-server/src/lib.rs:12522` at `c2bf78a`) and drops `uas`. | RCH | confirmed companion change | M3 |
| G-05 | Post-admission revocation: the pinned LXMF daemon broker cannot cancel a retry it already admitted. | LXMF-rs | upstream gate | M6 |
| G-06 | RCH lacks REM's per-radio pose coalescing policy; high-rate input needs field rate qualification. | RCH / `r3akt-mesh-delivery` | partial | M5 |
| G-07 | R3S `AltitudeDatum` has no take-off-relative value; DJI relative altitude maps to datum `unknown` plus **new** `uas.alt_takeoff_m`. `TelemetrySample` also needs optional speed, course and accuracy before a DJI source publishes origin fixes (POST F10). | `r3akt-map-core` | new field | M3 |
| G-08 | LXMF-rs revision split: RCH `c2bf78a` pins `888355c`, RCH HEAD (`fe43a2f`) pins `213af2d`, local LXMF-rs is at `c3ecc56` (plan inspected `6a192cb`). Decided on 10 November 2026: RCH `c2bf78a` with LXMF-rs `888355c`; recorded as qualified only after the milestone 2 proof. Milestone 2a task 1 (10 November 2026): behind the `publish` feature this set resolves to one LXMF-rs revision and compiles unpatched for aarch64 and x86_64 Android with NDK 27, including the bundled SQLite and bzip2 C code; linking and running it are shown in tasks 5 and 6. | this repo | decided | M2 |
| G-09 | Android TCP/RNode interface management lives in REM product code; `r3akt-mesh-delivery` excludes it. The owning shared home (LXMF-rs `rns-transport`/`rns-bluetooth` or an extraction) must be agreed before M5; it is not copied here. Owner ruling R-2 (10 November 2026): for milestone 2 this repository may construct the shared transport with one TCP client interface; nothing more. | LXMF-rs / REM | open | M5 |
| G-10 | DJI SDK 4.18 ships arm64-v8a, armeabi-v7a and x86 native libraries only. On an x86_64 emulator the dji4 variant starts and shows `NO DJI KEY`; registration and aircraft connection there are untested and not expected to work. | DJI | constraint | M4 (hardware testing) |
| G-11 | Telemeter battery sensor requires a `charging` boolean; aircraft batteries are sent as not charging because there is no unknown value. | `r3akt-map-core` | existing constraint | — (documented) |
| G-12 | Observation altitude and datum have no slot on marker `Position` (lat/lon only). | `r3akt-map-core` | new field | M3 |
| G-13 | No public range/bearing helper: `sensor_geometry` keeps its great-circle math private. The cockpit's home distance uses Android's `Location.distanceBetween`; radar contacts need a shared helper before they are drawn. | `r3akt-map-core` | new | M4 (radar contacts) |
| G-14 | 19 of the 36 DJI SDK 4.18 arm64 native libraries are not 16 KB page aligned. Android 17 on a Pixel 7 (4 KB pages) runs them and shows a compatibility warning on debuggable builds; a device with 16 KB pages cannot load them. | DJI | constraint | M6 (device matrix) |
| G-15 | No public client encoder for a complete object message: `sensor_wire::encode_origin_sensor_stream` returns only the field-3 value and rejects an object with no sensors, and the hub's builder for fields 3, 13 and 11 is private. Milestone 2a sends the origin fix as a field-13 event instead; field 3 and REM push wait for this encoder. | `r3akt-map-core` | new | M2b, M3 |
| G-16 | No shared crate exposes a typed builder for `mission.marker.create` arguments; a client writes the argument object by name. | `r3akt-profile-rch` | new | — (accepted for M2a) |
| G-17 | The in-process LXMF runtime at `888355c` offers no inbound message API: `poll_events` returns only events the host records itself. The app cannot read a command result. Confirmed in milestone 2a task 5 ([hub proof](../evidence/m2a-task5-hub-host-proof.md)). | LXMF-rs | confirmed | M2b, M3 |
| G-18 | RCH at `c2bf78a` authorizes the LXMF source hash without reading its daemon's signature-verification result (`signature_status` is never read in RCH source). A client should announce its delivery destination so the daemon can verify it; in the task 5 hub proof the daemon reported every app message `verified`. Still to raise with the RCH maintainers. | RCH | open | — |
| G-19 | At `c2bf78a` a marker created with symbol or category `uav` is stored as `drone`, so its R3S origin kind is `mobile`, not `uav`. Seen in the task 5 hub proof. | RCH | minor | M3 |
| G-20 | At `c2bf78a` `MarkerRecord::api_value` (`r3akt-map-core`) sets `stale_at` equal to `updated_at`, and the RCH webmap hides every marker whose `stale_at` has passed, so no marker stays visible on the RCH map. Milestone 2a's map evidence is therefore the hub store and `/api/markers` output ([hub proof](../evidence/m2a-task5-hub-host-proof.md)). Reported as [RCH #305](https://github.com/FreeTAKTeam/Reticulum-Community-Hub/issues/305); also present on RCH `main` at `184218b`. | RCH | open (reported) | M2a POST review |
