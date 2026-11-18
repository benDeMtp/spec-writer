# Contrat des données transmises au modèle (016)

La provenance d'un extrait est une balise `<provenance …/>` sur sa propre ligne, juste après la
balise ouvrante de l'extrait : les balises ouvrantes restent celles d'avant 016, ce qui garde le
golden set inchangé (SC-006). Ses attributs sont des données (valeurs échappées par
`UserMessages.attribute`), jamais des consignes ; un texte d'extrait ne peut pas s'en donner une
autre (`<` échappé). Un extrait sans provenance (module, spécification discutée) n'a pas cette
ligne. Valeurs :

- `source` : `jira`, `antora`, `sharepoint`, `confluence`, `architecture`, `specification`,
  `fiche-generale`, `document-joint` ;
- `maj` : date `AAAA-MM-JJ` de la dernière mise à jour à la source, ou `inconnue` ;
- `fiabilite` : `reference`, `interpretation`, `a-confirmer` ; absent pour la fiche générale et les
  documents joints.

## Résultats de `search_knowledge` (converse-agent, revise-specification)

```xml
<sources>
<source n="3" sorte="DOC_SECTION" ref="https://wiki/pages/viewpage.action?pageId=42#regles" titre="Règles de remboursement">
<provenance source="confluence" maj="2026-10-03" fiabilite="interpretation"/>
…
</source>
</sources>
```

## Connaissance d'une génération (generate, edit, merge, structure, propose-questions)

```xml
<document ref="docs:…/Demande remboursement.docx" titre="Demande remboursement">
<provenance source="sharepoint" maj="2026-09-12" fiabilite="reference"/>
…
</document>
<document ref="https://wiki/pages/viewpage.action?pageId=42" titre="Notes de frais">
<provenance source="confluence" maj="inconnue" fiabilite="a-confirmer"/>
…
</document>
<ticket ref="FACT-7" statut="Ouvert" ferme="non">
<provenance source="jira" maj="2026-11-02" fiabilite="interpretation"/>
…
</ticket>
```

Une page Confluence retenue pour une génération est recomposée en document entier, comme un
document SharePoint. Le vocabulaire et la vue d'architecture n'ont pas de ligne de provenance.

## Sortie : écarts entre sources

Le schéma `specification` gagne `sourceDeviations` (toutes les propriétés requises, liste vide
sans écart). Le schéma `revision` ne l'a pas : avec lui, sa grammaire compilée dépasse la limite
de Copilot (« compiled grammar is too large », vu à l'évaluation réelle) ; une révision reprend
les écarts de la version discutée (`ProposeRevision.withBaseDeviations`).

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
