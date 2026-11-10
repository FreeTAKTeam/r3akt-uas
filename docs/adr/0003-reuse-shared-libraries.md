# ADR 0003: Reuse shared R3AKT/LXMF libraries

**Context.** RCH, REM and R3AKTClient already own networking, wire formats, delivery policy, telemetry codecs and map/sensor models. Parallel copies drift and break interoperability.

**Decision.** This repository depends on those crates at immutable revisions and never reimplements them. Missing reusable capabilities are added to the owning crate first (gaps in [existing-system-and-gaps.md](../integration/existing-system-and-gaps.md)).

**Consequences.** Some milestones wait on upstream changes. `scripts/check_boundaries.py` rejects direct wire-codec or Reticulum/LXMF crates in any workspace member other than through the allowed shared crates.
