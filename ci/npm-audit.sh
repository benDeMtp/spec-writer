#!/usr/bin/env bash
# Audit npm du front (gate « aucune vulnérabilité haute », CONTRIBUTING) : échoue sur tout avis de
# niveau high ou critical, sauf ceux de ci/npm-audit-ignore.txt, nommés, datés et suivis dans TODO.md.
set -euo pipefail
cd "$(dirname "$0")/.."

npm --prefix frontend audit --json > /tmp/npm-audit.json 2>/dev/null || true
exec python3 - ci/npm-audit-ignore.txt /tmp/npm-audit.json <<'PY'
import json, re, sys

ignored = {}
for line in open(sys.argv[1], encoding="utf-8"):
    line = line.strip()
    if line and not line.startswith("#"):
        ghsa, _, reason = line.partition(" ")
        ignored[ghsa] = reason

report = json.load(open(sys.argv[2], encoding="utf-8"))
if "vulnerabilities" not in report:
    print("ÉCHEC : npm audit n'a pas rendu de rapport lisible", file=sys.stderr)
    sys.exit(1)

blocking, skipped = {}, set()
for name, vuln in report["vulnerabilities"].items():
    for via in vuln.get("via", []):
        if not isinstance(via, dict) or via.get("severity") not in ("high", "critical"):
            continue
        m = re.search(r"GHSA-[\w-]+", via.get("url", ""))
        ghsa = m.group(0) if m else via.get("url", via.get("title", "?"))
        if ghsa in ignored:
            skipped.add(ghsa)
        else:
            blocking[ghsa] = f"{via.get('name', name)} {via.get('range', '')} : {via.get('title', '')}"

for ghsa in sorted(skipped):
    print(f"Ignoré : {ghsa} ({ignored[ghsa]})")
for ghsa in sorted(set(ignored) - skipped):
    print(f"À retirer de ci/npm-audit-ignore.txt : {ghsa} n'est plus signalé")
if blocking:
    for ghsa, what in sorted(blocking.items()):
        print(f"ÉCHEC : {ghsa} {what}", file=sys.stderr)
    sys.exit(1)
print("Audit npm : aucune vulnérabilité haute hors des avis ignorés")
PY
