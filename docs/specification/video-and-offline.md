# Video, offline operation and authorization

Status: **specified, not implemented.** Source: [PLAN.md](../goals/bootstrap/PLAN.md), section 3. MediaMTX references: [publish and read](https://mediamtx.org/docs/features/publish-and-read), [authentication](https://mediamtx.org/docs/features/authentication).

| ID | Requirement |
|---|---|
| **UAS-VID-001** | The aircraft video is published by DJI-compatible RTMP into MediaMTX; browsers view it over WebRTC, other clients over HLS or RTSP. |
| **UAS-VID-002** | RCH associates an approved stream reference with aircraft, sensor and mission; playback access is resolved through current authorization. |
| **UAS-VID-003** | Broadband mode: configurable telemetry rate, live video and media synchronization. |
| **UAS-VID-004** | Constrained-mesh mode: reduced and coalesced telemetry, prioritized events, explicitly selected still images; never live video over LoRa/RNode. |
| **UAS-VID-005** | Default publication targets: 1 Hz in flight, up to 2 Hz when explicitly selected, 0.2 Hz while stationary. Actual sends stay bounded by interface capacity and existing transport backpressure. |
| **UAS-VID-006** | Events and observations are prioritized ahead of replaceable pose updates. On reconnection, current state goes first; historical samples stay distinguishable from live state. |
| **UAS-VID-007** | Shared telemetry never contains publishing credentials or credential-bearing playback URLs. |
| **UAS-VID-008** | Disconnected mode records the session, observations and available aircraft recordings locally, for later selective synchronization. |
| **UAS-VID-009** | Shared stores and transport custody are reused. New local storage is limited to application session/media records, each with its necessity documented. |
| **UAS-VID-010** | Current mission/team rights apply; credentials live in Android private storage; diagnostics redact secrets; storage and payloads are bounded. |
| **UAS-VID-011** | R3S's post-admission revocation limitation (the pinned LXMF daemon cannot cancel a retry it already admitted) is an upstream qualification gate before operational release. |
| **UAS-VID-012** | Telemetry and video fail independently: losing one does not stop the other. |

Example MediaMTX configuration, without credentials: [packaging/mediamtx/mediamtx.example.yml](../../packaging/mediamtx/mediamtx.example.yml).
