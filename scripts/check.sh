#!/usr/bin/env bash
# Repository checks that need no Android SDK: Rust, fixtures, docs, boundaries.
set -euo pipefail
cd "$(dirname "$0")/.."
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --locked -- -D warnings
cargo test --workspace --locked
# The publication path is optional: check it as well, and prove default builds carry none of it.
cargo clippy -p r3akt-uas-app -p uas-sim --all-targets --locked --features r3akt-uas-app/publish,uas-sim/publish -- -D warnings
cargo test -p r3akt-uas-app --locked --features publish
default_tree=$(cargo tree -p r3akt-uas-app -e normal --locked)
if grep -qE 'lxmf|reticulum' <<<"$default_tree"; then
  echo "default features pull LXMF or Reticulum crates" >&2
  exit 1
fi
cargo run -q --locked -p uas-sim -- --check fixtures/telemetry/sim-orbit-v1.jsonl
python3 scripts/check_boundaries.py
python3 scripts/check_docs.py
