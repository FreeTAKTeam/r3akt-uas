#!/usr/bin/env python3
"""Dependency-boundary check (UAS-ARC-002, UAS-SCF-009).

Fails when the workspace gains a member outside the allowlist, or when a member
depends directly on a wire codec or Reticulum/LXMF crate instead of going
through the shared R3AKT crates.
"""
import json
import subprocess
import sys

MEMBERS = {"r3akt-uas-app", "uas-sim", "r3akt-uas-uniffi-bindgen"}
# Direct use of these means a parallel codec/transport in this repository.
FORBIDDEN_PREFIXES = ("rmp", "msgpack", "reticulum", "rns-", "lxmf", "zeromq", "zmq")
# Shared crates may be added here as milestones adopt them (git dependencies only).
SHARED = {"r3akt-map-core", "r3akt-profile-rch", "r3akt-protocol", "r3akt-mesh-delivery",
          "lxmf-sdk", "lxmf-runtime",
          # Owner ruling R-2 (milestone 2a): lxmf-runtime needs the host to construct the
          # transport and identity. Construction calls only; no routing, queueing or
          # interface-management logic belongs in this repository.
          "reticulum-rs-transport", "reticulum-rs-core"}

meta = json.loads(subprocess.check_output(
    ["cargo", "metadata", "--format-version", "1", "--no-deps", ]))
errors = []
for pkg in meta["packages"]:
    name = pkg["name"]
    if name not in MEMBERS:
        errors.append(f"unexpected workspace member {name}")
    for dep in pkg["dependencies"]:
        dep_name = dep["name"]
        if dep_name in SHARED:
            if dep.get("source") is None or not dep["source"].startswith("git+"):
                errors.append(f"{name}: shared crate {dep_name} must be a pinned git dependency")
            elif "rev=" not in dep["source"]:
                errors.append(f"{name}: shared crate {dep_name} must pin rev=")
        elif dep_name.startswith(FORBIDDEN_PREFIXES):
            errors.append(f"{name}: direct dependency on {dep_name}; use the shared R3AKT crates")
        elif dep_name.startswith("r3akt-") and dep.get("path") is None:
            errors.append(f"{name}: unknown shared crate {dep_name}; add it to SHARED with a reason")

for e in errors:
    print(f"boundary: {e}", file=sys.stderr)
print("dependency boundaries: ok" if not errors else f"dependency boundaries: {len(errors)} error(s)")
sys.exit(1 if errors else 0)
