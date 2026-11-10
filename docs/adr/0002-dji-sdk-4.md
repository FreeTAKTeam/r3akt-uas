# ADR 0002: DJI Mobile SDK 4 for the first release

**Context.** An earlier discussion preferred SDK 5 and an observe-only app. The 4 November 2026 decision chose a full local flying app on SDK 4, which FreeTAKUAS already uses (4.16).

**Decision.** Use DJI Mobile SDK 4 (scaffold pins 4.18) in a separate `dji4` Gradle module and build variant. SDK 5 is a later milestone.

**Consequences.** Aircraft support is limited to SDK 4 models; every claim needs a compatibility-matrix row. SDK 4.18 has no x86_64 native libraries, so DJI runtime tests need arm64 hardware (G-10). The simulator variant never depends on DJI.
