# Architecture specification

Source: [PLAN.md](../goals/bootstrap/PLAN.md), section 2. Decisions: [ADR 0001](../adr/0001-native-android-thin-rust.md), [ADR 0002](../adr/0002-dji-sdk-4.md), [ADR 0003](../adr/0003-reuse-shared-libraries.md), [ADR 0005](../adr/0005-uniffi-native-bridge.md).

## Contract boundary

```text
DJI SDK 4 callbacks/actions ─► Android (Kotlin, dji4 module)
                                   │ generated UniFFI bindings (native-bridge)
                                   ▼
                     r3akt-uas-app (thin Rust integration)
                                   │ direct calls, no re-implementation
                                   ▼
   r3akt-map-core · r3akt-profile-rch · r3akt-protocol · r3akt-mesh-delivery · lxmf-sdk/lxmf-runtime
                                   │ Reticulum/LXMF (TCP/IP, RNode)
                                   ▼
                               RCH ◄──► REM
Camera RTMP ─────────────────► MediaMTX ─► WebRTC/HLS/RTSP viewers   (separate media path)
```

## Requirements

| ID | Requirement |
|---|---|
| **UAS-ARC-001** | Ownership follows the table below. A component outside its row must not implement that row's responsibility. |
| **UAS-ARC-002** | This repository contains no Reticulum stack, transport manager, wire codec, message envelope, routing policy or generic delivery queue. `scripts/check_boundaries.py` enforces the dependency side of this rule. |
| **UAS-ARC-003** | A missing reusable capability is added to its owning shared library, recorded in [existing-system-and-gaps.md](../integration/existing-system-and-gaps.md), and consumed here at an immutable revision. |
| **UAS-ARC-004** | Android owns DJI SDK lifecycle, local flight actions, camera/video, permissions, the foreground service, UI and hardware attachment. |
| **UAS-ARC-005** | The Rust crate `r3akt-uas-app` owns only DJI/simulator value normalization, flight-session coordination, publication decisions and projections for Android. It calls shared implementations directly. |
| **UAS-ARC-006** | RCH owns mission/team authorization, shared telemetry and object persistence, dissemination and authorized media discovery. |
| **UAS-ARC-007** | REM keeps its existing marker consumption and receives only narrowly specified UAS/sensor presentation extensions ([rch-rem-changes.md](../integration/rch-rem-changes.md)). |
| **UAS-ARC-008** | MediaMTX owns RTMP ingest, WebRTC/HLS/RTSP distribution, recording and media status. |
| **UAS-ARC-009** | Every shared dependency is pinned to an immutable Git revision; `Cargo.lock` and the Gradle version catalogue are committed. |
| **UAS-ARC-010** | Inspected baselines and integration-qualified versions are recorded separately ([reuse-matrix.md](../integration/reuse-matrix.md)). Dependency selection follows the tested library set, not sibling checkout HEADs. |
| **UAS-ARC-011** | Kotlin reaches Rust only through UniFFI-generated bindings; no hand-written JNI. |

## Ownership

| Responsibility | Owner |
|---|---|
| Reticulum networking and interfaces | LXMF-rs Reticulum crates |
| LXMF send/receive, propagation, transport delivery | `lxmf-sdk`, `lxmf-runtime` |
| Peer capabilities and delivery-route policy | `r3akt-mesh-delivery` (R3AKTClient) |
| Mission commands, results, events, LXMF field mapping | `r3akt-profile-rch` (RCH) |
| Shared internal protocol representations | `r3akt-protocol` (RCH) |
| Marker identities, symbols, replication, persistence, Telemeter codecs | `r3akt-map-core` (RCH) |
| Sensor pose, manifests, altitude datums, FOV geometry, origin lifecycle | `r3akt-map-core` R3S modules (`sensor*`) |
| Flight controls, DJI callbacks, video surfaces, camera interaction | this repository, adapting FreeTAKUAS/DJI components |
| Media distribution and server recording | MediaMTX |

## Scaffold state

Present: `r3akt-uas-app` links `r3akt-map-core` at the RCH baseline and exposes Telemeter encode/decode and a deterministic simulator through UniFFI; the Android `sim` and `dji4` variants build. Not present: any LXMF/Reticulum dependency, publication, persistence or DJI registration. Those arrive with milestone 2 and later.
