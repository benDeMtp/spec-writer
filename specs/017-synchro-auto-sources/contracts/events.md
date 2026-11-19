# Flux d'événements : `GET /api/events`

Flux SSE de l'application, ouvert par chaque onglet.

## Protocole

- Protégé par le cookie de session ; 403 `SESSION_REQUIRED` sans session. L'onglet retente toutes
  les 10 s.
- Chaque événement porte `id:` (UUID), `event:` (type) et `data:` (JSON sur une ligne).
- À la reconnexion, l'en-tête `Last-Event-ID` fait renvoyer les événements suivants encore en
  mémoire (50 derniers). Un identifiant inconnu ne renvoie rien de passé.
- Un commentaire `: ping` part toutes les 25 s pour garder la connexion ouverte.

## `source-updated`

Émis une fois par source dont une synchronisation, automatique ou manuelle, s'est terminée avec au
moins un élément nouveau, modifié ou retiré. Rien n'est émis pour une synchronisation sans
changement ou en échec.

```text
id: 3d1e…
event: source-updated
data: {"id":"3d1e…","projectId":"…","projectName":"Remboursements","sourceKey":"7f0c…","sourceKind":"DOCUMENTATION","sourceLabel":"Confluence : Remboursements","added":1,"modified":2,"removed":0,"at":"2026-11-18T16:40:03Z"}
```

`sourceKind` prend l'une des valeurs `JIRA`, `DOCUMENTATION` ou `ARCHITECTURE`. Le libellé de la
source dit déjà sa sous-sorte (« Confluence : … », chemin du dossier, adresse du site).
L'identifiant est dérivé du projet, de la source et de l'instant du bilan.

L'événement ne contient jamais de titre ni de contenu d'élément, ni jeton.

## Notification affichée par le front

Conditions d'affichage :
- le réglage `sourceUpdates` est vrai ;
- `Notification.permission === "granted"` ;
- l'onglet détient le verrou `spec-writer-notifications`.

| Champ | Valeur |
|---|---|
| `title` | « {Sorte} « {sourceLabel} » mise à jour », ex. « Documentation « Confluence : Remboursements » mise à jour » ; pour Jira, « Jira FACT mise à jour » |
| `body` | « {projectName} : 1 nouveau, 2 modifiés » (les zéros sont omis) |
| `tag` | `id` de l'événement |
| clic | focus de l'onglet, projet courant = `projectId` si besoin, navigation `/projet/connaissance` |
