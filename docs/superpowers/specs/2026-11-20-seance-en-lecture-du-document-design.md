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
3. **Colonne de notes (21/11/2026)**, après essai sur une spécification réelle : tout le texte
   s'annote, et les remarques et questions vont dans une colonne à droite du document, pas dans
   le document. Les hypothèses, écarts et points ouverts restent en carte sous leur élément.
4. **Relire, seul ou en séance (21/11/2026)** : un seul écran, deux usages. « Faire le point »
   devient « Relire » sur la page de la spécification. « Prendre en compte les notes », disponible
   à tout moment, termine la relecture sans dialogue (les points non traités restent ouverts) et
   lance la révision ; « Terminer » mène au récapitulatif et au compte rendu, pour la séance avec
   les sachants. Une spécification générée s'ouvre, depuis la liste, sur sa dernière version.
5. **Zoom de la relecture (21/11/2026)** : le document prend toute la largeur, agrandi à 125 % par
   défaut, réglable (A− / A+, de 100 à 200 %), retenu dans le navigateur. Il ne vaut que pour cet
   écran : la page de la spécification et la discussion restent à la taille normale.
6. **Relecture vide (21/11/2026)** : terminée sans aucun point noté, elle n'est pas gardée comme
   séance ; « Terminer » revient à la spécification, sans confirmation ni récapitulatif.

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

Le rendu est propre à la séance (`sw-interview-document`) : la page de la spécification range les
scénarios, les hypothèses et les points ouverts dans des rubriques à part, la séance les place sous
chaque élément. Les deux partagent l'énoncé de story (`storyParts`) et les scénarios
(`sw-scenario`). Chaque élément porte son ancre (`data-anchor="UC-01:2"`), et reçoit ses points
par un emplacement (`ng-template`). Les ancres restent les codes de la spécification.

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
- Une remarque ou une question notée en séance a aussi « Supprimer » ; la réponse à un point de
  la spécification se modifie, elle ne se supprime pas.
- « Source à mettre à jour… » (US6) reste dans le formulaire déplié.

Le formulaire déplié reprend `PointEditor` (issues, responsable, échéance, source désignée,
Ctrl+Entrée).

### Corriger un passage (à la Plannotator)

**Tout le texte du document s'annote** : titres, détails, tableaux, énoncé de story, et texte d'une
spécification antérieure sans ancres. Une sélection ouvre, dans la colonne de notes, une barre :

- **Commenter** : une remarque libre (origine `REMARK`) ;
- **Remplacer** : le texte qui doit remplacer le passage, prérempli avec le passage à réécrire
  (`REMARK`, issue `À MODIFIER`) ;
- **Supprimer** : le passage est à retirer (`REMARK`, issue `À RETIRER`), d'un clic ;
- **Question…** : une question nouvelle, avec son responsable.

La note se rattache à l'élément ancré qui contient le début de la sélection, sinon au dernier
élément qui le précède (un titre ou un tableau des détails se rattache au résumé), et **garde le
passage sélectionné tel quel**. Le passage reste surligné dans le document.

### La colonne de notes

À droite du document, toujours visible au défilement (sous le document sur un écran étroit) :

- en tête, la barre de la sélection en cours, puis la saisie de la note ;
- les remarques et questions de la spécification, dans l'ordre du document : l'élément
  (« Résumé », « UC-01, étape 1 »), le passage cité, l'issue et le texte ; **Aller au passage**
  (défile jusqu'au passage et le met en évidence), **Modifier**, **Supprimer**.

Rien n'est inséré dans le document pour une remarque : le texte ne bouge pas sous la souris, et
le rendu Markdown reste intact. Le bouton « Noter » par élément disparaît.

### Navigation

- ↑ ↓ (hors champ de saisie) : carte de point précédente / suivante, en faisant défiler le
  document ; « Prochain point à traiter » saute au suivant non traité.
- Le curseur enregistré (`PUT /api/interviews/{id}/cursor`) est l'ancre de la dernière carte
  focalisée : la séance reprend à cet endroit.
- Disparaissent : l'affichage arrêt par arrêt, `walkthrough.ts` (blocs, ← →), les raccourcis N, R
  et Q.

### Données

Migration **V31** : colonne `quote` (texte, 1 000 caractères au plus, facultative) sur
`interview_point`. Elle passe dans `PointInput` et `PointView`, et suit le libellé de l'élément
dans le compte rendu et dans le message de séance transmis à la révision (« UC-01, étape 1,
« passage » — à retirer », « … — à modifier : nouveau texte »). Le format de la sortie du modèle ne change pas : l'évaluation n'est
pas à refaire pour cette partie.

### Accessibilité et écran partagé

Gros caractères comme aujourd'hui ; chaque carte est une région nommée (« Hypothèse H-3 ») ; la
barre de sélection est un `role="toolbar"` ; l'issue enregistrée est annoncée (`aria-live`).
Contrastes du surlignage vérifiés en clair et en sombre.

## Impacts

- `spec.md` de la 021 : FR-002, FR-003, FR-005, FR-006 réécrits (document vertical, réponse
  proposée, sélection d'un passage) ; scénarios d'acceptation de US1 et US4 ajustés.
- `tasks.md` : une phase « US7 — séance en lecture du document » ; T094 (évaluation) inchangée.
- Front : `sw-interview-document`, `sw-point-card`, sélection d'un passage ; `interview.page.ts` réécrite ;
  `walkthrough.ts` supprimé ; tests Vitest et E2E de séance (V1 à V6) repris.
- Back : V31, `InterviewPoint.quote`, compte rendu, message de séance ; tests associés.
- Visite guidée : l'arrêt « Faire le point avec les sachants » décrit le nouvel écran.

## Hors périmètre

- Modifier le texte de la spécification en direct pendant la séance : les corrections restent des
  points, appliqués par la révision de fin.
- Réponse proposée par le modèle pour les points ouverts.
