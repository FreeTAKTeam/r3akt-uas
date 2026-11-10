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
| G-03 | Standard Telemeter sensor 2 requires altitude, speed, bearing and accuracy and has no datum; unknown values cannot be expressed. Resolution: send aircraft position through the R3S origin position (optional slots, datum); emit sensor 2 only when all slots are known and altitude is MSL (as R3S already requires). | `r3akt-map-core` (existing behaviour) | existing constraint | M2 |
| G-04 | RCH's REM telemetry forwarding builds a fixed argument list (`rem_telemetry_command_fields`, `crates/r3akt-rch-server/src/lib.rs:12522` at `c2bf78a`) and drops `uas`. | RCH | confirmed companion change | M3 |
| G-05 | Post-admission revocation: the pinned LXMF daemon broker cannot cancel a retry it already admitted. | LXMF-rs | upstream gate | M6 |
| G-06 | RCH lacks REM's per-radio pose coalescing policy; high-rate input needs field rate qualification. | RCH / `r3akt-mesh-delivery` | partial | M5 |
| G-07 | R3S `AltitudeDatum` has no take-off-relative value; DJI relative altitude maps to datum `unknown` plus **new** `uas.alt_takeoff_m`. | `r3akt-map-core` | new field | M3 |
| G-08 | LXMF-rs revision split: RCH `c2bf78a` pins `888355c`, RCH HEAD (`fe43a2f`) pins `213af2d`, local LXMF-rs is at `c3ecc56` (plan inspected `6a192cb`). Milestone 2 selects one tested set and records it as qualified. | this repo | decision | M2 |
| G-09 | Android TCP/RNode interface management lives in REM product code; `r3akt-mesh-delivery` excludes it. The owning shared home (LXMF-rs `rns-transport`/`rns-bluetooth` or an extraction) must be agreed before M5; it is not copied here. | LXMF-rs / REM | open | M5 |
| G-10 | DJI SDK 4.18 ships arm64-v8a, armeabi-v7a and x86 native libraries only; the dji4 variant cannot run on x86_64 emulators. | DJI | constraint | M4 (hardware testing) |
| G-11 | Telemeter battery sensor requires a `charging` boolean; aircraft batteries are sent as not charging because there is no unknown value. | `r3akt-map-core` | existing constraint | — (documented) |
| G-12 | Observation altitude and datum have no slot on marker `Position` (lat/lon only). | `r3akt-map-core` | new field | M3 |
