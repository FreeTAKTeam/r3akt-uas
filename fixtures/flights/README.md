# Flight fixtures

Recordings must not contain credentials, hub addresses, keys or tokens.

## Session records

The cockpit writes one session record per session (`SessionLog.kt`, UAS-VID-008) to app-private
storage, `files/sessions/<start ms>-<session id>.jsonl`; the SESSIONS page reads it back with
`SessionRecord.kt`. It holds cockpit state and DJI's result texts only, so it carries no secrets.

`sim-session-v1.jsonl`: a short simulator session in that format, assembled from the simulator's
orbit (`../telemetry/sim-orbit-v1.jsonl`, samples 0 to 30). Its header says `"simulated": true`.
The app unit test `SessionRecordTest` parses it.

Format `r3akt-uas-session/1`, one JSON object per line. Every line has `type`, `seq` (0 for the
header, then +1 per line, monotonic per session) and `time_ms` (device wall time, epoch ms).

| `type` | Fields | Written when |
|---|---|---|
| `session` | `format`, `session_id` (random UUID), `variant`, `simulated` (`null` = not known yet) | first line |
| `status` | `status` (cockpit status, without a publish build's counter suffix) | it changes, e.g. connected/disconnected |
| `airborne` | `airborne` (`true`, `false`, `null` = unknown) | it changes |
| `rth` | `active` | return-to-home starts or stops |
| `flight_mode` | `mode` (DJI's text, `null` = unknown) | it changes |
| `fix` | `lat`, `lon`, `alt_m` (`null` = unknown), `alt_ref` (`MSL`, `HAE`, `AGL`, `TAKEOFF_RELATIVE`, `UNKNOWN`) | at most every 5 s while airborne |
| `action` | `action` (`TAKEOFF`, `LAND`, `RTH`, `STOP_RTH`, `PHOTO`), `error` (DJI's text; `null` = accepted) | a local flight action got its result |
| `truncated` | `limit_bytes` | the record reached its size limit; nothing more is written |
| `end` | — | the cockpit closed |

Limits: the newest 50 records are kept, each at most 512 KiB. Altitude keeps its reference: the
SESSIONS page summarises only `TAKEOFF_RELATIVE` altitude, labelled "above take-off".

Aircraft video recordings and media downloads are not built yet; no recordings are stored.
