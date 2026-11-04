# Émission d'un avoir

## Résumé

Le gestionnaire de facturation corrige une facture émise par un avoir.

## Use cases

### UC-01 : Émettre un avoir

- **Acteur** : gestionnaire de facturation
- **Objectif** : Corriger le montant d'une facture émise
- **Préconditions** :
  - La facture est émise
- **Déroulement nominal** :
  1. Ouvrir la facture
  2. Saisir le montant de l'avoir
  3. Valider l'avoir
  4. Envoyer l'avoir au client
- **Alternatives** :
  - Le montant dépasse le restant dû : l'avoir est refusé
- **Source** : extrait du besoin « émettre un avoir sur une facture déjà émise »

### UC-02 : Refuser un avoir trop élevé

- **Acteur** : gestionnaire de facturation
- **Objectif** : Ne jamais rembourser plus que le restant dû
- **Déroulement nominal** :
  1. Saisir un montant supérieur au restant dû
  2. Lire le motif du refus
- **Source** : contexte du projet, fiche fonctionnelle « Un avoir ne peut pas dépasser le montant restant dû de la facture. »

## Scénarios d'acceptation

### UC-01 : Émettre un avoir

#### SC-01-01 : Avoir partiel
- **En tant que** gestionnaire de facturation
- **Étant donné** la facture F-001 de 120 € TTC est émise
- **Quand** j'émets un avoir de 20 € TTC
- **Alors** l'avoir est créé
- **Et** le client reçoit un courriel avec l'avoir en PDF

#### SC-01-02 : Deuxième avoir *(Hypothèse)*
- **En tant que** gestionnaire de facturation
- **Étant donné** un avoir de 20 € TTC existe sur F-001
- **Quand** j'émets un avoir de 30 € TTC
- **Alors** l'avoir est créé

### UC-02 : Refuser un avoir trop élevé

#### SC-02-01 : Avoir supérieur au restant dû
- **En tant que** gestionnaire de facturation
- **Étant donné** il reste 70 € TTC dus sur F-001
- **Quand** j'émets un avoir de 80 € TTC
- **Alors** l'avoir est refusé avec le restant dû indiqué

## Hypothèses

- **H-01** : « Peut-on émettre plusieurs avoirs sur une même facture ? » : Oui, dans la limite du restant dû (valeur par défaut, question passée)

## Exigences fonctionnelles

- **EF-01** : La facture d'origine n'est jamais modifiée.
- **EF-02** : Les montants sont affichés en euros TTC.

## Exigences techniques

Aucune.
