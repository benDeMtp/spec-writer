# Contrat : budget de contexte selon le modèle

## GET /api/settings/copilot (vue existante, enrichie)

```json
{ "state": "CONNECTED", "model": "claude-sonnet-5-5", "availableModels": ["…"],
  "models": [ { "id": "claude-sonnet-5-5", "name": "Claude Sonnet 5.5", "promptTokens": 128000,
                "contextTokens": 200000, "multiplier": 1.0, "knowledgeChars": 162000 },
              { "id": "gpt-5-mini", "name": "GPT-5 mini", "promptTokens": null, "contextTokens": null,
                "multiplier": 0.0, "knowledgeChars": 35000 } ],
  "knowledgeChars": 162000 }
```

`knowledgeChars` : total de la connaissance transmise avec ce modèle (`KnowledgeBudget.total`).

## POST /api/projects/{id}/mentions/resolve (007, enrichi)

Chaque état gagne `betterModel` : `{ "id", "name", "multiplier" }` ou `null` (research.md R5).

## Front

- `copilot-card.component.ts` : option du choix du modèle avec capacité et coefficient ; ligne
  « Connaissance transmise : jusqu'à N caractères ».
- `mention-status.component.ts` : « transmis en entier avec <nom> (×coefficient) » quand
  `betterModel` est présent.
