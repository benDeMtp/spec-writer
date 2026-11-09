# Modèle de données : budget de contexte selon le modèle

## Migration `V16__model_capacities.sql`

`copilot_settings` : colonne `model_capacities TEXT NOT NULL DEFAULT '[]'` (JSON, liste de
`ModelCapacity`). Remplie à la prochaine connexion ou relecture des modèles ; vide avant : tout est
inconnu, `STANDARD` s'applique.

## `domain/settings/ModelCapacity` (nouveau, record)

| Champ | Type | Règle |
|---|---|---|
| `id` | `String` | identifiant Copilot, non vide |
| `name` | `String` | nom affiché, à défaut l'identifiant |
| `promptTokens` | `Integer` | nul si absent, ≤ 0 ou > 10 000 000 (FR-002) |
| `contextTokens` | `Integer` | même règle |
| `multiplier` | `Double` | nul si absent ou < 0 |

## `domain/knowledge/KnowledgeBudget` (nouveau, record)

| Champ | `STANDARD` |
|---|---|
| `total` | 35 000 |
| `roles` | 3 000 |
| `adrText`, `adrPart` | 12 000, 8 000 |
| `documentText`, `documentPart` | 8 000, 6 000 |
| `mentionedText`, `mentionedPart` | 12 000, 8 000 |
| `ticketText` | 3 000 |
| `architecture` | 3 000 |
| `subtaskText` | 1 500 |

`forPromptTokens(OptionalInt)` : research.md R2 (minimum `STANDARD`, total ≤ 200 000, plafonds ×
facteur). `isStandard()`.

## Modifiés

- `GitHubAuthenticator.ModelOption` : `promptTokens`, `contextTokens`, `multiplier`.
- `CopilotSettings` : `capacities` ; `connected(login, models, capacities)`.
- `ProjectKnowledge` : `budget` (total) ; ligne `budget=` dans `fingerprint()` hors `STANDARD`.
- `SelectKnowledge` : constructeur avec `ModelCapacities` ; constantes → `KnowledgeBudget`.
- `MentionQueries.MentionState` : `betterModel` (`id`, `name`, `multiplier`) ou nul.
- Port `domain/port/ModelCapacities` : `promptTokens()`, `all()`.
