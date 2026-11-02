#!/usr/bin/env sh
# Lance spec-writer depuis le jar construit (./mvnw -B package -DskipTests), avec un JDK 25 local.
# Réservé aux développeurs : l'installeur embarque son propre runtime Java.
# Usage : dist/scripts/spec-writer.sh [options Java -D...]
#   SPEC_WRITER_JAR : chemin du jar (par défaut backend/target/quarkus-app/quarkus-run.jar)
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
jar=${SPEC_WRITER_JAR:-$root/backend/target/quarkus-app/quarkus-run.jar}

if [ -n "${JAVA_HOME:-}" ]; then
  java="$JAVA_HOME/bin/java"
else
  java=$(command -v java || true)
fi
if [ -z "$java" ] || [ ! -x "$java" ]; then
  echo "Java introuvable : installez un JDK 25 et définissez JAVA_HOME." >&2
  exit 1
fi

# « openjdk version "25.0.2" » ou « java version "25" »
version=$("$java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1)
if [ -z "$version" ] || [ "$version" -lt 25 ]; then
  echo "Java 25 ou plus est requis (trouvé : ${version:-inconnu}, $java)." >&2
  exit 1
fi

if [ ! -f "$jar" ]; then
  echo "Jar absent ($jar) : lancez d'abord ./mvnw -B package -DskipTests" >&2
  exit 1
fi

exec "$java" "$@" -jar "$jar"
