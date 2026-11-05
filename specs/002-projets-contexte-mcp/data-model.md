# Data Model: Projets, connaissance du projet et accès des agents de code

Complète le modèle de la feature 001 ([data-model.md](../001-local-spec-assistant/data-model.md)).
Migrations Flyway à partir de `V7__projects.sql` (tasks.md ; research.md R6, R7, R8, R11, R18 à
R25). Les numéros suivants sont attribués dans l'ordre de livraison ; une migration appliquée ne
change jamais.

## Project (nouveau)

| Champ | Type | Règles |
|---|---|---|
| id | UUID | |
| name | texte | 1 à 80 caractères, sans espace en début ni en fin. Unique sans tenir compte de la casse (`name_key` = nom en minuscules, index unique). |
| description | texte | 1 000 caractères maximum, facultative. |
| jiraProjectKey | texte | Facultative. Format de clé Jira `[A-Z][A-Z0-9_]{1,9}`. Surcharge le projet par défaut de la configuration Jira (FR-006). |
| context | texte (Markdown) | **Fiche générale** (FR-008) : 20 000 caractères maximum, vide par défaut, rédigée à la main. Reçoit la fusion des deux fiches de V9 et des réponses Q-n (research.md R25). |
| contextHash | texte | SHA-256 de `context`. Entre dans l'empreinte de génération (FR-011). |
| contextUpdatedAt | date | Date du dernier enregistrement. |
| architectureWorkspace | texte | Facultatif : chemin absolu du `workspace.dsl` du projet (FR-013). |
| architectureOwnership | texte | Facultatif : valeur de la propriété `ownership` qui désigne ce projet (research.md R21). |
| knowledgeSyncedAt | date | Fin de la dernière indexation réussie, complète ou incrémentale. |
| jiraSyncMark | date | Plus grande date `updated` des tickets lus (research.md R20). |

`contextOrigin` et les colonnes `technical_context*` de V9 sont supprimées par la migration de
fusion.
| createdAt, updatedAt | date | |

**Règles**
- Le « Projet par défaut » est créé par la migration, et toutes les sessions existantes lui sont
  rattachées (SC-001).
- Le dernier projet restant ne peut pas être supprimé (FR-002).
- Supprimer un projet impose un choix : déplacer ses sessions vers un autre projet, ou les
  supprimer (cascade).

## ProjectDocSource (nouveau)

| Champ | Type | Règles |
|---|---|---|
| id | UUID | |
| projectId | UUID | `REFERENCES project ON DELETE CASCADE`. |
| kind | enum | `URL` (adresse `http`/`https`, y compris `localhost`) ou `FOLDER` (dossier local du site généré). |
| location | texte | 2 000 caractères maximum. Une `URL` doit être absolue, en `http` ou `https`, sans identifiants (`user:pass@` refusé). Un `FOLDER` doit être un chemin absolu existant. |
| lastReadAt | date | Facultative. |
| lastResult | texte (JSON) | Pages lues, pages en erreur avec leur motif, borne atteinte. Ne contient jamais le contenu des pages. |

**Règles** : 10 sources au plus par projet. Le contenu lu n'est jamais stocké (FR-013b).

## ProjectContextQuestion, ContextProposal, ContextGeneration (retirés)

Les questions Q-n (V9), la proposition de fiches et la génération du contexte sont retirées
(research.md R25, R28). Les réponses déjà données sont reportées dans la fiche générale avant la
suppression de `project_context_question`.

## KnowledgeItem (nouveau, `knowledge_item`)

| Champ | Type | Règles |
|---|---|---|
| id | UUID | |
| projectId | UUID | `REFERENCES project ON DELETE CASCADE`. |
| kind | enum | `TICKET`, `DOC_SECTION`, `ADR`, `ARCH_ELEMENT`. |
| ref | texte | Clé du ticket, URL de la page avec ancre, ou nom canonique de l'élément (`Système/Conteneur/Composant`). Unique avec `projectId`, `kind` et `chunk`. |
| chunk | entier | Morceau d'un élément long (0 sinon), environ 1 500 caractères chacun. |
| title | texte | 500 caractères maximum. |
| body | texte | 8 000 caractères maximum, Markdown ou texte. |
| status | texte | Ticket : nom du statut ; ADR : `ACCEPTED`, `PROPOSED`, `SUPERSEDED`, `DEPRECATED`, `UNKNOWN`. |
| statusCategory | texte | Ticket : catégorie Jira (`new`, `indeterminate`, `done`) ; fermé si `done`. |
| supersededBy | texte | ADR remplacé : référence de l'ADR qui le remplace. |
| issueType | texte | Ticket : type Jira. |
| elementType | texte | Architecture : `PERSON`, `SOFTWARE_SYSTEM`, `CONTAINER`, `COMPONENT`. |
| scope | enum | Architecture : `PROJECT` ou `COMMON` (FR-038). |
| transport | booléen | Architecture : intermédiaire de transport (topic, file, bus, research.md R22). |
| role | enum | `VOCABULARY`, `ARCHITECTURE_OVERVIEW` ou nul (FR-042). |
| contentHash | texte | SHA-256 du titre et du corps ; un changement provoque la revectorisation. |
| sourceUpdatedAt | date | Date `updated` Jira, `lastmod` de la page ou date du fichier DSL. |
| indexedAt | date | |
| embedding | BLOB | Vecteur `float32` normalisé ; nul si le modèle est absent. |
| embeddingModel | texte | Nom et révision du modèle ; un autre modèle impose la revectorisation. |

**Règles**
- Index plein texte FTS5 `knowledge_fts(title, body)`, tenu par triggers.
- Le contenu ne quitte jamais le dossier de données, sauf en extraits retrouvés vers Copilot
  (FR-012) ; il n'est jamais journalisé.
- Un ticket supprimé dans Jira, une page disparue du sitemap ou un élément absent du modèle relu
  est supprimé de l'index.

## ArchRelation (nouveau, `arch_relation`)

| Champ | Type | Règles |
|---|---|---|
| projectId | UUID | `ON DELETE CASCADE`. |
| source, destination | texte | Noms canoniques d'éléments. |
| description, technology | texte | 500 caractères maximum. |

## ArchDeployment (nouveau, `arch_deployment`)

| Champ | Type | Règles |
|---|---|---|
| projectId | UUID | `ON DELETE CASCADE`. |
| element | texte | Nom canonique de l'élément déployé. |
| environment | texte | Nom de l'environnement de déploiement. |
| rootNode | texte | Nœud de déploiement racine (infrastructure : Azure, ITaaS…). |
| nodePath | texte | Chemin complet du nœud. |
| instances | entier | Nombre d'instances. |
| sharedInfrastructure | booléen | Nœud défini dans un fichier d'infrastructure partagée (research.md R21). |

## Indexation (en mémoire, non persistée)

Une par projet au plus, gardée 30 minutes après sa fin (research.md R14, R20) :
- `status` (`RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`), `mode` (`FULL`, `INCREMENTAL`), `step`
  (`MODEL`, `JIRA`, `DOCUMENTATION`, `ARCHITECTURE`, `EMBEDDING`), `current`, `done`, `total`,
  `startedAt`, `stepStartedAt`, `finishedAt` ;
- `errors` : `{kind, location, reason, httpStatus, message}`, avec `kind` parmi `PAGE`,
  `SOURCE`, `JIRA`, `ARCHITECTURE` (fichier et ligne), `MODEL` et `FAILURE`, sans contenu.

## KnowledgeSelection (valeur, enregistrée avec la version)

Connaissance transmise à l'IA pour une session (research.md R24) : fiche générale (hash), et pour
chaque élément retenu `{kind, ref, chunk, contentHash}`, plus les faits d'architecture calculés
(modules impactés et leur portée, échanges et leur existence, constats de déploiement).

## RelatedIssues (en mémoire, par session)

Tickets proches d'un besoin (FR-034) : `{key, title, status, statusCategory, statusFresh,
updatedAt, reason, url, parentKey}`, 5 au plus, hors famille de la story liée (R29) ; et le choix de l'utilisateur : `LINK_NEW`, `SAME`,
`DIFFERENT` ou `NONE`.

## Session (modifiée, feature 001)

| Champ ajouté | Type | Règles |
|---|---|---|
| projectId | UUID | Obligatoire, `REFERENCES project`. Projet courant à la création ; modifiable (déplacement, FR-004). |
| implementationStatus | enum | `DRAFT` (défaut), `READY`, `IN_PROGRESS`, `IMPLEMENTED` (FR-026). |
| implementedAt | date | Renseignée au passage à `IMPLEMENTED`, effacée en le quittant. |
| evolvedFromSessionId | UUID | Facultatif. Renseigné pour une évolution (FR-026b), `ON DELETE SET NULL`. |
| nextVersionNumber | entier | Numéro de la prochaine version. Il n'est jamais décrémenté, donc un numéro supprimé n'est pas réattribué (FR-014). |
| relatesTo | texte | Facultatif : clé du ticket fermé auquel la story sera liée à sa création dans Jira (FR-035). |

**Transitions de `implementationStatus`**

| De → vers | Utilisateur | Agent |
|---|---|---|
| DRAFT ↔ READY | oui | non |
| READY → IN_PROGRESS | oui | oui |
| IN_PROGRESS → IMPLEMENTED | oui | oui |
| IN_PROGRESS → READY ou DRAFT | oui | non |
| IMPLEMENTED → tout autre statut | oui, avec une confirmation qui rappelle `implementedAt` | non |

**Verrou** : en `IMPLEMENTED`, aucune écriture n'est possible sur la session et ses versions, sauf
le changement de statut par l'utilisateur et la suppression de la session (FR-026a).

**Features à implémenter** : les sessions `READY` ou `IN_PROGRESS` qui ont au moins une version.

## SpecificationVersion (modifiée, feature 001)

| Champ ajouté | Type | Règles |
|---|---|---|
| contextHash | texte | Hash de la fiche générale utilisée, vide si aucune (FR-011). |
| contextUpdatedAt | date | Date de la fiche utilisée. Affichée : « contexte du 2026-11-03 ». |
| knowledge | texte (JSON) | `KnowledgeSelection` utilisée (références et hash, faits d'architecture), sans contenu de ticket ni de page (research.md R24, R26). |

**Règles**
- `inputFingerprint` couvre aussi `contextHash` et la sélection de connaissance : à
  connaissance identique, la version existante est réutilisée (R12 de 001).
- La suppression d'une version est interdite si la session est verrouillée. Le lien Jira pointe
  alors sur la dernière version restante (research.md R8).

## SourceRef (modifiée, feature 001)

Nouveaux types (FR-010, research.md R24) :
- `PROJECT_CONTEXT` : `excerpt` est un extrait exact de la fiche générale, contrôlé par
  `TraceabilityValidator` comme `NEED_EXCERPT` ;
- `JIRA_ISSUE` (clé), `DOC_PAGE` (URL de page ou d'ADR) et `ARCH_ELEMENT` (nom canonique) : la
  référence doit appartenir à la sélection de connaissance de la session ; sinon elle est ramenée
  ou retirée (`Citations`).

## DraftSpecification (modifiée, feature 001)

- `adrDeviations` : `[{adrRef, decision, deviation}]`, rendus dans la rubrique « Écarts aux ADR »
  (FR-037).
- Les rubriques « Architecture » (modules impactés et portée, flux manquants) et « Efforts
  supplémentaires (déploiement) » sont ajoutées par l'outil à partir des faits calculés, pas
  rédigées par l'IA (FR-039 à FR-041).

## Settings (modifiés, feature 001)

Table `app_settings` (une seule ligne), créée par V7 : les réglages de 001 sont répartis entre
`copilot_settings` et `jira_settings`, sans table commune.

| Champ ajouté | Type | Règles |
|---|---|---|
| currentProjectId | UUID | Projet courant, conservé d'un lancement à l'autre (FR-005). S'il est absent ou supprimé, c'est le premier projet par nom. |
| agentAccessEnabled | booléen | `false` par défaut (FR-024). |
| embeddingModelFolder | texte | Facultatif : dossier d'un modèle ONNX remplaçant le modèle par défaut (`model.onnx`, `tokenizer.json`, research.md R19). |
| embeddingPooling, embeddingQueryPrefix, embeddingPassagePrefix | texte | Réglages du modèle remplaçant ; ceux de e5 par défaut. |

## Fichiers `models/<nom>/` (dossier de données, hors base)

Modèle d'embedding téléchargé une fois (`model.onnx`, `tokenizer.json`, `model.json` avec la
révision et les SHA-256), vérifié avant chaque chargement (research.md R19).

## Fichier `logs/journal.jsonl` (dossier de données, hors base)

Journal des avertissements et des erreurs (FR-031, research.md R15) :
- une entrée JSON par ligne : `at`, `level` (`WARN`, `ERROR`), `logger`, `message`, `cause`,
  `stack` (20 lignes au plus), tout expurgé par `RedactingLogFilter.redact` ;
- rotation par taille : 2 Mo, 4 sauvegardes (`journal.jsonl.1` à `.4`), soit 10 Mo au plus ;
- relu du plus récent au plus ancien pour les 500 dernières entrées ; une ligne illisible est
  ignorée.

## Fichier `agent-endpoint.json` (dossier de données, hors base)

`{ "port": <int>, "secret": "<256 bits en base64url>" }`
- permissions `rw-------`, dans le dossier de données en `rwx------` ;
- écrit au démarrage si l'accès des agents est activé, réécrit à chaque régénération, supprimé à
  la désactivation ;
- le secret est persistant d'un lancement à l'autre ; seule une régénération le change. Il n'est
  jamais journalisé ni renvoyé par une API (research.md R2).

## AgentAccess (nouveau, journal)

| Champ | Type | Règles |
|---|---|---|
| id | UUID | |
| at | date | |
| agentName | texte | `clientInfo.name` annoncé par l'agent, 80 caractères maximum, non vérifié (FR-028). |
| operation | enum | `LIST_PROJECTS`, `LIST_FEATURES`, `GET_FEATURE`, `SET_STATUS`, `SEARCH_KNOWLEDGE` (la requête n'est pas consignée). |
| projectId, sessionId | UUID | Facultatifs selon l'opération ; `ON DELETE SET NULL`. |
| oldStatus, newStatus | enum | Pour `SET_STATUS` seulement. |

**Règles** : aucun contenu de spécification n'est stocké. Les entrées de plus de 90 jours sont
purgées au démarrage.

## TicketLink (nouveau, `ticket_link`, migration `V14`)

Liens d'un ticket indexé (research.md R29, FR-044).

| Champ | Type | Règles |
|---|---|---|
| projectId | UUID | `REFERENCES project ON DELETE CASCADE`. |
| sourceKey | texte | Clé du ticket indexé qui porte le lien. |
| kind | enum | `PARENT`, `SUBTASK` ou `LINK`. |
| targetKey | texte | Clé du ticket visé, éventuellement d'un autre projet Jira. |
| linkType | texte | `LINK` : nom du type Jira (`Blocks`, `Relates`, `Duplicate`…) ; vide sinon. |
| direction | enum | `LINK` : `OUTWARD` ou `INWARD` ; nul sinon. |
| label | texte | `LINK` : libellé Jira du sens (« blocks », « is blocked by »), 100 caractères au plus. |

**Règles**
- Clé primaire `(projectId, sourceKey, kind, targetKey, linkType)` ; index sur
  `(projectId, targetKey)` pour retrouver le parent et les liens entrants.
- Les liens d'un ticket sont remplacés en bloc à chaque relecture du ticket ; ils disparaissent
  avec lui.
- `V14` remet `project.jira_sync_mark` à nul pour que la synchronisation suivante lise les liens
  des tickets déjà indexés.

## SubtaskReport (calculé, non persisté)

Rubrique « Sous-tâches de <clé> » d'une version (FR-046, FR-047) :
- `storyKey`, `parentKey` (si la story liée est elle-même une sous-tâche), `matching`
  (`SEMANTIC` ou `UNAVAILABLE` avec la raison) ;
- `subtasks` : `{key, title, url, status, statusCategory, statusFresh, statusDate, useCase
  {code, title} | null, alternative {code, title} | null, closeness (VERY_CLOSE | CLOSE) | null,
  change (CHANGED | REMOVED) | null, state (TO_REVIEW | DELIVERED_TO_REWORK | CONSISTENT |
  UNMATCHED)}`, triées : `TO_REVIEW`, `DELIVERED_TO_REWORK`, puis les autres.

## KnowledgeSelection (complétée)

Gagne `subtasks` : `{ref, status, closed, title}` de la famille de la story (15 au plus), et pour
les tickets retenus le lien explicite éventuel avec la story (`label`). Ils entrent dans
l'empreinte (FR-011) par leur référence et leur statut.
