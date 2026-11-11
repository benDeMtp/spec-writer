# Revue d'ergonomie : discussion d'une spécification (011)

**Date** : 2026-11-11
**Écrans** : `discussion-panel.component.ts`, `proposal-review.component.ts`, `specification.page.ts`
(colonne de discussion), `versions-panel.component.ts`, `conversation-list.component.ts`,
`confirm-dialog.component.ts`.
**Référence** : Web Interface Guidelines (Vercel), WCAG 2.2 AA, constitution (accessibilité).

## Points vérifiés

- [x] Le panneau est une `section` titrée (`aria-labelledby`), le bouton qui l'ouvre porte
  `aria-expanded`.
- [x] Focus à l'ouverture dans la saisie ; « Fermer » le rend au bouton « Discuter avec l'IA ».
- [x] Proposition prête : le focus va au titre de la relecture (`tabindex="-1"`).
- [x] Appliquer : le focus va au titre de la nouvelle version (corrigé pendant la revue, il était
  perdu au rechargement).
- [x] Abandonner : le focus revient à la saisie de la discussion (corrigé pendant la revue, il
  disparaissait avec la relecture).
- [x] « Proposer une nouvelle version » indisponible : la raison est écrite sous le bouton et liée
  par `aria-describedby` (discussion vide, réponse en cours, relecture en attente).
- [x] Annonces polies (`aria-live`) : discussion ouverte, rédaction commencée, proposition prête,
  aucun changement, version suivie.
- [x] Erreurs en `role="alert"` (rédaction en échec, ouverture impossible) ; avertissements en
  `role="note"` (version ancienne affichée, aucun changement établi).
- [x] La citation d'une section fait défiler le document et donne le focus à la section, sans
  changer de page.
- [x] Disposition : deux colonnes, le panneau sous le document au-dessous de 64 rem ; la relecture
  reste dans la colonne du document, où le diff a la place.
- [x] Confirmation de suppression : la case « Supprimer aussi ses discussions » est un vrai
  `checkbox` étiqueté, cochée par défaut, montrée seulement s'il y a des discussions.
- [x] Liste des conversations : la mention « Spécification : <nom> » est du texte, pas seulement
  une couleur ; « (spécification supprimée) » est écrit.
- [x] Pas d'animation ajoutée ; les couleurs reprennent les jetons `--sw-*` existants.
- [x] axe sans violation sur la spécification avec la discussion ouverte, la relecture et la
  confirmation (E2E `us14-discussion`).

## Seconde revue (Web Interface Guidelines), corrigée

- [x] « Fermer » pendant la rédaction perdait la proposition sans le dire : il attend la fin de la
  rédaction, avec la raison écrite (`aria-describedby`).
- [x] Quitter l'écran pendant une rédaction ou une relecture perdait la proposition : confirmation
  « Quitter sans appliquer la proposition ? » (garde de la route) et avertissement du navigateur.
- [x] Le panneau ouvert est dans l'adresse (`?discussion=1`), gardé en changeant de version et
  après « Appliquer » : un rechargement ou un lien le rouvre.
- [x] Petit écran : « Revenir à la discussion » reste en bas de l'écran pendant la lecture du
  document, et rend le focus à la saisie.
- [x] La liste des versions reste en tête quand la discussion est ouverte ; le défilement du
  panneau ne se prolonge plus sur la page (`overscroll-behavior: contain`).
- [x] Retouches : nom long de la spécification coupé, raison du modèle terminée par un point,
  lien « Issue de la discussion » qui dit où il mène, statut juste pour un ticket déjà importé.

## Écarts gardés

- La réflexion pendant la rédaction est dans un `details` replié : son contenu n'est pas annoncé
  au fil de l'eau (trop bavard) ; le titre d'activité et le chronomètre restent visibles.
- Le panneau ne se redimensionne pas à la souris ; sa largeur va de 22 à 30 rem.
- Le lien « Discussion du …, message n » ouvre la conversation, pas le message lui-même.
- « Abandonner » ne demande pas de confirmation : rien n'est créé, mais la proposition est perdue.
- Les libellés gardent l'apostrophe droite (« Discuter avec l'IA »), comme le reste de l'outil.
