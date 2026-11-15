# First flights with the app (`dji4Net`, Pixel 7, Mavic Air 2)

Two flights on 15 November 2026. Plan: [DJI field-trial build](../goals/dji-net/PLAN.md), task 5 (flight test), partly covered.

## Setup

- **Phone:** Pixel 7, Android 17, `dji4Net` debug builds from branch `feat/aircraft-media` (PR #13), with the R3AKT Server hub and team BLUE compiled in:
  - first flight: the build of 14 November 2026;
  - second flight: the same build plus the LANDING fix `d4995cd`, installed between the flights.
- **Aircraft and remote controller:** a DJI Mavic Air 2 with its standard remote controller (RC-N1), as stated by the owner for the second flight. The app's session record does not name them. Firmware: not recorded.
- **Source:** the app's session records (`files/sessions/…c76ffee0….jsonl` and `…dc1bb342….jsonl`) and its LXMF-rs outbox and marker store (`no_backup/publish-dji4/`), read from the phone over adb. The record's exact positions are left out of this file.

## First flight, from the session record

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

From the LAND tap to touchdown, DJI reported `isGoingHome`, so the cockpit showed RETURNING HOME, marked return home active and offered STOP RTH. Fixed in `d4995cd` (PR #13): return home counts only outside DJI's landing flight modes, and those modes show LANDING. The second flight shows the fix working.

## Second flight, from the session record

| Local time | Event |
|---|---|
| 14:00:37 | Cockpit opened, DJI registration |
| 14:04:01 | Aircraft connected: ON GROUND |
| 14:09:08 | **TAKEOFF** from the app: accepted; MOTORS ON, flight mode TakeOff |
| 14:09:11 | IN FLIGHT |
| 14:09:11–14:11:07 | 24 track fixes; up to 24.1 m above take-off, all take-off-relative |
| 14:10:02 | Marker Echo-1 placed at the aircraft position, in flight |
| 14:10:12 | **RTH** from the app: accepted; RETURNING HOME, flight mode GoHome |
| 14:10:31 | Final descent of the return home: LANDING, return home no longer active, flight mode Landing |
| 14:10:55 | Landing ended in a hover: IN FLIGHT, flight mode P-GPS (cause not in the record) |
| 14:11:01 | **LAND** from the app: accepted; LANDING |
| 14:11:09 | ON GROUND, about 118 s airborne |

- **Rejections:** none.
- **LANDING fix:** shown in both landing paths. The return home read RETURNING HOME while flying back and LANDING in its final descent; the LAND tap read LANDING throughout.

## Markers

The app's outbox and marker store hold six markers. Each was delivered to the hub's LXMF destination on the first attempt (LXMF-rs state `Delivered`), including Alpha-2 and Echo-1, placed in flight.

| Marker | Placed | Outbox state | On the hub |
|---|---|---|---|
| Charlie-1 (hostile) | 11 November | Delivered | no |
| Alpha-1 (hostile) | 13:42:19, on the ground | Delivered | no |
| Alpha-2 (hostile) | 13:43:31, in flight | Delivered | no |
| Bravo-1 (unknown) | 13:48:49, after landing | Delivered | no |
| Charlie-2 (neutral) | 14:05:40, after the team change | Delivered | **yes** (owner) |
| Echo-1 (friendly) | 14:10:02, in flight (second flight) | Delivered | not confirmed |

- **Cause: G-24.** The `dji4Net` identity, `84b40d986126dd2c67c3c935d9f55a24` (aircraft object `40cb47fb98e46c8c757ea0f5260196e0`), was not a member of team BLUE. The hub drops a team marker from a non-member without an answer, and the app cannot see the drop (G-17).
- **Resolution:** the owner added the identity to team BLUE. Charlie-2, placed afterwards, appeared on the hub (owner-reported, no screenshot).
- **Not re-sent:** the four dropped markers stay delivered in the outbox and are not sent again. Moving or re-placing them sends them anew.

## What this shows and what it does not

- **Shown:** take-off, landing and return home from the app on a Mavic Air 2, live flight values and take-off-relative altitude in the session records, DJI accepting the photo/video mode buttons, the LANDING status fix, and a marker from the `dji4Net` build reaching the hub once the identity is a team member.
- **Not shown:**
  - the aircraft on RCH or REM during either flight (in the first, its fixes were most likely dropped with the markers; the second is not checked);
  - Echo-1 on the hub;
  - photo, video recording, gimbal, live stream or media download in flight;
  - hub screenshots or relay traces;
  - controller disconnection or app recreation in flight;
  - REM.
