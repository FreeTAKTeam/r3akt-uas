# Operator markers, task 6: camera point (mode D-10 (c))

Recorded 10 November 2026 for branch `feat/m3-markers`.

## Re-pin

- **RCH crates:** `r3akt-map-core` and `r3akt-profile-rch` are pinned to RCH `d7ad6e17719baf0ea7221343c0cd8beb8ab806ab`. This revision merges two fixes:
  - RCH #317, which fixes #313: `camera_ground::ground_point` and a public `sensor_geometry::destination`.
  - RCH #318, which fixes #312: `r3akt_profile_rch::teams::is_canonical_team_uid`.
- **Unchanged:** LXMF-rs stays at `9b972f9a`, and Rust stays at 1.89.
- **Hub:** the hub for this proof stayed at RCH `83b5d64a`. The camera point is computed in the app, and the hub only relays the marker.

## What was built

- **`camera.rs`** (always compiled, so it is tested without `publish`):
  - `CameraPose` holds the source's absolute camera azimuth and elevation, the ground height in the fix's altitude reference, and the source's error bounds.
  - `camera_point` maps the aircraft fix and that pose onto the shared `ground_point`. The ground model follows the fix's reference:
    - MSL or HAE fix: flat ground at the given height in the same datum;
    - AGL fix: the ground under the aircraft;
    - take-off-relative fix: `TakeoffRelative`, which never relabels the datum (UAS-PRF-004).
  - Policy limits: a pose at most 5 s old, a ray at least 5° below the horizon including its error, and a slant range of at most 2 km.
  - The point is refused rather than estimated, with a reason the pilot can read: unknown altitude reference or ground height, stale or unsynchronized values, horizon, range.
  - The R3S origin mapping (`origin_position`) moved here from `publish.rs` and is shared by both.
- **`marker_create_at_camera`:** places a marker at the shared ground point. Its notes carry the bound and the ground model until a shared field exists, for example `SIMULATED · camera point ±27 m (bound, flat ground), 200 m from aircraft`. The bound is the shared conditional radius, not a CEP.
- **Simulator:** `sim::camera(seq)` is a simulated gimbal aimed at the orbit centre, over a simulated flat ground at 300 m MSL. The aircraft flies 100 m above that ground, and the HUD gimbal scale shows −27°. The answer is known, so the tests check it.
- **Team check:** `publish_start` refuses a team UID that is not an RCH canonical team, using the shared list. This closes G-22.
- **simNet:** the marker panel gains a PLACE AT CAMERA button. The pose is read on the tap and stamped with the time of the last offered fix (it is that sample's simulated pose).

## Host tests (20 in the crate, 5 new)

| Test | Checks |
|---|---|
| `simulated_camera_hits_the_orbit_centre_with_a_bound` | the point is within 1 m of the simulated target; ground range 200 m; the bound is larger than the fix accuracy and below 50 m |
| `take_off_relative_altitude_uses_the_take_off_ground` | the DJI-style path: take-off-relative altitude with ground 0 gives the same point and model `TakeoffRelative` |
| `refusals_never_invent_a_point` | refusals for a level camera (horizon), an unknown ground height, an unknown altitude reference, a stale pose, and a ray at 5.5° (horizon with error) |
| `camera_note_carries_the_bound_and_ground_model` | the note is labelled simulated and carries the bound, the ground model and the range |
| `non_canonical_team_is_refused_before_starting` | a 32-hex team UID that is not canonical is refused |

## Pixel 7 → RCH → REM

Setup as in [task 5](m3-task5-markers-rem-proof.md): the Pixel 7 runs `simNet` in team BLUE, and REM `0cfa422` runs on the emulator.

| Marker | Pose time | Hub and REM position | Notes |
|---|---|---|---|
| Charlie-2, hostile | wall clock at the tap (first build) | 45.00000, 7.00000 | `SIMULATED · camera point ±57 m (bound, flat ground), 200 m from aircraft` ([REM](m3-task6-rem-camera-point.png)) |
| Alpha-1, friendly | the fix's time (fixed build) | 45.00000, 7.00000 | `SIMULATED · camera point ±27 m (bound, flat ground), 200 m from aircraft` ([Pixel panel](m3-task6-pixel-camera-panel.png)) |

Both markers land on the simulated target, the orbit centre at 45.0, 7.0.

The first build stamped the pose with the wall clock, while the fix carries whole seconds. The shared function counted up to 1 s × 12 m/s of platform motion, which tripled the bound. Stamping the pose with its own fix's time fixed this. A real source must give its true pose time.

- **Stray marker:** while driving the UI, one tap created a stray "Delta-1" at the aircraft position. It was deleted from the panel and is gone from REM.

## Not covered

- **Mode (b), radar pick:** deferred. The simulator has no aircraft heading. Radar contacts (UAS-RAD-002 to 007) are milestone 4 work. The shared `destination` it would use is now public (G-13 closed).
- **Image offset:** a point tapped elsewhere in the video needs a video frame, roll and FOV calibration. The shared function supports it, but the simulator has no video.
- **DJI source:** real error bounds per aircraft and gimbal, the take-off ground height, and a DJI build with the publisher. None exist yet.
- **Terrain:** the ground is flat. Real terrain is excluded by the shared function.
- **Status:** implemented and device-proven in scope with simulated values only.
