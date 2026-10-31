# spec-writer

spec-writer aide à passer d'un besoin écrit en quelques phrases à une spécification exploitable.
L'outil pose les questions qui manquent, une à une et avec leur justification, puis rédige les use
cases et leurs scénarios d'acceptation au format « En tant que / Étant donné / Quand / Alors ».
Chaque élément renvoie à ce qui l'a fait naître : un extrait du besoin, une réponse, ou une
hypothèse signalée comme telle.

L'outil tourne sur votre poste. Il s'ouvre dans votre navigateur, à l'adresse
`http://127.0.0.1:47615`, et ne répond qu'à cette machine. Il sait aussi :

- reprendre une session interrompue, revenir sur une réponse, comparer les versions ;
- exporter une version en Markdown ;
- créer un ticket Jira à partir d'une spécification, ou faire évoluer avec l'IA un ticket existant,
  puis renvoyer la mise à jour en signalant tout conflit ;
- chercher à la fois dans vos spécifications et dans Jira.

## Prérequis

- **GitHub Copilot** : un abonnement Copilot actif. Si votre compte dépend d'une organisation,
  elle doit autoriser l'usage de Copilot hors de l'IDE (politique « Copilot CLI » dans les
  réglages Copilot de l'organisation). Sans cela, la connexion aboutit mais aucun modèle n'est
  proposé.
- **Copilot CLI de GitHub, version 1.0.105 ou plus récente**, installé sur le poste. spec-writer
  s'en sert pour dialoguer avec Copilot ; il ne l'embarque pas, car certains de ses composants ne
  peuvent pas être redistribués. Pour l'installer :
  - macOS ou Linux avec Homebrew : `brew install --cask copilot-cli` ;
  - avec Node.js, sur les trois OS : `npm install -g @github/copilot`.

  Pour vérifier la version : `copilot --version`. Pour la mettre à jour : `copilot update`, ou
  `brew upgrade --cask copilot-cli` si vous l'avez installé avec Homebrew.
- **Jira** (facultatif) : Jira Server ou Data Center 8.14 ou plus récent, un projet et un jeton
  d'accès personnel.

### Où spec-writer cherche le Copilot CLI

Dans l'ordre : la variable d'environnement `COPILOT_CLI_PATH` si elle est définie, puis le `PATH`,
puis les dossiers d'installation habituels (`~/.local/bin`, `~/.npm-global/bin`, `~/bin`,
`/opt/homebrew/bin`, `/usr/local/bin`, `/home/linuxbrew/.linuxbrew/bin` ; sous Windows,
`%LOCALAPPDATA%\Microsoft\WinGet\Links` et `%APPDATA%\npm`). Si le CLI est introuvable ou trop
ancien, la connexion à GitHub l'indique, avec la marche à suivre. Avec une installation par npm,
`node` doit se trouver dans le même dossier que `copilot`, ou dans le `PATH`.

## Installation

Les installeurs seront publiés avec la première version : `.dmg` pour macOS (Apple Silicon et
Intel), `.msi` pour Windows, `.deb` et `.tar.gz` pour Linux. Ils embarquent leur propre runtime
Java : seul le Copilot CLI est à installer (voir les prérequis). D'ici là, lancez l'outil depuis
les sources (voir [CONTRIBUTING.md](CONTRIBUTING.md)).

Les installeurs ne sont pas encore signés. Le système vous avertira au premier lancement :

- **macOS (Gatekeeper)** : « spec-writer ne peut pas être ouvert car le développeur ne peut pas
  être vérifié ». Faites un clic droit sur l'application, choisissez **Ouvrir**, puis confirmez.
  Sur les versions récentes, passez par **Réglages Système › Confidentialité et sécurité ›
  Ouvrir quand même**.
- **Windows (SmartScreen)** : « Windows a protégé votre ordinateur ». Cliquez sur **Informations
  complémentaires**, puis **Exécuter quand même**.
- **Linux** : installez le paquet (`sudo apt install ./spec-writer_<version>_amd64.deb`), ou
  décompressez l'archive `.tar.gz` et lancez l'exécutable `spec-writer` qu'elle contient.

## Premier lancement

1. L'outil ouvre la page **Configuration** dans votre navigateur.
2. **Se connecter à GitHub** : une page d'autorisation GitHub s'ouvre ; l'outil détecte seul la
   fin de la connexion, puis vous propose de choisir le modèle.
3. **Jira** (facultatif) : adresse du serveur, clé du projet par défaut et jeton, puis **Tester
   la connexion**. Si le serveur utilise une autorité de certification interne, indiquez le chemin
   de son certificat (format PEM).
4. Avant le premier envoi de données à Copilot ou à Jira, l'outil vous demande votre accord. Sans
   cet accord, rien ne part vers ce service.

Si l'outil est déjà lancé, rouvrez-le depuis son lanceur : l'adresse contient un jeton à usage
unique qui ouvre votre session.

### Proxy d'entreprise

L'outil utilise le proxy indiqué à Java (`-Dhttps.proxyHost=… -Dhttps.proxyPort=…`) ou, à défaut,
les variables `HTTPS_PROXY` et `HTTP_PROXY` (identifiants acceptés :
`http://utilisateur:motdepasse@proxy:3128`). Les exclusions se déclarent dans `NO_PROXY`
(`.intra.exemple.fr,jira.exemple.fr`) ou `-Dhttp.nonProxyHosts`. Le proxy retenu est écrit dans le
journal au démarrage. Si le proxy inspecte le trafic HTTPS, déclarez son certificat d'autorité
dans la configuration Jira.

## Vos données

| OS | Dossier de données |
|---|---|
| macOS | `~/Library/Application Support/spec-writer/` |
| Windows | `%LOCALAPPDATA%\spec-writer\` |
| Linux | `~/.local/share/spec-writer/` et `~/.config/spec-writer/` |

Le dossier contient la base des sessions et des spécifications (`spec-writer.db`) et la
configuration du client Copilot. Il est réservé à votre compte utilisateur.

Les jetons ne sont jamais écrits dans ce dossier ni dans les journaux : le jeton Jira est rangé
dans le trousseau du système (service `spec-writer`), le jeton GitHub dans l'entrée gérée par le
client Copilot. Vos spécifications, elles, sont en clair dans la base. **Activez le chiffrement
du disque** : FileVault sur macOS, BitLocker sur Windows, LUKS sur Linux.

## Désinstallation

1. Quittez l'outil.
2. Supprimez l'application : glissez-la dans la Corbeille sur macOS, passez par **Paramètres ›
   Applications** sur Windows, `sudo apt remove spec-writer` sur Linux.
3. Pour effacer aussi vos données, supprimez le dossier de données ci-dessus. Le Copilot CLI,
   installé à part, se désinstalle avec l'outil qui l'a installé (Homebrew ou npm).
4. Pour effacer les jetons, retirez depuis la page **Configuration**, avant la désinstallation,
   la connexion GitHub et la configuration Jira. Sinon, supprimez dans le trousseau l'entrée
   `spec-writer` et celle du client Copilot (Trousseaux d'accès sur macOS, Gestionnaire
   d'identification sur Windows, Secret Service sur Linux).

## Développer

Voir [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

spec-writer est distribué sous la [licence Apache 2.0](LICENSE). Les installeurs embarquent des
composants tiers soumis à leurs propres conditions : voir [NOTICE](NOTICE).
