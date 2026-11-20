# Contrat d'interface : préparation, séance, récapitulatif

Aucune nouvelle entrée de menu (spec, FR-001). WCAG 2.1 AA ; tout au clavier (FR-031).

## Page « Spécifications » (`/sessions`)

- Sous chaque spécification : « 3 hypothèses · 2 points ouverts · 1 écart entre sources », ou
  « Aucun point à traiter ». Lu avec le nom de la spécification par les lecteurs d'écran.
- Case « Avec des points à traiter » au-dessus de la liste (filtre, annonce du nombre affiché).
- Une case à cocher par spécification lisible (`aria-label` : « Sélectionner « Émission des
  avoirs » pour une séance »). Dès une case cochée : bouton « Préparer une séance (n) » en tête de
  liste.
- Séances préparées en tête de page : « Séance préparée : 2 spécifications, 7 points » avec
  « Démarrer », « Modifier », « Supprimer » (confirmation qui nomme la séance).

## Préparation (`/seances/:id/preparation`, shell normal)

- Spécifications dans l'ordre de passage, déplaçables (boutons « Monter », « Descendre », pas
  seulement le glisser).
- Participants : champ à jetons, proposés d'après la dernière séance.
- Par spécification, ses points (code, texte court, élément), chacun avec « Écarter de cette
  séance ».
- « Enregistrer », puis retour à la page « Spécifications » ; « Démarrer maintenant ».

## Séance (`/seances/:id`, présentation)

Présentation (research.md R6) : sans rail ni avis de projet, notifications du navigateur
suspendues, taille de base 1,25 rem.

```
┌──────────────────────────────────────────────────────────────┐
│ Spéc. 1/2 « Émission des avoirs »   UC-2 · 4/12   [Terminer] │
├──────────────────────────────────────────────────────────────┤
│ UC-2  Émettre un avoir partiel        source : CWiki p. 4512 │
│  1. Le gestionnaire ouvre la facture.                        │
│  2. Il choisit les lignes à créditer.                        │
│ ▶3. Il saisit le montant.                                    │
│      💬 Remarque : validation manager au-delà de 500 €       │
│  4. ⚠ H-3 Le montant ne dépasse pas le facturé  [Répondre]   │
│  5. L'avoir est émis et envoyé au client.                    │
│                                                              │
│ ↑↓ élément · R remarque · Q question · ↵ répondre · → UC     │
│ Points : 2 décidés · 1 remarque · 3 ouverts restants         │
└──────────────────────────────────────────────────────────────┘
```

- Un élément courant, mis en avant (bordure et fond), annoncé à son arrivée (région `polite`) :
  « UC-2, étape 3 : Il saisit le montant. 1 remarque. ».
- Points sur l'élément : icône et libellé texte (« Hypothèse H-3 », « Écart entre sources »,
  « Point ouvert Q-1 »), jamais la couleur seule. Non traités : « [Répondre] ».
- Saisie d'un point : champ de texte, puis l'issue en boutons radio (« Confirmé », « À modifier »,
  « À retirer », « Reporté ») ; pour « Reporté », responsable et échéance ; lien « Source à mettre
  à jour… » (recherche dans la connaissance du projet). « Enregistrer » (Entrée dans le champ
  responsable, Ctrl+Entrée dans le texte) ; enregistrement confirmé discrètement (« Enregistré »).
- Raccourcis (désactivés quand un champ a le focus) : ↑ ↓, ← →, `N` prochain point à traiter, `R`
  remarque, `Q` question, `Entrée` répondre au point de l'élément, `?` liste des raccourcis.
- Progression permanente : spécification, élément, « 2 décidés · 1 remarque · 3 restants ».
- Version plus récente apparue : bandeau « Une version plus récente existe (v5) : la révision
  partira d'elle. »
- « Terminer » : confirmation si des points obligatoires restent non traités (les nomme), puis
  récapitulatif.
- Reprise : « Reprendre la séance » sur la page de la spécification, et en tête de la page
  « Spécifications », ramène à l'élément du curseur.

## Récapitulatif (`/seances/:id/recapitulatif`, shell normal)

1. **Compte rendu** : aperçu, « Copier le compte rendu » (HTML et texte) ; confirmation « Compte
   rendu copié » (annoncée) ; repli : zone de texte sélectionnée et consigne « Ctrl+C pour copier ».
2. **Révision**, une ligne par spécification : « Proposer la nouvelle version » → la page de la
   spécification, discussion ouverte, tour de révision en cours, puis relecture en diff
   (composants existants de 011). Indisponible : la raison (« Copilot n'est pas autorisé : … ») et
   ce qu'il faut faire. Faite : « Version 5 appliquée ».
3. **Sources à mettre à jour** : source (lien), texte de la décision.

## Page d'une spécification

- En-tête : « Faire le point (5) » (nombre de points à traiter), ou « Reprendre la séance » si une
  séance est en cours ; indisponible pendant une génération ou une révision, avec la raison.
- Rubrique « Points ouverts » (après « Hypothèses ») ; chaque hypothèse, écart et point ouvert
  indique son élément (« UC-2, étape 4 »), avec un lien qui y mène.
- Bloc « Sources à mettre à jour » (s'il y en a) : source, texte, état (« Source modifiée, à
  vérifier »), « Marquer comme reporté » (clôt).
- « Séances » : liste des séances passées, chacune avec son compte rendu à revoir et copier, et
  « Proposer la nouvelle version » tant que sa révision n'est pas appliquée (FR-014).
