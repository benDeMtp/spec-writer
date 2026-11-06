# Quickstart : valider les tickets fermés gardés en cache

Prérequis : environnement du `CLAUDE.md` (JDK 25, `env -u JAVA_TOOL_OPTIONS`). Aucun compte :
Jira simulé par WireMock, LLM rejoué.

## Tests automatisés

```bash
cd backend
../mvnw -B -o -q test -DskipDependencyCheck -Djacoco.skip=true -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='SynchronizeKnowledgeTest,FindRelatedIssuesTest,SubtaskReportTest,JqlBuilderTest,JiraKnowledgeSyncIT,KnowledgeReferenceSetIT'
```

| Scénario | Test | Attendu |
|---|---|---|
| US1-1, SC-001 | `JiraKnowledgeSyncIT` : 100 tickets dont 80 fermés indexés, indexation complète | ≤ 20 tickets demandés ; bilan `read=20, keptClosed=80, removed=0` ; étape Jira ≥ 2× plus rapide |
| US1-2 | ticket fermé absent de l'index | lu par `issuesByKeys`, puis plus relu à l'indexation complète suivante |
| US1-3, SC-003 | ticket fermé rouvert (date modifiée) | relu par l'incrémentale, devient ouvert |
| US1-4 | ticket ouvert fermé | relu une dernière fois, gardé fermé |
| US1-5 | ticket fermé absent de la liste des clés | retiré ; `removed=1` |
| Cas limite | clé supprimée entre la liste et la lecture | ignorée (`validateQuery=warn`), pas d'erreur |
| FR-008 | repère nul | tout est relu ; journal « relecture de tous les tickets » |
| US2-1/2, SC-002 | `FindRelatedIssuesTest`, `SubtaskReportTest` | aucun `issueStatus` pour un fermé ; `fresh=true` |
| US3, FR-005 | changement de modèle, horloge + 1 an | fermés revectorisés sans Jira, ni relus ni retirés |
| SC-004 | `KnowledgeReferenceSetIT` | recherches identiques avant et après l'indexation complète |

## Gates avant commit

```bash
rm -f backend/target/jacoco.exec
env -u JAVA_TOOL_OPTIONS ./mvnw -B verify -DskipDependencyCheck
env -u JAVA_TOOL_OPTIONS ./mvnw -B package -DskipTests
export PATH=$JAVA_HOME/bin:$PATH && npm --prefix e2e test
```

E2E (`us8-project-context.spec.ts`) : projet indexé, stub `issue/{key}` des tickets fermés en
erreur 500 ; le besoin saisi fait apparaître deux tickets proches fermés et un ouvert : les fermés
s'affichent sans la mention « Jira n'a pas pu être relu », l'ouvert la porte.

## Essai manuel (projet réel)

1. Indexer un projet ancien (indexation complète), noter la durée de l'étape Jira.
2. Relancer une indexation complète : le panneau affiche « N tickets relus, M tickets fermés
   gardés sans relecture » avec M proche du nombre de tickets fermés ; la durée baisse.
3. Rouvrir un ticket fermé dans Jira, lancer la synchronisation : il réapparaît comme ouvert dans
   les tickets proches.
