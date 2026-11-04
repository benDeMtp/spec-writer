# Data Model: Projets, contexte de projet et accès des agents de code

Complète le modèle de la feature 001 ([data-model.md](../001-local-spec-assistant/data-model.md)).
Migrations Flyway `V7__projects.sql` à `V11__implementation_status_agents.sql` (tasks.md ; research.md R6, R7, R8, R11, R16).

## Project (nouveau)

| Champ | Type | Règles |
|---|---|---|
| id | UUID | |
| name | texte | 1 à 80 caractères, sans espace en début ni en fin. Unique sans tenir compte de la casse (`name_key` = nom en minuscules, index unique). |
| description | texte | 1 000 caractères maximum, facultative. |
| jiraProjectKey | texte | Facultative. Format de clé Jira `[A-Z][A-Z0-9_]{1,9}`. Surcharge le projet par défaut de la configuration Jira (FR-006). |
| context | texte (Markdown) | Fiche **fonctionnelle** (FR-008a) : 20 000 caractères maximum, vide par défaut. |
| contextHash | texte | SHA-256 de `context`, recalculé à chaque enregistrement. Entre dans l'empreinte de génération (FR-011). |
| contextOrigin | enum | `NONE`, `GENERATED`, `EDITED` (modifié à la main, après génération ou non). |
| contextUpdatedAt | date | Date du dernier enregistrement de la fiche fonctionnelle. |
| technicalContext, technicalContextHash, technicalContextOrigin, technicalContextUpdatedAt | | Fiche **technique**, mêmes règles (V9, research.md R16). |
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

## ProjectContextQuestion (nouveau, V9)

| Champ | Type | Règles |
|---|---|---|
| id | UUID | |
| projectId | UUID | `REFERENCES project ON DELETE CASCADE`. |
| number | entier | Unique dans le projet ; la référence citable est `Q-<number>` (FR-013i). |
| sheet | enum | `FUNCTIONAL` ou `TECHNICAL`. |
| label, rationale | texte | Posés par l'IA ; 500 caractères au plus chacun. |
| answer | texte | 2 000 caractères au plus ; vide si la question est passée. |
| state | enum | `ANSWERED` ou `SKIPPED`. |
| askedAt, answeredAt | date | `answeredAt` change à chaque modification de la réponse. |

**Règles** : seules les questions répondues ou passées sont enregistrées ; les autres restent dans
la proposition. Une réponse modifiée ne change aucune fiche avant la génération suivante.

## ContextProposal (en mémoire, non persistée)

Même cycle de vie que les propositions d'édition de 001 (`ProposalStore`) :
- elle contient `projectId`, pour **chaque fiche** le hash de base, le Markdown proposé et le diff
  unifié avec la fiche actuelle, puis les sources citées (URL de page, clé de ticket, `Q-n`), les
  pages en erreur et les questions de clarification en attente (FR-013h) ;
- chaque fiche s'accepte séparément, si son hash de base égale toujours le hash courant. Sinon,
  cette fiche a changé entre-temps, et il faut régénérer ou garder la fiche actuelle ;
- à l'acceptation, `contextOrigin` vaut `GENERATED`. Une modification manuelle ultérieure le fait
  passer à `EDITED`.

## ContextGeneration (en mémoire, non persistée)

Une par projet au plus, gardée 30 minutes après sa fin (research.md R14) :
- `status` (`RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`), `step` (`READING_DOCS`,
  `READING_TICKETS`, `SUMMARIZING`, `BUILDING`), `current`, `done`, `total`, `startedAt`,
  `finishedAt` ;
- `errors` : `{kind, location, reason, httpStatus, message}`, sans contenu de page ni de ticket ;
- `proposal` : la `ContextProposal` obtenue, une fois réussie. Une génération interrompue n'en a
  pas ;
- les faits extraits et les références connues, gardés pour une révision après les réponses
  (research.md R16), jamais renvoyés par l'API.

## Session (modifiée, feature 001)

| Champ ajouté | Type | Règles |
|---|---|---|
| projectId | UUID | Obligatoire, `REFERENCES project`. Projet courant à la création ; modifiable (déplacement, FR-004). |
| implementationStatus | enum | `DRAFT` (défaut), `READY`, `IN_PROGRESS`, `IMPLEMENTED` (FR-026). |
| implementedAt | date | Renseignée au passage à `IMPLEMENTED`, effacée en le quittant. |
| evolvedFromSessionId | UUID | Facultatif. Renseigné pour une évolution (FR-026b), `ON DELETE SET NULL`. |
| nextVersionNumber | entier | Numéro de la prochaine version. Il n'est jamais décrémenté, donc un numéro supprimé n'est pas réattribué (FR-014). |

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
| contextHash | texte | Hash combiné des deux fiches utilisées, vide si aucune (FR-011, research.md R16). |
| contextUpdatedAt | date | Date de la fiche la plus récente des deux. Affichée : « contexte du 2026-11-03 ». |

**Règles**
- `inputFingerprint` couvre aussi `contextHash` : à contexte identique, la version existante est
  réutilisée (R12 de 001).
- La suppression d'une version est interdite si la session est verrouillée. Le lien Jira pointe
  alors sur la dernière version restante (research.md R8).

## SourceRef (modifiée, feature 001)

Nouveau type `PROJECT_CONTEXT`, dont l'`excerpt` est un extrait exact du contexte du projet
utilisé. Il est contrôlé par `TraceabilityValidator` comme `NEED_EXCERPT` (FR-010).

## Settings (modifiés, feature 001)

Table `app_settings` (une seule ligne), créée par V7 : les réglages de 001 sont répartis entre
`copilot_settings` et `jira_settings`, sans table commune.

| Champ ajouté | Type | Règles |
|---|---|---|
| currentProjectId | UUID | Projet courant, conservé d'un lancement à l'autre (FR-005). S'il est absent ou supprimé, c'est le premier projet par nom. |
| agentAccessEnabled | booléen | `false` par défaut (FR-024). |

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
| operation | enum | `LIST_PROJECTS`, `LIST_FEATURES`, `GET_FEATURE`, `SET_STATUS`. |
| projectId, sessionId | UUID | Facultatifs selon l'opération ; `ON DELETE SET NULL`. |
| oldStatus, newStatus | enum | Pour `SET_STATUS` seulement. |

**Règles** : aucun contenu de spécification n'est stocké. Les entrées de plus de 90 jours sont
purgées au démarrage.
