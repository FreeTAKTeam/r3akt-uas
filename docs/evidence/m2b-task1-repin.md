# Milestone 2b task 1: re-pin to RCH f5822210, LXMF-rs 9b972f9a, Rust 1.89

Recorded 10 November 2026 for commit `95f6582` (decision D-5). This evidence was added after the POST review, which noted that it existed only in the commit message.

## Build

```text
cargo tree -p r3akt-uas-app --features publish -e normal --locked
  -> LXMF-rs.git?rev=9b972f9a8d3a7949eae89bc78f7a9691f06f3ed1   (only LXMF-rs revision)
  -> Reticulum-Community-Hub.git?rev=f5822210da63ce303ee86ffa95d67e0db5e12b3d   (only RCH revision)
scripts/check.sh on Rust 1.89.0: exit 0 (fmt, clippy -D warnings including the UniFFI scaffolding, tests, fixture, boundaries, docs)
./gradlew :app:assembleSimDebug :app:assembleDji4Debug: exit 0; CI publisher and INTERNET assertions pass
./gradlew -Pr3aktPublish=true … :app:assembleSimNetDebug: exit 0; uniffi_r3akt_uas_app_fn_func_publish present in arm64-v8a and x86_64
```

The only source change was the Rust 1.89 `clippy::collapsible_if` lint in `publish.rs`.

## 2a behaviour on the re-pinned set (no team, D-9)

This ran against a fresh RCH `f5822210` hub (`reticulumd` LXMF-rs `9b972f9a`) with a fresh store and `mission.content.write` granted. The steps were 20 s with a replay at 10 s, then a 10 s restart.

```text
markers: 1 ['0b6a1ab8715d7f428ed4bfafd0651579']
writers: [('0b6a1ab8715d7f428ed4bfafd0651579', 'aeefaa94dec8ba67da154c5bf0be7ffd')]
origin: 0b6a1ab8… writer aeefaa94… lat 45.00160260062283 lon 7.001154798432758 fixAtMs 1794341057000 kind mobile
run 1: 29 messages, first=9, field 9 at positions [0, 20], fixes=27, repeated fixAtMs: [1794341037000]
```

The two runs were less than 5 s apart, so the trace counts them as one run. The create appears at positions 0 and 20, once at the start of each. The repeated fix time is the replay. Fix messages carried field 13 only, as in step 2a.
