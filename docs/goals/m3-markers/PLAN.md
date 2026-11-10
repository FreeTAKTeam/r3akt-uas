# Operator markers from the UAS app — task plan (revision 2)

Status: **focused PRE on revision 1 returned blocked; the two owner decisions are taken (D-12, D-13) and every finding is applied here.** Task 0 (spike) done ([evidence](../../evidence/m3-task0-marker-relay-spike.md)). Task 1 (custody outbox spike) done: create, superseded offline moves and delete delivered once each, in order, across restarts and a hub outage ([evidence](../../evidence/m3-task1-outbox-spike.md)). Tasks 2 to 5 wait for RCH #315. Owner direction (10 November 2026): placing **other** markers from the UAS app into the network is the priority; the aircraft's own marker (milestone 2) is secondary. Reuse the FreeTAKUAS placement UI, improve its ground-position algorithm upstream, and reuse REM/shared code for everything else. Test devices: the Pixel 7 (publisher) and REM `0cfa422` on the emulator; the owner's Poco is no longer available.

**Intent:** a pilot places, moves and deletes markers (threat, friendly, neutral, unknown) from the flight app, and they appear for the team in RCH and REM like markers placed in REM.
**Expected outcome:** a marker placed on the Pixel appears once on the RCH map and in REM in the same team, with its affiliation symbol, name and position; moving or deleting it updates both; a marker change made while the hub is unreachable is delivered when it is reachable again and never lost; a camera-derived point (later) is placed only with valid geometry and carries its uncertainty.
**Target-perspective output:** the pilot sees a placement popup (name, affiliation) and the list of own markers with the last **sent** state ("sent"/"stored", never "accepted", never the hub's current state); REM users and the hub operator see the markers.
**Truth owner:** the pilot owns the decision to place; RCH owns the canonical marker and authorization; shared crates own the marker record, the command encoder, the custody outbox and the ground-point geometry.
**Contract boundary:** Kotlin cockpit → `r3akt-uas-app` → `r3akt-map-core` (`MarkerDraft`/`MarkerRecord`, symbols) and `r3akt-profile-rch` (`MissionCommandEnvelope`, team-scoped command encoder of RCH #315) → `lxmf-runtime` `LocalPropagation` custody outbox and in-process backend → RCH → team fan-out → REM.
**Cutover:** none; new feature behind `publish` and `simNet` (later the DJI source).
**Displaced path:** FreeTAKUAS GeoObject/SPI CoT sending (`SendCotTask`) is not imported; only its popup UI is adapted.
**Acceptance evidence:** REM screenshots and RCH store/API output for create, move and delete from the Pixel; a change made with the hub down delivered after it comes back, once; replay cases ending in the correct final state; later, camera-point cases.
**Kill criteria:** any marker message, command envelope, field map, outbox or queue written in this repository; ground-point or tap-to-position geometry written here; a camera or map point without valid shared geometry; a marker change silently lost; a simulated or aircraft-position marker not labelled as such; anything received reaching a flight command; a default build containing the publisher.

## Findings that shape the plan

- **FreeTAKUAS `704f4aa`** (`CompleteWidgetActivity.java`): GeoObject popup (name Alpha–Zulu with a counter, affiliation unknown, friendly, hostile, neutral, Send); position `alt / tan(gimbalPitch)` along the aircraft heading, with an invented "horizon" range. Only the UI is reused; the improved algorithm is requested upstream (RCH #313).
- **REM `0cfa422`** builds marker messages with the shared `marker_wire::encode_fields` (fields 3, 9, 13); its `MapMarkerChange` wrapper is REM code (RCH #314).
- **Spike:** RCH relays a marker to the team's REMs only with **field 11**. REM receives **RCH's own response** (fields 10, 11, 13), not the app's field 13. From the app the hub route therefore needs only **field 9 plus field 11**, so the gate is the small RCH #315, not #314.
- **RCH rules** (`f5822210`, PRE-checked):
  - **Replays:** re-sending an identical create is safe. A `position.patch` gets hub time (an older move replayed later overwrites a newer one), and a create replayed after a delete re-creates the marker. REM keeps no tombstone.
  - **No owner:** markers have no per-marker owner. Any writer with `mission.content.write` may move or delete them.
  - **Hub time:** `created`/`updated` time is the hub's clock.
- **Shared marker store** (map-core `sqlite`) has no pending slot and drops a deleted row, so it cannot hold durability (PRE #1). The custody outbox `lxmf-runtime::LocalPropagation` (`prepare`, `deposit_own`, `status`, `recover_direct`, `delivered`) is the shared mechanism to use (D-12), verified in task 1.
- **Cockpit:** no map and no tappable radar yet, and turning a tap into a position needs the private `destination()` (G-13). Map/radar placement waits for #313.

## Decisions (owner, 10 November 2026)

| ID | Decision | Ruling |
|---|---|---|
| D-10 | Placement modes | This plan: (a) **at the aircraft's current position** only, refused when it is unknown, notes "at aircraft position". (b) map/radar pick and (c) camera point wait for RCH #313 (task 5). |
| D-11 | Symbols and names | FreeTAKUAS popup: affiliation chooses the map-core NATO symbol (`friendly`, `hostile`, `neutral`, `unknown`); category = symbol (REM's default); name Alpha–Zulu with a counter, editable. Notes start with `SIMULATED` when the source is simulated. |
| D-12 | Durability | **LXMF-rs shared custody outbox** (`LocalPropagation`). The app deposits each marker message and the outbox keeps and re-sends it; no store, queue or pending mark in this repository. Per marker only {create, latest move, delete} are kept: a new move supersedes the stored one; once a delete is deposited, the create and moves are withdrawn. |
| D-13 | Gate | **Small upstream: optional team on `encode_commands`** (RCH #315), then re-pin. #314 (full builder for direct peer sends) and #313 (ground point) follow separately. |

## Tasks

| # | Task | Requirements | Files allowed | Evidence |
|---|---|---|---|---|
| 0 | **Spike** (done): app-built marker reaches RCH and team REMs only with field 11. | UAS-PRD-006 | `docs/evidence/` | [evidence](../../evidence/m3-task0-marker-relay-spike.md) |
| 1 | **Outbox spike:** with a throwaway host build, deposit a marker message in `LocalPropagation` while the hub is down, restart the process, bring the hub up, recover it with `recover_direct`; record how "delivered" is set without a receive path (G-17), how a superseded or withdrawn message is removed, and the store location and limits. **Stop rule:** if the outbox cannot hold app messages, stop for an owner decision. | UAS-PRD-010 | `docs/evidence/`, `docs/integration/existing-system-and-gaps.md` | hub trace: exactly one create after the restart; outbox status before and after |
| 2 | **Re-pin** to the RCH revision with #315 (and the LXMF-rs it pins); boundary check, notices. | UAS-ARC-009, UAS-ARC-010 | `Cargo.toml`, `Cargo.lock`, `rust-toolchain.toml`, `crates/r3akt-uas-app/Cargo.toml`, `scripts/check_boundaries.py`, `THIRD_PARTY_NOTICES.md`, `docs/integration/` | `check.sh`; both ABIs; default builds unchanged |
| 3 | **Marker changes in Rust:** `markers.rs` builds `MarkerRecord`s and `MissionCommandEnvelope`s (create, `position.patch`, delete; one fixed `command_id` per stored message) and encodes them with the team-scoped shared encoder; deposits them in the outbox per D-12; the publisher drives recovery on each connection after the aircraft create. UniFFI: `marker_create`, `marker_move`, `marker_delete`, `marker_list` (last sent state). | UAS-OBS-002 (team relay, no mission association yet), UAS-PRD-006, UAS-PRD-010, UAS-PRF-006 | `crates/r3akt-uas-app/src/markers.rs`, `src/publish.rs`, `src/lib.rs`, `tools/uas-sim/`, `apps/android/native-bridge/`, `docs/integration/existing-system-and-gaps.md` | host tests: messages decode with the shared `decode_commands` (field 9) and carry field 11; create→move→move→delete deposited in any order ends with only the delete; a deposited change survives a restart; simulated notes |
| 4 | **Placement UI (simNet):** adapted FreeTAKUAS popup (name, affiliation) from the session log; mode D-10 (a); own-marker list (last sent state) with move-to-aircraft-position and delete. | UAS-OBS-002, UAS-PRD-006 | `apps/android/app/src/main/` (source-neutral hook only), `apps/android/app/src/simNet/`, `apps/android/app/src/simCommon/`, `THIRD_PARTY_NOTICES.md` | screenshots |
| 5 | **Host and device proof:** Pixel → hub → REM (emulator): create, move, delete; a change made with the hub down delivered after reconnect, once; replay cases. **POST review.** | UAS-PRD-006, UAS-PRF-008, UAS-PRD-010 | `docs/evidence/`, `docs/specification/acceptance.md`, `docs/integration/` | REM screenshots; hub store and relay trace |
| 6 | **Map/radar and camera points** (after RCH #313): modes (b) and (c) via the shared ground point; refusals shown to the pilot; uncertainty in the notes until a shared field exists. The simulator gains gimbal values for the evidence. | UAS-OBS-003, UAS-OBS-004, UAS-PRF-004 | `crates/r3akt-uas-app/src/`, `tools/uas-sim/`, `apps/android/app/src/simCommon/`, `apps/android/app/src/` | host tests for the refusal cases; device evidence |

## Known limits

- **What the app can know:** "Sent" or "stored" is not "accepted" (G-17). The own-marker list shows the app's last sent state, never the hub's. Another team writer may move or delete the app's markers (RCH has no per-marker owner).
- **Hub time:** RCH stamps markers with hub time, so a marker placed offline shows when it reached the hub (gap, RCH). The shared marker field-3 Telemeter fills in altitude and accuracy 0.0 (gap, map-core). Both conflict with UAS-PRF-006/009 until fixed upstream.
- **Partial scope:** UAS-OBS-002's mission association is not covered; markers travel by team relay only.
- **Team UIDs:** checked as 32 hex only (G-22, RCH #312).
- **Aircraft marker:** until REM #341, REM does not follow it.
