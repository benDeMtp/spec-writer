# Implementation Plan: Interview d'une spécification avec les sachants

**Branch**: `feature/025-interview-specification` | **Date**: 2026-11-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/021-interview-specification/spec.md`

## Summary

Le mainteneur relit une ou plusieurs spécifications avec les sachants, sur un écran partagé. Il
répond aux hypothèses, écarts entre sources et points ouverts, et note remarques et questions sur
n'importe quel élément. À la fin, il copie un compte rendu et fait réviser chaque spécification en
une fois.

**Séance sans modèle**
- **Préparation, parcours et compte rendu** : ce sont des données propres à l'outil (migration
  V30) et du code déterministe. La séance n'attend jamais le modèle (SC-001).
- **Écran** : une route de présentation, sans menu ni notification (research.md R6).

**Révision par le chemin de la 011**
- Les décisions entrent dans la discussion de la spécification comme un seul message de séance,
  puis `ProposeRevision` rédige.
- Un post-traitement déterministe (`InterviewOutcome`) garantit ensuite le sort des points :
  tranchés retirés, reportés ajoutés comme points ouverts (R3, R4).
- La relecture en diff et « Appliquer » sont ceux de la 011.

**Format de la spécification**
- **Point ouvert** : `OpenQuestion`, code `Q-nn`.
- **Ancre** : `Anchor` sur les hypothèses, les écarts (qui reçoivent un code `EC-nn`) et les points
  ouverts.
- **Production** : ancres et points ouverts viennent des consignes, avec une évaluation étendue
  (R1, R2, R10).

**Sources de vérité**
- **Création** : des « à reporter » naissent à l'application de la révision, avec l'empreinte de
  la source.
- **Source modifiée** : calculée à la lecture, sans toucher à la synchronisation.
- **Lecture seule** : aucune écriture vers les sources (R8).

## Technical Context

**Language/Version**: Java 25 (backend Quarkus 4), TypeScript (Angular 22)

**Primary Dependencies**: aucune nouvelle.
- **Backend** : quarkus-langchain4j (consignes et schéma de sortie), Hibernate ORM et SQLite,
  Flyway.
- **Front** : Angular Material, CDK (`LiveAnnouncer`, `FocusTrap`), `ngx-markdown` déjà présent,
  API Clipboard du navigateur.

**Storage**: SQLite.
- Migration `V30__interview.sql` : `interview`, `interview_spec`, `interview_point`,
  `source_update`, et la colonne `conversation_message.interview_id`.
- Format JSON des versions étendu sans migration de données (champs facultatifs).

**Testing**:
- **Unitaires** : JUnit 5 sur le domaine (`Anchor`, `IdentifierAssigner`, `InterviewOutcome`,
  `InterviewReport`, ordre de parcours) et l'application (cas d'usage de séance, horloge et
  identifiants simulés).
- **Intégration** : `@QuarkusTest` pour les routes et la persistance.
- **Architecture et front** : ArchUnit, puis Vitest pour les composants.
- **E2E** : Playwright `us21-interview` sur le jar, avec axe et les rejeux LLM `IT-INTERVIEW`.
- **Évaluation** : nouveaux critères `anchoredRate` et `decidedRemoved` (R10).

**Target Platform**: poste de l'utilisateur (macOS, Windows, Linux), navigateur récent.

**Project Type**: application web locale (backend et frontend).

**Performance Goals**: toute action de séance (avancer, répondre, enregistrer) répond en moins
d'une seconde (SC-001), et une séance tient 200 points par spécification.

**Constraints**:
- **Séance** : aucun appel au modèle avant « Terminer », et aucune écriture vers Confluence,
  OneDrive, SharePoint ou Jira (FR-030).
- **Bundle initial** : moins de 500 kB. Préparation, séance et récapitulatif sont chargés à la
  demande.
- **Spécifications existantes** : elles restent lisibles (FR-021).

**Scale/Scope**: quelques dizaines de spécifications par projet, une séance de 1 à 5
spécifications, environ 50 points.
- **Routes** : 15 REST.
- **Écrans** : 3 nouveaux (préparation, séance, récapitulatif) et 2 enrichis (« Spécifications »
  et la page d'une spécification).
- **Consignes** : 5 modifiées.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | Parcours un élément à la fois avec progression visible. Reprise sans perte (FR-006). Messages métier (séance en cours, Copilot indisponible avec la marche à suivre). Tout au clavier, WCAG 2.1 AA (contracts/ui.md). | OK |
| II. Scénarios structurés et traçables | Une phrase issue d'une séance cite la séance (FR-017). Les hypothèses restent explicites et rattachées. Les points ouverts sont une nouvelle forme d'incertitude marquée, jamais un comportement inventé. Les points tranchés et reportés sont traités par le code (R3), donc le résultat est reproductible à entrées identiques. Export Markdown enrichi. | OK |
| III. Test-First | TDD sur le domaine et l'application. Horloge, identifiants et modèle simulés. Couverture ≥ 80 % sur `domain` et `application` par des tests unitaires (piège JaCoCo du CLAUDE.md). | OK |
| IV. Bout en bout et qualité des sorties | `us21-interview` couvre préparation → séance → compte rendu → révision → à reporter. Le golden set est étendu (ancres, révision de séance), et l'évaluation ne doit pas reculer (SC-008). | OK |
| V. Sécurité et confidentialité | Participants et points restent sur le poste. Ils ne vont au modèle que par la révision, sous le consentement Copilot existant. Entrées bornées (noms 120, points 5 000, 200 points). Le compte rendu HTML est échappé. Le message de séance est du contenu utilisateur, séparé des consignes, avec un test d'injection (texte de point qui se fait passer pour une consigne). Rien dans les journaux hors identifiants. | OK |
| Exigences de sécurité | Aucun secret, aucune dépendance, aucune écriture vers un service tiers. | OK |
| Workflow et gates | Constitution Check refait ci-dessous. Gates `verify`, E2E et évaluation avant chaque commit. README mis à jour. | OK |

**Après la phase de conception** : vérification refaite.
- **Révision non déterministe** : c'est le seul point sensible, puisque le modèle rédige la
  nouvelle version. Il est encadré par le post-traitement déterministe (R3), le diff relu avant
  « Appliquer » et le critère d'évaluation `decidedRemoved`.
- **Écarts** : aucun.

## Project Structure

### Documentation (this feature)

```text
specs/021-interview-specification/
├── plan.md
├── research.md          # R1–R12
├── data-model.md
├── quickstart.md        # V1–V6
├── contracts/
│   ├── rest-api.md      # séances, points, compte rendu, révision, à reporter
│   ├── llm-output.md    # ancres, points ouverts, fromCode, message de séance
│   └── ui.md            # page Spécifications, préparation, séance, récapitulatif
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/fr/kawamind/
├── domain/specification/
│   ├── Anchor.java                  # nouveau, validation contre une version
│   ├── OpenQuestion.java            # nouveau
│   ├── Assumption.java, SourceDeviation.java      # + anchor, fromCode (+ code pour l'écart)
│   ├── SpecificationContent.java    # + openQuestions
│   ├── IdentifierAssigner.java      # ancres par position → codes, Q-nn, EC-nn
│   └── MarkdownRenderer.java        # rubrique « Points ouverts », élément de chaque point
├── domain/llm/DraftSpecification.java              # DraftAnchor, DraftOpenQuestion, fromCode
├── domain/interview/                # nouveau
│   ├── Interview.java, InterviewSpec.java, InterviewPoint.java   # états et règles
│   ├── Walkthrough.java             # ordre de parcours d'une version (FR-003)
│   ├── InterviewOutcome.java        # post-traitement déterministe de la révision (R3)
│   ├── InterviewReport.java         # compte rendu HTML et texte (R7)
│   ├── PendingPoints.java           # points à traiter d'une spécification (FR-025)
│   └── SourceUpdate.java
├── domain/port/InterviewRepository.java, SourceUpdateRepository.java
├── domain/port/KnowledgeIndex.java  # + fingerprint(projectId, kind, ref)
├── application/interview/           # nouveau
│   ├── PrepareInterview.java        # créer, modifier, supprimer une séance préparée
│   ├── RunInterview.java            # démarrer, curseur, points, terminer
│   ├── InterviewRevision.java       # message de séance → ProposeRevision (R4)
│   └── SourceUpdates.java           # création à l'application, état, clôture
├── application/conversation/ProposeRevision.java   # + outcome, titre « Séance du … »
├── application/specification/ProposalStore.java    # + interviewId
├── infrastructure/persistence/interview/           # entités et dépôts JPA
├── infrastructure/knowledge/SqliteKnowledgeIndex.java  # fingerprint
├── infrastructure/llm/LlmSchemas.java              # schéma de sortie étendu
├── infrastructure/web/InterviewResource.java, SourceUpdateResource.java
├── infrastructure/web/SessionViews.java            # pending, openQuestions
└── infrastructure/config/UseCaseProducer.java

backend/src/main/resources/
├── db/migration/V30__interview.sql
└── prompts/{generate,revise,edit}-specification.txt, structure-imported-text.txt, merge-conflict.txt

frontend/src/app/
├── core/interviews/                 # interviews.api.ts, interviews.model.ts, presentation.ts
├── features/interview/              # chargé à la demande
│   ├── interview-preparation.page.ts
│   ├── interview.page.ts            # écran de séance
│   ├── walkthrough.ts               # ordre et navigation (miroir de Walkthrough)
│   ├── point-editor.component.ts
│   └── interview-recap.page.ts      # compte rendu, révisions, sources
├── features/sessions/sessions.page.ts               # compteurs, filtre, sélection, séances préparées
├── features/specification/specification.page.ts     # Faire le point, Points ouverts, ancres, sources, séances
├── core/events/app-events.service.ts                 # notifications suspendues en présentation
└── app.ts                                            # rail masqué en présentation

e2e/tests/us21-interview.spec.ts
e2e/fixtures/llm/                                     # rejeux IT-INTERVIEW
backend/src/test/resources/eval/                      # golden set : ancres, révision de séance
README.md                                             # faire le point avec les sachants
```

**Structure Decision**: la répartition hexagonale existante est gardée.
- **`domain/interview`** : un nouveau sous-domaine, sans dépendance au framework, qui porte les
  règles (états, parcours, compte rendu, post-traitement).
- **`application/interview`** : il orchestre, et réutilise `ProposeRevision` au lieu de dupliquer
  la révision.
- **Front** : l'écran de séance est une route de présentation du shell existant (R6), pas une
  fenêtre ni une application à part.

## Complexity Tracking

Aucune violation de la constitution à justifier. Deux choix de complexité, justifiés en recherche :
- **Ancres par position dans la sortie du modèle (R1)**, plutôt que par code : les codes n'existent
  pas encore quand le modèle rédige.
- **Post-traitement déterministe en plus du modèle (R3)** : c'est le seul moyen de garantir
  FR-015, puisque la révision réécrit toute la spécification.
