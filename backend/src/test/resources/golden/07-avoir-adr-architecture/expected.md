# Émission d'un avoir total

## Résumé

Le gestionnaire de facturation annule une facture émise par un avoir total, transmis à la comptabilité et envoyé au client.

## Use cases

### UC-01 : Émettre un avoir total

- **Acteur** : gestionnaire de facturation
- **Objectif** : Annuler une facture émise
- **Préconditions** :
  - La facture est émise
- **Déroulement nominal** :
  1. Ouvrir la facture
  2. Émettre l'avoir total
  3. Transmettre l'avoir à la comptabilité
- **Source** : ticket FACT-7

### UC-02 : Transmettre l'avoir à la comptabilité

- **Acteur** : gestionnaire de facturation
- **Objectif** : Tenir la comptabilité à jour
- **Préconditions** :
  - L'avoir est émis
- **Déroulement nominal** :
  1. Envoyer l'avoir à la comptabilité par un appel synchrone
- **Source** : page facturation/adr/adr-005.html

### UC-03 : Envoyer l'avoir au client

- **Acteur** : gestionnaire de facturation
- **Objectif** : Informer le client
- **Préconditions** :
  - L'avoir est émis
- **Déroulement nominal** :
  1. L'éditique envoie l'avoir au client par courriel
- **Source** : élément d'architecture Editique

## Scénarios d'acceptation

### UC-01 : Émettre un avoir total

#### SC-01-01 : Avoir total
- **En tant que** gestionnaire de facturation
- **Étant donné** la facture F-001 de 120 € TTC est émise
- **Quand** j'émets un avoir total
- **Alors** un avoir de 120 € TTC est créé

### UC-02 : Transmettre l'avoir à la comptabilité

#### SC-02-01 : Transmission à la comptabilité
- **En tant que** gestionnaire de facturation
- **Étant donné** un avoir de 120 € TTC est émis
- **Quand** l'avoir est transmis
- **Alors** la comptabilité enregistre l'avoir

### UC-03 : Envoyer l'avoir au client

#### SC-03-01 : Courriel au client
- **En tant que** gestionnaire de facturation
- **Étant donné** un avoir de 120 € TTC est émis
- **Quand** l'éditique traite l'avoir
- **Alors** le client reçoit un courriel avec l'avoir en PDF

## Hypothèses

Aucune.

## Exigences fonctionnelles

- **EF-01** : Les montants sont affichés en euros TTC.

## Exigences techniques

Aucune.

## Architecture

### Modules impactés

- Facturation/Service Avoirs *(propre au projet)*
- Comptabilité *(commun)*
- Editique *(commun)*

### Flux

- Facturation/Service Avoirs → Comptabilité : flux existant, via Bus/Topic Avoirs
- Facturation/Service Avoirs → Editique : **flux manquant, à créer**

## Écarts aux ADR

- **facturation/adr/adr-005.html**
  - Décision : La comptabilité est notifiée de façon asynchrone, par le topic des avoirs.
  - Écart : Le besoin demande un appel synchrone à la comptabilité.

## Efforts supplémentaires (déploiement)

- Facturation/Service Avoirs : aucune instance en production, à déployer
