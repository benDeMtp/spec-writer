## Vision et objectifs
- Réduire les appels au support sur le suivi des commandes. [source : ventes/vision.html]

## Acteurs
- Un partenaire consulte les commandes de son seul compte. [source : ventes/acteurs.html]

## Vocabulaire

## Règles métier
- Une commande passe par les statuts reçue, préparée, expédiée, livrée. [source : CMD-7]

## Contraintes fonctionnelles


## Architecture
- Les API publiques passent par la passerelle Kong, qui authentifie en OAuth2 client credentials. [source : archi/passerelle.html]

## Contraintes techniques
- Une API publique répond en moins de 300 ms au 95e centile. [source : archi/sla.html]

## Conventions de code
- Les API REST sont décrites en OpenAPI 3.1 et versionnées dans le chemin (/v1/). [source : Q-1]

## Intégrations
- Les statuts de commande viennent du système de gestion des commandes par la file « commandes.statut ». [source : archi/flux.html]
