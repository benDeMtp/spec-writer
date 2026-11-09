# Feature Specification: Agent conversationnel, point d'entrée principal

**Feature Branch**: `feature/014-agent-conversationnel`

**Created**: 2026-11-09

**Status**: Draft

**Input**: User description: "on va changer quelque chose de structurant. A présent je veux un agent conversationnel comme point d'entrée principal. Je choisis le contexte d'un projet, et je chatte avec l'agent. Nous conversons à propos de ce qu'il sait du projet. À partir de la discussion nous pouvons commencer à rédiger une spécification. Mais ce n'est pas le seul usage, nous pouvons juste obtenir des informations via le RAG et faire de la recherche documentaire. Il peut analyser les incohérences dans les documentations. Du coup le menu change. Par défaut nous sommes sur une interface de dialogue. Utilise /find-skills pour trouver une compétence permettant de critiquer l'interface et d'avoir une meilleure UX."

Change l'entrée de l'outil : aujourd'hui on arrive sur la recherche et on démarre une spécification
par « Nouveau besoin ». Désormais, on arrive sur une conversation avec un agent qui connaît le
projet courant. S'appuie sur la connaissance du projet (`specs/002-projets-contexte-mcp/` : index
de Jira, de la documentation et de l'architecture, fiche générale), les dossiers synchronisés
(006), les mentions @ (007), le budget selon le modèle (008) et les documents joints (009), dont
les décisions restent valables. Le questionnaire et la génération de la 001 restent la voie de
rédaction d'une spécification.

## Clarifications

### Session 2026-11-09

- Q: Par quel chemin la conversation mène-t-elle à une spécification ? → A: Par le questionnaire actuel, besoin prérempli ; les questions auxquelles la conversation a déjà répondu ne sont pas reposées.
- Q: Que deviennent les entrées « Recherche » et « Nouveau besoin » du menu ? → A: Elles quittent le menu et vont dans l'écran des spécifications (bouton « Nouvelle spécification », recherche dans la liste).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Questionner l'agent sur le projet (Priority: P1) 🎯 MVP

L'utilisateur ouvre l'outil : il arrive sur la conversation, avec le projet courant affiché. Il
demande « Comment sont émis les avoirs aujourd'hui ? ». L'agent cherche dans la connaissance du
projet (tickets, pages, ADR, documents du dossier synchronisé, architecture) et répond en citant
ses sources ; chaque source s'ouvre (ticket, page, ADR) ou se nomme (document). L'utilisateur
relance : « Et qui consomme l'événement avoir-émis ? » ; l'agent tient compte de l'échange
précédent.

**Why this priority**: c'est le nouveau point d'entrée ; sans lui, rien d'autre de la feature
n'existe. Il rend utile la connaissance déjà indexée sans passer par une spécification.

**Independent Test**: avec un projet indexé et l'IA rejouée, poser une question puis une relance ;
vérifier la réponse, les sources citées et que la relance s'appuie sur l'échange.

**Acceptance Scenarios**:

1. **Given** un projet indexé et Copilot connecté avec l'accord donné, **When** l'utilisateur ouvre
   l'outil, **Then** la conversation est l'écran affiché, le projet courant est visible et
   modifiable, et le champ de saisie a le focus.
2. **Given** une question dont la réponse est dans la connaissance du projet, **When** l'utilisateur
   l'envoie, **Then** l'agent répond en citant au moins une source de la connaissance, et chaque
   source citée existe dans le projet.
3. **Given** une question sans réponse dans la connaissance du projet, **When** l'utilisateur
   l'envoie, **Then** l'agent dit qu'il ne trouve pas l'information dans le projet, sans inventer.
4. **Given** une réponse précédente, **When** l'utilisateur pose une relance qui y fait référence,
   **Then** l'agent tient compte de l'échange précédent.
5. **Given** l'accord Copilot non donné, **When** l'utilisateur envoie son premier message, **Then**
   l'accord est demandé avant tout envoi, comme pour un besoin.
6. **Given** une réponse en cours, **When** l'agent travaille, **Then** l'écran annonce qu'il
   cherche puis qu'il répond, et l'utilisateur peut arrêter la réponse.

---

### User Story 2 - De la conversation à une spécification (Priority: P1)

Au fil de l'échange, l'utilisateur dit « Rédigeons la spécification de la saisie d'un avoir ».
L'agent propose un besoin rédigé à partir de la conversation ; l'utilisateur le relit, le corrige
et le valide. La rédaction de la spécification démarre alors à partir de ce besoin, avec ce que la
conversation a établi.

**Why this priority**: la rédaction de spécifications reste la raison d'être de l'outil ; la
conversation doit y mener sans ressaisie.

**Independent Test**: après deux échanges sur un sujet, demander la rédaction ; vérifier le besoin
proposé, sa correction, puis le démarrage de la spécification et ce qu'elle reprend de l'échange.

**Acceptance Scenarios**:

1. **Given** une conversation sur un sujet, **When** l'utilisateur demande de rédiger une
   spécification, **Then** l'agent propose un besoin rédigé, modifiable, sans rien démarrer.
2. **Given** le besoin proposé, **When** l'utilisateur le valide, **Then** une
   spécification démarre dans le questionnaire actuel avec ce besoin, et les questions auxquelles la
   conversation a déjà répondu ne sont pas reposées.
3. **Given** une spécification démarrée depuis une conversation, **When** l'utilisateur la consulte,
   **Then** elle indique la conversation dont elle vient, et la conversation montre le lien vers la
   spécification.
4. **Given** des documents mentionnés avec @ ou joints dans la conversation, **When** la
   spécification démarre, **Then** ils sont repris comme documents désignés de la spécification.

---

### User Story 3 - Recherche documentaire et incohérences (Priority: P2)

L'utilisateur demande « Les règles de lettrage sont-elles cohérentes entre la documentation et les
tickets ? ». L'agent compare les sources qui en parlent et liste les incohérences trouvées : pour
chacune, ce que dit chaque source, avec sa référence, et pourquoi elles divergent. Il dit aussi ce
qu'il n'a pas pu comparer.

**Why this priority**: c'est un usage nouveau à forte valeur (documentation dispersée entre Jira,
Antora et SharePoint), mais il s'appuie sur la conversation de l'US1.

**Independent Test**: avec deux documents indexés qui se contredisent sur une règle, demander une
analyse de cohérence ; vérifier que la contradiction est signalée avec les deux sources.

**Acceptance Scenarios**:

1. **Given** deux sources du projet qui se contredisent, **When** l'utilisateur demande une analyse
   de cohérence sur ce sujet, **Then** l'agent signale la contradiction en citant les deux sources et
   ce que dit chacune.
2. **Given** des sources cohérentes, **When** l'utilisateur demande l'analyse, **Then** l'agent dit
   n'avoir trouvé aucune incohérence, en citant les sources comparées.
3. **Given** plus de sources que la place transmise à l'IA, **When** l'analyse est faite, **Then**
   l'agent dit quelles sources n'ont pas été comparées.
4. **Given** un document désigné avec @ ou joint, **When** l'utilisateur demande s'il est cohérent
   avec le reste du projet, **Then** l'agent compare ce document au reste de la connaissance.

---

### User Story 4 - Retrouver et reprendre une conversation (Priority: P2)

L'utilisateur revient le lendemain : il retrouve la liste de ses conversations du projet courant,
nommées d'après leur sujet, en ouvre une et la poursuit. Il en renomme une, en supprime une autre.

**Why this priority**: sans reprise, une analyse ou une préparation de spécification est perdue à la
fermeture ; la reprise est attendue de tout outil de dialogue.

**Independent Test**: créer deux conversations, redémarrer l'outil, les retrouver, en poursuivre
une, en supprimer une.

**Acceptance Scenarios**:

1. **Given** des conversations passées dans le projet courant, **When** l'utilisateur ouvre la
   conversation, **Then** il voit la liste de ses conversations, la plus récente d'abord, et peut en
   commencer une nouvelle.
2. **Given** une conversation passée, **When** l'utilisateur l'ouvre et envoie un message, **Then**
   l'agent tient compte de l'échange enregistré.
3. **Given** une conversation, **When** l'utilisateur la supprime après confirmation, **Then** elle
   disparaît de l'outil et de ses données ; les spécifications démarrées depuis elle restent.
4. **Given** un changement de projet courant, **When** l'utilisateur ouvre la conversation, **Then**
   il ne voit que les conversations de ce projet, et l'agent ne connaît que ce projet.

---

### User Story 5 - Nouveau menu (Priority: P2)

Le menu principal place la conversation en premier et en écran d'accueil ; les autres écrans
restent accessibles.

**Why this priority**: le changement de point d'entrée doit se voir dans la navigation ; les écrans
existants ne doivent pas devenir introuvables.

**Independent Test**: ouvrir l'outil, parcourir le menu au clavier, atteindre chaque écran.

**Acceptance Scenarios**:

1. **Given** l'outil ouvert à son adresse d'accueil, **When** la page s'affiche, **Then** c'est la
   conversation, et l'entrée « Conversation » est la première du menu.
2. **Given** le menu, **When** l'utilisateur le parcourt, **Then** il atteint les spécifications, les
   tickets Jira, les projets, le journal et la configuration ; « Recherche » et « Nouveau besoin »
   ne sont plus des entrées du menu.
3. **Given** une ancienne adresse (marque-page vers la recherche ou vers « Nouveau besoin »),
   **When** l'utilisateur l'ouvre, **Then** il arrive sur un écran utile, jamais sur une page vide.
4. **Given** l'écran des spécifications, **When** l'utilisateur veut démarrer une spécification sans
   passer par l'agent ou en retrouver une, **Then** il dispose d'un bouton « Nouvelle spécification »
   (l'écran du besoin actuel) et de la recherche dans la liste.

### Edge Cases

- Copilot non connecté ou accord retiré : la conversation explique quoi faire (connexion, accord)
  et le lien vers la configuration ; les conversations passées restent lisibles.
- Projet sans connaissance indexée : l'agent le dit et propose d'indexer le projet ; il répond
  quand même à partir de la fiche générale si elle existe.
- Question sans rapport avec le projet : l'agent répond qu'il s'en tient au projet.
- Message très long ou collé depuis un document : borné, l'utilisateur est prévenu de la limite.
- Conversation très longue : les échanges les plus anciens ne sont plus tous transmis à l'IA ;
  l'outil le signale, et la conversation reste entière à l'écran.
- Réponse interrompue (arrêt demandé, panne de Copilot, fermeture) : la réponse partielle est
  marquée comme incomplète ; le message de l'utilisateur n'est pas perdu et peut être renvoyé.
- Contenu hostile dans la connaissance du projet ou dans un document joint (« ignore tes
  consignes ») : il reste une donnée et ne change pas le comportement de l'agent.
- Source citée par l'agent introuvable dans le projet : la citation n'est pas montrée comme une
  source.
- Feature implémentée : la conversation peut en parler, mais ne modifie pas sa spécification.

## Requirements *(mandatory)*

### Functional Requirements

**Conversation**

- **FR-001**: L'écran d'accueil de l'outil DOIT être la conversation avec l'agent, sur le projet
  courant affiché et modifiable depuis cet écran.
- **FR-002**: L'agent DOIT répondre à partir de la connaissance du projet courant (fiche générale,
  tickets, pages, ADR, documents des dossiers synchronisés, architecture), recherchée à chaque
  message selon la question et l'échange en cours.
- **FR-003**: Chaque réponse DOIT citer les éléments de la connaissance sur lesquels elle s'appuie ;
  seules les références existant dans le projet sont montrées comme sources ; un ticket, une page
  ou un ADR s'ouvre depuis sa citation.
- **FR-004**: Quand la connaissance ne contient pas la réponse, l'agent DOIT le dire et NE DOIT PAS
  inventer de fait sur le projet.
- **FR-005**: L'agent DOIT tenir compte des échanges précédents de la conversation, dans la limite
  de la place du modèle choisi (008) ; quand des échanges anciens ne sont plus transmis, l'écran DOIT
  le signaler.
- **FR-006**: L'utilisateur DOIT pouvoir désigner des documents avec @ (007) et joindre des
  documents (009) dans la conversation ; leur état (transmis, en partie, plus de place) DOIT
  s'afficher comme aujourd'hui.
- **FR-007**: Pendant une réponse, l'écran DOIT annoncer l'avancement (recherche, rédaction) de
  façon accessible, et l'utilisateur DOIT pouvoir l'arrêter ; une réponse arrêtée ou en échec est
  marquée incomplète et le message peut être renvoyé.
- **FR-008**: Aucun message NE DOIT être envoyé à Copilot sans l'accord de l'utilisateur ; l'accord
  est demandé au premier envoi s'il manque.

**Spécification**

- **FR-009**: Sur demande de l'utilisateur, l'agent DOIT proposer un besoin rédigé à partir de la
  conversation, que l'utilisateur relit, modifie et valide ; rien ne démarre sans cette validation.
- **FR-010**: La validation DOIT démarrer une spécification dans le projet courant par le
  questionnaire actuel, avec le besoin validé ; ce que la conversation a établi est transmis à l'IA,
  et une question dont la réponse est déjà connue par la conversation NE DOIT PAS être posée
  (constitution, principe I). Les documents désignés et joints de la conversation sont repris.
- **FR-011**: Une spécification démarrée depuis une conversation DOIT garder le lien vers elle, et
  la conversation DOIT montrer le lien vers la spécification.
- **FR-012**: Les règles de rédaction existantes restent valables : scénarios structurés, traçabilité
  au besoin et aux réponses, hypothèses marquées (constitution, principe II).

**Recherche et cohérence**

- **FR-013**: L'agent DOIT répondre aux demandes de recherche documentaire (« où est décrit… »,
  « quels tickets parlent de… ») par une réponse et la liste des sources trouvées.
- **FR-014**: Sur demande, l'agent DOIT comparer les sources d'un sujet et lister les incohérences :
  pour chacune, ce que dit chaque source, avec sa référence, et la nature de l'écart ; il DOIT dire
  quelles sources n'ont pas pu être comparées faute de place.
- **FR-015**: L'agent NE DOIT modifier aucune source (Jira, documentation, documents) : il lit et
  signale seulement.

**Conversations**

- **FR-016**: Les conversations DOIVENT être enregistrées localement, rattachées à un projet, listées
  de la plus récente à la plus ancienne, nommées d'après leur sujet, renommables et supprimables
  après confirmation.
- **FR-017**: Une conversation DOIT pouvoir être reprise après un redémarrage de l'outil, sans perte.
- **FR-018**: Supprimer une conversation DOIT effacer ses messages et ses documents joints des
  données de l'outil ; les spécifications démarrées depuis elle restent, sans lien.
- **FR-019**: Supprimer un projet DOIT supprimer ses conversations.

**Navigation**

- **FR-020**: Le menu principal DOIT commencer par « Conversation », écran d'accueil ; les écrans des
  spécifications, des tickets Jira, des projets, du journal et de la configuration DOIVENT rester
  accessibles depuis le menu. « Recherche » et « Nouveau besoin » quittent le menu : l'écran des
  spécifications porte un bouton « Nouvelle spécification » (écran du besoin actuel) et la recherche
  dans les spécifications.
- **FR-021**: Les anciennes adresses DOIVENT mener à un écran utile : l'ancienne adresse de la
  recherche à la conversation, celle de « Nouveau besoin » à l'écran du besoin.
- **FR-022**: L'interface de conversation DOIT respecter WCAG 2.1 AA : navigation au clavier
  (envoi, arrêt, liste des conversations, citations), annonces des réponses aux lecteurs d'écran
  sans relire tout l'historique, contrastes, libellés.
- **FR-023**: L'interface DOIT être revue avant livraison par une critique d'ergonomie formelle
  (règles d'interface web reconnues) ; les écarts relevés sont corrigés ou justifiés.

**Sécurité et confidentialité**

- **FR-024**: Les messages et les réponses DOIVENT être traités comme des données confidentielles :
  jamais journalisés en clair, ni exposés hors de l'outil, sauf ce que l'utilisateur exporte ou
  envoie lui-même.
- **FR-025**: Les messages DOIVENT être bornés en taille ; le contenu de la conversation et de la
  connaissance DOIT rester séparé des consignes de l'agent, et l'injection par un message, une source
  ou un document joint DOIT être couverte par des tests.
- **FR-026**: L'agent DOIT n'utiliser que la connaissance du projet courant ; aucune donnée d'un
  autre projet ne doit apparaître dans ses réponses.

### Key Entities

- **Conversation** : échange suivi entre l'utilisateur et l'agent dans un projet ; nom, dates de
  création et de dernière activité, messages, documents joints, spécifications démarrées depuis
  elle.
- **Message** : tour de parole de l'utilisateur ou de l'agent ; texte, date, état (complet,
  incomplet), et pour l'agent les sources citées.
- **Source citée** : élément de la connaissance du projet sur lequel une réponse s'appuie (ticket,
  page, ADR, document, élément d'architecture, document joint), avec sa référence.
- **Besoin proposé** : besoin rédigé par l'agent à partir de la conversation, modifiable, à valider
  avant de démarrer une spécification.
- **Incohérence** : écart entre deux sources ou plus sur un même sujet ; sources, ce que dit chacune,
  nature de l'écart.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: À l'ouverture de l'outil, l'utilisateur peut poser une question sans aucune navigation
  préalable (0 clic avant la saisie).
- **SC-002**: Sur un jeu de questions de référence dont la réponse est dans la connaissance du
  projet, au moins 90 % des réponses citent une source pertinente, et aucune source citée n'est
  inventée.
- **SC-003**: Sur un jeu de questions dont la réponse n'est pas dans le projet, au moins 90 % des
  réponses disent ne pas savoir plutôt que d'inventer.
- **SC-004**: Sur un jeu de sources contenant des contradictions connues, au moins 80 % des
  contradictions sont signalées avec leurs deux sources.
- **SC-005**: Passer d'une conversation à une spécification démarrée demande au plus 3 actions de
  l'utilisateur après la demande de rédaction.
- **SC-006**: Le premier signe d'activité de l'agent apparaît en moins d'une seconde après l'envoi ;
  une réponse courte est complète en moins de 20 secondes avec un modèle courant.
- **SC-007**: Une conversation de 50 échanges se rouvre et se poursuit après un redémarrage sans
  perte de message.
- **SC-008**: La critique d'ergonomie et l'audit d'accessibilité automatisé ne relèvent aucun écart
  bloquant sur la conversation et le menu.

## Assumptions

- Copilot reste le seul fournisseur d'IA ; le budget de connaissance transmis suit le modèle choisi
  (008).
- Un seul utilisateur local, comme aujourd'hui ; pas de partage de conversation.
- L'agent lit la connaissance déjà indexée ; il ne lance pas d'indexation ni de synchronisation
  lui-même (il peut la proposer).
- L'agent ne crée ni ne modifie de ticket Jira : l'envoi vers Jira reste une action de l'écran de la
  spécification.
- La passerelle des agents de code (MCP) ne change pas.
- Les conversations ne sont pas exportées dans cette feature.
- La critique d'ergonomie s'appuie sur une compétence reconnue de revue d'interface (règles
  d'interface web de Vercel, conception d'interface d'Anthropic), installée pour la phase de
  conception et de revue.
- Le jeu d'évaluation de l'IA est étendu aux réponses de l'agent (SC-002 à SC-004) ; une évaluation
  réelle se fera avec l'accord du mainteneur.
