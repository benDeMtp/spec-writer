# Validation : tickets Jira fermés gardés dans l'index

**Essai manuel (T029)** : 2026-11-06, par le mainteneur, sur le projet réel Aurora (24 tickets).

| Point de l'essai (quickstart.md) | Constat | Résultat |
|---|---|---|
| Indexation complète, durée de l'étape Jira | Indexation terminée en 1 s pour 24 tickets | Validé |
| Bilan affiché après une indexation complète | « 24 tickets relus, 0 ticket fermé gardé sans relecture, 0 retiré » : le projet n'a que des tickets ouverts (22 à faire, 2 en cours), tous relus | Validé |
| Réouverture d'un ticket fermé | Garde des tickets fermés et réouverture couvertes par les tests automatiques (E2E P5c, `SynchronizeKnowledgeTest`) | Validé |

Décision du mainteneur : validé.
