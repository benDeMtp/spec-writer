# Feature Specification: Projets, contexte de projet et accès des agents de code

**Feature Branch**: `005-projets-mcp`

**Created**: 2026-11-03

**Status**: Draft

**Input**: User description: "Je voudrais faire apparaitre la notion de projets. Chaque projet pourra surcharger le projet jira associé. Le but d'avoir un projet et d'avoir un contexte propre au projet afin d'obtimisé la redaction des feature. TU me fera des proposition à ce sujet ( contexte manuel ? Rag ? Contexte généré en même temps que le rag ?) Par la suite je veux qu'un agent de code puisse se connecter à spec-writer en MCP afin de récupérer les feature à implementer pour un projet. Il faut aussi améliorer la gestion des spec locale. Il faut pouvoir supprimer une verison locale ou une spec locale. Il faudrait voir aussi les spec jira de façon paginée"

Cette feature prolonge la feature 001 (`specs/001-local-spec-assistant/spec.md`), dont elle reprend
le vocabulaire : une **spécification** est le résultat d'une session (besoin, questions, versions).
Une **feature à implémenter** est une spécification qu'un agent de code peut récupérer.

## Clarifications

### Session 2026-11-03

- Q: D'où vient le contexte d'un projet ? → A: une fiche de contexte proposée par l'IA, relue et
  modifiable par l'utilisateur, sans RAG dans cette feature. Elle est générée à partir d'une ou
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

- Q: Comment rendre le contexte plus précis ? → A: deux fiches distinctes : une fiche
  fonctionnelle (vision et objectifs, acteurs, vocabulaire, règles métier, contraintes
  fonctionnelles) et une fiche technique (architecture, contraintes techniques, conventions de
  code, intégrations). Chacune est modifiable, proposée et acceptée séparément.
- Q: Que fait l'assistant quand les sources ne sont pas claires ? → A: avec sa proposition, il
  pose des questions de clarification (contradictions, informations absentes, termes ambigus).
  L'utilisateur répond ou passe, puis l'IA révise la proposition. Les réponses sont gardées avec
  le projet, citées comme sources et réutilisées aux régénérations.
- Q: Qui reçoit quelle fiche ? → A: la rédaction des spécifications reçoit les deux fiches ; les
  agents de code reçoivent la fiche technique avec chaque feature.

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

### User Story 2 - Un contexte de projet qui améliore la rédaction (Priority: P1)

Chaque projet a un contexte : vocabulaire métier, acteurs habituels, règles et contraintes
transverses, conventions de rédaction. L'utilisateur indique le lien de la documentation du projet
(un site produit par Antora, éventuellement servi sur son poste) ; l'IA rédige une fiche de
contexte à partir de cette documentation et des tickets déjà présents dans le projet Jira.
L'utilisateur relit la fiche, la corrige, et peut la régénérer quand la documentation évolue.
L'outil s'en sert pour poser moins de questions (il ne demande pas ce que le contexte dit déjà) et
pour rédiger des spécifications cohérentes avec le reste du projet. Ce qui vient du contexte est
tracé comme tel dans la spécification.

**Why this priority**: c'est la raison d'être des projets selon le besoin exprimé : optimiser la
rédaction des features.

**Independent Test**: sur un même besoin, générer une spécification dans un projet sans contexte,
puis dans un projet dont le contexte donne les acteurs et une règle métier : la seconde session ne
pose pas de question sur ces acteurs ni sur cette règle, et la spécification les reprend avec la
source « contexte du projet ».

**Acceptance Scenarios**:

1. **Given** un projet dont le contexte indique « Les acteurs sont le client et le conseiller »,
   **When** l'utilisateur saisit un besoin qui ne nomme pas les acteurs, **Then** aucune question
   ne porte sur les acteurs et les use cases utilisent ces deux acteurs.
2. **Given** un use case issu d'une règle du contexte, **When** l'utilisateur consulte la
   spécification, **Then** la source du use case est « contexte du projet », avec l'extrait
   concerné.
3. **Given** un contexte qui contredit le besoin saisi, **When** le questionnaire démarre, **Then**
   la contradiction est signalée et l'utilisateur la tranche, comme une contradiction entre deux
   réponses.
4. **Given** une spécification générée avec un contexte, **When** l'utilisateur modifie le
   contexte puis relance la génération, **Then** une nouvelle version est créée ; à contexte
   inchangé, la version existante est réutilisée.
5. **Given** un projet avec le lien de sa documentation Antora et un projet Jira contenant des
   tickets, **When** l'utilisateur demande de générer le contexte et accepte l'envoi à GitHub
   Copilot, **Then** l'outil lit la documentation et les tickets, puis propose une fiche de contexte
   qui cite ses sources (pages et tickets) ; rien n'est enregistré avant que l'utilisateur
   l'accepte.
6. **Given** une fiche de contexte acceptée puis corrigée à la main, **When** l'utilisateur la
   régénère après une mise à jour de la documentation, **Then** l'outil propose une nouvelle fiche
   et montre les différences avec la fiche actuelle ; l'utilisateur accepte ou garde la sienne.
7. **Given** une documentation injoignable ou qui n'est pas un site Antora, **When** l'utilisateur
   demande la génération, **Then** l'outil indique ce qui n'a pas pu être lu et propose de générer
   à partir des seuls tickets, ou de saisir le contexte à la main.
8. **Given** une génération de contexte en cours, **When** l'utilisateur suit la page du contexte,
   **Then** il voit l'étape en cours, la page ou le ticket en cours de lecture et l'avancement
   (« page 12 sur 32 », « ticket 50 sur 120 ») ; s'il l'interrompt, rien n'est proposé ni
   enregistré.
9. **Given** une génération terminée avec des pages en erreur, **When** l'utilisateur consulte le
   détail des erreurs ou l'écran « Journal », **Then** il voit pour chaque page son adresse, le
   motif et le code HTTP, sans aucun contenu de page ni de ticket.
10. **Given** une documentation qui décrit le métier et l'architecture, **When** l'utilisateur
   génère le contexte, **Then** l'outil propose une fiche fonctionnelle et une fiche technique,
   chacune avec ses sources et son diff ; l'utilisateur peut accepter l'une et garder l'autre.
11. **Given** des sources qui se contredisent sur un délai, ou qui ne disent rien de la vision du
   produit, **When** la proposition est prête, **Then** l'assistant pose une question pour chaque
   point flou ; l'utilisateur répond à l'une, passe l'autre, demande la révision, et la nouvelle
   proposition cite sa réponse comme source. À la régénération suivante, la question passée
   n'est pas reposée.

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
   la dernière version de la spécification et la fiche technique du projet, identiques à ce que montre
   l'outil.
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
- Le contexte dépasse la taille maximale : l'enregistrement est refusé avec la limite indiquée.
- La fiche générée dépasse la taille maximale du contexte : l'IA est relancée avec une consigne de
  concision ; à défaut, la proposition est refusée avec la limite indiquée.
- La documentation contient des consignes adressées à l'IA, ou un ticket contient un texte
  malveillant : comme le besoin, c'est une donnée, jamais une consigne (FR-026 de 001).
- La documentation change pendant la lecture, ou une page renvoie une erreur : la génération
  continue avec les pages lues et liste celles qui ne l'ont pas été.
- Le lien de documentation redirige vers un autre site : la redirection n'est pas suivie.
- Le projet n'a ni documentation ni ticket : la génération n'est pas proposée ; la saisie
  manuelle reste possible.
- Le contexte contient des consignes du type « ignore tes instructions » : il reste une donnée,
  comme le besoin (FR-026 de 001).
- Le contexte est modifié pendant un questionnaire en cours : les questions suivantes utilisent le
  nouveau contexte ; les réponses déjà données restent.
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

**Contexte de projet**

- **FR-008**: Chaque projet DOIT avoir un contexte : un texte en Markdown (vocabulaire, acteurs,
  règles et contraintes transverses, conventions), de 20 000 caractères au maximum, modifiable à
  tout moment.
- **FR-008a**: Le contexte d'un projet DOIT se composer de deux fiches distinctes, chacune de
  20 000 caractères au plus, avec son origine (générée, modifiée à la main) et sa date :
  - la **fiche fonctionnelle** : vision et objectifs, acteurs, vocabulaire, règles métier,
    contraintes fonctionnelles ;
  - la **fiche technique** : architecture, contraintes techniques, conventions de code,
    intégrations.
  Chaque fiche est modifiable, proposée avec son diff, puis acceptée ou refusée indépendamment de
  l'autre.
- **FR-009**: Les deux fiches DOIVENT être prises en compte par toutes les opérations de l'IA d'une
  spécification du projet : questions, réévaluation, génération, modification assistée, import et
  fusion. La fiche technique sert aux exigences techniques. Une question dont la réponse figure dans le contexte NE DOIT PAS être posée (principe I
  de la constitution).
- **FR-010**: Tout élément de la spécification qui découle du contexte DOIT avoir pour source
  « contexte du projet », avec l'extrait concerné (traçabilité, FR-011 de 001). Une contradiction
  entre le contexte et le besoin ou une réponse DOIT être signalée et tranchée comme les autres
  contradictions.
- **FR-011**: Les deux fiches DOIVENT entrer dans les entrées de la génération : à fiche modifiée, une
  nouvelle version est créée ; à contexte inchangé, la version existante est réutilisée (FR-014 et
  FR-018 de 001). Chaque version DOIT indiquer si un contexte a été utilisé et de quand il date.
- **FR-012**: Le contexte DOIT être traité comme une donnée et jamais comme une consigne (FR-026 de
  001), ne DOIT jamais figurer dans les journaux (FR-025 de 001), et n'est envoyé à GitHub Copilot
  que dans le cadre du consentement existant (FR-024 de 001).
- **FR-013**: L'utilisateur DOIT pouvoir déclarer pour un projet une ou plusieurs sources de
  documentation : le lien d'un site produit par Antora (adresse `http` ou `https`, y compris sur
  son poste) ou le dossier local de ce site. Seules les pages de ce site sont lues, sans suivre les
  liens vers d'autres sites.
- **FR-013a**: À la demande de l'utilisateur, l'IA DOIT proposer les deux fiches, rédigées à
  partir des sources de documentation, des tickets déjà présents dans le projet Jira associé et
  des réponses déjà données (FR-013i). Chaque affirmation cite ses sources (page de documentation,
  clé de ticket, réponse). Rien n'est enregistré avant que l'utilisateur accepte une fiche.
- **FR-013b**: Avant de générer, l'outil DOIT indiquer ce qui sera envoyé à GitHub Copilot (nombre
  de pages, nombre de tickets) et obtenir l'accord de FR-024 (001). Les pages lues ne sont
  conservées que le temps de la génération.
- **FR-013c**: L'utilisateur DOIT pouvoir modifier la fiche à la main à tout moment, et la
  régénérer à la demande ; une régénération est présentée comme une proposition, avec ses
  différences avec la fiche actuelle, que l'utilisateur accepte ou refuse.
- **FR-013d**: La génération DOIT rester possible si une partie des sources est inaccessible :
  l'outil liste ce qui n'a pas été lu (page en erreur, site introuvable, site qui n'est pas
  produit par Antora) et propose de continuer avec le reste, ou de saisir le contexte à la main.
- **FR-013e**: La lecture d'une documentation DOIT être bornée : 1 000 pages au maximum par
  source et 10 minutes au maximum ; l'outil indique quand une borne est atteinte. Les tickets pris
  en compte sont les 200 plus récemment modifiés du projet Jira.
- **FR-013f**: Pendant une génération de contexte, l'outil DOIT afficher l'étape en cours (lecture
  de la documentation, lecture des tickets Jira, extraction des faits, rédaction de la fiche), la
  page ou le ticket en cours de lecture et l'avancement de l'étape (pages, tickets ou lots traités
  sur le total), mis à jour au moins toutes les 2 secondes. L'utilisateur DOIT pouvoir quitter la
  page puis la retrouver en cours, et interrompre la génération : rien n'est alors proposé ni
  enregistré. Une seule génération à la fois par projet.
- **FR-013g**: À la fin d'une génération, réussie, interrompue ou en échec, l'outil DOIT afficher
  le détail de ses erreurs : pour chaque source ou page non lue, son adresse, le motif et le code
  HTTP s'il y en a un ; les erreurs Jira ; les corrections demandées à l'IA et leur motif ; la
  cause d'un échec. Ce détail ne contient aucun contenu de page ni de ticket.

- **FR-013h**: Avec sa proposition, l'IA DOIT poser des questions de clarification sur ce qui
  n'est pas clair : sources qui se contredisent, information attendue mais absente (vision,
  objectifs, architecture…), terme ambigu, règle incomplète. 10 questions au plus, chacune
  rattachée à une fiche, avec sa raison et des suggestions. Une information présente dans les
  sources ou les réponses NE DOIT PAS faire l'objet d'une question. L'utilisateur répond ou
  passe, puis demande la révision : l'IA révise la proposition à partir des mêmes faits et des
  réponses, sans relire les sources. Les réponses non encore utilisées restent proposables.
- **FR-013i**: Les réponses et les questions passées DOIVENT être conservées avec le projet,
  numérotées (Q-1, Q-2…). Une réponse est une source citable des fiches (« [source : Q-3] ») et
  est transmise aux générations suivantes ; une question passée n'est pas reposée. L'utilisateur
  DOIT pouvoir consulter ses réponses et en modifier une ; la modification ne change aucune fiche
  avant la génération suivante.

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
  la configuration, lecture des tickets pour la fiche de contexte), nouvelles tentatives après un
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
  l'écran, et la fiche technique du projet (FR-008a).
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

- **FR-030**: Les nouveaux écrans et dialogues (projets, contexte, suppressions, pagination,
  accès des agents) DOIVENT respecter les exigences d'interface de 001 (FR-027 à FR-029 : WCAG 2.1
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
  défaut), contexte, date de création et de modification. Contient des spécifications.
- **Contexte de projet** : deux fiches Markdown, fonctionnelle et technique, chacune avec sa date
  de dernière modification et son origine (générée, modifiée à la main) ; elles servent d'entrée
  aux opérations de l'IA et sont citées comme source dans les spécifications.
- **Question de contexte** : question de clarification posée par l'IA sur une fiche, numérotée
  dans le projet (Q-n), avec sa raison et sa réponse ou son état « passée ». Une réponse est une
  source citable des fiches.
- **Source de documentation** : lien ou dossier local d'un site Antora rattaché à un projet, avec
  la date et le résultat de sa dernière lecture (pages lues, pages en erreur).
- **Spécification** (session de 001) : rattachée à un projet ; reçoit un statut d'implémentation,
  la date de passage à « implémentée » et, pour une évolution, la spécification d'origine.
- **Version de spécification** (001) : indique si un contexte a été utilisé et sa date.
- **Source « contexte du projet »** : nouveau type de source d'un use case ou d'une hypothèse,
  avec l'extrait du contexte.
- **Génération de contexte** : opération en cours ou terminée d'un projet (étape, élément en cours
  de lecture, compteurs, erreurs, proposition obtenue), gardée en mémoire et jamais journalisée.
- **Entrée de journal** : date, niveau (avertissement, erreur), origine, message et cause expurgés.
- **Secret d'accès des agents** : secret généré par l'outil dans le dossier de données, lu par la
  commande MCP, régénérable par l'utilisateur, jamais affiché.
- **Accès d'agent** : entrée de journal (date, agent, opération, feature, ancien et nouveau statut
  le cas échéant), sans contenu.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Après la mise à jour, 100 % des spécifications existantes sont retrouvées, avec
  toutes leurs versions, dans le projet par défaut.
- **SC-002**: Sur le jeu de besoins de référence complété d'un contexte de projet, au plus 5 % des
  questions posées portent sur une information présente dans le contexte, et le nombre moyen de
  questions baisse d'au moins 20 % par rapport à la même session sans contexte.
- **SC-003**: Sur le même jeu, 100 % des éléments issus du contexte ont pour source « contexte du
  projet », et la part de questions pertinentes reste au moins au niveau de la base actuelle
  (SC-004 de 001).
- **SC-004**: Un utilisateur crée un projet avec sa clé Jira et son contexte, puis y démarre une
  spécification, en moins de 3 minutes.
- **SC-004a**: Pour une documentation Antora de 300 pages et 200 tickets, une proposition de fiche
  de contexte est prête en moins de 10 minutes, et 100 % de ses affirmations citent une page ou un
  ticket existant.
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
- Pas de RAG dans cette feature : le contexte est une fiche unique, relue par l'utilisateur. Une
  recherche des extraits les plus proches à chaque génération pourra s'y ajouter plus tard.
- La documentation est accessible sans authentification depuis le poste (site interne, serveur
  local ou dossier). Un site protégé par un identifiant est hors périmètre.
- Un site Antora publie la liste de ses pages ; l'outil s'en sert pour savoir quoi lire. Les
  autres générateurs de documentation sont hors périmètre.
- La lecture de la documentation passe par le proxy d'entreprise, comme Jira (T139 de 001).
- Les skills de rédaction envisagés plus tôt (fichiers SKILL.md) restent hors périmètre : le
  contexte de projet couvre une partie de ce besoin.
