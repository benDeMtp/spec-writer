# Implementation Plan: Périmètre des modules touchés

**Branch**: `feature/017-perimetre-modules` (depuis `feature/016-navigation-projet`, à rebaser sur `main` après la fusion de la PR #14) | **Date**: 2026-11-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/013-perimetre-modules/spec.md`

## Summary

spec-writer recense les modules touchés par une évolution, en lisant directement les sources
déclarées du projet. Il ne passe ni par un index ni par des outils laissés à l'IA.

- **Racines de sources** :
  - nouvelle table `project_source_root` et cas d'usage `ManageSourceRoots` ;
  - section « Sources du code » des réglages du projet, avec un aperçu des modules (Maven, npm,
    Helm) ;
  - exclusions dans cet ordre : règles fixes (dossiers générés, fichiers sensibles), puis
    `.gitignore`, puis motifs de la 006.
- **Analyse** : l'adaptateur `LocalSourceTree`, en Java pur, parcourt toutes les racines en une
  passe.
  - Lecture parallèle, résultat trié, donc déterministe.
  - Chaque terme est cherché littéralement, sans tenir compte de la casse.
  - Les occurrences sont regroupées par module, celui de plus bas niveau qui les contient.
- **Propagation** :
  - par les dépendances Maven : `MavenPomReader` (parseur protégé contre XXE) alimente le graphe
    `ModuleGraph`, et les dépendants, l'agrégateur et le parent sont signalés « à vérifier » ;
  - par les relations C4 déjà indexées, sur un saut. La correspondance module/élément se fait
    par la propriété `spec-writer.module`, sinon par le nom.
- **Conversation** :
  - le plan reçoit une action `PERIMETER {terms}` ; une analyse au plus par tour, qui compte
    dans les 5 étapes ;
  - puis un appel `qualify-perimeter`, à schéma strict, qualifie chaque module ; un module omis
    reste « non qualifié », aucun n'est retiré ;
  - l'événement SSE `perimeter` est publié deux fois, avant et après la qualification ;
  - le composant `sw-perimeter-step` affiche les modules en groupes et en tableaux ;
  - les modules deviennent des sources `MODULE` citables.
- **Spécification** :
  - un instantané de la dernière analyse est rattaché à la session ;
  - la rubrique « Modules touchés » est rendue par l'outil, comme « Architecture » ;
  - les modules à vérifier deviennent des hypothèses `TOOL_INFERENCE`.

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript 5 / Angular 22 (front)

**Primary Dependencies**:
- côté backend : Quarkus 4 REST, Flyway (SQLite), LangChain4j et SDK Copilot (existants), DOM
  XML du JDK, Jackson (existant) ;
- côté front : Angular Material, `LiveAnnouncer` ;
- **aucune nouvelle dépendance**. Ni JGit, ni ripgrep, ni bibliothèque YAML (research R2 à R5).

**Storage**: SQLite. Migration `V24__source_roots_perimeter.sql`, qui crée :
- `project_source_root` ;
- `perimeter_analysis`, `perimeter_module`, `perimeter_occurrence` ;
- `session_perimeter`.

Elle ajoute aussi la colonne `knowledge_item.module_hint`. Aucune table existante n'est
reconstruite.

**Testing**:
- **JUnit 5, domaine** :
  - `GitIgnoreRules`, `SourceExclusions` ;
  - `ModuleGraph`, `PerimeterTerms`, `PerimeterAnalysis` ;
  - rendu `MarkdownRenderer` de la rubrique.
- **JUnit 5, application** :
  - `ManageSourceRoots`, `AnalyzePerimeter` ;
  - `Converse` (action `PERIMETER`) ;
  - `StartFromConversation`, et `GenerateSpecification` pour les hypothèses.
- **JUnit 5, infrastructure** :
  - `LocalSourceTree` (arbres temporaires) ;
  - `MavenPomReader` ;
  - `StructurizrReader` (`moduleHint`).
- **IT** :
  - migration V24 ;
  - `SourceRootResource` ;
  - flux SSE `perimeter` avec rejeu ;
  - détail de la conversation.
- **Vitest** : `sw-source-roots`, `sw-perimeter-step`, `conversation-thread` (événement,
  citation `MODULE`), panneau du brouillon.
- **Playwright** : `us16-perimetre` (quickstart 1 à 15), axe.
- **Évaluation** : `PerimeterEval` (tag `eval`), avec les métriques `PERIMETER_TERM_RECALL` et
  `PERIMETER_QUALIFICATION` dans `baseline.json`. Elle tourne avec le vrai Copilot, et **seulement
  avec l'accord de l'utilisateur**.
- **Performance** : tag `perf`, hors gates (SC-003).

**Target Platform**: poste local (macOS, Windows, Linux), navigateur récent

**Project Type**: application web locale (backend + front)

**Performance Goals**:
- analyse de 5 termes sur 60 modules et 20 000 fichiers : moins de 30 s (SC-003) ;
- aperçu des réglages (descripteurs seulement) : moins de 5 s sur rv ;
- qualification : un appel LLM borné à 40 000 caractères.

**Constraints**:
- **Lecture seule** : aucun processus lancé, aucun outil Copilot (FR-022).
- Liens symboliques jamais suivis, chemins vérifiés sous la racine réelle (FR-023).
- **Contenu jamais journalisé** : ni chemins, ni termes, ni extraits (FR-024).
- Extraits de 200 caractères au plus, échantillons de 20 au plus par module et par terme (FR-025).
- Limites : 10 racines par projet ; par analyse, 10 termes, 50 000 fichiers et 120 s ;
  1 Mo par fichier.
- Résultat déterministe (FR-017).
- WCAG 2.1 AA.
- Migration ajoutée, jamais modifiée.

**Scale/Scope**:
- un utilisateur, des arbres de 10 à 100 modules ;
- environ 25 fichiers backend (dont 12 nouveaux) et 10 fichiers front ;
- 2 prompts : `converse-plan` modifié, `qualify-perimeter` nouveau.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | L'analyse se lance d'une question en langage naturel, et les termes sont proposés par l'agent. Le résultat est lisible : groupes, raisons, preuves. Les messages disent quoi faire (racine absente, terme trop générique, aucune racine, avec le lien vers les réglages). WCAG AA : tableaux, qualification en texte, annonces, axe. | OK |
| II. Scénarios traçables | La génération et ses prompts ne changent pas. La rubrique « Modules touchés » est rendue par l'outil à partir de l'instantané, jamais par le LLM. Chaque module y porte sa preuve. Les modules à vérifier deviennent des hypothèses explicites. Le rendu reste reproductible. | OK |
| III. Test-First | Toute la logique tient dans des classes pures du domaine (gitignore, graphe, termes, qualification) et dans un cas d'usage testé avec des doubles. Le parcours est testé sur des arbres temporaires. Le LLM reste derrière `ConversationLlm`. | OK |
| IV. E2E et qualité des sorties | Parcours complet en E2E avec rejeu, dont une qualification qui omet un module. Nouveau prompt, donc nouvelle évaluation `PerimeterEval` avec seuil dans `baseline.json`. Le golden set de génération est inchangé : la rubrique est déterministe. | OK |
| V. Sécurité | Lecture seule, sans processus. Fichiers sensibles exclus d'office. Liens symboliques et sorties de racine refusés. Parseur XML protégé contre XXE. Extraits bornés et transmis en blocs échappés, sous la règle de sécurité, avec un test d'injection placé dans un commentaire de code. Rien de journalisé. L'utilisateur est informé de l'envoi d'extraits (FR-026), et le consentement COPILOT est exigé. | OK |
| Complexité | Pas de nouvelle dépendance ni de nouvelle couche. Deux parseurs maison bornés et testés : `.gitignore` (environ 100 lignes) et `Chart.yaml` (ligne `name:`). Ils sont préférés à JGit et à une bibliothèque YAML (R3, R4). | OK, justifié |

Re-vérification après la phase 1 : inchangée, aucune violation.

## Project Structure

### Documentation (this feature)

```text
specs/013-perimetre-modules/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/api.md
├── contracts/ui.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/dev/specwriter/
├── domain/sources/        SourceRoot, SourceExclusions, GitIgnoreRules, GitIgnoreStack, Module, ModuleKind,
│                          ModuleDescriptor, ModuleGraph, PerimeterTerms, PerimeterAnalysis, PerimeterModule,
│                          Reason, Qualification, Occurrence, PerimeterSnapshot
├── domain/port/           SourceRootRepository, SourceTree, PerimeterRepository ; ConversationLlm (+PERIMETER, qualify)
├── domain/knowledge/      ArchElement (+moduleHint)
├── domain/conversation/   SourceKind (+MODULE)
├── domain/specification/  MarkdownRenderer (rubrique « Modules touchés »)
├── application/sources/   ManageSourceRoots, AnalyzePerimeter
├── application/conversation/ Converse, TurnEvents (+Perimeter), StartFromConversation, DraftNeed (annonce)
├── application/…/GenerateSpecification (+ hypothèses TOOL_INFERENCE)
├── infrastructure/sources/ LocalSourceTree, MavenPomReader, SqliteSourceRootRepository
├── infrastructure/persistence/conversation/ SqlitePerimeterRepository
├── infrastructure/architecture/StructurizrReader (spec-writer.module) ; knowledge/SqliteKnowledgeIndex (module_hint)
├── infrastructure/llm/    LlmSchemas (CONVERSE_PLAN, PERIMETER_QUALIFICATION), UserMessages, LangChainConversationLlm
├── infrastructure/web/    SourceRootResource, ConversationResource (perimeter SSE, perimeters), ProjectResource
├── infrastructure/config/UseCaseProducer
└── resources/
    ├── db/migration/V24__source_roots_perimeter.sql
    └── prompts/converse-plan.txt (modifié), qualify-perimeter.txt (nouveau), converse-answer.txt (sources MODULE)

frontend/src/app/
├── core/projects/         source-roots.api.ts, projects.model.ts (SourceRoot)
├── core/conversations/    conversations.model.ts (PerimeterView, event perimeter), conversations.api.ts (NAMES)
├── features/project-settings/ source-roots.component.ts, project-settings.page.ts
└── features/conversation/ perimeter-step.component.ts, conversation-thread.component.ts, need-draft.component.ts

e2e/fixtures/sources/demo-rv/ ; e2e/fixtures/llm/5x-*.json ; e2e/fixtures/c4 (relation) ; e2e/tests/us16-perimetre.spec.ts
backend/src/test/resources/llm-replay/ ; backend/src/test/resources/eval/perimeter/ ; backend/src/test/java/dev/specwriter/eval/PerimeterEval.java
README.md (Sources du code, analyse de périmètre) ; specs/002-projets-contexte-mcp/contracts/openapi.yaml
```

**Structure Decision**: on garde l'architecture hexagonale.
- Le nouveau sous-domaine `sources` contient les règles pures : exclusions, gitignore, graphe,
  qualification.
- Deux ports, `SourceTree` et `PerimeterRepository`, isolent le système de fichiers et SQLite.
- `AnalyzePerimeter` orchestre et reste testable avec des doubles.
- La conversation n'apprend qu'une action de plus.

## Complexity Tracking

| Écart | Pourquoi | Alternative plus simple écartée |
|---|---|---|
| Parseur `.gitignore` maison | La sémantique git (négation, ancrage, dossiers imbriqués) est nécessaire pour ne pas compter les fichiers générés non standards. | JGit : dépendance lourde et surface de CVE pour 100 lignes. Ignorer `.gitignore` : périmètre pollué. |
| Second appel LLM (`qualify-perimeter`) | Une qualification structurée est vérifiable, cohérente avec la liste, rejouable et évaluable. | Qualifier dans la réponse en flux : texte libre, impossible de garantir qu'aucun module n'est omis ni mal qualifié. |
