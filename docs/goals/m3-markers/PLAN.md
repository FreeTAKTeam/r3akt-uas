# Operator markers from the UAS app — task plan (revision 1)

Status: **draft for PRE.** Task 0 (spike) done ([evidence](../../evidence/m3-task0-marker-relay-spike.md)). Owner direction (10 November 2026): placing **other** markers from the UAS app into the network is the priority; the aircraft's own marker (milestone 2) is secondary. Reuse the FreeTAKUAS placement UI, improve its ground-position algorithm, and reuse REM's marker code for everything else.

**Intent:** a pilot places, moves and deletes markers (observations, points of interest, threats) from the flight app, and they appear for the team in RCH and REM exactly like markers placed in REM.
**Expected outcome:** a marker placed on the Pixel appears once on the RCH map and on every REM in the same team, with its affiliation symbol, name and position; moving or deleting it updates them; a marker placed while disconnected is sent when the hub is reachable again; a camera-derived point is placed only when the geometry is valid and carries its uncertainty.
**Target-perspective output:** the pilot sees a placement popup (name, affiliation) and the list of own markers with their state ("sent", never "accepted"); REM users and the hub operator see the markers.
**Truth owner:** the pilot owns the decision to place; RCH owns the canonical marker and authorization; shared crates own the marker record, store, message and ground-point geometry.
**Contract boundary:** Kotlin cockpit → `r3akt-uas-app` → `r3akt-map-core` (`MarkerDraft`/`MarkerRecord`, `sqlite` marker store, shared marker-change builder of RCH #314, ground point of RCH #313) → publisher (`lxmf-runtime`) → RCH → team relay → REM.
**Cutover:** none; new feature behind the `publish` feature and `simNet` (later the DJI source).
**Displaced path:** FreeTAKUAS GeoObject/SPI CoT sending (`SendCotTask`) is not imported; only its popup UI is adapted.
**Acceptance evidence:** REM screenshots (two devices) and RCH store/API output for create, move and delete from the Pixel; a marker created offline and sent after reconnect; camera-point cases (valid, refused near the horizon, unknown altitude) with their uncertainty.
**Kill criteria:** any marker message, command envelope or field map assembled in this repository; ground-point geometry written here; a camera point produced without valid geometry (no horizon or default-height substitution); a marker silently lost; anything received reaching a flight command; a default build containing the publisher.

## Findings that shape the plan

- **FreeTAKUAS `704f4aa`, `CompleteWidgetActivity.java`:** GeoObject popup (name Alpha–Zulu with a counter, affiliation unknown, friendly, hostile, neutral, Send). Its position is `alt / tan(gimbalPitch)` along the **aircraft heading**, with a "horizon" substitute when the camera is level. It ignores gimbal yaw, assumes flat ground at the take-off elevation, invents a point near the horizon and gives no accuracy. Only the UI is reused.
- **REM `0cfa422`, `node/map_marker_replication.rs`:** each change is one message of the shared `marker_wire::encode_fields`: field 9 command for RCH plus field 13 event for REM. `MapMarkerChange` (arguments and envelope) is REM product code; RCH #314 moves it into `marker_wire`.
- **Spike:** RCH relays a marker to the team's REMs only with **field 11** (team). The shared builder must take a team (comment on #314).
- **Shared pieces that already exist:**
  - **Markers:** the four NATO affiliation symbols match FreeTAKUAS's choices. `MarkerRecord` and the `sqlite` marker store (`create_once`, `upsert`, `delete`, `load_all`) are behind map-core's `sqlite` feature and are used by REM.
  - **Geometry:** `sensor_geometry` is display-only and keeps `destination()` private, so no shared ground point exists yet (RCH #313).

## Decisions to take

| ID | Decision | Proposed |
|---|---|---|
| D-10 | Placement modes, in order | (a) at the aircraft's current position ("mark here"); (b) at a point the pilot picks on the cockpit map/radar; (c) at the camera centre or a tapped video point, only once RCH #313 lands. |
| D-11 | Symbols and names | FreeTAKUAS popup: affiliation picks the map-core NATO symbol (friendly, hostile, neutral, unknown); name defaults to Alpha–Zulu with a counter and can be edited; marker type and category = affiliation. |
| D-12 | Durability | Own markers live in the shared map-core `sqlite` store (enable the `sqlite` feature under `publish`), each with a "to send" mark cleared when its message was handed to LXMF. The publisher sends pending changes in order on every connection, after the aircraft create. There is no new queue type; the store is the shared one. |
| D-13 | Upstream gate | Tasks 2 and 4 wait for RCH #314 (and #313 for task 5) to merge, then re-pin. Nothing is written here as a stopgap. |

## Tasks

| # | Task | Requirements | Files allowed | Evidence |
|---|---|---|---|---|
| 0 | **Spike** (done): app-built marker reaches RCH and team REMs. | UAS-PRD-006 | `docs/evidence/` | [evidence](../../evidence/m3-task0-marker-relay-spike.md) |
| 1 | **Re-pin** to the RCH revision with #314; enable map-core `sqlite` under `publish`; boundary check and notices. | UAS-ARC-009, UAS-ARC-010 | `Cargo.toml`, `Cargo.lock`, `crates/r3akt-uas-app/Cargo.toml`, `scripts/check_boundaries.py`, `THIRD_PARTY_NOTICES.md`, `docs/integration/` | `check.sh`; both ABIs; default builds unchanged |
| 2 | **Marker changes in Rust:** `markers.rs` creates, moves and deletes own markers in the shared store and builds each message with the shared builder (team from the publisher). The publisher sends pending changes in order after the create and clears the mark on "sent". UniFFI exports `marker_create`, `marker_move`, `marker_delete`, `marker_list`. | UAS-OBS-002, UAS-PRD-006 | `crates/r3akt-uas-app/src/markers.rs`, `src/publish.rs`, `src/lib.rs`, `tools/uas-sim/` | host tests: the store round trip; the message decodes with the shared decoder to the expected command and event with field 11; pending changes survive a restart |
| 3 | **Placement UI (simNet):** adapted FreeTAKUAS popup (name, affiliation) from the session log or a cockpit button; modes D-10 (a) and (b); own-marker list with state, move and delete. | UAS-OBS-002, UAS-PRD-006 | `apps/android/app/src/main/` (source-neutral hook only), `apps/android/app/src/simNet/`, `THIRD_PARTY_NOTICES.md` | screenshots |
| 4 | **Host and device proof:** Pixel → hub → REM on the emulator and the Poco: create, move, delete; an offline create sent after reconnect. **POST review.** | UAS-PRD-006, UAS-PRF-008 | `docs/evidence/`, `docs/specification/acceptance.md` | REM screenshots on both devices; hub store and relay trace |
| 5 | **Camera point** (after RCH #313): mode D-10 (c) via the shared ground point; refused cases shown to the pilot; uncertainty in the marker notes until a shared field exists. | UAS-OBS-003, UAS-OBS-004, UAS-PRF-004 | `crates/r3akt-uas-app/src/markers.rs`, `apps/android/app/src/` | host tests for the refusal cases; device evidence with the simulator's gimbal values |

## Known limits

- Until REM #341, REM follows a moved marker only through `mission.marker.position.updated` events (which REM does apply), not through R3S fixes. This affects only the aircraft marker.
- "Sent" is not "accepted" (G-17): a refused marker shows only on the hub.
- Team UIDs are checked as 32 hex only (G-22, RCH #312).
- REM's own markers may not reach the team through the hub without field 11. That is a REM-side observation and outside this plan.
