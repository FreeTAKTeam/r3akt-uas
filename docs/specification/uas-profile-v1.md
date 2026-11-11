# RCH UAS Telemetry Profile v1

Status: **specified, not implemented.** Only the Telemeter mapping in table T1 is exercised by the scaffold (`r3akt-uas-app` encode/decode and `fixtures/telemetry/sim-orbit-v1.jsonl`).

Source: [PLAN.md](../goals/bootstrap/PLAN.md), section 3. Existing contracts referenced here are at the RCH baseline `c2bf78a` (pinned at `f5822210` since milestone 2b, which adds `sensor_wire::encode_object_fields`): the Telemeter codec (`r3akt-map-core` `telemetry`, `telemetry_encode`), the [R3S/1 contract](https://github.com/FreeTAKTeam/Reticulum-Community-Hub/blob/c2bf78adc2795574a5fb8c39e6cdea7e523b7ef0/docs/r3s-telemetry.md) (`r3akt-map-core` `sensor*`), markers (`r3akt-map-core` `marker`), and mission commands/events (`r3akt-profile-rch`).

## Shape

The profile is an optional `uas` object inside the existing normalized telemetry and mission payloads. It has an internal `v` (integer, `1`). It is not a new envelope, does not wrap standard Telemeter data, and allocates no LXMF field. Shared types and validation for it belong in `r3akt-map-core` (map/sensor) and `r3akt-profile-rch` (mission mapping); see gaps G-01 and G-02 in [existing-system-and-gaps.md](../integration/existing-system-and-gaps.md).

## Wire carriers

| Data | Carrier | Status |
|---|---|---|
| Aircraft position | R3S origin position of the canonical object: the `mission.registry.origin.telemetry.upsert` event on LXMF field 13 (`r3akt-map-core` `OriginFixEvent` through `r3akt-profile-rch` `encode_events`) | existing; used by milestone 2a |
| Aircraft sensor readings | LXMF field 3 object telemetry: canonical object hash + Telemeter sensor map (sensor 2 only when complete and MSL) + R3S SID 128 readings | existing; the complete object message (fields 3, 11, 13) is encoded by `r3akt-map-core` `sensor_wire::encode_object_fields` (RCH `f5822210`, G-15 fixed); milestone 2b sends it with no sensors |
| Team scope | LXMF field 11: the RCH canonical team UID (lower-case 32 hex). RCH relays an object update to that team's REM members only when the sender is a member. | existing; used by milestone 2b |
| `uas` telemetry/sensor extension fields marked **new** | Proposed: one additional numeric Telemeter sensor ID in the same field 3 sensor map, allocated and validated by `r3akt-map-core`, at most 512 encoded bytes (the R3S extension budget). Old clients may drop unknown sensor IDs when repacking; standard sensors stay valid. | **decision pending (G-01), blocks M3** |
| Events | LXMF field 9 (`FIELD_COMMANDS`) command `mission.registry.log_entry.upsert` via `r3akt-profile-rch` | existing; UAS vocabulary new (G-02) |
| Observations promoted to markers | Existing marker creation + mission association commands (field 9) | existing |
| Sensor manifest, video reference | R3S `mission.registry.sensor_manifest.upsert` | existing |
| RCH → REM forwarding | `mission.registry.telemetry.upsert` args gain an optional `uas` object | companion change C-RCH-1 (G-04) |

Logical profiles: `rch.uas.telemetry.v1`, `rch.uas.sensor.v1`, `rch.uas.observation.v1`, `rch.uas.event.v1`, `rch.uas.video.v1`. These names identify sections of this document and of fixtures; they are not wire discriminators.

## Invariants

| ID | Requirement |
|---|---|
| **UAS-PRF-001** | The `uas` extension is optional, versioned by its own `v`, carried inside existing payloads, and never wraps standard Telemeter data or allocates an LXMF field. |
| **UAS-PRF-002** | Four identities stay distinct: the app's authenticated Reticulum identity, the aircraft object identity, each sensor identity, and the pilot identity. |
| **UAS-PRF-003** | One canonical aircraft object is created through the existing authorized marker/object lifecycle, and its identifier is reused for movement, sensors, observations and media. |
| **UAS-PRF-004** | Altitude keeps its reference. DJI take-off-relative altitude is never labelled MSL, HAE or AGL; standard Telemeter sensor 2 carries only MSL altitude. |
| **UAS-PRF-005** | Heading, course over ground, aircraft attitude and camera orientation are separate values. |
| **UAS-PRF-006** | Source timestamps, session identifiers and sequence numbers are preserved. Delayed or historical data never replaces newer live state. |
| **UAS-PRF-007** | Encoding uses the shared MessagePack codecs and standard telemetry units, with explicit conversion at the DJI boundary (for example ground speed m/s to Telemeter km/h). |
| **UAS-PRF-008** | A receiver without UAS support still gets valid standard telemetry and markers. |
| **UAS-PRF-009** | Unknown values stay unknown (nil/absent); nothing is fabricated or defaulted to look measured. |
| **UAS-PRF-010** | Aircraft position is published as the R3S origin position of the canonical aircraft object (origin-fix event, LXMF field 13); the field 3 object stream carries that object's sensor readings. It never replaces the controller's own position (field 2). |
| **UAS-PRF-011** | Every field below states type, unit, optionality, source, timestamp semantics and mapping. A field without an existing slot is marked **new** and belongs to the named owning crate. |

Column key: **Opt** R = required, O = optional (absent when unknown). **Time**: which clock the value belongs to.

## T1 `rch.uas.telemetry.v1`

| Field | Type | Unit | Opt | Source | Time | Mapping |
|---|---|---|---|---|---|---|
| aircraft object id | string | — | R | RCH object lifecycle | — | Canonical marker/object id; binary object hash on field 3 |
| aircraft serial | string | — | O | DJI `FlightController.getSerialNumber` | — | **new** `uas.aircraft.serial` (`r3akt-map-core`) |
| aircraft model | string | — | O | DJI `Aircraft.getModel` | — | Marker name text; **new** `uas.aircraft.model` (R3S origin `label` is hub-asserted only) |
| callsign | string | — | R | operator setting | — | Marker name / telemetry `callsign` |
| pilot identity | string | — | O | operator setting | — | **new** `uas.pilot.id`; never the app identity |
| controller identity | hex | — | R | Reticulum identity | — | LXMF source; R3S `writerId` |
| latitude, longitude | f64 | deg WGS-84 | R | DJI aircraft location | fix | Telemeter sensor 2 lat/lon (1e-6 deg); R3S `OriginPosition.lat/lon` |
| altitude | f64 | m | O | DJI / GNSS | fix | R3S `alt_m` + `altitude_datum` (hae/msl/agl/unknown); Telemeter sensor 2 only when MSL |
| take-off-relative altitude | f64 | m | O | DJI `FlightControllerState.altitude` | fix | **new** `uas.alt_takeoff_m`; R3S datum stays `unknown` |
| home position | lat/lon f64, alt f64 | deg, m | O | DJI `getHomeLocation` | set time | **new** `uas.home` with datum |
| heading | f64 | deg true | O | DJI aircraft yaw/compass | sample | **new** `uas.heading_deg` (not course) |
| course over ground | f64 | deg true | O | derived from N/E velocity | fix | Telemeter sensor 2 bearing; R3S `course_deg` |
| ground speed | f64 | m/s | O | DJI velocity N/E | fix | R3S `speed_mps`; Telemeter speed km/h (×3.6) |
| vertical speed | f64 | m/s, up positive | O | DJI velocity D (negated) | fix | **new** `uas.vspeed_mps` |
| attitude pitch/roll/yaw | f64 | deg | O | DJI `getAttitude` | sample | **new** `uas.attitude` |
| flight state / mode | enum string | — | R | DJI flight mode | sample | **new** `uas.flight_mode` |
| battery | f64 | % | O | DJI battery state | sample | Telemeter sensor 4 `(percent, charging)`; the shared encoder has no unknown-charging value, so aircraft batteries are sent as not charging (G-11) |
| GPS quality | int 0–5 + satellites | — | O | DJI `GPSSignalLevel`, satellite count | sample | **new** `uas.gps` (no metric accuracy is invented) |
| horizontal accuracy | f64 | m | O | only when the source states it | fix | Telemeter accuracy; R3S `accuracy_m` |
| link status | enum + quality % | — | O | DJI airlink | sample | **new** `uas.link` |
| session id | uuid | — | R | app | session start | **new** `uas.session` |
| sequence | u64 | — | R | app | — | **new** `uas.seq`, monotonic per session |
| sample time | u64 | epoch s (Telemeter) / ms (R3S) | R | app clock at sample | sample | Telemeter sensor 1; R3S `fix_at_ms` uses the fix time |

Gap G-03: standard sensor 2 needs altitude, accuracy, speed and bearing; when any is unknown the aircraft position is sent only through the R3S origin position, which has optional slots.

## T2 `rch.uas.sensor.v1`

| Field | Type | Unit | Opt | Source | Time | Mapping |
|---|---|---|---|---|---|---|
| sensor id | string | — | R | app (per camera index) | — | R3S `sensorId` (max 4 per origin) |
| camera label/type/model | string | — | O | DJI `Camera.getDisplayName` | — | R3S `SensorManifest.label/sensor_type/model` |
| mount | string | — | O | DJI gimbal index | — | R3S `mount_id` |
| intrinsics | focal/centre px, size px | px | O | calibration only | — | R3S `CameraIntrinsics`; absent when uncalibrated |
| gimbal azimuth | f64 | deg true | O | DJI gimbal yaw (absolute, −180…180) | sample | R3S `azimuth_deg`, normalized to [0, 360) (`rem_euclid(360)`) |
| gimbal elevation | f64 | deg | O | DJI gimbal pitch | sample | R3S `elevation_deg` |
| gimbal roll | f64 | deg | O | DJI gimbal roll (−180…180) | sample | R3S `roll_deg` in (−180, 180]: −180 is sent as 180 |
| HFOV / VFOV | f64 | deg | O | lens table × zoom | sample | R3S `horizontal_fov_deg/vertical_fov_deg` |
| zoom / camera mode | f64 / enum | ×, — | O | DJI camera | sample | **new** `uas.camera.zoom`, `uas.camera.mode` |
| slant range | f64 | m | O | laser rangefinder only | sample | R3S `slant_range_m` + `range_kind` (measured-target / display-limit / unknown) |
| image north | f64 | deg | O | derived | sample | R3S `image_north_deg` |
| observation point (SPI) | lat/lon/alt | deg, m | O | R3S geometry only | sample | Marker (T3); never from a display-limit range |
| footprint | polygon | deg | O | R3S geometry | sample | R3S display sector; terrain footprint is **new** and out of first release |
| sample time | u64 | epoch ms | R | gimbal callback | sample | R3S `sample_at_ms` (pose/fix skew ≤ 2 s) |

## T3 `rch.uas.observation.v1`

| Field | Type | Unit | Opt | Source | Time | Mapping |
|---|---|---|---|---|---|---|
| observation id | uuid | — | R | app | — | Marker idempotency key (`r3akt-map-core` `idempotency`) |
| kind | enum: spi, pilot, detection | — | R | app | — | **new** `uas.observation.kind` |
| position | lat/lon | deg | O | operator or R3S geometry | observed | Marker `Position` (lat/lon only); absent when geometry is insufficient |
| altitude + datum | f64 + hae/msl/agl/unknown | m | O | R3S geometry | observed | **new** `uas.observation.alt_m`, `uas.observation.altitude_datum` (marker `Position` has no altitude) |
| uncertainty | f64 | m (CEP) | O | geometry error model | observed | **new** `uas.observation.uncertainty_m` |
| classification, confidence | string, f64 0–1 | — | O | detector / operator | observed | Marker symbol (`symbols`) + **new** `uas.observation.confidence` |
| aircraft / sensor id | string | — | R | app | — | **new** association fields |
| media reference | string | — | O | recording index | — | Approved non-secret reference only (T5) |
| promotion | bool + operator id | — | R | operator action | promoted | Persistent markers only after promotion, via existing marker creation + mission association |

## T4 `rch.uas.event.v1`

| Field | Type | Unit | Opt | Source | Time | Mapping |
|---|---|---|---|---|---|---|
| event type | enum: takeoff, landing, rth_start, rth_end, battery_low, battery_critical, link_lost, link_restored, gps_degraded, gps_restored, sensor_fault, capture_photo, record_start, record_stop | — | R | DJI callbacks | event | Field 9 command `mission.registry.log_entry.upsert` via `r3akt-profile-rch`; RCH re-emits on field 13 |
| aircraft id, session id, mission uid | string | — | R / R / O | app | — | Event args |
| value | f64 | per type (%, s) | O | DJI | event | Event args |
| event time | u64 | epoch ms | R | source callback | event | Event timestamp; never receive time |

## T5 `rch.uas.video.v1`

| Field | Type | Unit | Opt | Source | Time | Mapping |
|---|---|---|---|---|---|---|
| stream reference | URL without credentials, query or fragment | — | R | RCH approval | — | R3S `VideoDescriptor.endpoint_ref` (`<protocol>://<authority>/...`, ≤ 512 bytes) |
| sensor id | string | — | R | app | — | R3S `VideoDescriptor.sensor_id` (must equal the manifest's `sensor_id`); aircraft linked through the R3S origin id |
| server id | string | — | R | RCH config | — | **new** `uas.video.server` |
| playback protocol | enum: rtsp, https (HLS), webrtc | — | R | RCH | — | R3S `VideoDescriptor.protocol` accepts `rtsp`, `rtmp`, `http`, `https`; HLS → `https`; WebRTC is **new** (G-02) |
| status | enum: offline, live, recording | — | R | MediaMTX status via RCH | status | **new** `uas.video.status` |
| codec, resolution, frame rate, bitrate | string, w×h px, fps, kbit/s | — | O | DJI live-stream settings | — | **new** `uas.video.format` |
| recording reference | string | — | O | MediaMTX recording index | — | Existing mission metadata reference |

Shared telemetry never contains publishing credentials or credential-bearing playback URLs (UAS-VID-007).
