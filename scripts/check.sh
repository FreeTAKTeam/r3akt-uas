#!/usr/bin/env bash
# Repository checks that need no Android SDK: Rust, fixtures, docs, boundaries.
set -euo pipefail
cd "$(dirname "$0")/.."
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --locked -- -D warnings
cargo test --workspace --locked
cargo run -q --locked -p uas-sim -- --check fixtures/telemetry/sim-orbit-v1.jsonl
python3 scripts/check_boundaries.py
python3 scripts/check_docs.py
