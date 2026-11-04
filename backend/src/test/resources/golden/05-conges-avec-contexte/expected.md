# Demande de congés payés

## Résumé

Le salarié demande des congés ; son responsable accepte ou refuse.

## Use cases

### UC-01 : Demander des congés

- **Acteur** : salarié
- **Objectif** : Obtenir des jours de congés payés
- **Déroulement nominal** :
  1. Choisir les dates en jours entiers
  2. Envoyer la demande
- **Alternatives** :
  - La demande dépasse le solde : elle est refusée à la saisie
- **Source** : extrait du besoin « poser une demande de congés payés »

### UC-02 : Répondre à une demande

- **Acteur** : responsable hiérarchique
- **Objectif** : Accepter ou refuser sous 5 jours ouvrés
- **Préconditions** :
  - Une demande est en attente
- **Déroulement nominal** :
  1. Ouvrir la demande
  2. Accepter ou refuser
- **Alternatives** :
  - Le responsable est absent : la demande va à son remplaçant
- **Source** : contexte du projet, fiche fonctionnelle « Le responsable hiérarchique valide les demandes de son équipe. »

## Scénarios d'acceptation

### UC-01 : Demander des congés

#### SC-01-01 : Demande dans le solde
- **En tant que** salarié
- **Étant donné** mon solde est de 10 jours
- **Quand** je demande 3 jours
- **Alors** la demande part à mon responsable

#### SC-01-02 : Demande au-delà du solde
- **En tant que** salarié
- **Étant donné** mon solde est de 2 jours
- **Quand** je demande 3 jours
- **Alors** la demande est refusée et mon solde est affiché

### UC-02 : Répondre à une demande

#### SC-02-01 : Acceptation
- **En tant que** responsable hiérarchique
- **Étant donné** une demande de 3 jours est en attente
- **Quand** je l'accepte
- **Alors** le solde du salarié baisse de 3 jours

#### SC-02-02 : Responsable absent
- **En tant que** salarié
- **Étant donné** mon responsable est absent
- **Quand** j'envoie ma demande
- **Alors** la demande est transmise à son remplaçant

## Hypothèses

Aucune.

## Exigences fonctionnelles

- **EF-01** : Le responsable répond sous 5 jours ouvrés.

## Exigences techniques

Aucune.
