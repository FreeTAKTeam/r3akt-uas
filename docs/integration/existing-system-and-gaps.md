# Existing system and gaps

Source inspection at the baselines in the [reuse matrix](reuse-matrix.md). "Existing" means present at that revision; it does not mean qualified together at runtime.

## Existing

| Capability | Where |
|---|---|
| Telemeter encode (`FIELD_TELEMETRY` 0x02, sensors 1/2/4/15) | `r3akt-map-core::telemetry_encode` — used by the scaffold |
| Telemeter checked decode and normalization | `r3akt-map-core::telemetry` — used by the scaffold |
| Markers, symbols, idempotent creation, replica convergence | `r3akt-map-core::{marker, symbols, idempotency, replica}` |
| R3S sensor states, manifests, origin position with altitude datum, geometry, expiry | `r3akt-map-core::sensor*`; [R3S/1 contract](https://github.com/FreeTAKTeam/Reticulum-Community-Hub/blob/f5822210da63ce303ee86ffa95d67e0db5e12b3d/docs/r3s-telemetry.md) |
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
| G-08 | LXMF-rs revision split: RCH `c2bf78a` pins `888355c`, RCH HEAD (`fe43a2f`) pins `213af2d`, local LXMF-rs is at `c3ecc56` (plan inspected `6a192cb`). Decided on 10 November 2026: RCH `c2bf78a` with LXMF-rs `888355c`; recorded as qualified only after the milestone 2 proof. Milestone 2a task 1 (10 November 2026): behind the `publish` feature this set resolves to one LXMF-rs revision and compiles unpatched for aarch64 and x86_64 Android with NDK 27, including the bundled SQLite and bzip2 C code; linking and running it are shown in tasks 5 and 6. A later set (RCH `cc6ca164` with LXMF-rs `9b972f9a`) requires Rust 1.89, above this repository's 1.88 pin, so the next re-pin bumps the toolchain. Milestone 2b (decision D-5): re-pinned to RCH `f5822210` with LXMF-rs `9b972f9a` and Rust 1.89; one LXMF-rs revision, both Android ABIs with and without `publish`, and the 2a host behaviour repeated against RCH `f5822210`. | this repo | decided | M2 |
| G-09 | Android TCP/RNode interface management lives in REM product code; `r3akt-mesh-delivery` excludes it. The owning shared home (LXMF-rs `rns-transport`/`rns-bluetooth` or an extraction) must be agreed before M5; it is not copied here. Owner ruling R-2 (10 November 2026): for milestone 2 this repository may construct the shared transport with one TCP client interface; nothing more. | LXMF-rs / REM | open | M5 |
| G-10 | DJI SDK 4.18 ships arm64-v8a, armeabi-v7a and x86 native libraries only. On an x86_64 emulator the dji4 variant starts and shows `NO DJI KEY`; registration and aircraft connection there are untested and not expected to work. | DJI | constraint | M4 (hardware testing) |
| G-11 | Telemeter battery sensor requires a `charging` boolean; aircraft batteries are sent as not charging because there is no unknown value. | `r3akt-map-core` | existing constraint | — (documented) |
| G-12 | Observation altitude and datum have no slot on marker `Position` (lat/lon only). | `r3akt-map-core` | new field | M3 |
| G-13 | No public range/bearing helper: `sensor_geometry` keeps its great-circle math private. The cockpit's home distance uses Android's `Location.distanceBetween`; radar contacts need a shared helper before they are drawn. Fixed upstream by RCH `d7ad6e17` (#317 makes `sensor_geometry::destination` public); pinned since milestone 3 task 6. | `r3akt-map-core` | fixed (pinned) | M4 (radar contacts) |
| G-14 | 19 of the 36 DJI SDK 4.18 arm64 native libraries are not 16 KB page aligned. Android 17 on a Pixel 7 (4 KB pages) runs them and shows a compatibility warning on debuggable builds; a device with 16 KB pages cannot load them. | DJI | constraint | M6 (device matrix) |
| G-15 | No public client encoder for a complete object message: `sensor_wire::encode_origin_sensor_stream` returns only the field-3 value and rejects an object with no sensors, and the hub's builder for fields 3, 13 and 11 is private. Milestone 2a sends the origin fix as a field-13 event instead; field 3 and REM push wait for this encoder. Still missing at RCH `cc6ca164`; requested as [RCH #308](https://github.com/FreeTAKTeam/Reticulum-Community-Hub/issues/308). Fixed upstream by RCH `f5822210` (`sensor_wire::encode_object_fields`); pinned since milestone 2b. | `r3akt-map-core` | fixed (pinned) | M2b, M3 |
| G-16 | No shared crate exposes a typed builder for `mission.marker.create` arguments; a client writes the argument object by name. | `r3akt-profile-rch` | new | — (accepted for M2a) |
| G-17 | The in-process LXMF runtime at `888355c` offers no inbound message API: `poll_events` returns only events the host records itself. The app cannot read a command result. Confirmed in milestone 2a task 5 ([hub proof](../evidence/m2a-task5-hub-host-proof.md)). Unchanged at LXMF-rs `9b972f9a` (the runtime differs only in its `Cargo.toml`). | LXMF-rs | confirmed | M2b, M3 |
| G-18 | RCH at `c2bf78a` authorizes the LXMF source hash without reading its daemon's signature-verification result (`signature_status` is never read in RCH source). A client should announce its delivery destination so the daemon can verify it; in the task 5 hub proof the daemon reported every app message `verified`. Still to raise with the RCH maintainers. | RCH | open | — |
| G-19 | At `c2bf78a` a marker created with symbol or category `uav` is stored as `drone`, so its R3S origin kind is `mobile`, not `uav`. Seen in the task 5 hub proof. | RCH | minor | M3 |
| G-20 | At `c2bf78a` `MarkerRecord::api_value` (`r3akt-map-core`) sets `stale_at` equal to `updated_at`, and the RCH webmap hides every marker whose `stale_at` has passed, so no marker stays visible on the RCH map. Milestone 2a's map evidence is therefore the hub store and `/api/markers` output ([hub proof](../evidence/m2a-task5-hub-host-proof.md)). Reported as [RCH #305](https://github.com/FreeTAKTeam/Reticulum-Community-Hub/issues/305) and fixed upstream in RCH `cc6ca164`; against that hub the marker shows and moves on the map ([map evidence](../evidence/m2a-rch-map-cc6ca164.md)). The pinned `c2bf78a` still has it until the next re-pin. Pinned since milestone 2b (`f5822210` contains the fix). | RCH | fixed (pinned) | next re-pin |
| G-21 | At RCH `f5822210` `POST /api/r3akt/teams` with `{"color":"BLUE"}` and no team name creates a team with a random UID: `canonical_team_from_team_args` treats the missing name as non-canonical and skips the colour. The canonical team must be created with its `uid`; field 11 and membership must use that UID ([2b spike](../evidence/m2b-task0-rem-relay-spike.md)). | RCH | minor | — |
| G-22 | RCH's canonical team UID table (needed for field 11) is private to the hub core; a client can only check that a team UID is 32 hex, and a non-canonical UID relays to nobody without an error the client sees. Requested as [RCH #312](https://github.com/FreeTAKTeam/Reticulum-Community-Hub/issues/312). Fixed upstream by RCH `d7ad6e17` (#318, `r3akt_profile_rch::teams::is_canonical_team_uid`); since milestone 3 task 6 the app refuses a non-canonical team at start. | RCH | fixed (pinned) | — |
| G-23 | The shared command encoder `encode_commands` wrote field 9 only, and RCH relays a command to the team only when it carries field 11 ([marker relay spike](../evidence/m3-task0-marker-relay-spike.md)). Requested as [RCH #315](https://github.com/FreeTAKTeam/Reticulum-Community-Hub/issues/315). Fixed upstream by RCH `83b5d64a` (`encode_commands_for_team`, merge of RCH #316), and pinned since milestone 3 task 2. | `r3akt-profile-rch` | fixed (pinned) | M3 |
| G-24 | RCH applies a team-scoped command (field 11) only when the sender is a member of that team: a sender with `mission.content.write` that is not a team member gets no marker and no answer. The app cannot see the drop (G-17). Reproduced on the local hub (RCH `83b5d64a`, 11 November 2026): `UAS Alpha-1` from a granted non-member was dropped, then created once the identity was added to team BLUE. Operators must add each app identity to the team as well as grant it. | RCH | constraint (operator setup) | — |
