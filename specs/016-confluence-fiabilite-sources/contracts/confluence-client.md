# Appels à Confluence Server / Data Center (016)

Lecture seule. En-tête `Authorization: Bearer <jeton>` ; `Accept: application/json`. Proxy
d'entreprise (`ProxySettings`), certificat interne et HTTP explicite comme Jira ; 8 requêtes par
seconde au plus ; `429` et `503` : attente selon `Retry-After`, comme `RateLimitHandler`.

| Usage | Requête | Lu |
|---|---|---|
| Test de connexion | `GET /rest/api/user/current` | `displayName`, `username` ; `401` → `UNAUTHORIZED` |
| Version du serveur | `GET /rest/api/settings/systemInfo` (facultatif, ignoré si refusé) | `version` |
| Page désignée par titre | `GET /rest/api/content?spaceKey=K&title=T&type=page` | `results[0].id` |
| Racine | `GET /rest/api/content/{id}?expand=version,space` | `id`, `title`, `type == page`, `version.number`, `version.when` |
| Descendance (paginée) | `GET /rest/api/content/search?cql=ancestor%3D{id}%20and%20type%3Dpage&expand=version&start=S&limit=100` | `results[].id/title/version`, `totalSize`, `size` |
| Contenu d'une page | `GET /rest/api/content/{id}?expand=body.view,version` | `body.view.value` (HTML rendu), `version` |

Formes d'adresse d'une racine acceptées (même hôte que le serveur, chemin de contexte compris) :

- `…/pages/viewpage.action?pageId=123`
- `…/spaces/KEY/pages/123/Titre`
- `…/display/KEY/Titre+de+la+page`

Référence indexée d'une page : `<serveur>/pages/viewpage.action?pageId=<id>`, plus `#<ancre>` par
section. Erreurs : `403` sur une page descendante → page ignorée et comptée (`ACCESS_DENIED`) ;
`404` sur la racine → racine en erreur (`NOT_FOUND`) ; injoignable → `UNREACHABLE`, pages gardées.
