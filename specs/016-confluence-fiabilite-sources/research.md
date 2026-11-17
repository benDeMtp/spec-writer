# Research: Confluence, source et date des extraits, fiabilité des sources

Décisions de conception de la feature 016. Chaque entrée : décision, raison, alternatives écartées.

## R1 — Une racine Confluence est une source de documentation du projet

**Décision** : une racine est une `DocSource` de sorte `CONFLUENCE` (`location` = adresse de la
page telle que saisie), à côté des sites Antora et des dossiers de documents. Elle profite de ce que
les sources de documentation ont déjà : réindexation par source (005), aperçu avant indexation,
résumé de lecture, erreurs par source, limite de 10 sources par projet, rattachement des éléments
à leur source (`knowledge_item.source_id`, V19). Les pages deviennent des `DOC_SECTION` (et des
`ADR` si elles en ont la forme, comme pour Antora), découpées en sections par le même découpage
que les pages HTML.

**Raison** : le cycle de vie d'une racine est celui d'un site Antora : déclarée par projet, lue
entière, remplacée à chaque lecture complète. Tout le reste (écran « Connaissance du projet »,
compteurs, réindexation sélective) suit sans code de plus.

**Écarté** : une source de connaissance à part, comme Jira (repère de synchronisation dans
`KnowledgeSource`). Elle aurait dupliqué la réindexation par source et l'écran des sources.

## R2 — Lecture de l'arborescence par CQL, contenu rendu

**Décision** : client REST Confluence Server / Data Center (`/rest/api`, jeton personnel en
`Authorization: Bearer`) :

1. Résolution de l'adresse de la racine : `pageId=<n>` (`viewpage.action`), `/pages/<n>/` (adresses
   récentes), ou `/display/<ESPACE>/<Titre>` résolu par `GET /rest/api/content?spaceKey=&title=`.
2. Racine : `GET /rest/api/content/{id}?expand=version,space`.
3. Descendance : `GET /rest/api/content/search?cql=ancestor=<id> and type=page&expand=version`,
   paginée par `start`/`limit` (100) : identifiant, titre, version (numéro et date). Une seule
   requête couvre toute la profondeur, sans parcourir l'arbre page par page.
4. Contenu d'une page à relire : `GET /rest/api/content/{id}?expand=body.view,version` ; le HTML
   rendu (`body.view`, macros résolues par le serveur) passe par `HtmlText` et `Sections`, comme une
   page Antora.

L'aperçu à l'ajout (FR-004) fait les étapes 1 à 3 avec `limit=1` pour lire `totalSize`, sans rien
indexer.

**Raison** : `ancestor=` est le moyen documenté d'obtenir toute une descendance ; `body.view` donne
le texte tel qu'il est affiché (FR edge case « contenu dynamique »), sans réimplémenter les macros.

**Écarté** : `child/page` récursif (une requête par page, lent sur 500 pages) ; `body.storage`
(XHTML avec macros non résolues) ; l'export PDF ou Word.

## R3 — Réindexation incrémentale par numéro de version

**Décision** : une table `confluence_page` garde, par racine, l'identifiant, le titre et le numéro de
version de chaque page lue. À la réindexation, la liste CQL (étape 3) est comparée : version
inchangée → éléments gardés sans relecture ; version nouvelle ou page nouvelle → relue ; page
absente de la liste → ses éléments sont retirés. Une indexation complète relit tout. Une page
atteinte par deux racines n'est relue qu'une fois par indexation, et indexée sous une seule
référence (son adresse canonique `…/pages/viewpage.action?pageId=<n>`).

**Raison** : SC-002 (aucune relecture sans changement) ; même mécanique que les états de fichiers
des dossiers de documents (`document_file`, 006).

**Écarté** : se fier à `lastModified` seul (un déplacement de page ne change pas sa version mais
change l'appartenance, que la liste CQL donne déjà).

## R4 — Connexion : un serveur pour l'outil, comme Jira

**Décision** : `ConfluenceSettings` (adresse, certificat d'autorité interne, HTTP accepté
explicitement, dernier test, persistance du secret) dans une table `confluence_settings`, jeton
dans le `SecretStore` (trousseau) sous sa propre clé. Test de connexion par
`GET /rest/api/user/current`. Le client applique `ProxySettings` (T139), le configurateur TLS de
Jira (généralisé en `HttpTlsConfigurer`) et un débit borné (8 requêtes par seconde, comme
`JiraThrottle`). Sans serveur déclaré, l'ajout d'une racine est refusé avec un lien vers
Configuration.

**Raison** : clarification du 17/11 (un serveur pour l'outil) ; reprend les choix de sécurité déjà
revus pour Jira (FR-035 de 001 sur HTTP, certificat PEM, trousseau).

**Écarté** : jeton par projet (clarification) ; mot de passe en Basic (les jetons personnels
existent depuis Confluence 7.9 ; un mot de passe au trousseau élargirait le risque).

## R5 — Source et date de chaque extrait

**Décision** : un extrait gagne trois informations, calculées à la lecture, sans réindexation :

- `origin` (`SourceOrigin`) : `JIRA`, `ANTORA`, `DOCUMENTS` (dossier SharePoint), `CONFLUENCE`,
  `ARCHITECTURE`, `SPECIFICATIONS`, `SHEET` (fiche générale), `ATTACHMENT` (document joint). Il se
  déduit de la sorte (`TICKET` → Jira, `ARCH_ELEMENT` → architecture), de `source_id` et du préfixe
  `docs:<source>/` pour les sections et les ADR (sorte de la `DocSource`).
- `updatedAt` : `knowledge_item.source_updated_at`. Il est déjà rempli pour les tickets ; il l'est
  désormais pour les documents d'un dossier (date de modification du fichier, déjà lue pour
  l'incrémental), pour les pages Confluence (`version.when`) et pour les pages Antora quand le
  serveur envoie `Last-Modified` (absent pour un dossier local : date du fichier). Sans date :
  « date inconnue » ; `indexed_at` n'est jamais transmis comme date de mise à jour (FR-012).
- `level` : niveau de fiabilité (R6).

Transmission : attributs `source`, `maj` et `fiabilite` sur `<source>` (résultats de
`search_knowledge`), `<document>`, `<adr>`, `<ticket>`, `<section>`… (connaissance de la génération,
`UserMessages.knowledge`). Affichage : les étapes de recherche de la conversation montrent la
source et la date de chaque résultat. `TurnSource` (persisté avec le message) gagne ces champs,
facultatifs à la lecture.

**Raison** : la date et la source existent déjà en partie dans l'index ; les calculer à la lecture
évite une migration des données et suit un changement de niveau sans réindexation (FR-022).

**Écarté** : stocker la source et le niveau dans `knowledge_item` (réindexation à chaque
changement de niveau) ; mettre la date dans le texte de l'extrait (le modèle la confondrait avec le
contenu).

## R6 — Niveaux de fiabilité : par source, et par racine Confluence

**Décision** : `Reliability` = `REFERENCE`, `INTERPRETATION`, `TO_CONFIRM`. Table
`project_source_reliability (project_id, origin, level)` : une ligne seulement si l'utilisateur a
changé le niveau d'une sorte de source ; défaut `DOCUMENTS` → référence, tout le reste →
interprétation (FR-016, un projet existant n'a aucune ligne). Colonne
`project_doc_source.reliability` (nullable) pour le niveau propre d'une racine Confluence ; sans
valeur, celui de Confluence. Une page atteinte par deux racines prend le plus haut niveau (FR-016a).
La fiche générale, les documents joints et les documents désignés n'ont pas de niveau (ils gardent
leurs règles actuelles) : ils sont transmis sans attribut `fiabilite`.

**Raison** : clarification du 17/11 (option A) ; un réglage par sorte couvre les autres sources
(Jira, Antora, architecture) sans multiplier les sélecteurs.

**Écarté** : niveau par sous-dossier SharePoint (clarification) ; niveau par élément indexé.

## R7 — Places réservées aux extraits de référence (approche B)

**Décision** : `ReservedPlaces` (domaine, pur) appliqué au résultat fusionné d'une recherche :
après le classement actuel (RRF) et la coupe à `MAX_RESULTS` (8), si moins de deux extraits de
référence figurent dans les 8 alors que des candidats de référence existent (parmi les 20 par
source examinés), les mieux classés des candidats de référence absents remplacent les derniers
résultats non référence, dans la limite de deux remplacements ; l'ordre relatif des autres est
gardé. Aucun candidat de référence, ou toutes les sources au même niveau : résultat inchangé
(FR-019, FR-020). Même règle pour la sélection de la connaissance d'une génération
(`SelectKnowledge`), sur ses documents et sections.

**Raison** : décision B du 17/11 ; « pertinent » = candidat de la recherche (hypothèse de la
spec) ; le score n'est jamais modifié, seule la coupe change.

**Écarté** : poids multiplicatif (C, écarté par l'utilisateur) ; quota proportionnel par source
(remplit les places de documents peu pertinents).

## R8 — Arbitrage par la consigne et rubrique « Écarts entre sources »

**Décision** : une consigne réutilisable `prompts/skills/fiabilite-sources.md` (mécanisme 014) dit
comment lire `source`, `maj` et `fiabilite` : même source → le plus récent prime ; une
interprétation ou une source à confirmer qui s'écarte d'une référence → la référence l'emporte, et
l'écart est signalé en citant les deux ; deux références contradictoires → aucune ne l'emporte,
écart signalé ; s'appuyer sur une source à confirmer → le dire. Elle est déclarée dans
`converse-agent`, `revise-specification`, `generate-specification`, `edit-specification`,
`merge-conflict`, `structure-imported-text` et `propose-questions` (qui reçoit la connaissance).

Côté spécification : le schéma des quatre rédactions et de la révision gagne `sourceDeviations`
(`reference` : référence citée, `interpretation` : référence citée, `difference` : ce qui diffère),
comme `adrDeviations` ; le code garde les écarts dont les deux références sont citables (même
résolution que les écarts aux ADR, `DraftValidation`) et ajoute sources et dates d'après l'index.
`MarkdownRenderer` rend la rubrique « Écarts entre sources » après « Écarts aux ADR », omise sans
écart ; elle est reprise à l'écran, à l'export et vers Jira. Champ facultatif à la lecture
(versions et fixtures antérieures), comme `story` (015).

Côté conversation : l'écart est une incohérence de la 010 (deux sources citées) ; la consigne de
l'agent le range parmi elles, sans mécanisme nouveau.

**Raison** : clarification du 17/11 (rubrique dédiée) ; réutilise la mécanique éprouvée des écarts
aux ADR, qui ne fait pas confiance au modèle pour les références.

**Écarté** : écarts dans « Hypothèses » (clarification) ; détection déterministe des contradictions
(impossible sans comprendre le texte).

## R9 — Évaluation réelle

**Décision** : deux volets dans `-Peval` :

- `sourceConflictFlagged` (SC-005, minimum 0,9) : cas de conversation dont le corpus porte une
  demande SharePoint (référence) et une page Confluence (interprétation, plus récente) qui fixe une
  autre règle ; réussite si la réponse suit la référence et cite les deux sources dans une
  incohérence. Un cas inverse (même règle, dates différentes) vérifie qu'aucun écart n'est inventé.
- `referenceRetained` (SC-007, minimum 1,0) : calcul déterministe sur les recherches des cas
  ci-dessus (une référence pertinente figure dans les résultats retenus), sans juge.

Le golden set existant ne change pas ; SC-006 est tenu par les minimums actuels.

**Raison** : SC-005 et SC-007 ; même structure que `ConversationRuns` (corpus, cas, citations).

## R10 — Débit, limites et performance

**Décision** : 8 requêtes par seconde vers Confluence ; 2 000 pages Confluence au plus par projet
(toutes racines), une racine qui dépasse la limite est refusée à l'ajout avec son nombre de pages
(FR-004) ; à l'indexation, `LIMIT_REACHED` comme pour Antora. Un arbre de 500 pages : 5 requêtes de
liste, 500 lectures, soit environ 65 s au débit maximal, sous les 5 minutes de SC-003.

**Écarté** : lecture parallèle (le débit reste le goulot ; le serveur de l'entreprise est partagé).
