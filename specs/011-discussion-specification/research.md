# Recherche : discuter d'une spécification avec l'agent

État des lieux (branche `feature/015-discussion-specification`, partie de la 010, 2026-11-10) :

- **Modification assistée** (`ProposeAiEdit`, `AcceptAiEdit`, `AiEditResource`) :
  - la consigne (2 000 caractères) donne un `EditRequest` ;
  - l'opération `edit-specification` (schéma `SPECIFICATION`) passe par
    `DraftValidation.produce` : identifiants, contrôle de structure des scénarios, traçabilité, 3
    tentatives avec correction ;
  - le résultat est une `Proposal` gardée en mémoire 30 min (`ProposalStore`), avec le diff
    unifié ;
  - `AcceptAiEdit` crée la version suivante, d'origine `AI_EDIT`.
- **Fusion d'un conflit Jira** (`PushToJira.merge`) : elle réutilise `ProposalStore`, le diff et
  `AcceptAiEdit`, avec l'origine `CONFLICT_MERGE`. Le front l'affiche dans le panneau de
  modification (`initialProposal`).
- **Origine des versions** : l'énumération `VersionOrigin` (`GENERATED`, `AI_EDIT`, `JIRA_IMPORT`,
  `CONFLICT_MERGE`) est doublée d'un `CHECK` SQLite (V4). Ajouter une valeur impose de reconstruire
  `specification_version`, avec ses triggers FTS (V6).
- **Conversation (010)** :
  - les sources d'un tour commencent par les documents désignés (`DesignatedDocuments`) ;
  - une spécification n'entre que comme extrait de 1 500 caractères trouvé par
    `ConversationSearch` (`spec:<session>@v<n>`) ;
  - `conversation_session` lie une conversation aux spécifications démarrées depuis elle ;
  - `Conversation` n'a pas de champ de spécification.
- **Suppression d'une spécification** (`ManageSessions.delete`) : la cascade SQL retire le lien
  `conversation_session`, pas la conversation.
- **Import Jira** (`ImportJiraIssue`) : un ticket déjà lié renvoie `409 ALREADY_LINKED`, sans
  l'identifiant de la session. Le front n'a pas de traitement particulier.
- **Verrou** : `SpecificationLock.requireEditable` est appelé par chaque cas d'usage d'écriture,
  dont la liste est vérifiée par ArchUnit (`HexagonalArchitectureTest`).
- **Budget** : `KnowledgeBudget.RESERVE` (60 000 caractères) est déjà réservé à « la version en cours
  d'une modification ».
- **Évaluation** : rien n'évalue aujourd'hui `edit-specification`. `ConversationRuns` évalue
  citation, abstention et incohérences.
- Dernière migration : V21.

## R1 — Rattacher une discussion à une spécification

**Decision** : nouvelle table `specification_discussion` (V22), une ligne par conversation
rattachée :

- `session_id`, en `ON DELETE SET NULL` ;
- le nom de la spécification gardé à part, pour afficher « spécification supprimée » ;
- un drapeau `current`, avec un index unique partiel : une seule discussion suivie par
  spécification (FR-013).

« Nouvelle discussion » crée une conversation, la marque suivie et retire le drapeau à l'ancienne
(FR-014). Le domaine gagne `SpecificationDiscussion(conversationId, sessionId?, specificationName,
current)`.

**Rationale** : `Conversation` et ses tables restent inchangées. La 010 (liste, reprise, mémoire,
réflexion) s'applique sans branche particulière. `SET NULL` donne directement le cas « discussions
gardées » de la suppression (R7).

**Alternatives** :
- réutiliser `conversation_session` : ce lien signifie « spécification démarrée depuis la
  conversation », et il cascade à la suppression ;
- une colonne `session_id` sur `conversation` : le nom gardé et le drapeau `current` s'y ajouteraient
  mal.

## R2 — La spécification comme source, découpée par section

**Decision** :

- **Lecture** : à chaque tour d'une discussion, `Converse` lit la dernière version de la
  spécification (`SpecificationVersion.markdown()`) et la découpe en sections (titres de niveau 2
  et 3).
- **Ordre des sources** : chaque section devient une source `SPECIFICATION`, de référence
  `spec:<session>@v<n>#<ancre>` et de titre « <nom>, <section> ». Ces sources sont placées avant les
  documents désignés et joints (FR-003).
- **Budget** : elles occupent une nouvelle part `discussedSpecification`, prise sur
  `KnowledgeBudget.RESERVE` et proportionnée au modèle comme les autres parts. La part des autres
  sources reste celle de la 010.
- **Spécification trop longue** : si elle dépasse sa part, les dernières sections sont résumées par
  leur titre seul, et l'écran le signale comme une source « en partie » (cas limite).
- **Citations** : une citation de la spécification discutée mène à l'ancre de la section dans le
  document affiché (FR-005). Le rendu du document reçoit des ancres stables, avec le même calcul
  que le découpage.
- **Documents joints à la spécification** (009) : ils entrent comme documents désignés, aux côtés
  de ceux de la conversation (`SelectKnowledge.mentioned`).
- **Dédoublonnage** : `ConversationSearch` ne renvoie plus la spécification discutée comme
  résultat de recherche.

**Rationale** : la spécification entière est toujours présente, et la citation par section rend
FR-005 vérifiable. Les numéros restent ceux de la 010 (résolution, retrait des numéros inconnus).

**Alternatives** :
- une seule source pour toute la spécification : la citation ne mène qu'au document entier ;
- un bloc `<specification>` hors des sources : il ne se cite pas, ce qui casse FR-002 et FR-006.

## R3 — Contexte d'une discussion dans les prompts

**Decision** : `converse-plan` et `converse-answer` reçoivent un bloc
`<specification-discutee nom="…" version="N"/>` quand la conversation est une discussion. Ce bloc
indique :

- que les sources de type spécification marquées comme discutées sont déjà là ;
- que les recherches servent à confronter la spécification au reste du projet (tickets,
  documentation, ADR, autres spécifications).

La consigne d'incohérences nomme ce cas. Les autres règles de la 010 (abstention, citations) ne
changent pas. Le tour enregistre la version transmise : l'écran place le repère « Contexte : version
N » au premier message de chaque version (FR-004).

**Rationale** : on garde une seule boucle et les mêmes prompts. Les évaluations de la 010 restent
comparables.

**Alternatives** : des prompts dédiés à la discussion, qui dupliqueraient les règles et doubleraient
l'évaluation.

## R4 — Proposer une nouvelle version d'après la discussion

**Decision** :

- **Opération** : nouvelle opération `revise-specification`, au port `ConversationLlm`
  (`revise(ReviseRequest)`). Elle reçoit :
  - la dernière version (contenu structuré et Markdown) ;
  - la mémoire et les échanges de la discussion (`ConversationHistory.prepare`, comme un tour) ;
  - les sources citées dans la discussion, relues ;
  - les réponses du questionnaire.
- **Sortie** : schéma strict `REVISION` = `{ changed, reason, specification }`.
  `specification` suit le schéma `SPECIFICATION` existant. `changed: false` produit le message
  « aucun changement établi » avec la raison (FR-009).
- **Validation** : `DraftValidation.produce` (identifiants, structure, traçabilité, 3 tentatives)
  s'applique sans changement (FR-008).
- **Traçabilité** : les messages de l'utilisateur dans la discussion deviennent des sources
  citables de type `DISCUSSION` (`discussion:<conversation>#<ordinal>`), au même titre que les
  connaissances. `TraceabilityValidator` les accepte. Le rendu Markdown les écrit « Discussion du
  <date>, message n ». Un ajout sans source est marqué comme hypothèse, comme aujourd'hui.
- **Préservation** (FR-007) : la consigne demande de recopier à l'identique les use cases et
  scénarios non concernés. Le code vérifie que leurs identifiants sont gardés : un identifiant
  disparu sans que la discussion le demande entraîne une correction, puis un échec.
- **Stockage** : la proposition est rangée dans `ProposalStore`, avec l'origine `DISCUSSION` et
  l'identifiant de la conversation. Elle est relue par le diff actuel, puis acceptée par
  `AcceptAiEdit`.

**Rationale** : la validation, le diff et l'acceptation sont réutilisés. Le contrôle des
identifiants rend SC-004 mesurable. La discussion est une entrée citable : elle sert la
traçabilité, sans recopier toute la conversation dans la spécification.

**Alternatives** :
- résumer la discussion en consigne, puis appeler `edit-specification` : on perd le lien entre
  changements et messages, et c'est la consigne en un coup que la feature retire ;
- un patch par sections : diff plus sûr, mais nouveau format, et la structure ne serait plus
  revalidée sur l'ensemble.

## R5 — Suivre la rédaction de la proposition

**Decision** : la proposition est un tour particulier de `TurnEvents`, publié sur le même flux SSE
que les réponses : événements `started`, `stage` (nouvelle étape `REVISE`), `reasoning`, `done`
(avec `proposalId` ou `unchanged` et sa raison), `error`. La réflexion n'est pas gardée : la
proposition n'est pas un message. `OperationRegistry` empêche de proposer pendant une réponse, et
de répondre pendant une proposition.

**Rationale** : on réutilise le chronomètre, le titre d'activité et la réflexion de la 010
(FR-012). Une rédaction de 20 à 40 s ne bloque pas une requête HTTP.

**Alternatives** : une requête synchrone comme aujourd'hui, qui ne montrerait pas la réflexion.

## R6 — Version « Issue de la discussion »

**Decision** :

- **Stockage** : nouvelle table `version_discussion(version_id → specification_version ON DELETE
  CASCADE, conversation_id → conversation ON DELETE SET NULL)` (V22).
- **Lecture** : le dépôt des versions lit l'origine `DISCUSSION` quand une ligne existe. En base,
  la version garde `AI_EDIT`, ce qui évite de reconstruire la table V4.
- **Affichage** : l'historique affiche « Issue de la discussion », avec un lien vers la
  conversation tant qu'elle existe.
- **Versions anciennes** : les versions `AI_EDIT` sans ligne gardent « Modifiée avec l'IA »
  (FR-021).

**Rationale** : on évite une reconstruction risquée (triggers FTS, clés étrangères). Le domaine
voit une vraie origine.

**Alternatives** : une migration qui reconstruit `specification_version` avec le nouveau CHECK.

## R7 — Supprimer une spécification et ses discussions

**Decision** :

- **API** : `DELETE /api/sessions/{id}?discussions=delete|keep` (défaut `delete`).
- **Cas d'usage** : `ManageSessions.delete(id, keepDiscussions)`.
  - Avec `delete`, il supprime les conversations rattachées (cascade des messages, de la mémoire et
    de la réflexion), puis la spécification.
  - Avec `keep`, la clé étrangère passe à `NULL` : les conversations restent, marquées
    « spécification supprimée » par le nom gardé (R1).
- **Refus** : la suppression est refusée pendant une réponse ou une proposition
  (`OPERATION_IN_PROGRESS`).
- **Confirmation** : elle montre la case cochée par défaut, seulement si la spécification a des
  discussions (`GET /api/sessions/{id}/discussions/count`).

**Alternatives** : laisser la case au front et faire deux appels. Écarté : ce n'est pas atomique.

## R8 — Ticket Jira : importer puis discuter

**Decision** :

- **Ticket déjà importé** : `POST /api/jira/issues/{key}/import` renvoie `200` avec la session
  existante quand le ticket est déjà lié (au lieu de `409`), et `201` sinon.
- **Ouverture** : le front ouvre `/sessions/{id}` avec `?discussion=1`, qui ouvre le panneau de
  discussion (FR-019).
- **Bouton** : « Discuter avec l'IA » sur les deux écrans.

**Rationale** : un aller-retour de moins ; le comportement demandé (« ouvre la spécification
existante ») devient le contrat.

**Alternatives** : garder `409` avec l'identifiant de la session dans le problème.

## R9 — Front : un fil de conversation partagé

**Decision** :

- **Extraction** : le fil de `conversation.page` (messages, étapes, réflexion, sources, champ de
  saisie avec mentions et documents joints, flux SSE, arrêt, nouvel essai, annonces) devient un
  composant `sw-conversation-thread`.
- **Utilisation** : l'écran Conversation et le panneau de discussion utilisent ce même composant.
- **Panneau** : il ajoute le contexte (« Contexte : <nom>, version N »), l'avertissement de version
  ancienne, « Nouvelle discussion », « Proposer une nouvelle version » et la relecture du diff
  (composant de relecture extrait de `ai-edit.panel`).
- **Disposition** : deux colonnes sur grand écran, et le panneau sous le document au-dessous de
  64 rem.
- **Focus** : il passe au champ à l'ouverture, puis revient au bouton à la fermeture (FR-022).
- **Fusion d'un conflit Jira** : sa proposition s'affiche dans le même composant de relecture,
  sans discussion (FR-018).
- **Retrait** : `ai-edit.panel` (consigne) est retiré, tout comme `ProposeAiEdit`,
  `POST /api/sessions/{id}/ai-edits` et le prompt `edit-specification`.

**Rationale** : un seul fil évite deux comportements divergents (réflexion, mémoire, accessibilité).

**Alternatives** : embarquer `conversation.page` tel quel, avec sa liste des conversations et sa
navigation, qui n'ont pas leur place dans le panneau.

## R10 — Évaluation

**Decision** : nouveau volet `eval/discussion/cases.json`, sur une spécification fictive du projet
Aurora et son corpus. Il mesure :

- `discussionCitation` (SC-002, seuil 0.9) : les réponses citent la spécification ou la source
  attendue ;
- `discussionInconsistency` (SC-003, seuil 0.8) : la contradiction connue est signalée avec ses
  deux sources ;
- `revisionCompleteness` (SC-004, seuil 0.9) : la proposition contient les changements attendus
  (motifs) ;
- `revisionPreservation` (SC-004, seuil 0.9) : les use cases non concernés sont identiques ;
- `revisionStructure` (SC-004, seuil 1.0) : le contrôle de structure passe ;
- `unchangedRecall` (FR-009) : une discussion sans changement établi donne `changed: false`.

Ces seuils s'ajoutent à `baseline.json`, et l'empreinte de `check-eval.sh` couvre le nouveau
prompt. L'évaluation réelle se fait avec l'accord du mainteneur.
