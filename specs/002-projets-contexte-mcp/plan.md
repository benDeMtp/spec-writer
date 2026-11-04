# Implementation Plan: Projets, connaissance du projet et accès des agents de code

**Branch**: `feature/005-projets-mcp` | **Date**: 2026-11-03, révisé le 2026-11-04 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-projets-contexte-mcp/spec.md`

## Summary

La feature prolonge l'outil local de la feature 001 sur cinq points :
- **Projets** : chaque spécification appartient à un projet, qui peut surcharger le projet Jira par
  défaut.
- **Connaissance du projet** (révisée le 2026-11-04, conception
  `docs/superpowers/specs/2026-11-04-connaissance-du-projet-design.md`) :
  - un **index local** par projet (tickets Jira en cache, sections et ADR Antora, modèle
    d'architecture Structurizr), tenu à jour par synchronisation incrémentale en tâche de fond ;
  - une **recherche hybride** (vecteurs d'un modèle local et plein texte) des éléments proches de
    chaque besoin ;
  - les **tickets proches** montrés avant les questions, avec le parcours selon leur statut (story
    liée « relates to », comparaison, bascule sur le ticket) ;
  - une rédaction qui cite les ADR et les écarts, les modules impactés, et des **faits calculés
    par l'outil** : flux manquants et efforts de déploiement en production ;
  - une **fiche générale** à la main qui surcharge les sources ;
  - la connaissance transmise aux agents de code, avec un outil de recherche MCP.
  Elle remplace la génération de deux fiches par l'IA, qui butait sur le volume des sources.
- **Suppression** d'une version ou d'une spécification locale, sans toucher à Jira.
- **Liste des tickets Jira paginée** et liste unique de recherche paginée par 25.
- **Débit vers Jira limité** à 8 requêtes par seconde, les requêtes en excès attendant leur tour (FR-022a, research.md R13).
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
- lecture des sites Antora par leur `sitemap.xml` avec jsoup (R3), découpés en sections et ADR
  (R20) ;
- index SQLite (`knowledge_item`, FTS5, vecteurs en BLOB) et recherche hybride en mémoire (R18) ;
- embeddings locaux par `OnnxEmbeddingModel` de LangChain4j et `multilingual-e5-small` quantifié,
  téléchargé une fois (R19) ;
- workspace Structurizr lu par l'analyseur officiel en mode restreint, propre ou commun (R21) ;
- flux et déploiements vérifiés par l'outil sur le graphe, l'IA ne choisissant que les modules et
  les échanges supposés (R22) ;
- trois petites opérations LLM bornées : `judgeRelatedIssues`, `compareWithIssue`,
  `identifyImpact` (R22, R23) ;
- pagination de la liste unique par curseur sur les deux sources (R9).

## Technical Context

**Language/Version** : Java 25 LTS (backend et passerelle), TypeScript 6.0 et Node.js 24 LTS
(frontend), comme 001.

**Primary Dependencies** : celles de 001, plus :
- **jsoup 1.23.2** (MIT) pour extraire le contenu des pages Antora ;
- **langchain4j-embeddings 1.21.0-beta31** (Apache 2.0, aligné sur `langchain4j-core` 1.21.0), qui
  apporte **onnxruntime 1.20.0** (MIT) et les **tokenizers DJL 0.36.0** (Apache 2.0), pour les
  embeddings locaux (research.md R19) ;
- **structurizr-dsl 6.2.4** (Apache 2.0, Java 21+), pour lire le workspace d'architecture
  (research.md R21).
Modèle téléchargé à l'usage : `Xenova/multilingual-e5-small` quantifié (118 Mo, MIT). Le
protocole MCP est implémenté sans SDK, avec Jackson déjà présent (research.md R1).

**Storage** : SQLite (001), migrations Flyway à partir de `V7` : tables `project`,
`project_doc_source`, `agent_access_log`, `knowledge_item` (+ FTS5 `knowledge_fts`),
`arch_relation` et `arch_deployment` ; colonnes ajoutées à `session`, `specification_version` et
`app_settings` ; fusion des fiches de V9 dans la fiche générale ([data-model.md](data-model.md)).
Dans le dossier de données : `agent-endpoint.json` en `rw-------`, et `models/<nom>/` pour le
modèle d'embedding.

**Testing** : comme 001. JUnit 5, `@QuarkusTest`, WireMock (Jira, site Antora), ArchUnit
(verrou), golden set avec connaissance, suite `eval` étendue, Vitest, Playwright + axe. Un client
MCP de test pilote la passerelle en E2E. Jeu de référence fictif dans `verify`, jeu réel hors
dépôt (tag `reference`, research.md R27). Un petit modèle ONNX de test évite tout téléchargement
dans les tests.

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
- première indexation en moins de 10 min pour 300 pages, 200 tickets et 2 000 lignes de DSL ;
  synchronisation sans changement en moins de 30 s ; tickets proches en moins de 5 s (SC-004a) ;
- recherche hybride en moins de 50 ms sur 10 000 éléments ; un vecteur en moins de 50 ms.

**Constraints** :
- local et mono-utilisateur ;
- aucune donnée vers un nouveau service : Copilot (extraits retrouvés seulement, sous
  consentement) et Jira ; le seul nouveau téléchargement est celui du modèle, sans donnée envoyée
  (FR-012) ;
- bornes : fiche générale de 20 000 caractères, 10 sources par projet, 1 000 pages par source,
  2 Mo par page, 8 000 caractères par élément indexé, 35 000 caractères de connaissance par appel
  LLM ;
- anti-objectif : aucun réglage ni relance pour que la rédaction aboutisse (SC-004b) ; aucune
  erreur de la connaissance ne fait échouer une rédaction (FR-043) ;
- dépendances Maven téléchargées par le proxy du poste (Maven Central joignable, vérifié le
  2026-11-04).

**Scale/Scope** :
- quelques dizaines de projets et quelques centaines de spécifications par projet ;
- projets Jira de plusieurs milliers de tickets, lus par pages de 25 ;
- index de quelques milliers d'éléments par projet, 10 000 au plus visés ;
- 5 user stories, une quarantaine d'exigences.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v2.0.0.

| Principe / section | Exigence | Avant Phase 0 | Après Phase 1 | Où c'est traité |
|---|---|---|---|---|
| I. UX guidée | Ne pas reposer ce qui est connu, erreurs utiles, WCAG 2.1 AA | ✅ | ✅ | La connaissance retrouvée évite les questions déjà couvertes (FR-009, SC-003). Tickets proches montrés avant les questions (FR-034). Indexation qui ne bloque jamais la saisie, résultats partiels annoncés (FR-033, FR-043). Motifs d'erreur précis (dossier, workspace avec fichier et ligne). Confirmations nommées pour les suppressions, pagination annoncée (« 26 à 50 sur 120 »), axe sur les nouveaux écrans (quickstart P1 à P12) |
| II. Traçabilité, reproductibilité | Sources de chaque use case, hypothèses, structure reproductible | ✅ | ✅ | Sources `PROJECT_CONTEXT`, `JIRA_ISSUE`, `DOC_PAGE`, `ARCH_ELEMENT` vérifiées contre la sélection (R24). Sélection de connaissance enregistrée avec la version et dans l'empreinte (FR-011). Flux et déploiements calculés de façon déterministe (R22). Numéros de version jamais réattribués (R8) |
| III. Test-First | TDD, couverture ≥ 80 % sur le cœur, dépendances externes simulées | ✅ | ✅ | Nouveaux cas d'usage dans `application`, couverts par JaCoCo. Site Antora et Jira simulés par WireMock. LLM rejoué. Passerelle testée sur des échanges JSON-RPC enregistrés |
| IV. E2E et qualité des sorties | Un E2E par parcours, golden set, évaluation, un recul bloque | ✅ | ✅ | Scénarios E2E P1 à P12 (P5 à P8 réécrits). Jeu de référence fictif dans `verify`, jeu réel hors dépôt (R27). Évaluation avec connaissance |
| V. Sécurité et confidentialité | Journaux sans contenu, entrées bornées, séparation des prompts, consentement | ✅ | ✅ | `<connaissance-projet>` échappée et déclarée comme donnée (R24, contracts/llm-port.md). Index dans le dossier de données, jamais journalisé ; seuls des extraits retrouvés vont à Copilot (FR-012). Embeddings calculés en local. DSL analysé en mode restreint, sans script (R21). Toutes les bornes sont dans [openapi.yaml](contracts/openapi.yaml) |
| Sécurité : secrets | Jamais versionnés, jamais en clair hors du stockage prévu | ✅ | ✅ | Secret d'accès des agents dans le dossier de données en `rw-------`, jamais affiché, journalisé ni renvoyé. Comparaison à temps constant. Le test SC-009 de 001 est étendu à ce fichier (R2) |
| Sécurité : isolation | Isolation vis-à-vis des autres sites et processus locaux | ✅ | ✅ | API agent séparée de l'API web, refus de tout `Origin`, contrôle `Host` conservé. Tests négatifs : ancien secret, page web, cookie sur l'API agent, secret sur l'API web (P12, SC-008) |
| Sécurité : OWASP | Top 10 et Top 10 LLM | ✅ | ✅ | A10 (SSRF) : sources limitées à `http`/`https` ou à un dossier, sans redirection hors hôte, avec bornes de taille et de temps (R3). A01 : transitions de statut autorisées par rôle (data-model). LLM01 : documentation et tickets traités comme données. LLM08 : l'agent n'a ni écriture de contenu ni appel aux services (FR-029) |
| Sécurité : dépendances | Scan à chaque build | ✅ | ⚠️ à vérifier | jsoup, langchain4j-embeddings, onnxruntime, tokenizers DJL et structurizr-dsl passent Dependency-Check comme les autres ; une CVE bloquante se traite par exclusion ou mise à jour, avant la première tâche qui les utilise. Modèle téléchargé vérifié par SHA-256 (R19) |
| Workflow : complexité justifiée | Toute nouvelle couche ou dépendance justifiée | ⚠️ justifiée | ⚠️ justifiée | Voir Complexity Tracking |
| Workflow : quality gates | `./mvnw verify`, E2E, évaluation | ✅ | ✅ | Gates de 001 inchangées ; le retrait des fiches générées se fait tâche par tâche, toujours vert (R28). Les prompts changent, donc l'évaluation est à relancer (`ci/check-eval.sh`) |

**Résultat** : la gate est franchie. Les deux points de complexité sont justifiés ci-dessous.

## Project Structure

### Documentation (this feature)

```text
specs/002-projets-contexte-mcp/
├── plan.md              # ce fichier
├── research.md          # décisions R1 à R28 (R18 à R28 : connaissance du projet)
├── data-model.md        # Project, sources, index de connaissance, statut, verrou, journal des agents
├── quickstart.md        # scénarios de validation P1 à P12
├── contracts/
│   ├── openapi.yaml     # ajouts à l'API locale et API agent
│   ├── mcp-tools.md     # passerelle MCP : méthodes, outils, erreurs
│   └── llm-port.md      # connaissance dans les opérations, nouvelles opérations
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

Il s'agit des ajouts à l'arborescence de 001 :

```text
backend/src/main/java/dev/specwriter/
├── domain/
│   ├── project/             # Project, ProjectName, JiraProjectKey, ProjectContext (fiche générale), DocSource
│   ├── knowledge/           # KnowledgeItem, KnowledgeSelection, ArchitectureModel (graphe, flux, déploiements)
│   ├── session/             # + ImplementationStatus (transitions par rôle), SpecificationLock
│   ├── specification/       # + SourceRef.PROJECT_CONTEXT, InputFingerprint (contextHash)
│   └── port/                # + ProjectRepository, DocumentationReader, AgentAccessLog,
│                            #   KnowledgeIndex, EmbeddingPort, ArchitectureReader
├── application/
│   ├── project/             # Create/Update/DeleteProject, SelectCurrentProject, MoveSession
│   ├── context/             # EditContext (fiche générale) ; Indexations (ex-ContextGenerations)
│   ├── knowledge/           # SynchronizeKnowledge, SearchKnowledge, SelectKnowledge,
│                            #   FindRelatedIssues, CompareWithIssue, ResolveRelatedIssue, AnalyzeArchitecture
│   ├── specification/       # + DeleteVersion, EvolveSpecification, ChangeStatus
│   ├── search/              # + recherche paginée par curseur
│   ├── jira/                # + ListProjectIssues ; résolution du projet Jira effectif dans JiraAccess
│   └── agent/               # ListFeatures, GetFeature, AgentChangeStatus (+ journal)
├── infrastructure/
│   ├── docs/                # AntoraReader (sitemap, dossier, jsoup, sections, ADR, bornes, proxy)
│   ├── knowledge/           # SqliteKnowledgeIndex (FTS5, vecteurs, fusion par rang)
│   ├── embedding/           # OnnxEmbeddingAdapter, ModelDownloader (SHA-256, proxy)
│   ├── architecture/        # StructurizrReader (analyseur DSL restreint, propre ou commun)
│   ├── web/                 # ProjectResource, ContextResource, AgentResource, AgentSecretFilter
│   ├── persistence/         # entités et repositories des nouvelles tables
│   └── platform/            # AgentEndpointFile (écriture rw-------, régénération)
└── mcp/                     # McpBridge (main sans Quarkus) : JSON-RPC stdio → /api/agent/**
backend/src/main/resources/
├── prompts/                 # + judge-related-issues.txt, compare-with-issue.txt, identify-impact.txt ;
│                            #   <connaissance-projet> dans les 6 prompts existants
└── db/migration/V7__projects_agents.sql
backend/src/test/resources/
├── golden/0x-*/context.md   # fiche générale facultative par cas
├── knowledge/               # jeu de référence fictif : tickets, site Antora, workspace C4, cases.json
├── model/                   # petit modèle ONNX de test
└── eval/                    # baseline.json : contextQuestionRate, questionReduction
frontend/src/app/features/
├── projects/                # liste, création, édition, suppression, projet courant (barre d'en-tête)
├── project-context/         # fiche générale, sources (Antora, Jira, workspace), indexation suivie
├── related-issues/          # tickets proches, comparaison, choix (session)
├── jira-issues/             # liste paginée des tickets du projet
├── specification/           # + statut, verrou, suppression de version, évolution
└── settings/                # + carte « Accès des agents » (activation, commande, régénération, journal)
e2e/
├── fixtures/jira/__files/antora/  # site Antora de test (sitemap, pages, erreurs, ADR, glossaire)
├── fixtures/c4/             # workspace Structurizr de test (extends, include, topic, déploiements)
├── fixtures/model/          # petit modèle ONNX de test
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
| Moteur d'embedding local (langchain4j-embeddings, onnxruntime, tokenizers DJL) et modèle téléchargé | La recherche par le sens est exigée (FR-032) et doit rester locale (choix de l'utilisateur) ; le SDK Copilot n'a pas d'API d'embedding | Plein texte seul : ne rapproche pas des formulations sans mot commun ; tri par Copilot : écarté par l'utilisateur, coûteux en crédits quand le projet grossit ; service d'embedding distant : envoie tout l'index hors du poste |
| Analyseur structurizr-dsl | Lire un workspace qui étend un modèle commun et inclut des fichiers, comme Structurizr lui-même (FR-038) | `workspace.json` : en retard sur le DSL ; analyseur maison : `extends`, `!include`, `!element` et `archetypes` sont longs à refaire et fragiles |
| Nouvelle dépendance jsoup | Extraire `article.doc` de pages HTML non fiables, sans les exécuter | Expressions régulières sur du HTML : fragiles et dangereuses ; lire les sources AsciiDoc : exige l'accès au dépôt et la résolution des inclusions |
