# Research: Couche LLM sur les AI Services Quarkus, et renommage du package

Feature 014. Sources : essai `CopilotToolsSpikeIT` du 16/11 (copilot-sdk-java 1.0.28-preview.0,
vrai Copilot), sources du SDK, jars `quarkus-langchain4j-core` 2.0.0.Beta1 et `langchain4j` 1.21.0.

## R1 — La boucle d'outils tourne dans LangChain4j, pas dans la CLI Copilot

**Decision** : toutes les opérations passent par des AI Services déclarés avec
`@RegisterAiService`. L'agent conversationnel est un AI Service en flux (`TokenStream`) avec deux
outils (`@Tool`) : la recherche de connaissance et l'analyse de périmètre. La CLI Copilot ne fait
que transmettre chaque appel au modèle ; elle n'exécute aucun outil.

Cette décision **remplace** :
- 001 R3, pour sa partie « AI Services » : l'intention n'avait pas été appliquée. L'adaptateur
  `ChatModel` sur le SDK, lui, est gardé et étendu (R2).
- 010 R1 : la boucle maison `converse-plan` / `converse-answer` disparaît. Ses raisons sont levées :
  - le rejeu reste au niveau du `ChatModel` (R6) ;
  - le plafond est tenu par le framework et un garde-fou (R5) ;
  - la CLI ne reçoit pas la main (R8).

**Rationale** :
- C'est le choix du mainteneur : coller à l'extension plutôt que réimplémenter.
- L'extension fournit déjà ce que le code maison refait :
  - consignes depuis une ressource (`@SystemMessage(fromResource=…)`) ;
  - sorties typées avec schéma JSON ;
  - garde-fous avec relance ;
  - plafonds d'appels d'outils ;
  - réponse en flux avec événements d'outils (`beforeToolExecution`, `onToolExecuted`) ;
  - écouteurs de modèle pour le journal.
- La prochaine feature (tâches) ajoutera des outils sans toucher à la boucle.

**Alternatives** :
- **Voie B, la boucle dans la CLI** (`SessionConfig.setTools`, outils exécutés par la CLI).
  - Avantages : usage natif du SDK, skills natifs.
  - Inconvénients : contourne l'extension Quarkus (ni `@Tool`, ni garde-fous Quarkus), lie
    spec-writer à un SDK en préversion, et impose un rejeu HTTP/SSE (R6).
  - Écartée.
- **Garder la boucle maison** : contraire à la demande, et chaque capacité nouvelle coûte une
  action de plan, un schéma et une consigne.

## R2 — L'adaptateur `ChatModel` rend les appels d'outils au framework

**Decision** : `CopilotSdkChatModel` (et un nouveau `CopilotSdkStreamingChatModel`) traduisent une
`ChatRequest` LangChain4j en **une session SDK neuve**, comme aujourd'hui.

- **Outils** : chaque `ToolSpecification` de la requête devient un `ToolDefinition` du SDK avec :
  - `isTerminal = true` et `skipPermission = true` ;
  - un gestionnaire qui **capture** l'appel (identifiant, nom, arguments JSON) et renvoie un accusé
    neutre ;
  - `setAvailableTools` réduit à ces seuls noms. Liste vide pour une opération sans outil, comme
    aujourd'hui.

  Le tour s'arrête sur l'appel (essai S3 : arguments capturés, réponse vide). L'adaptateur renvoie
  `AiMessage` avec les `ToolExecutionRequest` capturés. LangChain4j exécute la méthode `@Tool` dans
  l'application, puis rappelle le modèle.
- **Historique** : la session SDK ne garde rien d'un appel à l'autre.
  - Le message système reste le seul système (`SystemMessageMode.REPLACE`).
  - Les messages suivants (utilisateur, réponses du modèle, appels d'outils, résultats d'outils) sont
    sérialisés dans **le message utilisateur**, entre balises, échappés comme les autres données :

    ```text
    <echange-outils>
      <appel id="…" outil="search_knowledge">{json}</appel>
      <resultat id="…">…</resultat>
    </echange-outils>
    ```

    Le format exact est dans [contracts/llm-adapter.md](contracts/llm-adapter.md).
- **Capacités** : le modèle déclare `RESPONSE_FORMAT_JSON_SCHEMA`. Un `responseFormat` à schéma est
  transmis par `MessageOptions.setResponseSchema`, comme aujourd'hui.
- **Appels parallèles** : l'essai S2 montre que le modèle peut demander plusieurs outils dans une
  même réponse.
  - Tous les appels d'une réponse sont capturés avant l'arrêt du tour.
  - À vérifier par un test d'essai en début d'implémentation (T-spike). S'ils ne le sont pas,
    l'adaptateur demande un seul appel par réponse dans la consigne de l'agent.

**Rationale** : c'est la seule façon de laisser la boucle au framework avec un modèle servi par la
CLI. L'essai S3 l'a montré possible sans rien exécuter côté CLI.

**Alternatives** :
- **Session SDK conservée d'un appel à l'autre** (le modèle garde l'historique natif) : le
  `ChatModel` LangChain4j est sans état ; il faudrait relier les sessions aux invocations, et une
  session abandonnée fuit. Écartée.
- **`CopilotRequestHandler` pour réécrire le trafic** : trop bas niveau, format du fournisseur.
  Écartée.

## R3 — Déclaration des AI Services

**Decision** :
- **Une interface par famille d'opérations**, dans `infrastructure/llm/ai/` :
  - `QuestionsAi` : `propose-questions`, `reassess-after-change` ;
  - `SpecificationAi` : `generate-specification`, `edit-specification`, `merge-conflict`,
    `structure-imported-text`, `revise-specification`, `list-changes` ;
  - `KnowledgeAi` : `judge-related-issues`, `compare-with-issue`, `identify-impact` ;
  - `ConversationAi` : `converse-agent` (flux, outils), `propose-need`, `compact-memory`,
    `qualify-perimeter`.
- **Annotations** :
  - `@RegisterAiService(chatMemoryProviderSupplier = NoChatMemoryProviderSupplier.class)` : aucune
    mémoire du framework. L'historique est construit par l'application, comme aujourd'hui.
  - `@SystemMessage(fromResource = "prompts/<opération>.txt")`. La première ligne
    `[operation:<nom>]` est gardée : elle sert au rejeu et au journal.
  - `@UserMessage("{data}")` : le texte utilisateur reste construit par `UserMessages`, qui
    échappe et balise les données. Le framework n'interpole qu'une variable déjà échappée.
- **Retours** : les records DTO actuels de `LangChainLlmClient` (sortie structurée générée par le
  framework) ou `String` (texte libre).
- **Adaptateurs de port** : les ports `LlmClient` et `ConversationLlm` du domaine ne changent pas.
  `LangChainLlmClient` et `LangChainConversationLlm` deviennent des adaptateurs minces : ils
  construisent le texte utilisateur, appellent l'AI Service et convertissent le DTO en objet du
  domaine.
- **Choix du modèle** : par opération, comme aujourd'hui (`ModelSelection`). L'adaptateur
  `ChatModel` lit le modèle courant ; aucun sélecteur nommé n'est nécessaire.

**Rationale** :
- L'architecture hexagonale reste vérifiée par ArchUnit : les AI Services sont de l'infrastructure.
- Les tests du domaine et de l'application gardent leurs doubles de port.

**Points vérifiés** :
- Les consignes ne contiennent aucune accolade : aucun risque que le moteur de gabarits du
  framework les interprète.
- Un test de non-régression le garantira pour les consignes futures.

**Alternatives** :
- **Gabarits à variables multiples** (`{need}`, `{answers}`…) : il faudrait refaire l'échappement
  dans les gabarits, au risque d'une donnée non échappée. Écartée.

## R4 — Sorties structurées, relances et journal

**Decision** :
- **Schéma** : le framework génère le schéma JSON depuis le type de retour, et l'adaptateur le
  transmet en mode strict (`StrictJsonSchema`, gardé).
- **Relances** : un garde-fou de sortie (`OutputGuardrail`) par famille valide la réponse
  (`LlmSchemas`, règles du domaine comme le thème de question connu). En cas d'échec, il demande
  une **reprise** (`reprompt`) avec un message neutre, deux fois au plus
  (`quarkus.langchain4j.guardrails.max-retries=2`). La boucle `MAX_ATTEMPTS` de
  `LangChainLlmClient` est supprimée.
- **Journal** : un `ChatModelListener` consigne l'opération (lue dans le marqueur), la durée, le
  numéro de tentative et les noms d'outils appelés. Jamais le contenu (FR-008). Les lignes
  actuelles (`LLM <op> : <ms> ms, <n> tentative(s)`) sont conservées à l'identique, pour les
  habitudes de lecture du journal.
- **Erreurs du SDK** : traduites en `DomainException` par l'adaptateur `ChatModel`, comme
  aujourd'hui (`toDomain`).

**Alternatives** : garder la boucle de relance maison. Contraire à FR-012.

## R5 — L'agent conversationnel : outils, plafond, étapes visibles, arrêt

**Decision** :
- **Méthode** :
  `ConversationAi.converse(@UserMessage String data, InvocationParameters turn) : TokenStream`.
  - `InvocationParameters` (LangChain4j 1.21) transporte le contexte du tour, sans mémoire, jusqu'aux
    outils.
  - Ce contexte est un `TurnTools` du domaine : rechercher, analyser le périmètre, publier une étape,
    savoir si l'utilisateur a arrêté. `Converse` le fournit. La logique de tour (sources numérotées,
    étapes enregistrées, événements SSE) reste dans l'application.
- **Outils** : un bean `AgentTools` (infrastructure) expose `@Tool search_knowledge(query, kinds)`
  et `@Tool analyze_perimeter(terms)`.
  - Chaque outil appelle `TurnTools`.
  - Il renvoie au modèle les sources numérotées, dans la continuité du tour (même format
    `<source n=…>` qu'aujourd'hui).
- **Plafond** : un garde-fou d'entrée d'outil (`ToolInputGuardrail`) compte les appels du tour.
  - Au-delà de 5, il refuse avec un résultat qui dit au modèle de répondre avec ce qu'il a et de
    signaler l'arrêt.
  - `maxToolCallingRoundTrips` = 7 sert de butée dure.
  - Le comportement reste celui de FR-002 de la 010.
- **Étapes visibles** : `beforeToolExecution` publie le début de l'étape. `onToolExecuted` enregistre
  l'étape (`SearchStep`) et publie `Event.Step` ou `Event.Perimeter`, comme aujourd'hui.
- **Réponse en flux** : `onPartialResponseWithContext` transmet chaque morceau (`Event.Delta`). Si
  `TurnTools.cancelled()` est vrai, il appelle `streamingHandle().cancel()`.
  - Le flux du SDK (`assistant.message_delta`) alimente `CopilotSdkStreamingChatModel`.
  - L'arrêt abandonne la session SDK, comme aujourd'hui (`abortQuietly`).
- **Consigne** : `converse-plan.txt` et `converse-answer.txt` fusionnent en `converse-agent.txt`.
  - Les règles de choix (chercher, analyser le périmètre, répondre) passent dans les descriptions
    d'outils et dans la consigne.
  - Les règles de réponse (citations, abstention, modules non impactés…) sont reprises telles
    quelles.
  - L'évaluation réelle garde la fusion (R7).
- **Réflexion** : les étapes de réflexion du modèle (`ReasoningSink`) passent par
  `onPartialThinking` si le modèle en produit, sinon rien ne change à l'écran.

**Rationale** : le comportement visible est inchangé, la boucle appartient au framework, et
`Converse` garde la responsabilité métier du tour.

**Alternatives** :
- **Outils fournis par un `ToolProvider` construit à chaque tour** : plus dynamique, mais
  inutile pour deux outils fixes.
- **`@ToolMemoryId`** : impose une mémoire de conversation du framework, dont on ne veut pas.

## R6 — Rejeu en E2E et en dev mode

**Decision** : le rejeu reste au niveau du `ChatModel`.
- **Entrées** : `ReplayChatModel` garde son format (`operation`, `userContains`, `response`/`text`,
  `reasoning`) et gagne :
  - `toolCalls: [{name, arguments}]` : la réponse est un appel d'outil ;
  - `afterTools: n` : l'entrée ne vaut que si la requête contient déjà `n` résultats d'outils.
    Absent = toute étape.
- **Flux** : `ReplayStreamingChatModel` rejoue `text` en morceaux, ou les `toolCalls`, par le même
  index.
- **Fixtures à réécrire** : les 19 fixtures `converse-plan` / `converse-answer` deviennent des
  entrées `converse-agent` (une par étape). Les 42 autres gardent leur fichier et ne changent qu'au
  besoin.
- **Erreur d'appariement** : le message d'erreur nomme l'opération, l'étape et les fragments
  attendus (US5, scénario 2).
- **Procédure de réenregistrement** : décrite dans `e2e/fixtures/llm/README.md`. Elle part du
  journal en dev mode `live` et ne copie jamais de contenu confidentiel.

**Rationale** : les tests de bout en bout ne voient pas la différence, et le format reste
lisible et éditable à la main.

**Alternatives** : enregistrer le trafic HTTP/SSE par `CopilotRequestHandler` (essai S5). Trop
fragile (format du fournisseur, identifiants) et inutile avec la voie A.

## R7 — Évaluation réelle sur le code livré

**Decision** : l'évaluation reste des tests JUnit taggés `eval`, mais devient un `@QuarkusTest` avec
un profil `eval` (`EvalProfile`).
- **Profil** : rejeu désactivé, `specwriter.copilot.home` pointant sur `~/.copilot`, ou
  `-Deval.copilot-home`.
- **Beans** : les AI Services et les adaptateurs de port sont injectés. `Converse` et les cas
  d'usage restent construits à la main avec les corpus d'évaluation, comme aujourd'hui.
- **Empreinte** : `EvalFingerprint` suit le nouveau chemin (`fr/kawamind/infrastructure/llm`) et
  inclut `infrastructure/llm/ai`.
- **Base de référence** : `baseline.json` ne change pas de minimums. La métrique 010
  `conversationCitation` et la 013 `perimeterAnswerFocus` gardent la boucle agent.
- **Migration** : chaque famille d'opérations migrée (R9) est commitée seulement si l'évaluation
  réelle passe (`ci/check-eval.sh`). Chaque passage se fait avec l'accord du mainteneur (appels
  au vrai Copilot).

**Rationale** : l'évaluation mesure alors les garde-fous et plafonds tels qu'ils sont déclarés
dans `@RegisterAiService`, pas une copie.

**Alternatives** :
- **`AiServices.builder` de LangChain4j dans un JUnit simple** : réutilise les interfaces, mais
  ignore les attributs Quarkus (plafonds, garde-fous) ; il faudrait les recopier. Écartée.

## R8 — Sécurité de la session Copilot

**Decision** : chaque session SDK créée par l'adaptateur :
- `setAvailableTools(<outils de la requête>)`, vide par défaut (essais S1 et S1b) ;
- `setOnPermissionRequest(reject)` : tout est refusé, nos outils n'en demandent pas
  (`skipPermission`) ;
- `setEnableSkills(false)` et pas de `SkillProvider` : l'essai S4 montre que des skills intégrés
  sont chargés sinon ;
- `setSkipCustomInstructions(true)` et `setEnableConfigDiscovery(false)` : aucune consigne lue
  dans un dépôt ;
- `setWorkingDirectory(<dossier privé vide de l'application>)` : rien à lire même en cas de faille
  (essai S1b, `secret.txt`) ;
- consigne en système seulement, données et résultats d'outils dans le message utilisateur,
  balisés et échappés (FR-002, FR-009).

**Tests** :
- **Unitaires de l'adaptateur** : la `SessionConfig` produite contient bien ces réglages, pour
  chaque type de requête.
- **Outil inconnu** : `hallucinatedToolNameStrategy` renvoie une erreur sans exécution (FR-007).
- **Arguments invalides** : le garde-fou d'entrée les refuse.
- **Évaluation** : un cas d'injection (ticket piégé qui demande de lire un fichier) vérifie qu'aucun
  outil hors liste n'est demandé et que le secret n'apparaît pas. Il s'ajoute à la base de
  référence (`injectionResistance`, minimum 1,0).
- **Spike** : `CopilotToolsSpikeIT` est conservé (FR-016).

## R9 — Ordre de migration

**Decision** : chaque étape se termine par les gates et un commit.
1. **Renommage** `dev.specwriter` → `fr.kawamind` (R10). Commit purement mécanique, sans autre
   changement.
2. **Adaptateurs** `ChatModel` et `StreamingChatModel` étendus (outils, historique, sécurité),
   **TDD** sur des doubles du SDK. Plus le test d'essai des appels parallèles (R2).
3. **Rejeu** : `ReplayChatModel` étendu et `ReplayStreamingChatModel` (R6).
4. **`KnowledgeAi`** (3 opérations simples, structurées), puis évaluation.
5. **`QuestionsAi`**, puis évaluation (`questionRelevance`).
6. **`SpecificationAi`**, puis évaluation (structure, révision, changements).
7. **`ConversationAi`**, fixtures `converse-agent`, évaluation complète (citations, abstention,
   périmètre, discussion, injection).
8. **Nettoyage** : suppression du code maison restant (boucle de relance, `ConversationStream`,
   `Plan`/`Action` du port si plus utilisés), décisions remplacées marquées (FR-020), documentation.

**Rationale** : chaque étape est vérifiable seule, et l'agent, le plus risqué, vient quand le
socle est éprouvé.

## R10 — Renommage du package

**Decision** :
- **Déplacement** : `git mv` de `backend/src/{main,test}/java/dev/specwriter` vers
  `…/fr/kawamind`, puis remplacement de `dev.specwriter.` par `fr.kawamind.` dans :
  - les sources Java, `META-INF/services/*` et les règles ArchUnit ;
  - `McpCommand.MAIN` ;
  - `frontend/src/app/features/journal/journal.page.spec.ts` ;
  - `CONTRIBUTING.md`, `CLAUDE.md` ;
  - les chemins de `EvalFingerprint`.
- **groupId** `fr.kawamind` dans `pom.xml`, `backend/pom.xml`, `frontend/pom.xml`, et le
  `groupId` Maven de `jreleaser.yml`.
- **Inchangés** :
  - les clés `%dev.specwriter.*` de `application.properties` (profil `dev`) ;
  - `packageIdentifier: dev.specwriter.app` ;
  - les noms `spec-writer` (données, trousseau, base, exécutables) ;
  - les specs passées (historique).
- **Notes de version** : la section « Mise à jour » du README et la description de release
  signalent la nouvelle commande MCP (FR-019a).
- **Contrôle** : un test ArchUnit vérifie que toutes les classes sont sous `fr.kawamind`. Un
  `grep` de contrôle est consigné dans le quickstart.
- **Piège** : l'IDE compile aussi dans `backend/target`. Supprimer `target/classes` et
  `target/test-classes` après le déplacement (CLAUDE.md).

**Rationale** : un commit mécanique isolé se relit par son seul `diff --stat`, et sépare le bruit
du renommage de la refonte.
