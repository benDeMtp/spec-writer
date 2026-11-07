# Validation : budget de contexte selon le modèle

**Essai manuel (T019)** : 2026-11-07, par le mainteneur, sur le projet réel Aurora, modèle
`claude-sonnet-5.5` (limite de prompt annoncée : 936 000 tokens, coefficient non annoncé ; budget
de la connaissance : 200 000 caractères).

| Point de l'essai (quickstart.md) | Résultat |
|---|---|
| Premier essai : la matrice reste coupée (H-01 « document tronqué ») | Défaut : connexion d'avant la 008, capacités vides ; corrigé par T020 (relecture au démarrage) |
| Déconnexion pour relire les capacités | Défaut : trousseau macOS refusant la suppression d'un jeton rangé par un autre programme, message « Copilot ne répond pas » ; corrigé par T021 (message et marche à suivre) |
| Après relance : capacité affichée, connaissance transmise jusqu'à 200 000 caractères | Validé |
| Régénération de la session « planning intelligent » : la matrice (36 938 caractères indexés) est transmise en entier, H-01 disparaît | Validé |

Décision du mainteneur : « ok ça fonctionne !!! ».
