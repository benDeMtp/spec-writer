# Contrat de sortie du modèle : ancres, points ouverts, `fromCode`

Ajouts au schéma de sortie de la spécification (`LlmSchemas`, `DraftSpecification`). Tous
facultatifs : une sortie sans eux reste valide (versions et rejeux existants).

```json
{
  "assumptions": [{
    "statement": "…", "origin": "TOOL_INFERENCE", "questionId": "",
    "anchor": { "target": "USE_CASE", "index": 1, "step": 4, "alternative": false },
    "fromCode": "H-03"
  }],
  "sourceDeviations": [{ "…": "…", "anchor": { "target": "USE_CASE", "index": 0, "step": null }, "fromCode": "EC-01" }],
  "openQuestions": [{
    "question": "Qui valide quand le manager est absent ?",
    "owner": "", "anchor": { "target": "USE_CASE", "index": 1, "step": 3 }, "fromCode": ""
  }]
}
```

- **Forme retenue à l'implémentation** : `anchor` est une chaîne courte, et non un objet. Le schéma
  strict de Copilot rend toutes les propriétés obligatoires, et sa grammaire compilée a déjà atteint
  sa limite en 016 ; un objet de quatre champs sur trois listes la grossissait d'autant. Formes :
  `SUMMARY`, `USE_CASE:1`, `USE_CASE:1:4` (étape 4), `USE_CASE:1:A2` (alternative 2), `SCENARIO:0`,
  `REQUIREMENT:2`, ou chaîne vide. L'exemple objet ci-dessus garde la même sémantique.
- `target` : `USE_CASE`, `SCENARIO`, `REQUIREMENT` (position dans `requirements`, toutes
  catégories), `SUMMARY` ; `index` : position (à partir de 0) dans la liste de **cette sortie**.
- `step` : à partir de 1, dans `nominalFlow`, ou dans `alternatives` si `alternative`.
- `fromCode` : seulement en révision, code de l'élément repris de la version de base ; vide pour
  un élément nouveau.
- `openQuestions` : seulement en révision ; une question que les décisions de séance laissent en
  suspens. Les questions reportées en séance sont ajoutées par le code : le modèle ne les répète
  pas.

| Consigne | Ancre | `fromCode` | `openQuestions` |
|---|---|---|---|
| `generate-specification` | oui | non | non |
| `revise-specification` | oui | oui | oui |
| `edit-specification` | oui | non (schéma de la génération, voir tasks.md T054) | non |
| `structure-imported-text` | oui | non | non |
| `merge-conflict` | oui | non | non |

Traitement par le code (aucune violation bloquante) : ancre hors limites retirée ; `fromCode`
inconnu de la base ignoré ; puis `InterviewOutcome` (research.md R3) pour une révision de séance.

Message de séance transmis dans la discussion (rédigé par le code, contenu utilisateur séparé des
consignes comme tout message de discussion) :

```
Séance du 20/11/2026 avec Julie M., Paul D. Décisions à intégrer :
- H-03 (UC-02, étape 4) — confirmé : plafonné au montant TTC facturé.
- UC-02, étape 3 — à modifier : validation par le manager au-delà de 500 €.
- SC-02-01 — à retirer.
Questions reportées (ajoutées comme points ouverts, ne pas les reformuler) :
- UC-02, étape 3 — Qui valide quand le manager est absent ? (Paul D.)
```
