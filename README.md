# R3AKT UAS

An independent Android DJI flying application planned for the R3AKT ecosystem: existing Reticulum/LXMF libraries, RCH telemetry and mission services, REM-compatible markers, aircraft-centred radar, and separate MediaMTX video.

**Status: planning.** This repository currently contains the agreed plan and implementation handoff. The application and target scaffold have not been implemented.

- [Specification and repository structure plan](docs/goals/bootstrap/PLAN.md)
- [Implementation handoff](docs/goals/bootstrap/GOAL.md)

The agreed baseline is native Android with a thin Rust integration layer, DJI SDK 4, core local flight controls, TCP/IP and RNode connectivity, signed APK releases, and EPL-2.0 for original implementation code with upstream notices retained.

Existing shared Rust libraries own networking, wire formats, delivery policy, telemetry codecs, and map/sensor models. This project must reuse them rather than introduce parallel implementations.

See the plan for scope, reference baselines, required companion changes, milestones, and acceptance evidence.
