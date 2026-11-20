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
- **Texte** : dans l'onglet qui a quitté, selon `os` et `installation`, lus avant l'arrêt. Les
  textes par système sont dans le module de Quitter, chargé à la demande (budget du bundle
  initial) :
  - macOS, installeur : « Pour le relancer, cliquez sur son icône dans le Dock ou ouvrez-le depuis
    le Launchpad. » ;
  - Windows, installeur : « Pour le relancer, ouvrez-le depuis le menu Démarrer. » ;
  - Linux, installeur : « Pour le relancer, ouvrez-le depuis le menu des applications. » ;
  - sources : « Pour le relancer, lancez `dist/scripts/spec-writer.sh` (ou `.cmd` sous Windows). »
  - **autres onglets**, qui découvrent l'arrêt sans l'avoir demandé : « Pour le relancer,
    ouvrez-le depuis son raccourci. »
- **Fin de l'écran** : la phrase « Vous pouvez fermer cet onglet. »
- **Annonce** : le titre reçoit le focus, ce qui le fait lire par le lecteur d'écran.

## Détection dans les autres onglets

Un onglet passe à l'écran d'arrêt dans deux cas :
- erreur du flux `/api/events` ;
- erreur de statut 0 sur une requête.

Dans les deux cas, une sonde `GET /api/application` a aussi échoué pour une raison réseau. Toute
réponse HTTP, même `403` (session refusée), laisse l'onglet tel quel.
