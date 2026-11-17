# Quickstart : vérifier la feature 015

Prérequis : JDK 25 en tête du `PATH`, `env -u JAVA_TOOL_OPTIONS` (CLAUDE.md, « Pièges de
l'environnement »). Le dev mode rejoue le LLM ; Jira est un WireMock.

## 1. Gates automatiques

```bash
rm -f backend/target/jacoco.exec
./mvnw -B verify -DskipDependencyCheck        # unitaires, IT WireMock Jira, ArchUnit, front
./mvnw -B package -DskipTests && npm --prefix e2e test
```

Attendu : nouveaux tests verts, en particulier :
- `TaskProgressTest` (choix de `next`, statut calculé, R4, R7) ;
- `JiraTasksIT` (création de sous-tâche, transitions par catégorie, commentaire, refus, R2, R3) ;
- `TaskResourceIT` et `AgentTaskResourceIT` (codes d'erreur des contrats) ;
- `McpBridgeTest` (en-tête `X-Agent-Connection`, lecture de `{code, message}`) ;
- E2E `us15-taches.spec.ts` (parcours US1 à US3 sur le jar, Jira simulé, client MCP
  `e2e/mcp-client.ts`).

## 2. Proposition et validation (US1)

En dev mode, sur une spécification liée à un ticket du Jira simulé :
1. Onglet de la spécification, panneau « Tâches » : « Proposer des tâches ».
   Attendu : 3 à 8 tâches (fixture `propose-tasks`), base affichée, rien dans le journal WireMock.
2. Supprimer une tâche, en ajouter une, en monter une ; recharger la page : les corrections restent.
3. « Valider ». Attendu : un `POST /rest/api/2/issue` par tâche avec `fields.parent.key`, clés
   affichées, « 0 tâche sur N terminée ».
4. Sur une spécification sans ticket : bouton indisponible, message « Envoyez d'abord la
   spécification vers Jira ».

## 3. Suivi par un agent (US2)

Avec `e2e/mcp-client.ts` (deux processus pour deux connexions) :
1. `next_task` → première tâche ; `start_task` → `POST …/transitions` vers une transition de
   catégorie `indeterminate` ; journal des agents : `START_TASK`.
2. Second processus : `start_task` sur la même clé → `TASK_HELD` nommant l'agent ;
   `next_task` → tâche suivante.
3. Arrêter le premier processus, relancer : `next_task` → la tâche en cours, `resume: true`.
4. `complete_task` avec un compte rendu → transition `done` et `POST …/comment`.
5. Couper le WireMock, `complete_task` → `jiraSync: "PENDING"` et message ; relancer WireMock,
   ouvrir la spécification → transition envoyée, plus de tâche « à synchroniser ».

## 4. Avancement et statut (US3)

1. Dans le WireMock, passer une sous-tâche en catégorie `done` ; rouvrir : avancement mis à jour.
2. Toutes terminées → statut « Implémentée » ; en rouvrir une → « En cours ».
3. Choisir « Prête » à la main → suivi suspendu, `next_task` → `TRACKING_SUSPENDED` ;
   « Reprendre le suivi » → statut recalculé.

## 5. Énoncé de story (US4)

Générer une spécification avec un document joint : elle s'ouvre sur « En tant que … je veux …
afin de … » ; l'export Markdown et l'aperçu Jira le reprennent.

## 6. Évaluation réelle (mainteneur ou CI eval)

```bash
./mvnw -pl backend verify -Peval -DskipDependencyCheck
ci/check-eval.sh
```

Attendu : `taskBounds` 1.0, `taskBasisExact`, `taskModuleCoverage`, `taskVerifiable`,
`storyStatement` ≥ 0.9, aucun indicateur existant sous sa base ; `last-run.json` commité.

## 7. Vérification manuelle (mainteneur, profil `live`)

Sur un vrai Jira de test : proposer, valider, puis faire travailler Claude Code par la passerelle
sur deux tâches ; vérifier les sous-tâches et les commentaires dans Jira.
