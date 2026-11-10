# Companion RCH and REM changes

Status: **specified, not started.** Each change is made in its own repository under that repository's instructions (`AGENTS.md`), with its own review and fixtures. Nothing here is edited from this repository.

## RCH

| ID | Change | Evidence required |
|---|---|---|
| C-RCH-1 | Carry the optional `uas` object through REM telemetry forwarding. `rem_telemetry_command_fields` (`crates/r3akt-rch-server/src/lib.rs:12522` at `c2bf78a`) copies a fixed list (`uid`, `mission_uid`, `callsign`, `lat`, `lon`, `alt`, `speed`, `course`, `accuracy`, `battery`, `notes`, `source_identity`) and silently drops anything else (gap G-04). | Fixture: telemetry with `uas` forwarded intact; telemetry without `uas` byte-identical to today. |
| C-RCH-2 | Accept, validate and store `uas` on the receive and storage paths using the shared `r3akt-map-core` types (G-01), within existing payload bounds. | Round-trip fixtures; oversized/invalid `uas` rejected without losing the standard telemetry. |
| C-RCH-3 | Map UAS events and video references through `r3akt-profile-rch` (G-02); resolve playback through current authorization; never store credentials in shared metadata. | Authorized/denied/revoked playback tests; metadata scan for secrets. |
| C-RCH-4 | Treat the UAS aircraft as one canonical independent object (R3S origin), never the app identity's own position. | Recipient-side map evidence: one aircraft, correct position, no duplicate. |

## REM

| ID | Change | Evidence required |
|---|---|---|
| C-REM-1 | Keep consuming standard markers/telemetry unchanged when `uas` is present (baseline compatibility, UAS-PRF-008). | Old fixtures still pass; screenshot of an aircraft marker on an unmodified REM build. |
| C-REM-2 | Narrow presentation extension: show aircraft altitude with its datum, heading, and sensor/FOV state from R3S. | REM screenshots from the receiving device. |

Ordering: C-RCH-1 and C-REM-1 before milestone 3 data is published; C-RCH-4 is the milestone 2 gate.
