# Modèle de données : mention d'un document avec @

Aucune table ni migration (research.md R2, R9). Types nouveaux ou modifiés :

## `domain/knowledge/Mentionable` (nouveau, record)

| Champ | Type | Règle |
|---|---|---|
| `kind` | `MentionKind` (`DOCUMENT`, `PAGE`, `ADR`) | `DOCUMENT` : réf. `docs:` ; `PAGE` : `DOC_SECTION` rôle nul hors `docs:` ; `ADR` |
| `ref` | `String` | référence du document, sans `#` |
| `title` | `String` | titre de tête (non vide) |
| `path` | `String` | chemin relatif (dossier), chemin de la page, ou réf. de l'ADR |
| `size` | `int` | taille indexée totale, en caractères (estimation de la place) |

`names()` : noms acceptés (research.md R2), normalisés (minuscules `Locale.ROOT`, accents gardés
pour l'affichage, retirés pour la comparaison).

## `domain/knowledge/Mention` (nouveau, record)

| Champ | Type | Règle |
|---|---|---|
| `text` | `String` | texte de la mention tel qu'écrit, `@` compris |
| `field` | `MentionField` (`NEED`, `ANSWER`, `INSTRUCTION`) | champ d'origine |
| `start`, `end` | `int` | position dans le texte du champ |
| `matches` | `List<Mentionable>` | 0 : introuvable ; 1 : reconnue ; > 1 : ambiguë |

## `domain/knowledge/Mentions` (nouveau, fonctions pures)

- `find(String text, MentionField field, List<Mentionable> catalogue)` → `List<Mention>` :
  grammaire et plus long nom connu (research.md R1).
- `designated(need, answers, instruction, catalogue)` → documents reconnus distincts, dans l'ordre
  besoin, réponses, instruction ; les mentions ambiguës et introuvables n'y sont pas.
- `rewrite(Mentionable)` → mention sans ambiguïté à proposer (`@<chemin>`).

## `domain/port/KnowledgeIndex` (modifié)

- `List<Mentionable> mentionables(UUID projectId)` (research.md R3).

## `domain/knowledge/ProjectKnowledge.Entry` (modifié)

- `boolean designated` : désigné par l'utilisateur (rendu `designe="oui"`). Constructeurs existants
  gardés (`false`), `withDesignated()`.

## `application/knowledge/MentionQueries` (nouveau cas d'usage)

- `suggest(projectId, query)` → 8 `Suggestion(kind, name, path, insert)` au plus (R5).
- `resolve(projectId, sessionId?, List<FieldText>)` → `List<MentionState>` (R8).

`MentionState` : `text`, `field`, `state` (`TRANSMITTED`, `PARTIAL`, `NO_ROOM`, `AMBIGUOUS`,
`NOT_FOUND`, `SUPERSEDED`, `DEPRECATED`), `document` (sorte, titre, chemin) ou `null`,
`candidates` (sorte, titre, chemin, `insert`), `transmittedShare` (0 à 100, estimation).

## `application/knowledge/SelectKnowledge` (modifié)

Constantes `MAX_MENTIONED = 4`, `MAX_MENTIONED_TEXT = 12_000`, `MAX_MENTIONED_PART = 8_000`.
Étape « mentionnés » avant les ADR (research.md R6). Le calcul de la place est partagé avec
`MentionQueries.resolve` pour que l'état annoncé corresponde à la transmission.
