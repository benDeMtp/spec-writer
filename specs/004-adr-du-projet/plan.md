# Implementation Plan: ADR du projet

**Branch**: `feature/007-adr-du-projet` (depuis `feature/006-tickets-fermes-cache`) | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-adr-du-projet/spec.md`

## Summary

Trois changements autour des ADR reconnus dans la documentation d'un projet :

1. **Compteurs** : les ADR et les sections comptent pour un élément, plus pour leurs morceaux de
   découpage (research.md R1).
2. **Page des ADR** : nouvelle route `projets/:id/contexte/adr`, ouverte depuis le compteur « N ADR »
   du panneau de connaissance. Elle liste les ADR (numéro, titre, statut, successeur, signalements),
   lus par un nouveau cas d'usage `ListAdrs` ; le numéro devient une valeur du domaine `AdrNumber`,
   partagée avec `SelectKnowledge` (R2). Pour une documentation en dossier local, l'outil sert les
   pages lui-même (`/api/projects/{id}/doc-files/…`), confinées au dossier déclaré et sans script
   (R3).
3. **ADR entier dans la génération** : un morceau retenu fait transmettre son ADR entier, une fois,
   dans un budget de 6 000 caractères au total et 4 000 par ADR, coupé à une ligne de titre au-delà
   (R4). L'évaluation réelle est relancée (R5).

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript 5 / Angular 22 (front)

**Primary Dependencies**: Quarkus 4 (REST, Hibernate ORM natif sur SQLite), Angular Material ;
aucune nouvelle dépendance

**Storage**: SQLite, `knowledge_item` de la 002 ; aucune migration

**Testing**: JUnit 5 (unitaires `AdrNumberTest`, `ListAdrsTest`, `SelectKnowledgeTest`), IT Quarkus
(`SqliteKnowledgeIndexIT`, `AdrResourceIT`, `DocFileResourceIT`), Vitest (page, panneau), Playwright
(E2E sur le jeu fictif, axe-core), évaluation réelle `-Peval`

**Target Platform**: poste local (macOS, Windows, Linux), navigateur récent

**Project Type**: application web locale (backend + front)

**Performance Goals**: page des ADR affichée en moins d'une seconde pour quelques centaines d'ADR ;
SC-002 (ADR trouvé et ouvert en moins de 15 s)

**Constraints**: aucun script de la documentation exécuté dans l'origine de l'outil ; aucun fichier
servi hors des dossiers déclarés ; contenu jamais journalisé ; texte transmis à l'IA déterministe et
borné (6 000 caractères d'ADR)

**Scale/Scope**: jusqu'à quelques centaines d'ADR par projet ; dizaines de milliers de fichiers dans
un dossier de documentation (lecture à la demande, un fichier à la fois)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | Compteur juste ; page qui dit ce qui est transmis à l'IA et ce qui pose problème (statut inconnu, doublon, successeur introuvable) ; WCAG 2.1 AA (tableau au clavier, statut en texte, nouvel onglet annoncé), vérifié par axe en E2E | OK |
| II. Scénarios structurés, reproductibilité | Texte des ADR transmis déterministe (ordre des ADR et des morceaux) ; test de deux préparations identiques | OK |
| III. Test-First | Tests avant code pour chaque règle ; `domain` et `application` couverts par des tests unitaires (piège JaCoCo du CLAUDE.md) | OK |
| IV. E2E et qualité des sorties | E2E de la page sur le jeu fictif ; évaluation réelle relancée car le texte transmis à l'IA change (golden 07), scores ≥ référence | OK |
| V. Sécurité et confidentialité | Fichiers servis : sources FOLDER du projet seules, chemin normalisé et confiné (`..`, absolu, lien symbolique), liste d'extensions, CSP `sandbox` sans scripts, `nosniff`, authentification de l'API ; tests négatifs (traversée, extension, autre projet) ; journal sans chemin ni contenu | OK |
| Complexité | Un cas d'usage, une valeur du domaine, deux ressources REST, une page ; aucune dépendance ni table | OK |

Re-vérification après la phase 1 : inchangée, aucune violation.

## Project Structure

### Documentation (this feature)

```text
specs/004-adr-du-projet/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── api.md          # liste des ADR, fichiers de documentation, front
└── tasks.md            # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/dev/specwriter/
├── domain/knowledge/AdrNumber.java                 # nouveau : numéro d'un ADR
├── application/knowledge/
│   ├── ListAdrs.java, AdrList.java                 # nouveaux : liste et résumé des ADR
│   └── SelectKnowledge.java                        # ADR entier, budget 6 000 / 4 000, AdrNumber
├── infrastructure/
│   ├── knowledge/SqliteKnowledgeIndex.java         # count sur chunk = 0
│   ├── web/KnowledgeResource.java                  # GET /knowledge/adrs
│   ├── web/DocFileResource.java                    # nouveau : GET /doc-files/{path}
│   └── config/UseCaseProducer.java                 # ListAdrs
backend/src/test/java/dev/specwriter/...             # tests unitaires et IT
frontend/src/app/
├── core/projects/knowledge.api.ts, projects.model.ts   # AdrList
├── features/project-context/adrs.page.ts (+ spec)      # nouvelle page
├── features/project-context/knowledge-panel.component.ts  # « N ADR » en lien
└── app.routes.ts                                        # projets/:id/contexte/adr
e2e/tests/us8-project-context.spec.ts               # page des ADR sur le jeu fictif
```

**Structure Decision**: structure web existante (backend hexagonal + front Angular) ; la page est un
enfant du contexte du projet.

## Complexity Tracking

Aucune violation à justifier.
