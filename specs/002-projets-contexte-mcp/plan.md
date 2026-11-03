# Implementation Plan: Projets, contexte de projet et accès des agents de code

**Branch**: `feature/005-projets-mcp` | **Date**: 2026-11-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-projets-contexte-mcp/spec.md`

## Summary

La feature prolonge l'outil local de la feature 001 sur cinq points :
- **Projets** : chaque spécification appartient à un projet, qui peut surcharger le projet Jira par
  défaut.
- **Contexte de projet** : une fiche Markdown rédigée par l'IA à partir d'une documentation Antora
  (lien ou dossier, y compris local) et des tickets du projet Jira. L'utilisateur la relit, la
  modifie et la régénère. Elle est injectée dans toutes les opérations LLM et citée comme source.
- **Suppression** d'une version ou d'une spécification locale, sans toucher à Jira.
- **Liste des tickets Jira paginée** et liste unique de recherche paginée par 25.
- **Agents de code** :
  - accès par MCP via une passerelle stdio, `spec-writer-mcp`, qui s'authentifie par un secret du
    dossier de données ;
  - l'agent lit les features à implémenter et fait avancer leur statut ;
  - une feature « implémentée » est verrouillée.

Approche technique, détaillée dans [research.md](research.md) :
- même socle que 001 (Quarkus 4, hexagonal, SQLite + FTS5, Copilot SDK, Angular 22) et migration
  `V7` ;
- passerelle MCP minimale sans Quarkus, dans le même jar, qui appelle une API agent locale (R1,
  R2) ;
- lecture des sites Antora par leur `sitemap.xml` avec jsoup (R3) ;
- génération du contexte en map-reduce par deux nouvelles opérations LLM, avec citations
  vérifiées (R4) ;
- pagination de la liste unique par curseur sur les deux sources (R9).

## Technical Context

**Language/Version** : Java 25 LTS (backend et passerelle), TypeScript 6.0 et Node.js 24 LTS
(frontend), comme 001.

**Primary Dependencies** : celles de 001, plus **jsoup 1.23.2** (MIT) pour extraire le contenu des
pages Antora. Le protocole MCP est implémenté sans SDK, avec Jackson déjà présent (research.md R1).

**Storage** : SQLite (001), migration Flyway `V7__projects_agents.sql` : tables `project`,
`project_doc_source` et `agent_access_log`, colonnes ajoutées à `session`, `specification_version`
et `settings` ([data-model.md](data-model.md)). Le fichier `agent-endpoint.json` est dans le
dossier de données, en `rw-------`.

**Testing** : comme 001. JUnit 5, `@QuarkusTest`, WireMock (Jira, site Antora), ArchUnit
(verrou), golden set avec contexte, suite `eval` étendue, Vitest, Playwright + axe. Un client
MCP de test pilote la passerelle en E2E.

**Development Loop** : celle de 001 (R17) : dev mode piloté par le serveur MCP Quarkus, LLM
rejoué, et gates de commit inchangées.

**Target Platform** : celle de 001 (macOS, Windows, Linux), plus un second lanceur jpackage,
`spec-writer-mcp`.

**Project Type** : application web locale (backend + frontend embarqué), plus une commande
locale de passerelle MCP.

**Performance Goals** :
- une page Jira ou de recherche en moins de 3 s dans 95 % des cas (SC-006) ;
- liste puis contenu d'une feature par un agent en moins de 5 s (SC-007) ;
- passerelle prête en moins de 500 ms ;
- proposition de contexte en moins de 10 min pour 300 pages et 200 tickets (SC-004a).

**Constraints** :
- local et mono-utilisateur ;
- aucune donnée vers un nouveau service, seulement Copilot sous consentement (FR-013b) ;
- bornes : contexte de 20 000 caractères, 10 sources par projet, 1 000 pages par source, 2 Mo par
  page, 10 min de lecture, 200 tickets ;
- aucune dépendance Maven qui ne serait pas déjà dans le dépôt local (proxy du poste de
  développement).

**Scale/Scope** :
- quelques dizaines de projets et quelques centaines de spécifications par projet ;
- projets Jira de plusieurs milliers de tickets, lus par pages de 25 ;
- 5 user stories, une trentaine d'exigences.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v2.0.0.

| Principe / section | Exigence | Avant Phase 0 | Après Phase 1 | Où c'est traité |
|---|---|---|---|---|
| I. UX guidée | Ne pas reposer ce qui est connu, erreurs utiles, WCAG 2.1 AA | ✅ | ✅ | Le contexte évite les questions déjà couvertes (FR-009), mesurées par l'évaluation (SC-002). Confirmations nommées pour les suppressions, pagination annoncée (« 26 à 50 sur 120 »), axe sur les nouveaux écrans (quickstart P1 à P12) |
| II. Traçabilité, reproductibilité | Sources de chaque use case, hypothèses, structure reproductible | ✅ | ✅ | Source `PROJECT_CONTEXT` vérifiée par `TraceabilityValidator` (R5). `contextHash` dans l'empreinte (FR-011). Citations de la fiche vérifiées (R4). Numéros de version jamais réattribués (R8) |
| III. Test-First | TDD, couverture ≥ 80 % sur le cœur, dépendances externes simulées | ✅ | ✅ | Nouveaux cas d'usage dans `application`, couverts par JaCoCo. Site Antora et Jira simulés par WireMock. LLM rejoué. Passerelle testée sur des échanges JSON-RPC enregistrés |
| IV. E2E et qualité des sorties | Un E2E par parcours, golden set, évaluation, un recul bloque | ✅ | ✅ | Scénarios E2E P1 à P12. Golden set avec `context.md` (R10). Évaluation avec et sans contexte, et nouvelles métriques dans `baseline.json` |
| V. Sécurité et confidentialité | Journaux sans contenu, entrées bornées, séparation des prompts, consentement | ✅ | ✅ | `<contexte-projet>` et `<sources>` échappés et déclarés comme données (R5, contracts/llm-port.md). Pages Antora jamais stockées ni journalisées. Volume annoncé avant l'envoi à Copilot (FR-013b). Toutes les bornes sont dans [openapi.yaml](contracts/openapi.yaml) |
| Sécurité : secrets | Jamais versionnés, jamais en clair hors du stockage prévu | ✅ | ✅ | Secret d'accès des agents dans le dossier de données en `rw-------`, jamais affiché, journalisé ni renvoyé. Comparaison à temps constant. Le test SC-009 de 001 est étendu à ce fichier (R2) |
| Sécurité : isolation | Isolation vis-à-vis des autres sites et processus locaux | ✅ | ✅ | API agent séparée de l'API web, refus de tout `Origin`, contrôle `Host` conservé. Tests négatifs : ancien secret, page web, cookie sur l'API agent, secret sur l'API web (P12, SC-008) |
| Sécurité : OWASP | Top 10 et Top 10 LLM | ✅ | ✅ | A10 (SSRF) : sources limitées à `http`/`https` ou à un dossier, sans redirection hors hôte, avec bornes de taille et de temps (R3). A01 : transitions de statut autorisées par rôle (data-model). LLM01 : documentation et tickets traités comme données. LLM08 : l'agent n'a ni écriture de contenu ni appel aux services (FR-029) |
| Sécurité : dépendances | Scan à chaque build | ✅ | ✅ | jsoup est analysé par Dependency-Check comme les autres dépendances |
| Workflow : complexité justifiée | Toute nouvelle couche ou dépendance justifiée | ⚠️ justifiée | ⚠️ justifiée | Voir Complexity Tracking |
| Workflow : quality gates | `./mvnw verify`, E2E, évaluation | ✅ | ✅ | Gates de 001 inchangées. Les prompts changent, donc l'évaluation est à relancer (`ci/check-eval.sh`) |

**Résultat** : la gate est franchie. Les deux points de complexité sont justifiés ci-dessous.

## Project Structure

### Documentation (this feature)

```text
specs/002-projets-contexte-mcp/
├── plan.md              # ce fichier
├── research.md          # décisions R1 à R12
├── data-model.md        # Project, ProjectDocSource, statut, verrou, journal des agents
├── quickstart.md        # scénarios de validation P1 à P12
├── contracts/
│   ├── openapi.yaml     # ajouts à l'API locale et API agent
│   ├── mcp-tools.md     # passerelle MCP : méthodes, outils, erreurs
│   └── llm-port.md      # contexte dans les opérations, nouvelles opérations
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

Il s'agit des ajouts à l'arborescence de 001 :

```text
backend/src/main/java/dev/specwriter/
├── domain/
│   ├── project/             # Project, ProjectName, JiraProjectKey, ProjectContext, DocSource
│   ├── session/             # + ImplementationStatus (transitions par rôle), SpecificationLock
│   ├── specification/       # + SourceRef.PROJECT_CONTEXT, InputFingerprint (contextHash)
│   └── port/                # + ProjectRepository, DocumentationReader, AgentAccessLog
├── application/
│   ├── project/             # Create/Update/DeleteProject, SelectCurrentProject, MoveSession
│   ├── context/             # PreviewContextSources, ProposeProjectContext, AcceptContextProposal, EditContext
│   ├── specification/       # + DeleteVersion, EvolveSpecification, ChangeStatus
│   ├── search/              # + recherche paginée par curseur
│   ├── jira/                # + ListProjectIssues ; résolution du projet Jira effectif dans JiraAccess
│   └── agent/               # ListFeatures, GetFeature, AgentChangeStatus (+ journal)
├── infrastructure/
│   ├── docs/                # AntoraReader (sitemap, dossier, jsoup, bornes, proxy)
│   ├── web/                 # ProjectResource, ContextResource, AgentResource, AgentSecretFilter
│   ├── persistence/         # entités et repositories des nouvelles tables
│   └── platform/            # AgentEndpointFile (écriture rw-------, régénération)
└── mcp/                     # McpBridge (main sans Quarkus) : JSON-RPC stdio → /api/agent/**
backend/src/main/resources/
├── prompts/                 # + summarize-project-sources.txt, build-project-context.txt ;
│                            #   <contexte-projet> dans les 6 prompts existants
└── db/migration/V7__projects_agents.sql
backend/src/test/resources/
├── golden/0x-*/context.md   # contexte facultatif par cas, + 2 cas avec contexte
└── eval/                    # baseline.json : contextQuestionRate, questionReduction
frontend/src/app/features/
├── projects/                # liste, création, édition, suppression, projet courant (barre d'en-tête)
├── project-context/         # fiche, sources Antora, aperçu de l'envoi, proposition et diff
├── jira-issues/             # liste paginée des tickets du projet
├── specification/           # + statut, verrou, suppression de version, évolution
└── settings/                # + carte « Accès des agents » (activation, commande, régénération, journal)
e2e/
├── fixtures/antora/         # site Antora de test (sitemap, pages, erreurs)
├── mcp-client.ts            # client MCP stdio de test
└── tests/us7-projects.spec.ts … us11-agents.spec.ts
jreleaser.yml                # + lanceur spec-writer-mcp
```

**Structure Decision** : on garde la structure à deux modules de 001. La passerelle MCP est un
paquet `mcp` du module backend, livré dans le même jar avec sa propre classe `main`, sans module
Maven supplémentaire. Une règle ArchUnit lui interdit toute dépendance vers `domain`,
`application` ou `infrastructure` : elle ne parle à l'outil que par HTTP.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Passerelle MCP stdio en second point d'entrée du jar | MCP stdio est le seul mode qui permet l'authentification sans clé choisie en clarification (Q3) ; de plus, l'extension Quarkus MCP n'a pas encore de version pour Quarkus 4 (issue #1055, ouverte) | MCP HTTP servi par l'outil : clé à recopier dans la configuration de l'agent et exposition au navigateur ; seconde application Quarkus avec l'extension stdio : pas de version pour Quarkus 4 et environ 2 s de démarrage |
| Nouvelle dépendance jsoup | Extraire `article.doc` de pages HTML non fiables, sans les exécuter | Expressions régulières sur du HTML : fragiles et dangereuses ; lire les sources AsciiDoc : exige l'accès au dépôt et la résolution des inclusions |
