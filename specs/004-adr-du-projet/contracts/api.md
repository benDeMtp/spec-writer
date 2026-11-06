# Contrat : API de la page des ADR (delta de `002/contracts/openapi.yaml`)

## `GET /api/projects/{id}/knowledge/adrs`

ADR du projet, pour la page des ADR. 404 `PROJECT_NOT_FOUND` si le projet n'existe pas ; liste vide
si aucun ADR.

```yaml
AdrList:
  type: object
  properties:
    adrs:
      type: array
      items:
        type: object
        properties:
          number: { type: integer, nullable: true }
          title: { type: string }
          status: { enum: [ACCEPTED, PROPOSED, SUPERSEDED, DEPRECATED, UNKNOWN] }
          supersededBy: { type: string, nullable: true, description: "Successeur cité, « ADR-5 »" }
          successorFound: { type: boolean }
          duplicate: { type: boolean, description: "Un autre ADR porte la même valeur de numéro" }
          sentToAi: { type: boolean, description: "Faux si remplacé ou obsolète" }
          url: { type: string, nullable: true, description: "Page de l'ADR : adresse du site, ou /api/projects/{id}/doc-files/{ref}" }
    byStatus:
      type: object
      additionalProperties: { type: integer }
      description: Nombre d'ADR par statut, sur tous les ADR
    indexing: { type: boolean }
```

## `GET /api/projects/{id}/doc-files/{path}`

Fichier d'une source de documentation **dossier** du projet (research.md R3). `path` est le chemin
relatif au dossier déclaré, tel qu'il est indexé.

| Cas | Réponse |
|---|---|
| fichier sous un dossier déclaré du projet, extension permise | 200, type MIME de l'extension |
| chemin qui sort du dossier (`..`, absolu, lien symbolique sortant), extension refusée, absent, dossier | 404, sans détail |
| projet inconnu | 404 `PROJECT_NOT_FOUND` |

Extensions permises : `html`, `htm`, `css`, `png`, `jpg`, `jpeg`, `gif`, `svg`, `webp`, `woff`,
`woff2`.

En-têtes de toute réponse 200 :
`Content-Security-Policy: sandbox allow-same-origin; script-src 'none'; object-src 'none'; default-src 'self' 'unsafe-inline'`,
`X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`.

Authentification : celle de l'API (cookie de session). Journal : refus seulement, sans chemin.

## `GET /api/projects/{id}/knowledge` (002)

Inchangé dans sa forme ; `adrs` et `docSections` comptent les éléments, plus les morceaux.

## Front

- Route `projets/:id/contexte/adr`, titre « ADR · spec-writer ».
- Dans le panneau de connaissance, « N ADR » est un lien vers cette route si N ≥ 1.
- Lien de titre : `target="_blank"`, `rel="noopener noreferrer"`, nom accessible terminé par
  « (nouvel onglet) ».
- Statut : badge texte (jamais la seule couleur) ; ligne grisée + « non transmis à l'IA » ;
  « numéro en double » ; successeur « introuvable ».
- Projet sans ADR : « Aucun ADR n'a été trouvé dans la documentation de ce projet », lien de retour.
