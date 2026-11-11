# Quickstart : périmètre des modules touchés (013)

Ce guide valide la feature de bout en bout. Les détails sont dans
[contracts/api.md](contracts/api.md), [contracts/ui.md](contracts/ui.md) et
[data-model.md](data-model.md).

## Prérequis

```bash
export JAVA_HOME=~/.sdkman/candidates/java/25.0.2.r25-mandrel PATH=$JAVA_HOME/bin:$PATH
```

- Arbre de démonstration : `e2e/fixtures/sources/demo-rv/` (research R13).
- Rejeu du LLM : `e2e/fixtures/llm` (plan `PERIMETER`, `qualify-perimeter`, `converse-answer`).
- Modèle C4 de démonstration : `e2e/fixtures/c4`, avec la relation facturation → notification.

## Lancer

```bash
rm -f backend/target/jacoco.exec
env -u JAVA_TOOL_OPTIONS ./mvnw -B verify -DskipDependencyCheck
env -u JAVA_TOOL_OPTIONS ./mvnw -B package -DskipTests
env -u JAVA_TOOL_OPTIONS npm --prefix e2e test -- us16-perimetre
```

## Scénarios

| # | Action | Résultat attendu | Exigences |
|---|---|---|---|
| 1 | Réglages du projet → Sources du code. Ajouter `…/demo-rv`, puis Aperçu. | « 14 modules détectés ». Liste des modules. 2 fichiers sensibles écartés, fichiers ignorés par `.gitignore` comptés. Le texte d'information est visible. | US1-1, FR-004, FR-006, FR-026 |
| 2 | Ajouter le motif `legacy/**`, puis Aperçu. | Le module `legacy/ancien` disparaît de l'aperçu. | US1-2, FR-002 |
| 3 | Ajouter un chemin inexistant, puis un sous-dossier de `demo-rv`. | Refus, avec la raison sous le champ (chemin absent, racine imbriquée). | US1-4 |
| 4 | Conversation : « Quels modules touche l'ajout d'un statut SUSPENDU au contrat ? » | Étape « Analyse du périmètre » ouverte. Termes `ContractStatus`, `CONTRACT_STATUS`, `contract.status`. Modules trouvés : common-contract, quarkus-contract, facturation, front, charts/facturation, avec `fichier:ligne`. La réponse cite les modules `[n]`. | US2-1, FR-009 à FR-012 |
| 5 | Lire l'étape. | `relance` « à vérifier » avec la chaîne facturation ← quarkus-contract ← common-contract. Le pom agrégateur « agrège common-contract ». | US3-1, FR-018 |
| 6 | Lire l'étape. | `notification` « à vérifier », relié à facturation (REST). `annuaire` absent. | US4-1, FR-019 |
| 7 | La qualification rejouée omet `front`. | `front` figure sous « Non qualifiés ». Aucun module de l'étape 4 ne manque. | US2-3, FR-013, FR-014, SC-004 |
| 8 | Vérifier les pièges. | Aucune occurrence de `.env`, `settings.xml`, `node_modules/`, `target/`, du dossier exclu par `.gitignore`, du fichier binaire, ni de la cible du lien symbolique. | FR-003 à FR-005, FR-023, SC-005 |
| 9 | Répondre « cherche aussi `contract_suspended` ». | Nouvelle analyse avec le terme ajouté. Le module `batch-suspension` est marqué « Nouveau ». | US2-4, FR-015 |
| 10 | Projet sans racine : poser la même question. | Pas d'analyse. Message « Aucune source du code n'est déclarée », avec le lien vers `/projet/reglages#sources-code`. | US2-5 |
| 11 | Recharger la page de la conversation. | Les analyses sont réaffichées à l'identique, qualifications comprises. | R9 |
| 12 | Rédiger une spécification depuis la discussion, puis la générer. | Rubrique « Modules touchés » avec les modules et leurs preuves. Les modules à vérifier figurent parmi les hypothèses. | US5-1, US5-2, FR-021 |
| 13 | Supprimer la racine dans les réglages. | La conversation affiche toujours ses analyses passées. | R9 |
| 14 | Lire le journal (`/journal`) et les fichiers de données. | Aucun chemin de fichier, aucun terme, aucun extrait. Seulement des compteurs. | FR-024, SC-005 |
| 15 | Passer axe sur les réglages et sur l'étape d'analyse. Parcourir au clavier. | Aucune violation. Groupes et tableaux navigables, occurrences dépliables au clavier, fin annoncée. | FR-027 |

## Tests ciblés

- **Domaine** :
  - `GitIgnoreRulesTest` (négation, ancrage, dossiers, `**`) ;
  - `SourceExclusionsTest` ;
  - `ModuleGraphTest` (chaînes, cycles, coordonnées externes) ;
  - `PerimeterTermsTest` ;
  - `PerimeterAnalysisTest` (tri, règles de qualification).
- **Application** : `AnalyzePerimeterTest` (doubles de `SourceTree` et du LLM),
  `ConverseTest` (action `PERIMETER`, une seule par tour, sans racine).
- **Infrastructure** :
  - `LocalSourceTreeTest` (arbre temporaire : liens symboliques, binaires, taille, limites,
    déterminisme) ;
  - `MavenPomReaderTest` (XXE refusé, héritage du `groupId`, BOM, profils) ;
  - `V24MigrationIT`, `SourceRootResourceIT`.
- **Front** (Vitest) : `sw-source-roots`, `sw-perimeter-step` (groupes, non qualifiés,
  annonce), rendu `[n]` d'une source `MODULE`.
- **Performance** (tag `perf`, hors gates) : 60 modules et 20 000 fichiers générés, moins de
  30 s (SC-003).
- **Évaluation** (tag `eval`, vrai Copilot, **avec l'accord de l'utilisateur**) :
  `./mvnw -pl backend verify -Peval -Dtest=PerimeterEval`, rappel des termes ≥ 0,9 (SC-006).
