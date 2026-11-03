#!/usr/bin/env bash
# Évaluation LLM à jour et sans recul (T138, constitution §IV). Sans appel réseau ni jeton :
# - l'empreinte des prompts, du code LLM, du golden set, de la suite d'évaluation, de la version du
#   Copilot SDK et du modèle par défaut doit être celle de last-run.json ;
# - chaque score de last-run.json doit atteindre max(minimum, base - tolérance) de baseline.json.
# Même calcul que backend/src/test/java/dev/specwriter/eval/EvalFingerprint.java et EvalBaseline.java.
# --fingerprint : affiche l'empreinte courante et s'arrête.
set -euo pipefail
cd "$(dirname "$0")/.."

exec python3 - "$@" <<'PY'
import hashlib, json, os, re, sys

SOURCES = [
    "backend/src/main/resources/prompts",
    "backend/src/main/java/dev/specwriter/infrastructure/llm",
    "backend/src/test/resources/golden",
    "backend/src/test/resources/eval/prompts",
    "backend/src/test/java/dev/specwriter/eval",
]
EVAL = "backend/src/test/resources/eval"
RERUN = "relancer l'évaluation LLM (./mvnw -pl backend verify -Peval) et committer last-run.json"


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def read(path):
    with open(path, "rb") as f:
        return f.read()


def first(pattern, path):
    m = re.search(pattern, read(path).decode("utf-8"), re.MULTILINE)
    return m.group(1).strip() if m else ""


def fingerprint():
    files = []
    for source in SOURCES:
        for root, dirs, names in os.walk(source):
            dirs[:] = [d for d in dirs if not d.startswith(".")]
            files += [os.path.join(root, n).replace(os.sep, "/") for n in names if not n.startswith(".")]
    files.sort(key=lambda f: f.encode("utf-8"))
    manifest = "".join(f"{sha256(read(f))}  {f}\n" for f in files)
    manifest += "copilot-sdk=" + first(r"<copilot-sdk\.version>([^<]*)</copilot-sdk\.version>", "backend/pom.xml") + "\n"
    manifest += "default-model=" + first(r"^specwriter\.copilot\.model=(.*)$",
                                         "backend/src/main/resources/application.properties") + "\n"
    return sha256(manifest.encode("utf-8"))


def fail(message):
    print(f"ÉCHEC : {message}", file=sys.stderr)
    sys.exit(1)


current = fingerprint()
if sys.argv[1:] == ["--fingerprint"]:
    print(current)
    sys.exit(0)

if not os.path.exists(f"{EVAL}/last-run.json"):
    fail(f"{EVAL}/last-run.json absent : {RERUN}")
last = json.loads(read(f"{EVAL}/last-run.json"))
baseline = json.loads(read(f"{EVAL}/baseline.json"))

if last.get("fingerprint") != current:
    fail(f"les prompts, le code LLM, le golden set, la version du Copilot SDK ou le modèle par défaut ont changé "
         f"depuis la dernière évaluation ({last.get('date', '?')}) : {RERUN}")

failed = False
for metric, minimum in baseline["minimum"].items():
    required = max(minimum, baseline["scores"].get(metric, 0) - baseline.get("tolerance", 0))
    score = last.get("scores", {}).get(metric)
    if score is None or score + 1e-9 < required:
        print(f"ÉCHEC : {metric} = {score}, attendu au moins {required:.4f} (baseline.json)", file=sys.stderr)
        failed = True
    else:
        print(f"{metric} = {score} (minimum {required:.4f})")
if failed:
    sys.exit(1)
print(f"Évaluation à jour : {last['date']}, modèle {last.get('model')}, Copilot CLI {last.get('copilotCli')}, "
      f"{last.get('runs')} exécutions")
PY
