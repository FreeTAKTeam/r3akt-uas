# DJI field-trial build, tasks 1 to 3: DJI fix, shared publisher, `dji4Net` (no aircraft yet)

Recorded 10 November 2026 for branch `feat/dji-net`; plan: [DJI field-trial build](../goals/dji-net/PLAN.md), revision 2, with owner rulings D-14 to D-18.

## Task 1: Rust

- **Optional values:** `TelemetrySample.horizontal_accuracy_m` and `course_deg` are optional.
  - The standard Telemeter (`encode_telemetry`) refuses a sample without them (`UasError::MissingValue`). It never substitutes a default.
  - The R3S origin carries `None`.
  - The simulator fixture is unchanged (`check.sh`: 120 samples match).
- **Course:** `normalize_dji` gives a course over ground only at 1 m/s or faster.
- **`dji_sample`:** turns a DJI pose into a fix only when three conditions hold:
  - GPS signal level is 3 or better (D-15);
  - the state was received at most 2 s ago;
  - DJI reports a position and an altitude.

  Its fix time is the receive time, the altitude stays take-off-relative, accuracy is absent, and the sample is never marked simulated.
- **Publication rate:** `publish_offer_fix` sends 1 Hz while moving and 0.2 Hz while stationary (below 1 m/s; UAS-VID-005), however often the source polls. Every offered fix still becomes the marker placement position.
- **Tests:** 25 in the crate, 6 new:
  - hovering has no course;
  - the DJI sample is real and take-off-relative with no accuracy;
  - refusals for weak GPS, unknown GPS, stale state, future state and no fix;
  - Telemeter refusal without course or accuracy;
  - the 1 Hz / 0.2 Hz rate.

## Tasks 2 and 3: Android

- **`netCommon`:** `PublishingSource`, a wrapper around any `AircraftSource`, and `MarkerPanel`.
  - Publishing stops when the cockpit activity pauses, not DJI's USB activity, and the status shows `PUBLISH STOPPED` (D-16).
  - Each flavour has its own store directory: `publish` for `simNet` and `publish-dji4` for `dji4Net`.
  - Without a camera-pose provider, PLACE AT CAMERA is refused with the reason.
- **`dji4Common`:** `Dji4Source`, moved from the `dji4` variant, with `fix()` through `dji_sample`.
- **`Dji4Sdk`:** records the receive time of each flight-state callback, the GPS signal level, and the gimbal yaw both absolute and relative to heading. It shows them in the session log so the ground test can record them.
- **`dji4Net`:** `dji4Common` plus `netCommon`, with the `dji4` manifest. It is enabled only with `-Pr3aktPublish=true` (D-18). The default builds are unchanged.
- **CI:** CI now also builds `simNet` and `dji4Net` without a key or hub settings, after the default-build checks.

| APK | Publisher symbols | INTERNET | DJI key entry and USB activity |
|---|---|---|---|
| `sim` | 0 | no | no |
| `dji4` | 0 | yes (DJI registration) | yes |
| `simNet` | yes | yes | no |
| `dji4Net` | yes | yes | yes |

## Pixel 7 smoke tests

| Build | Result |
|---|---|
| `simNet` | Fixes went out at about 1 Hz (17 in about 15 s). PLACE AT CAMERA put Alpha-2 at the simulated target in REM, under the same identity as before. After leaving the cockpit and returning, the status read `IN FLIGHT · PUBLISH STOPPED` ([screenshot](dji-net-simnet-publish-stopped.png)). |
| `dji4Net`, owner's key, no aircraft | DJI registration succeeded: "registered, waiting for an aircraft" ([screenshot](dji-net-pixel-registered-no-aircraft.png)). The app has its own identity, destination `84b40d98…`, separate from simNet's `8db22674…`. With no aircraft the hub received nothing from it (0 messages): no position was invented. PLACE AT AIRCRAFT was refused, "aircraft position unknown" ([screenshot](dji-net-pixel-marker-refused.png)), and PLACE AT CAMERA was refused, "no camera point from this aircraft source". |

## Debug build against the R3AKT Server

- **Hub:** REM's default "R3AKT Server" at `134.122.46.48:37428`, hub destination `c4de028671f01d9649aabb85e73b50a4`, team BLUE.
- **Builds:** `simNet` and `dji4Net` were built for it, and the port is reachable.
- **Simulator check from the Pixel:**
  - result: hub connected; the aircraft create was sent; 40 fixes went to LXMF in about 40 s at 1 Hz;
  - identity: the simNet identity `8db22674…`, labelled SIMULATED;
  - after the check, publishing was stopped and `dji4Net` was reinstalled.
- **Not verified:** whether that hub applied the fixes. That needs the hub operator to add the app destination to team BLUE and grant `mission.content.write` (`8db22674…` for simNet, `84b40d98…` for dji4Net). Without that, the hub does not show or relay the aircraft.

## Not yet shown

The ground test (task 4) and the flight test (task 5) need the owner, a Mavic Air 2 or Air 2S with the RC-N1, and the hub reachable from the field (D-17). Until then everything from the aircraft onwards is implemented but unproven:
- aircraft connection;
- a live fix and the aircraft in RCH and REM;
- the GPS gate and the rate with real data;
- DJI's gimbal yaw meaning.

Before the trial, the hub operator must also do two things:
- add the `dji4Net` destination shown in the session log to the team and grant it `mission.content.write`;
- make sure the hub is reachable at the address built into the APK.
