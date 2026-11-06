# Research: Tickets fermés gardés en cache

Prolonge `specs/002-projets-contexte-mcp/research.md` (R20 indexation, R23 tickets proches, R29
liens et sous-tâches). Aucun point de la spec n'était à clarifier ; les décisions ci-dessous
portent sur la manière.

## R1. Requête Jira de l'indexation complète (FR-002, FR-004)

**Constat** : en mode complet, `SynchronizeKnowledge` appelle `searchUpdatedSince` sans repère
(JQL `project = X ORDER BY updated ASC`, champs complets) : tous les tickets sont relus, puis
`listKeys` retire les tickets supprimés.

**Decision** : quand le projet a un repère Jira (`jira_sync_mark` non nul), l'indexation complète
procède en trois temps :

1. **Ouverts et modifiés** : JQL `project = X AND (statusCategory != Done OR updated >= "<repère
   moins la marge>") ORDER BY updated ASC`, champs complets, paginée par 50. Le repère avance
   comme aujourd'hui, ticket par ticket, puisque l'ordre est croissant.
2. **Clés** : `listKeys` (champ `key` seul, comme aujourd'hui) donne les clés du projet. Sont lus
   ensuite, par clé : les clés absentes de l'index (nouveaux tickets, tickets fermés jamais lus,
   tickets d'un projet Jira changé) et les tickets indexés ouverts que l'étape 1 n'a pas ramenés
   (statut indexé faux, cas limite de la spec). JQL `key in (K1, …, K50) ORDER BY key ASC` par
   lots de 50, avec `validateQuery=warn` : une clé supprimée entre-temps donne un avertissement,
   pas une erreur 400. Cette lecture n'avance pas le repère.
3. **Retrait** : `index.retain` sur les clés de l'étape 2, comme aujourd'hui.

Sans repère (premier passage, ou remise à zéro par une migration), la lecture complète d'aujourd'hui
est gardée : c'est la relecture unique de FR-008 (R5).

La synchronisation incrémentale ne change pas : `updated >= repère`, fermés compris (FR-003).

**Rationale** : la condition `statusCategory != Done` est évaluée par Jira, sur la même définition
de « fermé » que la 002 (FR-001). Les tickets fermés absents de l'index sont rares (nouveau
projet Jira, ticket rendu visible) ; la liste des clés est déjà lue en mode complet, la différence
ne coûte rien de plus. `statusCategory` est un champ JQL de Jira Server depuis la 7.x.

**Alternatives considered** :
- *Exclure les fermés de la requête et relire tous les absents par `getIssue`* : une requête par
  ticket, trop lent sur un nouveau projet.
- *JQL `key not in (<indexés fermés>)`* : la requête grossit avec l'index (des milliers de clés),
  au-delà des limites de longueur d'URL.
- *Garder la lecture complète et ne sauter que la réécriture* : l'écriture est déjà évitée par
  l'empreinte ; le coût est la lecture Jira, que la demande vise.

## R2. Statuts montrés sans requête (FR-006, SC-002)

**Decision** : `FindRelatedIssues.fresh` et `ReportSubtasks.status` n'appellent plus
`issueStatus` pour un ticket indexé fermé (`KnowledgeItem.closed()`). Le statut indexé est rendu
avec `fresh = true` : le front n'affiche alors aucune mention « Jira n'a pas pu être relu », sans
changement côté front. Une sous-tâche absente de l'index garde la relecture.

**Rationale** : `fresh` signifie déjà « statut digne de confiance » pour l'affichage. Un ticket
fermé supprimé dans Jira reste montré jusqu'à la prochaine indexation complète, qui le retire
(FR-004) : c'est le prix de l'absence de requête, accepté par la spec (Jira coupé).

**Alternatives considered** : *nouveau champ `cached`* : il changerait le contrat et le front pour
un affichage identique.

## R3. Vecteurs et durée de vie (FR-005, FR-007)

**Constat** : `SqliteKnowledgeIndex.save` garde le vecteur quand l'empreinte ne change pas ;
`withoutVector` sélectionne les éléments d'un autre modèle et la vectorisation part du texte
indexé, sans Jira. Aucune expiration n'existe dans l'index.

**Decision** : pas de changement de code ; tests de non-régression : changement de modèle sans
appel Jira pour les tickets fermés, horloge avancée d'un an sans relecture ni retrait.

## R4. Bilan de l'indexation (FR-009)

**Decision** : nouveau `TicketSummary(read, keptClosed, removed)` produit par l'étape Jira et
transmis par `IndexationProgress.tickets(...)` (méthode par défaut sans effet). `Indexations.Snapshot`
le garde et l'API l'expose dans `Indexation.tickets` (contracts/openapi-delta.md). Le panneau de
connaissance affiche une ligne à la fin : « 20 tickets relus, 80 tickets fermés gardés sans
relecture, 0 retiré ». Le journal écrit les mêmes nombres, sans clé ni contenu.

- `read` : tickets lus dans Jira (étapes 1 et 2 de R1, ou lecture complète).
- `keptClosed` : tickets indexés fermés présents dans la liste des clés et non relus ; 0 en mode
  incrémental (la liste des clés n'y est pas lue).
- `removed` : retour de `index.retain`.

**Alternatives considered** : *bilan dans `KnowledgeStatus`* : il décrit l'index, pas une
indexation ; le bilan disparaît avec l'état de l'indexation, au bout de 30 minutes.

## R5. Relecture unique après une mise à jour (FR-008)

**Decision** : convention déjà appliquée par V14 : une migration qui a besoin de relire les
tickets remet `project.jira_sync_mark` à nul. La synchronisation suivante, complète ou
incrémentale, relit tout, puis R1 s'applique de nouveau. Le journal l'annonce : « Connaissance :
relecture de tous les tickets (pas de repère de synchronisation) ».

## R6. Mesures (SC-001, SC-004)

**Decision** :
- SC-001 : IT WireMock, 100 tickets dont 80 fermés, déjà indexés : l'indexation complète demande
  au plus 20 tickets à Jira (compte des tickets renvoyés par les stubs) ; la durée de l'étape Jira
  est comparée à une lecture complète avec un délai fixe par page simulé, rapport ≥ 2.
- SC-002 : test unitaire et E2E avec le stub de statut en erreur 500 : aucun appel pour un ticket
  fermé, aucune mention.
- SC-003 : test unitaire, ticket rouvert ramené par la requête incrémentale (date modifiée).
- SC-004 : jeu de référence de la 002 : recherche (termes et sens) avant puis après une
  indexation complète, résultats identiques sur les tickets fermés.
