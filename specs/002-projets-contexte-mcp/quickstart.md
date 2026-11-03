# Quickstart : valider la feature 002

Prérequis, démarrage et boucle de développement : ceux de la feature 001
([quickstart.md](../001-local-spec-assistant/quickstart.md), `CLAUDE.md`). Les scénarios
ci-dessous (P1 à P12) sont automatisés en E2E Playwright, sauf mention contraire, avec Jira
simulé par WireMock et le LLM rejoué. Ils sont lancés sur le jar par `npm --prefix e2e test`.

## Jeu d'essai

- **Projet Jira simulé** : `FACT`, avec 120 tickets dans `e2e/fixtures/jira/`. Le projet Jira par
  défaut de la configuration est `PROJ`.
- **Site Antora de test** : `e2e/fixtures/jira/__files/antora/`, avec un `sitemap.xml`, 30 pages
  `article.doc`, une page en erreur 500, une page sans `article.doc` et un lien vers un autre hôte.
  Il est servi par WireMock sous `http://127.0.0.1:47698/antora/` (WireMock ne sert que les
  fichiers de `__files`) et existe aussi en dossier local (`backend/src/test/resources/antora-site/`).
- **Réponses LLM rejouées** pour `summarize-project-sources` et `build-project-context`
  (`e2e/fixtures/llm/`).

## Scénarios

| # | Story | Étapes | Résultat attendu |
|---|---|---|---|
| P1 | US1 | Démarrer sur une base de la feature 001 qui contient 3 sessions. | Un « Projet par défaut » contient les 3 sessions et toutes leurs versions (SC-001). |
| P2 | US1 | Créer « Facturation » avec la clé `FACT`, y créer une spec, ouvrir « Créer un ticket Jira ». | L'aperçu indique `FACT`. Une spec d'un projet sans clé indique `PROJ`. |
| P3 | US1 | Changer de projet courant, relancer l'outil. | La liste et la recherche ne montrent que le projet choisi, qui est toujours courant après le redémarrage. |
| P4 | US1 | Supprimer un projet qui contient des specs, en choisissant « déplacer ». | Les specs sont dans le projet cible. Le dernier projet ne peut pas être supprimé. |
| P5 | US2 | Ajouter la source Antora (URL), demander la génération du contexte. | L'aperçu annonce 30 pages, 1 page en erreur et N tickets. Après accord, la fiche proposée cite ses sources. Rien n'est enregistré avant « Accepter ». |
| P6 | US2 | Modifier la fiche à la main, puis régénérer. | Une proposition apparaît, avec un diff par rapport à la fiche modifiée. « Garder la mienne » ne change rien. |
| P7 | US2 | Démarrer une spec dont le besoin ne nomme pas les acteurs, dans un projet dont le contexte les nomme. | Aucune question sur les acteurs. Les use cases ont des sources « contexte du projet ». |
| P8 | US2 | Régénérer une spec après modification du contexte, puis sans modification. | Une nouvelle version, puis la réutilisation de la version existante (FR-011). |
| P9 | US3 | Supprimer la version 2 d'une spec qui en a 3, puis supprimer une spec liée à `FACT-1`. | Les versions 1 et 3 restent. La spec n'apparaît plus dans la liste ni dans la recherche. Aucune requête n'est faite vers WireMock Jira (SC-005). |
| P10 | US4 | Ouvrir la liste des tickets de `FACT`, page suivante, page précédente. Puis rechercher « facture » (10 specs et 60 tickets). | « 1 à 25 sur 120 », puis « 26 à 50 », puis retour. La liste unique est triée par date sur toutes ses pages, sans doublon. Avec Jira coupé, la page reste affichée et un message est visible. |
| P11 | US5 | Passer une spec à « implémentée », puis tenter une génération, une modification IA, un envoi Jira et une suppression de version. | Tout est refusé avec la raison. « Rétablir » demande une confirmation. « Créer une évolution » ouvre une spec « brouillon » qui cite l'origine. |
| P12 | US5 | Activer l'accès des agents, lancer la passerelle MCP avec un client de test (`e2e/mcp-client.ts`) : `initialize`, `list_projects`, `list_features`, `get_feature`, `set_feature_status`. | Le Markdown est identique à l'export, et les deux changements de statut sont consignés dans le journal. Sont refusés : un appel avec l'ancien secret après régénération, un appel avec un en-tête `Origin`, et READY → IMPLEMENTED (SC-007, SC-008). |

## Contrôles non E2E

- **Évaluation LLM** (`./mvnw -pl backend verify -Peval`) : elle mesure, avec et sans contexte,
  la part de questions déjà couvertes par le contexte (5 % au plus) et la baisse du nombre de
  questions (20 % au moins), SC-002 et SC-003. Elle met `last-run.json` à jour.
- **SC-004a**, manuel : générer le contexte d'un vrai site Antora d'environ 300 pages. Mesurer la
  durée (moins de 10 minutes) et vérifier par échantillon que les références citées existent.
- **Installeur** : depuis l'application installée, déclarer `spec-writer-mcp` dans Claude Code,
  lister les features d'un projet et lire l'une d'elles.
