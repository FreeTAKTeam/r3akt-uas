# Reuse matrix

"Inspected" is the revision read during planning. "Qualified" is the revision proven by this repository's integration tests; it stays empty until that evidence exists (UAS-ARC-010).

| Component | Owner | Inspected | Qualified | Licence | Interface used | Reuse method | Upstream extension needed |
|---|---|---|---|---|---|---|---|
| `r3akt-map-core` | RCH | `c2bf78adc2795574a5fb8c39e6cdea7e523b7ef0` | `c2bf78a` (scaffold codec round trip only) | EPL-2.0 | `telemetry_encode::encode_with_information`, `telemetry::decode_field` | Cargo git dependency | G-01, G-07 |
| `r3akt-profile-rch` | RCH | `c2bf78a` | — | EPL-2.0 | mission commands/events | Cargo git dependency (M2) | G-02 |
| `r3akt-protocol` | RCH | `c2bf78a` | — | EPL-2.0 | envelopes | Cargo git dependency (M2) | — |
| `r3akt-mesh-delivery` | R3AKTClient | `e705533f8549f8556b1939da4731f233ea2e1b89` | — | EPL-2.0 | delivery policy | Cargo git dependency (M2) | G-06 |
| `lxmf-sdk`, `lxmf-runtime`, Reticulum crates | LXMF-rs | `6a192cbacc00adc26daed32e8af6d697f29b807e` (local); `888355c580932f886cfbf9f7076758b065a69879` (RCH-pinned) | — | EPL-2.0 OR GPL-2.0-or-later | in-process runtime, TCP/RNode interfaces | Cargo git dependency (M2) | G-05, G-08, G-09 |
| REM | FreeTAKTeam | `3e1e1720e24f5b782d7f7b9e7cab0276eda87b4a` | — | see repository | marker consumption; toolchain baseline | reference only; companion change | [rch-rem-changes.md](rch-rem-changes.md) |
| REM Tactical / REM Sync | FreeTAKTeam | `4b7b96c8e477f0e13bb13008ae359ba0f8345ef3` | — | see repository | radar acceptance behaviour | behaviour adapted, no code copied | — |
| RCH service | FreeTAKTeam | `c2bf78a` | — | EPL-2.0 | telemetry/object ingest, authorization | protocol peer; companion change | G-04 |
| FreeTAKUAS | FreeTAKTeam | `704f4aa3ba127ed4c090da2e30240971b6c55b55` (main, 2022-11-06) | — | MIT; files with TensorFlow example headers (for example `env/Logger.java`) are Apache-2.0 — check each file before import | DJI packaging rules (copied); flight widgets, detection (M4/M5) | adapted Java/XML; FTS/CoT networking excluded | — |
| DJI Mobile SDK 4 | DJI | `com.dji:dji-sdk:4.18` | builds only | DJI SDK licence (proprietary) | SDK manager, flight controller, camera, gimbal | Maven dependency in `dji4` | — |
| UniFFI | Mozilla | `0.28.3` | `0.28.3` (scaffold) | MPL-2.0 | Kotlin bindings | Cargo dependency, generated code | — |
| JNA | JNA project | `5.14.0` | `5.14.0` (scaffold) | Apache-2.0 OR LGPL-2.1 | UniFFI Kotlin runtime | Maven AAR | — |
| MediaMTX | bluenviron | to pin at M5 | — | MIT | RTMP/WebRTC/HLS/RTSP, auth, recording | separate deployment | — |
| rns-mavlink-rs | Beechat | reviewed only | — | — | — | later adapter reference, not a dependency | — |

Only the first row's crate is linked in the scaffold. The FreeTAKUAS packaging rules in `apps/android/app/build.gradle.kts` are the only imported FreeTAKUAS material.
