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
- **Connaissance du projet fictive** (research.md R27) : dans le site Antora de test, trois ADR
  (dont `ADR-002` remplacé par `ADR-005`) et un glossaire ; parmi les tickets `FACT`, `FACT-7`
  fermé « Annuler une facture » et `FACT-9` « à faire » ; un workspace Structurizr
  `e2e/fixtures/c4/` (`model.dsl` commun, `facturation/workspace.dsl` en `extends`, un
  `!include "../infra/…"`, un topic `avoirs-emis`, des déploiements de production sur deux
  infrastructures). Les mêmes fichiers servent au jeu fictif du backend.
- **Modèle d'embedding** : un petit modèle ONNX de test est déposé dans `e2e/fixtures/model/` et
  déclaré comme dossier de modèle, pour ne rien télécharger en E2E.
- **Réponses LLM rejouées** pour `judge-related-issues`, `compare-with-issue` et
  `identify-impact`, et pour les opérations de 001 avec `<connaissance-projet>`
  (`e2e/fixtures/llm/`).

## Scénarios

| # | Story | Étapes | Résultat attendu |
|---|---|---|---|
| P1 | US1 | Démarrer sur une base de la feature 001 qui contient 3 sessions. | Un « Projet par défaut » contient les 3 sessions et toutes leurs versions (SC-001). |
| P2 | US1 | Créer « Facturation » avec la clé `FACT`, y créer une spec, ouvrir « Créer un ticket Jira ». | L'aperçu indique `FACT`. Une spec d'un projet sans clé indique `PROJ`. |
| P3 | US1 | Changer de projet courant, relancer l'outil. | La liste et la recherche ne montrent que le projet choisi, qui est toujours courant après le redémarrage. |
| P4 | US1 | Supprimer un projet qui contient des specs, en choisissant « déplacer ». | Les specs sont dans le projet cible. Le dernier projet ne peut pas être supprimé. |
| P5 | US2 | Déclarer la source Antora, la clé `FACT` et le workspace `facturation/workspace.dsl`, puis suivre l'indexation, la quitter et y revenir. | Indexation en tâche de fond, avec étape, élément en cours, compteurs et temps écoulé, retrouvée après le retour. Fin : « 120 tickets, 31 sections, 3 ADR, 9 éléments d'architecture ». Saisir un besoin pendant l'indexation reste possible et annonce « résultats partiels ». |
| P5a | US2 | Pendant une indexation complète, « Interrompre », puis « Resynchroniser ». | L'interruption rend la main tout de suite et garde ce qui est indexé ; la resynchronisation ne relit que ce qui manque ou a changé. |
| P5b | US2 | Après P5, ouvrir le détail des erreurs, puis l'écran « Journal ». Déclarer ensuite un workspace dont un `!include` est introuvable. | Le détail cite `tarifs-2025.html` (HTTP 500) et `recherche.html` (pas une page Antora). Le workspace en erreur donne le fichier et la ligne, garde le modèle précédent, et le journal ne contient aucun contenu. |
| P5c | US2 | Saisir « Saisir un avoir sur une facture émise ». | Avant les questions : `FACT-7` (fermé, statut relu) avec sa raison, et `FACT-9`. Choisir `FACT-7` : la story rédigée est proposée liée ; à la création du ticket, l'aperçu annonce « relates to FACT-7 » et WireMock reçoit la création puis le lien. |
| P5d | US2 | Sur un autre besoin proche de `FACT-9`, « Comparer », puis « C'est la même ». | Commun et différences affichés côte à côte. La session bascule sur l'import de `FACT-9`, avec le besoin proposé comme modification assistée. |
| P6 | US2 | Rédiger le besoin de P5c jusqu'à la spécification. | Les use cases citent `ADR-005` (jamais `ADR-002`, remplacé). La rubrique « Architecture » cite les modules impactés (dont un commun, signalé) et le flux manquant attendu ; l'échange qui passe par `avoirs-emis` n'est pas signalé. La rubrique « Efforts supplémentaires (déploiement) » cite le module sans instance en production et l'échange entre deux infrastructures. |
| P7 | US2 | Fiche générale « l'ADR-005 n'est plus appliqué », puis régénérer la spécification de P6. | La spécification ne l'applique plus, une nouvelle version est créée ; régénérer sans changement réutilise la version (FR-011). |
| P8 | US2 | Besoin qui contredit `ADR-005`. | L'écart est signalé pendant les questions ; la spécification a une rubrique « Écarts aux ADR » qui nomme l'ADR, sa décision et l'écart. |
| P9 | US3 | Supprimer la version 2 d'une spec qui en a 3, puis supprimer une spec liée à `FACT-1`. | Les versions 1 et 3 restent. La spec n'apparaît plus dans la liste ni dans la recherche. Aucune requête n'est faite vers WireMock Jira (SC-005). |
| P10 | US4 | Ouvrir la liste des tickets de `FACT`, page suivante, page précédente. Puis rechercher « facture » (10 specs et 60 tickets). | « 1 à 25 sur 120 », puis « 26 à 50 », puis retour. La liste unique est triée par date sur toutes ses pages, sans doublon. Avec Jira coupé, la page reste affichée et un message est visible. |
| P11 | US5 | Passer une spec à « implémentée », puis tenter une génération, une modification IA, un envoi Jira et une suppression de version. | Tout est refusé avec la raison. « Rétablir » demande une confirmation. « Créer une évolution » ouvre une spec « brouillon » qui cite l'origine. |
| P12 | US5 | Activer l'accès des agents, lancer la passerelle MCP avec un client de test (`e2e/mcp-client.ts`) : `initialize`, `list_projects`, `list_features`, `get_feature`, `search_knowledge`, `set_feature_status`. | Le Markdown est identique à l'export ; `knowledge` porte la fiche générale, `ADR-005` et les modules impactés avec leurs relations ; `search_knowledge` « avoir » ramène `FACT-7` sans aucune requête vers Jira ni vers le LLM. Les deux changements de statut sont consignés dans le journal. Sont refusés : un appel avec l'ancien secret après régénération, un appel avec un en-tête `Origin`, et READY → IMPLEMENTED (SC-007, SC-008). |

## Contrôles non E2E

- **Jeu de référence fictif** (dans `verify`) : recherche, tickets proches (sans LLM), flux,
  propre ou commun, constats de déploiement sur `backend/src/test/resources/knowledge/`.
- **Jeu de référence réel** (manuel, hors dépôt, `-Dspecwriter.reference-set=<dossier>
  -Dgroups=reference`) : rappel des tickets attendus (≥ 90 %), ADR, flux et efforts (100 %, sans
  faux positif), rédaction du premier essai (100 %), SC-002 à SC-004b. Il compare aussi
  multilingual-e5-small et un modèle sentence-camembert déclaré par dossier.
- **Évaluation LLM** (`./mvnw -pl backend verify -Peval`) : part de questions déjà couvertes par
  la connaissance (5 % au plus) et pertinence des questions (SC-003).
- **SC-004a**, manuel : sur un vrai projet (300 pages, 200 tickets, workspace de 2 000 lignes),
  première indexation en moins de 10 minutes, hors téléchargement du modèle ; synchronisation sans
  changement en moins de 30 s ; tickets proches en moins de 5 s.
- **Mise en route du modèle**, manuel sur macOS, Windows et Linux : premier téléchargement par le
  proxy, puis fonctionnement sans réseau ; tokenizers et onnxruntime chargés depuis le jar
  installé (research.md R19).
- **Installeur** : depuis l'application installée, déclarer `spec-writer-mcp` dans Claude Code,
  lister les features d'un projet, lire l'une d'elles et chercher un ADR.
