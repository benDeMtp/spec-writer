# Connaissance du projet : rédiger des stories conscientes du contexte

Document de conception, 4 novembre 2026. Il remplace l'approche « fiches de contexte générées »
de la feature 002 (`specs/002-projets-contexte-mcp/`, FR-008a, FR-013 à FR-013i, R4, R16) et
servira d'entrée à la spécification Spec Kit qui la reprendra.

## 1. Intention

L'utilisateur veut que spec-writer rédige des stories pertinentes parce qu'il **connaît le projet**,
comme le ferait un membre de l'équipe : il sait si une story existe déjà sur le sujet, quelles
décisions d'architecture s'appliquent, quels modules sont touchés, quels flux manquent et quel
travail de déploiement se cache derrière le besoin.

L'approche actuelle résume d'avance toutes les sources (Jira, Antora) en deux fiches de
20 000 caractères. Elle a échoué plusieurs fois sur le **volume** : délais dépassés, fiches trop
longues, citations à réparer, générations perdues. Le problème vient de la méthode : on comprime
tout le savoir avant de connaître le besoin, alors qu'une story donnée n'en utilise qu'une petite
partie.

La nouvelle approche **indexe** les sources localement et **va chercher**, pour chaque besoin, les
seuls éléments qui le concernent. Le volume du projet ne fait plus jamais grossir un prompt.

## 2. Comment l'utilisateur travaille aujourd'hui (ce que l'outil doit reproduire)

Quand il rédige une story à la main, l'utilisateur consulte :

- **Jira** pour savoir si une story évoque déjà le sujet ;
- **Antora** pour les ADR, l'architecture et le vocabulaire ;
- **Structurizr** (modèle C4 en DSL, dans un dossier local) pour savoir quels modules sont
  impactés, quels flux manquent, et quel effort de déploiement s'ajoute ;
- **SharePoint** (Word, Excel) pour les spécifications métier. **Hors périmètre pour l'instant**,
  mais l'architecture doit pouvoir accueillir plus tard d'autres sources documentaires
  (SharePoint, wiki), dont des documents Office.

Volumes actuels : une vingtaine de tickets Jira, une dizaine de pages Antora, un workspace
Structurizr d'environ 1 800 lignes de DSL. Tous vont croître ; viser quelques milliers de tickets
et de pages sans changement de conception.

## 3. Objectifs

### 3.1 Tickets Jira proches (similarité)

- Dès la saisie du besoin, **avant les questions de clarification**, spec-writer cherche les
  tickets Jira du projet qui traitent du même problème et les montre : clé, titre, **statut**, et
  pourquoi il les juge proches.
- La similarité porte sur le **sens**, pas seulement les mots (« saisir un avoir » doit pouvoir
  rapprocher « annuler une facture »).
- Selon le statut du ticket retenu :
  - **fermé** : l'utilisateur enrichit probablement ce qui a été livré ; spec-writer rédige une
    **nouvelle story liée à l'ancienne** (lien Jira de type « relates to »), qui s'appuie sur son
    contexte sans le recopier ;
  - **ouvert, en cours, à faire** : spec-writer **compare** le besoin et le ticket, côte à côte,
    et dit ce qui est commun et ce qui diffère. L'utilisateur tranche :
    - **c'est la même** : il enrichit le ticket existant (mise à jour d'un ticket lié, déjà
      possible depuis la feature 001) ;
    - **c'est différent** : il crée une nouvelle story ;
  - **rien de proche** : la rédaction continue normalement.

### 3.2 ADR, architecture et vocabulaire (Antora)

- À la rédaction, spec-writer retrouve les **ADR** qui concernent le besoin. Leurs contraintes
  façonnent les use cases et les scénarios, et chaque élément qui en découle **cite l'ADR** (même
  mécanisme de citation que les sources actuelles, avec le lien Antora).
- Si le besoin **va à l'encontre d'un ADR**, la story le dit explicitement, dans une rubrique
  visible : quel ADR, quelle décision, en quoi le besoin s'en écarte. L'outil le signale aussi
  pendant la conversation, pour que l'utilisateur choisisse entre adapter le besoin et assumer
  l'écart.
- Un ADR **remplacé ou obsolète** n'est pas appliqué ; on suit celui qui le remplace.
- Le **vocabulaire** et la **vue d'ensemble de l'architecture** sont utiles à presque toutes les
  stories : ils sont retrouvés comme le reste, mais avec une priorité telle qu'ils accompagnent
  presque toujours la rédaction.

### 3.3 Modèle d'architecture (Structurizr)

- spec-writer lit le modèle C4 du projet depuis un **dossier local** : systèmes, conteneurs
  (modules), composants, leurs descriptions, technologies et tags, leurs **relations**, et les
  **déploiements**.
- À la rédaction, il retrouve les **modules impactés** par le besoin ; la story les cite, comme
  elle cite un ADR.
- Il repère les **flux manquants** : si le besoin suppose un flux entre deux modules et que le
  modèle n'a aucune relation entre eux, la story le dit explicitement. **L'existence d'une relation
  est vérifiée par l'outil dans le modèle**, jamais devinée par le LLM.
  - Un flux **indirect** compte (décidé avec l'utilisateur) : les topics Kafka sont des conteneurs
    du modèle, et un échange A → topic → B est un flux existant entre A et B.
  - *Décision du rédacteur* : l'intermédiaire admis est **un seul** élément de transport (topic,
    file, bus), reconnu par son tag ou son archétype (`topic` dans RelaiV2) ; un chemin plus long
    ne compte pas, sinon tout serait relié.
- Il signale les **efforts supplémentaires de déploiement**, dans une rubrique visible
  « Efforts supplémentaires (déploiement) », chacun justifié par l'élément du modèle qui le
  fonde, sans estimation de charge. **Seul l'environnement de production est regardé** (décidé
  avec l'utilisateur). *Décision du rédacteur* : deux modules sont dans des « infrastructures
  différentes » quand leurs instances de production sont sous des nœuds de déploiement racines
  différents (Azure, ITaaS, Suez, Panama…) ; « déployé à plusieurs endroits » veut dire plusieurs
  instances en production.
  - module nouveau, ou sans aucune instance dans les vues de déploiement : il faudra le déployer ;
  - flux entre modules déployés dans des environnements ou infrastructures différents (par
    exemple Azure et IaaS interne) : ouverture réseau, proxy ou exposition à prévoir ;
  - module déployé à plusieurs endroits : plusieurs déploiements et vérifications ;
  - dépendance à une infrastructure partagée (par exemple un nouveau topic Kafka sur un cluster
    commun), qui relève peut-être d'une autre équipe.
- **Plusieurs workspaces partagent des parties communes.** La story doit savoir si un module
  impacté est **propre au projet** ou **commun** (appartenant à un autre projet ou au paysage du
  SI). Voir 5.4.

### 3.4 Fiche générale de surcharge

- Le projet garde **une seule fiche**, rédigée et tenue à jour **à la main** par l'utilisateur,
  envoyée à chaque rédaction et aux agents de code.
- Elle sert à **surcharger** ce que disent les sources : « l'ADR-007 n'est plus appliqué, voir
  l'ADR-012 », « “dossier” veut dire dossier client », une contrainte écrite nulle part ailleurs.
- **En cas de conflit, la fiche l'emporte** sur Jira, Antora et Structurizr.
- Elle n'est plus générée par l'IA ; elle garde la limite de 20 000 caractères.

### 3.5 Agents de code (MCP)

- La feature lue par un agent porte les **ADR cités**, les **modules impactés** (avec leurs
  relations) et la fiche générale.
- Un outil MCP permet à l'agent de **chercher dans l'index** par sujet pendant qu'il code, au-delà
  de ce que cite la story.

## 4. Non-objectifs

- SharePoint, wiki et documents Office : plus tard (l'architecture les prévoit, sans les
  implémenter).
- Estimer une charge ou un délai : l'outil signale des efforts factuels, il ne chiffre pas.
- Modifier Antora ou le modèle Structurizr (lecture seule). Côté Jira, on garde les écritures de
  la feature 001 (créer ou mettre à jour une story) et on **ajoute** une seule écriture : le lien
  « relates to » entre la nouvelle story et l'ancienne (voir 5.7).
- Générer automatiquement des ADR ou des éléments C4.

## 5. Ce qui a été décidé sur le « comment »

### 5.1 Index local

- Un **index local** dans la base SQLite de l'outil, par projet, contient :
  - les **tickets Jira** du projet (clé, titre, description convertie en Markdown, statut, type,
    date de mise à jour) : c'est le **cache Jira** ;
  - les **sections des pages Antora** (un ADR, une entrée de vocabulaire, une section
    d'architecture…), avec leur lien ;
  - les **éléments du modèle Structurizr** (avec leur description), et les relations et
    déploiements sous une forme interrogeable comme un graphe.
- Deux recherches combinées :
  - **par le sens** : un vecteur (embedding) par élément indexé, comparé à celui du besoin ;
  - **par les termes exacts** : l'index plein texte FTS5 que l'outil possède déjà, pour les codes,
    noms de composants et identifiants.
- **Seuls les quelques candidats retenus** sont envoyés au LLM, pour juger s'ils traitent vraiment
  du même sujet, comparer, ou rédiger. Les appels au LLM restent petits et bornés, quel que soit
  le volume du projet.

### 5.2 Modèle d'embedding

- **Par défaut, en local** : un modèle exécuté dans la JVM au format ONNX
  (`OnnxEmbeddingModel` de LangChain4j), sans réseau ni AI Credit.
- Modèle par défaut recommandé : **`intfloat/multilingual-e5-small`** (118 M de paramètres, MIT,
  entraîné pour la recherche, multilingue : le vocabulaire mêle français et termes techniques
  anglais), en version quantifiée (environ 120 Mo).
- Le modèle est **remplaçable** par un autre modèle ONNX ; l'utilisateur a cité les modèles
  français de type BERT (sentence-camembert, sentence-flaubert). Ceux-ci sont entraînés à comparer
  deux phrases de même nature : bons pour story contre story, moins pour un besoin court contre
  un long ADR.
- **Seul le local est livré** (décidé avec l'utilisateur). Le SDK Copilot n'expose **aucune API
  d'embedding** (vérifié sur `copilot-sdk-java` 1.0.28) : une option « Copilot » ne pourrait être
  qu'un tri des candidats par le LLM. Elle reste possible plus tard : la recherche passe par un
  port qui l'accueillerait, sans la construire maintenant.
- Le choix entre e5 et un modèle CamemBERT se fera sur un essai avec les données réelles du
  projet (voir 7).

### 5.3 Synchronisation

- **Incrémentale** : à l'ouverture d'un besoin, et par un bouton « Resynchroniser ».
  - Jira : seuls les tickets modifiés depuis la dernière synchronisation (`updated >= …`).
  - Antora : les pages dont la date du sitemap a changé.
  - Structurizr : relecture quand un fichier `.dsl` du workspace (ou de ce qu'il inclut ou étend)
    a changé.
- La recherche de similarité tourne sur le cache, sans appel réseau. Seuls les quelques tickets
  retenus comme proches voient leur **statut relu en direct** avant d'être montrés, car c'est ce
  statut qui décide entre lier et comparer.

### 5.4 Lecture de Structurizr

- Source : un **dossier local**. Dans le projet spec-writer, l'utilisateur indique le fichier
  `workspace.dsl` du projet ; le modèle étendu et les inclusions se déduisent des chemins
  relatifs.
- Lecture avec l'**analyseur officiel du DSL** (`com.structurizr:structurizr-dsl`, Java), dans une
  version qui connaît `workspace extends`, `!include`, `!element` et `archetypes`. Il résout les
  inclusions exactement comme Structurizr lite. On **ne lit pas** `workspace.json`, régénéré
  seulement quand Structurizr lite tourne, donc possiblement en retard.
- Organisation observée (`~/Documents/Projets/laposte/C4`) :
  - `model.dsl` à la racine : le **modèle commun** (paysage du SI, groupes, systèmes) ;
  - `NN-projet/workspace.dsl` : `workspace extends ../model.dsl`, enrichit des systèmes communs
    avec `!element` (par exemple des topics Kafka ajoutés à `refpm_insee`, les modules de
    `relaiv2`), inclut des fichiers voisins et l'infrastructure partagée
    (`!include "../infra/…"`, nœuds de déploiement).
- **Propre ou commun** (décidé avec l'utilisateur) :
  1. la propriété `"ownership"` d'un élément, quand elle est renseignée, fait foi (aujourd'hui
     17 éléments, tous dans `veligo-relai.dsl`, avec `"ownership" "relaiv2"`) ;
  2. sinon, ce qui vient du modèle étendu (`model.dsl`) ou d'un fichier de `infra/` est
     **commun** ;
  3. le reste, y compris ce que le workspace ajoute à l'intérieur d'un système commun avec
     `!element`, est **propre au projet**.
  Comme `workspace.dsl` inclut lui-même `../infra/*`, la provenance se juge au fichier source de
  chaque élément, pas au workspace qui l'inclut.

### 5.5 Fiche générale

- Remplace les deux fiches (fonctionnelle et technique) : un seul éditeur Markdown, à la main.
- Transmise à chaque rédaction et via MCP, avec la règle « la fiche l'emporte sur les sources ».

### 5.6 Ce que l'on retire de la feature 002 en cours

Sur la branche `feature/005-projets-mcp` (non poussée), on retire :

- la génération des fiches par l'IA (synthèse par lots, rédaction, réparation des citations,
  condensation envisagée) ;
- les deux fiches, la proposition, son diff et son acceptation fiche par fiche ;
- les questions de clarification posées **après** la génération des fiches (réponses Q-n).

On garde : les projets, les sources de documentation (liens et dossiers Antora, avec leurs motifs
d'erreur précis), la lecture Antora et Jira, la progression suivie avec minuteur et
interruption (réutilisable pour la synchronisation), le journal des erreurs, les délais d'appel
au LLM par opération, et les questions de clarification **sur le besoin** de la feature 001
(elles peuvent désormais s'appuyer sur ce qui a été trouvé : « l'ADR-004 impose X, est-ce
toujours d'actualité pour ce besoin ? »).

Les migrations Flyway déjà appliquées ne sont jamais modifiées : on en ajoute.

*Décision du rédacteur* : les fiches déjà enregistrées ne sont pas perdues. Une migration les
**fusionne** dans la fiche générale (fiche fonctionnelle, puis fiche technique, chacune sous son
titre de niveau 2), que l'utilisateur retouche ensuite. Les versions de spécification existantes
gardent l'empreinte de contexte avec laquelle elles ont été produites ; l'empreinte des nouvelles
versions porte sur la fiche générale et sur les éléments retrouvés.

### 5.7 Parcours avec un ticket proche

*Décisions du rédacteur* :

- **Ticket fermé, nouvelle story liée** : le lien « relates to » est créé au moment de la création
  de la story dans Jira, sous le consentement Jira existant ; l'écran de création annonce le lien
  avant l'envoi. C'est une nouvelle écriture Jira (absente de 001).
- **Ticket ouvert, « c'est la même »** : la session bascule sur le ticket existant, par l'import
  de ticket de la feature 001 ; la rédaction enrichit ce ticket, mis à jour par le flux existant.
- **« C'est différent »** : la rédaction continue en nouvelle story, sans lien imposé.

### 5.8 Synchronisation et attente

*Décisions du rédacteur* :

- La **première indexation** démarre dès que le projet a ses sources (clé Jira, lien Antora,
  `workspace.dsl`), en tâche de fond, avec la progression et l'interruption déjà construites pour
  la génération du contexte. Elle ne bloque jamais la saisie d'un besoin.
- À l'ouverture d'un besoin, la synchronisation incrémentale est courte (quelques éléments) ; la
  recherche utilise l'index tel qu'il est. Si l'index est incomplet (première indexation en cours,
  Jira injoignable), l'écran le dit (« index à 40 %, résultats partiels ») et propose de relancer
  la recherche quand il est prêt.
- Une source injoignable n'empêche pas la rédaction : la story est rédigée avec ce qui est indexé,
  et le manque est signalé.

## 6. Contraintes

- **Anti-objectif principal : devoir s'y reprendre.** Si l'utilisateur doit faire plusieurs
  essais, modifications ou relances pour que ça fonctionne, c'est raté, même si techniquement
  tout marche. Concrètement :
  - aucun réglage préalable : une fois la clé Jira, le lien Antora et le `workspace.dsl`
    renseignés, l'index se construit seul ;
  - pas de boucle fragile avec l'IA : recherche déterministe, appels au LLM courts et bornés ;
    aucune limite de taille ne peut faire échouer une rédaction parce que le projet a grossi ;
  - un échec se comprend du premier coup : élément non indexé, synchronisation en échec, modèle
    introuvable, workspace illisible (erreur d'analyse du DSL avec fichier et ligne) : le motif
    est dit à l'écran et au journal, sans contenu ;
  - la première rédaction est la bonne : tickets proches, ADR et modules pertinents trouvés sans
    reformuler le besoin.
- Outil local : les données restent sur le poste. Rien ne part vers un service autre que Jira
  (lecture, et écriture déjà prévue par 001) et Copilot (rédaction), avec les consentements
  existants.
- Constitution du projet (`.specify/memory/constitution.md`) : architecture hexagonale vérifiée
  par ArchUnit, TDD, journaux sans contenu (FR-025 de 001), accessibilité, golden set.
- Pile : Quarkus 4, Java 25, SQLite, Angular 22, LangChain4j. Une nouvelle dépendance (moteur
  ONNX, `structurizr-dsl`) passe la vérification des dépendances (OWASP) du build.

## 7. Comment on saura que ça marche

- Un **jeu de référence réel**, indispensable : tiré des données du projet (les ~20 tickets, les
  ADR, le workspace RelaiV2), pour tel besoin, tel ticket doit remonter parmi les proches, tel
  ADR doit être cité, tel module doit être déclaré impacté, tel flux doit être signalé manquant.
  Il sert aussi à comparer e5 et sentence-camembert.
  - Ce sont des données La Poste : il **n'entre jamais dans le dépôt** spec-writer (décidé avec
    l'utilisateur). Il vit dans un dossier local, hors dépôt, que les tests lisent s'il existe.
  - Les réponses attendues sont écrites **par l'utilisateur**, qui seul les connaît ; l'outil peut
    préparer les besoins et les candidats pour qu'il n'ait qu'à valider.
  - Le dépôt garde un **jeu fictif**, plus petit, de même forme (tickets, ADR et workspace
    inventés), qui sert de garde-fou dans le build comme le golden set existant.
- Mesurer, sur ce jeu, que la rédaction aboutit **du premier coup**.

## 8. Laissé au constructeur

- Le découpage des pages Antora en sections, et le repérage d'un ADR (chemin, titre, attributs
  AsciiDoc) et de son statut.
- Le stockage des vecteurs dans SQLite et le calcul de similarité ; la pondération entre
  recherche par le sens et plein texte ; les seuils.
- La distribution du modèle d'embedding (fourni avec l'outil ou téléchargé une fois), tant que
  l'utilisateur n'a rien à manipuler.
- La forme exacte des rubriques de la story (écarts aux ADR, flux manquants, efforts de
  déploiement) dans le Markdown et à l'écran.
- Le nom et le contrat de l'outil MCP de recherche.
- L'ordre de livraison, et la façon de retirer proprement ce qui est abandonné de la feature 002.
