# Contrat : mention d'un document avec @

Complète `specs/002-projets-contexte-mcp/contracts/openapi.yaml`.

## GET /api/projects/{id}/mentions?q=<saisie>

`q` : 1 à 200 caractères, la saisie après `@`. 200 :

```json
[
  { "kind": "DOCUMENT", "name": "Aurora_Matrice_Regles_Gestion_Activites_1",
    "path": "10. MVP/Documentation DC/Aurora_Matrice_Regles_Gestion_Activites_1.xlsx",
    "insert": "@Aurora_Matrice_Regles_Gestion_Activites_1" },
  { "kind": "ADR", "name": "12. Sobriété d'usage de l'IA", "path": "ADR-12", "insert": "@ADR-12" }
]
```

8 éléments au plus ; liste vide si rien ne correspond. 404 : projet inconnu. Aucun appel externe,
aucun fichier lu.

## POST /api/projects/{id}/mentions/resolve

```json
{ "sessionId": "…(facultatif)",
  "fields": [ { "field": "ANSWER", "text": "les informations sont dans @Aurora_Matrice_Regles_Gestion_Activites_1" } ] }
```

200 :

```json
[ { "text": "@Aurora_Matrice_Regles_Gestion_Activites_1", "field": "ANSWER", "state": "PARTIAL",
    "document": { "kind": "DOCUMENT", "title": "Aurora_Matrice_Regles_Gestion_Activites_1",
                  "path": "10. MVP/Documentation DC/Aurora_Matrice_Regles_Gestion_Activites_1.xlsx" },
    "candidates": [], "transmittedShare": 21 } ]
```

`state` : `TRANSMITTED`, `PARTIAL`, `NO_ROOM`, `AMBIGUOUS` (avec `candidates[]` : `kind`, `title`,
`path`, `insert`), `NOT_FOUND`, `SUPERSEDED` (ADR remplacé : `document` est son successeur),
`DEPRECATED`. Avec `sessionId`, le besoin et les réponses enregistrés de la session comptent dans
l'ordre de priorité et dans la place ; les `fields` envoyés remplacent le champ correspondant en
cours de saisie. 422 : plus de 40 champs ou texte de plus de 20 000 caractères.

## Prompts

Balises `<document … designe="oui">` et `<adr … designe="oui">` ; une page Antora désignée est
rendue en `<document ref titre designe="oui">`.

## Front

- Directive `swMention` sur les zones de texte du besoin, des réponses et de l'instruction.
- Composant `sw-mention-status` sous chaque champ : une ligne par mention, état en clair, bouton
  « Utiliser <chemin> » par candidat d'une mention ambiguë (réécrit la mention, research.md R2).
