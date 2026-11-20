# Quickstart : valider le cycle de vie de l'outil

Prérequis : JDK 25 en tête du `PATH`, jar construit par `./mvnw -B verify -DskipDependencyCheck`.
Chaque scénario utilise un dossier de données à lui (`-Dspecwriter.data-dir=$(mktemp -d)`).

## Q1. Quitter sans travail en cours (US1, SC-001)

1. Lancer `dist/scripts/spec-writer.sh` avec un dossier de données neuf.
2. Menu › Application › « Quitter spec-writer », puis « Quitter ».
3. Attendu :
   - l'onglet affiche « spec-writer est arrêté » avec la consigne pour les sources ;
   - le terminal rend la main avec le code 0 (`echo $?`) en moins de 10 s.

## Q2. Quitter pendant un travail (US1, FR-002, FR-003, SC-005)

1. Lancer l'outil (profil `e2e`, rejeu lent), lancer une indexation, puis poser une question à
   l'agent.
2. Pendant la réponse, choisir Quitter.
   - Attendu : la confirmation nomme l'indexation et la réponse, et dit ce qu'elles deviendront.
3. Choisir « Annuler ».
   - Attendu : l'outil continue.
4. Recommencer, puis choisir « Quitter quand même ».
5. Relancer l'outil.
   - Attendu : la réponse partielle est dans la conversation, marquée incomplète, et
     l'indexation est marquée interrompue.

## Q3. Un seul lancement, qui rouvre (US2, SC-002, SC-003)

1. Lancer l'outil et fermer l'onglet.
2. Relancer le script dans un second terminal, avec le même dossier de données.
3. Attendu :
   - « spec-writer est déjà lancé : un nouvel onglet a été ouvert » ;
   - le code de sortie est 0 en moins de 5 s ;
   - l'onglet ouvert est connecté ;
   - `ps` ne montre qu'une instance.
4. Avec `-Dspecwriter.open-browser=false -Dspecwriter.print-launch-url=true` : le second terminal
   affiche une adresse qui ouvre une session.
5. Lancer deux scripts en même temps sur un dossier neuf. Attendu : une seule instance.

## Q4. Arrêt brutal (US2, FR-012, SC-004)

1. Lancer l'outil, puis `kill -9` sur son processus.
2. Relancer. Attendu : l'outil démarre normalement, sans message sur le verrou.

## Résultats du 20/11

Q1, Q3 et Q4 ont été déroulés par `dist/scripts/spec-writer.sh` sur le jar, sans navigateur. Q3 :
retour en 1 s, code 0, une seule instance. Q4 : redémarrage normal après `kill -9`. Q1 : arrêt en
2 s, `instance.sock` supprimée. Q5 (Dock, sur un `.dmg` installé) reste à faire par le mainteneur.

## Q5. macOS installé : Dock et ⌘Q (US3, FR-006, FR-015) — manuel

1. Construire le `.dmg` (`./mvnw -Pdist package`), installer, lancer.
2. Fermer l'onglet, cliquer sur l'icône du Dock. Attendu : un onglet connecté en moins de 5 s.
3. Fermer l'onglet, rouvrir depuis le Launchpad. Attendu : même résultat.
4. Lancer une indexation, puis Dock › clic droit › Quitter.
   - Attendu : une boîte du système nomme l'indexation.
   - « Annuler » : l'outil continue.
   - « Quitter quand même » : l'outil s'arrête, et l'onglet passe à l'écran d'arrêt.
5. Relancer, puis ⌘Q sans travail en cours. Attendu : arrêt sans boîte.

## Q6. Instance qui ne répond pas (FR-013) — manuel

1. Lancer l'outil, puis le suspendre (`kill -STOP`).
2. Relancer.
3. Attendu :
   - message « spec-writer est déjà lancé mais ne répond pas », avec la façon de l'arrêter ;
   - une boîte de dialogue sous macOS et Windows, `zenity` ou une notification sous Linux ;
   - code de sortie 3.
4. `kill -CONT` pour reprendre.

## Q7. Dev mode et E2E non gênés (FR-018)

- Dev mode : lancé à côté de l'outil installé, il démarre. Un rechargement à chaud ne déclenche
  ni message ni verrou.
- E2E : la suite complète passe, y compris `us18-premiers-pas` (seconde instance sur sa propre
  base).

## Tests automatisés

- Backend :
  - `InstanceLockTest` (processus enfant), `InstanceChannelTest`, `SecondLaunchTest` (attentes,
    codes de sortie) ;
  - `RunningWorkTest`, `GracefulStopTest`, `MacDesktopIntegrationTest` (abstraction de `Desktop`) ;
  - `ApplicationResourceIT`.
- Front : specs de `LifecycleStore`, `sw-app-stopped`, de la commande Quitter de `main-nav`, et des
  réactions de `AppEventsService` et `problem.interceptor` à une coupure.
- E2E : `us19-cycle-de-vie.spec.ts`, qui couvre Q1, Q2 (indexation et réponse), Q3 (points 3 à 5)
  et Q4.
