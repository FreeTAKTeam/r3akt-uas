# R3AKT UAS — Specification and Repository Structure

**Intent:** Create an independent R3AKT DJI flying application that reuses existing Rust libraries, RCH mission/telemetry services, and REM-compatible map contracts.
**Current Behavior:** The repository is new and contains no application implementation. FreeTAKUAS, RCH, REM, and LXMF-rs provide the reference behavior and reusable components described below.
**Expected Outcome:** A complete, traceable specification package and buildable Android/Rust skeleton, followed by the separately gated product milestones.
**Target-Perspective Output:** A maintainer can build the simulator variant and follow the specification; a pilot can ultimately fly locally, view aircraft-centred radar, share mission information through RCH, and stream video independently.
**Truth Owner:** DJI owns aircraft state and local flight execution; existing shared crates own protocol, map, sensor, and transport semantics; RCH owns mission authorization and shared coordination; MediaMTX owns media distribution.
**Contract Boundary:** DJI callbacks and actions ↔ native Android ↔ thin Rust application integration ↔ existing shared R3AKT/LXMF libraries ↔ RCH/REM. Video uses a separate media connection.
**Cutover:** Introduce a separately identified application. Reuse and adapt selected FreeTAKUAS components; no replacement of REM or the existing RCH service is required.
**Displaced Path:** FreeTAKUAS FTS-specific networking is replaced by existing R3AKT contracts in the new application. Do not introduce parallel Reticulum or telemetry implementations.
**Value Density:** Start with one simulated aircraft passing through existing telemetry/object contracts and appearing once in RCH and REM.
**Acceptance Evidence:** Clean-checkout builds, shared-codec interoperability, recipient-side map evidence, and separate physical aircraft/radio qualification.
**Evidence Lane:** Documentation/scaffold checks first; simulator integration next; physical device and flight evidence before operational support claims.
**Kill Criteria:** Reject duplicate transport/protocol owners, fabricated location or geometry, silent loss of UAS metadata, live-state rewinds, and unsupported hardware claims. Resolve blocking shared-library gaps upstream.
**Architecture Slice:** Native Android app and DJI adapter, one thin application Rust crate, existing shared libraries, RCH/REM companion changes, and optional MediaMTX deployment.
**Plan Review Gate:** Requires PRE review before application implementation; this planning commit does not claim that gate or any product acceptance gate has passed.

## 1. Product and delivery scope

Create an independent **R3AKT UAS** Android application for DJI aircraft, using FreeTAKUAS components where useful and existing R3AKT Rust libraries wherever they already provide the required functionality.

The agreed first release includes:

- Native Android phone/tablet application: Kotlin, reusable Java/XML DJI components, and a thin Rust integration layer.
- DJI Mobile SDK 4, with a documented aircraft/controller capability matrix.
- Full local flying interface: physical-controller piloting, takeoff, landing, return-to-home, camera capture, recording, and gimbal controls.
- Aircraft-centred radar with 500 m, 1 km, 2 km, and 5 km ranges.
- RCH telemetry and REM-compatible marker exchange over Reticulum/LXMF.
- Existing TCP/IP and RNode interfaces.
- Optional on-device object detection and operator-confirmed observation markers.
- Separate MediaMTX video transport and recording.
- Offline operation and selective synchronization.
- Signed APK distribution; EPL-2.0 for original code, preserving upstream notices.

Waypoint missions, advanced automated flight, SDK 5, MAVLink, desktop applications, and SRT are later milestones. Flight commands belong to the local DJI interface; the RCH integration carries telemetry and mission information.

**The specification/scaffolding phase delivers the full specification package and a buildable repository skeleton.** Companion RCH/REM changes are specified separately; building the complete product follows the milestone plan.

This document records the decisions agreed on 4 November 2026. The explicit full-flying-app and SDK 4 choices supersede the observe-only and SDK 5 preferences in the earlier discussion. The current Git commit publishes this plan only; it does not implement the scaffold.

## 2. Architecture and mandatory library reuse

The application owns DJI integration and its operator experience. Existing libraries retain ownership of networking, serialization, shared map objects, and delivery policy.

| Responsibility | Required implementation |
|---|---|
| Reticulum networking and interfaces | Existing LXMF-rs/Reticulum crates |
| LXMF sending, receiving, propagation, and transport delivery | lxmf-sdk, lxmf-runtime, and their existing dependencies |
| Peer capabilities and delivery-route policy | r3akt-mesh-delivery |
| Mission commands, results, events, and LXMF field mapping | r3akt-profile-rch |
| Shared internal protocol representations | r3akt-protocol |
| Marker identities, symbols, replication, persistence, and Telemeter codecs | r3akt-map-core |
| Sensor pose, manifests, altitude datums, FOV geometry, and origin lifecycle | Existing r3akt-map-core R3S functionality |
| Flight controls, DJI callbacks, video surfaces, and camera interaction | Adapt appropriate FreeTAKUAS/DJI components |
| Media distribution and server recording | MediaMTX |

**Do not introduce a project-specific Reticulum stack, transport-manager crate, wire codec, message envelope, routing policy, or generic delivery queue.** Extend the owning shared library when a reusable capability is missing.

The inspected [R3S contract](https://github.com/FreeTAKTeam/Reticulum-Community-Hub/blob/c2bf78adc2795574a5fb8c39e6cdea7e523b7ef0/docs/r3s-telemetry.md) already covers much of the sensor requirement. The inspected [shared delivery crate](https://github.com/FreeTAKTeam/R3AKTClient/blob/e705533f8549f8556b1939da4731f233ea2e1b89/crates/r3akt-mesh-delivery/src/lib.rs) provides policy; actual transport and propagation remain LXMF-rs responsibilities.

Implementation boundaries:

- **Android:** DJI SDK lifecycle, local flight actions, camera/video, permissions, foreground service, UI, and hardware attachment.
- **Thin application Rust layer:** DJI value normalization, flight-session coordination, publication decisions, and projections for Android. Call shared implementations directly.
- **RCH:** mission/team authorization, shared telemetry and object persistence, dissemination, and authorized media discovery.
- **REM:** existing marker consumption, plus narrowly specified UAS/sensor presentation extensions.
- **MediaMTX:** RTMP ingest, WebRTC/HLS/RTSP distribution, recording, and media status.

Use immutable Git revisions and committed lockfiles. Record inspected baselines separately from the versions subsequently qualified by integration tests. The inspected LXMF-rs checkout and RCH's pinned dependency differ, so dependency selection must follow the tested library set rather than sibling checkout HEADs.

### Inspected reference baselines

These are source-inspection anchors, not a claim of combined runtime or hardware qualification.

| Reference | Inspected baseline |
|---|---|
| RCH | c2bf78adc2795574a5fb8c39e6cdea7e523b7ef0 |
| REM | 3e1e1720e24f5b782d7f7b9e7cab0276eda87b4a |
| REM Tactical / REM Sync | 4b7b96c8e477f0e13bb13008ae359ba0f8345ef3 |
| LXMF-rs local reference | 6a192cbacc00adc26daed32e8af6d697f29b807e |
| RCH-pinned LXMF-rs, including inspected in-process runtime | 888355c580932f886cfbf9f7076758b065a69879 |
| Shared mesh-delivery crate | e705533f8549f8556b1939da4731f233ea2e1b89 |
| FreeTAKUAS | main branch inspected on 4 November 2026; record an immutable revision before importing components |

The [MAVLink reference](https://github.com/BeechatNetworkSystemsLtd/rns-mavlink-rs) was reviewed as a later adapter reference. It must not become a first-release dependency or replace the DJI SDK integration.

## 3. Functional and integration specification

### Flight application

Provide connection/setup, flight cockpit, map, radar, observations, recordings/session history, and settings screens.

The cockpit shows live video, aircraft position, altitude with its reference, heading, speed, battery, GPS quality, link status, flight mode, and available camera/gimbal controls. Actions are enabled according to the connected aircraft's reported capabilities and current state.

Keep flight interaction independent of hub and video-server availability. Commands are never replayed after reconnection. SDK registration, activation, firmware, controller, Android version, and native-library requirements belong in the compatibility matrix.

Reuse FreeTAKUAS's useful DJI integration and presentation components, while replacing its FTS-specific networking with shared R3AKT contracts. Review inherited coordinate calculations and hard-coded camera assumptions before reuse. See the [FreeTAKUAS reference](https://github.com/FreeTAKTeam/FreeTAKUAS).

### RCH UAS Telemetry Profile v1

Define an optional uas extension within the existing normalized telemetry and mission payloads. Version the extension internally; do not wrap standard Telemeter data in a new versioned envelope or allocate another LXMF field.

| Logical profile | Required information and existing representation |
|---|---|
| rch.uas.telemetry.v1 | Aircraft identity/model/callsign, pilot/controller association, position, altitude references, heading/course, horizontal/vertical speed, flight state/mode, battery, GPS quality, link status, and home position. Reuse standard position/battery telemetry and existing mission telemetry commands. |
| rch.uas.sensor.v1 | Camera capabilities, gimbal orientation, FOV, zoom/mode, pointing, observation point, and footprint. Map existing fields to R3S sensor states, manifests, origin positions, and events. |
| rch.uas.observation.v1 | SPI, pilot observations, detections, coordinates and uncertainty, classification/confidence, and media references. Persistent mission objects use existing marker creation and mission association. |
| rch.uas.event.v1 | Takeoff/landing, RTH, battery, communications, GPS, sensor, and capture events, associated with aircraft/session/mission. Reuse existing mission log/event mechanisms. |
| rch.uas.video.v1 | Stream/aircraft/server identities, status, playback protocol and reference, codec/resolution/frame rate/bitrate, recording state, and media references. Reuse approved sensor video references and existing mission metadata. |

The specification must give every field its type, units, optionality, source, timestamp semantics, and exact mapping to existing types.

Required invariants:

- Distinguish the app's authenticated Reticulum identity, aircraft object identity, sensor identity, and pilot identity.
- Establish one canonical aircraft object through the existing authorized object lifecycle. Reuse its identifier for movement, sensors, observations, and media.
- Publish independent aircraft objects through the existing object telemetry stream; never substitute aircraft position for controller position.
- Preserve source timestamps, session identifiers, and sequence numbers. Delayed data cannot replace newer live state.
- Preserve unknown values. DJI takeoff-relative altitude must not be labelled AMSL or AGL.
- Keep heading, course, aircraft attitude, and camera orientation distinct.
- Reuse existing MessagePack serialization and standard telemetry units, including explicit conversions at DJI boundaries.
- Retain standard telemetry and marker compatibility when a receiver lacks UAS-extension support.

A confirmed companion change is required: RCH's REM telemetry forwarding currently uses a fixed field list and would discard uas. Extend that forwarding and the relevant receiving/storage paths with compatibility fixtures.

Shared UAS types and validation belong in the existing shared crates: map/sensor extensions in r3akt-map-core, and mission mapping in r3akt-profile-rch. Do not create another UAS protocol library.

### Radar and observations

Adapt the established [REM radar behaviour](https://github.com/FreeTAKTeam/rem-tactical-trex2/blob/4b7b96c8e477f0e13bb13008ae359ba0f8345ef3/docs/rem-radar-acceptance.md) to aircraft position and heading.

- Fixed central aircraft pointer; contacts and compass rotate around it.
- Default 500 m range, selectable to 1/2/5 km, with linear distance rings.
- Display up to 50 nearest authorized contacts, with a visible indication when results are limited.
- Preserve canonical symbols and affiliation colours.
- Support contact selection, overlapping-contact selection, stable identities, and a separate details view.
- Show distance, bearing, source identity, and distinct position/contact/reception ages.
- Retain visibly aged data during transient loss; clear unauthorized data after access revocation.
- Do not silently substitute phone position or heading.
- Keep simulated contacts explicitly identified.

Object detection is optional and runs locally. Detections remain observations until the operator promotes them to mission markers. Image-space detections receive geographic coordinates only when sufficient geometry exists.

Reuse R3S geometry and its validity rules. Distinguish a display FOV sector from a measured or terrain-intersected footprint; missing range, altitude reference, or calibration must not produce a falsely precise SPI.

### Video, offline operation, and authorization

Use DJI-compatible RTMP ingest into MediaMTX, WebRTC for browser viewing, and HLS/RTSP where appropriate. MediaMTX already provides these distribution and authentication capabilities. See [stream distribution](https://mediamtx.org/docs/features/publish-and-read) and [authentication](https://mediamtx.org/docs/features/authentication).

RCH associates an approved stream reference with aircraft, sensor, and mission. Playback access is resolved through current authorization; shared telemetry never contains publishing credentials or credential-bearing playback URLs.

Support:

- **Broadband:** configurable telemetry, live video, and media synchronization.
- **Constrained mesh:** reduced/coalesced telemetry, prioritized events, and explicitly selected still images; no live video over LoRa.
- **Disconnected:** local session recording, observations, available aircraft recording, and later selective synchronization.

Default publication targets are 1 Hz in flight, up to 2 Hz when explicitly selected, and 0.2 Hz while stationary. Actual transmission remains bounded by interface capacity and existing transport backpressure.

Prioritize events and observations ahead of replaceable pose updates. On reconnection, publish current state first; historical samples remain distinguishable from live state. Reuse shared stores and transport custody mechanisms. Any new local storage is limited to application-specific session/media records, with its necessity documented.

Apply current mission/team rights, protect credentials in Android private storage, redact diagnostic secrets, and enforce bounded storage and payloads. Document R3S's existing post-admission revocation limitation as an upstream qualification gate.

## 4. Repository structure and specification package

The following tree is the target structure. Only the planning documents exist in the initial planning commit.

```text
r3akt-uas/
├── README.md
├── AGENTS.md
├── LICENSE
├── THIRD_PARTY_NOTICES.md
├── Cargo.toml
├── Cargo.lock
├── rust-toolchain.toml
├── apps/
│   └── android/
│       ├── app/                 # Cockpit, radar, map, observations, settings
│       ├── dji4/                # DJI SDK and adapted FreeTAKUAS components
│       ├── native-bridge/       # Generated Rust bindings and Android lifecycle
│       └── gradle/              # Wrapper and pinned dependency catalogue
├── crates/
│   └── r3akt-uas-app/           # Thin application integration; rlib + cdylib
├── tools/
│   └── uas-sim/                # Simulator binary using the same shared crates
├── fixtures/
│   ├── telemetry/
│   ├── radar/
│   └── flights/
├── docs/
│   ├── specification/
│   │   ├── product.md
│   │   ├── architecture.md
│   │   ├── uas-profile-v1.md
│   │   ├── flight-and-radar.md
│   │   ├── video-and-offline.md
│   │   └── acceptance.md
│   ├── integration/
│   │   ├── existing-system-and-gaps.md
│   │   ├── reuse-matrix.md
│   │   ├── rch-rem-changes.md
│   │   └── compatibility-matrix.md
│   ├── adr/
│   └── goals/bootstrap/
│       ├── PLAN.md
│       └── GOAL.md
├── packaging/
│   ├── android/
│   └── mediamtx/
├── scripts/
└── .github/workflows/
```

The specifications are the canonical requirements. Give requirements stable identifiers and map them to milestones and acceptance evidence.

The reuse matrix records each component's owner, immutable source revision, license, supported interface, reuse method, and any required upstream extension. Document existing, partially implemented, and newly required functionality separately.

The buildable skeleton includes:

- Independent application ID org.r3akt.uas.
- Simulator and DJI 4 build variants.
- A generated native bridge exercising real shared-library types/codecs.
- A simulator producing deterministic standard aircraft telemetry fixtures.
- No hardware connection or network publication by default.
- Example configuration without credentials.
- Android/Rust build checks, documentation checks, and dependency-boundary checks.
- A repository rule forbidding duplicate Reticulum and shared-domain implementations.

Use Android API 26 as the minimum and the inspected REM Android toolchain as the initial build baseline. The inspected baseline uses compile/target SDK 35, Android Gradle Plugin 8.7.2, Gradle 8.11.1, and NDK 27.0.12077973. These versions still require qualification with the selected DJI/native dependency set.

Qualify SDK 4/native-library compatibility explicitly; simulator builds must not require DJI registration credentials.

## 5. Implementation sequence and acceptance gates

1. **Specification and scaffold:** write the complete documents, decisions, attribution, dependency pins, Android/Rust skeleton, simulator fixtures, and bootstrap handoff. Run the PRE architecture review before product implementation.
2. **First vertical slice:** simulator → existing LXMF telemetry/object contracts → RCH map → REM. Prove one aircraft identity and correct position without duplicate objects.
3. **UAS and sensor integration:** shared optional profile additions, R3S pose/FOV, observations, event handling, and companion RCH/REM projections.
4. **Android flight experience:** DJI connection, core local controls, video preview, aircraft-centred radar, lifecycle recovery, and capability gating.
5. **Media, detection, and offline qualification:** MediaMTX access, recording references, detection promotion, constrained-network behaviour, and selective synchronization.
6. **Release qualification:** signed APK, model/controller matrix, sustained device tests, installation documentation, and recorded limitations.

Required evidence:

- **Scaffold:** clean-checkout Android simulator build, Rust checks/tests, valid generated bindings, and successful shared-codec round trips.
- **Compatibility:** old telemetry/marker fixtures remain valid; unsupported optional UAS data does not break baseline clients.
- **Identity and ordering:** separate controller/aircraft identities, duplicate suppression, out-of-order samples, restart/session changes, and replay handling.
- **Radar:** cardinal directions, heading wrap, range boundaries, overlap selection, 50-contact limits, stale states, permission revocation, and missing heading.
- **Sensors:** unit conversions, altitude-reference handling, camera/aircraft orientation, pose/fix skew, near-nadir views, unknown range, and invalid geometry.
- **Flight:** supported actions, rejection/error reporting, controller disconnection, app recreation, and absence of command replay.
- **Offline/mesh:** TCP and supported RNode operation, bounded backlog, prioritized events, current-state recovery, and independent telemetry/video failures.
- **Media:** authorized playback, denied/revoked access, recording discovery, and absence of credentials in shared metadata.
- **Hardware:** distinguish SDK-advertised compatibility from physically tested aircraft/controller/Android combinations.

The scaffolding milestone is complete when the specification is traceable, the skeleton builds, and shared-library use is demonstrated. Operational flight and radio support require their separate hardware evidence.
