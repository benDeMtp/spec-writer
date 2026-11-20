# Implementation Plan: Cycle de vie de l'outil installé (lancer, rouvrir, quitter)

**Branch**: `feature/023-cycle-de-vie` | **Date**: 2026-11-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/019-cycle-de-vie-outil/spec.md`

## Summary

**Une seule instance par dossier de données.** Avant de démarrer Quarkus, un `main` propre
(`@QuarkusMain`) prend un verrou de fichier exclusif sur `instance.lock`, dans le dossier de
données. Le système libère ce verrou à la mort du processus, donc un arrêt brutal ne bloque jamais
le lancement suivant (R1, R3).

**Un second lancement rouvre l'instance en marche.** Il trouve le verrou pris et se connecte à la
socket Unix `instance.sock` du même dossier, que seul l'utilisateur peut joindre. Il demande à
l'instance d'ouvrir un onglet avec un nouveau jeton à usage unique, puis se termine (R2, R5).
Sous macOS, le clic sur le Dock et la réouverture depuis le Finder passent par
`AppReopenedListener` et font la même ouverture (R6).

**Quitter.** La commande « Quitter spec-writer » du menu lit les travaux en cours
(`GET /api/application`) et demande confirmation, puis appelle `POST /api/application/quit`. Un
seul chemin d'arrêt (`GracefulStop`, sur `ShutdownEvent`) sert à Quitter, ⌘Q, Ctrl+C et SIGTERM
(R4) :
1. il annule les indexations et les tours de conversation, avec les mécanismes existants qui
   gardent le texte en `INCOMPLETE` ;
2. il attend leur fin, 5 s au plus ;
3. il arrête le serveur.

**Écran d'arrêt.** L'onglet affiche « spec-writer est arrêté ». Les autres onglets le détectent
par la coupure du flux `/api/events` (R7).

Le dev mode et `@QuarkusTest` ne passent pas par ce `main` : ils ne prennent ni verrou ni canal.

## Technical Context

**Language/Version**: Java 25 (backend Quarkus 4), TypeScript (Angular 22)

**Primary Dependencies**: aucune nouvelle.
- **JDK** : `java.nio.channels.FileChannel`/`FileLock`, `UnixDomainSocketAddress` (Java 16+),
  `java.awt.Desktop` et `java.awt.desktop` (module `java.desktop`, déjà dans le runtime jlink).
- **Quarkus** : `@QuarkusMain`, `Quarkus.asyncExit`, `ShutdownEvent`,
  `quarkus.shutdown.timeout`.
- **Front** : Angular Material `MatDialog` et `ConfirmDialogComponent`, CDK `LiveAnnouncer`.

**Storage**: aucune migration. Deux fichiers dans le dossier de données, `instance.lock` et
`instance.sock` ([data-model.md](data-model.md)).

**Testing**: JUnit 5 (processus enfant pour le verrou), `@QuarkusTest`, ArchUnit, Vitest,
Playwright sur le jar (seconde instance de 018, port 47697). Le Dock de macOS est vérifié à la
main (quickstart Q5). Pas d'évaluation LLM : aucun prompt touché.

**Target Platform**: poste de l'utilisateur, sous macOS (arm64, x64), Windows 10 1803+ et Linux,
avec les installeurs jpackage et le lancement depuis les sources (`spec-writer.sh`,
`spec-writer.cmd`).

**Project Type**: application web locale (backend + frontend), empaquetée en application de
bureau sans fenêtre.

**Performance Goals**:
- arrêt en 10 s au plus, travaux en cours compris (FR-004) ;
- nouvel onglet en 5 s au plus après un second lancement ou un clic sur le Dock (SC-002).

**Constraints**:
- aucun secret écrit sur le disque, et canal hors de portée d'une page web (FR-011) ;
- aucun changement pour le dev mode, les E2E ni la passerelle MCP (FR-016, FR-018) ;
- aucun ajout de bibliothèque graphique sous Linux (préparation de la 020).

**Scale/Scope**: une instance par dossier de données et par compte. Deux routes REST, un canal
local, un composant d'écran, une commande de menu.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | Les messages disent ce qui se passe et quoi faire : confirmation qui nomme les travaux et leur devenir, écran d'arrêt avec le geste de relance propre au système, second lancement qui explique. Confirmation au clavier, écran d'arrêt annoncé, WCAG 2.1 AA (contracts/ui.md). | OK |
| II. Scénarios structurés | Non concerné : génération et prompts inchangés. | N/A |
| III. Test-First | TDD sur `InstanceLock`, le canal, `SecondLaunch`, `RunningWork`, `GracefulStop` et la logique macOS derrière une abstraction de `Desktop`. Horloge simulée pour les attentes. Couverture des couches `application` et `infrastructure.platform` par des tests unitaires. | OK |
| IV. Bout en bout | `us19-cycle-de-vie` sur le jar : Quitter, second lancement, arrêt brutal, lancements simultanés, Quitter pendant une réponse. Le Dock n'est pas automatisable en CI : il est vérifié à la main (Q5), et c'est justifié en R6. | OK, avec une partie manuelle justifiée |
| V. Sécurité et confidentialité | Le canal n'est joignable que par le compte de l'utilisateur (socket dans un dossier `rwx------`), sans secret sur disque. Le jeton n'est jamais journalisé. `GET /api/application` ne renvoie que des noms de projet et de conversation, aux mêmes conditions que le reste de l'API. Le journal ne compte que les travaux interrompus. | OK |
| Exigences de sécurité (secrets) | Aucun fichier de secret ajouté : la socket remplace la solution « point d'entrée HTTP plus secret en fichier » (R2). | OK |
| Workflow et gates | Plan avec Constitution Check, gates `verify` et E2E avant chaque commit, README mis à jour (FR-020). | OK |
| Complexité | Aucune dépendance ni service ajouté. Le `@QuarkusMain` est le seul moyen d'agir avant la base et le port (R3). La socket Unix est l'alternative sans secret au point d'entrée HTTP (R2). | OK |

**Après la phase de conception** : vérification refaite, aucun écart. Le contrat du canal limite
la taille des messages (4 Kio) et le nombre de demandes par connexion (une), et rejette les
versions inconnues. Cela couvre la validation des entrées du principe V.

## Project Structure

### Documentation (this feature)

```text
specs/019-cycle-de-vie-outil/
├── plan.md
├── research.md          # R1–R8
├── data-model.md        # fichiers du dossier de données, états
├── quickstart.md        # Q1–Q7
├── contracts/
│   ├── rest-api.md      # GET /api/application, POST /api/application/quit
│   ├── instance-socket.md
│   └── ui.md            # menu, confirmation, écran d'arrêt
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/fr/kawamind/
├── application/lifecycle/
│   ├── RunningWork.java            # agrège Indexations, TurnEvents, LongWorkTracker
│   ├── LongWorkTracker.java        # générations synchrones en cours
│   ├── GracefulStop.java           # annule, attend (≤ 5 s), une seule fois
│   └── ExitPort.java               # port : arrêt du processus (Quarkus.asyncExit, simulé en test)
├── application/conversation/TurnEvents.java       # + nature du tour, liste des tours ouverts, cancelAll
├── application/knowledge/Indexations.java         # + running(), cancelAll()
├── infrastructure/platform/
│   ├── SpecWriterMain.java         # @QuarkusMain : verrou, second lancement, Quarkus.run
│   ├── InstanceLock.java           # FileChannel.tryLock sur instance.lock
│   ├── InstanceChannel.java        # serveur de la socket (bean @Startup, si verrou tenu)
│   ├── SecondLaunch.java           # client : attentes, réponses, codes de sortie
│   ├── LauncherMessages.java       # console + boîte (JOptionPane / zenity / kdialog / notify-send)
│   ├── TabOpener.java              # nouveau jeton + navigateur configuré, partagé canal et Dock
│   ├── MacDesktopIntegration.java  # setQuitHandler, AppReopenedListener (macOS seulement)
│   ├── QuarkusExit.java            # ExitPort → Quarkus.asyncExit
│   └── GracefulStopOnShutdown.java # @Observes ShutdownEvent → GracefulStop
├── infrastructure/web/
│   ├── ApplicationResource.java    # GET /api/application, POST /api/application/quit
│   ├── LongWork.java               # @NameBinding
│   └── LongWorkFilter.java         # compte les générations synchrones
└── infrastructure/config/UseCaseProducer.java   # câblage de RunningWork et GracefulStop

backend/src/main/resources/application.properties   # quarkus.shutdown.timeout=4s

frontend/src/app/
├── core/lifecycle/
│   ├── application.api.ts          # GET /api/application, POST /api/application/quit
│   └── lifecycle.store.ts          # running | stopped, sonde de vie
├── core/events/app-events.service.ts            # coupure du flux → sonde
├── core/http/problem.interceptor.ts             # statut 0 → sonde
├── features/shell/app-stopped.component.ts      # écran d'arrêt
├── features/shell/main-nav.component.ts         # « Quitter spec-writer »
├── features/shell/session-lost.component.ts     # consigne de relance (FR-019)
└── app.ts                                       # sw-app-stopped à la place du contenu

e2e/tests/us19-cycle-de-vie.spec.ts
e2e/tests/support/fresh-instance.ts              # + second lancement, arrêt brutal, code de sortie

README.md                                        # lancement, réouverture, arrêt (FR-020)
```

**Structure Decision**: la répartition hexagonale existante est gardée :
- **Couche `application`** : la logique d'arrêt et l'inventaire des travaux, sans dépendance au
  framework. L'arrêt du processus passe par le port `ExitPort`.
- **Couche `infrastructure.platform`** : tout ce qui touche au système (verrou, socket, Dock,
  boîtes de dialogue). ArchUnit y autorise déjà `java.awt` et l'horloge système.
- **Couche `infrastructure.web`** : les routes et le filtre.

## Complexity Tracking

Aucune violation de la constitution à justifier.
