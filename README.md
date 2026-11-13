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
3. **Jira** (facultatif) : adresse du serveur et jeton, puis **Tester la connexion**. La clé Jira
   par défaut est facultative : elle sert aux projets qui n'ont pas leur propre clé. Le test
   vérifie le projet Jira du projet courant et le nomme. Si le serveur utilise une autorité de
   certification interne, indiquez le chemin de son certificat (format PEM).
4. Avant le premier envoi de données à Copilot ou à Jira, l'outil vous demande votre accord. Sans
   cet accord, rien ne part vers ce service.

Si l'outil est déjà lancé, rouvrez-le depuis son lanceur : l'adresse contient un jeton à usage
unique qui ouvre votre session. Une fenêtre qui n'est plus reliée à l'outil (après un redémarrage,
par exemple) n'affiche plus que « Relancez spec-writer depuis son raccourci ».

### Le menu

Le menu a deux blocs :

- **Projet**, titré par le choix du projet courant : Conversation, Spécifications, Tickets Jira
  (avec la clé Jira du projet), Connaissance du projet et Réglages du projet. Ces pages portent
  sur le projet courant et le nomment dans leur titre.
- **Application** : Projets, Configuration et Journal, qui valent pour tous les projets. Sous
  Configuration, l'état de Copilot et de Jira reste visible.

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
