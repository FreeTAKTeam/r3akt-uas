# Flight application, radar and observations

Status: **specified, not implemented.** Source: [PLAN.md](../goals/bootstrap/PLAN.md), section 3.

## Flight application

| ID | Requirement |
|---|---|
| **UAS-FLT-001** | The cockpit shows live video, aircraft position, altitude with its reference, heading, speed, battery, GPS quality, link status, flight mode, and the camera/gimbal controls the aircraft supports. |
| **UAS-FLT-002** | Each action (takeoff, landing, RTH, capture, record, gimbal) is enabled only when the connected aircraft reports the capability and its current state allows it. |
| **UAS-FLT-003** | Flight interaction works with the hub and the video server unavailable. |
| **UAS-FLT-004** | Commands are never queued for or replayed after reconnection or activity recreation. |
| **UAS-FLT-005** | Rejected or failed actions show the DJI error to the operator; nothing is retried silently. |
| **UAS-FLT-006** | SDK registration, activation, firmware, controller, Android version and native-library requirements are recorded in the [compatibility matrix](../integration/compatibility-matrix.md). |
| **UAS-FLT-007** | FreeTAKUAS components are reused only after their revision and licence are recorded in the [reuse matrix](../integration/reuse-matrix.md); inherited coordinate calculations and hard-coded camera assumptions are reviewed first, and FTS/CoT networking is not imported. |

## Radar

Adapted from the [REM radar acceptance](https://github.com/FreeTAKTeam/rem-tactical-trex2/blob/4b7b96c8e477f0e13bb13008ae359ba0f8345ef3/docs/rem-radar-acceptance.md), centred on the aircraft instead of the phone.

| ID | Requirement |
|---|---|
| **UAS-RAD-001** | A fixed central pointer represents the aircraft; contacts and the compass rose rotate with aircraft heading. |
| **UAS-RAD-002** | Default range is 500 m, selectable to 1, 2 and 5 km, with linear distance rings. |
| **UAS-RAD-003** | At most the 50 nearest authorized contacts are shown, with a visible indication when the list is limited. |
| **UAS-RAD-004** | Canonical symbols and affiliation colours come from `r3akt-map-core` `symbols`. |
| **UAS-RAD-005** | Contacts are selectable, including among overlapping contacts, keep stable identities, and open a separate details view. |
| **UAS-RAD-006** | Details show distance, bearing, source identity, and separate position, contact and reception ages. |
| **UAS-RAD-007** | During transient loss, data stays visible and marked as aged; on access revocation, unauthorized data is cleared. |
| **UAS-RAD-008** | Without aircraft position or heading, the radar says so; phone position or heading is never substituted. |
| **UAS-RAD-009** | Simulated aircraft and contacts are explicitly identified (Telemeter Information sensor text `r3akt-uas simulated aircraft` in the scaffold). |

## Observations and detection

| ID | Requirement |
|---|---|
| **UAS-OBS-001** | Detection is optional, runs on the device, and produces observations, not markers. |
| **UAS-OBS-002** | An observation becomes a mission marker only by operator promotion, through existing marker creation and mission association. |
| **UAS-OBS-003** | Image-space detections get coordinates only when R3S geometry is valid (pose/fix skew, datum, range kind, calibration). |
| **UAS-OBS-004** | A display FOV sector is distinguished from a measured or terrain-intersected footprint; missing range, altitude reference or calibration never yields a precise SPI. |
