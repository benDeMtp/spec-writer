# Revue d'ergonomie et d'accessibilité : périmètre des modules touchés (T051)

**Date** : 2026-11-11
**Référentiel** : Web Interface Guidelines (vercel-labs, `command.md`), WCAG 2.2 AA.
**Fichiers** : `source-roots.component.ts`, `perimeter-step.component.ts`, `need-draft.component.ts`
(ajout de la 013 seulement).

## Écarts corrigés

- [x] **Bloquant.** `perimeter-step.component.ts` : les couleurs utilisaient des variables
  absentes du thème (`--sw-accent`, `--sw-surface-alt`, `--sw-border`, `--sw-muted`), donc leurs
  valeurs de repli claires. En thème sombre, le fond restait presque blanc sous un texte clair :
  contraste insuffisant. Remplacées par `--sw-sheet`, `--sw-pencil`, `--sw-rule` et
  `--sw-ink-soft`, qui suivent `light-dark()`.
- [x] **Sérieux.** `source-roots.component.ts` : toute la zone d'aperçu était `role="status"`. Un
  lecteur d'écran lisait jusqu'à 500 noms de modules à chaque aperçu. Seul le bilan
  (« 14 modules détectés · … ») est maintenant annoncé ; la liste reste consultable au clavier.
- [x] Chemins et noms longs : `overflow-wrap: anywhere` sur le chemin d'une racine, et sur le nom, le
  chemin et l'emplacement des occurrences d'un module, pour ne pas élargir le tableau.
- [x] Identifiants protégés de la traduction automatique du navigateur (`translate="no"`) :
  termes, noms et chemins de modules, extraits de code, libellé et chemin d'une racine.
- [x] Focus d'une ligne de module (cible d'une citation `[n]`) : contour au thème, décalé de 2 px.

## Conformes

- Tableaux réels (`<table>`, `<caption>`, `th scope="col"`), groupes en `<section>` titrées par un
  `h3` qui donne le nombre de modules ; la qualification est écrite en texte.
- Chaîne de dépendance en liste ordonnée nommée ; occurrences en `<details>` dépliable au clavier.
- Fin du recensement et de la qualification annoncées une fois chacune (`LiveAnnouncer`).
- Champs étiquetés, `autocomplete="off"` et `spellcheck="false"` sur le chemin ; liste des modules
  défilante atteignable au clavier (`tabindex="0"`, nom accessible).
- Suppression d'une racine après une confirmation nommée.
- Nombres au format français (`toLocaleString('fr-FR')`) ; points de suspension `…`.
- Aucune violation axe sur les écrans parcourus par `us16-perimetre.spec.ts`.

## Écarts non bloquants, laissés en l'état

- Une analyse peut afficher jusqu'à 2 000 modules sans virtualisation. Les tableaux restent du HTML
  simple ; à revoir si un vrai arbre dépasse quelques centaines de modules trouvés.
- « Détecter les modules » et « Ajouter la racine » sont désactivés tant que le chemin est vide,
  comme les autres formulaires des réglages du projet.
- Le champ « Chemin du dossier » n'a pas d'exemple en texte indicatif ; l'aide des motifs en donne.
