# Data Model: Interview d'une spécification avec les sachants

## Format de la spécification (JSON de `specification_version.content`)

Aucune migration de données : les champs ajoutés se lisent absents dans les versions existantes
(research.md R1).

### Anchor (nouveau)

| Champ | Type | Règle |
|---|---|---|
| `element` | texte | code d'un élément de la même version : `UC-nn`, `SC-nn-nn`, `EF-nn`, `ET-nn` ou `SUMMARY` |
| `step` | entier, nul | use case seulement : 1 à n dans `nominalFlow`, ou dans `alternatives` si `alternative` |
| `alternative` | booléen | faux par défaut |

Une ancre qui ne désigne aucun élément de la version (code inconnu, étape hors limites) est
retirée à l'enregistrement (FR-020).

### Assumption (modifié)

`code`, `statement`, `origin`, `questionId` (inchangés) + `anchor` (nul) + `fromCode` (nul ; code
de l'hypothèse dans la version de base, posé par la révision, R3).

### SourceDeviation (modifié)

Champs existants + `code` (`EC-01`…, attribué par `IdentifierAssigner` ; nul dans les versions
antérieures, où il est attribué à la lecture par position) + `anchor` (nul) + `fromCode` (nul).

### OpenQuestion (nouveau)

| Champ | Type | Règle |
|---|---|---|
| `code` | texte | `Q-01`… attribué par `IdentifierAssigner` puis renuméroté par `InterviewOutcome` |
| `question` | texte | 1 à 1 000 caractères |
| `owner` | texte, nul | ≤ 120 caractères |
| `due` | date, nulle | |
| `anchor` | Anchor, nul | |
| `fromCode` | texte, nul | code du point ouvert dans la version de base (révision) |

`SpecificationContent.openQuestions` : liste, vide par défaut, 50 au plus.

### Brouillon du modèle (`DraftSpecification`)

- `DraftAnchor(target, index, step, alternative)` : `target` ∈ `USE_CASE`, `SCENARIO`,
  `REQUIREMENT`, `SUMMARY` ; `index` position dans la liste correspondante de la sortie.
- `DraftAssumption` et `DraftSourceDeviation` + `anchor`, `fromCode`.
- `DraftOpenQuestion(question, owner, anchor, fromCode)` ; liste `openQuestions` (révision
  seulement).

## Séance (migration `V30__interview.sql`)

### interview

| Colonne | Type | Règle |
|---|---|---|
| `id` | UUID | |
| `project_id` | UUID | projet de ses spécifications |
| `state` | texte | `PREPARED` → `IN_PROGRESS` → `FINISHED` |
| `participants` | JSON | ≤ 20 noms, chacun 1 à 120 caractères |
| `created_at`, `started_at`, `finished_at` | instants | |
| `cursor_session_id`, `cursor_element`, `cursor_step` | | dernier élément affiché, pour reprendre (FR-006) |

Transitions : `PREPARED` (préparée depuis la page « Spécifications ») → `IN_PROGRESS` (« Démarrer »,
ou « Faire le point » qui crée directement une séance en cours) → `FINISHED` (« Terminer »). Une
séance préparée se modifie et se supprime ; une séance en cours ou terminée ne se supprime pas
(son compte rendu reste consultable, FR-012), sauf avec sa spécification.

### interview_spec

| Colonne | Type | Règle |
|---|---|---|
| `interview_id`, `session_id` | UUID | clé ; cascade à la suppression de l'un ou de l'autre |
| `rank` | entier | ordre de passage |
| `version` | entier, nul | version relue, fixée au démarrage (R11) |
| `excluded` | JSON | codes des points écartés à la préparation |
| `in_progress` | booléen | vrai tant que la séance est en cours ; index unique partiel (`session_id` où `in_progress`) : une séance en cours par spécification (FR-007) |
| `applied_version` | entier, nul | version créée par la révision de cette séance (FR-014, FR-025) |

### interview_point

| Colonne | Type | Règle |
|---|---|---|
| `id` | UUID | |
| `interview_id`, `session_id` | UUID | |
| `anchor_element`, `anchor_step`, `anchor_alternative` | | ancre (même règle que `Anchor`) ; élément nul : « Non rattachés » |
| `origin` | texte | `ASSUMPTION`, `SOURCE_DEVIATION`, `OPEN_QUESTION` (points de la spécification, `origin_code` obligatoire) ; `REMARK`, `QUESTION` (créés en séance) |
| `origin_code` | texte, nul | `H-nn`, `EC-nn`, `Q-nn` dans la version relue |
| `text` | texte | réponse ou remarque, ≤ 5 000 caractères ; obligatoire sauf `CONFIRMED` et `REMOVE` |
| `outcome` | texte, nul | `CONFIRMED`, `CHANGE`, `REMOVE`, `DEFERRED` ; nul = non traité ; `QUESTION` est toujours `DEFERRED` |
| `owner` | texte, nul | ≤ 120 ; attendu pour `DEFERRED` (facultatif) |
| `due` | date, nulle | |
| `source_kind`, `source_ref` | | source désignée à la main (FR-027), élément de la connaissance du projet |
| `created_at`, `updated_at` | instants | |

Au plus 200 points par spécification et par séance.

## À reporter (même migration)

### source_update

| Colonne | Type | Règle |
|---|---|---|
| `id` | UUID | |
| `session_id` | UUID | cascade à la suppression de la spécification |
| `interview_id`, `point_id` | UUID | séance et point d'origine |
| `source_kind`, `source_ref`, `source_title` | | `DOC_PAGE`, `JIRA_ISSUE`, `ARCH_ELEMENT` (pas de document joint) |
| `fingerprint` | texte, nul | empreinte de la source à la création (R8) |
| `text` | texte | texte de la décision, tel que noté |
| `state` | texte | `OPEN` → `CLOSED` |
| `created_at`, `closed_at` | instants | |

État affiché : `OPEN` ; « source modifiée, à vérifier » quand l'empreinte courante diffère (ou que
la source n'est plus indexée) ; `CLOSED`. Jamais clos automatiquement (FR-029).

## En mémoire

- `ProposalStore.Proposal` + `interviewId` (nul) : à « Appliquer », la séance enregistre
  `applied_version` et crée ses « à reporter ».
- `conversation_message.interview_id` (colonne ajoutée, nulle) : marque le message de séance,
  pour son titre de source (« Séance du 20/11/2026 (Julie M., Paul D.) »).

## Valeurs dérivées

- **Points à traiter d'une spécification** (FR-022, FR-025) : hypothèses, écarts et points ouverts
  de la dernière version, moins ceux dont `origin_code` est tranché (`CONFIRMED`, `CHANGE`,
  `REMOVE`) dans une séance terminée sans `applied_version`.
- **Ordre de parcours** (FR-003) : résumé ; pour chaque use case : le use case, ses étapes, ses
  alternatives, ses scénarios ; exigences fonctionnelles puis techniques ; « Non rattachés ».
