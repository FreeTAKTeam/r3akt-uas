# Repository rules

R3AKT UAS is a DJI SDK 4 Android flying application. Requirements live in `docs/specification/` (stable IDs `UAS-*`); the plan is `docs/goals/bootstrap/PLAN.md`.

## Ownership (must not be duplicated here)

Do not add a Reticulum stack, transport manager, LXMF envelope, wire codec, routing policy, delivery queue, telemetry codec, marker model or sensor-geometry implementation to this repository. Use the shared crates in the ownership table of `docs/specification/architecture.md`. If something reusable is missing, record a gap in `docs/integration/existing-system-and-gaps.md` and add it to the owning library.

- Rust: only `crates/r3akt-uas-app` (thin integration) and `tools/`. Shared crates are Git dependencies at immutable revisions in the root `Cargo.toml`; never a sibling path or branch.
- Kotlin reaches Rust only through generated UniFFI bindings (`apps/android/native-bridge`); no hand-written JNI.
- Flight commands stay local to the DJI interface; nothing received over the network commands the aircraft.
- Never fabricate data: unknown altitude reference, range, accuracy or heading stays unknown. Take-off-relative altitude is never MSL/HAE/AGL.
- Simulator output is always marked simulated.
- No credentials in the repository: DJI keys come from the `djiApiKey` Gradle property outside the repo.

## Before committing

```bash
scripts/check.sh            # Rust, fixtures, docs, dependency boundaries
(cd apps/android && ./gradlew :app:assembleSimDebug :app:assembleDji4Debug)
```

Requirement IDs used in code or docs must exist in the specification and appear in `docs/specification/acceptance.md`. Say "implemented but unproven" when acceptance evidence has not been captured; never describe planned features as implemented.

Companion changes to RCH, REM, R3AKTClient or LXMF-rs are made in those repositories under their own instructions (`docs/integration/rch-rem-changes.md`).
