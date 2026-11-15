# First flight with the app (`dji4Net`, Pixel 7)

Recorded 15 November 2026. Plan: [DJI field-trial build](../goals/dji-net/PLAN.md), task 5 (flight test), partly covered.

## Setup

- **Phone:** Pixel 7, Android 17, `dji4Net` debug build installed on 14 November 2026 (branch `feat/aircraft-media`, PR #13). Hub settings compiled in: the R3AKT Server hub and team BLUE.
- **Aircraft, remote controller and firmware:** not in the app's session record; to be confirmed by the owner. The aircraft named under D-14 is the Mavic Air 2.
- **Source:** the app's session record (`files/sessions/…c76ffee0….jsonl`) and its LXMF-rs outbox and marker store (`no_backup/publish-dji4/`), read from the phone over adb. The record's exact positions are left out of this file.

## Flight, from the session record

| Local time | Event |
|---|---|
| 13:39:59 | Cockpit opened, DJI registration, aircraft connected: ON GROUND |
| 13:40:40, 13:40:43 | VIDEO_MODE, then PHOTO_MODE: accepted by DJI |
| 13:42:13 | Hub announce received (hub link up) |
| 13:42:45 | **TAKEOFF** from the app: accepted; MOTORS ON, flight mode TakeOff |
| 13:42:48 | IN FLIGHT |
| 13:42:48–13:44:14 | Track fix every 5 s; up to 16.2 m above take-off, about 10 m of horizontal movement |
| 13:44:15 | **LAND** from the app: accepted; flight mode Landing |
| 13:44:22 | ON GROUND, about 94 s airborne |
| 13:51:12 | Aircraft disconnected |

- **Rejections:** none.
- **Altitude:** every fix is take-off-relative (`TAKEOFF_RELATIVE`), never labelled MSL, HAE or AGL.
- **Not simulated:** the session is marked `simulated: false`, variant "DJI SDK 4, RCH publish test build".
- **On the ground:** DJI reported the flight mode alternating between OPTI, P-Atti and P-GPS; recorded as reported.

### Defect found: LAND shown as RETURNING HOME

From the LAND tap to touchdown, DJI reported `isGoingHome`, so the cockpit showed RETURNING HOME, marked return home active and offered STOP RTH. Fixed in `d4995cd` (PR #13): return home counts only outside DJI's landing flight modes, and those modes show LANDING. The fix is not flight-tested yet.

## Markers

The app's outbox and marker store hold four markers. Each was delivered to the hub's LXMF destination on the first attempt (LXMF-rs state `Delivered`), including Alpha-2, placed in flight at 13:43:31.

| Marker | Placed | Outbox state | On the hub |
|---|---|---|---|
| Charlie-1 (hostile) | 11 November | Delivered | no |
| Alpha-1 (hostile) | 13:42:19, on the ground | Delivered | no |
| Alpha-2 (hostile) | 13:43:31, in flight | Delivered | no |
| Bravo-1 (unknown) | 13:48:49, after landing | Delivered | no |
| Charlie-2 (neutral) | 14:05:40, after the team change | Delivered | **yes** (owner) |

- **Cause: G-24.** The `dji4Net` identity, `84b40d986126dd2c67c3c935d9f55a24` (aircraft object `40cb47fb98e46c8c757ea0f5260196e0`), was not a member of team BLUE. The hub drops a team marker from a non-member without an answer, and the app cannot see the drop (G-17).
- **Resolution:** the owner added the identity to team BLUE. Charlie-2, placed afterwards, appeared on the hub (owner-reported, no screenshot).
- **Not re-sent:** the four dropped markers stay delivered in the outbox and are not sent again. Moving or re-placing them sends them anew.

## What this shows and what it does not

- **Shown:** take-off and landing from the app on a real aircraft, live flight values and take-off-relative altitude in the session record, DJI accepting the photo/video mode buttons, and a marker from the `dji4Net` build reaching the hub once the identity is a team member.
- **Not shown:**
  - the aircraft on RCH or REM during the flight (its fixes are team messages from the same identity, so they were most likely dropped too);
  - return home, photo, video recording, gimbal, live stream or media download in flight;
  - hub screenshots or relay traces;
  - controller disconnection or app recreation in flight;
  - REM.
