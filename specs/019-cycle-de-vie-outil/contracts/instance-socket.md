# Contrat : canal de l'instance (`instance.sock`)

Ce canal relie un second lancement à l'instance en marche, sur une socket Unix placée dans le
dossier de données (R2). Il n'est ouvert que par une instance qui tient `instance.lock`.

## Échange

Une connexion porte une seule demande. Chaque message est une ligne JSON en UTF-8, terminée par
`\n` et limitée à 4 Kio ; au-delà, la connexion est fermée.

Demande :

```json
{ "version": 1, "command": "open" }
```

Réponses :

```json
{ "status": "OPENED" }
{ "status": "PRINT", "url": "http://127.0.0.1:47615/?launch=…" }
{ "status": "STOPPING" }
```

- **`OPENED`** : un nouvel onglet a été ouvert, avec un nouveau jeton à usage unique. Le second
  lancement écrit `spec-writer est déjà lancé : un nouvel onglet a été ouvert.` et sort avec le
  code 0.
- **`PRINT`** : l'instance est réglée pour afficher l'adresse au lieu d'ouvrir le navigateur, ou
  l'ouverture a échoué. Le second lancement affiche l'adresse dans sa console et sort avec le
  code 0. L'adresse n'est écrite dans aucun journal.
- **`STOPPING`** : l'instance s'arrête. Le second lancement retente le verrou pendant 15 s au plus
  (R5), puis démarre.

Une commande inconnue, ou une `version` différente de 1, reçoit `{ "status": "UNSUPPORTED" }`. Le
second lancement le traite comme une instance qui ne répond pas, ce qui couvre une version plus
ancienne de l'outil encore en marche.

## Délais

- Connexion et réponse : 5 s au plus. Au-delà, l'instance ne répond pas : message (R5) et code de
  sortie 3.
- Le jeton émis expire au bout de 120 s (`ISSUED_TTL_SECONDS`, inchangé).

## Codes de sortie du lanceur

| Code | Cas |
|---|---|
| 0 | Onglet ouvert, ou adresse affichée, par l'instance en marche |
| 3 | Instance en marche qui ne répond pas, ou sans canal |
| autre | Échec du démarrage, comme aujourd'hui |
