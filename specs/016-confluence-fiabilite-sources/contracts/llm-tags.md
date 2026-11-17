# Contrat des données transmises au modèle (016)

Les attributs ajoutés sont des données (valeurs d'attribut échappées par `UserMessages.attribute`),
jamais des consignes. Valeurs :

- `source` : `jira`, `antora`, `sharepoint`, `confluence`, `architecture`, `specification`,
  `fiche-generale`, `document-joint` ;
- `maj` : date `AAAA-MM-JJ` de la dernière mise à jour à la source, ou `inconnue` ;
- `fiabilite` : `reference`, `interpretation`, `a-confirmer` ; absent pour la fiche générale et les
  documents joints.

## Résultats de `search_knowledge` (converse-agent, revise-specification)

```xml
<sources>
<source n="3" sorte="DOC_SECTION" ref="https://wiki/pages/viewpage.action?pageId=42#regles"
        titre="Règles de remboursement" source="confluence" maj="2026-10-03" fiabilite="interpretation">
…
</source>
</sources>
```

## Connaissance d'une génération (generate, edit, merge, structure, propose-questions)

```xml
<document ref="docs:…/Demande remboursement.docx#2" titre="Plafond" source="sharepoint"
          maj="2026-09-12" fiabilite="reference">…</document>
<ticket ref="FACT-7" statut="Ouvert" ferme="non" source="jira" maj="2026-11-02" fiabilite="interpretation">…</ticket>
<section ref="https://wiki/…" source="confluence" maj="inconnue" fiabilite="a-confirmer">…</section>
```

## Sortie : écarts entre sources

Les schémas `specification` et `revision` gagnent `sourceDeviations` (toutes les propriétés
requises, liste vide sans écart) :

```json
"sourceDeviations": [
  {"referenceRef": "docs:…/Demande remboursement.docx#2",
   "interpretationRef": "https://wiki/pages/viewpage.action?pageId=42#regles",
   "difference": "La demande plafonne à 500 €, la page Confluence à 1 000 €."}
]
```

Un écart dont l'une des deux références n'est pas dans la connaissance transmise est écarté par le
code (comme un ADR non cité). Une réponse sans `sourceDeviations` (fixtures antérieures) reste
lisible.

## Consigne réutilisable

`prompts/skills/fiabilite-sources.md`, déclarée par `[skill:fiabilite-sources]` dans les consignes
qui reçoivent de la connaissance (R8). Règles : même source → le plus récent prime ; interprétation
ou source à confirmer contre référence → la référence l'emporte, écart signalé avec les deux
extraits ; deux références → aucune ne l'emporte, écart signalé ; source à confirmer → le dire ;
`maj="inconnue"` n'est ni ancien ni récent.
