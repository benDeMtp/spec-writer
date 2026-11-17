# Contrat : format des échanges rejoués (`e2e/fixtures/llm/*.json`)

Étend le format actuel de `ReplayChatModel` ; les fichiers existants restent valides.

```json
{
  "operation": "converse-agent",
  "userContains": ["parking", "<question>"],
  "afterTools": 1,
  "toolCalls": [
    { "name": "search_knowledge", "arguments": { "query": "parking réservation", "kinds": ["TICKET"] } }
  ],
  "reasoning": "…facultatif…"
}
```

| Champ | Obligatoire | Sens |
|---|---|---|
| `operation` | oui | Opération lue dans le marqueur `[operation:<nom>]` du message système. |
| `userContains` | oui | Fragments qui doivent tous figurer dans le message utilisateur (résultats d'outils compris). |
| `afterTools` | non | Nombre de `<resultat>` déjà présents dans la requête. Absent : toute étape. |
| `response` | un des trois | JSON d'une opération structurée (inchangé). |
| `text` | un des trois | Texte libre ; rejoué en morceaux par `ReplayStreamingChatModel`. |
| `toolCalls` | un des trois | Réponse = appels d'outils (`name`, `arguments`). |
| `reasoning` | non | Réflexion rejouée avant la réponse (inchangé). |

**Appariement** : première entrée, dans l'ordre des noms de fichiers, dont l'opération,
`afterTools` et tous les fragments correspondent. Les substitutions existantes (`__THEME__`,
`__DISCUSSION_n__`, `__MODULE:…__`) s'appliquent aussi aux `arguments` et au `text`.

**Échec** : `DomainException(LLM_UNAVAILABLE)` dont le message donne l'opération, l'étape
(`afterTools` constaté) et, pour la meilleure entrée candidate, les fragments manquants. Le
journal E2E le montre ; aucun contenu utilisateur n'est écrit au-delà des fragments attendus.

**Réenregistrement** : procédure dans `e2e/fixtures/llm/README.md` (dev mode, profil `live`,
journal de débogage de l'adaptateur limité aux appels d'outils et aux réponses du scénario de
test, jamais activé hors du poste du mainteneur).
