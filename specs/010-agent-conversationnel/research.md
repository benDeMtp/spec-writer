# Recherche : agent conversationnel, point d'entrée principal

État des lieux (code de `main`, 2026-11-09) :

- Port `LlmClient` : 9 opérations synchrones, chacune avec un prompt `prompts/<op>.txt` commençant
  par `[operation:<op>]`, une sortie JSON à schéma strict validée et 3 tentatives
  (`LangChainLlmClient`). `CopilotRuntime.complete` ouvre une session Copilot sans outil par appel,
  `sendAndWait`, puis supprime la session du disque.
- Aucun streaming, aucun appel d'outil, aucun SSE dans l'outil ; le front suit l'indexation par
  polling. Le SDK Copilot (`1.0.28-preview.0`) expose `SessionConfig.setStreaming(true)`,
  `AssistantMessageDeltaEvent.deltaContent()`, `SessionIdleEvent`, `SessionErrorEvent`, `abort()`,
  et des outils (`ToolDefinition`).
- Rejeu : `ReplayChatModel` (E2E, dev) choisit la réponse d'après l'opération du message système et
  des fragments du message utilisateur ; `ScriptedChatModel` (tests) rejoue un script ordonné.
- Recherche de connaissance : `KnowledgeIndex.search` (FTS5 + vecteurs, fusion RRF),
  `KnowledgeQueries.search` (20 résultats, extraits de 600 caractères). Les spécifications ne sont
  pas dans `knowledge_item` : la dernière version de chaque session est dans la table FTS5
  `spec_search`, lue par `LocalSearch.byDate(projectId, terms, offset, limit)`.
- `StartSession` crée un questionnaire vide puis `NextBatch.ask` ; les réponses connues
  (`Questionnaire.treated()`) sont transmises à `propose-questions` dans `<reponses>`. Une
  `Question` peut être construite directement à l'état `ANSWERED`.
- `session.origin` a un CHECK (`LOCAL`, `JIRA_IMPORT`) ; le projet n'a pas de cascade vers les
  sessions (`DeleteProject` les traite à la main) mais en a vers la connaissance.
- `ci/check-eval.sh` hache les prompts et tout `infrastructure/llm` : cette feature impose une
  évaluation réelle (accord du mainteneur).
- Dernière migration : V17.

## R1 — Boucle de l'agent : étapes JSON, puis réponse rédigée

**Decision** : l'agent est une boucle de l'application (`application/conversation/Converse`), pas
un agent à outils du SDK. À chaque tour :

1. Opération `converse-plan` (JSON à schéma strict, 3 tentatives comme aujourd'hui) : elle reçoit
   l'échange récent, la question, les sources déjà trouvées (titres et références), le nombre de
   recherches restantes, et renvoie `{ "action": "SEARCH", "query": "…", "kinds": [...] }` ou
   `{ "action": "ANSWER" }`. 5 recherches au plus (FR-002) ; à la 5e, l'action est forcée à
   `ANSWER` et la réponse dit que la recherche s'est arrêtée.
2. Chaque `SEARCH` exécute la recherche de connaissance du projet (R3) et ajoute ses résultats,
   numérotés, aux sources du tour ; l'étape (requête, nombre de résultats) est publiée (R2).
3. Opération `converse-answer` : réponse en Markdown rédigée **en flux** (R2), qui cite ses sources
   par leur numéro (`[3]`). Les sources sont transmises dans un bloc de données numéroté
   (`<sources><source n="3" ref="…" titre="…">…</source></sources>`, échappé comme aujourd'hui).
   À la fin, les numéros cités sont résolus vers les sources du tour ; un numéro inconnu est retiré
   (FR-003) ; une réponse sans source citée est acceptée seulement si elle dit ne pas savoir
   (contrôle par la consigne et l'évaluation, pas par le code).

L'analyse d'incohérences (US3) passe par la même boucle : la consigne de `converse-answer` décrit
comment présenter une comparaison (ce que dit chaque source, avec son numéro, nature de l'écart,
sources non comparées).

**Rationale** : boucle testable avec les doubles existants (rejeu, scripts), indépendante du SDK,
plafond et étapes maîtrisés par l'outil, sortie de planification validée par schéma. Les outils du
SDK donneraient la main au CLI (permissions, exécution) et ne se rejouent pas en E2E.

**Alternatives** : outils natifs du SDK Copilot (`ToolDefinition`) : moins de code, mais rejeu E2E
et tests déterministes à réécrire, contrôle du plafond moins sûr ; une seule recherche par message
(écartée en clarification).

## R2 — Réponse en flux et envoi au navigateur

**Decision** :

- Port domaine `ConversationLlm` : `Plan plan(PlanRequest)` et
  `Answer answer(AnswerRequest, Consumer<String> deltas, Cancel cancel)`. Implémentation
  `infrastructure/llm/LangChainConversationLlm` sur un `StreamingChatModel` LangChain4j :
  - `CopilotSdkStreamingChatModel` : session sans outil, `setStreaming(true)`, chaque
    `AssistantMessageDeltaEvent` transmis, fin sur `SessionIdleEvent`, `abort()` à l'arrêt, session
    supprimée ensuite comme aujourd'hui ;
  - `ReplayStreamingChatModel` : même choix de fixture que `ReplayChatModel` (champ `text` au lieu
    de `response`), restitué par morceaux de quelques mots.
- Un tour tourne en tâche de fond (fil virtuel, `OperationRegistry` pour l'arrêt). Il enregistre
  ses étapes, son texte partiel et son état ; il publie ses événements dans un `TurnEvents` en
  mémoire.
- `GET /api/conversations/{id}/turns/{turnId}/events` : flux SSE (`text/event-stream`), avec les
  événements `step`, `delta`, `sources`, `done`, `error`. Il commence par rejouer ce qui est déjà
  enregistré : une reconnexion ou un rechargement ne perd rien. Le navigateur lit le flux avec
  `EventSource` (GET, même origine, cookie de lancement) : aucun changement du filtre
  `LocalSecurityFilter`.
- Arrêt : `DELETE /api/conversations/{id}/turns/{turnId}` ; le texte déjà écrit est gardé, le
  message est marqué `INCOMPLETE`.

**Rationale** : SSE est natif dans Quarkus REST et `EventSource`. L'enregistrement au fil du tour
rend la reprise sûre (SC-007). Le reste de l'API reste en JSON synchrone.

**Alternatives** : WebSocket (bidirectionnel inutile, protection d'origine à refaire) ; polling du
texte partiel (latence visible, SC-006) ; `fetch` en flux sur le POST (pas de reprise).

## R3 — Recherche de l'agent, spécifications comprises

**Decision** : `ConversationSearch.run(projectId, query, kinds)` fusionne par RRF :

- `KnowledgeIndex.search` sur les sortes demandées (tickets, documents et pages, ADR, éléments
  d'architecture) ;
- les spécifications du projet, sur la table `spec_search` existante (dernière version seulement),
  par un nouveau `LocalSearch.byRelevance(projectId, terms, limit)` (bm25).

Elle renvoie 8 résultats au plus par recherche, chacun avec un extrait borné. Les textes transmis
suivent la part du budget du modèle choisi (008) : `KnowledgeBudget` gagne deux parts,
`conversationSources` et `conversationHistory`, qui grandissent avec le modèle comme les autres.

- Une spécification est citée par la référence `spec:<sessionId>@v<n>`. Elle s'ouvre sur cette
  version (FR-002b). Une feature implémentée porte l'état `IMPLEMENTED` dans l'extrait transmis.
- La fiche générale est toujours transmise.
- Les documents désignés avec @ et les documents joints de la conversation passent en tête des
  sources du tour, avec les règles 007/009 (ordre, 4 au plus, coupe à une section).

**Rationale** : aucune nouvelle indexation (pas de vecteurs pour les spécifications dans cette
feature : FTS suffit pour des textes rédigés dans le vocabulaire du projet) ; le budget suit 008.

**Alternatives** : indexer les spécifications dans `knowledge_item` (vecteurs, CHECK du type,
réindexation à chaque version) : reportée.

## R4 — Historique transmis

**Decision** : `converse-plan` et `converse-answer` reçoivent l'échange le plus récent dans
`<echange>`, dans la part `conversationHistory`. Un message de l'agent y est transmis sans ses
sources. Les messages les plus anciens qui dépassent la part ne sont pas transmis ; le tour
l'enregistre (`historyTruncated`) et l'écran l'affiche (FR-005).

## R5 — De la conversation au questionnaire

**Decision** :

- Opération `propose-need` (JSON à schéma strict) : `{ need, points: [{ theme, question, answer,
  basis: USER|SOURCE, sourceRef? }] }`, 10 points au plus. La consigne n'accepte un point que si
  l'utilisateur l'a dit ou confirmé, ou si une source citée l'établit (FR-009).
- L'écran montre le brouillon. L'utilisateur modifie le besoin, modifie ou retire des points, puis
  valide. L'API reçoit alors le besoin et les points tels que validés.
- `StartSession.fromConversation(need, points, project, attachments, conversationId)` construit un
  questionnaire dont les points sont des questions `ANSWERED` (lot 0, `rationale` « Établi dans la
  conversation »), puis appelle `NextBatch.ask`. Les points arrivent dans `<reponses>` comme
  aujourd'hui : une question qu'ils couvrent n'est pas reposée (principe I), et une règle qui en
  vient est citée comme source `ANSWER` (principe II). `propose-questions` et la génération ne
  changent pas.
- Les documents désignés avec @ dans la conversation sont ajoutés au besoin sous forme de mentions
  `@…`. Les documents joints de la conversation sont copiés (texte) dans `session_attachment`.
- Lien : table `conversation_session` (pas de nouvelle valeur pour `session.origin`, dont le CHECK
  imposerait de reconstruire la table).

**Alternatives** : transmettre la conversation brute (écartée en clarification) ; nouvelle origine
`CONVERSATION` (reconstruction de la table `session`).

## R6 — Stockage

**Decision** : migration `V18__conversation.sql` :

- `conversation(id, project_id → project ON DELETE CASCADE, name, created_at, updated_at)` ;
- `conversation_message(id, conversation_id → CASCADE, ordinal, role USER|AGENT, text ≤ 100 000,
  state COMPLETE|INCOMPLETE|RUNNING, history_truncated, created_at)` ;
- `conversation_step(message_id → CASCADE, ordinal, query, kinds, hits)` ;
- `conversation_source(message_id → CASCADE, n, kind, ref, title)` : seules les sources citées sont
  gardées ;
- `conversation_attachment` : même forme que `session_attachment`, rattachée à la conversation ;
- `conversation_session(conversation_id → CASCADE, session_id → session CASCADE)`.

Un message utilisateur fait 8 000 caractères au plus (FR-025). Au démarrage de l'outil, un message
`RUNNING` passe à `INCOMPLETE`. Le nom par défaut est le début du premier message (60 caractères),
renommable. La suppression d'un projet supprime ses conversations par cascade (FR-019).

## R7 — Écrans et navigation

**Decision** :

- Route `''` → `ConversationPage` (`features/conversation/`) :
  - liste des conversations du projet courant (panneau latéral repliable : nouvelle, renommer,
    supprimer) ;
  - fil des messages (`role="log"`) ;
  - champ de saisie avec `swMention` (007) et `sw-attachments` en mode conversation (009, entrée
    `conversationId`) ;
  - bouton « Rédiger une spécification » qui ouvre le brouillon du besoin et des points.
- Pour chaque réponse : étapes de recherche (repliables après la réponse), texte Markdown rendu
  (ngx-markdown, HTML assaini), citations `[n]` rendues comme liens vers la source (ticket, page,
  ADR, spécification), liste des sources sous la réponse, « Arrêter » pendant l'écriture,
  « Réessayer » sur un message incomplet.
- Accessibilité : le fil `aria-live="off"` pendant l'écriture ; une région `aria-live="polite"`
  séparée annonce « Recherche : … » puis la réponse complète (FR-022). Entrée envoie,
  Maj+Entrée va à la ligne, Échap arrête, focus rendu au champ.
- Menu : Conversation, Spécifications (ex-Sessions), Tickets Jira, Projets, Journal, Configuration.
- Écran des spécifications : bouton « Nouvelle spécification » (`/nouveau`) et champ de recherche
  qui reprend la recherche unifiée (`/api/search`, résultats existants).
- Redirections : `/recherche` vers `''` ; `/nouveau` garde l'écran du besoin.
- Conception visuelle guidée par la compétence `frontend-design`. Revue finale par
  `web-design-guidelines` (FR-023, SC-008), avec axe en E2E.

## R8 — Évaluation

**Decision** :

- Nouvelles opérations et `infrastructure/llm` modifié : évaluation réelle obligatoire avant la
  fusion, avec l'accord du mainteneur.
- Le jeu d'évaluation gagne un volet conversation (`eval/conversation/`) :
  - questions à réponse connue, avec la source attendue (SC-002) ;
  - questions hors connaissance (SC-003) ;
  - paires de sources contradictoires (SC-004) ;
  - le tout sur une connaissance de test fixe (projet Aurora fictif, indexé à partir de
    `TestDocuments`).
- Trois nouvelles métriques sont ajoutées dans `baseline.json` : `conversationCitation`,
  `conversationAbstention` et `inconsistencyRecall`.
- Le golden set de la spécification ne change pas.

## R9 — Sécurité

**Decision** :

- Messages, réponses, étapes et extraits ne sont jamais journalisés : le journal ne garde que
  l'opération, la durée, le nombre d'étapes et le code d'erreur.
- Les données passent entre balises et sont échappées. Le Markdown de la réponse est assaini au
  rendu. Les références citées sont limitées aux sources du tour.
- Une conversation ne voit que son projet : la recherche est filtrée par `projectId`, et
  l'identifiant de conversation est vérifié contre le projet à chaque appel.
- Le test d'injection est étendu aux messages, aux sources et aux documents joints, et vérifie que
  `converse-plan` ne peut pas dépasser 5 recherches.
- L'accord Copilot est vérifié avant chaque tour (FR-008).
