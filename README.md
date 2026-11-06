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
- chercher à la fois dans vos spécifications et dans Jira, par pages de 25 ;
- ranger les spécifications par projet, chacun avec sa connaissance : tickets Jira, documentation
  Antora et modèle d'architecture Structurizr ;
- supprimer une version ou une spécification sans toucher à Jira ;
- suivre le statut d'implémentation, et donner aux agents de code (Claude Code, Copilot, Cursor)
  les features à implémenter.

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

## Projets et connaissance du projet

Chaque spécification appartient à un projet ; le projet courant se choisit en haut de l'écran.
La page **Contexte** d'un projet déclare ce que l'outil doit connaître :

- **Jira** : la clé du projet (sinon celle de la configuration). Les tickets sont copiés dans un
  index local, puis tenus à jour en tâche de fond, sans dépasser 8 requêtes par seconde.
- **Documentation Antora** : l'adresse du site généré, ou un dossier local de pages HTML. Les ADR
  sont reconnus, avec leur statut : pages `adr-<n>`, titres « ADR-<n> », ou pages numérotées d'un
  dossier `adr/`, `adrs/` ou `decisions/` (convention adr-tools, `0002-titre.html`).
- **Architecture** : le `workspace.dsl` Structurizr d'un dossier local (`workspace extends`,
  `!include`, `!element`, `!docs` et `!adrs` sont suivis ; un `!script` n'est jamais exécuté, il est
  ignoré avec un avertissement), et l'ownership, facultatif, qui distingue les modules propres au
  projet des modules communs. Sans ownership, ce qui vient du modèle étendu est commun.
- **Fiche générale** : un texte écrit à la main, qui l'emporte sur les sources.

Quand des ADR ont été trouvés, leur nombre est un lien vers la page **ADR du projet** : un ADR par
ligne, trié par numéro, avec son statut et son successeur. Les ADR remplacés ou obsolètes, qui ne
sont pas transmis à l'IA, sont grisés ; les statuts non lus et les numéros en double sont signalés.
Le titre ouvre la page de l'ADR dans un nouvel onglet ; pour une documentation en dossier local,
spec-writer sert lui-même la page, sans en exécuter les scripts et sans rien lire hors du dossier
déclaré. Un ADR retenu pour une génération est transmis en entier à l'IA (8 000 caractères au plus,
coupé à une section au-delà) ; un ADR cité par son numéro (« ADR 0017 », « l'ADR 17 ») dans le
besoin, une réponse ou l'instruction d'une modification est transmis d'office.

La recherche dans cet index se fait sur le poste, par mots et par sens. Le modèle de recherche
(multilingual-e5-small, environ 120 Mo) est téléchargé une seule fois, par le proxy d'entreprise,
à la première indexation. Il peut être remplacé par un autre modèle ONNX depuis
**Configuration › Modèle de recherche**.

Avant de poser ses questions, l'outil montre les tickets proches du besoin. Un ticket fermé peut
recevoir une nouvelle story liée (« relates to ») ; un ticket ouvert peut être comparé au besoin,
repris tel quel, ou laissé de côté. La spécification cite ensuite les ADR et leurs écarts, les
modules impactés, les flux manquants et les efforts de déploiement en production.

Les liens entre tickets sont lus avec eux : parent, sous-tâches et liens (« blocks »,
« relates to »…). Les sous-tâches de la story liée à une spécification ne sont jamais présentées
comme des travaux concurrents. La page d'une version les montre dans la rubrique « Sous-tâches de
<clé> » : chacune est rattachée au cas d'usage qu'elle semble implémenter (rattachement probable,
par le sens), et celles dont le cas d'usage a changé ou disparu sont signalées « à revoir », ou
« livrée, à reprendre » si elles sont fermées. Jira n'est jamais modifié. Après la mise à jour qui
apporte cette rubrique, la première synchronisation relit tous les tickets une fois, pour lire
leurs liens.

Un ticket fermé ne change plus : une fois indexé, il n'est plus relu dans Jira. « Tout réindexer »
ne relit que les tickets ouverts, ceux modifiés depuis la dernière synchronisation et ceux absents
de l'index ; un ticket rouvert est repris à la synchronisation suivante, et un ticket supprimé ou
déplacé est retiré. Les tickets proches et les sous-tâches fermés sont montrés avec leur statut
indexé, même quand Jira ne répond pas. La fin d'une indexation indique combien de tickets ont été
relus, gardés sans relecture et retirés.

« Réindexer des sources… » relit entièrement les seules sources cochées : Jira, une source de
documentation, le modèle d'architecture. La date « Synchronisée le » ne change pas, et une page
supprimée d'une source réindexée seule reste dans l'index jusqu'à la prochaine indexation de toutes
les sources. Une erreur inattendue dans une source n'arrête que son étape : les autres sources sont
indexées, et l'indexation se termine « avec des erreurs ».

## Statut d'implémentation

Une spécification est « brouillon », « prête à implémenter », « en cours » ou « implémentée ».
Une spécification implémentée est verrouillée : ni nouvelle version, ni envoi vers Jira, ni
suppression de version. Pour la faire évoluer, rétablissez un statut antérieur après
confirmation, ou **Créez une évolution** : une nouvelle spécification qui part de sa dernière
version et cite son origine.

## Agents de code (MCP)

Les agents de code lisent les features « prêtes » ou « en cours », leur contenu et la
connaissance du projet, et passent une feature à « en cours » puis à « implémentée ». Ils ne
peuvent rien faire d'autre : aucun accès aux jetons ni à la configuration, aucun appel à Jira ou
à Copilot.

1. Dans **Configuration › Accès des agents**, cliquez **Activer l'accès des agents**.
2. Copiez l'extrait affiché et ajoutez-le à la configuration MCP de l'agent. Il désigne le
   lanceur `spec-writer-mcp` installé avec l'application et ne contient aucun secret.

| Agent | Où déclarer le serveur |
|---|---|
| Claude Code | `.mcp.json` du dépôt, ou `claude mcp add spec-writer -- <commande>` |
| GitHub Copilot (VS Code) | `.vscode/mcp.json` : l'extrait sous la clé `servers` au lieu de `mcpServers` |
| Cursor | `~/.cursor/mcp.json` ou `.cursor/mcp.json` du dépôt |

```json
{ "mcpServers": { "spec-writer": { "command": "/Applications/spec-writer.app/Contents/MacOS/spec-writer-mcp" } } }
```

L'outil doit être ouvert pendant que l'agent travaille. La passerelle lit le secret dans le
fichier `agent-endpoint.json` du dossier de données, réservé à votre compte. **Régénérer le
secret** coupe les accès en cours, et désactiver l'accès supprime ce fichier. Chaque accès est
consigné, sans contenu, dans le journal de la carte.

## Vos données

| OS | Dossier de données |
|---|---|
| macOS | `~/Library/Application Support/spec-writer/` |
| Windows | `%LOCALAPPDATA%\spec-writer\` |
| Linux | `~/.local/share/spec-writer/` et `~/.config/spec-writer/` |

Le dossier contient la base des sessions, des spécifications et de l'index de connaissance
(`spec-writer.db`), le modèle de recherche, la configuration du client Copilot et, si l'accès des
agents est activé, `agent-endpoint.json`. Il est réservé à votre compte utilisateur.

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
