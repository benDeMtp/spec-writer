# Research : Assistant local de rédaction de spécifications

**Date** : 2026-10-30 | **Plan** : [plan.md](plan.md) | **Spec** : [spec.md](spec.md)

État des versions relevé le 2026-10-30. Chaque décision indique sa justification et les
alternatives écartées. Les points marqués « à valider » sont des risques à lever par un spike au
début de l'implémentation (voir [Risques](#risques-et-spikes)).

## R1. Version de Quarkus

- **Decision** : Quarkus **4.0** (4.0.0.Beta2 au démarrage, puis CR1 le 2026-11-04 et Platform
  Final prévue le 2026-11-25). Montée de version vers la finale dès sa sortie.
- **Rationale** : choix de l'utilisateur. La finale sortira avant la fin du développement, ce qui
  évite une migration 3 → 4 jugée dense (Jackson 3, Hibernate ORM 8 / Jakarta Persistence 4,
  Vert.x 5, CDI 5).
- **Alternatives considered** : Quarkus 3.40 LTS. Stable tout de suite, avec Quinoa et
  quarkus-langchain4j 1.x, mais impose une migration ultérieure.
- **Conséquences** : Quinoa et quarkus-playwright ne supportent pas encore Quarkus 4 (Vert.x 5,
  retrait de `RunningDevService`). Ils sont remplacés (voir R6 et R11).

## R2. Version de Java

- **Decision** : Java **25 LTS**.
- **Rationale** : Quarkus 4 exige Java 21 au minimum. Java 25 est la LTS courante et permet
  `quarkus.package.jar.type=aot-jar` (cache AOT Leyden) pour un démarrage plus rapide sur le poste.
- **Alternatives considered** : Java 21, compatible mais sans le cache AOT de Leyden.

## R3. Accès au modèle de langage (GitHub Copilot)

- **Decision** : **GitHub Copilot SDK Java** officiel (`com.github:copilot-sdk-java`, public
  preview), encapsulé dans un adaptateur `ChatModel` LangChain4j écrit dans le projet, consommé
  par les AI Services de **quarkus-langchain4j 2.x** (port Quarkus 4).
- **Rationale** :
  - aucun provider Copilot n'existe dans LangChain4j ni dans quarkus-langchain4j ;
  - le SDK est la seule voie officielle. La consommation est facturée sur l'abonnement Copilot de
    l'utilisateur (AI Credits depuis 2026-06-01) ;
  - l'adaptateur conserve l'intérêt de quarkus-langchain4j : `@SystemMessage` et `@UserMessage`
    séparés (protection contre l'injection de prompt, principe V), sorties structurées, et
    remplacement par un `ChatModel` factice dans les tests (principe III).
- **Alternatives considered** :
  - `quarkus-langchain4j-openai` pointé sur `api.githubcopilot.com` : API interne obtenue par
    rétro-ingénierie, contraire aux CGU de GitHub, avec un risque de suspension de compte ;
    rejetée ;
  - GitHub Models : service retiré le 2026-07-30, et le provider LangChain4j a été supprimé ;
  - autre fournisseur de LLM : contredit FR-023.
- **Conditions** :
  - le SDK pilote le runtime Copilot CLI par JSON-RPC, et ce runtime est embarqué par le SDK.
    Le packaging doit l'inclure pour chaque OS (à valider, spike S1) ;
  - jetons acceptés : `gho_` (OAuth App), `ghu_` (GitHub App) et `github_pat_`. Les `ghp_` sont
    refusés.
- **Choix du modèle** : le modèle utilisé est **configurable**. Chaque organisation n'active
  qu'une partie des modèles Copilot : au 2026-10-30, l'organisation `bscc` autorise par exemple
  Claude Sonnet 5.5, Claude Haiku 5.5, GPT-6.1 Sol et Gemini 3.8 Flash, et désactive Claude
  Opus 5.5 et GPT-6 Sol.
  - Après la connexion, l'outil liste les modèles réellement disponibles pour le compte.
    L'opération de listage du SDK est à confirmer pendant le spike S1 ; à défaut, on garde une
    liste configurée et on teste par un appel.
  - Par défaut, l'outil retient le premier modèle disponible d'une liste de préférence
    configurable.
  - Si le modèle choisi devient indisponible (politique modifiée), l'outil renvoie l'erreur
    `MODEL_UNAVAILABLE` avec l'action « choisir un autre modèle ».
  - Si le quota d'AI credits est épuisé, l'outil renvoie l'erreur `QUOTA_EXHAUSTED`, avec un
    message qui indique la date de remise à zéro lorsqu'elle est connue.

## R4. Connexion GitHub (device flow)

**Résultat du spike S1 (2026-10-30, T010) : voie A retenue.**

- SDK : `com.github:copilot-sdk-java:1.0.28-preview.0`. Runtime natif fourni par
  `com.github:copilot-sdk-java-runtime` avec un classifier par OS (`darwin-arm64`, `darwin-x64`,
  `linux-x64`, `linux-arm64`, `win32-x64`…). Il est chargé en processus par JNA et extrait dans
  `~/.copilot/runtime-cache/`. Le SDK dépend de Jackson 2 (`com.fasterxml`), qui cohabite avec
  Jackson 3 de Quarkus 4.
- Connexion : `session.getRpc().accounts.login.begin(GITHUBDOTCOM)` renvoie une étape
  `open-url`. C'est un **OAuth authorization code avec PKCE et un retour sur un port local**
  (`http://127.0.0.1:<port>/callback`), avec l'application OAuth de la Copilot CLI. Ce n'est pas
  un code court. L'appel `advance(flowId)` attend la fin du flux et renvoie `completed` avec le
  login.
- Portées demandées par cette application, non modifiables : `read:user read:org repo gist
  codespace write:plugin_gateway_connections`. Elles sont plus larges que nécessaire, ce qui doit
  être affiché dans le consentement Copilot (FR-024).
- Stockage : le jeton est dans le **Trousseau macOS** (service `copilot-cli`). Le fichier
  `COPILOT_HOME/config.json` ne contient que `loggedInUsers` et `lastLoggedInUser`, sans aucun
  jeton (vérifié, FR-033a).
- Compte utilisé : sans connexion dans `COPILOT_HOME`, le runtime retombe sur la connexion de la
  CLI `gh` (ici, le compte personnel `benDeMtp`, Copilot Free). Une fois le compte EMU connecté
  dans un `COPILOT_HOME` dédié, il est prioritaire au redémarrage (`authType=user`). L'outil doit
  donc utiliser **son propre `COPILOT_HOME`** dans son dossier de données, et toujours afficher le
  login effectivement utilisé.
- Modèles : `client.listModels()` renvoie 13 modèles pour le compte `bscc`, avec leur
  `policy.state` (`enabled`), ce qui correspond à la politique de l'organisation.
- Quota : `client.getRpc().account.getQuota()` renvoie des `quotaSnapshots` (`chat`,
  `premium_interactions`…) avec `remainingPercentage` et `resetDate` (`QUOTA_EXHAUSTED`, R3).
- Sortie structurée : `MessageOptions.setResponseSchema(...)` fonctionne. Le schéma doit avoir
  `additionalProperties: false` à chaque niveau objet, sinon on obtient une erreur 400.
- Injection : avec `SystemMessageMode.REPLACE` et le contenu entre balises, une consigne
  injectée a été traitée comme une donnée (claude-sonnet-5.5).
- **Contenu persisté par le SDK** : chaque session écrit la conversation en clair dans
  `COPILOT_HOME/session-state/<id>/events.jsonl`. L'adaptateur doit appeler
  `client.deleteSession(id)` après chaque appel, et `COPILOT_HOME` doit hériter des droits
  `rwx------` du dossier de données (FR-022, FR-025).
- Outils : la session doit être créée avec `setAvailableTools(List.of())` et un
  `PermissionHandler` qui refuse tout. L'outil n'a besoin d'aucun outil du runtime.
- Démarrage du runtime : environ 3 s au premier lancement (extraction), puis de 60 à 120 ms.
  Temps de réponse observé : de 2,8 à 4,1 s.
- `CopilotClientOptions.setEnvironment` n'a pas d'effet en mode processus. On ne peut pas
  masquer la connexion `gh` par variables d'environnement ; c'est le `COPILOT_HOME` dédié qui
  résout le problème.

**Conséquence sur la spec** : FR-030a (code court) est remplacé par une autorisation dans le
navigateur, sans jeton à copier.

**Constat sur le compte du mainteneur (2026-10-30, lu sur `github.com/settings/copilot`)** :
- compte **Enterprise Managed User** (EMU), géré par l'entreprise ;
- Copilot Business attribué et géré par l'organisation `bscc` ;
- politique **« Copilot CLI : Enabled »** : l'usage de Copilot hors IDE via le CLI, donc via le
  SDK, est autorisé ;
- quota de 2 500 AI credits par cycle.

Conséquences :
- la voie A ci-dessous est la plus adaptée. Avec un compte EMU, l'enregistrement et
  l'autorisation d'une GitHub App tierce sont en général plus contraints (inférence non
  vérifiée) ;
- il reste à vérifier pendant le spike S1 que le SDK réutilise la connexion du CLI et que le CLI
  stocke son jeton dans le trousseau.

Le **compte utilisateur** (licence Copilot Business ou Enterprise, authentification SSO de
l'entreprise) est distinct de l'**identité de l'application** auprès de GitHub. Pour obtenir un
accès au nom de l'utilisateur, GitHub doit connaître l'application qui le demande. Deux voies
sont possibles, dans cet ordre de préférence. Le choix sera tranché par le spike S1.

- **Decision, voie A (préférée, à valider)** : **réutiliser la connexion du Copilot CLI
  officiel**, que le Copilot SDK pilote. Le bouton « Se connecter à GitHub » déclenche la
  connexion du CLI (device flow de GitHub, avec code court). spec-writer relaie le code et
  l'adresse de validation à l'écran, puis le SDK utilise l'utilisateur connecté.
  - Aucune application à enregistrer, aucun `client_id` à embarquer.
  - Le jeton est stocké par le CLI. Il faut vérifier qu'il l'est dans le trousseau du système et
    non dans un fichier (FR-033a). Dans ce cas, spec-writer ne manipule jamais le jeton GitHub.
  - Déconnexion (FR-030b) : la commande de déconnexion du CLI, à vérifier.
- **Decision, voie B (repli)** : device flow implémenté par spec-writer avec une **GitHub App**
  enregistrée pour le projet. Seul son `client_id` est embarqué ; il n'y a pas de
  `client_secret` dans ce flux. Device flow activé dans les réglages de l'app, permission
  « Copilot Requests », nom exact à confirmer. Les jetons expirent (accès 8 h, refresh 6 mois).
  - Déroulé :
    1. `POST https://github.com/login/device/code` renvoie `user_code`, `verification_uri`,
       `expires_in` (900 s) et `interval` ;
    2. polling sur `POST https://github.com/login/oauth/access_token`. Réponses à gérer :
       `authorization_pending` (continuer), `slow_down` (+5 s), `expired_token` (proposer un
       nouveau code), `access_denied`, `device_flow_disabled` ;
    3. le jeton d'accès et le refresh token sont stockés dans le trousseau par spec-writer (R8),
       puis passés au SDK via `setGitHubTokenProvider`. Le renouvellement est transparent.
  - Prérequis : le mainteneur enregistre la GitHub App, une fois.
- **Rationale** : les deux voies donnent le comportement demandé par FR-030a (code court, aucun
  jeton à copier). La voie A supprime une démarche d'enregistrement et toute manipulation de
  jeton GitHub par spec-writer.
- **Alternatives considered** :
  - réutiliser le `client_id` de VS Code ou de JetBrains : contraire aux CGU ;
  - OAuth App, avec jeton non expirant : moins sûre que la GitHub App ;
  - PAT collé à la main : rejeté par la clarification de la spec.
- **Contraintes propres aux comptes d'entreprise** (les deux voies) :
  - si l'organisation GitHub restreint les applications tierces, un administrateur devra
    approuver l'application une fois. Voie A : vérifier si le Copilot CLI est déjà autorisé.
    Voie B : approbation de la GitHub App ;
  - la politique Copilot de l'organisation doit autoriser l'usage de Copilot hors IDE (CLI ou
    SDK) ;
  - avec le SSO SAML, l'autorisation doit être validée pour l'organisation pendant la connexion.
    Un échec doit produire un message explicite (User Story 1, scénario 3).

## R5. Frontend

- **Decision** : **Angular 22.2** (standalone, signals, zoneless et OnPush par défaut), Node.js
  24 LTS, TypeScript 6.0. Composants **Angular Material 22 (Material 3) + CDK**. `@angular/aria`
  pour les widgets sur mesure. Rendu Markdown avec `ngx-markdown` 22. Le diff unifié est calculé
  par le backend (java-diff-utils) et affiché par un composant maison, `shared/ui/unified-diff`.
  `ngx-diff` 16, retenu d'abord, a été écarté à l'implémentation : il ne distingue ajouts et
  suppressions que par la couleur et n'expose aucun attribut ARIA (WCAG 1.4.1, FR-029).
- **Rationale** :
  - Material + CDK est la bibliothèque la plus solide pour WCAG 2.1 AA et le clavier (FR-029).
    Elle suit le cycle de versions d'Angular, et son theming M3 par tokens CSS permet une identité
    visuelle moderne (FR-027a) ;
  - le skill `frontend-design` fixera la direction visuelle et les tokens.
- **Alternatives considered** :
  - Spartan + Tailwind : esthétique shadcn, mais le code copié est à maintenir par nous ;
  - PrimeNG : accessibilité inégale selon les composants ;
  - Taiga UI.

## R6. Intégration du front dans le livrable

- **Decision** : le front est construit par `frontend-maven-plugin` dans un module Maven
  `frontend`. Le contenu de `dist/spec-writer/browser` est copié dans les ressources statiques
  (`META-INF/resources`) du backend. En production, un fallback SPA renvoie `index.html` pour les
  routes du front. En dev, `ng serve` utilise un proxy vers le backend.
- **Rationale** : Quinoa n'est pas compatible Quarkus 4 (issue #1071 ; la PR #1143 n'est pas
  fusionnée).
- **Alternatives considered** : Quinoa, à réévaluer quand une version compatible Quarkus 4 sortira.

## R7. Base de données embarquée

**Résultat du spike S2 (2026-10-30, T012) : SQLite retenu.** Avec Quarkus 4.0.0.Beta2 et
`hibernate-community-dialects` 8.0.0.Beta3 (géré par le BOM Quarkus), `db-kind=other` et le
driver `org.sqlite.JDBC`, on obtient : CRUD et JPQL avec `SQLiteDialect`, migration Flyway, FTS5
avec `unicode61 remove_diacritics 2` (« regle » trouve « Règle », « refuse » trouve « refusé »)
et le mode WAL. Le test est `BT/spike/SqliteHibernateSpikeIT.java`.

- **Decision** : **SQLite** (`org.xerial:sqlite-jdbc`) en mode fichier, avec Hibernate ORM et le
  dialecte `org.hibernate.community.dialect.SQLiteDialect` (`db-kind=other`). Migrations
  **Flyway**. Recherche plein texte par une table virtuelle **FTS5** créée en SQL natif dans les
  migrations et alimentée par des triggers. Exécution en mode JVM uniquement.
- **Rationale** :
  - un seul fichier, robuste ;
  - FTS5 est embarqué dans les binaires xerial pour les 3 OS, et la recherche native (FR-040) en a
    besoin ;
  - mode WAL contre la corruption en cas d'arrêt brutal (SC-006).
- **Alternatives considered** :
  - H2 en mode fichier : extension core de Quarkus, mais plein texte plus faible ; c'est le repli
    si S2 échoue ;
  - extension Quarkiverse `quarkus-jdbc-sqlite` : figée sur Quarkus 3.15 ;
  - `sqlite4j` : expérimental, base en mémoire ;
  - Derby : retiré de Quarkus 3.30.
- **À valider (spike S2)** : compatibilité du dialecte community avec Hibernate ORM 8.

## R8. Trousseau de clés du système

- **Decision** : `io.alapierre:credential-secure-storage` (fork maintenu de la bibliothèque
  Microsoft) : Keychain sur macOS, Credential Manager sur Windows, Secret Service sur Linux. Il est
  appelé derrière un port `SecretStore`, avec un timeout de 5 s par appel. **Aucun repli fichier.**
  Si le backend est absent ou verrouillé, le jeton reste en mémoire pour la session (FR-033b).
- **Rationale** :
  - la bibliothèque est maintenue ;
  - le timeout évite le blocage connu sous Linux quand la collection est verrouillée ;
  - le refus explicite de tout repli fichier applique FR-033a.
- **Alternatives considered** :
  - `java-keyring` : sans activité depuis 2023, et cassé par jlink sans `jdk.security.auth` ;
  - `secret-store` (FFM) : retombe silencieusement sur un fichier sous Linux, ce qui viole
    FR-033a.
- **Conséquence packaging** : le runtime jlink doit inclure les modules dont JNA a besoin.

## R9. Dossier de données utilisateur

- **Decision** : résolveur maison (quelques lignes, testé sur les 3 OS) :

  | OS | Données | Configuration |
  |---|---|---|
  | macOS | `~/Library/Application Support/spec-writer/` | même dossier, `config/` |
  | Windows | `%LOCALAPPDATA%\spec-writer\data\` | `%LOCALAPPDATA%\spec-writer\config\` |
  | Linux | `$XDG_DATA_HOME/spec-writer/` (défaut `~/.local/share`) | `$XDG_CONFIG_HOME/spec-writer/` (défaut `~/.config`) |

  Sous POSIX, les dossiers sont créés en `rwx------` et les fichiers en `rw-------` (FR-022).
  Sous Windows, les ACL du profil s'appliquent.
- **Rationale** : `%LOCALAPPDATA%` plutôt que le profil itinérant `%APPDATA%`, pour que les
  données confidentielles ne soient pas répliquées sur un serveur de profils.
- **Alternatives considered** : `dev.dirs:directories` (dépôt archivé, et lance PowerShell sous
  Windows).

## R10. Intégration Jira

- **Decision** :
  - périmètre : Jira Server / Data Center **≥ 8.14** (version on premise), API REST v2, appelée
    par le Quarkus REST Client ;
  - authentification : `Authorization: Bearer <PAT>` ;
  - test de connexion en 4 étapes, chacune avec sa cause d'erreur (FR-032) :
    1. `serverInfo` (serveur joignable et version) ;
    2. `myself` (jeton) ;
    3. `project/{key}` ;
    4. `mypermissions?projectKey=…&permissions=BROWSE_PROJECTS,CREATE_ISSUES,EDIT_ISSUES` ;
  - création de ticket : métadonnées par `createmeta/{project}/issuetypes[/{id}]` (l'ancien
    `?expand` a été supprimé en 9.0), puis `POST /issue` ;
  - mise à jour : `PUT /issue/{key}` avec `fields.description` seul ;
  - recherche : `/search` avec la requête JQL `project = X AND text ~ "…"`. Les caractères
    spéciaux Lucene sont retirés de la saisie (ils ne sont pas indexés), les guillemets sont
    échappés. Champs demandés : `summary,status,issuetype,updated`, avec pagination ;
  - format : Markdown converti en wiki markup Jira par `flexmark-jira-converter`. Les tableaux et
    le texte barré sont couverts par des tests ;
  - conflit (FR-048) : Jira n'a pas de verrou optimiste. On mémorise `updated` et un SHA-256 de la
    description lue, puis on relit le ticket juste avant le `PUT`. Toute différence déclenche le
    conflit. Une fenêtre de course minime reste possible ;
  - limitation de débit : en cas de 429, on attend la durée de `Retry-After`, plafonnée à 30 s,
    avec 3 tentatives au maximum. La gestion est manuelle, car `@Retry` ignore `Retry-After` ;
  - TLS : certificat d'autorité interne optionnel (chemin PEM), ajouté au registre TLS de Quarkus
    pour ce client. Jamais de `trust-all`. Une adresse `http://` déclenche l'avertissement de
    FR-035.
- **Risque** : fin de Jira Data Center le 2029-03-28. Le port `IssueTracker` permettra d'ajouter
  un adaptateur Jira Cloud plus tard.

## R11. Tests

- **Decision** :
  - backend :
    - JUnit 5 et `@QuarkusTest` avec RestAssured ;
    - WireMock (bibliothèque seule) pour Jira et GitHub, faute d'extension Quarkus 4 confirmée ;
    - ArchUnit pour les règles hexagonales ;
    - JaCoCo, avec un seuil de 80 % sur `domain` et `application` (principe III) ;
  - frontend : Vitest (lanceur par défaut d'Angular 22) avec couverture v8 ;
  - E2E : Playwright TypeScript avec `@axe-core/playwright` (tags `wcag2a`, `wcag2aa`,
    `wcag21aa`), sur le jar démarré via `webServer` dans un dossier de données temporaire. Le LLM
    est simulé par un profil `e2e` ;
  - golden set : des besoins de référence et leurs réponses dans `backend/src/test/resources/golden/`,
    rejoués avec un `ChatModel` enregistré ou simulé de façon déterministe (principes III et IV) ;
  - suite d'évaluation LLM : un profil Maven `eval`, lancé manuellement ou de façon planifiée avec
    un vrai compte Copilot. Elle mesure la pertinence des questions et la structure des scénarios.
    Un recul des scores bloque la fusion.
- **Alternatives considered** : quarkus-playwright (pas encore compatible Quarkus 4).

## R12. Déterminisme de la génération (FR-014)

- **Decision** :
  - température à 0 quand le fournisseur le permet. Le Copilot SDK ne l'expose pas (spike S1), donc les deux points suivants sont décisifs ;
  - sortie structurée en JSON validée par un schéma, puis rendue en Markdown par du code
    déterministe ;
  - identifiants des use cases et scénarios attribués par le code (`UC-01`, `SC-01-01`), pas par
    le LLM ;
  - une empreinte des entrées (besoin, réponses, version de l'outil) est enregistrée avec chaque
    version. À empreinte identique, l'outil réutilise la version existante au lieu de rappeler le
    LLM.
- **Rationale** : un LLM n'est pas déterministe même à température 0. C'est la combinaison
  « identifiants calculés + réutilisation par empreinte » qui garantit FR-014.

## R13. Contrôle de structure des scénarios

- **Decision** : validateur du domaine, sans dépendance externe. Il vérifie la présence et la
  non-vacuité des champs acteur, action et résultat, et le contexte facultatif. Il valide aussi
  les références : chaque scénario pointe vers un use case existant, chaque use case vers une
  source (FR-011, FR-012). Le format est décrit dans
  [contracts/scenario-format.md](contracts/scenario-format.md).

## R14. Sécurité HTTP locale

- **Decision** :
  - `quarkus.http.host=127.0.0.1` ;
  - validation du header Host active (comportement par défaut de Quarkus 4 en localhost), contre
    le DNS rebinding ;
  - CORS désactivé ;
  - un filtre maison rejette toute requête de l'API dont l'`Origin` n'est pas
    `http://127.0.0.1:<port>` ou `http://localhost:<port>` ;
  - un **jeton de lancement** aléatoire de 256 bits est généré à chaque démarrage et passé dans
    l'URL ouverte dans le navigateur. Il est échangé contre un cookie `HttpOnly; SameSite=Strict`,
    exigé sur toute requête `/api/**` ;
  - `Content-Type: application/json` est obligatoire pour les écritures ;
  - port par défaut 47615, configurable, avec repli sur un port libre s'il est occupé.
- **Rationale** : désactiver le CORS n'empêche pas un site tiers d'envoyer un POST « simple ». Le
  jeton et la vérification d'Origin ferment cette porte (principe V, OWASP A01 et A05).

## R15. Packaging et lancement

- **Decision** :
  - **JReleaser + jpackage** avec un runtime jlink embarqué : `.dmg` (macOS), `.msi` (Windows),
    `.deb` et archive `.tar.gz` (Linux), produits par une matrice CI de 3 runners ;
  - jar `aot-jar` (Java 25) ;
  - au démarrage : création du dossier de données, migrations Flyway, recherche du port, puis
    ouverture du navigateur avec le jeton de lancement (`Desktop.browse`, sinon `open`,
    `xdg-open` ou `rundll32`) ;
  - pour les développeurs, une archive zip contenant le jar et des scripts `spec-writer.sh` /
    `.cmd`, qui exige un JDK 25.
- **Rationale** : un seul double-clic, sans prérequis Java pour l'utilisateur final.
- **Différé** : signature et notarisation macOS (certificat « Developer ID », signature de la
  `.dylib` sqlite-jdbc et du runtime Copilot CLI), signature Authenticode sous Windows. Sans
  elles, Gatekeeper et SmartScreen affichent un avertissement : c'est documenté dans le guide
  utilisateur.
- **Alternatives considered** : image native, rejetée (JNA, sqlite natif, runtime Copilot CLI,
  coût de la CI) ; uber-jar seul, rejeté parce qu'il exige que l'utilisateur ait installé Java.

## R16. Qualité et sécurité en CI

- **Decision** :
  - OWASP Dependency-Check côté Maven et `npm audit --audit-level=high` côté front : les
    vulnérabilités hautes ou critiques bloquent la fusion ;
  - Semgrep (règles Java, TypeScript et OWASP) comme SAST ;
  - gitleaks contre les secrets versionnés ;
  - SpotBugs + PMD et ESLint (`angular-eslint`) ;
  - test SC-009 : après un parcours E2E complet, une recherche de motifs de jetons (`gho_`, `ghu_`,
    jeton Jira factice) dans le dossier de données, les journaux et les exports doit revenir vide.

## R17. Boucle de développement : dev mode Quarkus et serveur MCP Quarkus

Demande du mainteneur (2026-10-30) : développer avec le dev mode et le serveur MCP Quarkus, pour
que le rechargement à chaud évite les redémarrages. Constats d'un essai du dev mode sur le
projet (4.0.0.Beta2) :

- Dev MCP existe en Quarkus 4 sous la forme d'une extension de développement à part,
  `quarkus-devmcp`, ajoutée automatiquement en dev mode. Elle est servie sur `/q/dev-mcp` sans
  dépendance à déclarer dans le pom.
- Le serveur MCP `quarkus-agent-mcp` (1.0.10, via jbang) pilote le dev mode : démarrage, arrêt,
  journaux, outils Dev MCP de l'application, recherche dans la documentation Quarkus et skills par
  extension. Sa recherche documentaire démarre un conteneur pgvector par Docker.
- Le proxy local (`127.0.0.1:8118`), imposé à toute JVM par `JAVA_TOOL_OPTIONS`, présente un
  certificat que Java refuse : jbang ne télécharge plus son catalogue, et Maven ne pourrait plus
  résoudre les dépendances du dev mode. L'accès direct fonctionne.
- `StartupGuard` refuse GitHub simulé et le rejeu du LLM hors des profils `test` et `e2e` :
  le dev mode ne démarre pas avec eux.
- La validation du schéma par Hibernate après démarrage, propre au dev mode, échoue sur les
  tables virtuelles FTS5 (`NoSuchElementException` dans l'extraction des colonnes). L'erreur est
  journalisée sans bloquer le démarrage.
- Les tests lancés par Dev MCP tournent dans la JVM du dev mode, qui reçoit le proxy déclaré dans
  `~/.m2/settings.xml`. Les appels vers WireMock (`127.0.0.1`) y passent par ce proxy et échouent
  (« Internal Privoxy Error »), alors que surefire n'est pas concerné. Neutraliser ces propriétés
  dans le pom produit `-Dhttp.proxyHost=null` : abandonné. Les IT WireMock se lancent sous Maven.
- `specwriter.port` et `specwriter.data-dir` sont lues comme propriétés système de la JVM (avant
  la configuration Quarkus) : un réglage `%dev` dans `application.properties` est sans effet.
- Le proxy de `ng serve` (`changeOrigin: false`) garde l'en-tête `Host: localhost:4200` et
  l'`Origin` du serveur Angular : `LocalSecurityFilter` refuse alors toutes les requêtes (R14).
  Le parcours `npm start` de quickstart.md n'a donc jamais fonctionné.

- **Decision** :
  - **Serveur MCP** enregistré pour le projet (portée locale) avec `JAVA_HOME` en Java 25 et sans
    `JAVA_TOOL_OPTIONS` (`env -u JAVA_TOOL_OPTIONS jbang quarkus-agent-mcp@quarkusio`). Le dev
    mode qu'il lance hérite de cet environnement.
  - **Dev mode déterministe par défaut**, sans aucun compte ni crédit :
    - GitHub simulé, LLM rejoué depuis `e2e/fixtures/llm` et trousseau simulé, comme en E2E ;
    - dossier de données `.dev-data/` à la racine du dépôt, ignoré par git, pour ne jamais
      toucher aux données ni à la base de l'outil installé ;
    - port 47625, pour cohabiter avec l'outil installé sur 47615 ;
    - URL de lancement affichée dans le journal, pour obtenir le cookie de session.

    Port et dossier de données passent par la configuration `systemProperties` du goal `dev` de
    `quarkus-maven-plugin`.
  - **Profil `live`**, à cumuler avec `dev` (`-Dquarkus.profile=dev,live`, le dernier profil
    l'emporte) : vrai Copilot, vrai trousseau, aucun rejeu. Il sert aux essais manuels du
    mainteneur, avec ses propres comptes.
  - **`StartupGuard`** se fonde sur le mode de lancement de Quarkus (`LaunchMode.DEVELOPMENT` ou
    `TEST`, ou le profil `e2e`) plutôt que sur le seul nom de profil. Le jar installé tourne
    toujours en mode `NORMAL` : un simple `-Dquarkus.profile=dev` n'y active pas les simulations.
  - **Validation Hibernate en dev mode** désactivée (`%dev.quarkus.hibernate-orm.validate-in-dev-mode=false`).
    Le schéma reste garanti par Flyway et par les tests d'intégration.
  - **Front** : `ng serve` (rechargement à chaud natif) avec un proxy vers 47625. Le proxy récrit
    `Host` (`changeOrigin: true`) et `Origin` vers l'adresse du backend : le backend n'a aucune
    exception de sécurité pour le dev. Le cookie `sw_launch` (`SameSite=Strict`) reste la
    protection contre les requêtes venues d'autres sites.
  - **Dev MCP** activé en `%dev` (`quarkus.dev-mcp.enabled=true`) : il est désactivé par défaut,
    et `/q/dev-mcp` répond « Method Not Found » sans ce réglage.
  - **Sessions et port stables au rechargement**, deux défauts apparus à la vérification :
    - chaque rechargement recréait `LaunchTokenService`, ce qui déconnectait le navigateur. En dev
      mode seul, les cookies de session sont gardés dans `.dev-data/dev-sessions` (`rw-------`) ;
      le jeton de lancement n'y est jamais écrit ;
    - `PortSelector` voyait 47625 occupé par le serveur du dev mode lui-même, qui survit au
      rechargement, et choisissait un autre port pour l'URL affichée. En dev mode, le port
      configuré est gardé tel quel.
  - **Tests continus** en pause au démarrage (`%dev.quarkus.test.continuous-testing=paused`).
    Pendant le TDD, on les relance par les outils Dev MCP, sur les seules classes touchées.
  - **La gate de commit ne change pas** : `./mvnw verify` (JaCoCo, ArchUnit, SpotBugs, PMD,
    front), plus la suite E2E sur le jar empaqueté, jamais sur le dev mode.
- **Rationale** :
  - le rechargement à chaud évite le cycle « package puis redémarrage » (environ 1 minute) à
    chaque essai d'un endpoint ou d'un écran ;
  - les journaux, les exceptions structurées et les tests deviennent accessibles par Dev MCP sans
    relancer Maven ;
  - le mode déterministe évite toute consommation d'AI Credits et toute écriture dans le vrai
    trousseau ou dans la base de l'outil installé.
- **Alternatives considered** :
  - dev mode avec le vrai Copilot par défaut : il consomme des crédits et exige la connexion
    GitHub du mainteneur à chaque session de développement ;
  - données du dev mode dans `backend/target/` : effacées par chaque `mvn clean`, ce qui perd la
    configuration Jira de test ;
  - autoriser l'origine `localhost:4200` dans `LocalSecurityFilter` en dev : c'est une exception de
    sécurité dans le code livré, là où le proxy de développement suffit ;
  - tests continus actifs en permanence : chaque modification relancerait les 275 tests, dont
    les tests d'intégration WireMock, et ralentirait le rechargement.

## Risques et spikes

| # | Risque | Spike / mitigation |
|---|---|---|
| S1 | Copilot SDK Java en preview : changements d'API, runtime CLI à embarquer par OS, mode d'authentification (R4) | Spike au démarrage, sur un compte Copilot d'entreprise : (1) voie A, connexion via le Copilot CLI piloté par le SDK, relai du code court dans l'UI, emplacement du jeton stocké (trousseau ou fichier), déconnexion ; (2) si la voie A échoue, voie B avec un jeton `ghu_` d'une GitHub App de test ; (3) vérifier la politique de l'organisation (applications tierces, Copilot hors IDE, SSO) ; (4) packaging jpackage sur un OS. Adaptateur isolé derrière le port `LlmClient`. |
| S2 | Dialecte SQLite community avec Hibernate ORM 8 | Spike au démarrage : CRUD et FTS5 sous Quarkus 4 Beta. Repli : H2 fichier avec FT natif. |
| S3 | Quarkus 4 encore en Beta/CR | Montée vers la finale le 2026-11-25. Pas de dépendance Quarkiverse non portée. |
| S4 | quarkus-langchain4j 2.0 en Beta | Usage limité aux AI Services, à `@SystemMessage` et aux sorties structurées. |
| S5 | Jira DC en fin de vie (2029-03-28) | Port `IssueTracker`, adaptateur Jira Cloud possible plus tard. |
| S6 | Coût en AI Credits Copilot | Réutilisation par empreinte (R12), limite de taille du besoin (FR-001), nombre de questions borné. |
