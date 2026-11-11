# Feature Specification: Discuter d'une spécification avec l'agent

**Feature Branch**: `feature/015-discussion-specification`

**Created**: 2026-11-10

**Status**: Draft

**Input**: User description: "Dans les spécifications, « Faire évoluer avec l'IA » (consigne en un coup, proposition, différences, appliquer) devient une discussion comme l'écran Conversation, dont le contexte est prérempli avec la spécification sur laquelle on se trouve. « Nouveau besoin » reste tel quel. Décisions déjà prises : la discussion s'affiche à côté de la spécification (panneau latéral, sous le document sur petit écran) ; une seule discussion suivie par spécification, reprise à chaque retour, « Nouvelle discussion » repart de zéro et l'ancienne reste dans la liste des conversations ; la version affichée est transmise entière à chaque message, en tête des sources, l'agent garde la recherche dans la connaissance du projet, les citations, les incohérences (entre la spécification et le reste du projet), la mémoire, la réflexion, le chronomètre, les mentions @ et les documents joints ; « Proposer une nouvelle version » fait réécrire la spécification par l'agent d'après la discussion, puis la relecture actuelle (différences, appliquer, abandonner) crée la version suivante, marquée « Issue de la discussion » avec un lien vers la conversation ; après une nouvelle version, l'agent passe à cette version (repère « Contexte : version N » dans le fil) ; la fusion IA d'un conflit Jira reste une proposition directe ; une spécification verrouillée (implémentée) ne se discute pas ; « Faire évoluer avec l'IA » depuis un ticket Jira importe le ticket puis ouvre la discussion. Feature 011, à part de la 010 (PR #12)."

## Contexte

Aujourd'hui, « Faire évoluer avec l'IA » demande une consigne, l'IA propose aussitôt une nouvelle
version, et l'utilisateur relit les différences avant de l'appliquer. Il ne peut ni questionner
l'IA, ni vérifier ce que le projet dit déjà du sujet, ni affiner la consigne avant la proposition.

La feature 010 a introduit l'agent conversationnel : recherches dans la connaissance du projet,
réponses citées, incohérences, mémoire, réflexion visible. Cette feature applique cet agent à une
spécification existante : on en discute, puis on demande une nouvelle version.

## Clarifications

### Session 2026-11-10

- Q: Quand l'utilisateur consulte une version plus ancienne, sur quelle version porte la discussion ? → A: Toujours la dernière version ; le panneau le signale quand on consulte une version plus ancienne.
- Q: Que deviennent les discussions d'une spécification supprimée ? → A: Au choix de l'utilisateur dans la confirmation (case « Supprimer aussi ses discussions »).
- Q: Quel libellé pour le bouton qui ouvre la discussion ? → A: « Discuter avec l'IA », sur l'écran de la spécification et sur celui d'un ticket Jira.
- Q: Comment les discussions de spécification apparaissent-elles dans la liste des conversations ? → A: Dans la même liste, par date, avec la mention « Spécification : <nom> ».
- Q: Au clic sur « Proposer une nouvelle version », l'utilisateur peut-il ajouter une précision ? → A: Non : la proposition se fonde sur la discussion seule ; une précision passe par un message.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Discuter d'une spécification à côté du document (Priority: P1) 🎯 MVP

Sur l'écran d'une spécification, l'utilisateur ouvre la discussion avec « Discuter avec
l'IA ». Un panneau s'ouvre à côté du document ; il indique que l'agent connaît la spécification
(« Contexte : <nom>, version N »). L'utilisateur pose des questions sur la spécification
(« Que se passe-t-il si la salle est déjà prise ? », « Ce scénario contredit-il un ADR ? »).
L'agent répond comme dans l'écran Conversation : il cherche dans la connaissance du projet, cite
ses sources (la spécification elle-même en premier), signale les incohérences entre la
spécification et le reste du projet, montre sa réflexion et le temps écoulé.

**Why this priority**: c'est le cœur de la demande ; sans la discussion préremplie, rien d'autre
n'a de sens. Seule, elle apporte déjà de la valeur : relire et contrôler une spécification avec
l'agent.

**Independent Test**: avec une spécification et un projet indexé, l'IA rejouée, ouvrir la
discussion, poser une question sur un scénario, vérifier que la réponse cite la spécification et
une autre source du projet, sans quitter l'écran de la spécification.

**Acceptance Scenarios**:

1. **Given** une spécification en version 3, **When** l'utilisateur clique « Discuter avec
   l'IA », **Then** un panneau de discussion s'ouvre à côté du document, avec le repère
   « Contexte : <nom>, version 3 », et le champ de saisie a le focus.
2. **Given** la discussion ouverte, **When** l'utilisateur demande « Quels scénarios couvrent
   l'annulation ? », **Then** l'agent répond en citant la spécification ; un appel de note vers la
   spécification mène à la section concernée du document affiché.
3. **Given** un ADR du projet qui fixe un délai différent de celui de la spécification, **When**
   l'utilisateur demande si la spécification est cohérente avec le projet, **Then** l'agent
   signale l'écart avec ses deux sources (la spécification et l'ADR).
4. **Given** une réponse en cours, **When** l'utilisateur regarde le panneau, **Then** il voit
   les étapes de recherche, le panneau de réflexion dont le titre suit l'action de l'agent, et le
   chronomètre ; il peut arrêter la réponse.
5. **Given** un petit écran, **When** la discussion est ouverte, **Then** le panneau passe sous le
   document, et le document reste lisible.
6. **Given** une spécification implémentée (verrouillée), **When** l'utilisateur affiche la
   spécification, **Then** « Discuter avec l'IA » est indisponible et l'écran indique
   comment faire évoluer (« Créer une évolution »).

---

### User Story 2 - Proposer une nouvelle version d'après la discussion (Priority: P1)

Quand la discussion a établi ce qui doit changer, l'utilisateur clique « Proposer une nouvelle
version ». L'agent réécrit la spécification d'après ce qui a été établi. L'utilisateur relit les
différences, comme aujourd'hui, puis applique ou abandonne. Appliquer crée la version suivante,
marquée « Issue de la discussion », avec un lien vers la conversation. L'agent passe à cette
version pour la suite de l'échange.

**Why this priority**: c'est ce qui remplace la fonction actuelle ; sans elle, la discussion ne
fait pas évoluer la spécification.

**Independent Test**: après deux échanges qui établissent un cas d'erreur à ajouter, proposer une
nouvelle version, vérifier que les différences ajoutent ce cas sans toucher au reste, appliquer,
vérifier la version suivante, son origine et le repère dans le fil.

**Acceptance Scenarios**:

1. **Given** une discussion qui a établi « une salle déjà réservée renvoie un message d'erreur »,
   **When** l'utilisateur clique « Proposer une nouvelle version », **Then** l'agent rédige une
   proposition, le chronomètre tourne pendant la rédaction, puis les différences avec la version
   courante s'affichent avec « Appliquer » et « Abandonner ».
2. **Given** une proposition affichée, **When** l'utilisateur applique, **Then** la version
   suivante est créée, marquée « Issue de la discussion » dans l'historique des versions, avec un
   lien vers la conversation ; le fil montre le repère « Contexte : version 4 ».
3. **Given** une proposition affichée, **When** l'utilisateur abandonne, **Then** aucune version
   n'est créée et la discussion continue sur la version courante.
4. **Given** une discussion qui n'a rien établi de nouveau, **When** l'utilisateur demande une
   proposition, **Then** l'agent dit qu'aucun changement n'a été établi et ne propose pas de
   version.
5. **Given** une proposition, **When** l'utilisateur la relit, **Then** les parties de la
   spécification dont la discussion n'a pas parlé sont inchangées, et chaque scénario ajouté ou
   modifié est rattaché à un point établi par la discussion, à une source citée, ou marqué comme
   hypothèse.
6. **Given** la version 4 appliquée, **When** l'utilisateur pose une nouvelle question, **Then**
   l'agent répond d'après la version 4.

---

### User Story 3 - Reprendre ou recommencer la discussion d'une spécification (Priority: P2)

Chaque spécification a une discussion suivie. En revenant sur la spécification, même après un
redémarrage, l'utilisateur retrouve la discussion là où il l'a laissée. « Nouvelle discussion »
repart de zéro ; l'ancienne reste dans la liste des conversations, avec le nom de sa
spécification.

**Why this priority**: une évolution se prépare souvent en plusieurs fois ; sans reprise,
l'analyse est perdue. Recommencer évite qu'une discussion longue et dépassée pèse sur les
réponses.

**Independent Test**: discuter d'une spécification, redémarrer l'outil, rouvrir la
spécification, retrouver la discussion ; démarrer une nouvelle discussion, retrouver l'ancienne
dans la liste des conversations.

**Acceptance Scenarios**:

1. **Given** une discussion de trois échanges sur une spécification, **When** l'utilisateur
   rouvre la spécification après un redémarrage, **Then** la discussion s'affiche entière et se
   poursuit.
2. **Given** une discussion en cours, **When** l'utilisateur clique « Nouvelle discussion »,
   **Then** le panneau repart vide, toujours sur la spécification, et l'ancienne discussion reste
   dans la liste des conversations du projet, avec le nom de sa spécification.
3. **Given** la liste des conversations, **When** l'utilisateur ouvre une discussion de
   spécification, **Then** l'écran Conversation l'affiche avec son contexte et un lien vers la
   spécification.
4. **Given** une spécification qui a des discussions, **When** l'utilisateur demande sa
   suppression, **Then** la confirmation propose la case « Supprimer aussi ses discussions »,
   cochée par défaut ; cochée, les discussions sont supprimées avec la spécification ; décochée,
   elles restent dans la liste des conversations, marquées « spécification supprimée », lisibles
   et continuables sans le contexte de la spécification.

---

### User Story 4 - Discuter d'un ticket Jira importé (Priority: P3)

Depuis l'écran d'un ticket Jira, « Discuter avec l'IA » importe le ticket comme
spécification (comme aujourd'hui), puis ouvre cette spécification avec la discussion prête.

**Why this priority**: c'est le second point d'entrée de la fonction actuelle ; il doit suivre le
même parcours, mais il sert moins souvent.

**Independent Test**: depuis un ticket Jira simulé, cliquer « Discuter avec l'IA », vérifier
l'import, puis l'ouverture de la spécification avec la discussion ouverte sur la version
importée.

**Acceptance Scenarios**:

1. **Given** un ticket Jira affiché, **When** l'utilisateur clique « Discuter avec l'IA »,
   **Then** le ticket est importé comme spécification liée, sans rien écrire dans Jira, et la
   spécification s'ouvre avec la discussion ouverte.
2. **Given** un ticket déjà importé, **When** l'utilisateur clique de nouveau, **Then** la
   spécification existante s'ouvre avec sa discussion, sans second import.

---

### Edge Cases

- Copilot non connecté ou accord retiré : la discussion explique quoi faire, comme l'écran
  Conversation ; le document reste lisible et la discussion passée aussi.
- Version plus ancienne affichée : la discussion porte toujours sur la dernière version ; le
  panneau le dit (« La discussion porte sur la version 4, la plus récente »).
- Nouvelle version créée hors de la discussion (régénération, import depuis Jira, fusion d'un
  conflit) pendant qu'elle est ouverte : le message suivant porte sur la nouvelle version, et le
  fil montre le repère « Contexte : version N ».
- Proposition demandée pendant que l'agent répond : indisponible jusqu'à la fin de la réponse.
- Proposition affichée, puis nouveau message : la proposition reste à appliquer ou à abandonner
  avant d'en demander une autre ; la discussion continue.
- Spécification devenue verrouillée (implémentée) pendant la discussion : la proposition ne peut
  plus être appliquée ; la discussion reste lisible.
- Spécification trop longue pour la place du modèle choisi : elle reste transmise en priorité ;
  la place des autres sources diminue, et l'écran le signale comme pour les documents désignés.
- Proposition dont la structure est invalide (scénario sans acteur, action ou résultat) : elle
  n'est pas présentée ; l'utilisateur est invité à réessayer.
- Contenu hostile dans la spécification, une source, un document joint ou un message (« ignore
  tes consignes ») : il reste une donnée.
- Conflit Jira à l'envoi : la fusion par l'IA reste une proposition directe à relire, sans
  discussion.

## Requirements *(mandatory)*

### Functional Requirements

**Discussion**

- **FR-001**: L'écran d'une spécification non verrouillée DOIT offrir « Discuter avec l'IA » (à la
  place de « Faire évoluer avec l'IA », de même sur l'écran d'un ticket Jira),
  qui ouvre un panneau de discussion à côté du document ; sur un petit écran, le panneau passe sous
  le document. La consigne en un coup de la fonction actuelle disparaît.
- **FR-002**: La discussion DOIT offrir tout ce qu'offre l'écran Conversation (010) : recherches
  visibles dans la connaissance du projet, réponse en flux, citations, incohérences, mémoire,
  réflexion et chronomètre, arrêt et nouvel essai, mentions @ et documents joints.
- **FR-003**: La dernière version de la spécification DOIT être transmise entière à l'agent à
  chaque message, en tête des sources, avant les documents désignés, joints et les résultats de
  recherche. Les documents joints à la spécification (009) DOIVENT être disponibles comme sources.
- **FR-004**: Le panneau DOIT montrer sur quelle spécification et quelle version porte la
  discussion (« Contexte : <nom>, version N »), et un repère dans le fil à chaque changement de
  version. La discussion porte toujours sur la dernière version ; quand l'utilisateur consulte une
  version plus ancienne, le panneau DOIT le signaler, et une proposition part toujours de la
  dernière version.
- **FR-005**: Une citation de la spécification DOIT mener à la section citée du document affiché.
- **FR-006**: L'agent DOIT signaler les incohérences entre la spécification et le reste du projet
  (tickets, documentation, ADR, autres spécifications) avec leurs sources, et NE DOIT PAS inventer
  de fait sur le projet.

**Nouvelle version**

- **FR-007**: « Proposer une nouvelle version » DOIT faire réécrire la spécification par l'agent à
  partir de la version courante, de la discussion et des sources qu'elle a citées ; la proposition
  DOIT ne modifier que ce que la discussion a établi, et garder le reste à l'identique.
- **FR-008**: Chaque scénario ou use case ajouté ou modifié DOIT être rattaché à un point établi
  par la discussion (dit ou confirmé par l'utilisateur) ou à une source citée ; sinon il DOIT être
  marqué comme hypothèse (constitution, principe II). La proposition DOIT passer le contrôle de
  structure des scénarios avant d'être présentée.
- **FR-008a**: « Proposer une nouvelle version » NE DOIT demander aucune saisie : la proposition se
  fonde sur la discussion seule ; une précision se donne par un message avant de proposer.
- **FR-009**: Si la discussion n'a établi aucun changement, l'agent DOIT le dire au lieu de
  proposer une version.
- **FR-010**: La proposition DOIT être relue comme aujourd'hui (différences avec la version
  courante, « Appliquer », « Abandonner ») ; rien n'est créé sans « Appliquer ».
- **FR-011**: Appliquer DOIT créer la version suivante, d'origine « Issue de la discussion », avec
  un lien vers la conversation ; la discussion DOIT ensuite porter sur cette version.
- **FR-012**: La rédaction de la proposition DOIT montrer le chronomètre, et la réflexion de
  l'agent quand le modèle en donne une.

**Discussions d'une spécification**

- **FR-013**: Chaque spécification DOIT avoir au plus une discussion suivie, enregistrée
  localement, reprise à chaque retour sur la spécification et après un redémarrage.
- **FR-014**: « Nouvelle discussion » DOIT démarrer une discussion vide sur la même spécification ;
  l'ancienne DOIT rester dans la liste des conversations du projet, avec le nom de sa
  spécification.
- **FR-014a**: Les discussions de spécification DOIVENT figurer dans la liste des conversations du
  projet, triées par date avec les autres, avec la mention « Spécification : <nom> » (y compris la
  discussion suivie).
- **FR-015**: Une discussion de spécification ouverte depuis la liste des conversations DOIT
  s'afficher avec son contexte et un lien vers la spécification ; la proposition d'une nouvelle
  version se fait depuis l'écran de la spécification.
- **FR-016**: La confirmation de suppression d'une spécification qui a des discussions DOIT
  proposer la case « Supprimer aussi ses discussions », cochée par défaut. Cochée, les discussions
  sont supprimées avec la spécification ; décochée, elles deviennent des conversations ordinaires
  du projet, marquées « spécification supprimée », lisibles et continuables sans le contexte de la
  spécification.

**Autres parcours**

- **FR-017**: « Nouveau besoin » et son questionnaire NE DOIVENT PAS changer.
- **FR-018**: La fusion par l'IA d'un conflit avec Jira DOIT rester une proposition directe,
  relue de la même façon, sans discussion.
- **FR-019**: « Discuter avec l'IA » depuis un ticket Jira DOIT importer le ticket comme
  aujourd'hui, puis ouvrir la spécification avec sa discussion ; un ticket déjà importé ouvre la
  spécification existante.
- **FR-020**: Une spécification verrouillée NE DOIT PAS pouvoir être discutée ni modifiée ; ses
  discussions passées restent lisibles dans la liste des conversations.
- **FR-021**: Les versions existantes d'origine « Modifiée avec l'IA » gardent leur libellé.

**Accessibilité, sécurité et confidentialité**

- **FR-022**: Le panneau DOIT respecter WCAG 2.1 AA : ouverture et fermeture au clavier, focus
  placé dans le champ à l'ouverture et rendu au bouton à la fermeture, annonces des réponses sans
  relire tout le fil, libellés, contrastes.
- **FR-023**: Les messages, la spécification transmise et la proposition DOIVENT rester des données
  confidentielles : jamais journalisées en clair ; aucun envoi à Copilot sans l'accord de
  l'utilisateur ; contenu séparé des consignes de l'agent, et injection couverte par des tests.
- **FR-024**: L'agent DOIT n'utiliser que la connaissance du projet de la spécification.

### Key Entities

- **Discussion de spécification** : conversation (010) rattachée à une spécification ; au plus une
  discussion suivie par spécification ; les précédentes restent consultables.
- **Contexte de discussion** : spécification et version sur lesquelles porte la discussion ; change
  quand une nouvelle version existe, avec un repère dans le fil.
- **Proposition de version** : spécification réécrite par l'agent d'après la discussion, avec la
  version de départ, les différences et les rattachements de chaque changement ; appliquée ou
  abandonnée.
- **Version issue de la discussion** : version d'une spécification créée depuis une proposition,
  avec son origine et le lien vers la conversation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Depuis l'écran d'une spécification, l'utilisateur pose sa première question en une
  seule action (ouvrir la discussion), sans ressaisir le contexte.
- **SC-002**: Sur un jeu de questions de référence portant sur des spécifications, au moins 90 %
  des réponses citent la spécification ou une source pertinente du projet, et aucune source citée
  n'est inventée.
- **SC-003**: Sur un jeu de spécifications contredites par une source connue du projet, au moins
  80 % des contradictions sont signalées avec leurs deux sources.
- **SC-004**: Sur un jeu de discussions de référence, au moins 90 % des propositions de version
  contiennent les changements établis et laissent intactes les sections dont la discussion n'a pas
  parlé ; 100 % des scénarios proposés passent le contrôle de structure.
- **SC-005**: Passer d'une discussion à une nouvelle version appliquée demande au plus 2 actions
  après la discussion (proposer, appliquer).
- **SC-006**: Une discussion de 30 échanges se rouvre et se poursuit après un redémarrage sans
  perte de message.
- **SC-007**: L'audit d'accessibilité automatisé ne relève aucun écart bloquant sur l'écran de la
  spécification, discussion ouverte.

## Assumptions

- La feature 010 (agent conversationnel, PR #12) est fusionnée avant l'implémentation ; cette
  feature réutilise sa conversation, sa mémoire, sa réflexion et son chronomètre.
- La case « Supprimer aussi ses discussions » est cochée par défaut : elle limite les données
  confidentielles gardées ; l'utilisateur la décoche pour garder une analyse utile.
- Copilot reste le seul fournisseur d'IA ; la place de la spécification dans les sources suit le
  modèle choisi (008).
- L'agent ne modifie ni Jira ni les sources du projet ; l'envoi vers Jira reste une action de
  l'écran de la spécification.
- Le jeu d'évaluation de l'IA est étendu aux discussions de spécification (SC-002 à SC-004) ; une
  évaluation réelle se fera avec l'accord du mainteneur.
