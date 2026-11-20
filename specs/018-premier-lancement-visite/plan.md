# Implementation Plan: Assistant de premier lancement et visite guidée

**Branch**: `feature/022-premier-lancement` | **Date**: 2026-11-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/018-premier-lancement-visite/spec.md`

## Summary

Un assistant de premier lancement (`/premiers-pas/:etape`) mène une installation vierge de la
connexion à Copilot jusqu'à l'indexation du projet. Il place les composants existants (cartes de
connexion, formulaire d'identité du projet) et deux composants extraits de la page des réglages du
projet (sources de documentation, modèle d'architecture), ainsi qu'un composant de progression
extrait du panneau de connaissance. Sa progression est une donnée de l'outil (V29) :
- elle permet la reprise après un arrêt ;
- la migration la marque terminée pour une installation déjà utilisée (R1).

L'installation a toujours un « Projet par défaut » : l'étape projet le configure au lieu d'en
créer un second (R2).

Une visite guidée de 11 arrêts, écrite avec le CDK d'Angular (`Overlay`, `cdkTrapFocus`,
`LiveAnnouncer`), parcourt les écrans dans l'ordre d'usage. Elle ne fait que des lectures.

Le README gagne une section « Installer depuis les sources », vérifiée sur un clone neuf.

## Technical Context

**Language/Version**: Java 25 (backend Quarkus 4), TypeScript (Angular 22)

**Primary Dependencies**: aucune nouvelle. `@angular/cdk` 22 (déjà présent) pour la visite :
`Overlay`, `A11yModule` (`cdkTrapFocus`, `LiveAnnouncer`). Côté backend, Quarkus REST et la
persistance existante.

**Storage**: SQLite, migration V29 : tables `onboarding` (une ligne) et `onboarding_step`,
remplies selon l'usage constaté de l'installation (R1).

**Testing**: JUnit 5, `@QuarkusTest`, ArchUnit, Vitest, Playwright (E2E sur le jar, dont une
seconde instance sur une base neuve pour l'installation vierge, R7). Pas d'évaluation LLM (aucun
prompt touché).

**Target Platform**: poste de l'utilisateur, outil local sur 127.0.0.1 ; guide d'installation
pour macOS et Linux.

**Project Type**: application web locale (backend + frontend).

**Performance Goals**: assistant complet en moins de 10 minutes hors durée d'indexation
(SC-001) ; tout passer en moins de 3 minutes après Copilot (SC-002) ; visite en moins de 5 minutes
(SC-003).

**Constraints**:
- Copilot seule étape obligatoire, vérifiée côté serveur (FR-003).
- Aucune duplication des formulaires (FR-005).
- La visite n'écrit rien (FR-017).
- Le projet par défaut est configuré, jamais dupliqué (R2).
- Les E2E existants ne voient pas l'assistant (`%e2e`, R7).
- WCAG 2.1 AA (FR-011, FR-018).

**Scale/Scope**: une progression par installation, 6 étapes, 11 arrêts de visite.

## Constitution Check

| Principe | Vérification | Statut |
|---|---|---|
| I. Expérience guidée | L'assistant est le principe I appliqué à la configuration : étapes une par une avec progression visible, retour en arrière, étapes passées tracées, reprise sans perte. Les messages d'erreur viennent des formulaires existants. Clavier, focus, annonces et contrôle `axe` sur l'assistant et sur chaque arrêt de la visite (WCAG 2.1 AA). | OK |
| II. Scénarios traçables | Génération inchangée. | OK |
| III. Test-first | Domaine `Onboarding` (ordre, obligation de Copilot, retour, fin) testé d'abord. Le cas d'usage est testé avec des dépôts en mémoire, la migration V29 sur une base vierge et sur une base utilisée. Domaine et application ont des tests unitaires (piège JaCoCo). | OK |
| IV. Bout en bout | Deux E2E : `us18-premiers-pas` (installation vierge sur une seconde instance, tout passer, reprise, installation utilisée) et `us18-visite` (11 arrêts au clavier, `axe`, aucune écriture). Golden set et évaluation inchangés. | OK |
| V. Sécurité | Aucune donnée saisie dans la progression ni au journal : seulement les noms d'étapes et leurs états. Les accords d'envoi à Copilot et à Jira passent par les écrans existants, avant tout envoi. L'API de progression est protégée comme le reste (cookie, origine). | OK |
| Complexité | Pas de bibliothèque de visite : le CDK, déjà présent, fait le positionnement, le piège du focus et les annonces (R6). Trois composants sont extraits plutôt que copiés (R3, R4), ce qui retouche la page des réglages et le panneau de connaissance. Leurs specs et les E2E existants en gardent le rendu. Une seconde instance E2E coûte environ 3 s ; c'est le seul moyen de tester une vraie installation vierge sans écriture réservée aux tests (R7). | OK |

Re-vérification après conception : inchangée.

## Project Structure

### Documentation (this feature)

```text
specs/018-premier-lancement-visite/
├── plan.md
├── research.md          # R1 à R8
├── data-model.md        # V29, domaine Onboarding, arrêts de la visite
├── quickstart.md        # Q1 à Q6
├── contracts/
│   ├── rest-api.md      # /api/onboarding
│   └── tour.md          # les 11 arrêts et leurs cibles
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/fr/kawamind/
├── domain/onboarding/OnboardingStep.java, StepState.java, Onboarding.java
├── domain/port/OnboardingRepository.java
├── domain/error/ErrorCode.java                       # ONBOARDING_COPILOT_REQUIRED, ONBOARDING_UNKNOWN_STEP
├── application/onboarding/ManageOnboarding.java
└── infrastructure/
    ├── persistence/onboarding/OnboardingSqliteRepository.java
    ├── web/OnboardingResource.java
    └── config/UseCaseProducer.java                   # specwriter.onboarding.auto-open
backend/src/main/resources/
├── db/migration/V29__onboarding.sql
└── application.properties                            # auto-open=true ; %e2e false

frontend/src/app/
├── core/onboarding/onboarding.api.ts, onboarding.service.ts   # ouverture automatique (R5)
├── core/tour/tour-stops.ts, tour.service.ts, tour-bubble.component.ts   # R6
├── features/onboarding/onboarding.page.ts            # cadre, progression, Précédent/Suivant/Passer
├── features/onboarding/steps/*.component.ts          # accueil, copilot, jira, confluence, projet, sources, indexation, fin
├── features/project-settings/doc-sources.component.ts        # extrait (R3)
├── features/project-settings/architecture-model.component.ts # extrait (R3)
├── features/project-knowledge/indexation-progress.component.ts # extrait (R4)
├── features/shell/main-nav.component.ts              # entrée « Visite guidée »
├── features/settings/settings.page.ts                # « Relancer l'assistant »
└── app.routes.ts                                     # /premiers-pas/:etape

e2e/tests/us18-premiers-pas.spec.ts, us18-visite.spec.ts
e2e/tests/support/fresh-instance.ts                   # seconde instance sur base neuve (R7)
README.md                                             # Installer depuis les sources, Premier lancement
```

**Structure Decision**: même découpage hexagonal. La progression est un petit domaine pur
(`Onboarding`). Le seul contrôle serveur est celui de l'étape obligatoire ; les autres étapes
s'appuient sur les API existantes. La visite est entièrement côté front.

## Ordre de réalisation

1. **Fondations** : extraction de `sw-doc-sources`, `sw-architecture-model` et
   `sw-indexation-progress`, sans changer le rendu. Les specs et E2E existants restent verts.
2. **US1** (assistant) : V29, domaine, API, puis écrans. C'est le MVP.
3. **US2** (visite) : cibles `data-tour`, service, bulle, entrée de menu, proposition en fin
   d'assistant.
4. **US3** (README) : section « Installer depuis les sources » vérifiée sur un clone neuf (Q6),
   « Premier lancement » réécrite pour l'assistant et la visite.
