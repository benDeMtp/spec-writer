# Implementation Plan: Assistant local de rédaction de spécifications

**Branch**: `feature/001-local-spec-assistant` | **Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-local-spec-assistant/spec.md`

## Summary

Outil local et mono-utilisateur. À partir d'un besoin exprimé librement, il pose des questions
ciblées, puis produit une spécification : use cases, scénarios d'acceptation
« En tant que / Quand / Alors », hypothèses et exigences. Il s'intègre à Jira Server / Data
Center pour la recherche, la création de ticket et la mise à jour avec détection de conflit.

Approche technique :
- **backend** : Quarkus 4 (Java 25), architecture hexagonale, servi sur `127.0.0.1` ;
- **frontend** : Angular 22 avec Material 3, embarqué dans le jar ;
- **LLM** : quarkus-langchain4j 2.x, branché sur le **GitHub Copilot SDK Java** officiel par un
  adaptateur `ChatModel` ;
- **données** : SQLite avec FTS5 dans le dossier utilisateur standard de chaque OS ;
- **secrets** : uniquement dans le trousseau du système ;
- **livraison** : installeurs jpackage avec runtime embarqué, via JReleaser.

Les décisions et leurs alternatives sont dans [research.md](research.md).

## Technical Context

**Language/Version**: Java 25 LTS (backend), TypeScript 6.0 et Node.js 24 LTS (frontend)

**Primary Dependencies**:
- Quarkus 4.0 (Beta2, puis Final le 2026-11-25) : REST (Jackson 3), Hibernate ORM, Flyway,
  REST Client, SmallRye Health ;
- quarkus-langchain4j 2.x et `com.github:copilot-sdk-java` (preview) ;
- `io.alapierre:credential-secure-storage` (trousseau) ;
- `flexmark-java` avec `flexmark-jira-converter` (Markdown vers wiki markup Jira) ;
- Angular 22.2, Angular Material et CDK 22, `ngx-markdown`, `ngx-diff`.

**Storage**: SQLite (`sqlite-jdbc` xerial, mode WAL, dialecte Hibernate community), migrations
Flyway, index FTS5. Fichier `spec-writer.db` dans le dossier utilisateur de l'OS (research.md
R9). Secrets dans le trousseau du système.

**Testing**:
- backend : JUnit 5, `@QuarkusTest`, RestAssured, WireMock (Jira et GitHub), ArchUnit, JaCoCo ;
- frontend : Vitest ;
- E2E : Playwright avec `@axe-core/playwright` ;
- golden set rejoué avec un `ChatModel` factice ;
- suite `eval` (vrai Copilot) dans un profil Maven dédié.

**Target Platform**: poste de travail macOS (arm64 et x64), Windows 10/11 x64, Linux x64 (deb et
tar.gz). Navigateurs evergreen récents.

**Project Type**: application web locale (front et back dans un seul livrable de bureau).

**Performance Goals**:
- passage d'une question à la suivante en moins de 3 s pour 95 % des cas (SC-007), hors temps
  de réponse du LLM pour un nouveau lot ;
- démarrage jusqu'à l'ouverture du navigateur en moins de 5 s ;
- recherche locale en moins de 300 ms pour 1 000 sessions.

**Constraints**:
- écoute sur 127.0.0.1 uniquement ;
- aucun secret sur disque hors du trousseau ;
- aucun contenu utilisateur dans les journaux ;
- une seule sortie réseau à la fois, vers GitHub/Copilot ou vers Jira, chacune après
  consentement ;
- fonctionne derrière un proxy d'entreprise (proxy système et variables `HTTPS_PROXY`).

**Scale/Scope**: 1 utilisateur. Jusqu'à environ 1 000 sessions et 10 000 versions locales.
Environ 12 écrans : configuration, consentement, accueil et recherche, nouveau besoin,
questionnaire, spécification, versions, diff, modification IA, aperçu Jira, conflit, à propos.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v2.0.0.

| Principe / section | Exigence | Statut avant Phase 0 | Après Phase 1 | Où c'est traité |
|---|---|---|---|---|
| I. UX guidée | Questions déduites du besoin, lots de 5 au maximum, justification, retour en arrière, reprise, erreurs métier, WCAG 2.1 AA | ✅ | ✅ | Entité Question (`theme`, `rationale`, `batch`, `dependsOn`), `Problem.userMessage` et `action`, Material + CDK, tests axe dans chaque parcours E2E |
| II. Scénarios structurés et traçables | Format En tant que / Quand / Alors, contrôle de structure, traçabilité, hypothèses marquées, reproductibilité, export Markdown | ✅ | ✅ | [contracts/scenario-format.md](contracts/scenario-format.md), `SourceRef`, identifiants calculés par le code et réutilisation par empreinte (research.md R12) |
| III. Test-First | TDD, couverture ≥ 80 % sur le cœur, tests déterministes | ✅ | ✅ | Port `LlmClient` simulé, horloge et générateur d'UUID injectés, JaCoCo bloquant sur `domain` et `application` |
| IV. E2E et qualité des sorties | Un E2E par parcours, golden set, suite d'évaluation LLM, un recul bloque la fusion | ✅ | ✅ | Module `e2e` (V1 à V15 automatisés), `golden/`, profil `eval`, `last-run.json` vérifié en CI par `ci/check-eval.sh` |
| V. Sécurité et confidentialité | Données confidentielles, journaux sans contenu, entrées bornées, sorties échappées, séparation des prompts, consentement | ✅ | ✅ | [contracts/llm-port.md](contracts/llm-port.md), `ConsentRecord`, limites de taille dans [openapi.yaml](contracts/openapi.yaml), filtre de journalisation |
| Sécurité : secrets | Trousseau du système, jamais dans un fichier | ✅ | ✅ | research.md R8, test SC-009 (V16) |
| Sécurité : dépendances et SAST | Scan à chaque build, haute ou critique bloquante, SAST en CI | ✅ | ✅ | research.md R16 : Dependency-Check, npm audit, Semgrep, gitleaks, exécutés par Forgejo Actions (`ci.yml`) |
| Sécurité : isolation | Multi-utilisateur non concerné. Isolation vis-à-vis des autres sites et processus locaux | ✅ | ✅ | Jeton de lancement, contrôle d'Origin et du header Host (R14), test négatif V15 |
| Sécurité : stockage | Chiffrement au repos « lorsque l'hébergement le permet », rétention définie, suppression | ✅ | ✅ | Droits `rwx------` sur le dossier. Le chiffrement du disque (FileVault, BitLocker, LUKS) est recommandé dans le guide utilisateur. Rétention jusqu'à suppression par l'utilisateur, suppression physique des versions |
| Sécurité : OWASP | Top 10 et Top 10 LLM | ✅ | ✅ | A01 et A05 (R14), A03 (échappement, JQL nettoyé), LLM01 (séparation des prompts et corpus d'injection), LLM02 (sortie validée par schéma), LLM06 (aucun secret dans les prompts) |
| Workflow : complexité justifiée | Toute nouvelle couche ou dépendance justifiée | ⚠️ justifiée | ⚠️ justifiée | Voir Complexity Tracking |

**Résultat** : la gate est franchie. Aucune violation n'est laissée sans justification.

## Project Structure

### Documentation (this feature)

```text
specs/001-local-spec-assistant/
├── plan.md              # Ce fichier
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── openapi.yaml         # API HTTP front ↔ back
│   ├── scenario-format.md   # Format et contrôle des scénarios
│   └── llm-port.md          # Contrat du port LLM et règles anti-injection
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
pom.xml                              # Agrégateur Maven (modules frontend, backend)
frontend/                            # Angular 22, construit par frontend-maven-plugin
├── pom.xml
├── angular.json  package.json  proxy.conf.json
└── src/app/
    ├── core/                        # client HTTP, intercepteur Problem, état des services
    ├── features/
    │   ├── settings/                # US1 : Copilot (device flow), Jira, consentements
    │   ├── need/                    # US2 : saisie du besoin
    │   ├── questionnaire/           # US2, US3 : lots, progression, retour, passer
    │   ├── specification/           # US2, US4 : rendu, versions, export
    │   ├── search/                  # US6 : liste unifiée, provenance
    │   ├── ai-edit/                 # US6 : instruction, diff, validation
    │   └── jira/                    # US5, US6 : aperçu, création, envoi, conflit
    └── shared/ui/                   # composants Material thémés, rendu des scénarios
backend/
├── pom.xml
└── src/
    ├── main/java/dev/specwriter/
    │   ├── domain/                  # modèle et règles pures (aucune dépendance framework)
    │   │   ├── session/  question/  specification/  scenario/  jira/
    │   │   └── port/                # LlmClient, IssueTracker, SecretStore, SessionRepository, Clock…
    │   ├── application/             # cas d'usage (StartSession, AnswerQuestion, Generate, Search, PushToJira…)
    │   └── infrastructure/
    │       ├── web/                 # ressources REST, Problem, filtres de sécurité locale
    │       ├── persistence/         # entités JPA, repositories, FTS5
    │       ├── llm/                 # AI Services langchain4j + CopilotSdkChatModel
    │       ├── github/              # connexion GitHub (via le Copilot CLI, ou device flow en repli)
    │       ├── jira/                # REST Client, createmeta, conversion wiki, conflit
    │       ├── secrets/             # adaptateur trousseau, avec timeout et repli mémoire
    │       └── platform/            # dossiers utilisateur OS, ouverture du navigateur, port
    ├── main/resources/
    │   ├── application.properties
    │   └── db/migration/            # Flyway (V1__schema.sql, V2__fts5.sql…)
    └── test/
        ├── java/…                   # unit (domain), application, @QuarkusTest, ArchUnit
        └── resources/
            ├── golden/              # besoins de référence et réponses LLM enregistrées
            └── prompt-injection/
e2e/                                 # Playwright + axe, démarre le jar (profil e2e)
├── package.json  playwright.config.ts
└── tests/                           # un fichier par user story
dist/                                # JReleaser (jpackage dmg / msi / deb / tar.gz), scripts de lancement
.forgejo/workflows/                  # ci.yml (verify, Linux) et release.yml (matrice 3 OS)
ci/                                  # verify.sh, check-eval.sh
```

**Structure Decision** : application web à deux modules (`frontend`, `backend`), plus un module
de tests `e2e` indépendant du build Maven et un dossier `dist` pour le packaging. Le backend suit
l'architecture hexagonale :
- `domain` ne dépend d'aucun framework ;
- les adaptateurs (Copilot, Jira, trousseau, SQLite) se branchent sur des ports ;
- ArchUnit vérifie ces règles.

Cette structure isole les deux dépendances les plus instables (Copilot SDK en preview, Jira DC
en fin de vie) et permet des tests déterministes.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Adaptateur `ChatModel` maison au-dessus du Copilot SDK | Aucun provider Copilot dans LangChain4j. L'adaptateur garde les AI Services, la séparation des messages système et utilisateur, et le remplacement par un modèle factice en test | Appel direct à l'API interne de Copilot : contraire aux CGU. SDK utilisé sans LangChain4j : il faudrait recoder la sortie structurée et les tests factices |
| Filtre de sécurité HTTP maison (jeton de lancement et Origin) | Un serveur local est joignable par n'importe quel site ouvert dans le navigateur | CORS désactivé seul : n'empêche pas les POST « simples » venant d'un site tiers |
| Module `e2e` séparé, en TypeScript | quarkus-playwright n'est pas compatible Quarkus 4 | Tests E2E en Java : bloqués jusqu'au portage de l'extension |
| Build du front par `frontend-maven-plugin` | Quinoa n'est pas compatible Quarkus 4 | Quinoa : à réévaluer quand une version compatible sortira |
| Packaging JReleaser sur une matrice de 3 OS | « Lancé facilement » sans Java préinstallé | Archive jar et scripts : exige un JDK chez l'utilisateur. Image native : JNA, sqlite natif et runtime Copilot CLI |

## Points ouverts (non bloquants pour `/speckit-tasks`)

- **Authentification GitHub** (research.md R4), tranchée par le spike S1 :
  - voie A, préférée : réutiliser la connexion du Copilot CLI piloté par le SDK. Aucune
    application à enregistrer ;
  - voie B, repli : une GitHub App enregistrée par le mainteneur, dont le `client_id` est
    injecté au build ;
  - dans les deux cas, vérifier auprès de l'administrateur de l'organisation GitHub que les
    applications tierces et l'usage de Copilot hors IDE sont autorisés.
- **Forge et CI** : Forgejo, sur l'instance du mainteneur, avec Forgejo Actions.
  - Workflow `ci.yml` sur un runner Linux (Docker), obligatoire pour la fusion.
  - Workflow `release.yml` en matrice 3 OS. Les runners macOS et Windows, en mode host, sont à
    héberger par le mainteneur.
  - L'évaluation LLM est lancée en local. La CI vérifie son résultat committé (empreinte et
    scores, `ci/check-eval.sh`) : un recul des scores bloque donc la fusion (principe IV).
- **Signature des installeurs** (Developer ID et notarisation sur macOS, Authenticode sur
  Windows) : différée. Les avertissements Gatekeeper et SmartScreen sont documentés en
  attendant.
- **Spikes** S1 (Copilot SDK et packaging) et S2 (SQLite avec Hibernate ORM 8) à placer en tête
  de `tasks.md` (research.md, section Risques).
- **Direction visuelle** : à définir avec le skill `frontend-design` au début de l'implémentation
  du front (FR-027a).
