# spec-writer

Outil local de rédaction de spécifications : front Angular 22, backend Quarkus 4 (Java 25), SQLite.
La feature en cours est décrite dans `specs/001-local-spec-assistant/` (spec, plan, research,
tasks). La constitution est dans `.specify/memory/constitution.md`.

## Boucle de développement (research.md R17)

Pendant le développement, travailler en **dev mode Quarkus piloté par le serveur MCP Quarkus**
(`quarkus-agent`), pas par des cycles `package` puis redémarrage :

1. `quarkus_start` sur le projet, puis `quarkus_status`. Le backend écoute sur
   `http://127.0.0.1:47625`, avec ses données dans `.dev-data/`. LLM rejoué depuis
   `e2e/fixtures/llm`, GitHub et trousseau simulés : aucun compte, aucun AI Credit.
2. TDD : écrire le test, puis le code. Le rechargement à chaud se fait au premier accès HTTP.
3. Tests ciblés par Dev MCP : `quarkus_searchTools`, puis `quarkus_callTool` sur
   `devui-testing_runTest` (`className`, `methodName`). Lire les listes `passing` et `failing` de
   la réponse : les compteurs cumulent tous les lancements depuis le démarrage.
4. Erreurs : `quarkus_logs` et `devui-exceptions_getLastException`. Base : `quarkus-agroal_executeSQL`.
5. Appels d'API : cookie obtenu en échangeant le jeton de l'URL de lancement du journal
   (`POST /api/session-auth`, en-tête `Origin: http://127.0.0.1:47625`). Le cookie survit aux
   rechargements.
6. Front : `npm --prefix frontend start` (`http://localhost:4200`, proxy `/api` vers 47625).

Un redémarrage (`quarkus_restart`) reste nécessaire après une modification du pom ou l'ajout d'une
extension. Ne jamais modifier une migration Flyway déjà appliquée : en ajouter une, ou vider
`.dev-data/`.

**Avant chaque commit** : arrêter le dev mode (`quarkus_stop`), puis lancer `./mvnw -B verify
-DskipDependencyCheck` (JaCoCo, ArchUnit, SpotBugs, PMD, front) et les E2E sur le jar
(`./mvnw -B package -DskipTests` puis `npm --prefix e2e test`). Le dev mode ne remplace aucune
gate.

## Pièges de l'environnement

- JDK : `export JAVA_HOME=~/.sdkman/candidates/java/25.0.2.r25-mandrel`. Le JDK par défaut est le
  21, avec lequel le jar ne démarre pas.
- `JAVA_TOOL_OPTIONS` impose un proxy (`127.0.0.1:8118`) dont Java refuse le certificat : lancer
  le dev mode et jbang avec `env -u JAVA_TOOL_OPTIONS`. Le serveur MCP est enregistré ainsi.
- Ports : 47615 pour l'outil installé, 47625 pour le dev mode, 47699 et 47698 pour les E2E (jar
  et WireMock).
- Profil `live` (`-Dquarkus.profile=dev,live`) : vrai Copilot et vrai trousseau. Il est réservé
  aux essais manuels du mainteneur.

## Conventions

- Architecture hexagonale (`domain`, `application`, `infrastructure`), vérifiée par ArchUnit.
  Les cas d'usage sont câblés dans `infrastructure/config/UseCaseProducer`.
- Cocher chaque tâche `[X]` dans `tasks.md`, puis commiter par groupe logique. Commits en
  Conventional Commits, en français, fichiers stagés explicitement, sans ligne `Co-Authored-By`.
