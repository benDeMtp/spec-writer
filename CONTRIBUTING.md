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

# Suite d'évaluation LLM (vrai Copilot, consomme des AI Credits)
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

### Quality gates (CI bloquante)

| Gate | Seuil |
|---|---|
| Tests unitaires, d'intégration et E2E | tous au vert |
| Couverture JaCoCo de `domain` et `application` | 80 % par package |
| SpotBugs, PMD, ESLint | aucune alerte |
| Accessibilité (axe, dans les E2E) | aucune violation WCAG 2.1 AA |
| OWASP Dependency-Check, `npm audit` | aucune vulnérabilité haute |
| Semgrep, gitleaks | aucun constat |
| Évaluation LLM (`ci/check-eval.sh`, à venir : T138) | à jour si les prompts changent, scores au-dessus de la base |

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
