# Product specification

Status: **specified**. The scaffold implements none of the product features below except where [acceptance.md](acceptance.md) says otherwise.

Source: [PLAN.md](../goals/bootstrap/PLAN.md), section 1, decisions of 4 November 2026. These supersede the earlier observe-only and SDK 5 preferences.

## Requirements

| ID | Requirement |
|---|---|
| **UAS-PRD-001** | R3AKT UAS is an independent Android phone/tablet application with application ID `org.r3akt.uas`. It does not replace REM or RCH. |
| **UAS-PRD-002** | The application is native: Kotlin, reusable Java/XML DJI components, and one thin Rust integration crate. |
| **UAS-PRD-003** | The first release targets DJI Mobile SDK 4. Every claimed aircraft, remote controller and Android combination appears in the [compatibility matrix](../integration/compatibility-matrix.md). |
| **UAS-PRD-004** | The application is a full local flying interface: physical-controller piloting, takeoff, landing, return-to-home, photo capture, video recording and gimbal control. |
| **UAS-PRD-005** | The application shows an aircraft-centred radar with 500 m, 1 km, 2 km and 5 km ranges ([flight-and-radar.md](flight-and-radar.md)). |
| **UAS-PRD-006** | The application exchanges RCH telemetry and REM-compatible markers over Reticulum/LXMF ([uas-profile-v1.md](uas-profile-v1.md)). |
| **UAS-PRD-007** | The application uses the existing TCP/IP and RNode interfaces of the shared Reticulum libraries. |
| **UAS-PRD-008** | Object detection is optional and on-device. Detections become mission markers only after operator confirmation. |
| **UAS-PRD-009** | Video travels over a separate MediaMTX connection, never over the LXMF telemetry path ([video-and-offline.md](video-and-offline.md)). |
| **UAS-PRD-010** | The application works offline and synchronizes selectively when connectivity returns. |
| **UAS-PRD-011** | Releases are signed APKs. Original code is EPL-2.0; upstream notices are kept in [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md). |
| **UAS-PRD-012** | Flight commands belong only to the local DJI interface. RCH carries telemetry and mission information and never commands the aircraft. |

## Out of scope for the first release

Waypoint missions, advanced automated flight, DJI SDK 5, MAVLink (the [rns-mavlink-rs](https://github.com/BeechatNetworkSystemsLtd/rns-mavlink-rs) reference stays a later adapter reference), desktop applications and SRT video.

## Screens

Connection/setup, flight cockpit, map, radar, observations, recordings and session history, settings. The scaffold has only a status screen.
