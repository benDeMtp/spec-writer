# Revue d'ergonomie : navigation par projet (012, T045)

Revue des écrans de la feature avec les Web Interface Guidelines (Vercel), le 2026-11-11. Fichiers
relus : menu (`main-nav`), annonce de bascule, écran de session perdue, Réglages et Connaissance du
projet, sélecteur, formulaire d'identité, Configuration, Tickets Jira, titre des pages du projet.

## Constats corrigés

- [X] Bouton « Menu » sans état au survol : fond teinté au survol.
- [X] Clé Jira du menu traduite par la traduction automatique du navigateur : `translate="no"`.
- [X] Titre des pages du projet : `text-wrap: balance`, et un nom de projet long se coupe
  (`overflow-wrap: anywhere`).
- [X] Formulaire d'identité : `autocomplete="off"` sur le nom et la description, `autocomplete="off"`
  et `spellcheck="false"` sur la clé Jira.
- [X] Réglages du projet : `autocomplete="off"` et `spellcheck="false"` sur l'adresse d'une source
  et le chemin du `workspace.dsl`.
- [X] Réglages du projet : une saisie non enregistrée est aussi signalée au rechargement ou à la
  fermeture de l'onglet (`beforeunload`), en plus de la garde de route et du sélecteur.

## Constats de la mise en œuvre (E2E)

- [X] Un `role="status"` permanent dans le cadre doublait le statut propre à chaque page : l'annonce
  de bascule est un paragraphe visible, transmis aux lecteurs d'écran par `LiveAnnouncer`.
- [X] « Modifier » sur la page Projets avait un nom accessible sans son texte visible (axe,
  `label-content-name-mismatch`) : « Modifier le projet <nom> ».
- [X] Après « Déplacer vers… », le menu et la page montraient deux projets : le projet de
  destination devient courant.
- [X] Une page ouverte directement pouvait annoncer une bascule à tort, avant la lecture du projet
  courant : le projet courant est lu avant la comparaison.

## Sans suite

- Majuscules de titre (« Title Case ») : règle de l'anglais ; l'interface est en français.
- Formats de date : `DatePipe` d'Angular, déjà localisé.

## Hors périmètre, à reprendre

- 011 : quand une proposition échoue très vite (sortie invalide du modèle), le panneau de
  discussion affiche « Le suivi de la proposition a été interrompu » au lieu du message d'erreur :
  l'événement d'erreur arrive avant l'abonnement au flux.
