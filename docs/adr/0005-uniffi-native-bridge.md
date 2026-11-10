# ADR 0005: UniFFI-generated Kotlin bridge

**Context.** The plan requires a generated native bridge. REM already uses UniFFI 0.28.3 with a `uniffi-bindgen` tool crate.

**Decision.** `r3akt-uas-app` exports its API with UniFFI proc-macros. `tools/uniffi-bindgen` (pinned `=0.28.3`) generates Kotlin from the built Android library in library mode; `scripts/build-android-native.sh` runs from the `native-bridge` Gradle module, so bindings and `.so` files are build outputs, never committed.

**Consequences.** JNA is a runtime dependency. UniFFI's scaffolding needs `unsafe`, so this one crate denies rather than forbids `unsafe_code` and allows it only at the scaffolding macro.
