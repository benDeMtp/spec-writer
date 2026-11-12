# spec-writer

Outil local de rédaction de spécifications : front Angular 22, backend Quarkus 4 (Java 25), SQLite.
La feature en cours est `specs/012-navigation-projet/` (menu en deux blocs « Projet » et
« Application », réglages et connaissance du projet courant, bascule du projet courant, clé Jira par
défaut facultative, écran de session perdue), après `specs/011-discussion-specification/` (« Discuter avec l'IA » sur une
spécification : conversation rattachée, affichée à côté du document, qui propose une nouvelle
version relue par diff), après `specs/010-agent-conversationnel/` (l'outil s'ouvre sur une conversation avec
un agent qui connaît le projet : recherches visibles, réponse en flux citée, incohérences,
rédaction d'une spécification depuis la discussion ; menu Conversation et Spécifications), après
`specs/006-documents-sharepoint/` (dossier SharePoint synchronisé par
OneDrive : Word, Excel, PDF, HTML, motifs d'exclusion, documents relus à la volée), après
`specs/005-reindexation-par-source/` (étapes de l'indexation
indépendantes, réindexation des seules sources choisies), `specs/004-adr-du-projet/`
(compteurs, page des ADR du projet, ADR entier transmis à la génération) et `specs/003-tickets-fermes-cache/` (tickets Jira fermés gardés dans
l'index sans relecture). Elles prolongent `specs/002-projets-contexte-mcp/` (projets, connaissance du projet par
index local de Jira, Antora et Structurizr, suppression locale, Jira paginé, passerelle MCP pour
les agents de code). La conception de la connaissance du projet est dans
`docs/superpowers/specs/2026-11-04-connaissance-du-projet-design.md`. La 002 prolonge `specs/001-local-spec-assistant/`, dont les décisions restent valables.
La constitution est dans `.specify/memory/constitution.md`.

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
   Exception : les IT qui appellent WireMock (`Jira*IT`, `JiraLinkResourceIT`…) échouent en dev
   mode (« Internal Privoxy Error ») car la JVM de dev reçoit le proxy Maven de `settings.xml`.
   Les lancer sous Maven : `./mvnw -B test -DskipDependencyCheck -Dtest=… -Dsurefire.failIfNoSpecifiedTests=false`.
   Pour joindre un Jira simulé sur la boucle locale depuis le dev mode, le démarrer avec
   `"-Djvm.args=-Dhttp.nonProxyHosts=127.0.0.1|localhost"` ; le jeton Jira (trousseau simulé) est
   perdu à chaque redémarrage du dev mode.
   Même chose pour les règles ArchUnit (`HexagonalArchitectureTest`) : le runner du dev mode ne les
   voit pas (0 test lancé). Préférer `devui-testing_runTest` à `runTests` : la suite complète dépasse
   le délai d'un appel MCP. Les tags `spike` et `eval` sont exclus en dev mode
   (`quarkus.test.exclude-tags`) : l'évaluation appelle le vrai Copilot.
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
- Proxy (T139) : `ProxySettings` applique `https.proxyHost` (donc celui de `JAVA_TOOL_OPTIONS`),
  sinon `HTTPS_PROXY`, au client Jira et au Copilot CLI, sauf boucle locale et exclusions
  `NO_PROXY` / `http.nonProxyHosts`. Le proxy retenu est écrit au démarrage (`Proxy d'entreprise : …`).
- Ports : 47615 pour l'outil installé, 47625 pour le dev mode, 47699 et 47698 pour les E2E (jar
  et WireMock).
- L'IDE compile aussi dans `backend/target/` (ecj) : un test qui échoue sous Maven avec
  « Unresolved compilation problems » tourne sur une classe de l'IDE. Supprimer la classe de
  `target/test-classes` (ou toucher le fichier source) et relancer.
- JaCoCo : `backend/target/jacoco.exec` cumule les lancements successifs, et les `@QuarkusTest`
  n'apportent aucune couverture aux classes de l'application. Un `verify` local peut donc passer
  sur des données anciennes alors que la CI échoue : supprimer `jacoco.exec` avant le `verify`
  qui précède un commit, et couvrir `domain` et `application` par des tests unitaires.
- E2E : `JAVA_HOME` 25 doit aussi être en tête du `PATH` (`export PATH=$JAVA_HOME/bin:$PATH`),
  sinon le jar s'arrête au démarrage.
- Profil `live` (`-Dquarkus.profile=dev,live`) : vrai Copilot et vrai trousseau. Il est réservé
  aux essais manuels du mainteneur.

## Conventions

- Architecture hexagonale (`domain`, `application`, `infrastructure`), vérifiée par ArchUnit.
  Les cas d'usage sont câblés dans `infrastructure/config/UseCaseProducer`.
- Cocher chaque tâche `[X]` dans `tasks.md`, puis commiter par groupe logique. Commits en
  Conventional Commits, en français, fichiers stagés explicitement, sans ligne `Co-Authored-By`.
