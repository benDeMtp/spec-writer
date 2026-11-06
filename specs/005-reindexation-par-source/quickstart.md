# Quickstart : valider la réindexation par source

Prérequis : environnement du `CLAUDE.md` (JDK 25, `env -u JAVA_TOOL_OPTIONS`).

## Tests automatisés

```bash
cd backend
../mvnw -B -o -q test -DskipDependencyCheck -Djacoco.skip=true -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='SynchronizeKnowledgeTest,IndexationScopeTest,IndexationsTest,KnowledgeResourceIT'
npx --prefix ../frontend ng test --watch=false
```

| Scénario | Test | Attendu |
|---|---|---|
| US1-1 | `SynchronizeKnowledgeTest` | erreur inattendue à Jira → documentation et architecture indexées, erreur `JIRA/UNEXPECTED`, repère Jira inchangé |
| US1-2 | `SynchronizeKnowledgeTest` | une source de documentation en erreur → l'autre indexée, aucun retrait |
| US1-3 | `SynchronizeKnowledgeTest` | architecture en erreur → empreinte gardée, Jira et documentation indexés |
| US1-5 | `SynchronizeKnowledgeTest` | annulation → `Cancelled` remonte |
| US1 fin | `IndexationsTest` | statut `SUCCEEDED` avec erreurs |
| US2-2, 3, 4 | `SynchronizeKnowledgeTest` | seules les sources choisies sont lues ; pas de retrait de documentation ; date « Synchronisée le » inchangée |
| US2-6 | `IndexationScopeTest`, `KnowledgeResourceIT` | liste vide, valeur inconnue, `INCREMENTAL` avec liste → 400 |
| US2-1, 7 | `KnowledgeResourceIT` | `sources` du statut : seules les sources déclarées |

E2E (`us8-project-context.spec.ts`, jeu fictif) : « Réindexer des sources… », cocher la
documentation, lancer : aucune requête Jira au WireMock ; bilan « Sources réindexées » ; axe sans
violation.

## Essai manuel (projet de l'utilisateur)

1. Réindexer la seule source de documentation : Jira n'est pas relu (bilan sans tickets), les
   ADR restent 21.
2. Réindexer le seul modèle d'architecture : 134 éléments, « Synchronisée le » inchangée.
