# Implementation Plan: Confluence, source et date des extraits, fiabilité des sources

**Branch**: `feature/020-confluence-fiabilite` | **Date**: 2026-11-17 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/016-confluence-fiabilite-sources/spec.md`

## Summary

Confluence Server / Data Center devient une source de documentation d'un projet : une racine est
une page désignée par son adresse, dont toute la descendance est lue par une recherche CQL
`ancestor=`, puis indexée en sections comme une page Antora. Une réindexation compare les numéros
de version pour ne relire que les pages changées. Le serveur et son jeton sont déclarés une fois,
dans Configuration, comme Jira (trousseau, proxy, certificat, débit borné).

Chaque extrait transmis au modèle porte désormais sa source, sa date de mise à jour (ou
« inconnue ») et le niveau de fiabilité de sa source. Ces informations sont calculées à la lecture,
sans réindexation. Les niveaux (référence, interprétation, à confirmer) se règlent par sorte de
source et par racine Confluence. Une recherche réserve au plus deux places aux extraits de
référence pertinents, sans modifier les scores. Une consigne réutilisable dit au modèle comment
arbitrer, et les spécifications gagnent une rubrique « Écarts entre sources ». L'évaluation réelle
mesure qu'une page Confluence qui contredit la demande SharePoint est signalée.

## Technical Context

**Language/Version**: Java 25 (backend Quarkus 4), TypeScript (Angular 22)

**Primary Dependencies**: client REST Quarkus (comme Jira), Jsoup via `HtmlText` / `Sections`
(existant), quarkus-langchain4j (consignes, garde-fous, 014), Flyway, SQLite, Angular Material.
Aucune dépendance nouvelle.

**Storage**: SQLite, migration V27 (`confluence_settings`, `confluence_page`,
`project_source_reliability`, reconstruction de `project_doc_source` pour la sorte `CONFLUENCE`,
le niveau propre, l'identifiant et le titre de la page racine). Jeton au trousseau (`SecretStore`).

**Testing**: JUnit 5, `@QuarkusTest`, WireMock (IT Confluence sous Maven, comme les IT Jira),
ArchUnit, Vitest, Playwright (E2E sur le jar, Confluence et Jira simulés, LLM rejoué), évaluation
réelle `-Peval`.

**Target Platform**: poste de l'utilisateur (macOS, Windows, Linux), outil local sur 127.0.0.1.

**Project Type**: application web locale (backend + frontend).

**Performance Goals**: 500 pages indexées en moins de 5 minutes (SC-003 ; environ 65 s au débit
de 8 requêtes par seconde) ; une réindexation sans changement ne fait que les requêtes de liste
(5 pour 500 pages) ; le calcul des places réservées n'ajoute aucune requête à une recherche.

**Constraints**: lecture seule de Confluence ; jeton jamais en clair (trousseau, aucun journal) ;
contenu des pages jamais journalisé ; extraits Confluence traités comme des données (injection) ;
2 000 pages Confluence par projet ; 10 sources de documentation par projet (inchangé).

**Scale/Scope**: quelques racines par projet, quelques centaines à 2 000 pages ; 6 sortes de
sources réglables.

## Constitution Check

| Principe | Vérification | Statut |
|---|---|---|
| I. Expérience guidée | Aperçu d'une racine avant indexation (titre, nombre de pages) ; refus qui disent quoi faire (racine trop large, illisible, autre serveur, serveur non déclaré → lien vers Configuration) ; niveaux avec leurs défauts expliqués ; WCAG 2.1 AA des nouveaux réglages et de la rubrique (FR-024). | OK |
| II. Scénarios traçables | Rien d'inventé : un écart entre sources n'est gardé que si ses deux références sont citables (même contrôle que les écarts aux ADR, R8) ; la spécification suit la référence ; structure reproductible (champ facultatif, fixtures antérieures lisibles). | OK |
| III. Test-first | Domaine pur testé d'abord (`Reliability`, `ReliabilitySettings`, `ReservedPlaces`, `ConfluenceSettings`, `SourceDeviation`) ; IT WireMock du client ; horloge et réseau derrière des ports ; couverture du domaine et de l'application par tests unitaires (piège JaCoCo du CLAUDE.md). | OK |
| IV. Bout en bout et qualité | E2E `us16-confluence` sur le jar ; golden set inchangé ; évaluation réelle avec deux métriques nouvelles (`sourceConflictFlagged`, `referenceRetained`) et aucun recul (SC-006). | OK |
| V. Sécurité | Jeton au trousseau, jamais renvoyé ni journalisé ; HTTP seulement après confirmation ; certificat interne ; proxy ; lecture seule (FR-010) ; contenu Confluence balisé et échappé, test d'injection ; aucune donnée de spécification envoyée à Confluence. Les extraits partent vers Copilot sous l'accord existant (assumption de la spec) : pas d'accord de plus, car rien n'est envoyé à Confluence. | OK |
| Complexité | Pas de nouveau service ni de dépendance. Nouvelle notion transverse (`Provenance`) justifiée par FR-011 ; alternative plus simple écartée : stocker source et niveau dans l'index (réindexation à chaque changement de niveau, R5). | OK |

Re-vérification après conception : inchangée. `ReservedPlaces` reste un filtre déterministe sur la
coupe, sans toucher aux scores (FR-020) ; la consigne `fiabilite-sources` respecte
`PromptRulesTest` (règle de sécurité dans chaque consigne d'opération, aucune accolade).

## Project Structure

### Documentation (this feature)

```text
specs/016-confluence-fiabilite-sources/
├── plan.md
├── research.md          # R1 à R10
├── data-model.md        # V27, domaine, provenance
├── quickstart.md
├── contracts/
│   ├── rest-api.md      # Configuration Confluence, racines, niveaux, vues
│   ├── llm-tags.md      # attributs source / maj / fiabilite, sourceDeviations
│   └── confluence-client.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/fr/kawamind/
├── domain/
│   ├── settings/ConfluenceSettings.java
│   ├── project/DocSource.java                  # Kind.CONFLUENCE, reliability, pageId, title
│   ├── knowledge/SourceOrigin.java, Reliability.java, ReliabilitySettings.java,
│   │            Provenance.java, ReservedPlaces.java
│   ├── specification/SpecificationContent.java # sourceDeviations, SourceDeviation
│   ├── llm/DraftSpecification.java             # DraftSourceDeviation
│   ├── conversation/TurnSource.java            # origin, updatedAt, level
│   └── port/ConfluenceClient.java, ConfluenceSettingsRepository.java,
│            ReliabilityRepository.java, ConfluencePageStates.java
├── application/
│   ├── settings/SaveConfluenceSettings.java, DeleteConfluenceSettings.java  # comme Jira
│   ├── knowledge/SynchronizeKnowledge.java     # lecture des racines Confluence (R3)
│   ├── context/ManageDocSources.java           # aperçu et ajout CONFLUENCE, niveau d'une racine
│   ├── knowledge/ManageReliability.java
│   ├── knowledge/ProvenanceResolver.java       # sorte + source_id + préfixe → origine, niveau
│   ├── knowledge/SelectKnowledge.java          # provenance et places réservées (génération)
│   ├── conversation/ConversationSearch.java    # provenance et places réservées (agent)
│   └── specification/DraftValidation.java      # écarts entre sources citables
├── infrastructure/
│   ├── confluence/ConfluenceApi.java, ConfluenceRestClient.java, ConfluenceThrottle.java,
│   │              ConfluencePageAddress.java  # trois formes d'adresse
│   ├── jira/JiraTlsConfigurer.java → platform/HttpTlsConfigurer.java  # généralisé
│   ├── persistence/…                           # V27 : réglages, pages, niveaux, doc_source
│   ├── llm/UserMessages.java                   # attributs source / maj / fiabilite
│   ├── llm/LlmSchemas.java, ai/LlmOutputs.java # sourceDeviations
│   └── web/ConfluenceSettingsResource.java, ContextResource.java, ReliabilityResource.java
└── resources/
    ├── db/migration/V27__confluence_reliability.sql
    └── prompts/skills/fiabilite-sources.md     # + déclarations dans 7 consignes

frontend/src/app/
├── features/settings/confluence-card.component.ts
├── features/project-settings/project-settings.page.ts      # racines Confluence, niveaux
├── features/project-settings/reliability.component.ts
├── features/conversation/…                                  # source et date des résultats
└── features/specification/specification.page.ts            # « Écarts entre sources »

e2e/
├── fixtures/confluence/                       # arbre de démonstration (WireMock)
├── fixtures/llm/7x-*.json                     # recherche et génération avec écart
└── tests/us16-confluence.spec.ts

backend/src/test/resources/eval/sources/cases.json   # R9
```

**Structure Decision**: même découpage hexagonal que les features précédentes ; Confluence est un
adaptateur `infrastructure/confluence` derrière le port `ConfluenceClient`, branché dans la lecture
des sources de documentation existante.

## Complexity Tracking

Aucune violation à justifier. Généralisation de `JiraTlsConfigurer` en `HttpTlsConfigurer` :
évite une copie du code de certificat pour un second client.

## Ordre de réalisation

1. **US2 d'abord** (provenance et date jusqu'au modèle) : elle vaut pour les sources existantes et
   prépare les deux autres ; elle remplit `source_updated_at` des documents et des pages Antora.
2. **US1** (Confluence) : réglages, client, racines, indexation incrémentale, E2E.
3. **US3** (niveaux, places réservées, consigne, écarts entre sources, évaluation).
