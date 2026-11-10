# Operator markers, tasks 2 and 5: team encoder re-pin and Pixel → RCH → REM proof

Recorded 10 November 2026 for branch `feat/m3-markers`.

## Task 2: re-pin (D-13)

- **RCH crates:** `r3akt-map-core` and `r3akt-profile-rch` re-pinned from `f5822210` to `83b5d64aac69347c997ac9e88d84242eb87a1814`, which merges RCH #316 (fixes [RCH #315](https://github.com/FreeTAKTeam/Reticulum-Community-Hub/issues/315)). That revision still pins LXMF-rs `9b972f9a`, so LXMF-rs and Rust 1.89 are unchanged.
- **Encoder switch:** `markers::message` now calls the shared `r3akt_profile_rch::encode_commands_for_team(commands, team)`. With a team it writes field 9 and field 11; without one it writes field 9 only, as before. Nothing in this repository writes field 11.
- **Tests:** they check that field 11 is present with a team (decoded with the shared `r3akt_map_core::msgpack::to_json`) and that only key `9` is written without one.
- **Checks:** clippy and the 15 tests pass, and so does `scripts/check.sh`. The default `sim` and `dji4` builds pass, and both still contain no `publish_`/`marker_` symbols. The `simNet` build with `-Pr3aktTeamUid=43341e5c822d99857fa6e8641f2ca9c0` also passes.

## Task 5 setup

- **Hub:** RCH `83b5d64a` (`r3akt-rch-server` with its `reticulumd` at LXMF-rs `9b972f9a`), with team BLUE `43341e5c…`.
- **Publisher:** the Pixel 7 (`simNet`, lxmf.delivery `8db22674…`), a BLUE member with `mission.content.write`, reaching the hub at its Wi-Fi address.
- **Recipient:** an unchanged REM `0cfa422` on the x86_64 emulator (`9d1a1963…`, BLUE), reaching the hub at `10.0.2.2:4246`.
- **Method:** every step was driven through the Pixel's marker panel. Recipient state was read from REM's own `rch_markers` table (`run-as … app_state.db`) and from REM's map.

## Create, move, delete (hub up)

| Step on the Pixel | Hub (inbound from `8db22674`) | Relayed to REM `9d1a1963` | REM |
|---|---|---|---|
| hostile, Bravo → PLACE AT AIRCRAFT ([panel](m3-task5-pixel-bravo-placed.png)) | fields 9 + 11, `mission.marker.create` | fields 10, 11, 13, `mission.marker.created`, receipt delivered | "Bravo-1", hostile, 44.99866, 6.99830, notes "SIMULATED · at aircraft position" ([screenshot](m3-task5-rem-bravo-created.png)) |
| MOVE HERE | fields 9 + 11, `mission.marker.position.patch` | `mission.marker.position.updated`, delivered | 45.00056, 6.99758, the Pixel's last sent state ([screenshot](m3-task5-rem-bravo-moved.png)) |
| DELETE | fields 9 + 11, `mission.marker.delete` | `mission.marker.deleted`, delivered | gone from the map and from `rch_markers` ([screenshot](m3-task5-rem-bravo-deleted.png)) |

```text
1794350438 in  8db22674 -> 44f1b6fb fields ['11', '9'] ['mission.marker.create']
1794350438 out 44f1b6fb -> 9d1a1963 fields ['10', '11', '13'] ['mission.marker.created'] delivered
1794350707 in  8db22674 -> 44f1b6fb fields ['11', '9'] ['mission.marker.position.patch']
1794350706 out 44f1b6fb -> 9d1a1963 fields ['10', '11', '13'] ['mission.marker.position.updated'] delivered
1794350731 in  8db22674 -> 44f1b6fb fields ['11', '9'] ['mission.marker.delete']
1794350731 out 44f1b6fb -> 9d1a1963 fields ['10', '11', '13'] ['mission.marker.deleted'] delivered
```

Inbound times are the message's own timestamp, set on the Pixel; outbound times are the hub's.

## Change made while the hub is down

| Step | Result |
|---|---|
| Hub stopped (port 4246 closed); on the Pixel: neutral, Charlie → PLACE AT AIRCRAFT, then MOVE HERE ([panel](m3-task5-pixel-charlie-offline.png)) | both changes held in the custody outbox; the Pixel's "sent" counter stops |
| Hub restarted, with no action on the Pixel | the publisher reconnected by itself; within about 60 s the hub received the create (message time `…783`) and then the move (`…791`), and relayed `created` and `position.updated` to REM, both delivered |
| REM | "Charlie-1", neutral, at the moved position 44.99821, 7.00027 ([screenshot](m3-task5-rem-charlie-after-outage.png)) |
| Re-send check, 35 s later | the hub holds exactly 2 inbound messages for Charlie-1 and 3 for Bravo-1. Nothing was re-sent after delivery. The hub's `/api/markers` lists Charlie-1 at the moved position and no Bravo-1. |

## Replay cases

- **Covered by host tests:** create→move→move→delete deposited in any order ends with only the delete pending. A superseded move never leaves the device, and the replacement key `marker:<id>` is checked in `markers.rs` tests.
- **Covered by the outbox spike:** a delivered create is terminal, so the outbox never re-sends it after a delete ([task 1](m3-task1-outbox-spike.md)).
- **On the device:** each change crossed a hub outage and arrived once, in order.
- **Not exercised here:** a replay through RCH itself. In RCH, a late `position.patch` overwrites a newer one (hub time), and a create replayed after a delete re-creates the marker. The custody outbox prevents both from this app. Another writer can still cause them (Known limits in the plan).

## Observations

- REM applies `mission.marker.position.updated` relayed from the hub for operator markers: Bravo-1 moved on REM's map. The aircraft's own marker still does not follow the orbit on REM, because it moves by origin telemetry, not marker events (REM #341, D-6). Both are visible in the screenshots.
- RCH stamps markers with hub time, so Charlie-1, placed during the outage, carries the time it reached the hub (plan, Known limits). This run did not compare the times.
- **RCH map:** the RCH webmap was not captured in this run. The hub evidence is the inbound message log above and the final `/api/markers` output.
- **Team required:** without `-Pr3aktTeamUid` the `simNet` build sends markers without field 11, so RCH keeps them and REM never receives them.
- **Panel label (POST fix):** the panel used to say "last sent state" even while a change was only stored. It now says "last change made here" and shows how many changes are stored and waiting for the hub. The rebuilt panel on the Pixel shows the count, here 0 with the hub up ([screenshot](m3-task5-pixel-panel-stored-count.png)). A non-zero count with the hub down was not photographed.
- **Delete during an outage:** shown on the host only ([task 1](m3-task1-outbox-spike.md)), not on the device.
- **Status:** device-proven in scope for create, move and delete by team relay; it is not proven on a DJI aircraft or over RNode/mesh.
