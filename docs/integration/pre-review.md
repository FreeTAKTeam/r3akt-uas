# Milestone 1 reviews

Two independent reviewers ran on 10 November 2026 against `docs/goals/bootstrap/PLAN.md`, using the Krypton plan-review (PRE) and post-implementation (POST) templates. Both returned **partially aligned**. The findings and their resolutions are below.

## PRE: plan plus specification, before product implementation

| # | Severity | Finding | Resolution |
|---|---|---|---|
| 1 | blocker | Milestones 2–6 are goals, not tasks; milestone 2 depends on unmade decisions (LXMF-rs set, identity, aircraft marker binding, network gate) | **Open.** [Milestone 2 task plan](../goals/m2-vertical-slice/PLAN.md) drafted with decisions D-1…D-4. It needs a focused PRE before milestone 2 starts. Milestones 3–6 stay gated outlines. |
| 2 | major | No wire carrier is defined for `uas` | Carrier table added to [uas-profile-v1.md](../specification/uas-profile-v1.md). The extension carrier is a pending decision (G-01, blocks M3). |
| 3 | major | Events mapped to field 13; clients send commands on field 9 | Remapped to the field-9 `mission.registry.log_entry.upsert` command. |
| 4 | major | Video mapping violates `SensorManifest::validate` (protocol set, URL form, `sensor_id`) | Mapping corrected; WebRTC reference added to G-02. |
| 5 | major | Observation altitude mapped to marker `Position`, which has only lat/lon | Marked **new** (G-12). |
| 6 | minor | Gimbal angle ranges differ from R3S | Normalization rules added. |
| 7 | minor | R3S origin `label` is hub-asserted only | Model now goes in the marker name and `uas.aircraft.model`. |
| 8 | minor | Battery `charging=false` is fabricated | Documented as encoder constraint G-11 (code comment + gap). |
| 9 | minor | Link to this file was broken | This file. |

## POST: scaffold against the plan

| # | Severity | Finding | Resolution |
|---|---|---|---|
| 1 | major | `check.sh` red (broken link); acceptance claims otherwise | Fixed; `check.sh` re-run green. |
| 2 | major | "Clean clone" claimed with nothing committed | Evidence restated as a clean `git init` copy; a clone build after commit is still pending. |
| 3 | minor | CI DJI-exclusion step could pass vacuously | `set -euo pipefail` + `test -f`. |
| 4 | minor | MediaMTX example open by default | Default-deny `authInternalUsers` (local API only), still credential-free. |
| 5 | minor | Battery charging made up | Same as PRE 8. |
| 6 | minor | UAS-SCF-005 overstated for dji4 | Scoped to sim; dji4 implemented but unproven. |
| 7 | minor | Gradle inputs incomplete; hard-coded target dir | Inputs extended; `CARGO_TARGET_DIR` honoured. |
| 8 | minor | Boundary check is dependency-level only | Evidence wording narrowed. |

## Gate status

- Milestone 1 (specification and scaffold): complete except the clean-clone build after commit, the first CI run, and dji4 runtime on arm64 hardware.
- PRE gate for product implementation: **not passed.** PRE finding 1 stays open until the milestone 2 plan is reviewed and decisions D-1…D-4 are made.
