# Milestone 2: first vertical slice — task plan

Status: **draft, awaiting a focused PRE review.** Not started.

**Intent:** one simulated aircraft travels through existing LXMF object-telemetry contracts and appears exactly once, at the correct position, on the RCH map and in REM.
**Truth owner:** the simulator owns the aircraft state; RCH owns the canonical aircraft object and authorization; shared crates own every wire format.
**Contract boundary:** `r3akt-uas-app` → `lxmf-runtime`/`lxmf-sdk` (in-process) → RCH ingest → RCH → REM forwarding.
**Cutover:** none; `publication.enabled` stays off by default and is turned on only in the simulator test build.
**Acceptance evidence:** RCH map record and screenshot plus REM screenshot showing one aircraft object marked simulated at the fixture position; RCH store shows no duplicate object after an app restart and a replayed sample.
**Kill criteria:** any parallel transport, envelope or codec; aircraft position written as the app identity's own field-2 position; a second aircraft object after restart.

## Decisions required before task 1

| ID | Decision | Input |
|---|---|---|
| D-1 | LXMF-rs revision set (G-08): RCH `c2bf78a` pins `888355c`; RCH HEAD pins `213af2d`. Pick one with RCH and record it as "qualified" after task 6. | RCH maintainers |
| D-2 | App Reticulum identity: generated on first run, stored in Android private storage, never exported. | this repo |
| D-3 | Aircraft object creation: the app creates the canonical marker through the existing authorized marker-creation command before any field-3 telemetry, so the writer binding exists (R3S: an unbound object cannot be claimed by its first tuple). | RCH contract at `c2bf78a` |
| D-4 | Network gate: the `INTERNET` permission is added only to a `simNet` test configuration (or behind a settings switch defaulting off), preserving UAS-SCF-005 for default builds. | this repo |

## Tasks (sequential)

| # | Task | Files allowed | Evidence |
|---|---|---|---|
| 1 | Pin D-1 LXMF crates and `r3akt-profile-rch`, `r3akt-mesh-delivery` as git revs; extend `check_boundaries.py` `SHARED` | `Cargo.toml`, `Cargo.lock`, `scripts/check_boundaries.py` | `check.sh` green; `cargo tree` shows one LXMF revision |
| 2 | Identity provisioning (D-2) via shared identity APIs | `crates/r3akt-uas-app/src/identity.rs`, `native-bridge` regen, Android settings | unit test: identity stable across restart, absent from logs |
| 3 | Aircraft marker creation (D-3) with idempotency key | `crates/r3akt-uas-app/src/aircraft.rs` | fixture: same key after restart → same object id |
| 4 | Field-3 publish: Telemeter + R3S origin position for the aircraft object, 1 Hz, through `lxmf-runtime` | `crates/r3akt-uas-app/src/publish.rs`, Android foreground service | trace of outgoing fields; no field-2 aircraft position |
| 5 | Network gate (D-4) | `apps/android/app` manifests/flavors | merged manifests for default vs test build |
| 6 | Recipient-side proof against a local RCH at the D-1 set, with REM attached (cross-repo: companion change C-RCH-4 is done in RCH if needed) | none here; evidence in `docs/evidence/` | RCH map + REM screenshots, RCH object count before/after restart and replay |
