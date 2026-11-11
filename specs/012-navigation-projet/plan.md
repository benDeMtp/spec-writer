# Implementation Plan: Navigation par projet et réglages de l'application

**Branch**: `feature/016-navigation-projet` (depuis `feature/015-discussion-specification`, à rebaser sur `main` après la fusion de la PR #13) | **Date**: 2026-11-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/012-navigation-projet/spec.md`

## Summary

Chaque écran dit désormais s'il porte sur le projet courant ou sur toute l'application.

- **Menu** : il sort du shell dans `sw-main-nav`, en deux groupes nommés, « Projet » et
  « Application ».
  - L'entrée active est calculée par une fonction pure, qui couvre aussi les pages filles.
  - La clé Jira effective s'affiche à côté de « Tickets Jira ».
  - Deux pastilles d'état, Copilot et Jira, s'affichent sous Configuration.
  - Sous 48 rem, le menu se replie derrière un bouton.
- **Titres** : une `TitleStrategy` et un composant `sw-project-heading` ajoutent le nom du projet
  au titre de l'onglet et au `h1` des pages du bloc « Projet ».
- **Pages du projet** : `project-context.page` est découpé en deux pages, sur des adresses sans
  identifiant qui portent sur le projet courant :
  - « Réglages du projet » (`/projet/reglages`) : identité avec la clé Jira, fiche, sources,
    architecture ;
  - « Connaissance du projet » (`/projet/connaissance`, et `/adr`).

  Les anciennes adresses redirigent, après avoir rendu le projet courant.
- **Bascule** : `ProjectFollower` rend courant le projet d'une spécification, d'un questionnaire
  ou d'une conversation ouverts par leur adresse, et l'annonce. `LeaveGuard` partage les
  confirmations de sortie entre les gardes de route et le sélecteur.
- **Jira** :
  - la clé par défaut devient facultative (migration V23, reconstruction de `jira_settings`) ;
  - le nouveau code `JIRA_PROJECT_MISSING` porte l'action `OPEN_PROJECT_SETTINGS`, qui mène aux
    réglages du projet ;
  - le test de connexion vérifie la clé effective du projet courant (`OK_NO_PROJECT` sans clé) ;
  - l'écran d'un ticket annonce le projet de destination.
- **Configuration** : elle est séparée en « Connexions » et « Avancé », avec la portée écrite sous
  chaque réglage avancé.
- **Session perdue** : le serveur distingue `SESSION_REQUIRED` de `FORBIDDEN`, et le front affiche
  alors un écran dédié, sans menu.

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript 5 / Angular 22 (front)

**Primary Dependencies**:
- Quarkus 4 REST ;
- Flyway (SQLite) ;
- Angular Router (`TitleStrategy`, gardes fonctionnelles) ;
- Angular Material (`mat-select`, `MatDialog`) ;
- aucune nouvelle dépendance.

**Storage**: SQLite. Migration `V23__jira_default_key_optional.sql`, qui reconstruit
`jira_settings` avec `default_project_key` nullable. Aucune autre table ne change.

**Testing**:
- JUnit 5 :
  - `JiraSettings` (clé facultative) ;
  - `JiraAccess` (`requireProject`) ;
  - `TestJiraConnection` (clé du projet, clé par défaut, aucune clé) ;
  - `UnifiedSearch` sans clé ;
  - `LocalSecurityFilter` (`SESSION_REQUIRED` d'un côté, `FORBIDDEN` de l'autre) ;
- IT : migration V23, `SettingsResource`, création de ticket sans clé ;
- Vitest :
  - `activeEntry`, `sw-main-nav`, `ProjectFollower`, `LeaveGuard` et le sélecteur ;
  - `ProjectTitleStrategy`, `project-settings.page`, `project-knowledge.page` ;
  - `jira-issues.page`, `problem-banner`, interceptor, `sw-session-lost` ;
- Playwright E2E : `us15-navigation` (quickstart 1 à 15), axe ; specs existantes mises à jour ;
- évaluation : aucune, aucun prompt ne change.

**Target Platform**: poste local (macOS, Windows, Linux), navigateur récent

**Project Type**: application web locale (backend + front)

**Performance Goals**:
- aucun appel supplémentaire au chargement (l'état des pastilles vient de `GET /api/settings`,
  déjà fait) ;
- bascule de projet : un `PUT` et un rechargement des projets, moins de 300 ms en local.

**Constraints**:
- WCAG 2.1 AA (groupes nommés, `aria-current`, `aria-expanded`, état en texte) ;
- confirmations de la 011 respectées ;
- migration Flyway ajoutée, jamais modifiée ;
- la ligne `jira_settings` existante est conservée ;
- les agents MCP ne sont pas touchés.

**Scale/Scope**:
- un utilisateur, quelques projets ;
- 14 pages ;
- environ 15 fichiers front touchés, 10 côté backend.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | La portée de chaque écran est explicite : menu en deux blocs, titres nommant le projet. Les messages d'erreur mènent à l'endroit où l'on corrige (`OPEN_PROJECT_SETTINGS`). La session perdue dit quoi faire. WCAG AA : groupes nommés, état en texte, menu replié au clavier, axe sur chaque nouvel écran. | OK |
| II. Scénarios traçables | Non concerné : la génération, la validation et l'export ne changent pas. | OK (n/a) |
| III. Test-First | Fonction d'entrée active, services `ProjectFollower` et `LeaveGuard`, domaine Jira et filtre de sécurité testés d'abord. Les doubles existants (horloge, Jira simulé, dépôts en mémoire) suffisent. | OK |
| IV. E2E et qualité des sorties | Parcours de navigation complet en E2E. Aucun changement du moteur de questions ni du générateur : golden set et évaluation inchangés. | OK |
| V. Sécurité | `SESSION_REQUIRED` ne révèle rien de plus que `FORBIDDEN`. Les refus d'hôte et d'origine restent indistincts. Aucune donnée saisie dans les adresses (le projet n'y figure pas). La bascule passe par la route existante, contrôlée par le cookie. | OK |
| Complexité | Pas de nouvelle dépendance ni de nouvelle couche. Deux petits services front (`ProjectFollower`, `LeaveGuard`) remplacent des cas particuliers par page. Le découpage d'une page de 455 lignes réduit la complexité. | OK |

Re-vérification après la phase 1 : inchangée, aucune violation.

## Project Structure

### Documentation (this feature)

```text
specs/012-navigation-projet/
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
├── domain/settings/JiraSettings.java, JiraTestResult.java   # clé par défaut facultative, OK_NO_PROJECT
├── domain/error/ErrorCode.java, ErrorAction.java            # JIRA_PROJECT_MISSING, SESSION_REQUIRED, OPEN_PROJECT_SETTINGS
├── application/jira/JiraAccess.java                         # Target.requireProject()
├── application/jira/TicketForm.java, PreviewJiraTicket.java, CreateJiraTicket.java
├── application/search/UnifiedSearch.java                    # part Jira omise sans clé
├── application/settings/TestJiraConnection.java             # clé effective du projet courant
├── infrastructure/web/LocalSecurityFilter.java, SessionAuthResource.java   # SESSION_REQUIRED
├── infrastructure/web/SettingsResource.java                 # TestView.projectKey
├── infrastructure/config/UseCaseProducer.java               # TestJiraConnection reçoit ProjectRepository
└── resources/db/migration/V23__jira_default_key_optional.sql

frontend/src/app/
├── app.ts, app.routes.ts, app.config.ts                     # shell réduit, nouvelles routes, TitleStrategy
├── features/shell/main-nav.component.ts, nav.ts             # menu en deux groupes, activeEntry
├── features/shell/project-notice.component.ts               # annonce « Projet courant : … »
├── features/shell/session-lost.component.ts
├── features/projects/project-switcher.component.ts          # libellé « Projet », LeaveGuard, retour à la liste
├── features/projects/project-identity-form.component.ts     # extrait de projects.page
├── features/projects/projects.page.ts                       # Modifier → réglages ; Contexte → Connaissance
├── features/project-settings/project-settings.page.ts       # identité, fiche, sources, architecture
├── features/project-knowledge/project-knowledge.page.ts     # knowledge-panel (déplacé), liens
├── features/project-knowledge/adrs.page.ts                  # déplacé depuis project-context/
├── features/project-context/                                # retiré (sheet-editor déplacé vers project-settings/)
├── features/settings/settings.page.ts, jira-card.component.ts, embedding-card…, agent-access-card…
├── features/jira-issues/jira-issues.page.ts                 # trois états, lien vers les réglages
├── features/search/item-detail.page.ts                      # projet de destination
├── features/specification/specification.page.ts, questionnaire/questionnaire.page.ts, conversation/conversation.page.ts  # follow()
├── core/projects/project-follower.ts
├── core/navigation/leave-guard.ts, project-title.strategy.ts, follow-project.guard.ts
├── core/http/problem.interceptor.ts                         # SESSION_REQUIRED → launch.state 'rejected'
└── shared/ui/project-heading.component.ts, problem-banner.component.ts

e2e/tests/us15-navigation.spec.ts ; specs existantes mises à jour : us1-settings, us5-create-ticket,
  us6-search-edit, us7-projects, us8-project-context, us9-deletion, us10-jira-paging, us11-agents, network-egress
  (anciennes adresses, libellés « Projet courant », « Contexte », « Ouvrir la configuration », clé par défaut)
backend/src/test/…/LocalSecurityFilterTest.java (code SESSION_REQUIRED sans cookie, FORBIDDEN pour hôte et origine)
README.md (navigation, réglages du projet, clé Jira par défaut) ; specs/002-projets-contexte-mcp/contracts/openapi.yaml
```

**Structure Decision**: la structure web existante et l'architecture hexagonale sont conservées.
- Côté backend, les changements restent dans le domaine (règles de la clé, codes d'erreur),
  l'application (accès Jira, test de connexion) et l'infrastructure web (filtre, vues).
- Côté front, la navigation et le projet courant vont dans `core/` (services) et
  `features/shell/` (composants du cadre). Les pages du projet ont chacune leur dossier.

## Complexity Tracking

Aucune violation à justifier.
