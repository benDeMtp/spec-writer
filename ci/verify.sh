#!/usr/bin/env bash
# Quality gates de la constitution : tests, couverture, scans, évaluation LLM, E2E.
# S'arrête au premier échec.
set -euo pipefail
cd "$(dirname "$0")/.."

step() { printf '\n==> %s\n' "$*"; }

step "Build, tests unitaires et d'intégration, couverture, SpotBugs, PMD, Dependency-Check"
./mvnw -B verify

step "Audit npm (front)"
npm --prefix frontend audit --audit-level=high

step "SAST Semgrep"
semgrep scan --error --config .semgrep.yml --config p/java --config p/typescript --config p/owasp-top-ten

step "Secrets versionnés (gitleaks)"
gitleaks detect --no-banner --redact --config .gitleaks.toml

step "Évaluation LLM à jour et sans recul"
if [[ -x ci/check-eval.sh ]]; then
  ci/check-eval.sh
else
  echo "ATTENTION : ci/check-eval.sh absent (tâche T138 non réalisée), contrôle de l'évaluation non effectué" >&2
fi

step "Tests de bout en bout (Playwright + axe)"
npm --prefix e2e ci --no-audit --no-fund
npx --prefix e2e playwright install --with-deps chromium
npm --prefix e2e test

step "Toutes les quality gates sont passées"
