# Implementation Plan: Joindre un document à une spécification

**Branch**: `feature/013-joindre-document` (depuis `main`) | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/009-joindre-document/spec.md`

## Summary

Un bouton « Joindre un document » (écran du besoin, questionnaire, spécification) envoie le contenu
d'un fichier Word, Excel, PDF ou HTML au backend local, qui l'extrait avec les lecteurs et les bornes
de la 006 dans un fichier temporaire aussitôt supprimé, et garde seulement le texte extrait : en
mémoire tant que la session n'est pas démarrée, puis en base avec la session (V17), supprimé avec
elle. Ce texte est transmis comme un document désigné avec @ (007), dans l'ordre mentions du besoin,
documents joints, mentions des réponses et de l'instruction, dans le budget du modèle choisi (008) ;
son état s'affiche comme celui d'une mention. Il ne sort jamais de l'outil : la spécification le
cite par son titre, sans extrait. Prompts et `infrastructure/llm` inchangés.

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript 5 / Angular 22 (front)

**Primary Dependencies**: Quarkus 4 (REST, corps brut `application/octet-stream`), lecteurs de la 006
(`OoxmlText`, `PdfText`, `HtmlText`) ; aucune nouvelle dépendance

**Storage**: SQLite ; migration `V17__session_attachment.sql` ; brouillons en mémoire (60 min)

**Testing**: JUnit 5 (règles des documents joints, brouillons, sélection, état, rendu sans extrait),
IT (envoi, limites de corps, persistance, cascade, injection, vues sans texte), Vitest
(`sw-attachments`, écrans), Playwright E2E (parcours, axe, export sans texte) ; golden set et
`ci/check-eval.sh` inchangés

**Target Platform**: poste local (macOS, Windows, Linux)

**Project Type**: application web locale (backend + front)

**Performance Goals**: document de 50 pages joint et état affiché en moins de 10 s (SC-001)

**Constraints**: aucun fichier ni chemin gardé ; texte jamais journalisé ni exporté ; limite de corps
de 256 Ko gardée hors des routes d'envoi ; session sans document joint traitée comme avant

**Scale/Scope**: 5 documents par session, 200 000 caractères extraits au plus chacun, 20 Mo par fichier

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | État de chaque document avant la génération, refus expliqués, retrait possible ; composant accessible (clavier, annonces, axe) | OK |
| II. Scénarios structurés, reproductibilité | Texte gardé : régénération identique sans le fichier ; empreinte d'entrée suivant le texte transmis ; source citée vérifiée (`citable`) | OK |
| III. Test-First | Règles (plafond, doublons), brouillons, sélection et état couverts par des tests unitaires d'abord | OK |
| IV. E2E et qualité des sorties | E2E du parcours ; prompts inchangés, évaluation inchangée ; golden set inchangé | OK |
| V. Sécurité et confidentialité | Bornes de la 006, fichier temporaire supprimé, entrées bornées (nom, taille, nombre) ; corps limité à 256 Ko ailleurs ; texte en bloc de données, test d'injection ; aucun texte dans journaux, erreurs, export, Jira, passerelle ; suppression en cascade | OK |
| Complexité | Une table, un port de lecture, un magasin de brouillons en mémoire, un filtre de taille | OK |

Re-vérification après la phase 1 : inchangée, aucune violation.

## Project Structure

### Documentation (this feature)

```text
specs/009-joindre-document/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/api.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/dev/specwriter/
├── domain/attachment/Attachment.java, AttachmentDraft.java, Attachments.java   # nouveaux
├── domain/port/AttachmentReader.java, AttachmentRepository.java                # nouveaux
├── domain/knowledge/MentionField.java, MentionKind.java                        # + ATTACHMENT
├── domain/specification/MarkdownRenderer.java                                  # « Document joint », sans extrait
├── application/attachment/AttachmentDrafts.java, ManageAttachments.java         # nouveaux
├── application/session/StartSession.java                                       # attachmentIds
├── application/knowledge/SelectKnowledge.java, MentionQueries.java             # documents joints désignés
├── application/specification/DraftValidation.java                              # extrait retiré (joint:)
├── infrastructure/docs/DocumentExtraction.java, TempFileAttachmentReader.java  # nouveaux
├── infrastructure/persistence/attachment/                                      # entité, dépôt
├── infrastructure/web/AttachmentResource.java, BodySizeFilter.java             # nouveaux
├── infrastructure/web/SessionResource.java, KnowledgeResource.java, SourceLinks.java
└── resources/db/migration/V17__session_attachment.sql ; application.properties (max-body-size)

frontend/src/app/
├── shared/ui/attachments.component.ts                                          # nouveau
├── core/sessions/attachments.api.ts                                            # nouveau
├── features/need/need.page.ts, features/questionnaire/questionnaire.page.ts
└── features/specification/specification.page.ts

e2e/tests/us11-joindre-document.spec.ts
```

**Structure Decision**: structure web existante. Les règles (plafond, doublons, ordre) sont du
domaine ; la lecture d'un fichier reste derrière un port en infrastructure ; la transmission réutilise
entièrement la voie des documents désignés (007), sans toucher au message de l'IA.

## Complexity Tracking

Aucune violation à justifier.
