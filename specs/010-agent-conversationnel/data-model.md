# Modèle de données : agent conversationnel

## Conversation (`domain/conversation/Conversation`)

| Champ | Type | Règles |
|---|---|---|
| id | UUID | |
| projectId | UUID | projet de la conversation ; la conversation ne voit que lui |
| name | texte | 1 à 120 caractères ; par défaut, début du premier message (60 caractères) |
| createdAt, updatedAt | instant | `updatedAt` change à chaque message ; tri de la liste |

Supprimée avec son projet (cascade). Supprimer une conversation supprime ses messages, ses étapes,
ses sources et ses documents joints. Les spécifications démarrées depuis elle restent, sans lien.

## Message (`ConversationMessage`)

| Champ | Type | Règles |
|---|---|---|
| id | UUID | |
| conversationId | UUID | |
| ordinal | entier | ordre dans la conversation |
| role | `USER` \| `AGENT` | |
| text | texte | utilisateur : 1 à 8 000 caractères ; agent : 100 000 au plus |
| state | `COMPLETE` \| `INCOMPLETE` \| `RUNNING` | `RUNNING` pendant un tour ; arrêt, panne ou redémarrage : `INCOMPLETE` |
| historyTruncated | booléen | agent : des messages anciens n'ont pas été transmis |
| createdAt | instant | |

Un seul tour `RUNNING` par conversation à la fois.

## Étape de recherche (`SearchStep`)

| Champ | Type | Règles |
|---|---|---|
| messageId | UUID | message de l'agent |
| ordinal | 1 à 5 | 5 au plus par réponse |
| query | texte | 300 caractères au plus |
| kinds | sortes cherchées | TICKET, DOC_SECTION, ADR, ARCH_ELEMENT, SPECIFICATION |
| hits | entier | nombre de résultats |

## Source citée (`CitedSource`)

| Champ | Type | Règles |
|---|---|---|
| messageId | UUID | |
| n | entier | numéro cité dans la réponse (`[n]`) |
| kind | sorte | dont `SPECIFICATION` et `ATTACHMENT` |
| ref | texte | référence de la connaissance, `spec:<sessionId>@v<n>` ou `joint:<nom>` |
| title | texte | |

Seules les sources citées par la réponse sont gardées ; un numéro sans source du tour est retiré
du texte.

## Document joint de conversation (`ConversationAttachment`)

Même forme et mêmes règles que `Attachment` (009) : texte extrait seul, 5 au plus, doublons
refusés. Rattaché à la conversation. Copié dans la session (texte) quand une spécification démarre.

## Brouillon de spécification (`NeedDraft`, non enregistré)

| Champ | Type | Règles |
|---|---|---|
| need | texte | règles de `Need` (10 mots, 10 000 caractères) |
| points | liste de `EstablishedPoint` | 10 au plus |

`EstablishedPoint` : `theme` (`QuestionTheme`), `question` (500 caractères au plus), `answer`
(5 000 au plus), `basis` (`USER` \| `SOURCE`), `sourceRef` (si `SOURCE`, une source citée de la
conversation).

À la validation, chaque point devient une `Question` à l'état `ANSWERED` (lot 0, justification
« Établi dans la conversation ») du questionnaire de la nouvelle session.

## Lien conversation → spécification (`conversation_session`)

`(conversationId, sessionId)`, supprimé avec l'une ou l'autre.

## Budget (`KnowledgeBudget`, 008)

Deux parts nouvelles, proportionnelles au total comme les autres :

- `conversationSources` (STANDARD 20 000 caractères, extrait de 1 500 par résultat) ;
- `conversationHistory` (STANDARD 8 000).

## Migration `V18__conversation.sql`

Tables `conversation`, `conversation_message`, `conversation_step`, `conversation_source`,
`conversation_attachment`, `conversation_session`. Clés étrangères en `ON DELETE CASCADE`,
contraintes CHECK sur les rôles, les états et les longueurs. Index
`(project_id, updated_at)` et `(conversation_id, ordinal)`.
