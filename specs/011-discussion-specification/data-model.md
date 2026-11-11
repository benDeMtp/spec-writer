# Modèle de données : discuter d'une spécification avec l'agent

## Domaine

### SpecificationDiscussion (`domain/conversation`)

Rattache une conversation (010) à une spécification.

| Champ | Type | Règle |
|---|---|---|
| `conversationId` | UUID | identifiant de la conversation rattachée |
| `sessionId` | UUID ou absent | absent quand la spécification a été supprimée et la discussion gardée |
| `specificationName` | texte, 1 à 200 caractères | nom de la spécification à la création de la discussion ; mis à jour quand la spécification est renommée |
| `current` | booléen | au plus une discussion suivie (`current`) par spécification |

Transitions :

- **création** : à l'ouverture du panneau, si la spécification n'a pas de discussion suivie ;
- **nouvelle discussion** : l'ancienne passe à `current = false`, la nouvelle est créée en
  `current = true` ;
- **suppression de la spécification, case cochée** : les conversations et leurs lignes sont
  supprimées ;
- **suppression de la spécification, case décochée** : `sessionId` devient absent et `current` faux.
  La conversation devient ordinaire, avec la mention « spécification supprimée ».

Une discussion n'accepte de message que si sa spécification existe et n'est pas verrouillée
(`SPECIFICATION_LOCKED`). Une discussion dont la spécification a été supprimée accepte des messages,
mais sans contexte de spécification.

### Contexte d'un tour (`ConversationMessage`, champ ajouté)

| Champ | Type | Règle |
|---|---|---|
| `specificationVersion` | entier ou absent | version transmise à l'agent pour ce tour ; absent hors discussion |

L'écran place le repère « Contexte : version N » devant le premier message de chaque version.

### Thinking.Stage (010, valeur ajoutée)

`REVISE` est l'étape de rédaction d'une proposition. Elle sert seulement pendant le flux et n'est
pas gardée.

### Source de discussion (`SourceKind.DISCUSSION`, valeur ajoutée)

Un message de l'utilisateur dans la discussion, citable par une proposition :

- référence `discussion:<conversationId>#<ordinal>` ;
- titre « Discussion du <date>, message <n> » ;
- jamais montré comme source dans une réponse de l'agent.

### Section de spécification discutée (source `SPECIFICATION`)

Une section (titre de niveau 2 ou 3) de la dernière version :

- référence `spec:<sessionId>@v<n>#<ancre>`, où l'ancre est calculée de la même façon par le
  découpage et par le rendu du document ;
- titre « <nom>, <titre de section> ».

### VersionOrigin.DISCUSSION (valeur ajoutée)

Version créée depuis une proposition de discussion. Elle est lue quand une ligne
`version_discussion` existe ; en base, la version garde `AI_EDIT` (research.md R6). Elle s'affiche
« Issue de la discussion ».

### Proposal (`ProposalStore`, champ ajouté)

| Champ | Type | Règle |
|---|---|---|
| `conversationId` | UUID ou absent | présent pour l'origine `DISCUSSION` ; repris par l'acceptation dans `version_discussion` |

### ConversationLlm (port, opération ajoutée)

`revise(ReviseRequest) → Revision`

- `ReviseRequest(language, generalSheet, need, answers, currentContent, currentMarkdown, memory,
  history, discussionSources, citedSources, correction)` ;
- `Revision(changed, reason, content)` : `content` est présent si `changed`.

### KnowledgeBudget (part ajoutée)

`discussedSpecification` est prise sur `RESERVE` : 40 000 caractères en standard, proportionnée au
modèle.

## Base (migration `V22__specification_discussion.sql`)

```text
specification_discussion
  conversation_id     TEXT PK → conversation(id) ON DELETE CASCADE
  session_id          TEXT NULL → session(id) ON DELETE SET NULL
  specification_name  TEXT NOT NULL CHECK (length BETWEEN 1 AND 200)
  current             INTEGER NOT NULL DEFAULT 1
  created_at          TEXT NOT NULL
  UNIQUE INDEX (session_id) WHERE current = 1 AND session_id IS NOT NULL
  INDEX (session_id)

version_discussion
  version_id          TEXT PK → specification_version(id) ON DELETE CASCADE
  conversation_id     TEXT NULL → conversation(id) ON DELETE SET NULL

ALTER TABLE conversation_message ADD COLUMN specification_version INTEGER NULL
```

Quand `session_id` passe à `NULL` (suppression avec discussions gardées), le cas d'usage remet aussi
`current` à 0 dans la même transaction.

## Front (`core/conversations/conversations.model.ts`)

- `ConversationSummary.specification?: { sessionId: string | null; name: string }`. `sessionId`
  `null` signifie « spécification supprimée ».
- `ConversationMessage.specificationVersion?: number | null`.
- `TurnEvent` : `stage` accepte `REVISE` ; `done` d'une proposition porte
  `{ proposalId } | { unchanged: true, reason }`.
- `VersionSummary.origin` accepte `DISCUSSION`, et `VersionSummary.conversationId?: string | null`.
