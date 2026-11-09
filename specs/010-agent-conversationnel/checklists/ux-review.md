# Revue d'ergonomie : agent conversationnel (T029, FR-023, SC-008)

**Outil** : compétence `web-design-guidelines` (règles Vercel « Web Interface Guidelines », relues le 2026-11-09).
**Fichiers** : `conversation.page.ts`, `need-draft.component.ts`, `conversation-list.component.ts`,
`sessions.page.ts`, `app.ts`. Aucun écart bloquant ne reste.

## Écarts corrigés

- [x] `conversation.page.ts` : `(click)` sur l'article de la note sans équivalent clavier (lint
  `click-events-have-key-events`). La délégation passe sur l'hôte ; Entrée sur un lien produit le
  même clic.
- [x] `conversation.page.ts` : le placeholder du champ finit par « … » ; `autocomplete="off"`.
- [x] `conversation.page.ts` : le champ collé en bas pouvait cacher l'élément qui a le focus
  (`scroll-margin-bottom` sur le fil).
- [x] `conversation.page.ts` : questions et notes très longues (adresses) coupées
  (`overflow-wrap: anywhere`).
- [x] `conversation.page.ts` : « Cette conversation n'existe plus » dit quoi faire ensuite.
- [x] `need-draft.component.ts` : « Démarrer la spécification » reste actif ; un besoin vide est
  signalé sous le champ (`aria-invalid`, `aria-describedby`), qui reçoit le focus.
- [x] `need-draft.component.ts`, `conversation-list.component.ts` : `autocomplete="off"` sur les
  champs.
- [x] `conversation-list.component.ts` : nom long dans la confirmation de suppression coupé.

## Écarts justifiés

- Casse « Title Case » des titres et boutons : règle de l'anglais. L'interface est en français, en
  casse de phrase, comme le reste de l'outil.
- « Envoyer » désactivé tant que le champ est vide, « Enregistrer » d'un renommage vide : aucune
  requête n'est possible, et c'est le comportement des autres écrans (renommage des
  spécifications).
- Panneau des étapes de recherche replié ou ouvert absent de l'adresse : état d'affichage
  secondaire ; l'adresse désigne la conversation (`/conversations/{id}`).
- Pas d'alerte avant de quitter un brouillon de besoin non validé : le brouillon se redemande d'un
  clic à l'agent, rien n'est perdu de la conversation.
- Suppression d'une conversation : confirmation dans la liste plutôt qu'en fenêtre modale ; elle
  dit ce qui disparaît et ce qui reste.

## Conformes

Libellés des champs, liens et boutons sémantiques, région `aria-live` séparée pour les annonces,
titres hiérarchiques avec lien d'évitement (`app.ts`), focus visible (styles globaux), états vides
(accueil avec exemples, « Aucune conversation dans ce projet »), aucune animation ajoutée, axe sans
violation (E2E us13).
