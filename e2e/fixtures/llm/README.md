# Échanges rejoués avec le modèle

Les tests de bout en bout (et le dev mode) ne parlent jamais à Copilot : chaque appel au modèle
reçoit la réponse d'un fichier de ce dossier (`ReplayChatModel`, `ReplayStreamingChatModel`).
Le format complet est décrit dans
[`specs/014-couche-llm-quarkus/contracts/replay-format.md`](../../../specs/014-couche-llm-quarkus/contracts/replay-format.md).

## Format

```json
{
  "operation": "converse-agent",
  "userContains": ["E2E-AGENT", "<question>"],
  "afterTools": 1,
  "text": "Les avoirs passent par le bus d'événements [1].",
  "reasoning": "facultatif : réflexion rejouée avant la réponse"
}
```

| Champ | Sens |
|---|---|
| `operation` | Opération lue dans le marqueur `[operation:<nom>]` de la consigne. |
| `userContains` | Fragments qui doivent tous figurer dans le message utilisateur, résultats d'outils compris. |
| `afterTools` | Facultatif : nombre de résultats d'outils (`<resultat …>`) déjà reçus. Absent : toute étape. |
| `response` | JSON d'une opération structurée. |
| `text` | Texte libre, rejoué en morceaux pour une réponse en flux. |
| `toolCalls` | Appels d'outils : `[{"name": "search_knowledge", "arguments": {"query": "…", "kinds": []}}]`. |

Exactement un des champs `response`, `text` ou `toolCalls` est présent. Une entrée invalide fait
échouer le démarrage, avec le nom du fichier.

## Appariement

La première entrée, **dans l'ordre des noms de fichiers**, dont l'opération, l'étape
(`afterTools`) et tous les fragments correspondent est rendue. Mettez donc les entrées les plus
précises avant les plus générales (préfixe numérique).

Les identifiants aléatoires se désignent par des substitutions, dans `response`, `text` et les
`arguments` :
- `"__GOAL__"` (un thème de question) : l'identifiant de la question de ce thème ;
- `"__DISCUSSION_n__"` : la référence du n-ième message de la discussion (011) ;
- `"__MODULE:nom__"` : l'identifiant du module de ce nom (013).

Un tour de l'agent (`converse-agent`) se rejoue en plusieurs entrées, une par appel au modèle :
`afterTools: 0` avec les `toolCalls` de la première étape, `afterTools: 1` avec ceux de la
suivante, et ainsi de suite, puis le `text` de la réponse. Les résultats des outils sont dans le
message utilisateur : un fragment de `userContains` peut viser une source trouvée (`FACT-7`).

Quand aucune entrée ne correspond, l'erreur `LLM_UNAVAILABLE` donne l'opération, l'étape et, pour
l'entrée la plus proche, les fragments manquants. Le journal du jar E2E
(`e2e/.state/…/spec-writer.log`) la montre.

## Réenregistrer un échange

1. Lancer le dev mode sur le vrai Copilot (profil `live`, réservé au poste du mainteneur) :
   `quarkus_start` avec `-Dquarkus.profile=dev,live`.
2. Activer le journal de débogage de l'adaptateur pour la seule durée du scénario :
   `quarkus.log.category."fr.kawamind.infrastructure.llm".level=DEBUG`.
3. Jouer le scénario dans le front, avec les données de démonstration du test (jamais des données
   réelles).
4. Pour chaque appel journalisé (`LLM <opération> : …, outils : …`), écrire l'entrée : l'opération,
   un ou deux fragments distinctifs du test (`E2E-…`), l'étape, et la réponse ou les appels
   d'outils observés.
5. Remettre le niveau de journal par défaut, puis passer `npm --prefix e2e test`.

**À ne jamais copier dans une fixture** : un contenu réel de projet (tickets, documents, code), un
nom de personne, un jeton, une adresse interne. Les fixtures sont publiques : elles ne contiennent
que les données de démonstration des tests.
