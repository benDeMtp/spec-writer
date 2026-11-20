# Séance en lecture du document — conception

Date : 20/11/2026. État : à valider par le mainteneur. Prolonge
`2026-11-20-interview-specification-design.md` (feature 021) et en remplace la section
« Écran de séance ».

## Pourquoi

L'écran livré parcourt la spécification élément par élément : un arrêt à la fois, un éditeur à
ouvrir (↵, R, Q) pour chaque point. Ce n'est pas ce que le mainteneur attendait. Il veut :

- **dérouler la spécification verticalement**, comme on la lit, en suivant le flux ;
- sur chaque **hypothèse**, une **réponse déjà proposée**, qu'on valide d'un clic ;
- partout ailleurs, pouvoir **corriger ce qui est faux** directement dans le texte, à la manière de
  Plannotator : on sélectionne un passage, on le commente, le remplace ou le supprime.

## Ce qui a été décidé

1. **Réponse proposée (choix A)** : pour une hypothèse, la réponse proposée est l'hypothèse
   elle-même. « Confirmer » l'enregistre d'un clic ; « Réécrire » ouvre le texte de l'hypothèse
   prérempli. Aucun appel au modèle pendant la séance.
2. **Approche 1** : un écran de séance qui affiche le document, distinct de la page de la
   spécification. Les approches 2 (mode séance sur la page de la spécification) et 3 (annotation
   du Markdown rendu, sans structure) sont écartées.

## Conception

### Ce qui ne change pas

Modèle et serveur de la 021 : séance, préparation, points (`InterviewPoint`), issues, ancres,
compte rendu, révision en une fois par la discussion de la 011, « à reporter ». Le parcours calculé
par le serveur (`Walkthrough`) reste la source du **placement** des points (par ancre, points
écartés exclus, « Non rattachés ») et des compteurs.

### Le document

L'écran `/seances/:id` (menu et notifications toujours masqués) affiche la version de la séance
(`GET /api/sessions/{id}/versions/{n}`) **en entier, de haut en bas** : énoncé de story, résumé,
détails, chaque use case (acteur, objectif, préconditions, étapes, alternatives) suivi de ses
scénarios (Soit / Quand / Alors), puis les exigences, et en fin « Non rattachés » s'il y a lieu.

Le rendu est extrait de la page de la spécification dans un composant partagé
`sw-spec-document`, qui expose un emplacement par élément (`ng-template` reçu en entrée, avec
l'ancre de l'élément). La page de la spécification l'utilise sans emplacement ; l'écran de séance y
insère les cartes de points. Un seul rendu à maintenir, et les ancres restent les codes de la
spécification (`UC-2`, étape 3, `SA-2.1`…).

En-tête fixe : « Spéc. 1 sur 2 · Émission des avoirs », compteurs (« 2 décidés · 1 remarque ·
3 à traiter »), « Prochain point à traiter », « Terminer ». Une séance de plusieurs
spécifications les présente l'une après l'autre ; « Spécification suivante » en bas de chaque
document.

### Les cartes de points

Sous l'élément qu'elle concerne, une carte par point, dans le flux du texte :

```
 3. Il saisit le montant.
 ┌ H-3 Hypothèse ─────────────────────────────────────────────┐
 │ Le montant ne dépasse pas le montant facturé.              │
 │ [Confirmer]  [Réécrire]  [Retirer]  [Reporter…]            │
 └────────────────────────────────────────────────────────────┘
 4. L'avoir est émis et envoyé au client.
```

- **Hypothèse** : Confirmer (un clic, issue `CONFIRMÉ`), Réécrire (texte prérempli, `À MODIFIER`),
  Retirer (`À RETIRER`), Reporter (responsable, échéance, `REPORTÉ`).
- **Écart entre sources** : même jeu ; « Confirmer » garde la spécification telle qu'écrite.
- **Point ouvert** : pas de réponse proposée ; « Répondre » (texte, `À MODIFIER`) ou « Reporter ».
- Une fois traitée, la carte se replie en une ligne (« ✓ Confirmé », « ✎ Réécrit : … »,
  « ⏳ Reporté à Paul D. ») avec « Modifier » pour revenir dessus.
- « Source à mettre à jour… » (US6) reste dans le formulaire déplié.

Le formulaire déplié reprend `PointEditor` (issues, responsable, échéance, source désignée,
Ctrl+Entrée).

### Corriger un passage (à la Plannotator)

On sélectionne un passage dans n'importe quel élément du document ; une barre flottante propose :

- **Commenter** : une remarque libre (origine `REMARK`) ;
- **Remplacer** : le texte qui doit remplacer le passage (`REMARK`, issue `À MODIFIER`) ;
- **Supprimer** : le passage est à retirer (`REMARK`, issue `À RETIRER`).

Le point est ancré sur l'élément qui contient la sélection et **garde le passage cité**. Une
sélection qui déborde de l'élément est ramenée au premier élément touché. Le passage apparaît
surligné dans le document, avec la carte de la remarque sous l'élément.

« Question… » reste disponible sur chaque élément (bouton au survol et au focus) pour une question
nouvelle, avec son responsable.

Au clavier : la barre s'ouvre aussi par Maj+flèches puis Entrée sur la sélection ; chaque élément
a un bouton « Noter » focalisable, qui ouvre les mêmes trois actions sur l'élément entier.

### Navigation

- ↑ ↓ (hors champ de saisie) : carte de point précédente / suivante, en faisant défiler le
  document ; « Prochain point à traiter » saute au suivant non traité.
- Le curseur enregistré (`PUT /api/interviews/{id}/cursor`) est l'ancre de la dernière carte
  focalisée : la séance reprend à cet endroit.
- Disparaissent : l'affichage arrêt par arrêt, `walkthrough.ts` (blocs, ← →), les raccourcis N, R
  et Q.

### Données

Migration **V31** : colonne `quote` (texte, 1 000 caractères au plus, facultative) sur
`interview_point`. Elle passe dans `PointInput` et `PointView`, dans le compte rendu (« Sur
« passage » : … ») et dans le message de séance transmis à la révision (« remplacer « passage » par
… », « supprimer « passage » »). Le format de la sortie du modèle ne change pas : l'évaluation n'est
pas à refaire pour cette partie.

### Accessibilité et écran partagé

Gros caractères comme aujourd'hui ; chaque carte est une région nommée (« Hypothèse H-3 ») ; la
barre de sélection est un `role="toolbar"` ; l'issue enregistrée est annoncée (`aria-live`).
Contrastes du surlignage vérifiés en clair et en sombre.

## Impacts

- `spec.md` de la 021 : FR-002, FR-003, FR-005, FR-006 réécrits (document vertical, réponse
  proposée, sélection d'un passage) ; scénarios d'acceptation de US1 et US4 ajustés.
- `tasks.md` : une phase « US7 — séance en lecture du document » ; T094 (évaluation) inchangée.
- Front : `sw-spec-document` extrait de `specification.page.ts` ; `interview.page.ts` réécrite ;
  `walkthrough.ts` supprimé ; tests Vitest et E2E de séance (V1 à V6) repris.
- Back : V31, `InterviewPoint.quote`, compte rendu, message de séance ; tests associés.
- Visite guidée : l'arrêt « Faire le point avec les sachants » décrit le nouvel écran.

## Hors périmètre

- Modifier le texte de la spécification en direct pendant la séance : les corrections restent des
  points, appliqués par la révision de fin.
- Réponse proposée par le modèle pour les points ouverts.
