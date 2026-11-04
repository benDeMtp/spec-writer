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

> **Remplacée le 2026-11-04** par R18 à R28 (connaissance du projet par index local). Gardée pour
> l'historique des tâches T019–T034 et T065–T087.

- **Decision** : génération en deux temps (map-reduce) par le port LLM, avec deux nouvelles
  opérations :
  1. **`summarize-project-sources`** : les pages et tickets sont regroupés par lots d'environ
     40 000 caractères. Pour chaque lot, le LLM extrait des faits structurés (vocabulaire, acteurs,
     règles, contraintes, conventions), chacun avec sa source (URL de page ou clé de ticket).
  2. **`build-project-context`** : à partir de tous les faits, le LLM rédige la fiche Markdown de
     20 000 caractères au plus, organisée en sections fixes : Vocabulaire, Acteurs, Règles
     métier, Contraintes, Conventions. Chaque affirmation porte ses références `[source : …]`.
  - Le code vérifie que chaque référence citée existe dans les sources lues, et **répare plutôt
    que de redemander** (`Citations`) : une rédaction coûte une à deux minutes.
    - Une référence écrite autrement (chemin relatif ou URL complète, ancre, casse, ponctuation)
      est ramenée à la source qu'elle désigne sans ambiguïté.
    - Une affirmation dont aucune source n'est connue est retirée : ce qui n'est pas vérifiable
      n'entre pas dans la fiche. Un fait sans source du lot est écarté de même.
    - Une question déjà posée est écartée.
    - Chaque réparation est signalée (`REPAIRED`), sans contenu : références, numéros `Q-n`.
    - Le LLM n'est rappelé avec une `correction`, 3 essais au plus, que si une fiche dépasse
      20 000 caractères, si les deux sont vides ou si aucune affirmation ne cite une source lue.
  - Pour 300 pages et 200 tickets, il faut une quinzaine d'appels au LLM, ce qui tient dans les
    10 minutes de SC-004a.
  - **Délai d'un appel** : 600 s pour `summarize-project-sources` et `build-project-context`
    (`specwriter.llm.timeout.context`), 180 s pour les opérations interactives de 001
    (`specwriter.llm.timeout.interactive`). Un lot de 40 000 caractères, ou la rédaction des deux
    fiches, dépasse souvent la minute ; la génération tourne en tâche de fond et s'interrompt à
    tout moment (R14), donc rien ne justifie de couper un appel long. Le délai est passé au SDK
    (`sendAndWait(message, délai)`) : sans lui, le SDK abandonne au bout de 60 s, quel que soit
    le délai d'attente de l'appelant.
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

> **Remplacée le 2026-11-04** par R24 (connaissance retrouvée dans les opérations) et R25 (fiche
> générale).

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
  - la FTS5 est tenue à jour : le trigger `DELETE` de V6 retire une session, et V12 ajoute celui
    d'une version (la dernière restante prend sa place dans l'index). Les propositions en mémoire
    de la session sont purgées ;
  - lien Jira après la suppression de la version liée : il passe à la dernière restante, en
    `LOCAL_AHEAD` (Jira porte une version qui n'existe plus localement) ; après celle d'une autre
    version, le ticket redevient `IN_SYNC` si plus aucune version ne dépasse la version liée. Un
    conflit en cours le reste ;
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

> **Complétée le 2026-11-04** par R27 (jeu de référence de la connaissance du projet).

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

> **Réutilisée le 2026-11-04** pour l'indexation (R20) : même suivi, même interruption, même
> détail des erreurs. La génération des fiches disparaît.

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
  - Minuteur : l'état porte `stepStartedAt` ; l'écran affiche le temps écoulé depuis le début de
    la génération et de l'étape en cours (`role="timer"`, sans annonce), puis la durée finale.
  - Erreurs (FR-013g) : `{kind, location, reason, httpStatus, message}`, avec `kind` parmi
    `PAGE`, `SOURCE`, `JIRA`, `CORRECTION`, `REPAIRED` et `FAILURE`. Les messages ne citent que des
    adresses, des clés de ticket et des motifs, jamais un contenu.
  - Chaque erreur est aussi écrite au niveau WARN dans le journal (R15), sans contenu, avec le
    motif des corrections et des réparations ;
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

> **Remplacée le 2026-11-04** par R25 (fiche générale unique, fusion des deux fiches et des
> réponses Q-n).

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

## R18. Index local de la connaissance du projet (FR-032, FR-012)

- **Decision** :
  - Une table `knowledge_item` par élément indexé, rattachée au projet (`ON DELETE CASCADE`) :
    `kind` (`TICKET`, `DOC_SECTION`, `ADR`, `ARCH_ELEMENT`), `ref` (clé du ticket, URL de la page
    avec son ancre, nom canonique de l'élément d'architecture), `title`, `body` (texte borné à
    8 000 caractères), `status` (statut Jira et sa catégorie, statut d'un ADR), `scope`
    (`PROJECT`, `COMMON`) pour l'architecture, `role` (`VOCABULARY`, `ARCHITECTURE_OVERVIEW`, nul)
    pour la priorité de FR-042, `content_hash`, `source_updated_at`, `indexed_at`, `embedding`
    (BLOB, `float32` normalisés) et `embedding_model`.
  - Plein texte : table FTS5 `knowledge_fts` (`title`, `body`), tenue par des triggers comme
    `session_fts` (V6 de 001).
  - Graphe d'architecture : `arch_relation` (source, destination, description, technologie) et
    `arch_deployment` (élément, environnement, nœud racine, chemin du nœud, nombre d'instances).
  - Recherche **hybride** : les 50 meilleurs par le sens (cosinus en mémoire sur les vecteurs du
    projet, chargés à la demande puis gardés tant que l'index ne change pas) et les 50 meilleurs
    par BM25 (FTS5), fusionnés par *reciprocal rank fusion* (k = 60). Filtres par `kind`.
  - Volume visé : 10 000 éléments × 384 dimensions, soit 15 Mo de vecteurs et un parcours complet
    en moins de 20 ms, sans index vectoriel.
- **Rationale** :
  - SQLite et FTS5 sont déjà là ; le calcul exhaustif suffit largement à ce volume et évite une
    extension native (`sqlite-vec`) de plus.
  - La fusion par rang ne demande aucun réglage de poids entre deux échelles de score
    incomparables, ce qui sert l'anti-objectif « aucun réglage ».
  - Le texte indexé reste dans le dossier de données (`rwx------`), comme les spécifications ; il
    n'est envoyé à Copilot que par extraits retrouvés (FR-012).
- **Alternatives considered** :
  - `sqlite-vec` : une bibliothèque native à distribuer sur trois OS pour un gain inutile à ce
    volume ;
  - un moteur externe (Lucene, Qdrant) : une dépendance ou un service de plus ;
  - plein texte seul : ne rapproche pas « saisir un avoir » et « annuler une facture ».

## R19. Modèle d'embedding local (FR-032, Q « recherche par le sens »)

- **Decision** :
  - `dev.langchain4j:langchain4j-embeddings:1.21.0-beta31` (aligné sur `langchain4j-core` 1.21.0
    de Quarkus LangChain4j), avec `OnnxEmbeddingModel`, onnxruntime 1.20.0 et les tokenizers DJL
    0.36.0, en *mean pooling*, vecteurs normalisés.
  - Modèle par défaut : `Xenova/multilingual-e5-small`, `onnx/model_quantized.onnx` (118 Mo) et
    `tokenizer.json` (17 Mo), licence MIT, à une révision épinglée avec les SHA-256 des fichiers.
  - Préfixes de e5 : `query: ` pour un besoin ou une recherche, `passage: ` pour un élément indexé.
  - **Distribution** : téléchargé une fois au premier besoin, dans `${data}/models/<nom>/`, par le
    proxy du poste (`ProxySettings`), avec la progression de l'indexation et la vérification des
    SHA-256. Un modèle incomplet ou altéré est supprimé et retéléchargé.
  - **Remplacement** : dans Configuration, un dossier local contenant `model.onnx` et
    `tokenizer.json` (par exemple sentence-camembert exporté en ONNX), le mode de *pooling* et les
    préfixes. Changer de modèle recalcule tous les vecteurs (`embedding_model` diffère).
  - Sans modèle (téléchargement impossible, fichier illisible, bibliothèque native introuvable) :
    recherche par les termes seuls, signalée (FR-043).
  - **Chargement au démarrage** : un modèle déjà présent sur le poste est chargé en tâche de fond
    au lancement (`EmbeddingWarmUp`, jamais de téléchargement) ; sinon le premier besoin après le
    lancement serait cherché par les termes seuls. Les tickets proches ne sont « partiels » que si
    le modèle manque ou si l'index se construit (indexation complète, ou première
    synchronisation), pas pendant la mise à jour incrémentale lancée à chaque besoin.
- **Rationale** :
  - SDK Copilot vérifié (1.0.28) : aucune API d'embedding, seulement des sessions de conversation.
  - e5 est entraîné pour la recherche (besoin court contre long passage) et multilingue ; sur
    MTEB-French, 0,52 en recherche contre 0,44 pour les MiniLM multilingues. Les modèles
    sentence-camembert et sentence-flaubert sont entraînés à comparer deux phrases de même nature :
    ils restent possibles par le dossier local et seront comparés sur le jeu de référence (R27).
  - Le téléchargement à l'usage garde l'installeur léger ; les fichiers sont joignables par le
    proxy (vérifié le 2026-11-04).
- **Alternatives considered** : modèle embarqué dans l'installeur (135 Mo de plus pour chaque
  installation, même sans connaissance du projet) ; `langchain4j-embeddings-e5-small-v2-q`
  (anglais seulement) ; tri des candidats par Copilot (écarté par l'utilisateur).
- **Vérifié en mise en route (T089, 2026-11-04)** :
  - le jar des tokenizers DJL 0.36.0 embarque ses natifs pour linux-x86_64, linux-aarch64,
    osx-aarch64 et win-x86_64, **pas pour osx-x86_64** ; onnxruntime 1.20.0 couvre les cinq ;
  - avec `ai.djl.offline=true`, les natifs embarqués se chargent sans téléchargement ; un vecteur
    prend moins de 50 ms (`OnnxEmbeddingSmokeIT`, modèle MiniLM quantifié de test) ;
  - DJL extrait ses natifs dans `~/.djl.ai` : la propriété `DJL_CACHE_DIR` pointe vers
    `${data}/djl` pour garder les données de l'outil dans son dossier ;
  - sur un Mac Intel, le natif manquant n'est pas téléchargé (mode hors ligne) : la recherche
    passe par les termes seuls, signalée (FR-043). Aucun poste de l'équipe n'est concerné.

## R20. Indexation et synchronisation (FR-013d à FR-013g, FR-033)

- **Decision** :
  - Une **indexation** par projet à la fois, sur thread virtuel, avec le suivi de R14 (étapes
    `JIRA`, `DOCUMENTATION`, `ARCHITECTURE`, `EMBEDDING`, élément en cours, compteurs, temps
    écoulé, interruption immédiate). Ce qui est indexé avant une interruption est gardé.
  - **Déclencheurs** : enregistrement d'une source (complète), ouverture d'un besoin
    (incrémentale, sans attendre), bouton « Resynchroniser » (incrémentale, ou complète au choix).
    Aucune indexation au démarrage de l'outil. Une source enregistrée pendant une indexation est
    relue par une indexation complète dès la fin de celle-ci (une seule en attente par projet,
    annulée par « Interrompre ») ; l'ouverture d'un besoin pendant une indexation est sans effet.
  - **Écritures concurrentes** : l'indexation écrit par petites transactions pendant que
    l'utilisateur travaille. Chaque transaction SQLite prend le verrou d'écriture dès son début
    (`transaction_mode=IMMEDIATE` dans l'URL JDBC) et attend son tour (`busy_timeout=5000`) : sans
    cela, une transaction qui lit puis écrit (création d'une session) échoue aussitôt en
    `SQLITE_BUSY_SNAPSHOT`. La fin d'une synchronisation relit la source avant d'enregistrer ses
    repères, pour ne pas effacer un workspace déclaré entre-temps.
  - **Jira** : `project = KEY AND updated >= "<repère - 1 jour>" ORDER BY updated ASC`, par pages
    de 50 (Jira lit la date JQL dans le fuseau de l'utilisateur : la marge d'un jour évite d'en
    manquer ; relire quelques tickets inchangés ne coûte rien, l'empreinte les écarte), dans la limite de débit de R13. Le repère est la plus grande date `updated` lue. La
    description passe par `JiraFormatConverter.toMarkdown`. Un ticket est **fermé** si sa
    catégorie de statut Jira est `done`. Les tickets supprimés sont retirés à la synchronisation
    complète (liste des clés seule, `fields=key`), et dès qu'un ticket proche renvoie 404.
  - **Antora** : relecture des pages du sitemap ; chaque section (`.sect1`, titre `h2`) devient un
    élément, découpé en morceaux d'environ 1 500 caractères s'il est plus long. Seuls les
    éléments dont le `content_hash` change sont réécrits et revectorisés : la date `lastmod`
    d'Antora est celle de la construction du site, elle ne suffit pas.
  - **ADR** : une page dont le chemin ou le titre désigne un ADR (`adr`, `ADR-<n>`,
    « Architecture Decision Record ») est un élément `ADR` unique (découpé si long), avec son
    statut lu dans la page (ligne « Statut » ou « Status » : accepté, proposé, remplacé par
    ADR-<n>, obsolète). Un ADR remplacé garde le lien vers celui qui le remplace.
  - **Rôles** (FR-042) : une page ou section dont le titre évoque le glossaire ou le vocabulaire
    porte `VOCABULARY` ; l'architecture d'ensemble porte `ARCHITECTURE_OVERVIEW`.
  - **Structurizr** : relecture quand le hash du workspace ou d'un fichier qu'il étend ou inclut
    change (fichiers repérés par les directives `extends` et `!include`). Une erreur d'analyse
    garde le modèle précédent et donne le fichier et la ligne.
  - Un index incomplet (indexation en cours, source en erreur, modèle absent) est **annoncé**
    par l'état de la connaissance ; il ne bloque rien (FR-043).
- **Rationale** : la synchronisation incrémentale tient en quelques requêtes ; la comparaison par
  hash évite de revectoriser tout le site à chaque construction ; la reprise de R14 donne le
  suivi déjà testé.
- **Alternatives considered** : synchronisation périodique en tâche de fond (un service qui tourne
  sans demande, contraire à l'esprit local de 001) ; webhook Jira (exige une exposition réseau).

## R21. Lecture du workspace Structurizr (FR-038)

- **Decision** :
  - `com.structurizr:structurizr-dsl:6.2.4` (Java 21+), `StructurizrDslParser` en **mode
    restreint** (`setRestricted(true)`), puis seules les fonctionnalités `Features.FILE_SYSTEM`
    et `Features.INCLUDE` réactivées : en version 6, le mode restreint coupe aussi le système de
    fichiers, donc `extends` et `!include`. Restent coupés : `!script`, `!plugin`, HTTP et HTTPS,
    variables d'environnement, `!components`, `!docs` et `!adrs`. Les moteurs de script (Groovy,
    Kotlin, JRuby) ne sont qu'en portée `test` dans son pom.
  - Vérifié en mise en route (T088, T090) : `structurizr-client` ne peut pas être exclu (le
    parseur charge `com.structurizr.http.HttpClient` à sa construction) ; `structurizr-component`
    (bcel, javaparser, guava) est exclu sans effet sur l'analyse.
  - On lit le **DSL**, jamais `workspace.json` (régénéré seulement par Structurizr lite).
  - Éléments indexés : personnes, systèmes, conteneurs, composants, avec un nom canonique
    (`Système/Conteneur/Composant`), description, technologie, tags et propriétés.
  - **Propre ou commun** :
    1. la propriété `ownership` fait foi quand elle est renseignée (valeur égale à l'identifiant
       du projet déclaré avec le workspace : propre ; autre : commun) ;
    2. sinon on analyse aussi, seul, le modèle étendu (`workspace extends <fichier>`) ; un élément
       qui y existe déjà est commun ;
    3. le reste est propre au projet, y compris ce qu'il ajoute dans un système commun avec
       `!element`.
  - **Infrastructure partagée** : un nœud de déploiement défini dans un fichier inclus depuis un
    dossier partagé (par défaut `infra/`, repéré par les directives `!include`) est partagé.
- **Rationale** : l'analyseur officiel résout `extends`, `!include`, `!element` et `archetypes`
  comme Structurizr ; réécrire ce DSL serait long et fragile. Le mode restreint évite d'exécuter
  du code venu d'un fichier (OWASP A03, A08).
- **Alternatives considered** : `workspace.json` (en retard sur le DSL) ; l'API du serveur
  Structurizr (choix de l'utilisateur : dossier local) ; un analyseur maison.
- **Organisation réelle observée** (`~/Documents/Projets/laposte/C4`) : `model.dsl` commun,
  `02-relaiV2/workspace.dsl` en `extends ../model.dsl`, `!include "../infra/…"`, `!element`,
  archétype `topic`, 17 éléments avec `ownership`. Elle sert de base au jeu de référence (R27).

## R22. Flux, modules impactés et efforts de déploiement (FR-039 à FR-041)

- **Decision** :
  - **Modules impactés** : la recherche (R18) propose au plus 15 éléments d'architecture
    candidats ; une opération LLM `identifyImpact` choisit parmi eux les modules impactés et
    déclare les échanges que le besoin suppose (paires source, destination, raison). Un nom hors
    des candidats est refusé (même mécanisme de réparation que `Citations`).
  - **Existence d'un flux**, calculée par l'outil : relation directe entre A et B ou leurs
    descendants, dans un sens ou dans l'autre ; ou relation A–T et T–B avec **un seul**
    intermédiaire T de transport. T est de transport si un tag ou un archétype vaut `topic`,
    `queue` ou `bus`, ou si sa technologie cite Kafka, AMQP, JMS ou MQ (liste réglable).
  - **Déploiement de production** : environnement dont le nom contient `prod` (insensible à la
    casse, réglable). Constats :
    - module sans instance en production : « à déployer » ;
    - module en plusieurs instances : « plusieurs déploiements » ;
    - échange entre modules dont les instances de production ont des nœuds racines différents :
      « traverse des infrastructures (ouverture réseau ou exposition à prévoir) » ;
    - module propre ajouté dans un système commun, ou déployé sur un nœud partagé : « dépend d'une
      infrastructure partagée ».
  - **À confirmer sur le jeu de référence réel** : dans `~/Documents/Projets/laposte/C4`, tous les
    nœuds de production sont définis dans `infra/`. La règle « déployé sur un nœud partagé »
    signalerait alors chaque module propre. Le jeu fictif sépare un nœud propre (Azure, défini
    dans le workspace avec `!element production`) d'un nœud partagé (ITaaS, dans `infra/`) ; si le
    constat se révèle bavard sur le jeu réel, il sera restreint aux modules ajoutés dans un système
    commun.
  - Ces constats sont des **faits calculés** : ils sont insérés tels quels dans les rubriques de
    la spécification (« Architecture », « Efforts supplémentaires (déploiement) »), sans être
    réécrits par le LLM.
- **Rationale** : l'IA juge ce que le besoin suppose ; l'outil vérifie ce que le modèle contient
  (FR-040 : « jamais deviné »). Borner l'intermédiaire à un seul évite que tout soit relié.
- **Alternatives considered** : laisser le LLM lire tout le modèle (volume, et vérification
  impossible) ; chemins de longueur quelconque (tout serait relié).

## R23. Tickets proches et parcours selon le statut (FR-034, FR-035)

- **Decision** :
  - Après la saisie du besoin, `FindRelatedIssues` cherche les 20 meilleurs tickets (R18), puis
    l'opération LLM `judgeRelatedIssues` en garde 5 au plus, avec la raison du rapprochement (titre
    et extrait de 600 caractères par candidat). Sans consentement Copilot, ou si l'appel échoue,
    les 3 meilleurs par score sont montrés sans raison.
  - Le statut des tickets retenus est relu dans Jira (`fields=status,updated`) ; à défaut, le
    statut indexé et sa date sont montrés.
  - Les questions de clarification ne sont demandées qu'après le choix de l'utilisateur sur ces
    tickets (ou « aucun ne correspond »).
  - **Implémentation (T108)** : la recherche se fait à la création de la session, de façon
    synchrone (index local, un appel `judgeRelatedIssues`, relecture des statuts). Sans ticket
    proche, le premier lot de questions est demandé aussitôt, comme avant ; avec des tickets, la
    session est créée sans questions et le lot est demandé par la décision. L'état `PENDING` du
    contrat reste réservé à une recherche asynchrone future. Le lien et son état (`PENDING`,
    `LINKED`, `FAILED`) sont gardés dans `session.relates_to` et `session.relates_to_link`.
  - **Fermé** : la session garde `relatesTo`. À la création du ticket (flux de 001), l'aperçu
    annonce le lien, puis `POST /rest/api/2/issueLink` crée le lien, avec le type dont le sens
    sortant est « relates to » (lu une fois par `GET /rest/api/2/issueLinkType`). Un échec du lien
    laisse la story créée et propose de réessayer le lien.
  - **Non fermé** : `compareWithIssue` (besoin, ticket complet) renvoie le commun, les écarts et
    une suggestion. « C'est la même » : la session est remplacée par l'import du ticket (001),
    puis le besoin est proposé comme modification assistée (`ProposeAiEdit`) du ticket importé.
    « C'est différent » : la rédaction continue.
- **Rationale** : la recherche ramène des candidats, le LLM juge sur un petit nombre ; le parcours
  réutilise l'import, la modification assistée et la création de ticket de 001.
- **Alternatives considered** : seuil de similarité seul (bruit selon le modèle, réglage à
  faire) ; création du lien avant la story (impossible : la story n'existe pas encore).

## R24. Connaissance retrouvée dans les opérations de l'IA (FR-009 à FR-011, FR-036, FR-037, FR-042)

- **Decision** :
  - Une **sélection de connaissance** est calculée pour la session à partir du besoin et des
    réponses : fiche générale ; rôles `VOCABULARY` et `ARCHITECTURE_OVERVIEW` (3 000 caractères
    au plus) ; 4 ADR au plus (6 000), un ADR remplacé étant remplacé par son successeur ; 3
    tickets proches retenus (3 000) ; faits d'architecture calculés (R22, 3 000). Elle est
    recalculée quand le besoin ou les réponses changent.
  - Elle est transmise aux 6 opérations dans une balise `<connaissance-projet>`, avec
    `<fiche-generale>`, `<vocabulaire>`, `<adr ref statut>`, `<ticket ref statut>` et
    `<architecture>`, contenu échappé par `data()`. Elle remplace `<contexte-fonctionnel>` et
    `<contexte-technique>`.
  - Nouvelles sources d'un élément de spécification : `PROJECT_CONTEXT` (extrait de la fiche),
    `JIRA_ISSUE` (clé), `DOC_PAGE` (URL, y compris un ADR), `ARCH_ELEMENT` (nom). Une référence
    hors de la sélection est ramenée par `Citations` ou retirée (T087).
  - `DraftSpecification` gagne `adrDeviations` (ADR, décision, écart), rendu dans une rubrique
    « Écarts aux ADR ». Un écart détecté pendant les questions passe par le canal
    `contradictions` de `propose-questions` (FR-037).
  - `InputFingerprint` couvre la fiche générale et, pour chaque élément de la sélection, sa
    référence et son `content_hash` (FR-011). La version enregistre la sélection (références et
    hash), ce qui sert aussi à MCP (R26).
  - **Implémentation (T110)** :
    - `SelectKnowledge` calcule la sélection à chaque opération, à partir du besoin et des réponses
      (recherche locale, sans appel externe). L'analyse d'architecture (`identifyImpact`) n'est
      faite que pour les opérations qui produisent une spécification (génération, modification,
      fusion, import) : les questions reçoivent la sélection sans `<architecture>`, ce qui évite un
      appel à l'IA par lot. Le choix de l'IA est gardé par session tant que le besoin, les réponses
      et les candidats (référence et `content_hash`) ne changent pas : à entrées identiques, la
      version existante est réutilisée sans appel. L'existence des échanges et les constats de
      production sont toujours recalculés sur le modèle courant. Un échec de l'analyse laisse la
      rédaction se faire sans faits d'architecture, sans rien garder.
    - Budget : fiche générale 20 000, vocabulaire et vue d'ensemble 3 000, 4 ADR de 1 500
      (titre compris), 3 tickets de 1 000, faits d'architecture 3 000 : 35 000 au plus.
    - Successeur d'un ADR remplacé : l'ADR dont le nom de fichier ou le titre porte le numéro cité
      (« Remplacé par ADR-005 »), sur 5 remplacements au plus ; un ADR obsolète est écarté.
    - Une source de la connaissance hors de la sélection est d'abord ramenée par `Citations` ; si
      elle reste inconnue, la traçabilité la signale et la spécification est redemandée (même
      boucle que FR-012). Un écart à un ADR inconnu est retiré.
    - En attendant la fusion des fiches (T113), la fiche générale est la fiche fonctionnelle suivie
      de la fiche technique.
- **Rationale** : le volume envoyé reste borné quel que soit le projet (anti-objectif
  « devoir s'y reprendre ») ; la sélection enregistrée rend la version reproductible.
- **Alternatives considered** : laisser le LLM appeler un outil de recherche pendant la rédaction
  (sessions Copilot sans outil, règle 6a de 001) ; une sélection par opération (empreintes
  instables d'une opération à l'autre).

## R25. Fiche générale (FR-008, FR-008a)

- **Decision** :
  - La colonne `context` de `project` devient la fiche générale ; une migration y fusionne la
    fiche fonctionnelle, la fiche technique (sous les titres « ## Fonctionnel » et
    « ## Technique ») et, s'il y en a, les réponses Q-n (« ## Réponses de clarification »),
    tronquée à 20 000 caractères avec un avertissement journalisé si besoin. Les colonnes
    `technical_context*` et la table `project_context_question` sont supprimées par une migration
    nouvelle (les migrations appliquées ne changent pas).
  - Un seul éditeur, à la main ; `PUT /api/projects/{id}/context`.
  - **Implémentation (T113)** : migration Java `V11__general_context` (journalisation de la
    troncature, sans contenu). Une fiche fonctionnelle seule, sans réponse, est gardée telle quelle,
    sans titre ajouté ; l'empreinte de chaque fiche est recalculée.
- **Rationale** : aucune saisie de l'utilisateur n'est perdue, et le modèle redevient simple.

## R26. Connaissance pour les agents de code (FR-025)

- **Decision** :
  - `get_feature` renvoie `knowledge` : la fiche générale, les ADR et les modules de la sélection
    enregistrée avec la version (R24), avec leur statut, leur lien et, pour les modules, leurs
    relations et leur portée (propre, commun).
  - Nouvel outil `search_knowledge {projectId, query, kinds?}` : 10 éléments au plus (type,
    référence, titre, statut, extrait de 600 caractères). Le vecteur de la requête est calculé
    localement ; aucun appel à Jira ni à Copilot, aucune synchronisation (FR-029).
- **Rationale** : l'agent lit exactement la connaissance qui a servi à rédiger, et peut chercher
  au-delà sans élargir ses droits.

## R27. Jeu de référence de la connaissance (SC-002 à SC-004b, principe IV)

- **Decision** :
  - **Forme** : un dossier contenant `cases.json` (`[{need, expectedIssues, expectedAdrs,
    expectedModules, expectedMissingFlows: [[a, b]], expectedEfforts}]`) et les sources figées :
    `jira/` (tickets exportés en JSON), `antora/` (site produit) et `c4/` (workspace et fichiers
    inclus).
  - **Jeu fictif** dans le dépôt (`backend/src/test/resources/knowledge/`) : tickets, ADR (dont un
    remplacé), glossaire, workspace qui étend un modèle commun, inclut une infrastructure et
    passe par un topic. Il tourne dans `verify` (recherche, flux, déploiements, sans LLM) et en
    E2E avec le LLM rejoué.
  - **Jeu réel** hors du dépôt : chemin donné par `specwriter.reference-set` ; la classe de test
    taguée `reference` (exclue par défaut, comme `eval`) s'arrête proprement s'il est absent. Les
    réponses attendues sont écrites par l'utilisateur ; une commande prépare les besoins et les
    candidats retrouvés pour qu'il n'ait qu'à valider.
  - Mesures : rappel des tickets attendus parmi les 5 montrés (≥ 90 %), ADR cités (100 %), flux
    manquants et efforts (100 %, sans faux positif), rédaction aboutie du premier essai (100 %).
    Le jeu réel sert aussi à comparer e5 et sentence-camembert (R19).
- **Rationale** : les données de l'entreprise ne doivent jamais entrer dans le dépôt personnel ;
  le jeu fictif garde un garde-fou dans le build.

## R28. Retrait des fiches générées (tâches T019–T034 et T065–T087)

- **Decision** :
  - Retirés : `ProposeProjectContext`, `ContextProposalStore`, `AcceptContextProposal`,
    `AnswerContextQuestion`, `ManageContextQuestions`, `PreviewContextSources`, les opérations
    `summarize-project-sources` et `build-project-context` (prompts, schémas, rejeux),
    `ContextSheet`, `ContextSheets`, `ContextQuestion`, les endpoints `context-proposals`,
    `context-questions` et `context/{sheet}`, et les composants front de proposition.
  - Gardés et réemployés : projets, sources de documentation et leurs motifs, `AntoraReader`
    (lecture et sections), la limite de débit Jira, `ContextGenerations` devenu `Indexations`
    (suivi, minuteur, interruption), le journal des erreurs et `SafeThrowable`, les délais
    d'appel par opération, `Citations` (références de la sélection).
  - Le retrait se fait dans les tâches qui remplacent chaque partie, test par test, pour que
    `verify` et les E2E restent verts à chaque commit.

