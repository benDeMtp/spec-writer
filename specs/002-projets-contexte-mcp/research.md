# Research: Projets, contexte de projet et accès des agents de code

Feature : [spec.md](spec.md). Elle s'appuie sur les décisions de la feature 001
([research.md](../001-local-spec-assistant/research.md)), qui restent valables (Quarkus 4,
architecture hexagonale, SQLite + FTS5, Copilot SDK, Jira Server/DC 8.14+, Angular 22).

## R1. Serveur MCP : passerelle stdio hors de Quarkus

- **Decision** :
  - l'outil lancé expose une API locale réservée aux agents, `/api/agent/**`, sur le port
    habituel (`127.0.0.1`) ;
  - une **passerelle MCP** sert le protocole MCP en **stdio** :
    - c'est une classe `main` du même jar, qui ne démarre pas Quarkus ;
    - l'agent de code la lance comme commande locale ;
    - elle traduit chaque appel d'outil MCP en requête HTTP vers l'API agent.
  - installeurs : un second lanceur jpackage, `spec-writer-mcp`. Depuis les sources, la passerelle
    se lance avec `java -cp spec-writer-runner.jar dev.specwriter.mcp.McpBridge`.
- **Rationale** :
  - les agents visés (Claude Code, Copilot, Cursor, JetBrains) savent tous lancer un serveur MCP
    stdio, et c'est le seul mode qui permet l'authentification sans clé de la clarification Q3 ;
  - l'extension Quarkiverse `quarkus-mcp-server` n'existe pas encore pour Quarkus 4. Sa branche
    `main` vise Quarkus 3.40.1.1, et l'issue
    [#1055](https://github.com/quarkiverse/quarkus-mcp-server/issues/1055) « Upgrade Quarkus to
    4.0.x », ouverte le 2026-10-05, prévoit une branche séparée ; la 2.1.0 visera encore Quarkus
    3.40 LTS. Aucun essai sur Quarkus 4.0.0.Beta2 n'a été fait. Ce point est à revoir quand une
    version pour Quarkus 4 sortira, mais il ne change pas la décision, qui repose d'abord sur
    l'authentification sans clé ;
  - une passerelle sans Quarkus démarre en moins de 500 ms et ne duplique ni la base ni la logique ;
  - le domaine reste unique, et l'API agent est testée comme les autres ressources REST.
- **Protocole** :
  - implémentation minimale de MCP `2025-06-18` sur stdio : JSON-RPC 2.0, un message par ligne ;
  - méthodes : `initialize`, `notifications/initialized`, `ping`, `tools/list` et `tools/call`,
    plus les erreurs JSON-RPC standard ;
  - Jackson est déjà présent, donc pas de nouvelle dépendance. Le SDK Java MCP officiel
    (`io.modelcontextprotocol.sdk`) a été écarté : il apporterait Reactor et un client HTTP pour 5
    méthodes. Des tests de conformité rejouent des échanges enregistrés.
- **Alternatives considered** :
  - MCP Streamable HTTP servi par l'outil : cela impose une clé dans la configuration de l'agent
    (refusé en clarification) et expose une surface supplémentaire au navigateur (DNS rebinding) ;
  - `quarkus-mcp-server-stdio` dans une seconde application Quarkus : aucune version pour Quarkus
    4 à ce jour (issue #1055), et environ 2 s de démarrage par session d'agent.

## R2. Authentification de la passerelle (FR-024, FR-024a)

- **Decision** :
  - au démarrage, quand l'accès des agents est activé, l'outil écrit `agent-endpoint.json` dans le
    dossier de données : `{port, secret}` en `rw-------`, dans un dossier déjà `rwx------` ;
  - le secret fait 256 bits, issus de `SecureRandom`. Il est persistant d'un lancement à l'autre ;
    seule sa régénération le change ;
  - la passerelle lit ce fichier et envoie `Authorization: Bearer <secret>` ;
  - l'API agent :
    - compare le secret à temps constant ;
    - refuse toute requête qui porte un en-tête `Origin` (navigateur) ;
    - passe par le contrôle `Host` existant (`127.0.0.1:<port>`) ;
  - le cookie de session et le jeton de lancement de l'interface ne donnent **pas** accès à l'API
    agent, et inversement ;
  - désactiver l'accès supprime le fichier et invalide le secret en mémoire ; régénérer réécrit le
    fichier.
- **Rationale** : la protection équivaut à celle de la base (même compte, même dossier). Aucun
  secret n'est recopié dans un `.mcp.json` versionnable. Un programme ou une page web qui joint
  `127.0.0.1` sans lire le fichier est refusé.
- **Alternatives considered** :
  - le trousseau du système : la passerelle devrait y accéder à chaque lancement, avec une
    demande d'autorisation possible sous macOS, pour un gain nul face à un fichier `rw-------` du
    dossier de données ;
  - Unix domain socket : pas d'équivalent simple sous Windows avec le serveur HTTP de Quarkus.

## R3. Lecture d'une documentation Antora (FR-013, FR-013d, FR-013e)

- **Decision** :
  - **Site distant ou local** (`http`, `https`, y compris `localhost`) :
    - lire `sitemap.xml` à la racine du lien. Antora le génère quand `site.url` est défini. Avec
      plusieurs composants, c'est un index qui renvoie vers `sitemap-<composant>.xml` ;
    - à défaut, parcourir depuis la page d'accueil les liens du même hôte et sous le même chemin.
  - **Dossier local** : parcourir les fichiers `*.html` du dossier. Un sous-dossier illisible est
    signalé (`ACCESS_DENIED`) sans arrêter le parcours. Un dossier sans page donne son motif :
    `FOLDER_NOT_FOUND`, `NOT_A_FOLDER`, `ACCESS_DENIED`, `ADOC_SOURCES` (sources `.adoc` au lieu
    du site généré) ou `NO_HTML`. L'ajout refuse un chemin relatif, absent, un fichier ou un
    dossier illisible, avec un message propre à chaque cas.
  - Une page est reconnue comme Antora si elle contient `article.doc`. Seul ce contenu est gardé,
    sans navigation, en-tête ni pied de page. Il est converti en texte structuré (titres, listes,
    tableaux) avec **jsoup** (MIT, déjà dans le dépôt Maven local, version 1.23.2).
  - Garde-fous :
    - aucune redirection vers un autre hôte n'est suivie ;
    - 2 Mo au plus par page, 1 000 pages par source, 10 minutes au plus ;
    - délai de 15 s par page et 4 requêtes en parallèle ;
    - proxy d'entreprise via `ProxySettings` (T139 de 001) ;
    - certificat d'autorité Jira réutilisable si la documentation est interne.
  - Les pages lues restent en mémoire le temps de la génération (FR-013b) et ne sont jamais
    journalisées.
- **Rationale** : le sitemap donne la liste exacte des pages publiées, ce qui évite un
  explorateur web complet. jsoup est la référence Java pour extraire du HTML non fiable sans
  l'exécuter.
- **Alternatives considered** :
  - lire les sources AsciiDoc du playbook Antora : il faudrait accéder au dépôt et résoudre les
    inclusions et attributs, ce qui est hors portée ;
  - un explorateur web générique : plus de cas limites et de risques (pièges à robots, liens
    infinis).

## R4. Génération de la fiche de contexte (FR-013a à FR-013c, SC-004a)

- **Decision** : génération en deux temps (map-reduce) par le port LLM, avec deux nouvelles
  opérations :
  1. **`summarize-project-sources`** : les pages et tickets sont regroupés par lots d'environ
     40 000 caractères. Pour chaque lot, le LLM extrait des faits structurés (vocabulaire, acteurs,
     règles, contraintes, conventions), chacun avec sa source (URL de page ou clé de ticket).
  2. **`build-project-context`** : à partir de tous les faits, le LLM rédige la fiche Markdown de
     20 000 caractères au plus, organisée en sections fixes : Vocabulaire, Acteurs, Règles
     métier, Contraintes, Conventions. Chaque affirmation porte ses références `[source : …]`.
  - Le code vérifie que chaque référence citée existe dans les sources lues. Une référence
    inconnue déclenche une `correction`, comme pour la traçabilité de 001.
  - Pour 300 pages et 200 tickets, il faut une quinzaine d'appels au LLM, ce qui tient dans les
    10 minutes de SC-004a.
  - Les tickets sont les 200 plus récemment modifiés du projet Jira associé (JQL
    `project = X ORDER BY updated DESC`, par pages de 50). Leur description passe par
    `JiraFormatConverter.toMarkdown`.
  - Le résultat est une **proposition**, conservée en mémoire comme les propositions d'édition de
    001 (`ProposalStore`). Le diff avec la fiche actuelle est calculé par `TextDiff`. Rien n'est
    enregistré avant acceptation.
- **Rationale** : une documentation de 300 pages ne tient pas dans un seul prompt. La passe
  d'extraction de faits sourcés rend les citations vérifiables (SC-004a) et limite les inventions.
- **Alternatives considered** :
  - un seul appel tronqué : il perd la majeure partie de la documentation ;
  - RAG : écarté dans cette feature (clarification Q1).

## R5. Contexte dans les opérations LLM et traçabilité (FR-009 à FR-012)

- **Decision** :
  - une balise `<contexte-projet>` est ajoutée au message utilisateur des 6 opérations existantes
    (`UserMessages`), placée après `<langue>`. Son contenu est échappé par `data()`. Les prompts
    déclarent cette balise comme donnée et précisent son rôle : connaissance du projet, à ne pas
    redemander, citable ;
  - un nouveau type de source, `PROJECT_CONTEXT` avec l'extrait, s'ajoute à `NEED_EXCERPT`,
    `ANSWER` et `ASSUMPTION`. `TraceabilityValidator` vérifie que l'extrait figure bien dans le
    contexte (même contrôle que pour le besoin) ;
  - une contradiction entre le contexte et le besoin ou une réponse est remontée par le canal
    `contradictions` existant de `propose-questions` ;
  - `InputFingerprint` ajoute le SHA-256 du contexte (FR-011). La version enregistre ce hash et la
    date du contexte utilisé ;
  - les records de requête `domain/llm/*Request` reçoivent `projectContext` (chaîne, vide si
    absent), propagé dans `withCorrection`.
- **Rationale** : c'est le même modèle que le besoin, avec la même séparation données/instructions
  (constitution V) et la même traçabilité (principe II).

## R6. Projets, migration et projet courant (FR-001 à FR-007, SC-001)

- **Decision** :
  - migration Flyway `V7__projects.sql` :
    - table `project` (id, name, `name_key` = nom en minuscules unique, description,
      `jira_project_key`, `context`, `context_hash`, `context_origin`, `context_updated_at`,
      created_at, updated_at) ;
    - table `project_doc_source` (id, project_id, kind URL|FOLDER, location, last_read_at,
      last_result) ;
    - colonne `session.project_id` en `NOT NULL`, en `REFERENCES project` ;
  - la migration crée le « Projet par défaut » et y rattache toutes les sessions (SC-001) ;
  - le projet courant est un réglage (`settings`), lu à chaque requête ; la liste et la recherche
    locale filtrent sur `project_id` ;
  - le projet Jira effectif est `project.jira_project_key ?? settings.jira.defaultProject`. Il est
    résolu en un seul point (`JiraAccess`), utilisé par la création de ticket, la recherche, la
    liste et l'import.
- **Rationale** : une seule résolution évite les écarts entre les actions Jira. La migration
  garantit qu'aucune session n'est sans projet.

## R7. Statut d'implémentation et verrouillage (FR-026 à FR-027, US5-7)

- **Decision** :
  - colonnes `session.implementation_status` (DRAFT, READY, IN_PROGRESS, IMPLEMENTED),
    `implemented_at` et `evolved_from_session_id` ;
  - transitions dans le domaine (`ImplementationStatus`) :
    - **utilisateur** : toutes, avec confirmation pour quitter IMPLEMENTED ;
    - **agent** : READY → IN_PROGRESS et IN_PROGRESS → IMPLEMENTED seulement ;
  - verrouillage : un garde unique `SpecificationLock.requireEditable(session)` est appelé par tous
    les cas d'usage qui écrivent. Ceux-ci sont :
    - `AnswerQuestion`, `ReviseAnswer`, `ConcludeQuestionnaire`, `GenerateSpecification` ;
    - `ProposeAiEdit`, `AcceptAiEdit` ;
    - `PushToJira` (envoi, fusion, reprise de Jira) et l'import sur une session existante ;
    - `DeleteVersion`.

    Une règle ArchUnit vérifie que ces cas d'usage passent par le garde ;
  - une opération IA en cours quand la feature passe à IMPLEMENTED : `AcceptAiEdit` et
    `GenerateSpecification` revérifient le verrou avant d'enregistrer (edge case) ;
  - « Créer une évolution » : nouveau cas d'usage `EvolveSpecification`. Il crée une session
    dans le même projet, avec le besoin = Markdown de la dernière version et l'origine citée,
    à l'état DRAFT.
- **Rationale** : un garde unique, vérifié par ArchUnit, empêche d'oublier le verrou dans un cas
  d'usage futur.

## R8. Suppression locale (FR-014 à FR-019)

- **Decision** :
  - `DeleteVersion` supprime une ligne `specification_version`. Les numéros des autres versions
    ne changent pas : la numérotation garde un compteur `session.next_version_number` au lieu de
    `max + 1`, pour ne pas réattribuer un numéro supprimé ;
  - si la version supprimée était celle liée à Jira, `linked_ticket.version_number` pointe sur la
    dernière version restante et l'état de synchronisation est recalculé. S'il ne reste aucune
    version, la session repasse à l'état prêt à générer (FR-015) ;
  - `DeleteSession` existe déjà (FR-017 de 001) : la cascade SQL couvre les réponses, les versions
    et le lien Jira. Une confirmation nommée est ajoutée dans l'interface ;
  - la FTS5 est tenue à jour par les triggers `DELETE` de V6. Les propositions en mémoire de la
    session sont purgées ;
  - refus pendant une opération IA en cours : un registre en mémoire des opérations par session
    (`OperationRegistry`), consulté par les suppressions (FR-019) ;
  - aucune suppression n'appelle Jira (FR-017). Un test WireMock vérifie qu'aucune requête ne part.
- **Alternatives considered** : la suppression logique (corbeille), écartée par la spec (suppression
  définitive).

## R9. Pagination Jira et liste unique (FR-020 à FR-022, SC-006)

- **Decision** :
  - **Liste des tickets** : `GET /rest/api/2/search` avec `jql=project = X ORDER BY updated DESC`,
    `startAt`, `maxResults=25` et les champs utiles seulement. Jira renvoie `total`.
  - **Liste unique de recherche**, fusion de deux flux triés par date de modification
    décroissante :
    - le serveur renvoie la page et un **curseur opaque** `{localOffset, jiraOffset, prochaine
      date}` ;
    - pour produire 25 éléments, il lit au plus 25 éléments locaux (FTS, `ORDER BY updated_at
      DESC LIMIT/OFFSET`) et 25 tickets Jira à partir des offsets, fusionne, coupe à 25, puis
      avance chaque offset du nombre d'éléments consommés ;
    - un élément « les deux » (ticket lié) n'est gardé qu'une fois, côté local ;
    - le front conserve la pile des curseurs pour la page précédente ;
    - le total affiché est `local + Jira − liés`, approximatif quand Jira est indisponible.
  - En cas d'erreur Jira, la page courante reste affichée et l'erreur `JIRA_UNREACHABLE` est
    renvoyée avec le curseur inchangé (FR-022).
- **Rationale** : une page ne demande jamais plus de 25 tickets à Jira (SC-006), et l'ordre est
  stable entre les pages. Un `startAt` global est impossible avec deux sources.

## R10. Évaluation et golden set (SC-002, SC-003, principe IV)

- **Decision** :
  - chaque cas du golden set reçoit un `context.md` facultatif. Deux nouveaux cas avec contexte
    sont ajoutés, et le rejeu (`ScriptedChatModel`) vérifie `<contexte-projet>` par
    `userContains` ;
  - suite d'évaluation (T137 de 001) :
    - le juge reçoit le contexte et un quatrième verdict, `DEJA_DANS_LE_CONTEXTE`, qui sert à
      mesurer SC-002 (5 % au plus) ;
    - chaque cas est joué avec et sans contexte, pour mesurer la baisse du nombre de questions
      (20 % au moins) ;
    - `baseline.json` reçoit les métriques `contextQuestionRate` et `questionReduction` ;
  - `EvalFingerprint` couvre déjà les prompts, le code LLM, le golden set et la suite
    d'évaluation, donc rien à ajouter.
- **Rationale** : les critères SC-002 et SC-003 deviennent mesurés en CI par le même mécanisme
  que SC-004 de 001.

## R11. Journal des accès des agents (FR-028)

- **Decision** : la table `agent_access_log` (id, at, agent_name, operation, project_id,
  session_id, old_status, new_status) contient le nom annoncé par la passerelle dans
  `initialize` (`clientInfo.name`), tronqué à 80 caractères. Les entrées de plus de 90 jours sont
  purgées au démarrage. Elle est consultable dans Configuration, par pages de 25.
- **Rationale** : le journal sert à la lecture, pas au contrôle d'accès (FR-028). La purge borne
  la taille de la base.

## R12. Points d'attention pour la mise en œuvre

- **Dépendances** : la seule nouvelle dépendance est jsoup, déjà présente dans le dépôt Maven
  local. Le proxy du poste de développement (Privoxy) bloque les téléchargements Maven nouveaux :
  toute autre dépendance devrait être validée avant d'être ajoutée.
- **Packaging** : le second lanceur jpackage (`--add-launcher`) est à déclarer dans
  `jreleaser.yml`. On vérifiera sur l'installeur macOS (T140 de 001) que la passerelle démarre
  sans le runtime Copilot.
- **Opérations Copilot** : la génération du contexte consomme une quinzaine de requêtes. Le
  consentement Copilot de 001 couvre cet envoi, et l'interface annonce le volume (FR-013b).

## R13. Limite de débit vers Jira (FR-022a)

- **Decision** : un limiteur maison `JiraRateLimiter` (`infrastructure/jira`, `@ApplicationScoped`)
  à **fenêtre glissante** : il garde les instants des 8 dernières requêtes, et une nouvelle requête
  attend que la plus ancienne ait au moins une seconde. Il est appelé au début de
  `JiraRestClient.once()`, qui correspond à exactement une requête HTTP : les pages de createmeta,
  la pagination et les nouvelles tentatives après un 429 (R10 de 001) sont donc comptées.
  - Limiteur **unique pour l'application** : une installation ne parle qu'à un serveur Jira.
  - File **équitable** (`ReentrantLock(true)`) : les requêtes passent dans leur ordre d'arrivée.
  - Attente plafonnée à **10 s** : au-delà, `IssueTrackerException(RATE_LIMITED)`, sans envoyer
    la requête. Une interruption donne `UNREACHABLE`, comme dans `call()`.
  - Seuil lu dans `specwriter.jira.max-requests-per-second` (8 par défaut), pour les tests.
  - Horloge (`ClockPort`) et attente injectées, pour des tests unitaires sans `sleep` réel.
- **Rationale** : la fenêtre glissante garantit au plus 8 requêtes sur toute seconde, là où un
  espacement fixe de 125 ms en laisse passer 9 sur un intervalle fermé. Faire attendre plutôt que
  refuser garde les parcours paginés et la lecture des tickets de la fiche de contexte fluides.
- **Alternatives considered** :
  - SmallRye Fault Tolerance `@RateLimit` : rejette l'appel en excès (`RateLimitException`) au
    lieu de le faire attendre, et ajoute une extension.
  - Guava `RateLimiter` (`@Beta`) ou Bucket4j : une dépendance de plus (R12) pour une trentaine de
    lignes.
  - Un `ClientRequestFilter` : il risquerait de bloquer un thread d'E/S du client REST.


## R14. Progression, interruption et erreurs de la génération du contexte (FR-013f, FR-013g)

- **Decision** : la génération devient une **opération en arrière-plan**, suivie par interrogation.
  - `POST /api/projects/{id}/context-generations` vérifie le consentement, démarre la génération
    et répond `202` avec son état. Une génération déjà en cours pour ce projet donne `409
    OPERATION_IN_PROGRESS`.
  - `GET /api/projects/{id}/context-generations/current` renvoie l'état ; le front l'interroge
    toutes les secondes tant qu'elle n'est pas terminée, et le retrouve s'il revient sur la page.
  - `DELETE /api/projects/{id}/context-generations/current` l'interrompt.
  - État : `status` (`RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`), `step` (`READING_DOCS`,
    `READING_TICKETS`, `SUMMARIZING`, `BUILDING`), `current` (adresse de la page ou clé du ticket
    en cours), `done` et `total` de l'étape, `errors`, et `proposal` une fois réussie. Une
    génération terminée reste consultable 30 minutes, comme sa proposition.
  - `ContextGenerations` (`application/context`) garde une génération par projet et l'exécute sur
    un thread virtuel. Le cas d'usage `ProposeProjectContext` reçoit un `GenerationProgress` (port
    d'`application`), qu'il informe à chaque page, ticket, lot et appel au LLM.
  - `DocumentationReader.read(source, listener)` signale chaque page lue ou non lue ; `Unread`
    gagne `httpStatus` (nul sans réponse HTTP).
  - Interruption **immédiate** : la génération passe aussitôt à `CANCELLED`, ce qui rend la main
    et permet d'en relancer une. Le drapeau reste lu avant chaque page, chaque page de tickets et
    chaque appel au LLM, et le thread de la tâche est interrompu : un appel Copilot en cours est
    abandonné (`CopilotSession.abort`), puis sa session est supprimée quand même (FR-022). Ce que
    la tâche coupée renvoie ensuite (échec ou proposition) est ignoré.
  - Une interrogation remplacée (par la relecture qui suit l'interruption) ignore sa réponse : une
    seule boucle d'interrogation à la fois.
  - Erreurs (FR-013g) : `{kind, location, reason, httpStatus, message}`, avec `kind` parmi
    `PAGE`, `SOURCE`, `JIRA`, `CORRECTION` et `FAILURE`. Les messages ne citent que des
    adresses, des clés de ticket et des motifs, jamais un contenu.
  - Chaque erreur est aussi écrite au niveau WARN dans le journal (R15), sans contenu ;
    l'avancement, lui, n'est jamais journalisé.
- **Rationale** : une génération dure jusqu'à 10 minutes (SC-004a), au-delà des délais d'une
  requête HTTP ; une opération suivie survit à un rechargement de la page. L'interrogation passe
  par le filtre d'origine et le cookie existants (R14 de 001), sans canal de plus.
- **Alternatives considered** :
  - Server-Sent Events : un canal de plus à protéger, à reconnecter après un rechargement, pour un
    rafraîchissement d'une seconde que l'interrogation donne déjà ;
  - WebSocket : bidirectionnel, inutile ici ;
  - garder la requête synchrone avec un délai allongé : aucune progression, et la génération est
    perdue si l'onglet se ferme.

## R15. Journal des erreurs consultable dans l'interface (FR-031)

- **Decision** : un handler de journal dédié, `JournalHandler` (`infrastructure/logging`),
  enregistré au démarrage sur le logger racine, au niveau WARN.
  - Il écrit `${specwriter.data-dir.resolved}/logs/journal.jsonl`, avec la rotation par taille de
    JBoss LogManager (`SizeRotatingFileHandler`, 2 Mo, 4 sauvegardes, donc 5 fichiers).
  - Une entrée par ligne, en JSON : `at`, `level`, `logger`, `message`, `cause` (classe et message
    de chaque cause) et `stack` : 20 lignes par exception, puis celles de chaque cause sous un
    en-tête « Causée par », 80 lignes au plus.
  - Les erreurs du Copilot SDK, dont le message peut citer la requête, sont gardées comme cause
    sous forme de `SafeThrowable` : classe et pile de chaque cause, sans message, avec seulement
    des marqueurs techniques reconnus (code HTTP, `ECONNREFUSED`, erreur de certificat, code de
    sortie, délai, limite de jetons). L'outil y ajoute la phase (démarrage du CLI, création de la
    session, envoi du message), le modèle, la durée et le délai.
  - Chaque échec d'un appel au LLM est journalisé en WARN avec l'opération, la tentative et la
    durée, et la cause sans contenu.
  - Tout passe par `RedactingLogFilter.redact`, y compris la cause et la pile, que le filtre de la
    console ne touche pas.
  - `LogJournal` relit les fichiers du plus récent au plus ancien et renvoie les 500 dernières
    entrées. Une ligne illisible est ignorée.
  - `GET /api/journal?level=WARN|ERROR` : les entrées, les plus récentes d'abord.
  - Écran `/journal` : filtre par niveau, cause dépliable, bouton « Copier » (texte brut de
    l'entrée), « Actualiser ». La page du contexte y renvoie depuis le détail des erreurs.
  - Les erreurs de lecture Antora (page, code HTTP, motif), les échecs Jira et les sorties
    invalides du LLM sont journalisés en WARN, sans contenu.
- **Rationale** : un JSON par ligne se relit sans analyser un format de texte multiligne, et le
  handler dédié peut expurger la cause et la pile. Le fichier tournant borne la place occupée
  (10 Mo) et survit au redémarrage, souvent provoqué par l'erreur qu'on cherche.
- **Alternatives considered** :
  - handler fichier de Quarkus (`quarkus.log.file.*`) avec un format texte : il faut analyser des
    entrées sur plusieurs lignes, et le filtre ne masque pas la pile ;
  - extension `quarkus-logging-json` : une dépendance de plus (R12) pour une seule sortie ;
  - mémoire seule : l'erreur qui a fait redémarrer l'outil serait perdue ;
  - un tableau en base : des écritures SQLite pour chaque avertissement, et un journal perdu si la
    base est en cause.

## R16. Deux fiches et questions de clarification (FR-008a, FR-013h, FR-013i)

- **Decision** :
  - **Deux fiches**, `FUNCTIONAL` et `TECHNICAL`, chacune un `ProjectContext` (Markdown, hash,
    origine, date). La migration `V9__context_sheets.sql` ajoute à `project` les colonnes
    `technical_context`, `technical_context_hash`, `technical_context_origin` et
    `technical_context_updated_at`. Les colonnes `context*` existantes deviennent la fiche
    fonctionnelle : une fiche déjà acceptée y reste telle quelle, à régénérer pour adopter les
    nouvelles sections.
  - **Une seule génération** produit les deux fiches : l'extraction classe chaque fait dans une
    catégorie fonctionnelle ou technique, puis `build-project-context` rédige les deux fiches et
    les questions en un appel. La proposition porte deux diffs ; `accept` reçoit la liste des
    fiches acceptées, et chaque fiche est comparée à son propre hash de base (`CONTEXT_CHANGED`
    par fiche).
  - **Questions** : 10 au plus, rattachées à une fiche. Une question à laquelle l'utilisateur
    répond ou qu'il passe est enregistrée aussitôt dans `project_context_question` (`number`
    unique dans le projet, `sheet`, `label`, `rationale`, `answer`, `state` `ANSWERED`|`SKIPPED`,
    `asked_at`, `answered_at`). Les questions sans suite restent dans la proposition, en mémoire.
  - **Révision** : `POST /context-proposals/{id}/revise` relance une génération réduite à l'étape
    `BUILDING`, avec les faits gardés par la génération (30 minutes) et toutes les réponses. Elle
    remplace la proposition. Une génération expirée renvoie `404` : il faut régénérer.
  - Les réponses entrent dans `<reponses>` (`<reponse ref="Q-3" fiche="TECHNICAL"
    question="…">…</reponse>`), les questions passées dans `<questions-passees>`. `Q-n` est une
    référence connue pour la vérification des citations.
  - **Opérations sur les spécifications** : `ProjectContextInput(functional, technical)` remplace
    la chaîne `projectContext`. Les balises `<contexte-fonctionnel>` et `<contexte-technique>`
    remplacent `<contexte-projet>`. Une source `PROJECT_CONTEXT` cite l'une des deux fiches. Le
    hash de version combine les deux hash (`sha256(fonctionnel + ":" + technique)`, vide si les
    deux fiches le sont) ; la date retenue est la plus récente des deux.
  - **Agents de code** (US5) : `get_feature` renvoie `technicalContext`, la fiche technique.
- **Rationale** :
  - Deux fiches séparent ce qui guide la rédaction (vision, règles) de ce qui guide
    l'implémentation (architecture, conventions), que les agents de code lisent seule.
  - Les questions après la proposition ne bloquent pas la génération et portent sur un texte
    concret.
  - Enregistrer chaque réponse aussitôt évite de la perdre si la proposition expire.
- **Alternatives considered** :
  - une fiche en deux parties : une seule origine, donc une correction à la main de la partie
    technique bloquerait l'acceptation de la partie fonctionnelle ;
  - deux générations indépendantes : deux lectures des sources et deux fois plus d'appels ;
  - les questions avant la rédaction : la génération resterait suspendue en attente de
    l'utilisateur, au-delà des 30 minutes de conservation ;
  - une table `project_context` à deux lignes par projet : plus générale, mais elle impose de
    reconstruire `project` sous SQLite, ce que R6 évite.
