# Data Model: ADR du projet

Aucune table ni migration : les ADR sont les lignes `knowledge_item` de sorte `ADR` (002), un ou
plusieurs morceaux par référence.

## AdrNumber (domaine, nouveau)

Numéro d'un ADR, extrait de sa référence puis de son titre.

| Règle | Exemple → valeur |
|---|---|
| `adr[-_ ]?0*(\d+)` (référence ou titre) | `adr-005.html` → 5, « ADR-12 : … » → 12 |
| fichier `0*(\d+)[-_]…` (dernier segment de la référence) | `0005-trois-domaines.html` → 5 |
| titre `^0*(\d+)\.\s` | « 5. Trois domaines » → 5 |
| aucun | vide |

Comparaison par valeur entière. Utilisé par `ListAdrs` et `SelectKnowledge` (succession).

## AdrSummary (application, nouveau)

Une ligne de la page des ADR.

| Champ | Type | Règle |
|---|---|---|
| `number` | entier ou vide | `AdrNumber` |
| `title` | texte ≤ 500 | titre du morceau 0 |
| `status` | `ACCEPTED`, `PROPOSED`, `SUPERSEDED`, `DEPRECATED`, `UNKNOWN` | statut indexé ; absent → `UNKNOWN` |
| `supersededBy` | texte ou vide | successeur cité (« ADR-5 ») |
| `successorFound` | booléen | le numéro du successeur existe parmi les ADR du projet |
| `duplicate` | booléen | un autre ADR a la même valeur de numéro |
| `sentToAi` | booléen | faux si `SUPERSEDED` ou `DEPRECATED` |
| `url` | texte ou vide | R3 : adresse de la page (URL), ou `/api/projects/{id}/doc-files/{ref}` (dossier), vide si introuvable |

## AdrList (application, nouveau)

| Champ | Règle |
|---|---|
| `adrs` | `AdrSummary` triés : numéro croissant, puis titre ; sans numéro à la fin |
| `byStatus` | nombre d'ADR par statut, sur tous les ADR |
| `indexing` | vrai si une indexation du projet est en cours |

## Compteurs (KnowledgeStatus de la 002)

`docSections` et `adrs` comptent les éléments (morceau 0), plus les morceaux (R1). `tickets` et
`architectureElements` ne changent pas (jamais découpés).

## ProjectKnowledge.Entry (002)

Pour un ADR : `chunk = 0`, `text` = texte entier (morceaux joints), borné à 4 000 et marqué
« [ADR tronqué] » au besoin (R4).
