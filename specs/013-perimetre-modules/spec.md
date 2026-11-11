# Feature Specification: Périmètre des modules touchés

**Feature Branch**: `feature/017-perimetre-modules`

**Created**: 2026-11-11

**Status**: Draft

**Input**: User description: "Périmètre des modules touchés à partir des sources du code. Objectif : être exhaustif sur les modules touchés par une évolution, dans un arbre multi-modules (comme rv) et d'autres racines (pipelines, charts Helm). Décisions prises : ni RAG sur le code ni outils grep/find laissés au LLM ; spec-writer recense de façon déterministe, le LLM propose seulement les termes et qualifie les modules trouvés sans pouvoir en masquer un. US1 (P1) déclarer plusieurs racines de sources par projet dans les réglages du projet, motifs d'exclusion, .gitignore respecté, fichiers sensibles exclus d'office, aperçu « N modules détectés » (pom.xml, package.json, charts), rien copié ni indexé. US2 (P1) analyse de périmètre dans la conversation (010) : termes proposés et affichés, recherche dans toutes les racines sans plafond de pertinence, occurrences groupées par module (fichier:ligne), qualification impacté / à vérifier / non impacté avec justification, liste complète toujours visible, relance avec d'autres termes. US3 (P1) propagation par les dépendances Maven (agrégateur, parent, BOM) avec chaîne de dépendance. US4 (P2) relations du modèle C4 déjà indexé. US5 (P2) section « Modules touchés » dans la spécification rédigée depuis la discussion. Lecture seule, aucun outil Copilot, code jamais journalisé, extraits bornés, résultats déterministes rejouables en E2E, WCAG AA. Hors périmètre : RAG sur le code, analyse sémantique/AST, outil de lecture de fichier pour l'agent, Gradle. Feature 013, branche partant de feature/016-navigation-projet (PR #14 non mergée)."

## Contexte

Une US technique ou un découpage en tâches vaut par l'exhaustivité de son périmètre. Un module
oublié (une bibliothèque partagée, un consommateur d'une file, un chart de déploiement) se
découvre en recette ou en production. Dans un arbre de plusieurs dizaines de modules, personne ne
les a tous en tête.

spec-writer connaît déjà le projet par Jira, la documentation et le modèle d'architecture, mais
pas par son code. Deux façons de lui donner le code ont été écartées :

- **Un index de recherche sur le code** ne rend que les passages les plus proches de la question.
  Le module qui n'emploie qu'une fois le concept passe sous le seuil, et l'index vieillit à chaque
  commit.
- **Laisser l'IA explorer le code elle-même** : elle s'arrête quand elle croit en savoir assez.
  Elle échantillonne, elle ne recense pas. Ces outils exécuteraient aussi des commandes sur le
  poste, ce que l'outil refuse depuis la feature 010.

Le principe retenu partage le travail. **L'outil recense** : il cherche partout, sans plafond, et
de façon reproductible. **L'IA propose quoi chercher et qualifie ce qui a été trouvé**, sans
pouvoir retirer un module de la liste. L'utilisateur voit tout et tranche.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Déclarer les sources du projet (Priority: P1)

Dans les réglages du projet, l'utilisateur déclare une ou plusieurs racines de sources : l'arbre
des modules applicatifs, le dépôt des pipelines, celui des charts de déploiement. Pour chaque
racine, il peut ajouter des motifs d'exclusion. Les fichiers ignorés par le gestionnaire de
versions sont exclus. Les fichiers sensibles (variables d'environnement, clés, réglages Maven avec
identifiants, magasins de certificats) le sont toujours, quels que soient les motifs. Un aperçu
indique le nombre de modules détectés par racine et en liste les noms. L'outil ne copie ni
n'indexe rien : les sources sont relues à chaque analyse.

**Why this priority**: sans racines déclarées, aucune analyse n'est possible.

**Independent Test**: déclarer deux racines d'un arbre de démonstration et lire l'aperçu, qui
liste les modules attendus et aucun dossier exclu.

**Acceptance Scenarios**:

1. **Given** le projet Aurora sans racine de sources, **When** l'utilisateur ajoute la racine
   d'un arbre de 12 modules, **Then** l'aperçu indique « 12 modules détectés » et les nomme.
2. **Given** une racine déclarée, **When** l'utilisateur ajoute le motif `legacy/**`, **Then**
   l'aperçu retire les modules qui se trouvent sous `legacy/`.
3. **Given** une racine contenant un fichier `.env` et un `settings.xml`, **When** une analyse
   est lancée, **Then** aucun de ces fichiers n'est lu, même si un motif semble les inclure.
4. **Given** un chemin qui n'existe pas, ou qui n'est pas un dossier, **When** l'utilisateur
   l'ajoute, **Then** la racine est refusée avec un message qui dit pourquoi.
5. **Given** une racine déclarée puis déplacée sur le disque, **When** l'utilisateur ouvre les
   réglages, **Then** la racine est signalée introuvable, sans que les autres soient touchées.

---

### User Story 2 - Analyser le périmètre dans la conversation (Priority: P1) 🎯 MVP

Dans la conversation du projet, l'utilisateur décrit une évolution et demande quels modules elle
touche. L'agent propose des termes à chercher, tirés de la discussion et de la connaissance du
projet : noms de classes, d'entités, de clés de configuration, de files, de topics, de chemins
d'API. L'outil cherche chacun de ces termes dans toutes les racines, sans limite de pertinence,
et regroupe les occurrences par module. L'analyse s'affiche comme une étape de la conversation,
comme les recherches actuelles : termes employés, nombre de fichiers parcourus, modules trouvés
avec leurs occurrences (fichier et ligne). L'agent qualifie ensuite chaque module (« impacté »,
« à vérifier » ou « non impacté »), justifie son choix et répond en citant les modules. La liste
complète des modules trouvés reste affichée, même si la réponse en omet un. L'utilisateur peut
demander une nouvelle analyse avec d'autres termes au message suivant.

**Why this priority**: c'est la valeur de la feature, un périmètre dont on sait qu'il est complet
pour les termes choisis.

**Independent Test**: sur un arbre de démonstration où un terme apparaît dans 5 modules,
demander le périmètre et vérifier que les 5 modules s'affichent avec leurs occurrences.

**Acceptance Scenarios**:

1. **Given** un projet avec des racines déclarées, **When** l'utilisateur demande « quels modules
   touche l'ajout d'un statut SUSPENDU au contrat ? », **Then** une étape « Analyse du
   périmètre » affiche les termes cherchés, puis les modules trouvés avec leurs occurrences, et
   la réponse qualifie chaque module en le citant.
2. **Given** un terme présent dans 5 modules, **When** l'analyse se termine, **Then** les 5
   modules figurent dans l'étape, quel que soit le nombre d'occurrences de chacun.
3. **Given** une réponse de l'agent qui ne cite pas un module trouvé, **When** l'utilisateur lit
   l'étape, **Then** ce module y figure, marqué « non qualifié ».
4. **Given** une analyse affichée, **When** l'utilisateur répond « cherche aussi
   `contract.status` », **Then** une nouvelle analyse inclut ce terme, et les modules qu'il
   ajoute sont signalés comme nouveaux.
5. **Given** un projet sans racine de sources, **When** l'utilisateur demande un périmètre,
   **Then** l'agent répond avec la connaissance indexée et indique que l'analyse du code
   demande de déclarer des racines dans les réglages du projet, avec un lien vers ces réglages.
6. **Given** un terme présent dans plus de fichiers que la limite d'affichage, **When**
   l'analyse se termine, **Then** tous les modules concernés figurent, avec leur nombre exact
   d'occurrences et un échantillon de chacune, et un message invite à affiner le terme.

---

### User Story 3 - Suivre les dépendances entre modules (Priority: P1)

Un module touché par une évolution en entraîne d'autres : ceux qui en dépendent directement ou
indirectement. L'outil lit les dépendances déclarées entre les modules de l'arbre : module
agrégateur, parent, nomenclature de versions. Il ajoute au périmètre, « à vérifier », chaque
module qui dépend d'un module trouvé, avec la chaîne qui les relie.

**Why this priority**: dans un arbre bâti sur des bibliothèques partagées, la plupart des oublis
viennent de dépendances indirectes. Les ignorer rendrait le périmètre faussement complet.

**Independent Test**: dans un arbre de démonstration où B dépend de A et C dépend de B, une
occurrence dans A seul fait apparaître B et C « à vérifier », avec les chaînes A → B et
A → B → C.

**Acceptance Scenarios**:

1. **Given** une bibliothèque partagée trouvée par l'analyse, **When** l'étape s'affiche,
   **Then** chaque module qui en dépend figure « à vérifier », avec sa chaîne de dépendance.
2. **Given** un module trouvé à la fois par un terme et par une dépendance, **When** l'étape
   s'affiche, **Then** il figure une seule fois, avec ses deux raisons.
3. **Given** une dépendance vers une bibliothèque extérieure à l'arbre, **When** l'analyse se
   termine, **Then** elle n'ajoute aucun module.
4. **Given** deux modules qui dépendent l'un de l'autre, **When** l'analyse se termine, **Then**
   elle se termine normalement et chaque module figure une seule fois.

---

### User Story 4 - Suivre les relations du modèle d'architecture (Priority: P2)

Certains modules communiquent sans dépendance de code : appels d'API, files, topics. Le modèle
d'architecture du projet, déjà connu de l'outil, décrit ces relations. Quand un module trouvé
correspond à un conteneur du modèle, les conteneurs qui lui sont reliés figurent « à vérifier »,
avec la description de la relation.

**Why this priority**: c'est un complément utile, mais il dépend d'un modèle d'architecture à
jour et de la correspondance entre modules et conteneurs.

**Independent Test**: avec un modèle où le conteneur « facturation » appelle « contrats », une
occurrence dans le module contrats fait apparaître facturation « à vérifier », relation citée.

**Acceptance Scenarios**:

1. **Given** un module trouvé qui correspond à un conteneur du modèle, **When** l'étape
   s'affiche, **Then** les conteneurs reliés figurent « à vérifier », avec la relation.
2. **Given** un module sans conteneur correspondant, **When** l'étape s'affiche, **Then** il
   est indiqué « absent du modèle d'architecture », sans erreur.

---

### User Story 5 - Reprendre le périmètre dans la spécification (Priority: P2)

Quand l'utilisateur fait rédiger une spécification depuis la discussion (feature 010), la
spécification contient une section « Modules touchés ». Elle reprend la dernière analyse :
module, qualification, justification et preuves (occurrences ou chaîne de dépendance). Les
modules restés « à vérifier » y sont listés à part, comme hypothèses à valider.

**Why this priority**: c'est l'aboutissement du parcours, mais le périmètre a déjà de la valeur
lu dans la conversation.

**Independent Test**: après une analyse, rédiger une spécification et vérifier que la section
liste chaque module de l'analyse avec sa qualification.

**Acceptance Scenarios**:

1. **Given** une analyse de 8 modules dans la discussion, **When** l'utilisateur fait rédiger la
   spécification, **Then** la section « Modules touchés » liste les 8 modules et leur
   qualification.
2. **Given** des modules « à vérifier », **When** la spécification est rédigée, **Then** ils
   figurent parmi les hypothèses à valider.
3. **Given** une discussion sans analyse de périmètre, **When** la spécification est rédigée,
   **Then** elle n'a pas de section « Modules touchés ».

---

### Edge Cases

- Racine absente, déplacée ou illisible : signalée, et l'analyse se fait sur les autres racines
  en le disant.
- Terme trop générique (« id », « status ») : l'analyse le signale avec son nombre
  d'occurrences, montre un échantillon par module et propose de l'affiner. Aucun module n'est
  omis.
- Fichiers binaires, générés ou de dépendances (sorties de compilation, paquets téléchargés) :
  ignorés d'office.
- Fichier trop gros : ignoré et compté, avec son nom.
- Dossier sans descripteur de module : ses fichiers sont rattachés au module parent le plus
  proche, ou à la racine s'il n'y en a pas.
- Module imbriqué dans un autre : les occurrences sont rattachées au module le plus profond.
- Lien symbolique, ou chemin qui sort de la racine : jamais suivi.
- Analyse qui dépasse la durée ou le nombre de fichiers maximal : arrêtée. L'étape dit qu'elle
  est incomplète et nomme ce qui n'a pas été parcouru. Un périmètre incomplet n'est jamais
  présenté comme complet.
- Aucun résultat : l'étape le dit avec les termes employés, et l'agent propose d'autres termes.
- Termes proposés contenant des caractères spéciaux : cherchés littéralement.
- Texte des sources qui contient des instructions adressées à l'IA : traité comme une donnée, ce
  qui ne change pas la liste des modules.

## Requirements *(mandatory)*

### Functional Requirements

**Racines de sources**

- **FR-001**: Un projet DOIT pouvoir déclarer plusieurs racines de sources dans ses réglages, et
  l'utilisateur DOIT pouvoir les modifier et les supprimer.
- **FR-002**: Chaque racine DOIT accepter des motifs d'exclusion, une ligne par motif, avec les
  mêmes règles et la même validation que les dossiers de documents (feature 006).
- **FR-003**: Les fichiers ignorés par le gestionnaire de versions de la racine DOIVENT être
  exclus.
- **FR-004**: Les fichiers sensibles DOIVENT être exclus d'office, sans qu'un motif puisse les
  réintégrer : variables d'environnement, clés privées, magasins de certificats, réglages Maven
  ou npm qui peuvent porter des identifiants.
- **FR-005**: Les sorties de compilation, les dépendances téléchargées, les fichiers binaires et
  les fichiers au-delà d'une taille maximale DOIVENT être ignorés.
- **FR-006**: Les réglages DOIVENT afficher, par racine, le nombre de modules détectés et leurs
  noms. Un module se reconnaît à son descripteur Maven, à son descripteur npm ou à son chart de
  déploiement.
- **FR-007**: Le contenu des sources NE DOIT être ni copié, ni indexé, ni conservé : seule la
  dernière analyse de chaque conversation est gardée (termes, modules, occurrences sous forme
  d'emplacement et d'extrait borné).

**Analyse du périmètre**

- **FR-008**: L'agent de conversation DOIT pouvoir lancer une analyse de périmètre quand la
  discussion porte sur l'impact d'une évolution. L'analyse est une étape visible de la réponse.
- **FR-009**: L'agent DOIT proposer les termes à chercher. L'outil DOIT les afficher avant les
  résultats.
- **FR-010**: L'outil DOIT chercher chaque terme littéralement dans tous les fichiers retenus de
  toutes les racines, sans limite de pertinence et sans s'arrêter au premier résultat.
- **FR-011**: Les occurrences DOIVENT être regroupées par module, avec le fichier, la ligne et un
  extrait borné de la ligne.
- **FR-012**: Chaque module trouvé DOIT figurer dans l'étape, avec son nombre exact
  d'occurrences. Seul l'affichage des occurrences peut être réduit à un échantillon.
- **FR-013**: L'agent DOIT qualifier chaque module (impacté, à vérifier, non impacté) avec une
  justification. Un module que l'agent n'a pas qualifié DOIT être affiché « non qualifié ».
- **FR-014**: Ni la réponse de l'agent ni sa qualification NE DOIVENT retirer un module de la
  liste affichée.
- **FR-015**: L'utilisateur DOIT pouvoir faire relancer l'analyse avec d'autres termes dans la
  conversation. Les modules nouveaux par rapport à l'analyse précédente DOIVENT être signalés.
- **FR-016**: Une analyse arrêtée par une limite (durée, nombre de fichiers) ou privée d'une
  racine DOIT être affichée comme incomplète, avec ce qui manque.
- **FR-017**: À sources et termes identiques, l'analyse DOIT produire les mêmes modules, les
  mêmes occurrences et le même ordre.

**Propagation**

- **FR-018**: L'outil DOIT lire les dépendances déclarées entre les modules de l'arbre (agrégateur,
  parent, nomenclature de versions, dépendances) et ajouter « à vérifier » chaque module qui
  dépend, directement ou non, d'un module trouvé, avec la chaîne de dépendance.
- **FR-019**: Quand un module trouvé correspond à un conteneur du modèle d'architecture, les
  conteneurs reliés DOIVENT être signalés « à vérifier », avec la relation.
- **FR-020**: Un module qui a plusieurs raisons d'être dans le périmètre DOIT figurer une seule
  fois, avec toutes ses raisons.

**Spécification**

- **FR-021**: Une spécification rédigée depuis une discussion qui contient une analyse DOIT avoir
  une section « Modules touchés » qui reprend la dernière analyse. Les modules « à vérifier »
  DOIVENT aussi figurer parmi les hypothèses à valider.

**Sécurité et confidentialité**

- **FR-022**: L'analyse DOIT être en lecture seule. Elle NE DOIT lancer aucune commande et NE
  DOIT donner à l'IA aucun accès direct aux fichiers.
- **FR-023**: Aucun lien symbolique NE DOIT être suivi. Aucun fichier hors d'une racine NE DOIT
  être lu.
- **FR-024**: Le texte des sources NE DOIT jamais apparaître dans le journal ni dans les messages
  d'erreur.
- **FR-025**: Seuls les termes, les noms de modules et les extraits bornés DOIVENT être transmis
  à l'IA. Leur volume total DOIT être borné. Le texte des sources DOIT être séparé des
  instructions et traité comme une donnée.
- **FR-026**: L'utilisateur DOIT être informé, dans les réglages des racines, que des extraits du
  code sont transmis au service d'IA lors d'une analyse.

**Accessibilité**

- **FR-027**: L'étape d'analyse DOIT respecter le niveau WCAG 2.1 AA. Liste des modules
  parcourable au clavier, qualification lisible sans la couleur, fin de l'analyse annoncée aux
  lecteurs d'écran.

### Key Entities

- **Racine de sources** : dossier du poste rattaché à un projet, avec ses motifs d'exclusion. Un
  projet en a zéro ou plusieurs.
- **Module** : sous-arbre d'une racine reconnu par son descripteur. Il a un nom, un chemin relatif
  et les modules dont il dépend dans l'arbre.
- **Analyse de périmètre** : étape d'une conversation. Elle a des termes, les racines parcourues,
  un état (complète ou incomplète, avec la raison) et des modules du périmètre.
- **Module du périmètre** : un module dans une analyse. Il a ses raisons (occurrences d'un terme,
  chaîne de dépendance, relation d'architecture), sa qualification (impacté, à vérifier, non
  impacté, non qualifié) et la justification de l'agent.
- **Occurrence** : fichier, ligne, terme trouvé et extrait borné.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Sur un arbre de démonstration, 100 % des modules qui contiennent un terme cherché
  figurent dans l'analyse.
- **SC-002**: Sur ce même arbre, 100 % des modules qui dépendent, directement ou non, d'un module
  trouvé figurent « à vérifier », avec une chaîne de dépendance exacte.
- **SC-003**: Une analyse de 5 termes sur un arbre de 60 modules et 20 000 fichiers se termine en
  moins de 30 secondes sur le poste de l'utilisateur.
- **SC-004**: Dans 100 % des essais de bout en bout, aucun module trouvé n'est absent de
  l'affichage, y compris quand la réponse de l'IA l'omet.
- **SC-005**: Aucun fichier sensible n'est jamais lu, et aucun texte des sources n'apparaît dans
  le journal (vérifié par un test de bout en bout).
- **SC-006**: Sur le jeu d'évaluation, les termes proposés par l'agent retrouvent au moins 90 %
  des modules attendus. Le recul de ce score bloque la fusion.
- **SC-007**: Deux analyses identiques donnent un résultat identique.

## Assumptions

- Les sources sont présentes sur le poste de l'utilisateur, à jour selon ce qu'il a récupéré.
  L'outil ne récupère ni ne met à jour aucun dépôt.
- L'analyse porte sur la branche présente sur le disque, sans tenir compte de l'historique.
- Les dépendances sont lues dans les descripteurs Maven. Les autres systèmes de construction
  (Gradle notamment) et les dépendances entre paquets npm sont hors périmètre. Ces modules sont
  détectés et cherchés, mais sans propagation.
- La correspondance entre un module et un conteneur du modèle d'architecture se fait par le nom
  ou par le chemin déclaré dans le modèle. Sans correspondance, la propagation par l'architecture
  ne s'applique pas à ce module.
- Hors périmètre : un index de recherche sur le code, une analyse sémantique (appels entre
  classes, arbres syntaxiques), un outil permettant à l'IA de lire un fichier entier, et
  l'analyse depuis le questionnaire ou la génération classique.
- Les données transmises à l'IA suivent le consentement déjà donné pour le service d'IA. FR-026
  l'étend explicitement aux extraits de code.
- Cette feature s'appuie sur la page « Réglages du projet » de la feature 012 et sur la
  conversation de la feature 010.
