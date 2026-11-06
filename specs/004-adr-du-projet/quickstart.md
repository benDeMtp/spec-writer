# Quickstart : valider les ADR du projet

Prérequis : environnement du `CLAUDE.md` (JDK 25, `env -u JAVA_TOOL_OPTIONS`).

## Tests automatisés

```bash
cd backend
../mvnw -B -o -q test -DskipDependencyCheck -Djacoco.skip=true -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='AdrNumberTest,ListAdrsTest,SelectKnowledgeTest,SqliteKnowledgeIndexIT,AdrResourceIT,DocFileResourceIT'
npx --prefix ../frontend ng test --watch=false
```

| Scénario | Test | Attendu |
|---|---|---|
| US1, SC-001 | `SqliteKnowledgeIndexIT` | 3 ADR dont un en 3 morceaux → `count(ADR) = 3` ; section en 2 morceaux → 1 |
| US2-2, 5, 6 | `ListAdrsTest` | tri par numéro, `UNKNOWN`, doublons, successeur introuvable, `sentToAi` |
| US2-3 (dossier) | `DocFileResourceIT` | page servie avec CSP `sandbox allow-same-origin; script-src 'none'` ; `..`, absolu, extension refusée → 404 |
| US2-8 | `AdrResourceIT` | projet sans ADR → liste vide ; projet inconnu → 404 |
| US3-1 à 3 | `SelectKnowledgeTest` | ADR entier, une fois, morceaux dans l'ordre ; budget 6 000 / 4 000 ; « [ADR tronqué] » à une ligne de titre |
| US3-5 | `SelectKnowledgeTest` | deux préparations identiques |

E2E (`us8-project-context.spec.ts`, jeu fictif) : « 3 ADR » est un lien ; la page liste les ADR
triés, l'ADR remplacé grisé « non transmis à l'IA » ; le titre ouvre un nouvel onglet ; axe sans
violation.

## Évaluation (SC-004)

Avec l'accord de l'utilisateur (environ 150 requêtes Copilot) :

```bash
env -u JAVA_TOOL_OPTIONS ./mvnw -B -pl backend verify -Peval -DskipDependencyCheck
bash ci/check-eval.sh
```

Scores ≥ `baseline.json` ; `last-run.json` commité.

## Essai manuel (projet de l'utilisateur)

1. Page Contexte : « 21 ADR » (et non 57), en lien.
2. Page des ADR : 21 lignes, « 6 acceptés, 15 proposés », 0005 et 0017 signalés en double ; un
   titre ouvre la page de l'ADR dans un nouvel onglet.
3. Génération qui touche l'ADR 0017 : l'IA ne dit plus que son texte complet n'est pas fourni.
