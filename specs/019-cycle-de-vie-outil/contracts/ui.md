# Contrat d'interface : Quitter et écran d'arrêt

## Menu

- **Commande** : dans le bloc Application de `main-nav`, après « Visite guidée », un bouton
  « Quitter spec-writer ». Il est accessible au clavier et se trouve dans l'ordre de tabulation
  du menu.

## Confirmation

La confirmation passe par `ConfirmDialogComponent`.

- **Sans travail en cours** :
  - titre : « Quitter spec-writer ? » ;
  - texte : « L'outil s'arrête. Vos données sont gardées ; relancez-le depuis <geste> pour y
    revenir. » ;
  - boutons : « Quitter » et « Annuler ».
- **Avec des travaux en cours**, même titre :
  - texte : la liste des travaux (« Indexation du projet Aurora », « Réponse de l'agent dans
    Facturation des avoirs »…) suivie de ce qu'ils deviennent :
    - indexation : « s'arrête, à relancer depuis Connaissance du projet » ;
    - réponse ou proposition : « le texte déjà reçu est gardé, marqué incomplet » ;
    - génération : « est perdue, à relancer » ;
  - boutons : « Quitter quand même » et « Annuler ».
- **Clavier** : le focus initial est sur « Annuler », et Échap annule.

## Écran d'arrêt (`sw-app-stopped`)

Il remplace tout le contenu, menu compris, comme `sw-session-lost`.

- **Titre** (`h1`, qui reçoit le focus) : « spec-writer est arrêté ».
- **Texte**, selon `os` et `installation`, lus avant l'arrêt et gardés en mémoire :
  - macOS, installeur : « Pour le relancer, cliquez sur son icône dans le Dock ou ouvrez-le depuis
    le Launchpad. » ;
  - Windows, installeur : « Pour le relancer, ouvrez-le depuis le menu Démarrer. » ;
  - Linux, installeur : « Pour le relancer, ouvrez-le depuis le menu des applications. » ;
  - sources : « Pour le relancer, lancez `dist/scripts/spec-writer.sh` (ou `.cmd` sous Windows). »
  - **inconnu** (onglet ouvert après l'arrêt, sans état lu) : « Pour le relancer, ouvrez-le depuis
    son raccourci. »
- **Fin de l'écran** : la phrase « Vous pouvez fermer cet onglet. »
- **Annonce** : le changement est annoncé par `LiveAnnouncer`.

## Détection dans les autres onglets

Un onglet passe à l'écran d'arrêt dans deux cas :
- erreur du flux `/api/events` ;
- erreur de statut 0 sur une requête.

Dans les deux cas, une sonde `GET /api/application` a aussi échoué pour une raison réseau. Si
elle répond `401`, c'est l'écran de session perdue qui s'affiche, comme aujourd'hui.
