# Paiement en ligne par carte

## Résumé

Les clients paient leur panier par carte bancaire, sans frais pour eux.

## Use cases

### UC-01 : Payer le panier

- **Acteur** : client de la boutique
- **Objectif** : Régler son panier par carte bancaire
- **Préconditions** :
  - Le panier contient au moins un article
- **Déroulement nominal** :
  1. Valider le panier
  2. Saisir la carte
  3. Confirmer le paiement
- **Alternatives** :
  - La banque refuse : le panier est conservé
- **Source** : extrait du besoin « payer leur panier en ligne par carte bancaire »

## Scénarios d'acceptation

### UC-01 : Payer le panier

#### SC-01-01 : Paiement accepté
- **En tant que** client de la boutique
- **Étant donné** un panier de 3 articles pour 45 €
- **Quand** je valide le paiement
- **Alors** la commande est confirmée
- **Et** le montant débité est de 45 €, sans frais

#### SC-01-02 : Paiement refusé
- **En tant que** client de la boutique
- **Étant donné** un panier de 3 articles
- **Quand** je valide le paiement
- **Et** ma banque refuse la transaction
- **Alors** un message m'indique que le paiement a échoué et me propose de réessayer
- **Et** mon panier est conservé

## Hypothèses

- **H-01** : La boutique accepte les cartes des réseaux Visa et Mastercard.

## Exigences fonctionnelles

- **EF-01** : Aucun frais de traitement n'est facturé au client.

## Exigences techniques

- **ET-01** : Les données de carte ne sont pas conservées par la boutique.
