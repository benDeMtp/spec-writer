# spec-writer

spec-writer aide à passer d'un besoin écrit en quelques phrases à une spécification exploitable.
L'outil pose les questions qui manquent, une à une et avec leur justification, puis rédige les use
cases et leurs scénarios d'acceptation au format « En tant que / Étant donné / Quand / Alors ».
Chaque élément renvoie à ce qui l'a fait naître : un extrait du besoin, une réponse, ou une
hypothèse signalée comme telle.

L'outil tourne sur votre poste. Il s'ouvre dans votre navigateur, à l'adresse
`http://127.0.0.1:47615`, et ne répond qu'à cette machine, sur une conversation avec un agent qui
connaît le projet courant (voir [Converser avec l'agent](#converser-avec-lagent)). Il sait aussi :

- reprendre une session interrompue, revenir sur une réponse, comparer les versions ;
- exporter une version en Markdown ;
- créer un ticket Jira à partir d'une spécification, ou faire évoluer avec l'IA un ticket existant,
  puis renvoyer la mise à jour en signalant tout conflit ;
- chercher à la fois dans vos spécifications et dans Jira, par pages de 25 ;
- ranger les spécifications par projet, chacun avec sa connaissance : tickets Jira, documentation
  Antora, documents Word, Excel, PDF et HTML d'un dossier SharePoint synchronisé, et modèle
  d'architecture Structurizr ;
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

## Installer depuis les sources

Tant qu'aucun installeur n'est publié, l'outil se construit et se lance depuis le dépôt. Comptez
une quinzaine de minutes la première fois, téléchargements compris. Les commandes ci-dessous
valent pour macOS et Linux.

### 1. Prérequis

| Outil | Version | Vérifier |
|---|---|---|
| JDK | 25 ou plus récent (Temurin, Mandrel ou autre) | `java -version` affiche `25` ou plus |
| Git | toute version récente | `git --version` |
| Copilot CLI de GitHub | 1.0.105 ou plus récent (voir [Prérequis](#prérequis)) | `copilot --version` |

Node.js n'est pas à installer : la construction télécharge la version dont le front a besoin. Le
wrapper `./mvnw` télécharge de même Maven.

Si plusieurs JDK sont installés, désignez le 25 avant de construire et de lancer, par exemple
`export JAVA_HOME=$(/usr/libexec/java_home -v 25)` sur macOS, ou le chemin du JDK sous Linux.

### 2. Construire

```bash
git clone https://github.com/benDeMtp/spec-writer.git
cd spec-writer
./mvnw -B package -DskipTests -DskipDependencyCheck
```

La construction produit le front puis le backend, dans `backend/target/quarkus-app/`. La première
fois, elle télécharge les dépendances Maven, Node.js et les paquets du front.

### 3. Lancer

```bash
dist/scripts/spec-writer.sh
```

L'outil ouvre votre navigateur sur `http://127.0.0.1:47615/`, avec un jeton de lancement à usage
unique dans l'adresse. Au tout premier lancement, l'**assistant de premier lancement** prend le
relais (voir [Premier lancement](#premier-lancement)). Laissez le terminal ouvert : fermer le
terminal arrête l'outil.

### 4. Mettre à jour

```bash
git pull
./mvnw -B package -DskipTests -DskipDependencyCheck
dist/scripts/spec-writer.sh
```

Arrêtez l'outil avant de reconstruire : la construction remplace le jar en cours d'utilisation.
Vos données, vos réglages et vos jetons ne sont pas dans le dépôt (voir [Vos données](#vos-données)) :
ils sont gardés, et la base est mise à jour au démarrage.

### En cas de difficulté

- **« Java 25 ou plus est requis (trouvé : 21 …) »** au lancement, ou **« release version 25 not
  supported »** à la construction : le JDK utilisé est trop ancien. Installez un JDK 25 et
  désignez-le par `JAVA_HOME` (étape 1).
- **La construction échoue en téléchargeant Maven, Node.js ou des paquets** (délai dépassé,
  certificat refusé) derrière un proxy d'entreprise : Maven lit le proxy dans
  `~/.m2/settings.xml`, npm dans `HTTPS_PROXY`. Si le proxy inspecte le HTTPS, son certificat
  d'autorité doit être connu du JDK et de Node (`NODE_EXTRA_CA_CERTS`). L'outil lancé, lui, suit
  les réglages décrits dans [Proxy d'entreprise](#proxy-dentreprise).
- **« Picked up JAVA_TOOL_OPTIONS: -Dhttps.proxyHost=… »** suivi d'erreurs de certificat : une
  variable d'environnement impose un proxy à toutes les JVM du poste. Si ce proxy n'est pas
  nécessaire pour construire, lancez la commande avec `env -u JAVA_TOOL_OPTIONS ./mvnw …`.
- **« Jar absent »** : la construction n'a pas abouti. Relancez-la et lisez la première erreur.
- **Le navigateur ne s'ouvre pas** : relancez avec `dist/scripts/spec-writer.sh
  -Dspecwriter.print-launch-url=true` et ouvrez l'adresse affichée dans le terminal.
- **L'étape Copilot de l'assistant dit que le Copilot CLI est introuvable ou trop ancien** :
  installez-le ou mettez-le à jour (voir [Prérequis](#prérequis)), puis cliquez de nouveau sur
  **Se connecter à GitHub**. Les dossiers où l'outil le cherche sont listés dans
  [Où spec-writer cherche le Copilot CLI](#où-spec-writer-cherche-le-copilot-cli).
- **La connexion aboutit mais aucun modèle n'est proposé** : votre organisation GitHub n'autorise
  pas Copilot hors de l'IDE (voir [Prérequis](#prérequis)).

## Installation

Les installeurs seront publiés avec la première version : `.dmg` pour macOS (Apple Silicon et
Intel), `.msi` pour Windows, `.deb` et `.tar.gz` pour Linux. Ils embarquent leur propre runtime
Java : seul le Copilot CLI est à installer (voir les prérequis). D'ici là, voir
[Installer depuis les sources](#installer-depuis-les-sources).

Les installeurs ne sont pas encore signés. Le système vous avertira au premier lancement :

- **macOS (Gatekeeper)** : « spec-writer ne peut pas être ouvert car le développeur ne peut pas
  être vérifié ». Faites un clic droit sur l'application, choisissez **Ouvrir**, puis confirmez.
  Sur les versions récentes, passez par **Réglages Système › Confidentialité et sécurité ›
  Ouvrir quand même**.
- **Windows (SmartScreen)** : « Windows a protégé votre ordinateur ». Cliquez sur **Informations
  complémentaires**, puis **Exécuter quand même**.
- **Linux** : installez le paquet (`sudo apt install ./spec-writer_<version>_amd64.deb`), ou
  décompressez l'archive `.tar.gz` et lancez l'exécutable `spec-writer` qu'elle contient.

### Mise à jour

Installez la nouvelle version par-dessus l'ancienne : vos données, vos réglages et le jeton Jira du
trousseau sont gardés.

**Version qui renomme le code en `fr.kawamind` (feature 014)** : la commande de la passerelle MCP
change quand un agent la lance par `java -cp <jar> dev.specwriter.mcp.McpBridge` (lancement depuis
les sources). Cette ancienne commande ne démarre plus. Recopiez la commande affichée dans
**Configuration › Accès des agents** (elle lance `fr.kawamind.mcp.McpBridge`) dans la configuration
MCP de chaque agent. Le lanceur `spec-writer-mcp` des installeurs, lui, ne change pas.

## Premier lancement

Sur une installation neuve, l'outil s'ouvre sur l'**assistant de premier lancement**, qui mène
de la connexion à Copilot jusqu'à un projet indexé, en six étapes :

1. **GitHub Copilot**, seule étape obligatoire : **Se connecter à GitHub** ouvre une page
   d'autorisation GitHub, l'outil détecte seul la fin de la connexion. Choisissez le modèle, puis
   donnez votre accord à l'envoi de vos besoins et réponses à Copilot.
2. **Jira** (facultatif) : adresse du serveur et jeton, puis **Tester la connexion**. Si le
   serveur utilise une autorité de certification interne, indiquez le chemin de son certificat
   (format PEM). L'outil demande votre accord avant le premier envoi de données à Jira.
3. **Confluence** (facultatif) : adresse du serveur et jeton, puis **Tester la connexion**.
4. **Votre projet** : son nom et, si Jira est connecté, sa clé Jira.
5. **Documentation et architecture** (facultatif) : sites Antora, dossier SharePoint, racine
   Confluence, et le fichier `workspace.dsl` du modèle d'architecture.
6. **Indexation** : l'outil lit les sources du projet ; vous pouvez ouvrir la conversation sans
   attendre la fin.

Chaque étape facultative se passe d'un clic (**Passer**) et se reprend plus tard depuis
**Configuration** ou les **Réglages du projet**. Si vous fermez l'onglet ou arrêtez l'outil en
cours de route, l'assistant reprend à la même étape. Il se relance depuis **Configuration ›
Relancer l'assistant de premier lancement**. Une installation déjà utilisée avant cette version
ne le voit pas s'ouvrir de lui-même.

À la fin, l'assistant propose la **visite guidée**, qui se lance aussi à tout moment depuis le
menu (**Visite guidée**, bloc Application). Elle parcourt les écrans dans l'ordre où vous vous en
servirez : conversation, spécification et discussion, tâches suivies par les agents, tickets
Jira, connaissance et réglages du projet, réglages avancés et passerelle MCP. À chaque arrêt, une
bulle désigne un élément de l'écran et explique son rôle. **Précédent**, **Suivant** et
**Quitter la visite** (ou Échap) se pilotent au clavier ; la visite ne modifie rien et ramène à
l'écran d'où vous l'avez lancée.

Si l'outil est déjà lancé, rouvrez-le depuis son lanceur : l'adresse contient un jeton à usage
unique qui ouvre votre session. Une fenêtre qui n'est plus reliée à l'outil (après un redémarrage,
par exemple) n'affiche plus que « Relancez spec-writer depuis son raccourci ».

### Le menu

Le menu a deux blocs :

- **Projet**, titré par le choix du projet courant : Conversation, Spécifications, Tickets Jira
  (avec la clé Jira du projet), Connaissance du projet et Réglages du projet. Ces pages portent
  sur le projet courant et le nomment dans leur titre.
- **Application** : Projets, Configuration et Journal, qui valent pour tous les projets. Sous
  Configuration, l'état de Copilot et de Jira reste visible. **Visite guidée** relance la visite
  de l'outil.

Ouvrir une spécification, un questionnaire ou une conversation d'un autre projet (par un lien, la
recherche ou l'historique) rend ce projet courant, et l'outil l'annonce. Une proposition en
attente ou des réglages non enregistrés demandent confirmation avant un changement de projet.
Sur petit écran, le menu se replie derrière le bouton **Menu**.

La **Configuration** présente d'abord les **Connexions** (GitHub Copilot, Jira), puis les réglages
**Avancés** (modèle de recherche, accès des agents), avec leur portée : ils valent pour tous les
projets, et changer de modèle de recherche demande de réindexer chaque projet.

### Proxy d'entreprise

L'outil utilise le proxy indiqué à Java (`-Dhttps.proxyHost=… -Dhttps.proxyPort=…`) ou, à défaut,
les variables `HTTPS_PROXY` et `HTTP_PROXY` (identifiants acceptés :
`http://utilisateur:motdepasse@proxy:3128`). Les exclusions se déclarent dans `NO_PROXY`
(`.intra.exemple.fr,jira.exemple.fr`) ou `-Dhttp.nonProxyHosts`. Le proxy retenu est écrit dans le
journal au démarrage. Si le proxy inspecte le trafic HTTPS, déclarez son certificat d'autorité
dans la configuration Jira.

## Converser avec l'agent

L'outil s'ouvre sur la conversation du projet courant. Posez une question : l'agent fait jusqu'à 5
recherches dans la connaissance du projet (tickets, pages, ADR, documents, architecture) et dans la
dernière version de ses spécifications, chacune affichée comme une étape, puis rédige sa réponse au
fil de l'eau. Il cite ses sources par numéro, `[1]`, listées sous la réponse ; une spécification
citée s'ouvre sur sa version. Sans source, il dit qu'il ne sait pas. Les exemples de questions de
l'accueil sont tirés de la connaissance du projet (un ADR, un ticket ouvert, une page de
documentation) ; tant que rien n'est indexé, il n'y en a pas.

- **Recherche documentaire** : « Quels tickets parlent des avoirs ? ».
- **Incohérences** : « La documentation et les tickets sont-ils d'accord sur le délai de lettrage ? ».
  L'agent signale chaque écart avec les deux sources, et dit ce qu'il n'a pas pu comparer faute de
  place.
- **Documents** : mentionnez un document avec @, ou joignez-en un à la conversation ; ils passent en
  tête des sources.
- **Rédiger une spécification** : l'agent propose le besoin et les points que la discussion a
  établis. Corrigez-les ou retirez-en, puis démarrez : le questionnaire part de ces points comme de
  réponses déjà données, et ne les repose pas. Les documents joints suivent.

**Mémoire** : l'agent reçoit les échanges récents en entier, dans la place du modèle choisi. Quand
la conversation s'allonge, il résume les échanges les plus anciens (faits établis, décisions,
questions ouvertes) et garde ce résumé avec la conversation ; l'écran le montre pendant qu'il le
fait, puis un repère dans le fil indique les échanges transmis sous forme de résumé. Dans
l'historique, les appels `[n]` des réponses passées sont remplacés par le titre de leur source.

**Réflexion et durée** : pendant que l'agent travaille, un panneau replié dit ce qu'il fait (il
analyse la question, examine les résultats d'une recherche, résume les échanges anciens, rédige sa
réponse), avec un chronomètre. Déplié, il montre au fil de l'eau la réflexion que le modèle partage,
un résumé qu'il rédige lui-même, souvent en anglais ; elle reste avec la réponse (20 000 caractères
au plus), avec la durée du tour. Pour l'obtenir, l'outil demande au modèle une réflexion poussée
(`specwriter.copilot.reasoning-effort`, `high` par défaut ; Claude Sonnet 5.5 n'en partage aucune
en dessous) : une réponse prend quelques secondes de plus. Un modèle qui ne propose pas de réflexion
répond sans. Ailleurs (questionnaire, génération, proposition d'une version, Jira), un chronomètre
accompagne chaque attente de l'IA.

**Arrêter** (ou Échap) garde le texte déjà écrit, marqué incomplet ; **Réessayer** le relance. Les
conversations sont gardées par projet : la liste permet de les rouvrir, de les renommer et de les
supprimer (les spécifications qui en sont issues restent). La recherche et **Nouvelle
spécification** sont dans l'écran **Spécifications**.

Chaque message part vers GitHub Copilot, avec votre accord. Les messages restent dans la base de
l'outil et n'apparaissent jamais dans les journaux.

## Discuter d'une spécification

Sur une spécification, **Discuter avec l'IA** ouvre une discussion à côté du document (sous le
document sur un petit écran). L'agent reçoit la dernière version entière, en tête de ses sources,
découpée par section : il la cite par section (`[n]` mène à la section du document affiché), la
compare au reste du projet et signale les incohérences, avec les mêmes recherches, mentions @,
documents joints, mémoire et réflexion que la conversation. Si vous affichez une version plus
ancienne, le panneau le dit : la discussion porte toujours sur la dernière.

**Proposer une nouvelle version** ne demande rien : l'agent réécrit la spécification d'après ce
que la discussion a établi (ce que vous avez dit ou confirmé), garde le reste à l'identique, et
rattache chaque ajout au message qui l'établit, sinon il le marque comme hypothèse. Le chronomètre
et sa réflexion s'affichent pendant la rédaction. La proposition se relit à côté du document
(différences, **Appliquer**, **Abandonner**) ; appliquée, elle devient la version suivante,
« Issue de la discussion », avec un lien vers la conversation, et la discussion passe à cette
version (repère « Contexte : version N » dans le fil). Si la discussion n'a rien établi, l'agent le
dit au lieu de proposer.

La discussion est reprise à chaque retour sur la spécification ; **Nouvelle discussion** repart de
zéro, l'ancienne reste dans la liste des conversations, avec la mention « Spécification : <nom> ».
Une spécification implémentée ne se discute plus. Depuis un ticket Jira, **Discuter avec l'IA**
importe le ticket (ou ouvre sa spécification s'il est déjà importé) et ouvre la discussion. La
fusion d'un conflit avec Jira reste une proposition directe.

Supprimer une spécification qui a des discussions propose la case **Supprimer aussi ses
discussions**, cochée par défaut ; décochée, elles restent dans la liste, marquées « spécification
supprimée », et se poursuivent comme des conversations ordinaires.

## Faire le point avec les sachants

Une spécification s'écrit souvent avec des hypothèses, des écarts entre sources et des points
ouverts. **Faire le point** les tranche avec les sachants, en séance sur écran partagé.

**Préparer.** La page **Spécifications** montre sous chacune ses points à traiter (« 3 hypothèses ·
2 points ouverts · 1 écart entre sources ») ; la case **Avec des points à traiter** filtre la liste.
Cochez une ou plusieurs spécifications, puis **Préparer une séance** : l'ordre de passage, les
participants (ceux de la dernière séance sont proposés) et les points à écarter se règlent avant la
séance, qui attend en tête de la page avec **Démarrer**, **Modifier** et **Supprimer**. Sur une
spécification seule, **Faire le point** démarre directement.

**La séance** s'affiche en plein écran, sans menu ni notification. Elle parcourt la spécification
élément par élément, chaque point sur l'élément qu'il concerne (« UC-02, étape 4 »), ceux sans
élément à la fin. Au clavier : ↑ ↓ d'un élément à l'autre, **N** le prochain point à traiter,
**Entrée** pour y répondre (confirmé, à modifier, à retirer, reporté avec un responsable et une
échéance), **R** une remarque, **Q** une question. Tout est enregistré au fil de l'eau : une séance
interrompue reprend où elle en était. Rien n'est envoyé au modèle pendant la séance.

**Terminer** mène au récapitulatif : le compte rendu (décisions, points à vérifier, sources à
mettre à jour) se copie d'un clic, mis en forme pour un mail ou Teams. Les séances passées et
leur compte rendu restent sur la page de la spécification.

**Réviser.** **Proposer la nouvelle version** fait entrer les décisions dans la discussion de la
spécification, en un seul message, puis l'agent rédige la version suivante, relue par différences
comme toute proposition. Les points tranchés en disparaissent, les points reportés y deviennent des
**Points ouverts**, et la séance est citée comme source (« Séance du 20/11/2026 (Julie M., Paul
D.) »). La révision exige l'accord pour GitHub Copilot ; elle se relance tant qu'elle n'est pas
appliquée.

**Sources de vérité.** Une décision qui touche une page Confluence, une page de documentation, un
ticket ou un élément d'architecture cité laisse, une fois la révision appliquée, une entrée dans
**Sources à mettre à jour** sur la page de la spécification ; en séance, **Source à mettre à
jour…** en désigne une à la main dans la connaissance du projet. L'outil n'écrit jamais dans ces
sources : il signale « Source modifiée, à vérifier » quand la source change après la décision, et
**Marquer comme reporté** clôt l'entrée une fois la mise à jour faite.

## Projets et connaissance du projet

Chaque spécification appartient à un projet ; le projet courant se choisit en tête du menu. La
page **Projets** crée, supprime et choisit les projets. **Réglages du projet** regroupe, pour le
projet courant, son nom, sa description et tout ce que l'outil doit connaître :

- **Jira** : la clé du projet, sinon la clé par défaut de la configuration. Sans l'une ni l'autre,
  le projet n'a pas de tickets Jira, et l'outil mène à ce réglage. Les tickets sont copiés dans un
  index local, puis tenus à jour en tâche de fond, sans dépasser 8 requêtes par seconde.
- **Documentation Antora** : l'adresse du site généré, ou un dossier local de pages HTML. Les ADR
  sont reconnus, avec leur statut : pages `adr-<n>`, titres « ADR-<n> », ou pages numérotées d'un
  dossier `adr/`, `adrs/` ou `decisions/` (convention adr-tools, `0002-titre.html`).
- **Dossier de documents (SharePoint synchronisé)** : un dossier SharePoint synchronisé par
  OneDrive sur le poste (voir plus bas). Les fichiers Word (`.docx`), Excel (`.xlsx`), PDF et HTML
  sont indexés, sous-dossiers compris ; les anciens formats (`.doc`, `.xls`) et les autres sont
  ignorés et comptés. Des motifs d'exclusion, un par ligne et relatifs au dossier, écartent des
  fichiers : `*.xlsx`, `Archives/**`, `**/brouillon*`. « Aperçu du dossier » montre leur effet
  (documents retenus, exclus, ignorés) sans ouvrir aucun fichier.
- **Architecture** : le `workspace.dsl` Structurizr d'un dossier local (`workspace extends`,
  `!include`, `!element`, `!docs` et `!adrs` sont suivis ; un `!script` n'est jamais exécuté, il est
  ignoré avec un avertissement), et l'ownership, facultatif, qui distingue les modules propres au
  projet des modules communs. Sans ownership, ce qui vient du modèle étendu est commun.
- **Fiche générale** : un texte écrit à la main, qui l'emporte sur les sources.

**Connaissance du projet** montre ce que l'outil en a tiré : compteurs, indexation, réindexation
par source et recherche d'essai. Les anciennes adresses `/projets/<id>/contexte` mènent à cette page,
après avoir rendu le projet courant.

Quand des ADR ont été trouvés, leur nombre est un lien vers la page **ADR du projet** : un ADR par
ligne, trié par numéro, avec son statut et son successeur. Les ADR remplacés ou obsolètes, qui ne
sont pas transmis à l'IA, sont grisés ; les statuts non lus et les numéros en double sont signalés.
Le titre ouvre la page de l'ADR dans un nouvel onglet ; pour une documentation en dossier local,
spec-writer sert lui-même la page, sans en exécuter les scripts et sans rien lire hors du dossier
déclaré. Un ADR retenu pour une génération est transmis en entier à l'IA (8 000 caractères au plus,
coupé à une section au-delà) ; un ADR cité par son numéro (« ADR 0017 », « l'ADR 17 ») dans le
besoin, une réponse ou l'instruction d'une modification est transmis d'office.

### Mentionner un document avec @

Dans le besoin, une réponse ou l'instruction d'une modification, `@` suivi de quelques lettres
propose les documents du projet : documents du dossier synchronisé, pages de la documentation,
ADR. Les flèches choisissent, Entrée insère, Échap ferme. Une mention écrite à la main suffit
aussi : `@Aurora_Matrice_Regles_Gestion_Activites_1`, le nom du fichier avec ou sans extension,
son chemin, le titre d'une page, « @ADR-12 ». Le document mentionné est transmis à l'IA d'office,
avant ceux que trouve la recherche, après la fiche générale (4 documents et 12 000 caractères au
plus, coupé à une section au-delà de 8 000, avec un modèle de capacité inconnue).

Sous le champ, chaque mention montre ce qu'elle deviendra : transmise, transmise en partie, non
transmise faute de place, ambiguë, introuvable, ADR remplacé ou obsolète. Quand deux documents
portent le même nom, « Utiliser <chemin> » réécrit la mention avec le chemin du bon document.
Quand un document est coupé et qu'un autre modèle autorisé le transmettrait en entier, la mention
le nomme, le moins coûteux d'abord (« transmis en entier avec Claude Sonnet 5.5 (×1) ») ; le
modèle se change dans la configuration.

### Joindre un document

Un document reçu par mail ou téléchargé, hors du dossier synchronisé, se joint à une spécification :
**Joindre un document** sous le besoin, dans le questionnaire ou sur la page de la spécification.
Word (`.docx`), Excel (`.xlsx`), PDF et HTML, avec les mêmes limites que le dossier synchronisé
(20 Mo par fichier, 200 000 caractères extraits) ; 5 documents au plus par spécification. Un
fichier protégé par un mot de passe, un PDF scanné sans texte ou un ancien format (`.doc`, `.xls`)
est refusé avec la raison.

Le document joint est transmis à l'IA comme un document mentionné avec @ : après les mentions du
besoin, avant celles des réponses, 4 documents au plus en tout, dans la place du modèle choisi. Son
état s'affiche comme celui d'une mention (transmis, en partie, plus de place). Il ne sert qu'à
cette spécification et n'entre pas dans l'index du projet.

spec-writer ne garde ni le fichier ni son chemin : seulement le texte extrait, avec la session,
supprimé avec elle. Une régénération reste donc possible si le fichier est déplacé ou supprimé. Ce
texte ne sort pas de l'outil : la spécification, l'export, Jira et les agents de code ne voient que
le nom du fichier, cité comme « Document joint <nom> », sans extrait. Une feature implémentée est
verrouillée : rien ne s'y joint ni ne s'en retire.

### Choisir le modèle selon la taille de la connaissance

**Configuration › GitHub Copilot › Modèle utilisé** montre, pour chaque modèle, sa limite
(« environ 128 000 tokens (≈ 384 000 caractères) ») et son coefficient de facturation (« ×1 »),
tels que Copilot les annonce, ou « capacité inconnue » / « coût inconnu ». Sous le choix, la ligne
« Connaissance transmise : jusqu'à N caractères » donne la place de la connaissance du projet
avec ce modèle : 35 000 caractères pour un modèle de capacité inconnue ou petite, comme avant,
jusqu'à 200 000 pour un grand modèle. Toutes les parts (ADR, documents trouvés, documents
mentionnés, tickets, architecture) grandissent dans la même proportion ; les nombres de documents
restent les mêmes. Avec un modèle de 128 000 tokens, un document mentionné de 30 000 caractères
est transmis en entier. Les capacités sont relues à chaque connexion à GitHub : reconnectez-vous
si un modèle vient d'apparaître.

### Dossier SharePoint synchronisé

spec-writer ne se connecte pas à SharePoint : la lecture directe exigerait une application déclarée
par l'administrateur Microsoft 365. Il lit le dossier que OneDrive synchronise sur le poste :

1. Ouvrez le dossier partagé dans SharePoint, dans le navigateur.
2. Bibliothèque entière : bouton **Synchroniser**. Dossier partagé avec vous : **Ajouter un
   raccourci à Mes fichiers**, sur le dossier.
3. Le dossier apparaît dans le Finder, sous `~/Library/CloudStorage/OneDrive-<Organisation>/`
   (raccourci) ou `~/Library/CloudStorage/OneDrive-SharedLibraries-<Organisation>/` (bibliothèque).
   Déclarez ce chemin dans **Réglages du projet**, section Sources de documentation.
4. Facultatif : clic droit sur le dossier, **Toujours conserver sur cet appareil**, pour que les
   fichiers se lisent hors ligne.

Si le bouton Synchroniser n'apparaît pas, la synchronisation est désactivée par l'IT.

Les fichiers ne sont jamais copiés : l'index garde le texte découpé et ses vecteurs. Un fichier
inchangé (même taille, même date) n'est pas rouvert, ce qui évite à OneDrive de télécharger un
fichier « à la demande ». Un fichier protégé par mot de passe, un PDF scanné sans texte ou un
fichier non téléchargé hors ligne est signalé dans le bilan, sans arrêter les autres. Quand la
recherche retient un passage d'un document pour une génération, le document est relu entier dans
le dossier (2 documents, 8 000 caractères au plus, coupé à une section au-delà, après la fiche
générale et les ADR) ; s'il ne se lit plus, ses passages indexés sont transmis. Un ADR rangé dans
ce dossier (`adr-17.pdf`, `decisions/0017-titre.docx`, ou un titre « ADR-17 ») est reconnu comme
un ADR.

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
les sources ; pour un dossier de documents, les fichiers supprimés ou exclus sont retirés dès la
réindexation du dossier. Une erreur inattendue dans une source n'arrête que son étape : les autres sources sont
indexées, et l'indexation se termine « avec des erreurs ».

### Confluence

Une page Confluence Server ou Data Center (pas Confluence Cloud) et **toute sa descendance**, à
toute profondeur, peuvent servir de documentation à un projet. spec-writer lit Confluence et n'y
écrit jamais.

1. **Configuration › Confluence** : adresse du serveur (avec son chemin de contexte, par exemple
   `https://wiki.entreprise.fr/confluence`) et jeton d'accès personnel (Confluence : votre profil,
   Jetons d'accès personnels). Le jeton est gardé au trousseau, jamais renvoyé ni journalisé. Le
   proxy d'entreprise et le certificat de l'autorité interne s'appliquent comme pour Jira.
   **Tester la connexion** affiche votre compte.
2. **Réglages du projet › Sources de documentation**, type « Page Confluence et toute sa
   descendance » : collez l'adresse de la page depuis le navigateur (`…/pages/viewpage.action?
   pageId=…`, `…/spaces/CLE/pages/…` ou `…/display/CLE/Titre`). **Aperçu de la racine** donne son
   titre et son nombre de pages ; une racine de plus de 2 000 pages est refusée, choisissez une
   page plus basse.
3. L'indexation lit chaque page et la découpe en sections. **Resynchroniser** ne relit que les
   pages nouvelles ou modifiées (numéro de version) et retire celles sorties de l'arbre ; **Tout
   réindexer** relit tout. Une page restreinte pour votre jeton est comptée en erreur, sans bloquer
   le reste.

### Source, date et fiabilité des extraits

Chaque extrait transmis à l'IA porte sa source (Jira, Antora, SharePoint, Confluence, modèle
d'architecture, spécification, fiche générale, document joint), sa date de dernière mise à jour
(ou « date inconnue ») et le niveau de fiabilité de sa source. Les étapes de recherche de la
conversation et la recherche d'essai affichent la source et la date de chaque résultat.

Trois niveaux, réglés dans **Réglages du projet › Fiabilité des sources** :

- **Référence** : porte la demande d'origine, l'emporte en cas d'écart. Par défaut, les documents
  SharePoint (ils portent les demandes métier).
- **Interprétation** : compréhension de l'équipe ; suit la référence, l'écart est signalé. Par
  défaut, tout le reste, dont Confluence.
- **À confirmer** : pas encore validée ; l'IA dit quand elle s'y appuie.

Une racine Confluence peut avoir son propre niveau. Un changement vaut dès la recherche suivante,
sans réindexation. Quand un extrait de référence correspond à une recherche, deux places au plus
lui sont réservées dans les résultats, sans changer les scores.

En cas d'écart, la spécification suit la référence et le signale dans la rubrique **Écarts entre
sources**, avec les deux extraits, leur source et leur date ; la rubrique est reprise à l'export
et dans la description Jira. Dans la conversation, l'écart est présenté comme une incohérence.

### Synchronisation automatique et notifications

Chaque source d'un projet (une documentation, Jira, le modèle d'architecture) a son délai de
synchronisation automatique dans **Réglages du projet › Synchronisation des sources** : désactivée
(par défaut), 15 minutes, 30 minutes, 1 heure, 4 heures ou une fois par jour. Tant que spec-writer
tourne, une source dont le délai est écoulé est relue seule, de façon incrémentale, jamais en même
temps qu'une autre indexation du projet ; au démarrage, les échéances dépassées partent une à une.
Chaque source montre sa dernière synchronisation (nouveaux, modifiés, retirés, ou l'échec et la
dernière réussite) et la prochaine. Une synchronisation manuelle repart le délai.

Avec **Configuration › Notifications**, une notification du navigateur dit quelle source a été
mise à jour et combien d'éléments ont changé, jamais leur contenu. Le navigateur demande sa
permission à l'activation. Un seul onglet de spec-writer notifie ; rien si aucun onglet n'est
ouvert. Un clic mène à la connaissance du projet de la source.

La vue **Tickets Jira** lit l'index local : elle se trie sur toute la liste par date de
modification, statut ou état de synchronisation avec la spécification liée, sans appeler Jira, et
dit de quand date la dernière synchronisation de Jira (« Resynchroniser Jira » la relance).

## Sources du code et analyse de périmètre

Pour une évolution technique, l'agent peut recenser tous les modules du code qu'elle touche, sans
en oublier. Il ne lit pas le code au hasard d'une recherche : spec-writer parcourt toutes les
sources et liste chaque module qui contient un des termes. L'agent propose seulement ces termes, puis
qualifie les modules trouvés, sans pouvoir en retirer un.

1. **Déclarer les racines.** Dans **Réglages du projet › Sources du code**, ajoutez le dossier
   d'un arbre de sources (jusqu'à 10 par projet : application, pipelines, charts Helm…). Deux
   racines ne peuvent pas se contenir. **Détecter les modules** montre ce que l'outil reconnaît :
   un module par `pom.xml`, `package.json` ou `Chart.yaml`.
2. **Exclure.** Des motifs d'exclusion (`legacy/**`, `**/*.generated.java`) retirent des dossiers
   ou des fichiers. Le `.gitignore` est respecté, ainsi que les dossiers générés (`target/`,
   `node_modules/`…) et les dossiers cachés.
3. **Demander.** Dans la conversation : « Quels modules touche l'ajout d'un statut SUSPENDU au
   contrat ? ». L'étape « Analyse du périmètre » montre les termes cherchés, puis tous les modules
   trouvés, groupés en impactés, à vérifier, non impactés et non qualifiés, avec leurs raisons et
   leurs occurrences (`fichier:ligne`). Pour relancer avec d'autres termes, répondez par exemple
   « cherche aussi contract_suspended » : les modules nouveaux sont marqués.
4. **Rédiger.** Une spécification rédigée depuis la discussion reprend la dernière analyse dans
   sa rubrique « Modules touchés ». Les modules à vérifier y deviennent des hypothèses à valider.

Au-delà des modules qui contiennent un terme, l'analyse ajoute « à vérifier » :
- les modules Maven qui en dépendent, directement ou non (dépendance, parent, import de BOM), avec
  la chaîne de dépendance, et le pom qui les agrège ;
- les modules reliés par le modèle d'architecture (une seule relation, dans les deux sens). Un
  conteneur Structurizr se rattache à son module par son nom, ou par la propriété
  `spec-writer.module`, qui vaut le nom ou le chemin du module :

  ```text
  facturation = container "Facturation API" {
      properties {
          "spec-writer.module" "modules/facturation"
      }
  }
  ```

**Ce qui est lu et envoyé.** Le code n'est ni copié ni indexé : il est relu à chaque analyse, en
lecture seule, et aucune commande n'est lancée. Les fichiers sensibles (`.env`, clés,
`settings.xml`, keystores) ne sont jamais lus, ni les liens symboliques, ni les fichiers binaires
ou de plus de 1 Mo. GitHub Copilot reçoit les noms des modules et au plus quelques courts extraits
des lignes trouvées (200 caractères chacun). Le journal ne garde que des compteurs. Une analyse
s'arrête à 50 000 fichiers ou 120 secondes, et le dit.

## Statut d'implémentation

Une spécification est « brouillon », « prête à implémenter », « en cours » ou « implémentée ».
Une spécification implémentée est verrouillée : ni nouvelle version, ni envoi vers Jira, ni
suppression de version. Pour la faire évoluer, rétablissez un statut antérieur après
confirmation, ou **Créez une évolution** : une nouvelle spécification qui part de sa dernière
version et cite son origine.

Une spécification générée s'ouvre sur son énoncé de story (« En tant que… je veux… afin de… ») ;
une partie supposée est marquée « (hypothèse) ». Une story trop large est signalée, avec une
proposition de découpage en stories, sans être découpée d'office.

## Tâches et avancement

Sur une spécification envoyée vers Jira, le panneau **Tâches** propose un découpage :
**Proposer des tâches** demande au modèle 3 à 8 tâches, chacune avec un résultat vérifiable,
fondées sur les modules touchés quand la spécification en reprend, sinon sur les cas d'usage.
Renommez, fusionnez, retirez, réordonnez ou ajoutez, puis **Valider** crée une sous-tâche Jira par
tâche, sous la story liée. Les sous-tâches déjà présentes sous la story sont reprises.

L'avancement (« 3 tâches sur 5 terminées ») est relu dans Jira à l'ouverture de la
spécification, et le statut suit les tâches : « en cours » dès qu'une tâche est commencée,
« implémentée » quand toutes sont terminées. Repasser la spécification à « brouillon » ou « prête »
**suspend le suivi** ; **Reprendre le suivi** le rétablit. Jira injoignable : la transition est
enregistrée « à synchroniser » et envoyée par **Synchroniser** ou à la relecture suivante. Une
tâche tenue par un agent peut être **libérée**.

## Agents de code (MCP)

Les agents de code lisent les features « prêtes » ou « en cours », leur contenu, leurs tâches et
la connaissance du projet. Sur une feature découpée, ils suivent les tâches : `next_task` donne
la tâche à faire (choisie par spec-writer, jamais déduite de la branche), `start_task` la prend
pour la conversation en cours, `complete_task` la termine avec un compte rendu facultatif, ajouté
en commentaire de la sous-tâche. Sans tâches, ils passent la feature à « en cours » puis à
« implémentée ». Ils n'ont accès ni aux jetons ni à la configuration : c'est spec-writer qui
appelle Jira, avec votre compte, jamais la passerelle.

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

Le dossier contient la base des sessions, des conversations, des spécifications et de l'index de connaissance
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
