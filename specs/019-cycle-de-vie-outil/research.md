# Research : cycle de vie de l'outil installé

## R1. Exclusivité : verrou de fichier tenu par le processus

- **Decision** : au démarrage, le lanceur prend un verrou exclusif (`FileChannel.tryLock`) sur
  `instance.lock`, dans le dossier de données résolu (`UserDirectories.fromSystem()`, qui respecte
  `-Dspecwriter.data-dir`). Le verrou est tenu toute la vie du processus, par une référence
  statique. Il est pris dans une méthode `main` propre (`@QuarkusMain`, R3), avant `Quarkus.run`,
  donc avant la base, Flyway et le port HTTP.
- **Rationale** : le système d'exploitation libère un verrou de fichier à la mort du processus,
  même après un `kill -9` ou une coupure. Un verrou abandonné n'existe donc pas (FR-012) : le
  fichier reste, mais il n'est plus verrouillé. `tryLock` est atomique entre processus, ce qui
  règle les lancements simultanés (FR-014). Il fonctionne de la même façon sous macOS, Linux et
  Windows.
- **Cas particuliers** :
  - Dossier de données illisible ou en lecture seule : une `IOException` à l'ouverture n'est pas
    une instance en marche. Le démarrage continue et échoue comme aujourd'hui, avec un message qui
    nomme le dossier.
  - Un même processus qui reprendrait le verrou (`OverlappingFileLockException`) n'arrive pas
    hors du dev mode, voir R3.
- **Alternatives considered** :
  - Fichier PID avec vérification du processus : un PID peut être réutilisé, et la vérification
    diffère selon l'OS. Rejeté.
  - Port fixe comme verrou : le port se replie déjà sur un port libre (R14 de 001), et deux
    dossiers de données doivent cohabiter. Rejeté.
  - Verrou SQLite : il est pris trop tard, après le démarrage de Quarkus et le choix du port. Le
    mode WAL accepte aussi plusieurs connexions. Rejeté.

## R2. Canal entre deux lancements : socket Unix du dossier de données

- **Decision** : l'instance en marche écoute sur une socket Unix (`UnixDomainSocketAddress`,
  Java 16+, qui vaut aussi sous Windows 10 1803+) à `instance.sock`, dans le dossier de données.
  Un second lancement qui trouve le verrou pris s'y connecte et envoie une demande d'ouverture
  (contrat dans [contracts/instance-socket.md](contracts/instance-socket.md)). L'instance émet
  alors un nouveau jeton à usage unique (`LaunchTokenService.issue()`, qui existe depuis 013,
  T057) et ouvre le navigateur configuré (`ManageBrowser`). Si elle est réglée pour afficher
  l'adresse au lieu de l'ouvrir, elle renvoie l'adresse au second lancement, qui l'affiche
  (FR-010).
- **Rationale** :
  - **Accès** : le dossier de données est en `rwx------` (et, sous Windows, dans `LocalAppData`,
    privé à l'utilisateur). Seul le compte de l'utilisateur peut se connecter à la socket, et
    aucun secret n'est écrit sur le disque (FR-011, exigence « Secrets » de la constitution).
  - **Isolement** : une page web ne peut pas joindre une socket Unix. L'API HTTP et son filtre
    (`LocalSecurityFilter`) restent inchangés, sans exception de chemin.
  - **Cohérence** : le jeton circule seulement en mémoire, ou vers la console du second
    lancement quand l'utilisateur a choisi ce mode. Il n'est jamais journalisé.
- **Socket orpheline** : après un arrêt brutal, le fichier `instance.sock` reste. L'instance qui
  prend le verrou le supprime avant d'écouter. À l'arrêt propre, la socket est fermée et le
  fichier supprimé avant que le verrou soit rendu.
- **Longueur de chemin** : une socket Unix est limitée à 104 octets sous macOS et 108 sous Linux.
  Les dossiers par défaut (`~/Library/Application Support/spec-writer`,
  `~/.local/share/spec-writer`, `%LOCALAPPDATA%\spec-writer`) restent en dessous pour des noms de
  compte usuels. Si le chemin est trop long, l'instance démarre quand même, sans canal. Un second
  lancement trouve alors le verrou pris sans canal : il le dit et ne démarre pas, comme en R5.
  Ce cas est journalisé au démarrage.
- **Alternatives considered** :
  - Point d'entrée HTTP authentifié par un secret dans un fichier (comme `agent-endpoint.json`) :
    il écrit un secret sur le disque et ajoute une exception au filtre de sécurité. Rejeté au
    profit de la socket.
  - Signal du système (SIGUSR1) : n'existe pas sous Windows et ne transporte pas de réponse.
    Rejeté.

## R3. Point d'entrée : `@QuarkusMain` avec `main` statique

- **Decision** : une classe `fr.kawamind.infrastructure.platform.SpecWriterMain` annotée
  `@QuarkusMain`, avec un `main(String[])` statique qui :
  1. prend le verrou (R1) ;
  2. en cas d'échec, joue le rôle de second lancement (R2, R5), puis `System.exit` ;
  3. sinon appelle `Quarkus.run(args)`.

  Le canal (R2) est ouvert par un bean `@Startup` quand le verrou est tenu, c'est-à-dire quand le
  processus est passé par ce `main`.
- **Rationale** : le dev mode et `@QuarkusTest` ne passent pas par le `main` statique. Ils
  appellent directement l'application. Ils ne prennent donc ni verrou ni canal, ce qui évite les
  conflits au rechargement à chaud (FR-018). Le jar construit par `verify`, lancé par les E2E, et
  l'uber-jar des installeurs passent, eux, par ce `main`. Chaque E2E a son dossier de données,
  donc son verrou. `McpBridge` garde son propre `main` et n'est pas concerné (FR-016).
- **À vérifier en T001 (spike)** : que `quarkus.package.jar.type=uber-jar` (profil `dist`) et
  fast-jar appellent bien le `main` de la classe `@QuarkusMain`, et que `quarkus:dev` ne le fait
  pas. Le journal du spike tranchera.
- **Alternatives considered** : prendre le verrou dans `DataDirConfigSource`, appelée tôt. Mais
  elle est aussi appelée en dev mode et en test, et elle ne peut pas arrêter proprement le
  processus. Rejeté.

## R4. Arrêt propre : un seul chemin pour Quitter, ⌘Q, Ctrl+C et SIGTERM

- **Decision** :
  - **Travaux en cours** : un service applicatif `RunningWork` les liste à partir des registres
    existants. `Indexations` donne les indexations et synchronisations au statut `RUNNING`.
    `TurnEvents` donne les tours ouverts : réponse de l'agent (`Converse`) et proposition de
    version (`ProposeRevision`). Il faut lui ajouter l'énumération des tours ouverts et de leur
    nature. Les générations synchrones (`POST …/specifications` et générations de session) sont
    comptées par un filtre JAX-RS attaché à ces routes par une annotation `@LongWork`.
  - **Quitter** : `POST /api/application/quit` répond `202` puis planifie l'arrêt hors de la
    requête.
  - **Arrêt** : un observateur de `ShutdownEvent` (`GracefulStop`) est le chemin commun à
    Quitter, ⌘Q, Ctrl+C et SIGTERM. Il annule toutes les indexations
    (`Indexations.cancelAll()`) et tous les tours (`TurnEvents.cancelAll()`), puis attend qu'ils
    aient fini, au plus 5 s. Quitter déclenche l'arrêt par `Quarkus.asyncExit(0)` ; les autres
    passent par le crochet d'arrêt de la JVM.
  - **Délais** : `quarkus.shutdown.timeout=4s` borne la fin des requêtes HTTP en cours.
- **Rationale** :
  - `Converse` enregistre déjà le texte reçu en `INCOMPLETE` quand son tour est annulé (FR-003).
    `Indexations` sait déjà annuler une indexation entre deux sources.
  - Un seul chemin pour les quatre causes d'arrêt évite des comportements divergents.
  - 5 s d'attente des travaux et 4 s pour les requêtes HTTP tiennent dans les 10 s de FR-004.
  - Une génération synchrone est un appel en cours au modèle. Elle n'a pas de texte partiel à
    garder. Interrompue, elle n'enregistre rien, et l'utilisateur la relancera : la confirmation
    le dit.
- **Alternatives considered** : arrêter par `System.exit` dans la requête. La réponse ne
  partirait pas, et l'onglet ne saurait pas que l'arrêt a commencé. Rejeté.

## R5. Second lancement : attentes et messages

- **Decision** :
  - **Verrou pris** : le second lancement se connecte au canal.
  - **Pas de canal** (instance en démarrage ou en arrêt) : il retente le verrou puis le canal
    toutes les 250 ms, pendant 15 s au plus. Si le verrou se libère entre-temps, il devient
    l'instance (FR-014, « relance pendant l'arrêt »).
  - **Pas de réponse en 5 s** sur un canal ouvert : l'instance ne répond pas (FR-013).
  - **Messages** : toujours sur la sortie standard, en français. Il faut en plus un message
    visible sans terminal :
    - sous macOS et Windows, une boîte `JOptionPane`, puisque le runtime embarque déjà
      `java.desktop` ;
    - sous Linux, `zenity` ou `kdialog` s'ils sont présents, sinon `notify-send`, sinon la
      console seule. La 020 veut se passer des bibliothèques graphiques sous Linux.

    Aucune boîte ne s'affiche quand l'ouverture a réussi : l'onglet suffit.
  - **Codes de sortie** : `0` quand un onglet est ouvert ou l'adresse affichée, `3` quand
    l'instance ne répond pas.
- **Rationale** : les 15 s couvrent un démarrage normal (moins de 10 s) et l'arrêt borné de R4.
  Les 5 s viennent de la spécification.

## R6. macOS : Dock, ⌘Q et réouverture

- **Decision** : sous macOS, hors mode `java.awt.headless`, quand l'instance tient le verrou, un
  bean `MacDesktopIntegration` est démarré au lancement. Il initialise `Desktop`, ce qui fait
  apparaître l'icône dans le Dock même avant la première ouverture du navigateur, puis enregistre
  deux gestionnaires :
  - `Desktop.setQuitHandler` : s'il y a des travaux en cours (R4), une boîte `JOptionPane` les
    nomme et propose « Quitter quand même » ou « Annuler ». Elle répond ensuite
    `performQuit()` ou `cancelQuit()`. `performQuit` passe par le crochet d'arrêt, donc par R4.
  - `Desktop.addAppEventListener(AppReopenedListener)` : à chaque clic sur le Dock ou réouverture
    depuis le Finder ou le Launchpad, le Finder envoie l'événement `reopen` à l'application en
    marche. Le gestionnaire fait la même ouverture qu'une demande du canal (R2).
- **Rationale** : macOS ne relance pas une application déjà ouverte : il lui envoie l'événement
  Apple `reopen`, que Java expose par `AppReopenedListener` (`java.awt.desktop`, Java 9+). Sans
  gestionnaire de fin, ⌘Q appelle déjà `System.exit`, donc le crochet d'arrêt. Le gestionnaire
  ajoute seulement la confirmation.
- **Vérification** : rien de tout cela ne s'automatise en CI, car les exécuteurs macOS n'ont pas
  de session graphique interactive. Les tests unitaires couvrent la logique derrière les
  gestionnaires, avec une abstraction de `Desktop` comme `BrowserLauncher` en a déjà. Le reste se
  vérifie à la main sur le `.dmg` construit en local (quickstart Q5).
- **Alternatives considered** : un `Info.plist` avec `LSUIElement`, qui masque l'icône du Dock :
  il supprime le geste de réouverture. Rejeté.

## R7. Interface : Quitter et écran d'arrêt

- **Decision** :
  - **Menu** : un bouton « Quitter spec-writer » dans le bloc Application de `main-nav`.
  - **Confirmation** : au clic, l'interface lit `GET /api/application`, puis ouvre le
    `ConfirmDialogComponent` existant (Angular Material, focus géré, annoncé). Il porte la liste
    des travaux en cours et ce qu'ils deviendront.
  - **Arrêt** : après confirmation, `POST /api/application/quit`, puis un `LifecycleStore` passe
    à `stopped`. Le shell affiche alors un composant `sw-app-stopped` à la place du contenu,
    comme `sw-session-lost`. Il dit comment relancer selon le système détecté : le Dock ou le
    Launchpad sous macOS, le menu Démarrer sous Windows, le menu des applications sous Linux, et
    le script s'il a été lancé depuis les sources (`GET /api/application` indique le
    système et le mode d'installation).
  - **Autres onglets** : le flux `/api/events` (017) passe en erreur dès que le serveur s'arrête.
    `AppEventsService` prévient alors le `LifecycleStore`, qui vérifie par
    `GET /api/application` (`cache: no-store`). Un échec réseau fait passer l'onglet à `stopped`.
    `problem.interceptor` fait la même vérification sur une erreur de statut 0. Les deux voies
    couvrent « prochaine action » et « dans les 15 s » (FR-005).
  - **Session perdue** : l'écran de session perdue garde « Relancez spec-writer depuis son
    raccourci », complété par le Dock sous macOS (FR-019).
- **Rationale** : ces choix réutilisent la boîte de confirmation, le flux d'événements et la
  forme du shell, sans nouvelle dépendance.

## R8. Tests

- **Backend unitaire** :
  - `InstanceLock` et le second lancement : un processus enfant (`ProcessBuilder` sur la JVM
    courante) tient le verrou, car deux verrous dans la même JVM ne se testent pas.
  - Protocole du canal : serveur et client sur une socket dans un dossier temporaire.
  - `RunningWork` : registres simulés.
  - `GracefulStop` : annulation, puis attente bornée par une horloge simulée.
  - Logique des gestionnaires macOS : abstraction de `Desktop`.
- **`@QuarkusTest`** : `ApplicationResourceIT` vérifie le contrat de `GET /api/application`, et que
  `POST /quit` répond `202`. L'arrêt lui-même est remplacé par un `ExitPort` simulé en test.
- **E2E** (`us19-cycle-de-vie.spec.ts`), sur une seconde instance (`fresh-instance.ts` de 018,
  port 47697) :
  - Quitter : confirmation, écran d'arrêt, processus terminé avec le code 0, second onglet passé à
    l'écran d'arrêt.
  - Second lancement : même dossier de données, en mode « afficher l'adresse ». Il se termine en
    moins de 5 s avec le code 0 et affiche une adresse qui ouvre une session.
  - Arrêt brutal : `SIGKILL`, puis relance qui démarre.
  - Lancements simultanés : une seule instance.
  - Quitter pendant une réponse de l'agent, rejouée lentement : le texte est retrouvé, marqué
    incomplet.
- **Manuel** : le Dock de macOS et la boîte de dialogue sous Windows et Linux (quickstart).
