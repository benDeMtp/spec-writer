# Feature Specification: Tâches d'une spécification, avancement suivi avec Jira et par les agents

**Feature Branch**: `feature/019-taches-avancement`

**Created**: 2026-11-17

**Status**: Draft

**Input**: User description: "Feature 015 : rédaction d'une story avec cas d'usage à partir d'un document entrant, découpage en tâches proposé par spec-writer et suivi d'avancement synchronisé avec Jira et exposé aux agents par le MCP. Décisions prises le 16/11 : découpage sur demande, fondé sur les modules touchés ou sur les cas d'usage, 3 à 8 tâches vérifiables, relu puis validé, créé en sous-tâches Jira ; suivi par l'agent avec get_feature, next_task, start_task, complete_task, choix déterministe ; transitions Jira choisies d'après la catégorie de statut ; statut de la spécification automatique ; statut des sous-tâches relu depuis Jira ; avancement affiché sur la spécification ; consignes inspirées de skills publics (user-story, INVEST, user-story-splitting, planning-and-task-breakdown) sans recopie, sous forme de consignes réutilisables gardées par l'évaluation réelle. Hors périmètre : l'argument --project du pont MCP, livré avant dans sa propre PR."

## Clarifications

### Session 2026-11-17

- Q: Quand un agent prend ou termine une tâche et que Jira est injoignable, spec-writer refuse-t-il ou
  enregistre-t-il pour envoyer plus tard ? → A: Il enregistre la transition et marque la tâche « à
  synchroniser » ; l'envoi à Jira est retenté à la prochaine occasion et par un bouton
  « Synchroniser » ; la passerelle répond à l'agent que la transition est enregistrée mais pas encore
  dans Jira, et l'agent choisit de continuer ou non.
- Q: Peut-on découper et suivre les tâches d'une spécification liée à aucun ticket Jira ? → A: Non :
  sans ticket lié, la proposition est indisponible et un message invite à envoyer d'abord la
  spécification vers Jira ; aucune tâche n'existe sans sous-tâche Jira.
- Q: Une fois qu'une spécification a des tâches, l'utilisateur peut-il changer son statut à la main ?
  → A: Non pour « En cours » et « Implémentée », qui ne dépendent que des tâches ; il peut la
  repasser en Brouillon ou en Prête, ce qui suspend le suivi par les agents.
- Q: Comment spec-writer distingue-t-il deux agents qui travaillent en même temps ? → A: Par
  connexion : chaque lancement de la passerelle a son identifiant et une tâche est réservée à cette
  connexion ; l'écran montre le nom de l'agent et l'heure de prise ; une tâche tenue par une connexion
  fermée est proposée à la conversation suivante pour être reprise.
- Q: En terminant une tâche, l'agent fournit-il un compte rendu ajouté en commentaire sur la
  sous-tâche Jira ? → A: Facultatif : 2 000 caractères au plus, ajouté en commentaire Jira et
  affiché sur l'écran de la spécification.

## Contexte

Une spécification de spec-writer est liée à un seul ticket Jira (une story). Aujourd'hui, son
avancement se résume à un statut global (Brouillon, Prête, En cours, Implémentée) qu'un agent de code
fait passer à « En cours » puis « Implémentée » par la passerelle MCP. Rien ne dit où en est
l'implémentation entre ces deux bornes, ni à l'équipe dans Jira, ni à l'agent qui reprend le travail
dans une nouvelle conversation, puisqu'il ne garde rien d'une conversation à l'autre.

Cette feature découpe une spécification en quelques tâches, sur demande. spec-writer propose le
découpage, l'utilisateur le corrige et le valide, et les tâches deviennent des sous-tâches de la
story dans Jira. Les agents de code demandent alors à spec-writer quelle tâche faire, la prennent et la
terminent ; chaque étape fait avancer la sous-tâche dans Jira. L'état vit dans spec-writer et dans
Jira, jamais dans la mémoire de l'agent ni dans une déduction tirée du code.

Elle complète aussi la rédaction : la spécification produite à partir d'un document joint (009)
s'ouvre sur l'énoncé de la story (« En tant que… je veux… afin de… »), et sa rédaction suit des
critères de qualité de story reconnus.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Proposer, corriger et valider les tâches d'une spécification (Priority: P1) 🎯 MVP

Sur une spécification liée à une story Jira, l'utilisateur clique sur « Proposer des tâches ».
spec-writer lui présente 3 à 8 tâches, dans l'ordre, chacune avec un titre, un résultat vérifiable et
la base du découpage (un module touché, un cas d'usage). L'utilisateur renomme, fusionne, supprime,
réordonne ou ajoute des tâches, puis valide. spec-writer crée alors une sous-tâche Jira par tâche,
sous la story de la spécification, et affiche la clé Jira de chacune.

**Why this priority**: sans tâches, ni l'avancement ni le suivi par les agents n'existent. C'est aussi
le premier bénéfice visible : la story est découpée dans Jira sans ressaisie.

**Independent Test**: sur une spécification liée à une story d'un Jira simulé, proposer, retirer une
tâche, en ajouter une, valider : le Jira simulé reçoit exactement les sous-tâches de la liste validée,
dans l'ordre, sous la bonne story.

**Acceptance Scenarios**:

1. **Given** une spécification liée à une story et sans tâche, **When** l'utilisateur demande une
   proposition, **Then** il voit entre 3 et 8 tâches ordonnées, chacune avec un titre, un résultat
   vérifiable et sa base (module ou cas d'usage), et rien n'est encore envoyé à Jira.
2. **Given** une spécification qui a une rubrique « Modules touchés » (013), **When** l'utilisateur
   demande une proposition, **Then** les tâches s'appuient sur ces modules ; sans cette rubrique,
   elles s'appuient sur les cas d'usage.
3. **Given** une proposition affichée, **When** l'utilisateur renomme une tâche, en fusionne deux, en
   supprime une, en déplace une et en ajoute une, **Then** la liste reflète chaque modification avant
   la validation.
4. **Given** une proposition corrigée, **When** l'utilisateur valide, **Then** une sous-tâche Jira est
   créée par tâche sous la story liée, et chaque tâche affiche sa clé Jira et le statut « À faire ».
5. **Given** une spécification qui n'est liée à aucun ticket Jira, **When** l'utilisateur ouvre ses
   tâches, **Then** l'action de proposition est indisponible et un message l'invite à envoyer d'abord
   la spécification vers Jira.
6. **Given** une story qui a déjà des sous-tâches créées à la main dans Jira, **When** l'utilisateur
   demande une proposition, **Then** ces sous-tâches apparaissent comme tâches existantes et la
   proposition ne les recrée pas.

---

### User Story 2 - Un agent de code suit les tâches par la passerelle MCP (Priority: P1)

Un agent de code, dans une nouvelle conversation, demande à spec-writer où en est une feature. Il
reçoit la tâche en cours s'il y en a une, sinon la prochaine tâche à faire. Il la prend, ce qui la
passe « En cours » dans spec-writer et dans Jira et la réserve à cet agent. Quand il a fini, il la
termine, ce qui la passe « Terminée » des deux côtés. Chaque étape est inscrite au journal des agents.

**Why this priority**: c'est la raison du découpage pour l'utilisateur : voir les sous-tâches avancer
pendant que l'agent travaille, et permettre à l'agent de reprendre là où il s'était arrêté.

**Independent Test**: avec trois tâches validées et un Jira simulé, un client MCP enchaîne
« prochaine tâche », « prendre », « terminer » trois fois : à chaque étape le Jira simulé reçoit la
bonne transition, le journal des agents a une ligne, et la quatrième demande répond qu'il ne reste
rien.

**Acceptance Scenarios**:

1. **Given** une feature avec des tâches, **When** l'agent lit la feature, **Then** il reçoit aussi la
   liste des tâches : clé Jira, titre, résultat attendu, ordre, statut et, pour une tâche en cours,
   l'agent qui l'a prise.
2. **Given** une tâche en cours tenue par la connexion de l'agent, ou par une connexion fermée,
   **When** l'agent demande la prochaine tâche, **Then** il reçoit cette tâche en cours, même dans une
   nouvelle conversation, et la reprendre la rattache à sa connexion.
2b. **Given** une tâche en cours tenue par une autre connexion encore ouverte, **When** un agent
   demande la prochaine tâche, **Then** il reçoit la tâche « À faire » suivante, pas celle-ci.
3. **Given** aucune tâche en cours, **When** l'agent demande la prochaine tâche, **Then** il reçoit la
   première tâche « À faire » dans l'ordre dont les tâches préalables sont terminées.
4. **Given** toutes les tâches terminées, **When** l'agent demande la prochaine tâche, **Then** la
   réponse dit qu'il ne reste aucune tâche.
5. **Given** une tâche « À faire », **When** l'agent la prend, **Then** elle passe « En cours » dans
   spec-writer et dans Jira, avec le nom de l'agent, et le journal des agents l'inscrit.
6. **Given** une tâche prise par un autre agent, **When** un second agent tente de la prendre,
   **Then** la demande est refusée avec un message clair qui nomme l'agent qui l'a prise.
7. **Given** une tâche « En cours », **When** l'agent la termine, **Then** elle passe « Terminée » dans
   spec-writer et dans Jira, et le journal des agents l'inscrit.
7b. **Given** une tâche « En cours », **When** l'agent la termine avec un compte rendu, **Then** le
   compte rendu est ajouté en commentaire sur la sous-tâche Jira et affiché avec la tâche sur l'écran
   de la spécification ; au-delà de 2 000 caractères, la demande est refusée avec un message clair.
8. **Given** la même situation, **When** deux appels « prochaine tâche » se suivent sans autre
   changement, **Then** ils renvoient la même tâche.

---

### User Story 3 - Voir l'avancement, à jour avec Jira (Priority: P2)

Sur l'écran de la spécification, l'utilisateur voit l'avancement (« 3 tâches sur 5 terminées ») et le
statut de chaque tâche. Si quelqu'un a fait avancer une sous-tâche directement dans Jira, spec-writer
relit son statut à l'ouverture de la spécification. Le statut de la spécification suit les tâches :
« En cours » dès la première tâche commencée, « Implémentée » quand toutes sont terminées.

**Why this priority**: l'avancement visible est le but du découpage, mais il repose sur les tâches
(US1) et leurs transitions (US2).

**Independent Test**: avec cinq tâches dont trois terminées dans le Jira simulé, ouvrir la
spécification : elle affiche « 3 tâches sur 5 terminées » et le statut « En cours » ; terminer les
deux dernières dans le Jira simulé, rouvrir : « 5 sur 5 » et « Implémentée ».

**Acceptance Scenarios**:

1. **Given** une spécification avec des tâches, **When** l'utilisateur l'ouvre, **Then** il voit le
   nombre de tâches terminées sur le total et la liste des tâches avec leur statut et leur clé Jira,
   qui ouvre la sous-tâche dans Jira.
2. **Given** une sous-tâche passée « Terminée » directement dans Jira, **When** l'utilisateur ouvre la
   spécification, **Then** la tâche apparaît « Terminée » et l'avancement en tient compte.
3. **Given** une spécification « Prête », **When** une première tâche passe « En cours » (par un agent
   ou dans Jira), **Then** la spécification passe « En cours ».
4. **Given** une spécification « En cours », **When** la dernière tâche passe « Terminée », **Then** la
   spécification passe « Implémentée ».
5. **Given** une spécification « Implémentée » par ses tâches, **When** une tâche est rouverte dans
   Jira, **Then** à la relecture la spécification repasse « En cours ».
6. **Given** Jira injoignable, **When** l'utilisateur ouvre la spécification, **Then** il voit les
   derniers statuts connus, avec l'heure de la dernière relecture et un message qui signale que Jira
   n'a pas pu être relu.

---

### User Story 4 - Story rédigée à partir d'un document, avec ses cas d'usage (Priority: P3)

L'utilisateur joint un document reçu (compte rendu, cahier des charges) et décrit son besoin en une
phrase, ou renvoie simplement au document. La spécification produite s'ouvre sur l'énoncé de la
story (« En tant que <rôle>, je veux <capacité>, afin de <bénéfice> »), suivi des cas d'usage et de
leurs scénarios Étant donné / Quand / Alors, comme aujourd'hui. La rédaction suit des critères de
qualité de story (indépendante, négociable, porteuse de valeur, estimable, petite, testable) : une
story trop large est signalée, avec une proposition de découpage en plusieurs stories.

**Why this priority**: la génération à partir d'un document joint existe déjà (009) ; cette story en
améliore la forme et la qualité, sans changer le parcours.

**Independent Test**: avec un document joint qui décrit une capacité et son bénéfice, générer : la
spécification commence par un énoncé de story qui nomme le rôle, la capacité et le bénéfice tirés du
document, et l'évaluation réelle mesure la conformité de cet énoncé.

**Acceptance Scenarios**:

1. **Given** un besoin d'une phrase et un document joint, **When** l'utilisateur génère la
   spécification, **Then** elle s'ouvre sur un énoncé de story dont le rôle, la capacité et le
   bénéfice viennent du besoin ou du document.
2. **Given** un document qui ne dit pas le bénéfice attendu, **When** la spécification est générée,
   **Then** le bénéfice de l'énoncé est marqué comme une hypothèse, comme les autres hypothèses de la
   spécification.
3. **Given** un besoin qui couvre plusieurs capacités indépendantes, **When** la spécification est
   générée, **Then** elle signale que la story est trop large et propose un découpage en stories, sans
   le faire à la place de l'utilisateur.
4. **Given** l'énoncé de story, **When** la spécification est exportée ou envoyée vers Jira, **Then**
   l'énoncé figure en tête de la description.

---

### Edge Cases

- **Story liée de type sous-tâche** : un ticket de type sous-tâche ne peut pas en porter. La
  proposition est indisponible, avec un message qui l'explique.
- **Pas de type sous-tâche dans le projet Jira** : la validation est refusée avec un message qui le
  dit ; rien n'est créé.
- **Création partielle** : si Jira refuse une sous-tâche au milieu de la validation, celles déjà
  créées restent liées, les autres restent « non créées » et l'utilisateur peut relancer leur
  création, sans doublon.
- **Nouvelle proposition après validation** : elle ne remplace pas les tâches existantes ; elle
  propose seulement des tâches complémentaires, que l'utilisateur valide de la même façon.
- **Tâche retirée après validation** : spec-writer ne supprime jamais de sous-tâche dans Jira.
  Retirer une tâche créée n'est pas proposé ; l'utilisateur la ferme dans Jira, et la relecture la
  marque terminée ou abandonnée.
- **Sous-tâche supprimée ou déplacée dans Jira** : à la relecture, la tâche est marquée
  « introuvable dans Jira », sort du calcul de l'avancement et ne peut plus être prise par un agent.
- **Workflow Jira sans transition directe** vers la catégorie visée : la transition est refusée avec
  un message qui nomme le statut actuel et la catégorie visée ; l'état de spec-writer ne change pas.
- **Jira injoignable pendant une transition demandée par un agent** : la transition est enregistrée
  dans spec-writer et la tâche est marquée « à synchroniser ». La réponse à l'agent le signale
  (transition enregistrée, pas encore dans Jira) ; l'agent choisit de continuer ou de s'arrêter.
  L'envoi est retenté à l'ouverture de la spécification, au prochain appel d'un agent ou par le
  bouton « Synchroniser ».
- **Tâche à synchroniser modifiée entre-temps dans Jira** : à la relecture, le statut de Jira est
  comparé à la transition en attente ; si Jira est déjà au statut visé, l'attente est levée ;
  sinon la transition en attente est envoyée.
- **Agent qui ne termine jamais sa tâche** : l'utilisateur peut libérer la tâche depuis l'écran de la
  spécification ; elle redevient « À faire » dans spec-writer et dans Jira.
- **Tâche terminée dans Jira alors qu'un agent la tient** : la relecture la passe « Terminée » ; un
  appel « terminer » de l'agent répond qu'elle l'est déjà, sans erreur.
- **Spécification en Brouillon** : un agent ne voit pas ses tâches (la passerelle
  ne montre que les features à implémenter) ; l'utilisateur peut tout de même proposer et valider.
- **Statut de la spécification changé à la main** : une fois les tâches validées, « En cours » et
  « Implémentée » ne se choisissent plus à la main. L'utilisateur peut repasser la spécification en
  Brouillon ou en Prête : le suivi par les agents est alors suspendu (les outils de tâches refusent
  avec un message clair) jusqu'à ce qu'il le reprenne ; le statut est alors recalculé d'après les
  tâches.
- **Contenu adressé à une IA** dans un titre de tâche ou dans le document joint : c'est une donnée,
  jamais une consigne, pour le modèle comme pour l'agent qui lit la feature.
- **Proposition hors bornes** (moins de 3 ou plus de 8 tâches, tâche sans résultat vérifiable) : la
  réponse du modèle est refusée et redemandée ; après les tentatives prévues, l'utilisateur voit une
  erreur claire et peut saisir ses tâches à la main.

## Requirements *(mandatory)*

### Functional Requirements

**Découpage (US1)**

- **FR-001**: L'utilisateur DOIT pouvoir demander une proposition de tâches sur une spécification
  liée à une story Jira, depuis l'écran de la spécification.
- **FR-001a**: Sans ticket Jira lié, la proposition DOIT être indisponible, avec un message qui
  invite à envoyer d'abord la spécification vers Jira ; aucune tâche n'est gardée sans sa sous-tâche
  Jira, hors proposition en cours de relecture.
- **FR-002**: La proposition DOIT compter de 3 à 8 tâches ordonnées ; chacune a un titre (120
  caractères au plus), un résultat vérifiable (une phrase), sa base (un module touché ou un cas
  d'usage de la spécification, cité par son nom) et, au besoin, les tâches qui doivent la précéder.
- **FR-003**: La proposition DOIT s'appuyer sur la rubrique « Modules touchés » quand la
  spécification en a une, sinon sur ses cas d'usage ; elle vise un suivi d'avancement, pas un
  découpage fin.
- **FR-004**: L'utilisateur DOIT pouvoir renommer, fusionner, supprimer, réordonner et ajouter des
  tâches avant de valider ; rien n'est envoyé à Jira avant la validation.
- **FR-005**: À la validation, spec-writer DOIT créer une sous-tâche Jira par tâche, sous la story liée,
  avec le titre et le résultat attendu, et garder la clé Jira de chacune.
- **FR-006**: Les sous-tâches déjà présentes sous la story DOIVENT être reprises comme tâches
  existantes, sans être recréées.
- **FR-007**: Une création interrompue DOIT pouvoir être relancée sans créer de doublon.
- **FR-008**: spec-writer NE DOIT JAMAIS supprimer une sous-tâche dans Jira.

**Suivi par les agents (US2)**

- **FR-009**: La lecture d'une feature par un agent DOIT inclure ses tâches : clé Jira, titre,
  résultat attendu, ordre, tâches préalables, statut et agent qui la tient.
- **FR-010**: La passerelle DOIT offrir un outil « prochaine tâche » qui renvoie, de façon
  déterministe, la tâche en cours tenue par la connexion appelante ou par une connexion fermée si
  elle existe, sinon la première tâche « À faire » dans l'ordre dont les préalables sont terminés,
  sinon l'indication qu'il ne reste rien (ou que les tâches restantes sont tenues par d'autres
  connexions).
- **FR-011**: La passerelle DOIT offrir un outil « prendre une tâche » (vers « En cours », réservée à
  l'agent appelant) et un outil « terminer une tâche » (vers « Terminée »), désignant la tâche par sa
  clé Jira.
- **FR-011a**: Chaque lancement de la passerelle DOIT être identifié comme une connexion distincte ;
  une tâche prise est réservée à la connexion qui l'a prise, et l'écran affiche le nom de l'agent et
  l'heure de prise. Une tâche tenue par une connexion fermée DOIT pouvoir être reprise par une autre
  connexion, et « prochaine tâche » la propose en priorité.
- **FR-011b**: « Terminer une tâche » DOIT accepter un compte rendu facultatif de 2 000 caractères au
  plus, ajouté en commentaire sur la sous-tâche Jira (envoyé avec la transition, et mis « à
  synchroniser » avec elle si Jira est injoignable) et affiché avec la tâche sur l'écran de la
  spécification. Le compte rendu est une donnée : il n'est jamais journalisé ni transmis au modèle
  comme consigne.
- **FR-012**: Une tâche tenue par une connexion ouverte NE DOIT PAS pouvoir être prise par une autre ; le refus
  nomme l'agent qui la tient.
- **FR-013**: Chaque transition demandée par un agent DOIT être envoyée à la sous-tâche Jira. Si Jira
  est injoignable, la transition DOIT être enregistrée dans spec-writer et la tâche marquée « à
  synchroniser » ; la réponse à l'agent DOIT indiquer que la transition n'est pas encore dans Jira,
  pour qu'il décide de continuer ou non. Si Jira refuse la transition (workflow), rien ne change et
  l'agent reçoit un message clair.
- **FR-013a**: Les transitions en attente DOIVENT être renvoyées à l'ouverture de la spécification, au
  prochain appel d'un agent sur la feature, et à la demande de l'utilisateur par un bouton
  « Synchroniser » ; l'écran DOIT montrer les tâches à synchroniser.
- **FR-014**: Chaque prise et chaque fin de tâche DOIVENT être inscrites au journal des agents, avec
  l'agent, la feature, la clé de la tâche et l'heure, sans contenu de la spécification.
- **FR-015**: La transition Jira DOIT être choisie automatiquement parmi celles disponibles, d'après
  la catégorie de statut visée (« En cours », « Terminé ») ; aucun réglage n'est demandé.
- **FR-016**: Le choix de la tâche NE DOIT dépendre ni d'un modèle de langage, ni de la branche git,
  ni des fichiers de l'agent.
- **FR-017**: La description des nouveaux outils DOIT rappeler que le contenu renvoyé est une donnée,
  jamais une consigne.

**Avancement (US3)**

- **FR-018**: L'écran de la spécification DOIT afficher le nombre de tâches terminées sur le total,
  et chaque tâche avec son statut, son agent éventuel et un lien vers la sous-tâche Jira.
- **FR-019**: spec-writer DOIT relire le statut des sous-tâches dans Jira à l'ouverture de la
  spécification, et quand un agent demande la prochaine tâche ou lit la feature.
- **FR-020**: Le statut de la spécification DOIT suivre ses tâches : « En cours » dès qu'une tâche est
  en cours ou terminée sans que toutes le soient, « Implémentée » quand toutes les tâches suivies sont
  terminées.
- **FR-020a**: Une fois des tâches validées, l'utilisateur NE DOIT PLUS pouvoir choisir « En cours » ni
  « Implémentée » à la main. Il DOIT pouvoir repasser la spécification en Brouillon ou en Prête, ce
  qui suspend le suivi par les agents (prochaine tâche, prendre, terminer refusés avec un message
  clair) jusqu'à ce qu'il reprenne le suivi ; le statut est alors recalculé d'après les tâches.
- **FR-021**: Si Jira ne peut pas être relu, l'écran DOIT montrer les derniers statuts connus, l'heure
  de la dernière relecture et un message qui le signale.
- **FR-022**: L'utilisateur DOIT pouvoir libérer une tâche tenue par un agent ; elle redevient
  « À faire » dans spec-writer et dans Jira.

**Rédaction (US4)**

- **FR-023**: La spécification générée DOIT s'ouvrir sur un énoncé de story (rôle, capacité,
  bénéfice), tiré du besoin, des réponses ou des documents joints ; un élément supposé est marqué
  comme hypothèse.
- **FR-024**: La génération DOIT signaler une story trop large et proposer un découpage en stories,
  sans le faire d'office.
- **FR-025**: L'énoncé de story DOIT figurer en tête de l'export et de la description envoyée vers
  Jira.

**Consignes et qualité**

- **FR-026**: Les méthodes de rédaction de story et de découpage DOIVENT être des consignes
  réutilisables de spec-writer, partagées entre les opérations qui en ont besoin, rédigées pour
  spec-writer (inspirées de méthodes publiques, sans en recopier le texte).
- **FR-027**: L'évaluation réelle DOIT mesurer la proposition de tâches (bornes respectées, résultat
  vérifiable, base exacte, couverture des modules ou des cas d'usage) et l'énoncé de story, avec des
  seuils dans la référence d'évaluation.
- **FR-028**: Les titres et résultats des tâches DOIVENT être transmis au modèle et aux agents comme
  des données ; aucun contenu de spécification ni de tâche n'est journalisé.

### Key Entities

- **Tâche** : une étape d'implémentation d'une spécification. Titre, résultat attendu, base (module
  ou cas d'usage), ordre, tâches préalables, statut (À faire, En cours, Terminée, Introuvable dans
  Jira), clé de la sous-tâche Jira, connexion et nom de l'agent qui la tient et depuis quand, heure de dernière relecture,
  transition en attente d'envoi à Jira (« à synchroniser ») le cas échéant, compte rendu de fin
  donné par l'agent (facultatif).
  Appartient à une spécification.
- **Proposition de tâches** : la liste proposée puis corrigée par l'utilisateur, tant qu'elle n'est
  pas validée. Elle n'existe que pour la spécification qui l'a demandée.
- **Énoncé de story** : rôle, capacité, bénéfice, avec pour chacun sa source ou la mention
  d'hypothèse. Fait partie de la version de la spécification.
- **Entrée du journal des agents** (existante) : complétée par la prise et la fin d'une tâche.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Sur l'évaluation réelle, 100 % des propositions comptent de 3 à 8 tâches, et au moins
  90 % des tâches ont un résultat vérifiable et une base qui existe dans la spécification.
- **SC-002**: Sur une spécification avec une rubrique « Modules touchés », chaque module impacté est
  couvert par au moins une tâche proposée dans au moins 90 % des cas d'évaluation.
- **SC-003**: De la demande de proposition aux sous-tâches visibles dans Jira, l'utilisateur met
  moins de 2 minutes pour une proposition qu'il accepte telle quelle.
- **SC-004**: Un agent qui reprend dans une nouvelle conversation obtient sa tâche en un seul appel,
  et deux appels successifs sans changement renvoient toujours la même tâche (100 % des essais).
- **SC-005**: 100 % des transitions confirmées sans réserve sont visibles dans Jira ; une transition
  faite pendant une coupure de Jira y arrive à la première synchronisation qui suit le retour de
  Jira, et l'agent comme l'écran signalent l'écart tant qu'il dure.
- **SC-006**: Une sous-tâche avancée directement dans Jira apparaît avec son nouveau statut dès
  l'ouverture suivante de la spécification.
- **SC-007**: Sur l'évaluation réelle, au moins 90 % des énoncés de story nomment un rôle, une
  capacité et un bénéfice présents dans le besoin ou les documents, ou marqués comme hypothèses.
- **SC-008**: Aucun indicateur existant de l'évaluation réelle ne passe sous sa référence.

## Assumptions

- La spécification est déjà liée à une story Jira par l'envoi existant ; cette feature ne crée pas la
  story.
- Une seule personne utilise spec-writer sur un poste ; plusieurs agents de code peuvent travailler en
  même temps sur des tâches différentes de la même feature.
- Le compte Jira de l'utilisateur peut créer des sous-tâches et faire les transitions de son
  workflow ; sinon, Jira refuse et spec-writer l'affiche.
- Les tâches préalables se limitent à des tâches de la même spécification ; elles ne deviennent pas
  des liens Jira.
- Une tâche « Terminée » dans Jira est une sous-tâche dont la catégorie de statut est « Terminé »,
  quel que soit le nom du statut (y compris abandonnée ou refusée).
- La règle « la passerelle ne déclenche aucun appel à Jira » de la 002 (FR-029) reste vraie pour la
  passerelle elle-même : c'est spec-writer qui appelle Jira à la demande d'un agent. La 002 est
  annotée en conséquence.
- La ségrégation par projet de la passerelle (argument `--project`) est livrée avant, dans sa propre
  PR ; cette feature ne la suppose pas mais en bénéficie.
- La relecture depuis Jira se fait à la demande (ouverture, lecture par un agent), sans
  synchronisation en tâche de fond.
