# Operator markers, task 1: LXMF-rs custody outbox for marker messages (spike)

Recorded 10 November 2026 by the agent. The spike used a throwaway host build from a scratch worktree of `feat/m3-markers`, not committed. Its module called the shared LXMF-rs custody outbox `lxmf_runtime::LocalPropagation` at `9b972f9a` (`prepare`, `remember_recipient`, `deposit_own`, `status`, `recover_direct`), with the store type from `lxmf-propagation` (`StoreLimits`, `MessageKind`). Marker messages were `r3akt_profile_rch::encode_commands` (field 9) with field 11 appended by hand. The hand-appended field 11 is for the spike only, until RCH #315.

Setup: hub RCH `f5822210` with team BLUE; the publisher identity is a copy of the task 3 host store (BLUE member, granted); REM `0cfa422` on the emulator in BLUE. Each phase ran as a **separate process**, so the outbox had to survive restarts.

**Result: the outbox holds marker changes across restarts and a hub outage, keeps only the latest move, and delivers each change exactly once, in order. The stop rule does not apply.**

## Phases

| Phase | Hub | Outbox after |
|---|---|---|
| `deposit-create` "UAS OUTBOX Hostile-1" (45.006, 7.006), `MessageKind::Application`, not sent | up; the app learns the hub's identity (`remember_recipient`) | create `StoredLocally`; the hub has no such marker |
| hub stopped; `deposit-move` 45.007, 7.007, then 45.008, 7.008, `MessageKind::Telemetry { key: "marker:<id>" }`, no connection | down | first move **`Evicted`** (superseded), second `StoredLocally` |
| `recover` with the hub down | down | both pending still `StoredLocally`, `attempts=1` ("destination identity unavailable") |
| hub restarted; `recover` after the 30 s retry window | up | create and latest move sent as **Resource**, both **`Delivered`** |
| `recover` again | up | nothing re-sent |
| `deposit-delete` (same key), `recover` | up | delete `Delivered` |

On the hub:

```text
hub received for this marker: 2                       # create + latest move; the superseded move never left
hub: UAS OUTBOX Hostile-1 {'lat': 45.008, 'lon': 7.008}
emulator REM 1794347871 ['mission.marker.created']          delivered
emulator REM 1794347871 ['mission.marker.position.updated'] delivered
emulator REM 1794347911 ['mission.marker.deleted']          delivered
hub OUTBOX markers after the delete: []
```

## What the product needs (task 3)

- **Kinds:**
  - The create uses `MessageKind::Application`, which is never superseded.
  - Moves and the delete of one marker share `MessageKind::Telemetry { key: "marker:<object id>" }`. A newer move replaces a pending one, and a delete replaces a pending move.
  - A delivered create is terminal and is never re-sent. That gives the replay rule D-12 asks for: no create after its delete.
- **Delivery:**
  - `delivered` needs a recipient proof, and the app has no receive path (G-17).
  - Forcing every recovery through a Resource (`PropagationRouteLimits { max_packet_bytes: Some(0), .. }`) lets `recover_direct` mark `Delivered` on resource completion. Small packets would otherwise stay pending and be re-sent until retention runs out.
- **Recipient:** `prepare` needs the hub's identity. `remember_recipient` stores it once it is known, so later deposits work offline.
- **Retries:** a failed recovery waits `retry_at`; the spike used 30 s, and the store caps it at 300 s. The publisher should try again on each connection and on that cadence.
- **Dependencies:** `lxmf-propagation` (LXMF-rs, already in the lock through `lxmf-runtime`) becomes a direct dependency under `publish` and is added to the shared-crate allowlist.
- **File mode:** the outbox SQLite file is created with mode 644. On Android it lives in app-private storage, but this is recorded as an observation for LXMF-rs.
