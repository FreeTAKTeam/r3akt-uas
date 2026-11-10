# Telemetry fixtures

`sim-orbit-v1.jsonl`: 120 samples from `uas-sim --count 120`, each with the input sample, the `FIELD_TELEMETRY` bytes from the shared encoder, and the shared decoder output. Regenerate only when the simulator or the pinned shared crate changes:

```bash
cargo run -p uas-sim -- --count 120 > fixtures/telemetry/sim-orbit-v1.jsonl
```
