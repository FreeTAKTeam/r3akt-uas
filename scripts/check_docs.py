#!/usr/bin/env python3
"""Documentation checks: relative links resolve and requirement IDs trace.

- Every **UAS-XXX-NNN** defined in docs/specification is listed exactly once in
  the traceability table of acceptance.md, and nothing undefined is listed.
- Every UAS-XXX-NNN mentioned anywhere in the repository is defined.
"""
import pathlib
import re
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parent.parent
ID = re.compile(r"UAS-[A-Z]{3}-\d{3}")
errors = []

files = subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard"],
                                cwd=root, text=True).split()
spec = root / "docs/specification"
defined = {}
for md in sorted(spec.glob("*.md")):
    for m in re.finditer(r"\*\*(UAS-[A-Z]{3}-\d{3})\*\*", md.read_text()):
        if m.group(1) in defined:
            errors.append(f"{m.group(1)} defined twice ({defined[m.group(1)]}, {md.name})")
        defined[m.group(1)] = md.name

acceptance = (spec / "acceptance.md").read_text()
table = acceptance.split("## Traceability", 1)[1].split("\n## ", 1)[0]
traced = re.findall(r"^\| (UAS-[A-Z]{3}-\d{3}) \|", table, re.M)
for rid in sorted(set(defined) - set(traced)):
    errors.append(f"{rid} is not traced in acceptance.md")
for rid in sorted({t for t in traced if traced.count(t) > 1}):
    errors.append(f"{rid} traced more than once")

link = re.compile(r"\]\(([^)#\s]+)(?:#[^)]*)?\)")
for f in files:
    path = root / f
    if not path.is_file() or path.suffix not in {".md", ".rs", ".kt", ".kts", ".py", ".sh", ".toml", ".yml"}:
        continue
    text = path.read_text(errors="ignore")
    for rid in set(ID.findall(text)) - set(defined):
        if "goals/bootstrap" not in f:
            errors.append(f"{f}: undefined requirement {rid}")
    if path.suffix == ".md":
        for target in link.findall(text):
            if "://" in target or target.startswith("mailto:"):
                continue
            if not (path.parent / target).exists():
                errors.append(f"{f}: broken link {target}")

for e in errors:
    print(f"docs: {e}", file=sys.stderr)
print(f"docs: {len(defined)} requirements, {len(traced)} traced" + (f", {len(errors)} error(s)" if errors else ", ok"))
sys.exit(1 if errors else 0)
