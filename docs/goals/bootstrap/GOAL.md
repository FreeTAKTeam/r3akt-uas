# Goal: R3AKT UAS specification and buildable skeleton

Use Krypton Execution to execute [PLAN.md](PLAN.md).

The first authorized implementation milestone in the plan is the complete specification package and a buildable native Android/Rust skeleton. This planning commit contains no application implementation.

Core rules:

- Treat PLAN.md as the source plan and preserve its explicit product decisions.
- Reuse the provided Reticulum/LXMF crates and shared R3AKT libraries directly. Do not create another transport manager, protocol, wire codec, routing policy, or delivery queue.
- Put reusable missing capabilities in their existing owning library; keep this repository's Rust integration thin.
- Target a full local DJI SDK 4 flying application, aircraft-centred radar, RCH/REM interoperability, and separate MediaMTX video.
- Preserve intent, ownership, contracts, cutover, evidence, and kill criteria.
- Complete the PRE review before application implementation and resolve blocker findings.
- Keep companion RCH/REM changes explicit and follow the applicable repository-scope instructions before editing another repository.
- Capture acceptance evidence from the operator and receiving-client perspectives.
- Say "implemented but unproven" when the required acceptance evidence cannot be captured.
- Do not describe the target directory tree or planned features as already implemented.

Start with the specification/scaffold milestone. Later product milestones have separate integration and hardware acceptance gates.
