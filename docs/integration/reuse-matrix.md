# Reuse matrix

"Inspected" is the revision read during planning. "Qualified" is the revision proven by this repository's integration tests; it stays empty until that evidence exists (UAS-ARC-010).

| Component | Owner | Inspected | Qualified | Licence | Interface used | Reuse method | Upstream extension needed |
|---|---|---|---|---|---|---|---|
| `r3akt-map-core` | RCH | `d7ad6e17719baf0ea7221343c0cd8beb8ab806ab` (M3 camera point); `83b5d64aac69347c997ac9e88d84242eb87a1814` (M3 markers); `f5822210da63ce303ee86ffa95d67e0db5e12b3d` (M2b); `c2bf78a` before | `c2bf78a` (codec round trip; R3S origin fix to the hub, M2a); `f5822210` (object message and REM relay, host and Pixel 7, M2b); `83b5d64a` (marker records and store, Pixel 7 to REM, M3); `d7ad6e17` (`camera_ground::ground_point`, host tests and Pixel 7 to REM, M3) | EPL-2.0 | `telemetry_encode::encode_with_information`, `telemetry::decode_field` | Cargo git dependency | G-01, G-07 |
| `r3akt-profile-rch` | RCH | `d7ad6e17` (M3 camera point); `83b5d64a` (M3 markers); `f5822210` (M2b); `c2bf78a` before | `c2bf78a` (field 9 and 13 encoders to the hub, M2a); `f5822210` (object message and REM relay, host and Pixel 7, M2b); `83b5d64a` (`encode_commands_for_team`, Pixel 7 to REM, M3); `d7ad6e17` (`teams::is_canonical_team_uid`, host test, M3) | EPL-2.0 | mission commands/events | Cargo git dependency (M2a) | G-02, G-16 |
| `r3akt-protocol` | RCH | `c2bf78a` | — | EPL-2.0 | envelopes | Cargo git dependency (not needed for M2a) | — |
| `r3akt-mesh-delivery` | R3AKTClient | `e705533f8549f8556b1939da4731f233ea2e1b89` | — | EPL-2.0 | delivery policy | Cargo git dependency (not needed for M2a) | G-06 |
| `lxmf-sdk`, `lxmf-runtime`, Reticulum crates | LXMF-rs | `6a192cbacc00adc26daed32e8af6d697f29b807e` (local); `888355c580932f886cfbf9f7076758b065a69879` (RCH `c2bf78a` pin); `9b972f9a8d3a7949eae89bc78f7a9691f06f3ed1` (RCH `f5822210`, `83b5d64a` and `d7ad6e17` pin, M2b and M3) | `888355c` (in-process runtime, one TCP client, host and Pixel 7, M2a); `9b972f9a` (host and Pixel 7, M2b; custody outbox `LocalPropagation`, Pixel 7, M3) | EPL-2.0 OR GPL-2.0-or-later | in-process runtime with one TCP client (M2a); RNode interfaces later | Cargo git dependency (M2a, behind the `publish` feature) | G-05, G-08, G-09, G-17 |
| REM | FreeTAKTeam | `3e1e1720e24f5b782d7f7b9e7cab0276eda87b4a` | — | see repository | marker consumption; toolchain baseline | reference only; companion change | [rch-rem-changes.md](rch-rem-changes.md) |
| REM Tactical / REM Sync | FreeTAKTeam | `4b7b96c8e477f0e13bb13008ae359ba0f8345ef3` | — | see repository | radar acceptance behaviour | behaviour adapted, no code copied | — |
| RCH service | FreeTAKTeam | `c2bf78a`; `f5822210` (M2b); `83b5d64a` (M3) | — | EPL-2.0 | telemetry/object ingest, authorization | protocol peer; companion change | G-04 |
| FreeTAKUAS | FreeTAKTeam | `704f4aa3ba127ed4c090da2e30240971b6c55b55` (main, 2022-11-06) | — | MIT; files with TensorFlow example headers (for example `env/Logger.java`) are Apache-2.0 — check each file before import | DJI packaging rules (copied); flight widgets, detection (M4/M5) | adapted Java/XML; FTS/CoT networking excluded | — |
| DJI Mobile SDK 4 | DJI | `com.dji:dji-sdk:4.18` | builds only | DJI SDK licence (proprietary) | SDK manager, flight controller, camera, gimbal | Maven dependency in `dji4` | — |
| UniFFI | Mozilla | `0.28.3` | `0.28.3` (scaffold) | MPL-2.0 | Kotlin bindings | Cargo dependency, generated code | — |
| JNA | JNA project | `5.14.0` | `5.14.0` (scaffold) | Apache-2.0 OR LGPL-2.1 | UniFFI Kotlin runtime | Maven AAR | — |
| MediaMTX | bluenviron | to pin at M5 | — | MIT | RTMP/WebRTC/HLS/RTSP, auth, recording | separate deployment | — |
| rns-mavlink-rs | Beechat | reviewed only | — | — | — | later adapter reference, not a dependency | — |

Only the first row's crate is linked in the scaffold. The FreeTAKUAS packaging rules in `apps/android/app/build.gradle.kts` are the only imported FreeTAKUAS material.
