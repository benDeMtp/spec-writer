# Research : 017 tri de la vue Jira, synchronisation automatique, notifications

Aucune inconnue ne reste ouverte dans le contexte technique. Les décisions ci-dessous fixent les
choix de conception.

## R1. Vue Jira servie par l'index local

**Decision**: nouveau point d'accès `GET /api/projects/{projectId}/jira-issues`. Il lit les
tickets (`kind = TICKET`) de l'index du projet : clé, titre, statut, catégorie de statut et
`source_updated_at` de l'élément. Il les joint à gauche au lien ticket ↔ spécification
(`linked_jira_ticket`) pour l'état de synchronisation et la session liée.

Le tri, la direction et la pagination (50 lignes) se font en SQL :

- `updated` : `source_updated_at`, puis clé ;
- `status` : rang de la catégorie (`new`, `indeterminate`, `done`, inconnue en dernier), puis nom
  du statut, puis clé ;
- `sync` : rang `CONFLICT`, `LOCAL_AHEAD`, `IN_SYNC`, non lié, puis `source_updated_at`
  décroissant.

La réponse porte aussi la date de dernière synchronisation réussie de Jira (R2) pour l'en-tête de
la vue.

La vue garde le tri dans les paramètres d'URL (`?tri=…&ordre=…&page=…`). Le retour à la vue, le
rechargement et le partage du lien retrouvent donc le même tri, et un changement de tri ramène à la
page 1 (FR-004). Les en-têtes sont des boutons dans les `th`, avec `aria-sort` sur la colonne
active et une annonce polie du nouveau tri (FR-006).

L'ancien point d'accès `GET /api/jira/projects/{key}/issues` (lecture de Jira en direct) n'a qu'un
appelant, cette vue. Il est retiré avec son cas d'usage et son test, après vérification qu'aucun
outil MCP ne s'en sert.

**Rationale**: le tri doit porter sur toute la liste (FR-003). Or Jira ne sait pas trier par état
de synchro, qui n'existe que dans l'outil, et la lecture paginée en direct ne trierait qu'une page.
L'index contient déjà ces champs, et les tickets fermés y restent (003).

**Alternatives considered**:
- tri côté client d'une page lue dans Jira : faux au-delà d'une page ;
- tout charger dans le navigateur : inutile à 2 000 tickets alors que SQLite trie en quelques ms.

## R2. Délai et bilan par source : table `source_sync`

**Decision**: migration V28 avec une table `source_sync`, clé primaire
`(project_id, source_key)`. `source_key` reprend l'identifiant de `IndexableSource` : `jira`,
`architecture` ou l'identifiant de la source de documentation. Ses colonnes :

- le délai (`interval_minutes`, nul = désactivé) ;
- le dernier bilan : date, résultat `CHANGED | UNCHANGED | ERROR`, décomptes, code d'erreur ;
- la date de la dernière réussite.

La ligne se crée au premier réglage ou au premier bilan. Elle s'efface avec le projet (clé
étrangère en cascade) et avec la source de documentation : suppression explicite dans le cas
d'usage de retrait, car `source_key` n'est pas une clé étrangère (valeurs `jira` et
`architecture`).

Le domaine porte `SyncInterval`, une énumération fermée (`OFF`, `M15`, `M30`, `H1`, `H4`, `D1`),
et `SyncSchedule.dueAt(lastRunAt)`, qui donne l'échéance ou rien. L'échéance part de la dernière
tentative, réussie ou non (FR-012, pas de relance immédiate). Une source jamais synchronisée est
échue dès qu'un délai lui est donné.

**Rationale**: une table dédiée garde l'état d'ordonnancement hors des tables de sources
(`documentation_source`, réglages Jira, modèle d'architecture), qui n'ont pas la même forme. Elle
traite aussi les trois sortes de source de la même façon.

**Alternatives considered**:
- colonnes dans chaque table de source : trois schémas, rien pour Jira et l'architecture qui n'ont
  pas de ligne propre ;
- dériver la dernière synchro de `indexed_at` : pas de résultat, pas d'erreur, pas de décompte.

## R3. Décompte des changements par source

**Decision**: `SynchronizeKnowledge` remonte pour chaque source relue un
`SourceChange(added, modified, removed)`, calculé là où l'index est écrit :

- **documentation** (Antora, dossiers, Confluence) : une page nouvelle compte en `added`, une page
  dont l'empreinte de contenu ou le numéro de version change en `modified`, une page absente de la
  nouvelle liste et retirée de l'index en `removed`. Ce sont les compteurs déjà disponibles aux
  appels `retain` et `replaceSource`.
- **Jira** : `TicketSummary` s'enrichit de `added` et `modified`, à côté de `read`, `keptClosed`
  et `removed`. Un ticket relu dont `source_updated_at` est inchangé n'est pas compté.
- **architecture** : éléments ou vues ajoutés, modifiés et retirés, par l'empreinte de chaque
  élément.

Ce bilan passe par `IndexationProgress.sourceDone(sourceKey, change)` et s'enregistre dans
`source_sync` (R2), que l'indexation soit automatique ou manuelle (FR-013). Une source en échec
enregistre `ERROR` avec son code, et garde sa dernière réussite.

**Rationale**: le décompte exact n'est connu qu'au moment de l'écriture dans l'index. Le refaire
après coup par différence d'instantanés coûterait une lecture complète de l'index.

**Alternatives considered**: comparer les nombres d'éléments avant et après. Cette méthode ne voit
pas les modifications, et un ajout plus un retrait s'annulent.

## R4. Ordonnanceur : extension `quarkus-scheduler`

**Decision**: ajouter l'extension `io.quarkus:quarkus-scheduler` (version du BOM Quarkus, pas de
version propre), puis redémarrer le dev mode après l'ajout.

- **`SourceSyncJob`** (`infrastructure/scheduling`) porte une seule méthode :

  ```java
  @Scheduled(identity = "source-sync",
             every = "${specwriter.knowledge.auto-sync.every:30s}",
             delayed = "${specwriter.knowledge.auto-sync.delayed:10s}",
             concurrentExecution = SKIP)
  @RunOnVirtualThread
  void tick() { autoSync.tick(); }
  ```

  - `delayed` lance le premier passage peu après le démarrage, pour rattraper les échéances
    dépassées (FR-011) sans retarder le démarrage.
  - `SKIP` empêche deux passages simultanés.
  - `@RunOnVirtualThread` suit la règle des tâches bloquantes du projet.
  - Une méthode `@Scheduled` a un contexte de requête actif ; les lectures Hibernate passent par
    des méthodes `@Transactional`, comme `BackgroundReadsIT` l'exige déjà.
- **`AutoSync.tick()`** (application, sans dépendance au framework) :
  - lit l'heure par `ClockPort`, puis les sources échues de tous les projets, de l'échéance la plus
    ancienne à la plus récente ;
  - si une indexation tourne déjà sur le projet, ne fait rien : l'échéance reste due et repassera
    au tick suivant, ce qui exclut toute double mise en file (FR-010) ;
  - sinon, lance `Indexations.start(projectId, INCREMENTAL, List.of(key))` pour la première source
    échue ;
  - un seul lancement par tick et par projet.
- Une source dont le prérequis manque (Jira non configuré, jeton absent, accord refusé) est
  consignée en `ERROR` avec le code de la cause, sans appel. Elle repasse au délai suivant.
- **Tests** :
  - `%test.quarkus.scheduler.enabled=false` : aucun passage dans les `@QuarkusTest`.
  - `AutoSync` se teste en unitaire avec une horloge simulée.
  - Un IT unique, sous un `QuarkusTestProfile` qui réactive le scheduler, vérifie le câblage :
    `every=1s`, puis attente du bilan. Il est appelé par l'API `Scheduler` injectée, ou par le
    déclenchement manuel de la Dev UI, plutôt qu'en attendant.
- **E2E** : le profil `%e2e` (déjà utilisé par le jar des E2E) fixe `auto-sync.every=2s`,
  `auto-sync.delayed=1s` et `auto-sync.minute=1s`.
- En dev mode, l'outil Dev MCP de l'extension (liste, pause et déclenchement des tâches
  planifiées) sert à déclencher un passage sans attendre.

**Rationale**: la demande est de rester dans le framework. L'extension fournit le déclencheur, le
non-chevauchement, le démarrage différé, la configuration par propriété et l'outillage de dev.
L'horloge métier reste dans `ClockPort` : le tick n'est qu'un réveil, et la décision « échue ou
non » se prend dans le cas d'usage, qui est donc testable.

**Alternatives considered**:
- un `ScheduledExecutorService` maison : du code d'infrastructure que l'extension fournit déjà ;
- une tâche programmatique par source (`Scheduler.newJob`) : beaucoup de tâches à réarmer à chaque
  réglage, et des échéances perdues au redémarrage ;
- l'extension Quartz : persistance et cluster inutiles pour un outil local à un seul processus.

## R5. Événements de l'application : événements CDI et flux SSE de Quarkus REST

**Decision**:
- Le port de domaine `AppEvents.publish(SourceUpdated)` est appelé par `Indexations` à chaque
  bilan `CHANGED`. Les bilans `UNCHANGED` et `ERROR` ne publient rien (FR-015).
- L'adaptateur `CdiAppEvents` émet l'événement CDI `Event<SourceUpdated>`. L'émission se fait
  après la validation de la transaction, par `@Observes(during = TransactionPhase.AFTER_SUCCESS)`
  côté observateur.
- `EventsResource` observe cet événement. Il garde les 50 derniers événements, puis les pousse dans
  un `BroadcastProcessor` de Mutiny (diffusion à plusieurs abonnés).
- `GET /api/events` renvoie un `Multi<OutboundSseEvent>`, construit avec le `Sse` injecté :
  `id`, `name("source-updated")`, `data` en JSON. Le point d'accès est déclaré avec
  `@Produces(SERVER_SENT_EVENTS)` et `@RestStreamElementType(APPLICATION_JSON)`.
- À la reconnexion, `@HeaderParam("Last-Event-ID")` fait renvoyer d'abord les événements manqués
  encore en mémoire (`Multi.createBy().concatenating()`).
- Un `Multi.createFrom().ticks()` toutes les 25 s émet un commentaire de maintien de connexion.
- Le point d'accès est protégé comme le reste de l'API.

L'événement porte :

- un identifiant unique ;
- le projet et son nom ;
- la source : clé, sorte et nom affiché ;
- les décomptes ;
- la date.

**Rationale**: tout reste dans le framework. Les événements CDI découplent l'indexation de la
diffusion. Le `Multi` SSE de Quarkus REST gère l'abonnement, la fin de connexion et la
contre-pression, là où le flux des tours écrit à la main dans un `SseEventSink`. Le flux arrive en
moins d'une seconde, bien sous les 10 s de SC-005, et `EventSource` se reconnecte seul.

**Alternatives considered**:
- `SseEventSink` et `SseBroadcaster` à la main, comme les tours : plus de code de cycle de vie ;
- Reactive Messaging en mémoire : une extension de plus pour un seul canal ;
- interrogation périodique : latence ou trafic ;
- WebSockets Next : bidirectionnel sans besoin.

## R6. Une seule notification pour plusieurs onglets

**Decision**: chaque onglet ouvre le flux. Seul l'onglet qui tient le verrou Web Locks
`spec-writer-notifications` affiche les notifications ; les autres attendent le verrou et prennent
le relais si cet onglet se ferme. La notification porte `tag` = identifiant de l'événement : même
en cas de course pendant le relais, le navigateur remplace au lieu de doubler (FR-017).

**Rationale**: Web Locks est disponible dans les navigateurs visés (Chrome, Edge, Firefox,
Safari 15.4+) et libère le verrou à la fermeture de l'onglet sans code de plus.

**Alternatives considered**:
- `BroadcastChannel` avec élection maison : plus de code pour le même résultat ;
- Service Worker et notifications push : hors périmètre (rien quand aucun onglet n'est ouvert, par
  choix de l'utilisateur).

## R7. Réglage et permission des notifications

**Decision**: réglage d'application `notifications.sourceUpdates` (booléen, faux par défaut) dans
les réglages de l'application (V28), exposé par `GET/PUT /api/settings/notifications`. La carte
« Notifications » de Configuration a un interrupteur. À l'activation, elle appelle
`Notification.requestPermission()` dans le geste de l'utilisateur :

- permission accordée : le réglage est enregistré ;
- permission refusée : il ne l'est pas, et un message dit comment l'accorder dans les réglages du
  site du navigateur (FR-016) ;
- permission déjà refusée : le message apparaît sans appel.

Le contenu de la notification :

- titre : « {Sorte} {nom} mise à jour » ;
- corps : « {projet} : n nouveaux, n modifiés, n retirés », en omettant les zéros ;
- `tag` : l'identifiant de l'événement.

Le clic appelle `window.focus()`, fait du projet de la source le projet courant s'il ne l'est pas,
puis navigue vers `/projet/connaissance` (FR-018).

**Rationale**: le réglage est porté par le serveur pour valoir dans tous les onglets et survivre
au navigateur. La permission reste celle du navigateur, la seule qui compte pour afficher.

## R8. Tests

**Decision**:
- **domaine** : unitaires pour `SyncInterval`, `SyncSchedule`, `SourceChange`, `IssueSort` (rangs
  de catégorie et de synchro) ;
- **application** : `AutoSync` avec horloge simulée, file occupée et prérequis manquants ;
  `ManageSourceSync` ; décomptes de `SynchronizeKnowledge` par source (faux index existants) ;
  `ListProjectIssues` ;
- **planification** : IT sous profil avec le scheduler réactivé (R4) ;
- **persistance** : `@QuarkusTest @Isolated` pour le tri SQL (2 000 tickets générés, temps
  mesuré) et pour la cascade de `source_sync` ;
- **web** : IT des points d'accès ; flux SSE avec `Last-Event-ID` ;
- **front** : Vitest pour la page Jira (tri, URL, `aria-sort`), la carte de notifications
  (permission accordée, refusée, déjà refusée) et le service d'événements (verrou, `tag`, clic) ;
- **E2E** `us17-synchro.spec.ts` :
  - jar lancé avec `auto-sync.every=2s` ;
  - Jira WireMock dont un ticket change entre deux réponses ;
  - minute de synchronisation réduite à une seconde dans le profil `%e2e`
    (`specwriter.knowledge.auto-sync.minute=1s`), donc un délai de 15 minutes échoit en 15 s,
    sans horloge simulée ni accès à la base ;
  - permission `notifications` accordée par le contexte Playwright ;
  - `window.Notification` espionné par `addInitScript` pour compter les notifications.

