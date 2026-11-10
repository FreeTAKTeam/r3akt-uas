# ADR 0001: Native Android with one thin Rust crate

**Context.** DJI SDK 4 is a Java/Android SDK, FreeTAKUAS's reusable flight components are Java/XML, and all R3AKT wire semantics live in Rust crates.

**Decision.** Kotlin application, Java/XML DJI components, and exactly one application Rust crate (`r3akt-uas-app`, rlib + cdylib) that normalizes values and calls the shared crates. No cross-platform UI framework.

**Consequences.** Two languages, one FFI boundary (ADR 0005). Rust logic beyond normalization, session coordination, publication decisions and projections belongs upstream (UAS-ARC-005).
