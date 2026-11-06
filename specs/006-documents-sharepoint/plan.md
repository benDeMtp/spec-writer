# Implementation Plan: Documents SharePoint par dossier synchronisé

**Branch**: `feature/009-documents-sharepoint` (depuis `main`) | **Date**: 2026-11-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/006-documents-sharepoint/spec.md`

## Summary

Un nouveau type de source, « dossier de documents » (`DOCUMENTS`), lit le dossier SharePoint
synchronisé par OneDrive : Word et Excel par lecture directe du format OOXML (JDK), PDF par
PDFBox, HTML par jsoup (research.md R1). Les références `docs:<source>/<chemin>` rattachent les
éléments à leur source (R2) ; une table `document_file` évite de relire, donc de télécharger, les
fichiers inchangés (R3). Des motifs d'exclusion en globbing, avec aperçu, filtrent le dossier
(R4, R5). La génération reçoit les documents utiles entiers, relus à la volée (R6) : nouveau bloc
`<document>` dans les prompts, évaluation réelle relancée.

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript 5 / Angular 22 (front)

**Primary Dependencies**: Quarkus 4, jsoup (existant) ; **nouvelle** : Apache PDFBox 3.0.x
(Apache-2.0) ; OOXML lu avec `java.util.zip` et StAX du JDK

**Storage**: SQLite ; migration `V15__documents_folder.sql` (CHECK du type, colonne
`exclusions`, table `document_file`)

**Testing**: JUnit 5 (`ExclusionPatternsTest`, `OoxmlTextTest`, `PdfTextTest`,
`LocalDocumentFolderTest`, `SynchronizeKnowledgeTest`, `SelectKnowledgeTest`), IT Quarkus
(`ContextResourceIT`), Vitest, Playwright (axe), évaluation réelle `-Peval`

**Target Platform**: poste local (macOS, Windows, Linux) avec OneDrive

**Project Type**: application web locale (backend + front)

**Performance Goals**: 200 documents mixtes indexés en moins de 5 minutes (SC-001) ;
synchronisation sans changement : aucun fichier ouvert (SC-005)

**Constraints**: fichiers jamais copiés ni servis ; texte jamais journalisé ; lecture OOXML sans
DTD ni entité externe, bornée contre les bombes zip ; contenu des documents traité comme donnée
non fiable dans les prompts (OWASP LLM01)

**Scale/Scope**: jusqu'à 2 000 documents retenus par source, 20 Mo par fichier

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | Aide à la synchronisation OneDrive ; aperçu des motifs avant indexation ; fichiers ignorés et en erreur expliqués ; WCAG 2.1 AA (zone de texte étiquetée, aperçu annoncé), axe en E2E | OK |
| II. Scénarios structurés, reproductibilité | Texte transmis déterministe (ordre des documents et des sections) ; deux préparations identiques | OK |
| III. Test-First | Tests d'abord ; extraction, motifs et sélection couverts par des tests unitaires (`domain`, `application`, `infrastructure/docs`) | OK |
| IV. E2E et qualité des sorties | E2E sur un jeu de documents générés ; prompts modifiés → évaluation réelle, scores ≥ référence | OK |
| V. Sécurité et confidentialité | Dossier déclaré seul lu, liens symboliques non suivis ; XXE et bombes zip couverts par des tests ; aucun fichier copié (test E2E) ; PDFBox scanné par Dependency-Check ; contenu non fiable balisé dans les prompts | OK |
| Complexité | Une dépendance (PDFBox) justifiée en R1 ; une migration ; un port et son adaptateur | OK |

Re-vérification après la phase 1 : inchangée, aucune violation.

## Project Structure

### Documentation (this feature)

```text
specs/006-documents-sharepoint/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── api.md
└── tasks.md            # /speckit-tasks
```

### Source Code (repository root)

```text
backend/pom.xml                                          # PDFBox
backend/src/main/resources/db/migration/V15__documents_folder.sql
backend/src/main/resources/prompts/generate-specification.txt, edit-specification.txt
backend/src/main/java/dev/specwriter/
├── domain/project/DocSource.java                        # Kind.DOCUMENTS, exclusions
├── domain/project/ExclusionPatterns.java                # nouveau : motifs
├── domain/port/DocumentFolder.java                      # nouveau : scan, read
├── domain/port/KnowledgeIndex.java                      # retainUnder
├── application/context/ManageDocSources.java            # motifs, aperçu, retrait à la suppression
├── application/knowledge/SynchronizeKnowledge.java      # étape « dossiers de documents »
├── application/knowledge/SelectKnowledge.java           # sélection des documents (US3)
├── infrastructure/docs/LocalDocumentFolder.java         # nouveau : parcours, délais, limites
├── infrastructure/docs/OoxmlText.java, PdfText.java, HtmlText.java   # nouveaux : extraction
├── infrastructure/persistence/…                         # exclusions, document_file
├── infrastructure/knowledge/SqliteKnowledgeIndex.java   # retainUnder
├── infrastructure/llm/UserMessages.java                 # balise <document>
└── infrastructure/web/ContextResource.java              # exclusions, aperçu
frontend/src/app/
├── core/projects/projects.model.ts, context.api.ts
└── features/project-context/project-context.page.ts (+ spec)
e2e/tests/us8-project-context.spec.ts
```

**Structure Decision**: structure web existante ; l'extraction est un adaptateur d'infrastructure
derrière le port `DocumentFolder` ; `AntoraReader` n'est pas modifié.

## Complexity Tracking

Aucune violation à justifier.
