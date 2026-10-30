# Réservation des salles de réunion

## Résumé

Les employés du siège réservent une salle pour un créneau et sont prévenus si elle est prise.

## Use cases

### UC-01 : Réserver une salle

- **Acteur** : employé du siège
- **Objectif** : Obtenir une salle libre pour un créneau
- **Préconditions** :
  - L'employé est connecté
- **Déroulement nominal** :
  1. Choisir le créneau
  2. Choisir une salle libre
  3. Confirmer la réservation
- **Alternatives** :
  - La salle est prise : afficher les autres salles libres du même étage
- **Source** : extrait du besoin « réserver une salle de réunion depuis leur poste, pour un créneau donné »

### UC-02 : Proposer une autre salle

- **Acteur** : employé du siège
- **Objectif** : Trouver une salle quand celle demandée est prise
- **Préconditions** :
  - La salle demandée est réservée sur le créneau
- **Déroulement nominal** :
  1. Afficher les salles libres du même étage
  2. Réserver l'une d'elles
- **Source** : réponse « Proposer les autres salles libres du même étage »

## Scénarios d'acceptation

### UC-01 : Réserver une salle

#### SC-01-01 : Réservation d'une salle libre
- **En tant que** employé du siège
- **Étant donné** la salle Arago est libre de 10 h à 11 h
- **Quand** je réserve la salle Arago de 10 h à 11 h
- **Alors** la réservation est confirmée
- **Et** le créneau n'est plus proposé aux autres employés

#### SC-01-02 : Réservation au-delà de la durée maximale *(Hypothèse)*
- **En tant que** employé du siège
- **Quand** je réserve une salle pour 5 heures
- **Alors** la réservation est refusée car elle dépasse 4 heures

### UC-02 : Proposer une autre salle

#### SC-02-01 : Salle déjà prise
- **En tant que** employé du siège
- **Étant donné** la salle Arago est réservée de 10 h à 11 h
- **Et** la salle Curie, au même étage, est libre
- **Quand** je demande la salle Arago de 10 h à 11 h
- **Alors** un message m'indique que la salle est prise
- **Et** la salle Curie m'est proposée

## Hypothèses

- **H-01** : « Quelle est la durée maximale d'une réservation ? » : 4 heures (valeur par défaut, question passée)

## Exigences fonctionnelles

- **EF-01** : Une salle ne peut pas être réservée deux fois sur le même créneau.

## Exigences techniques

- **ET-01** : La réservation est accessible depuis le poste de travail de l'employé.
