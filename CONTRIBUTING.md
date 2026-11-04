# Contribuer à spec-writer

Front Angular 22, backend Quarkus 4 (Java 25), base SQLite embarquée. La feature en cours est
décrite dans [`specs/001-local-spec-assistant/`](specs/001-local-spec-assistant/) (spec, plan,
recherche, tâches). La constitution du projet est dans
[`.specify/memory/constitution.md`](.specify/memory/constitution.md).

## Prérequis

- JDK 25 et Maven 3.9 (ou le wrapper `./mvnw`).
- Node.js 24 LTS. `frontend-maven-plugin` l'installe aussi localement pour le build Maven.
- Pour les E2E : un navigateur Chromium pour Playwright, installé une fois par
  `npx --prefix e2e playwright install chromium`.
- Si votre `~/.m2/settings.xml` active des profils d'entreprise qui gênent le build, désactivez-les
  dans `.mvn/maven.config`, propre à votre poste et ignoré par Git (par exemple `-P!mon-profil`).

## Commandes

```bash
# Build complet : front, back, tests unitaires et d'intégration, couverture, SpotBugs, PMD
./mvnw verify

# Lancer l'outil depuis le jar construit, avec un JDK 25 local
./mvnw -B package -DskipTests
dist/scripts/spec-writer.sh          # Windows : dist\scripts\spec-writer.cmd

# Dev mode : backend avec rechargement à chaud sur 127.0.0.1:47625, données dans .dev-data/,
# LLM rejoué, GitHub et trousseau simulés (aucun compte, aucun AI Credit). L'URL de lancement
# est affichée dans le journal.
./mvnw -pl backend quarkus:dev
# Même chose avec le vrai Copilot et le vrai trousseau (consomme des AI Credits)
./mvnw -pl backend quarkus:dev -Dquarkus.profile=dev,live
# Front avec rechargement à chaud sur http://localhost:4200 (proxy /api vers 47625)
npm --prefix frontend start

# E2E et accessibilité : jar et Jira simulé (WireMock) démarrés par le globalSetup
npm --prefix e2e test

# Suite d'évaluation LLM (vrai Copilot, consomme des AI Credits) : voir plus bas
./mvnw -pl backend verify -Peval

# Toutes les quality gates, comme la CI
ci/verify.sh
```

Le détail des scénarios de validation (V1 à V16) est dans
[`quickstart.md`](specs/001-local-spec-assistant/quickstart.md). Un agent qui travaille sur le
projet suit en plus [`CLAUDE.md`](CLAUDE.md).

## Règles

### Test d'abord (principe III, non négociable)

1. Écrire le test, le lancer, et le voir échouer pour la bonne raison.
2. Écrire le code minimal qui le fait passer.
3. Remanier, tests au vert.

Un changement de comportement sans test qui l'a fait échouer d'abord n'est pas accepté.

### Architecture

- Hexagonale : `domain` (sans dépendance technique), `application` (cas d'usage), `infrastructure`
  (web, persistance, Jira, LLM, plateforme). ArchUnit vérifie ces frontières.
- Les cas d'usage sont câblés dans `infrastructure/config/UseCaseProducer`.
- L'heure vient de `ClockPort`, jamais de `Instant.now()` hors plateforme et web.
- Migrations Flyway : ne jamais modifier une migration déjà appliquée, en ajouter une.

### Passerelle MCP

- `dev.specwriter.mcp` est un processus à part, sans Quarkus ni CDI : il ne dépend ni du domaine,
  ni de l'application, ni de l'infrastructure (règle ArchUnit). Il ne parle à l'outil que par
  `/api/agent/**`, en HTTP/1.1, avec le secret de `agent-endpoint.json`.
- Depuis les sources : `java -cp 'backend/target/quarkus-app/app/*:backend/target/quarkus-app/lib/main/*'
  dev.specwriter.mcp.McpBridge`, ou la commande affichée dans **Configuration › Accès des agents**.
- Tests : `McpBridgeTest` rejoue les échanges JSON-RPC contre un serveur HTTP du JDK ;
  `AgentResourceIT` couvre l'API des agents et ses refus ; `e2e/tests/us11-agents.spec.ts` lance
  la passerelle avec le client `e2e/mcp-client.ts`. stdout ne doit porter que du JSON-RPC.
- Le lanceur `spec-writer-mcp` des installeurs est déclaré dans `jreleaser.yml`
  (`dist/launchers/spec-writer-mcp.properties`).

### Quality gates (CI bloquante)

| Gate | Seuil |
|---|---|
| Tests unitaires, d'intégration et E2E | tous au vert |
| Couverture JaCoCo de `domain` et `application` | 80 % par package |
| SpotBugs, PMD, ESLint | aucune alerte |
| Accessibilité (axe, dans les E2E) | aucune violation WCAG 2.1 AA |
| OWASP Dependency-Check, `npm audit` | aucune vulnérabilité haute |
| Semgrep, gitleaks | aucun constat |
| Évaluation LLM (`ci/check-eval.sh`) | à jour si les prompts, le code LLM, le golden set, le Copilot SDK ou le modèle par défaut changent ; scores au-dessus de la base |

### Évaluation LLM (principe IV)

La CI n'appelle jamais Copilot. Elle vérifie que `backend/src/test/resources/eval/last-run.json`
correspond au code (empreinte) et que ses scores n'ont pas reculé. Après toute modification d'un
prompt, du code de `infrastructure/llm`, du golden set, de la version du Copilot SDK ou du modèle
par défaut, la CI demande donc de relancer l'évaluation :

1. Connectez le Copilot CLI une fois : `copilot login`.
2. Lancez `./mvnw -pl backend verify -Peval -DskipDependencyCheck`. Pour 3 exécutions des 3
   besoins, comptez de l'ordre de 150 requêtes Copilot (questions, réponses simulées, jugement,
   génération), décomptées de votre quota selon le modèle.
3. Relisez le détail de `last-run.json` (questions jugées non pertinentes, violations de
   structure), puis committez-le avec le changement.

Options : `-Deval.runs=1` pour un essai rapide (à ne pas committer), `-Deval.model=<id>` pour un
autre modèle, `-Deval.copilot-home=<dossier>` si votre Copilot CLI n'utilise pas `~/.copilot`.

Les seuils sont dans `baseline.json` : un score doit atteindre le plus haut de son minimum (critère
de succès de la spec) et de sa base moins la tolérance (0,02), qui absorbe la variabilité du LLM.
Quand les scores progressent durablement, relevez la base dans la même merge request.

### Sécurité

- Aucun jeton dans le code, les journaux, la base ou les fixtures : les jetons vivent dans le
  trousseau. Les E2E le vérifient (`secrets-at-rest.spec.ts`).
- Les données de l'utilisateur ne sont jamais journalisées, ni les entrées et sorties du LLM.
- Rien ne part vers Copilot ou Jira sans le consentement correspondant
  (`network-egress.spec.ts`).

### Commits

- [Conventional Commits](https://www.conventionalcommits.org/), en français :
  `feat(us5): création d'un ticket Jira depuis la spécification`.
- Un commit par tâche ou par groupe logique ; cocher la tâche `[X]` dans `tasks.md`.
- Stager les fichiers explicitement (`git add <fichiers>`), jamais `git add .`.
- Jamais de commit direct sur `main` : branche `feature/…`, puis merge request.
