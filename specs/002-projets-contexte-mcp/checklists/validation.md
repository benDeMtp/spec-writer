# Essais manuels de la feature 002 (T061)

Résultats des essais faits par le mainteneur sur ses propres projets, hors du dépôt.

- [X] Sous-tâches de la story (FR-044 à FR-047), après resynchronisation : la modification assistée
  ne présente plus les sous-tâches comme des travaux concurrents, et la rubrique « Sous-tâches de
  <clé> » est correcte. Validé par l'utilisateur le 2026-11-04.
- [X] Recherche par le sens (RAG) après le correctif du téléchargement du modèle (redirection
  302) : validée par l'utilisateur le 2026-11-04.
- [X] Lecture des workspaces C4 réels depuis l'application (`!script` ignoré avec un
  avertissement, `!docs` et `!adrs` acceptés) : l'utilisateur la juge fonctionnelle le 2026-11-04
  (« semble fonctionner »). Les 7 workspaces avaient aussi été lus sans erreur par un test jetable
  du développeur.
- [ ] SC-004a sur un vrai projet (300 pages, 200 tickets, workspace de 2 000 lignes) : première
  indexation, hors téléchargement du modèle. Observation partielle le 2026-11-04 : 20 tickets, une
  documentation de 10 pages et un workspace C4 indexés en moins de 30 secondes. Le volume visé par
  SC-004a reste à mesurer.
- [ ] Jeu de référence réel hors dépôt (`-Preference`) : SC-002 à SC-004c, et comparaison de
  multilingual-e5-small avec un modèle sentence-camembert déclaré par dossier.
- [ ] Modèle sur Windows et Linux depuis l'application installée (téléchargement par le proxy, puis
  sans réseau). macOS : validé par l'utilisateur le 2026-11-04.
- [ ] `spec-writer-mcp` avec Claude Code : features, contenu, `search_knowledge`.
