# Milestone 2a task 5: hub host proof

Recorded 10 November 2026 on the host (Linux), before the POST review that gates task 6. All addresses are loopback; the hub, its keys and its store were throwaway test state.

## Setup

| Part | Revision / command |
|---|---|
| App | `r3akt-uas` at this commit, `cargo build --release --locked -p uas-sim --features uas-sim/publish` |
| Hub | RCH `c2bf78adc2795574a5fb8c39e6cdea7e523b7ef0`, separate clone, `cargo build --release --locked -p r3akt-rch-server` |
| Hub daemon | LXMF-rs `888355c580932f886cfbf9f7076758b065a69879`, separate clone, `cargo build --release --locked -p reticulumd --bin reticulumd` |

Hub start (the hub runs the daemon itself; `[hub] announce_interval = 10` in `config.ini` so a client that connects later learns the hub's path quickly):

```bash
r3akt-rch-server --bind 127.0.0.1:8080 --db-path ./state/rch.sqlite3 --config-path ./state/config.ini \
  --api-key "$KEY" --reticulumd-exe <lxmf>/target/release/reticulumd \
  --reticulumd-db-path ./state/reticulum.db --reticulumd-transport 127.0.0.1:4242 --ui-dist-path <rch>/ui/dist
```

Hub `lxmf.delivery` destination, from `GET /api/v1/app/info` (`reticulum_destination`): `5ca609638dd20e4f677019cb688f2a93`.

## Grant

The grant takes the app's **`lxmf.delivery` destination hash** (the LXMF source hash, which is also the bound writer), not the identity hash. The app prints it as `destination_hash` before it sends anything; it was read first with `uas-sim --publish 127.0.0.1:1 …` (a closed port, nothing sent).

```bash
curl -X PUT -H "X-API-Key: $KEY" -H 'Content-Type: application/json' -d '{}' \
  http://127.0.0.1:8080/api/r3akt/capabilities/4d8ffce8db31d5f2a063a7906f46d191/mission.content.write
```

Only the field-13 origin fix needs the grant (`mission.content.write`). At `c2bf78a` the field-9 create is accepted without one because `authorization_required` is off by default; no mission or team is needed, and no field 11 is sent.

## Runs

```bash
uas-sim --publish 127.0.0.1:4242 5ca609638dd20e4f677019cb688f2a93 --store <store> --seconds N [--resend-at 20]
```

1. **First start**, ended after about 2 s when the reading pipe closed (the shared transport prints `link: close …` lines to stdout; the tool's JSON lines start with `{`). Create and the first fixes were sent.
2. **Restart**, 40 s, with `--resend-at 20`: the exact bytes of the last sent fix were sent a second time.
3. **Restart** after the hub itself was restarted, 180 s.

The app's status at the end of run 3: `state: Stopped`, `create_sent: true`, `fixes_sent: 179`, `last_error: null`, last sent fix `fix_time_s 1794328565`, lat 44.998203824247156, lon 7.000133124988917.

## Hub store after the three runs

Query (Python `sqlite3`, read-only; there is no `sqlite3` CLI on the host):

```sql
SELECT object_destination_hash FROM rch_markers;
SELECT origin_id, writer_id FROM r3s_marker_writers;
SELECT origin_id, writer_id, json FROM r3s_sensor_origins;   -- position from json
```

```text
markers: 1 ['8a4a8c244ef6af1c90b1bde138447780']
writers: [('8a4a8c244ef6af1c90b1bde138447780', '4d8ffce8db31d5f2a063a7906f46d191')]
origin: 8a4a8c244ef6af1c90b1bde138447780 writer 4d8ffce8db31d5f2a063a7906f46d191 lat 44.99820382424715 lon 7.000133124988917 fixAtMs 1794328565000 kind mobile
```

One marker and one origin for the object id after two restarts and a replayed fix; the origin holds the last fix the app sent. `GET /api/markers` returns the same single marker, named `SIMULATED UAS 8a4a8c24`, `origin_rch` = the app's destination hash, `symbol`/`category` `drone` (G-19). `GET /Sensors` returned the origin with the moving position during run 3.

## Outgoing field trace (hub side)

From the daemon's `messages` table (`reticulum.db`), every message whose source is the app:

```text
messages from app: 224
field sets: {'9,_lxmf': 3, '13,_lxmf': 221}      # _lxmf is the daemon's own annotation, not a wire field
3 creates; identical: True                        # one per start, the stored bytes re-sent
fixes: 221 repeated fixAtMs (replays): [1794328273000]
signature_status: {'verified': 224}
```

Each message carries field 9 only or field 13 only; the create precedes the first fix in every run. The three creates are the same command (`command_id uas-create-8a4a8c244ef6af1c90b1bde138447780`), and the hub kept one marker. The daemon verified the signature of every message, because the app announces its delivery destination on each connection.

## RCH map screenshot: not obtainable at `c2bf78a` (G-20)

![RCH webmap centred on 45°N 7°E with no marker drawn](m2a-rch-webmap-marker-hidden-g20.png)

The webmap draws no marker here, although `/api/markers` returns it at 44.998 N, 7.000 E. At `c2bf78a` `r3akt-map-core::marker::MarkerRecord::api_value` sets `stale_at` equal to `updated_at`, and the UI drops every marker whose `stale_at` is in the past (`ui/src/stores/markers.ts` `expired`, `WebMapPage.vue` feature filter). Every marker is therefore hidden about a second after its last update, whoever created it. This is recorded as gap G-20 and reported as [RCH #305](https://github.com/FreeTAKTeam/Reticulum-Community-Hub/issues/305); the task-5 map evidence above is the store and API output instead. The screenshot was taken with headless Chromium (software WebGL) because the app's browser pane has no WebGL.

## Gaps settled by this run

- **G-17 confirmed:** `lxmf-runtime` at `888355c` exposes no inbound messages; `poll_events` returns only events the host records itself. Command results from the hub are not seen by the app.
- **G-18 confirmed from source:** the daemon annotates each message with `signature_status`; RCH at `c2bf78a` never reads it.
- **G-19 confirmed:** `uav` is stored as `drone`, origin kind `mobile`.
