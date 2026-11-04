# Feature Specification: Projets, connaissance du projet et accès des agents de code

**Feature Branch**: `005-projets-mcp`

**Created**: 2026-11-03

**Status**: Draft

**Input**: User description: "Je voudrais faire apparaitre la notion de projets. Chaque projet pourra surcharger le projet jira associé. Le but d'avoir un projet et d'avoir un contexte propre au projet afin d'obtimisé la redaction des feature. TU me fera des proposition à ce sujet ( contexte manuel ? Rag ? Contexte généré en même temps que le rag ?) Par la suite je veux qu'un agent de code puisse se connecter à spec-writer en MCP afin de récupérer les feature à implementer pour un projet. Il faut aussi améliorer la gestion des spec locale. Il faut pouvoir supprimer une verison locale ou une spec locale. Il faudrait voir aussi les spec jira de façon paginée"

Cette feature prolonge la feature 001 (`specs/001-local-spec-assistant/spec.md`), dont elle reprend
le vocabulaire : une **spécification** est le résultat d'une session (besoin, questions, versions).
Une **feature à implémenter** est une spécification qu'un agent de code peut récupérer.

## Clarifications

### Session 2026-11-03

- Q: D'où vient le contexte d'un projet ? → A: *(remplacé le 2026-11-04, voir plus bas)* une fiche
  de contexte proposée par l'IA, relue et modifiable par l'utilisateur, sans RAG dans cette feature. Elle est générée à partir d'une ou
  plusieurs documentations du projet (sites produits par Antora, accessibles par un lien, y compris
  local) et des tickets déjà présents dans le projet Jira. Elle peut être régénérée à la demande.
- Q: Que peut faire un agent de code connecté ? → A: lire, et faire passer une feature de « prête
  à implémenter » à « en cours », puis à « implémentée ». Chaque changement est consigné et
  l'utilisateur peut revenir en arrière.
- Q: Quand une feature « en cours » ou « implémentée » reçoit une nouvelle version, que devient
  son statut ? → A: (révisé) une feature « implémentée » ne peut plus recevoir de nouvelle version
  ni être mise à jour ; pour la faire évoluer, l'utilisateur rétablit un statut antérieur après
  confirmation, ou crée une nouvelle spécification à partir d'elle. Pour les autres statuts, une
  nouvelle version ne change pas le statut.
- Q: Comment paginer la liste unique de la recherche (spécifications locales et tickets Jira) ?
  → A: une seule liste paginée par 25, toutes sources confondues, triée par date de dernière
  modification.
- Q: Comment un agent de code prouve-t-il qu'il a le droit de se connecter au serveur MCP local ?
  → A: sans clé à manipuler : l'agent lance la commande locale de spec-writer dédiée à MCP, qui
  s'authentifie auprès de l'outil grâce à un secret rangé dans le dossier de données, lisible par
  le seul compte de l'utilisateur ; régénérer ce secret coupe tous les accès.
- Q: Faut-il limiter le débit des appels à Jira ? → A: oui : jamais plus de 8 requêtes par
  seconde vers Jira, toutes opérations confondues. Au-delà, les requêtes attendent leur tour au
  lieu d'échouer.
- Q: Comment suivre une génération de contexte et comprendre ses erreurs ? → A: la génération
  affiche sa progression (étape, page ou ticket en cours de lecture, compteurs) et peut être
  interrompue. Le détail de ses erreurs est affiché sur la page du contexte. Un écran « Journal »
  montre les avertissements et les erreurs de l'outil, expurgés, conservés dans un fichier tournant
  du dossier de données pour rester visibles après un redémarrage.

### Session 2026-11-04

- Q: Comment rendre le contexte plus précis ? → A: *(remplacé, voir ci-dessous)* deux fiches
  distinctes : une fiche fonctionnelle (vision et objectifs, acteurs, vocabulaire, règles métier,
  contraintes fonctionnelles) et une fiche technique (architecture, contraintes techniques, conventions de
  code, intégrations). Chacune est modifiable, proposée et acceptée séparément.
- Q: Que fait l'assistant quand les sources ne sont pas claires ? → A: *(remplacé, voir
  ci-dessous)* avec sa proposition, il
  pose des questions de clarification (contradictions, informations absentes, termes ambigus).
  L'utilisateur répond ou passe, puis l'IA révise la proposition. Les réponses sont gardées avec
  le projet, citées comme sources et réutilisées aux régénérations.
- Q: Qui reçoit quelle fiche ? → A: *(remplacé, voir ci-dessous)* la rédaction des spécifications
  reçoit les deux fiches ; les agents de code reçoivent la fiche technique avec chaque feature.

### Session 2026-11-04 (suite) : connaissance du projet

Les fiches générées ont buté plusieurs fois sur le volume des sources (délais, fiches trop
longues, générations perdues). Conception validée :
`docs/superpowers/specs/2026-11-04-connaissance-du-projet-design.md`.

- Q: Comment spec-writer connaît-il le projet ? → A: il indexe localement les sources du projet
  (tickets Jira, pages Antora, modèle d'architecture Structurizr) et va chercher, pour chaque
  besoin, les seuls éléments qui le concernent. Rien n'est résumé d'avance.
- Q: Que devient le contexte ? → A: une seule fiche générale, rédigée à la main, qui surcharge les
  sources en cas de conflit. Les fiches générées, leur proposition et les questions Q-n sont
  retirées ; les fiches déjà enregistrées sont fusionnées dans la fiche générale.
- Q: Que fait l'outil d'un ticket Jira proche du besoin ? → A: il le montre avant les questions,
  avec son statut. Fermé : nouvelle story liée à l'ancienne (« relates to »). Ouvert : comparaison
  côte à côte ; l'utilisateur tranche entre enrichir ce ticket et créer une nouvelle story.
- Q: Que faire des ADR ? → A: ils sont cités ; leurs contraintes façonnent les use cases ; un
  écart à un ADR est dit explicitement. Un ADR remplacé n'est pas appliqué.
- Q: Que faire du modèle d'architecture ? → A: il est lu depuis un dossier local (workspace
  Structurizr, avec ce qu'il étend et inclut). La story cite les modules impactés, signale les
  flux manquants et les efforts de déploiement supplémentaires en production. Elle distingue ce qui
  est propre au projet de ce qui est commun.
- Q: Quand un flux existe-t-il ? → A: relation directe, ou indirecte par un seul intermédiaire de
  transport (topic, file, bus).
- Q: Comment distinguer propre et commun ? → A: la propriété `ownership` d'un élément quand elle
  est renseignée ; sinon ce qui vient du modèle commun ou de l'infrastructure partagée est commun,
  le reste est propre au projet.
- Q: Où se fait la recherche par le sens ? → A: en local seulement, avec un modèle remplaçable
  exécuté sur le poste ; aucun AI Credit. Une option passant par Copilot est écartée pour
  l'instant (Copilot ne fournit pas de vecteurs).
- Q: Comment vérifier la pertinence ? → A: un jeu de référence réel, tiré des données du projet,
  gardé hors du dépôt, et un jeu fictif de même forme dans le dépôt.
- Q: Quel accès à Jira ? → A: l'index sert de cache ; seuls les tickets modifiés sont relus, et le
  statut des tickets proches est relu en direct avant d'être montré.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ranger ses spécifications par projet (Priority: P1)

L'utilisateur travaille sur plusieurs produits. Il crée un projet par produit, lui donne un nom et,
s'il le souhaite, la clé du projet Jira qui lui correspond, différente du projet Jira par défaut de
la configuration. Chaque nouvelle spécification est créée dans un projet. La liste des
spécifications, la recherche et les actions Jira (création de ticket, import, recherche) se font
dans le projet choisi.

**Why this priority**: le projet est le socle des autres stories : le contexte, la liste paginée
des tickets Jira et l'accès des agents s'y rattachent.

**Independent Test**: créer deux projets dont un avec une clé Jira propre, créer une spécification
dans chacun, puis créer un ticket Jira depuis chacune : chaque ticket part dans le bon projet Jira,
et chaque liste ne montre que les spécifications de son projet.

**Acceptance Scenarios**:

1. **Given** aucun projet n'a encore été créé et des spécifications existent, **When**
   l'utilisateur ouvre l'outil après la mise à jour, **Then** toutes ses spécifications existantes
   se trouvent dans un projet « Projet par défaut », sans perte.
2. **Given** un projet « Facturation » avec la clé Jira `FACT`, alors que le projet Jira par défaut
   est `PROJ`, **When** l'utilisateur crée un ticket depuis une spécification de « Facturation »,
   **Then** l'aperçu indique le projet `FACT` et le ticket y est créé.
3. **Given** un projet sans clé Jira, **When** l'utilisateur crée un ticket depuis une de ses
   spécifications, **Then** le projet Jira par défaut de la configuration est utilisé.
4. **Given** deux projets, **When** l'utilisateur choisit l'un d'eux, **Then** la liste des
   spécifications et la recherche ne portent que sur ce projet, et ce choix est conservé au
   prochain lancement.
5. **Given** une spécification rangée dans le mauvais projet, **When** l'utilisateur la déplace
   vers un autre projet, **Then** elle apparaît dans ce projet avec toutes ses versions.
6. **Given** un projet qui contient des spécifications, **When** l'utilisateur le supprime, **Then**
   l'outil lui demande de choisir entre déplacer ces spécifications vers un autre projet ou les
   supprimer avec le projet, puis demande confirmation.

---

### User Story 2 - spec-writer connaît le projet (Priority: P1)

Quand il rédige une story à la main, l'utilisateur vérifie dans Jira si une story évoque déjà le
sujet, lit dans Antora les ADR, l'architecture et le vocabulaire, et consulte le modèle
d'architecture Structurizr pour savoir quels modules sont touchés, quels flux manquent et quel
travail de déploiement s'ajoute. spec-writer fait ce travail à sa place : il indexe les sources du
projet sur le poste, retrouve pour chaque besoin les tickets, ADR et modules qui le concernent, et
s'en sert pour rédiger une story juste du premier coup. Une fiche générale, écrite à la main, lui
dit ce que les sources ne disent pas ou disent mal.

**Why this priority**: c'est la raison d'être des projets selon le besoin exprimé : optimiser la
rédaction des features.

**Independent Test**: sur un projet dont l'index contient un ticket fermé sur le sujet, un ADR
applicable et un workspace d'architecture, saisir un besoin proche : le ticket est montré avant les
questions avec son statut, la story rédigée cite l'ADR et les modules impactés, signale le flux
manquant et l'effort de déploiement attendus, et la story est proposée liée au ticket fermé.

**Acceptance Scenarios**:

1. **Given** un projet avec sa clé Jira, le lien de sa documentation Antora et son workspace
   Structurizr, **When** l'utilisateur enregistre ces sources, **Then** l'indexation démarre en
   tâche de fond, sa progression est visible, et l'utilisateur peut saisir un besoin sans attendre.
2. **Given** un ticket fermé « Annuler une facture » dans l'index, **When** l'utilisateur saisit le
   besoin « saisir un avoir sur une facture émise », **Then** le ticket est montré avant les
   questions avec sa clé, son titre, son statut relu dans Jira et la raison du rapprochement ; la
   story rédigée est proposée liée à ce ticket (« relates to »), et le lien est annoncé avant
   l'envoi vers Jira.
3. **Given** un ticket « à faire » proche du besoin, **When** l'utilisateur l'examine, **Then**
   l'outil compare le besoin et le ticket côte à côte (commun, différent) ; si l'utilisateur juge
   que c'est la même story, la session bascule sur ce ticket pour l'enrichir ; sinon une nouvelle
   story est rédigée.
4. **Given** un ADR qui impose un traitement asynchrone, **When** la story est rédigée, **Then** ses
   use cases n'annoncent pas de réponse immédiate et les éléments concernés citent l'ADR avec son
   lien.
5. **Given** un besoin qui va à l'encontre d'un ADR, **When** la story est rédigée, **Then** une
   rubrique visible nomme l'ADR, sa décision et l'écart, et l'outil l'a signalé pendant les
   questions pour que l'utilisateur adapte le besoin ou assume l'écart.
6. **Given** un ADR remplacé par un autre, **When** le besoin le concerne, **Then** seul l'ADR en
   vigueur est appliqué et cité.
7. **Given** un besoin qui suppose un échange entre deux modules sans relation dans le modèle, ni
   directe ni par un topic, **When** la story est rédigée, **Then** elle cite les modules impactés
   et signale le flux manquant ; un échange qui passe déjà par un topic n'est pas signalé.
8. **Given** un besoin qui touche un module sans instance en production, ou un flux entre deux
   modules déployés sur des infrastructures différentes en production, **When** la story est
   rédigée, **Then** une rubrique « Efforts supplémentaires (déploiement) » le signale, avec
   l'élément du modèle qui le justifie, sans estimation de charge.
9. **Given** un module impacté qui appartient au modèle commun, **When** la story le cite, **Then**
   elle indique qu'il n'est pas propre au projet.
10. **Given** une fiche générale qui dit « l'ADR-007 n'est plus appliqué », **When** le besoin
   concerne l'ADR-007, **Then** la story suit la fiche et ne l'applique pas.
11. **Given** une indexation en cours ou Jira injoignable, **When** l'utilisateur saisit un besoin,
   **Then** l'outil dit que les résultats sont partiels, rédige avec ce qui est indexé, et propose
   de relancer la recherche quand l'index est prêt.
12. **Given** une indexation terminée avec des pages ou des fichiers en erreur, **When**
   l'utilisateur consulte le détail ou l'écran « Journal », **Then** il voit pour chacun son
   adresse ou son chemin et le motif précis (page en erreur HTTP, dossier introuvable, erreur
   d'analyse du workspace avec fichier et ligne…), sans aucun contenu.
13. **Given** un projet indexé, **When** un ticket est modifié dans Jira ou une page Antora change,
   **Then** la synchronisation suivante ne relit que ce qui a changé.

---

### User Story 3 - Supprimer une version ou une spécification locale (Priority: P2)

L'utilisateur fait le ménage : il supprime une version ratée d'une spécification, ou une
spécification entière dont il n'a plus besoin. Rien n'est supprimé dans Jira.

**Why this priority**: les versions et spécifications d'essai s'accumulent et encombrent la liste,
la recherche et ce que verront les agents.

**Independent Test**: supprimer la version 2 d'une spécification qui en a trois, puis supprimer
une autre spécification entière : elles n'apparaissent plus nulle part dans l'outil, et les
tickets Jira liés sont inchangés.

**Acceptance Scenarios**:

1. **Given** une spécification avec les versions 1, 2 et 3, **When** l'utilisateur supprime la
   version 2 et confirme, **Then** les versions 1 et 3 restent, avec leurs numéros, et la
   version 2 n'apparaît plus dans l'historique, la comparaison, la recherche ni l'export.
2. **Given** une spécification liée à un ticket Jira, **When** l'utilisateur supprime la version
   envoyée vers Jira, **Then** l'outil le prévient que Jira n'est pas modifié, puis recalcule
   l'état de synchronisation à partir de la dernière version restante.
3. **Given** une spécification qui n'a plus qu'une version, **When** l'utilisateur supprime cette
   version, **Then** la session garde son besoin et ses réponses et propose de générer à nouveau.
4. **Given** une spécification liée à un ticket Jira, **When** l'utilisateur la supprime et
   confirme en voyant son nom, **Then** la session, ses réponses et toutes ses versions sont
   supprimées, le ticket Jira est inchangé, et la spécification disparaît de la liste et de la
   recherche.
5. **Given** la boîte de confirmation d'une suppression, **When** l'utilisateur annule, **Then**
   rien n'est supprimé.

---

### User Story 4 - Parcourir les tickets Jira page par page (Priority: P2)

L'utilisateur parcourt les tickets du projet Jira associé à son projet, du plus récemment modifié
au plus ancien, page par page, avec ou sans mot-clé, sans attendre le chargement de tous les
tickets.

**Why this priority**: un projet Jira compte souvent des centaines de tickets ; sans pagination,
la liste est lente, tronquée ou inutilisable.

**Independent Test**: sur un projet Jira de 120 tickets, afficher la première page, aller à la
page suivante puis à la précédente : chaque page s'affiche vite, sans doublon ni ticket manquant.

**Acceptance Scenarios**:

1. **Given** un projet Jira de 120 tickets, **When** l'utilisateur ouvre la liste des tickets du
   projet, **Then** il voit les 25 plus récemment modifiés, le nombre total de tickets et sa
   position (« 1 à 25 sur 120 »).
2. **Given** la première page, **When** l'utilisateur demande la page suivante, **Then** les
   tickets 26 à 50 s'affichent, et la page précédente reste accessible.
3. **Given** une recherche par mot-clé qui trouve 10 spécifications locales et 60 tickets Jira,
   **When** l'utilisateur la lance, **Then** il voit une seule liste de 25 résultats, toutes sources
   confondues, du plus récemment modifié au plus ancien, et la page suivante continue dans le même
   ordre ; un élément « les deux » n'apparaît qu'une fois.
4. **Given** Jira qui ne répond pas, **When** l'utilisateur change de page, **Then** la page
   affichée reste en place et un message propose de réessayer.

---

### User Story 5 - Un agent de code récupère les features à implémenter (Priority: P3)

Le développeur connecte son agent de code (dans son IDE ou son terminal) à spec-writer, sur son
poste. L'agent liste les projets, récupère les features à implémenter d'un projet, puis lit le
contenu complet de l'une d'elles (use cases, scénarios d'acceptation, exigences, détails, contexte
du projet) pour l'implémenter.

**Why this priority**: l'utilisateur l'annonce comme une étape suivante ; elle s'appuie sur les
projets et sur une gestion propre des spécifications.

**Independent Test**: activer l'accès des agents, déclarer la commande MCP de spec-writer dans un
agent de code,
lui demander les features à implémenter d'un projet puis le contenu de l'une d'elles : la liste
correspond à ce que montre l'outil et le contenu est identique à l'export Markdown.

**Acceptance Scenarios**:

1. **Given** l'accès des agents désactivé (réglage par défaut), **When** un agent tente de se
   connecter, **Then** la connexion est refusée.
2. **Given** l'accès activé, **When** un agent de code lance la commande MCP de spec-writer sous le
   compte de l'utilisateur, **Then** il peut lister les projets et les features à implémenter d'un
   projet, sans que l'utilisateur ait copié de clé.
3. **Given** une feature à implémenter, **When** l'agent en demande le contenu, **Then** il reçoit
   la dernière version de la spécification, identique à ce que montre l'outil, avec la fiche
   générale du projet, les ADR cités et les modules impactés avec leurs relations ; il peut aussi
   chercher dans l'index du projet un ADR ou un module par sujet.
4. **Given** l'utilisateur a régénéré le secret d'accès des agents, **When** un programme
   réutilise l'ancien secret, **Then** la connexion est refusée ; la commande MCP, elle, lit le
   nouveau secret et continue de fonctionner.
5. **Given** un programme qui joint l'outil sans passer par la commande MCP, ou une page web,
   **When** il tente d'appeler le serveur MCP, **Then** l'appel est refusé.
6. **Given** des accès d'agents, **When** l'utilisateur consulte le journal des accès, **Then** il
   voit, pour chaque accès, la date, l'agent et la feature consultée, sans le contenu.
7. **Given** une feature « implémentée », **When** l'utilisateur ou un agent tente de la modifier
   (nouvelle version, modification par l'IA, import ou envoi Jira), **Then** l'action est refusée
   avec la raison ; l'utilisateur peut rétablir un statut antérieur après confirmation, ou créer
   une évolution dans une nouvelle spécification.
8. **Given** un agent connecté et une feature « prête à implémenter », **When** l'agent la passe à
   « en cours » puis à « implémentée », **Then** l'outil affiche ces statuts, le journal des accès
   consigne les deux changements, et l'utilisateur peut rétablir le statut précédent.
9. **Given** un agent connecté, **When** il tente de modifier le contenu d'une feature ou de
   passer une feature « brouillon » à « en cours », **Then** l'opération est refusée.

---

### Edge Cases

- Deux projets portent le même nom : refusé, les noms de projet sont uniques (sans tenir compte de
  la casse).
- La clé Jira d'un projet n'existe pas ou n'est pas accessible avec le jeton : le test de la clé
  le signale à l'enregistrement, et toute action Jira du projet affiche l'erreur de FR-028 (001).
- Le projet Jira par défaut est modifié dans la configuration : les projets sans clé propre
  suivent ce changement, les autres non.
- La fiche générale dépasse la taille maximale : l'enregistrement est refusé avec la limite
  indiquée.
- La documentation, un ticket ou une description du modèle d'architecture contient des consignes
  adressées à l'IA : comme le besoin, c'est une donnée, jamais une consigne (FR-026 de 001).
- La documentation change pendant l'indexation, ou une page renvoie une erreur : l'indexation
  continue avec le reste et liste ce qui n'a pas été indexé.
- Le lien de documentation redirige vers un autre site : la redirection n'est pas suivie.
- Le projet n'a aucune source : aucune recherche n'est faite ; la rédaction se fait avec la fiche
  générale seule, comme en 001.
- La fiche générale contient « ignore tes instructions » : elle reste une donnée (FR-026 de 001).
- La fiche générale ou l'index change pendant un questionnaire en cours : les questions suivantes
  utilisent la nouvelle connaissance ; les réponses déjà données restent.
- Le workspace d'architecture ne s'analyse pas (fichier inclus introuvable, erreur de syntaxe) :
  l'index garde le dernier modèle lu, et l'erreur est signalée avec le fichier et la ligne.
- Deux ADR se contredisent, ou un ADR et la fiche générale : la fiche l'emporte ; entre deux ADR,
  celui qui remplace l'autre s'applique, et à défaut la contradiction est signalée comme les
  autres.
- Un ticket proche a été supprimé dans Jira depuis la dernière synchronisation : il n'est pas
  montré, et il est retiré de l'index.
- Le modèle de recherche par le sens est absent ou illisible : la recherche se fait par les termes
  seuls, et l'outil le signale avec la cause.
- Un même module est cité sous deux noms (modèle et documentation) : l'élément du modèle fait foi.
- Une suppression est demandée pendant une génération ou une proposition de l'IA en cours sur la
  même spécification : elle est refusée tant que l'opération n'est pas terminée.
- Une version supprimée était ouverte dans un autre onglet : l'onglet affiche « version supprimée »
  au prochain accès.
- Des tickets Jira sont créés ou modifiés entre deux pages : un ticket peut apparaître deux fois
  ou être sauté ; l'utilisateur peut recharger la liste depuis la première page.
- Un agent demande une feature supprimée ou déplacée entre-temps : il reçoit « introuvable », sans
  autre détail.
- L'outil n'est pas lancé : la commande MCP indique à l'agent que spec-writer doit être ouvert ;
  aucun service ne tourne en arrière-plan pour lui.
- Le ticket Jira lié à une feature implémentée est modifié dans Jira : l'outil le signale à
  l'affichage, mais n'importe rien tant que la feature est verrouillée ; une évolution peut
  partir de la version Jira.
- Une génération ou une proposition de l'IA est en cours quand un agent passe la feature à
  « implémentée » : le résultat n'est pas enregistré et l'utilisateur en est averti.
- L'accès des agents est désactivé alors qu'un agent est connecté : ses requêtes suivantes sont
  refusées.

## Requirements *(mandatory)*

### Functional Requirements

**Projets**

- **FR-001**: L'utilisateur DOIT pouvoir créer un projet avec un nom (unique, sans tenir compte de
  la casse, 80 caractères au maximum), une description facultative et une clé de projet Jira
  facultative.
- **FR-002**: L'utilisateur DOIT pouvoir lister, renommer et modifier ses projets, et en supprimer
  un après avoir choisi de déplacer ses spécifications vers un autre projet ou de les supprimer
  avec lui, puis confirmé. Le dernier projet restant ne peut pas être supprimé.
- **FR-003**: Chaque spécification DOIT appartenir à exactement un projet. Lors de la mise à jour,
  les spécifications existantes DOIVENT être rangées dans un projet « Projet par défaut » créé
  automatiquement, sans perte de données.
- **FR-004**: Une nouvelle spécification DOIT être créée dans le projet courant ; l'utilisateur
  DOIT pouvoir déplacer une spécification vers un autre projet, avec toutes ses versions.
- **FR-005**: Le projet courant DOIT être choisi par l'utilisateur et conservé d'un lancement à
  l'autre ; la liste des spécifications et la recherche locale ne portent que sur ce projet.
- **FR-006**: Toute action Jira liée à un projet (création de ticket, recherche, liste des tickets,
  import) DOIT utiliser la clé Jira du projet si elle est renseignée, et sinon le projet Jira par
  défaut de la configuration (FR-031 de 001). L'adresse du serveur et le jeton restent communs à
  tous les projets.
- **FR-007**: La clé Jira d'un projet DOIT être vérifiée auprès de Jira à l'enregistrement quand
  Jira est configuré ; une clé introuvable est signalée sans bloquer l'enregistrement.

**Connaissance du projet**

- **FR-008**: Chaque projet DOIT avoir une **fiche générale** : un texte en Markdown de 20 000
  caractères au maximum, rédigé et modifié à la main à tout moment, avec sa date de dernière
  modification. Elle dit ce que les sources ne disent pas ou disent mal (vocabulaire, décision qui
  n'est plus appliquée, contrainte écrite nulle part ailleurs). En cas de conflit, elle l'emporte
  sur les tickets, la documentation et le modèle d'architecture.
- **FR-008a**: *(retirée le 2026-11-04 : deux fiches générées)* À la mise à jour, les fiches
  fonctionnelle et technique déjà enregistrées DOIVENT être fusionnées dans la fiche générale
  (fonctionnelle puis technique, chacune sous son titre), sans perte.
- **FR-009**: La fiche générale et les éléments retrouvés pour le besoin (FR-032 à FR-041)
  DOIVENT être pris en compte par toutes les opérations de l'IA d'une spécification du projet :
  questions, réévaluation, génération, modification assistée, import et fusion. Une question dont
  la réponse figure dans la fiche ou dans les éléments retrouvés NE DOIT PAS être posée (principe I
  de la constitution).
- **FR-010**: Tout élément de la spécification qui découle de la connaissance du projet DOIT citer
  sa source : « contexte du projet » (fiche générale) avec l'extrait, ticket Jira avec sa clé,
  page de documentation ou ADR avec son lien, élément du modèle d'architecture avec son nom
  (traçabilité, FR-011 de 001). Une contradiction entre ces sources et le besoin ou une réponse
  DOIT être signalée et tranchée comme les autres contradictions.
- **FR-011**: La fiche générale et les éléments retrouvés (identifiant et date de mise à jour de
  chacun) DOIVENT entrer dans les entrées de la génération : si l'un change, une nouvelle version
  est créée ; sinon la version existante est réutilisée (FR-014 et FR-018 de 001). Chaque version
  DOIT indiquer la connaissance utilisée et de quand elle date.
- **FR-012**: La fiche générale et le contenu indexé DOIVENT être traités comme des données et
  jamais comme des consignes (FR-026 de 001), et NE DOIVENT jamais figurer dans les journaux
  (FR-025 de 001). Seuls les éléments retrouvés pour un besoin, jamais l'index entier, sont
  envoyés à GitHub Copilot, dans le cadre du consentement existant (FR-024 de 001).
- **FR-013**: L'utilisateur DOIT pouvoir déclarer pour un projet ses sources : la clé Jira
  (FR-001), une ou plusieurs documentations Antora (lien `http` ou `https`, y compris sur son poste,
  ou dossier local du site produit ; seules les pages de ce site sont lues, sans suivre les liens
  vers d'autres sites) et le fichier de workspace Structurizr du projet, dans un dossier local.
- **FR-013a**: *(retirée le 2026-11-04 : proposition des fiches par l'IA ; remplacée par FR-032)*
- **FR-013b**: *(retirée le 2026-11-04 : aperçu de l'envoi d'une génération de fiches ; voir
  FR-012)*
- **FR-013c**: *(retirée le 2026-11-04 : régénération des fiches ; voir FR-008)*
- **FR-013d**: L'indexation DOIT rester possible si une partie des sources est inaccessible :
  l'outil indexe le reste et liste ce qui n'a pas été indexé, avec le motif précis (page en
  erreur, site introuvable, site qui n'est pas produit par Antora, dossier absent ou illisible,
  sources AsciiDoc au lieu du site produit, workspace qui ne s'analyse pas). La rédaction reste
  possible avec ce qui est indexé.
- **FR-013e**: La lecture d'une documentation DOIT être bornée à 1 000 pages par source ; l'outil
  indique quand la borne est atteinte. Tous les tickets du projet Jira sont indexés, dans la limite
  de débit de FR-022a.
- **FR-013f**: Pendant une indexation, l'outil DOIT afficher l'étape en cours (tickets Jira,
  documentation, modèle d'architecture), l'élément en cours et l'avancement (éléments traités sur
  le total), mis à jour au moins toutes les 2 secondes, et le temps écoulé. L'utilisateur DOIT
  pouvoir quitter la page puis la retrouver en cours, et l'interrompre : ce qui a déjà été indexé
  est gardé. Une seule indexation à la fois par projet.
- **FR-013g**: À la fin d'une indexation, réussie, interrompue ou en échec, l'outil DOIT afficher
  le détail de ses erreurs : pour chaque source, page, ticket ou fichier non indexé, son adresse,
  sa clé ou son chemin, le motif et le code HTTP s'il y en a un, et la cause d'un échec. Ce détail
  ne contient aucun contenu de page, de ticket ni de modèle.
- **FR-013h**: *(retirée le 2026-11-04 : questions de clarification sur les fiches ; les
  questions sur le besoin de 001 demeurent, FR-009)*
- **FR-013i**: *(retirée le 2026-11-04 : réponses Q-n citables)*
- **FR-032**: L'outil DOIT tenir, sur le poste et par projet, un **index** des sources déclarées :
  les tickets du projet Jira (clé, titre, description, type, statut, date de mise à jour), les
  sections des pages de documentation (un ADR, une entrée de vocabulaire, une section
  d'architecture…) avec leur lien, et le modèle d'architecture (éléments, relations,
  déploiements). Il DOIT pouvoir y chercher les éléments proches d'un besoin **par le sens** (un
  besoin et un ticket sans mot commun doivent pouvoir se rapprocher) et **par les termes exacts**
  (codes, noms de composants). La recherche par le sens se fait sur le poste, sans réseau ni AI
  Credit ; le modèle qui la permet est remplaçable par l'utilisateur.
- **FR-033**: L'index DOIT se construire seul, en tâche de fond, dès qu'une source est déclarée,
  puis se tenir à jour **par synchronisation incrémentale** : à l'ouverture d'un besoin et à la
  demande (« Resynchroniser »). Seuls les tickets modifiés depuis la dernière synchronisation, les
  pages dont la date a changé et les fichiers du workspace modifiés sont relus. Une
  synchronisation ne bloque jamais la saisie d'un besoin ; un index incomplet est annoncé
  (« résultats partiels ») et la recherche peut être relancée quand il est prêt.
- **FR-034**: Dès la saisie d'un besoin et **avant les questions de clarification**, l'outil DOIT
  montrer les tickets Jira du projet qui traitent du même problème, 5 au plus, avec leur clé,
  titre, statut et la raison du rapprochement. Le statut de ces tickets DOIT être relu dans Jira
  avant d'être montré ; s'il ne peut pas l'être, le statut indexé est montré avec sa date.
- **FR-035**: Selon le ticket retenu par l'utilisateur :
  - **ticket fermé** : l'outil DOIT rédiger une nouvelle story, qui s'appuie sur le contexte de
    l'ancienne sans le recopier, et la créer dans Jira liée à l'ancienne par un lien « relates
    to » ; le lien est annoncé dans l'aperçu avant l'envoi, sous le consentement Jira existant ;
  - **ticket non fermé** : l'outil DOIT comparer le besoin et le ticket (ce qui est commun, ce qui
    diffère). Si l'utilisateur juge que c'est la même story, la session bascule sur ce ticket par
    l'import de 001 pour l'enrichir ; sinon une nouvelle story est rédigée ;
  - **aucun ticket retenu** : la rédaction continue normalement.
- **FR-036**: L'outil DOIT retrouver les **ADR** qui concernent le besoin. Leurs contraintes
  DOIVENT façonner les use cases et les scénarios, et chaque élément qui en découle DOIT citer
  l'ADR (FR-010). Un ADR remplacé ou obsolète NE DOIT PAS être appliqué ; celui qui le remplace
  l'est.
- **FR-037**: Si le besoin va à l'encontre d'un ADR, l'outil DOIT le signaler pendant les questions,
  pour que l'utilisateur adapte le besoin ou assume l'écart, et la story DOIT le dire dans une
  rubrique visible : l'ADR, sa décision, l'écart.
- **FR-038**: L'outil DOIT lire le **modèle d'architecture** du projet depuis le fichier de
  workspace déclaré, avec le modèle qu'il étend et les fichiers qu'il inclut, comme le fait
  Structurizr. Il DOIT distinguer, pour chaque élément, s'il est **propre au projet** ou
  **commun** : la propriété `ownership` de l'élément quand elle est renseignée ; sinon un élément
  défini dans le modèle étendu ou dans un fichier d'infrastructure partagée est commun, et le reste
  (y compris ce que le workspace ajoute à l'intérieur d'un système commun) est propre au projet.
- **FR-039**: La story DOIT citer les **modules impactés** par le besoin, en indiquant pour chacun
  s'il est propre au projet ou commun.
- **FR-040**: Si le besoin suppose un échange entre deux modules sans **flux** dans le modèle, la
  story DOIT le signaler comme flux manquant. Un flux existe s'il y a une relation directe, ou une
  relation indirecte par **un seul** intermédiaire de transport (topic, file, bus). Cette
  vérification DOIT être faite par l'outil sur le modèle, jamais laissée à l'IA.
- **FR-041**: La story DOIT signaler, dans une rubrique « Efforts supplémentaires (déploiement) »,
  ce que le modèle de déploiement de **production** révèle, chacun avec l'élément qui le justifie
  et sans estimation de charge : module sans instance en production ; flux entre modules dont les
  instances de production sont sous des infrastructures racines différentes ; module déployé en
  plusieurs instances en production ; dépendance à une infrastructure partagée. Ces constats
  DOIVENT être établis par l'outil sur le modèle.
- **FR-042**: Le vocabulaire et la vue d'ensemble de l'architecture de la documentation DOIVENT
  accompagner chaque rédaction, en priorité sur les autres éléments retrouvés.
- **FR-043**: Une erreur de la connaissance du projet (index incomplet, source injoignable, modèle
  de recherche absent) NE DOIT jamais faire échouer une rédaction : la story est rédigée avec ce
  qui est disponible, et le manque est signalé.

**Suppression locale**

- **FR-014**: L'utilisateur DOIT pouvoir supprimer une version d'une spécification, après une
  confirmation qui nomme la spécification et le numéro de version. Les numéros des autres versions
  NE DOIVENT PAS changer.
- **FR-015**: La suppression de la dernière version restante DOIT conserver la session (besoin,
  réponses) et permettre une nouvelle génération.
- **FR-016**: L'utilisateur DOIT pouvoir supprimer une spécification entière (session, réponses,
  versions), après une confirmation qui nomme la spécification (complète FR-017 de 001).
- **FR-017**: Une suppression locale NE DOIT jamais modifier Jira. Si la spécification ou la
  version supprimée est liée à un ticket, l'outil DOIT le dire dans la confirmation ; la
  suppression d'une spécification retire son lien, celle d'une version recalcule l'état de
  synchronisation à partir de la dernière version restante.
- **FR-018**: Un élément supprimé DOIT disparaître immédiatement de la liste, de l'historique, de
  la comparaison, de la recherche, de l'export et de l'accès des agents. La suppression est
  définitive.
- **FR-019**: Une suppression DOIT être refusée tant qu'une génération ou une proposition de l'IA
  est en cours sur la même spécification.

**Tickets Jira paginés**

- **FR-020**: L'utilisateur DOIT pouvoir parcourir les tickets du projet Jira associé au projet
  courant, sans mot-clé, du plus récemment modifié au plus ancien.
- **FR-021**: La liste des tickets du projet Jira DOIT être paginée par 25, avec page suivante et
  page précédente, la position (« 26 à 50 sur 120 ») et le total quand Jira le fournit. Seule la
  page affichée est demandée à Jira.
- **FR-021a**: Les résultats d'une recherche par mot-clé DOIVENT rester une liste unique (FR-041 de
  001), paginée par 25 toutes sources confondues et triée par date de dernière modification, de la
  plus récente à la plus ancienne. Un élément « les deux » n'y apparaît qu'une fois, et l'ordre est
  le même d'une page à l'autre. L'outil ne demande à Jira que les tickets nécessaires à la page
  affichée.
- **FR-022**: Une erreur de Jira pendant un changement de page DOIT laisser la page affichée en
  place, avec un message qui propose de réessayer (FR-028 et FR-045 de 001).
- **FR-022a**: L'outil NE DOIT jamais envoyer plus de 8 requêtes à Jira sur une même seconde,
  toutes opérations confondues (recherche, pagination, import, envoi, création, vérification de
  la configuration, synchronisation de l'index), nouvelles tentatives après un
  refus de débit (429) comprises. Une requête en excès attend son tour ; si l'attente dépasse
  10 secondes, l'opération échoue avec l'erreur de débit de FR-028 (001).

**Accès des agents de code**

- **FR-023**: Le système DOIT permettre à un agent de code de se connecter à spec-writer par le
  protocole MCP (Model Context Protocol), depuis le même poste uniquement, tant que l'outil est
  lancé. Toute connexion venant d'une autre machine DOIT être refusée.
- **FR-024**: L'accès des agents DOIT être désactivé par défaut et activable par l'utilisateur. Un
  agent DOIT se connecter en lançant une commande locale fournie par spec-writer, qui
  s'authentifie auprès de l'outil lancé grâce à un secret rangé dans le dossier de données (lisible
  par le seul compte de l'utilisateur, FR-022 de 001). L'utilisateur n'a aucune clé à copier ; il
  DOIT pouvoir régénérer ce secret, ce qui coupe immédiatement tout accès qui s'en servait. Le
  secret n'apparaît jamais à l'écran, dans les journaux ni dans les réponses (FR-034 de 001).
- **FR-024a**: Toute requête MCP qui ne présente pas le secret en cours DOIT être refusée, y compris
  depuis le poste ou depuis une page web ouverte dans le navigateur. L'écran de configuration DOIT
  donner la ligne de configuration à déclarer dans un agent de code.
- **FR-025**: Un agent connecté DOIT pouvoir lister les projets, lister les features à implémenter
  d'un projet, et lire le contenu complet d'une feature : la dernière version de la spécification
  (use cases, scénarios, hypothèses, exigences, détails), identique à l'export Markdown et à
  l'écran, avec la connaissance du projet qui l'accompagne : la fiche générale, les ADR cités et
  les modules impactés avec leurs relations. Il DOIT aussi pouvoir chercher dans l'index du projet
  par sujet (FR-032), sans déclencher de synchronisation ni d'appel à Jira ou à Copilot.
- **FR-026**: Chaque spécification DOIT avoir un statut d'implémentation, modifiable par
  l'utilisateur : « brouillon », « prête à implémenter », « en cours », « implémentée ». Les
  features à implémenter sont les spécifications « prêtes à implémenter » ou « en cours ». Une
  nouvelle spécification est « brouillon ». Une nouvelle version (génération, modification par
  l'IA, import ou fusion Jira) NE DOIT PAS changer le statut : l'utilisateur le met à jour
  lui-même.
- **FR-026a**: Une spécification « implémentée » DOIT être verrouillée : aucune nouvelle version
  (génération, modification par l'IA, import ou fusion depuis Jira), aucune suppression de version,
  aucun envoi vers Jira, aucune modification de ses réponses. Les actions concernées sont
  indisponibles et l'outil explique pourquoi. La suppression de la spécification entière reste
  possible (FR-016).
- **FR-026b**: Pour faire évoluer une spécification implémentée, l'utilisateur DOIT pouvoir soit
  rétablir un statut antérieur, après une confirmation qui rappelle qu'elle a été implémentée et
  depuis quand, soit créer une nouvelle spécification dans le même projet à partir de sa dernière
  version (évolution), qui démarre en « brouillon » et cite la spécification d'origine.
- **FR-027**: Un agent DOIT pouvoir faire passer une feature de « prête à implémenter » à « en
  cours », et de « en cours » à « implémentée ». Il NE DOIT pouvoir faire aucune autre
  modification, et en particulier ne peut pas déverrouiller une feature implémentée. Chaque changement de statut par un agent est consigné (FR-028) et l'utilisateur
  peut rétablir le statut précédent.
- **FR-028**: Chaque accès d'un agent DOIT être consigné localement (date, nom annoncé par
  l'agent, opération, feature concernée), sans le contenu, et consultable par l'utilisateur. Le nom
  annoncé n'est pas vérifié : il sert à la lecture du journal, pas au contrôle d'accès.
- **FR-029**: Un agent NE DOIT avoir accès à aucun secret ni aucune configuration (jetons,
  adresse Jira, secret d'accès des agents), et NE DOIT pas pouvoir déclencher d'appel à GitHub Copilot ou à
  Jira.

**Interface**

- **FR-030**: Les nouveaux écrans et dialogues (projets, fiche générale, sources, indexation,
  tickets proches, suppressions, pagination, accès des agents) DOIVENT respecter les exigences d'interface de 001 (FR-027 à FR-029 : WCAG 2.1
  AA, messages d'erreur utiles).
- **FR-031**: L'outil DOIT proposer un écran « Journal » qui affiche les 500 dernières entrées
  d'avertissement et d'erreur du backend (date, niveau, origine, message, cause), les plus récentes
  d'abord, filtrables par niveau et copiables pour un signalement. Ces entrées sont écrites dans un
  fichier du dossier de données, avec rotation (5 fichiers de 2 Mo au plus), et restent visibles
  après un redémarrage. Le message, la cause et la pile d'appels sont expurgés comme la console
  (FR-025 et FR-034 de 001) ; le journal ne contient jamais de secret, de besoin, de réponse, de
  contenu de spécification, de page ni de ticket (FR-012). L'adresse d'une page et la clé d'un
  ticket peuvent y figurer.

### Key Entities

- **Projet** : nom unique, description, clé de projet Jira facultative (surcharge du projet par
  défaut), fiche générale, sources, date de création et de modification. Contient des
  spécifications.
- **Fiche générale** : texte Markdown rédigé à la main, avec sa date de dernière modification ;
  surcharge les sources, entre dans les opérations de l'IA et est citée comme « contexte du
  projet ».
- **Source de documentation** : lien ou dossier local d'un site Antora rattaché à un projet, avec
  la date et le résultat de sa dernière lecture (pages lues, pages en erreur).
- **Source d'architecture** : fichier de workspace Structurizr d'un projet, avec la date et le
  résultat de sa dernière lecture.
- **Élément indexé** : ticket Jira (clé, titre, description, type, statut, date de mise à jour),
  section de documentation (lien, titre, type : ADR et son statut, vocabulaire, architecture,
  autre) ou élément d'architecture (nom, type, description, technologie, propre ou commun), avec ce
  qui permet de le retrouver par le sens et par les termes.
- **Relation et déploiement** : relation entre deux éléments d'architecture (description,
  technologie) ; instance d'un élément dans un environnement de déploiement, sous une
  infrastructure racine.
- **Ticket proche** : ticket retrouvé pour un besoin, avec son statut relu et la raison du
  rapprochement ; mène à une story liée, à l'enrichissement du ticket ou à une nouvelle story.
- **Indexation** : opération en cours ou terminée d'un projet (étape, élément en cours, compteurs,
  erreurs, temps écoulé), gardée en mémoire et jamais journalisée.
- **Spécification** (session de 001) : rattachée à un projet ; reçoit un statut d'implémentation,
  la date de passage à « implémentée » et, pour une évolution, la spécification d'origine.
- **Version de spécification** (001) : indique la connaissance utilisée (fiche générale, éléments
  retrouvés) et sa date.
- **Sources de la connaissance du projet** : nouveaux types de source d'un use case ou d'une
  hypothèse : « contexte du projet » (extrait de la fiche), ticket Jira, page ou ADR, élément
  d'architecture.
- **Entrée de journal** : date, niveau (avertissement, erreur), origine, message et cause expurgés.
- **Secret d'accès des agents** : secret généré par l'outil dans le dossier de données, lu par la
  commande MCP, régénérable par l'utilisateur, jamais affiché.
- **Accès d'agent** : entrée de journal (date, agent, opération, feature, ancien et nouveau statut
  le cas échéant), sans contenu.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Après la mise à jour, 100 % des spécifications existantes sont retrouvées, avec
  toutes leurs versions, dans le projet par défaut.
- **SC-002**: Sur le jeu de référence (besoins types, avec pour chacun les tickets, ADR, modules,
  flux manquants et efforts de déploiement attendus), au moins 90 % des tickets attendus figurent
  parmi les tickets proches montrés, 100 % des ADR attendus sont cités, et 100 % des flux manquants
  et efforts de déploiement attendus sont signalés, sans aucun flux existant signalé à tort.
- **SC-003**: Sur le même jeu, 100 % des éléments issus de la connaissance du projet citent une
  source existante, au plus 5 % des questions posées portent sur une information présente dans la
  fiche ou les éléments retrouvés, et la part de questions pertinentes reste au moins au niveau de
  la base actuelle (SC-004 de 001).
- **SC-004**: Un utilisateur crée un projet avec sa clé Jira, sa documentation et son workspace,
  puis y démarre une spécification, en moins de 3 minutes, sans attendre la fin de l'indexation.
- **SC-004a**: Pour 300 pages, 200 tickets et un workspace de 2 000 lignes, la première indexation
  se termine en moins de 10 minutes sur un poste de développement courant ; une synchronisation
  incrémentale sans changement en moins de 30 secondes ; les tickets proches s'affichent moins de
  5 secondes après la saisie du besoin.
- **SC-004b**: Sur le jeu de référence, 100 % des rédactions aboutissent du premier essai, sans
  réglage ni relance de la part de l'utilisateur, et aucune n'échoue à cause du volume des sources
  du projet.
- **SC-005**: Après confirmation, un élément supprimé n'apparaît plus dans aucune vue, recherche,
  export ni réponse à un agent, dans 100 % des cas, et aucun appel n'est fait vers Jira.
- **SC-006**: Une page de tickets Jira s'affiche en moins de 3 secondes dans 95 % des cas, quel que
  soit le nombre de tickets du projet.
- **SC-007**: Un agent de code obtient la liste des features à implémenter d'un projet, puis le
  contenu d'une feature, en moins de 5 secondes au total, avec un contenu identique à l'export
  Markdown dans 100 % des cas.
- **SC-008**: 100 % des tentatives de connexion d'agent sans le secret en cours (ancien secret,
  programme ou page web qui joint directement l'outil, autre machine) sont refusées, et l'agent
  déclaré par la ligne de configuration fournie se connecte du premier coup.

## Assumptions

- L'outil reste local et mono-utilisateur (FR-021 de 001) : les projets ne sont pas partagés entre
  utilisateurs et n'ont pas de droits d'accès propres.
- Seule la clé du projet Jira est surchargée par projet ; l'adresse du serveur et le jeton restent
  ceux de la configuration.
- Une « feature » au sens de l'agent est une spécification de spec-writer ; l'agent lit sa
  dernière version.
- Les agents de code visés tournent sur le même poste que spec-writer (IDE ou terminal) et savent
  se connecter à un serveur MCP.
- Le statut d'implémentation est géré dans spec-writer ; il n'est pas synchronisé avec le statut du
  ticket Jira dans cette feature.
- Une suppression est définitive : pas de corbeille. La confirmation nommée suffit à éviter les
  erreurs.
- La pagination par 25 suit les usages de Jira ; ce nombre n'est pas réglable dans cette feature.
- La connaissance du projet repose sur un index local et une recherche des éléments proches de
  chaque besoin ; rien n'est résumé d'avance. Le modèle de recherche par le sens est fourni avec
  l'outil ou téléchargé une fois, sans manipulation de l'utilisateur.
- Les ADR sont des pages de la documentation Antora ; leur statut (en vigueur, remplacé) se lit
  dans la page.
- Le modèle d'architecture est un workspace Structurizr en DSL, dans un dossier local, qui peut
  étendre un modèle commun et inclure d'autres fichiers ; seuls les déploiements de production
  servent aux efforts supplémentaires.
- SharePoint, wiki et documents Office (Word, Excel) sont hors périmètre ; l'index est conçu pour
  accueillir d'autres sources plus tard.
- Le jeu de référence réel est tiré de données de l'entreprise : il reste hors du dépôt de
  spec-writer, dans un dossier local lu par les tests s'il existe ; ses réponses attendues sont
  écrites par l'utilisateur. Un jeu fictif de même forme est versionné et sert de garde-fou au
  build.
- La recherche par le sens se fait seulement en local ; une option passant par GitHub Copilot est
  hors périmètre.
- La documentation est accessible sans authentification depuis le poste (site interne, serveur
  local ou dossier). Un site protégé par un identifiant est hors périmètre.
- Un site Antora publie la liste de ses pages, avec leur date de modification ; l'outil s'en sert
  pour savoir quoi lire et quoi relire. Les autres générateurs de documentation sont hors
  périmètre.
- La lecture de la documentation passe par le proxy d'entreprise, comme Jira (T139 de 001).
- Les skills de rédaction envisagés plus tôt (fichiers SKILL.md) restent hors périmètre : la
  connaissance du projet couvre une partie de ce besoin.
