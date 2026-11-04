# API de suivi des commandes

## Résumé

Les partenaires consultent le statut de leurs commandes par une API publique.

## Use cases

### UC-01 : Consulter ses commandes

- **Acteur** : partenaire
- **Objectif** : Suivre ses commandes sans appeler le support
- **Préconditions** :
  - Le partenaire est authentifié par la passerelle
- **Déroulement nominal** :
  1. Demander la liste de ses commandes
  2. Lire le statut de chaque commande
- **Alternatives** :
  - Plus de 100 commandes : la réponse est paginée
- **Source** : extrait du besoin « consulter le statut de leurs commandes par une API »

### UC-02 : S'authentifier auprès de la passerelle

- **Acteur** : partenaire
- **Objectif** : Obtenir un jeton d'accès
- **Déroulement nominal** :
  1. Présenter ses identifiants client
  2. Recevoir un jeton
- **Alternatives** :
  - Identifiants refusés : aucun jeton n'est délivré
- **Source** : contexte du projet, fiche technique « Les API publiques passent par la passerelle Kong, qui authentifie en OAuth2 client credentials. »

## Scénarios d'acceptation

### UC-01 : Consulter ses commandes

#### SC-01-01 : Liste paginée
- **En tant que** partenaire
- **Étant donné** j'ai 250 commandes
- **Quand** je demande mes commandes
- **Alors** je reçois les 100 premières et le lien de la page suivante

#### SC-01-02 : Commande d'un autre compte
- **En tant que** partenaire
- **Étant donné** la commande C-9 appartient à un autre partenaire
- **Quand** je demande la commande C-9
- **Alors** la réponse indique que la commande est introuvable

### UC-02 : S'authentifier auprès de la passerelle

#### SC-02-01 : Jeton délivré
- **En tant que** partenaire
- **Étant donné** mes identifiants client sont valides
- **Quand** je demande un jeton
- **Alors** je reçois un jeton d'accès

## Hypothèses

Aucune.

## Exigences fonctionnelles

- **EF-01** : Un partenaire ne voit que les commandes de son compte.

## Exigences techniques

- **ET-01** : L'API est décrite en OpenAPI 3.1 et versionnée dans le chemin (/v1/).
- **ET-02** : L'API répond en moins de 300 ms au 95e centile.
- **ET-03** : Les statuts sont lus depuis la file « commandes.statut ».
