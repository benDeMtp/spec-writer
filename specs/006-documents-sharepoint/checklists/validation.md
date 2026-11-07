# Validation : documents SharePoint par dossier synchronisé

**Essai manuel (T034)** : 2026-11-06, par le mainteneur, sur le projet réel Aurora (dossier
« DC Mission Outils - Documents/General » synchronisé par OneDrive).

| Point de l'essai (quickstart.md) | Résultat |
|---|---|
| Dossier déclaré en « dossier de documents », indexé : 84 documents, 2 026 passages | Validé |
| Génération sur le planning intelligent : 2 documents du dossier transmis (FAQ, compte rendu de suivi) | Validé |
| Matrice des règles de gestion citée « @Aurora_Matrice_… » dans une réponse : non transmise, l'IA n'en voit que le nom (hypothèse H-01) | Hors périmètre : mention explicite et classeur en base, feature suivante (`docs/superpowers/specs/2026-11-06-documents-designes-design.md`) |

Décision du mainteneur : la 006 est gardée telle quelle et fusionnée ; la désignation explicite
d'un document vient avec la feature suivante.
