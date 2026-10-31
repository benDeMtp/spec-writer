# Feature Specification: Assistant local de rédaction de spécifications (use cases et scénarios d'acceptation)

**Feature Branch**: `001-local-spec-assistant`

**Created**: 2026-10-30

**Status**: Draft

**Input**: User description: "Outil d'aide à la rédaction de spécifications techniques / fonctionnelles qui génère des spécifications au format Gherkin avec use cases, après avoir posé une série de questions déterminées à partir d'un besoin initialement exprimé. Pour le moment, focus sur une version qui tourne en local et mono-utilisateur."

## Clarifications

### Session 2026-10-30

- Q: Que doit lire l'outil dans Jira ? → A: une recherche de tickets via l'API Jira, présentée
  dans la même liste que la recherche native sur les spécifications stockées dans spec-writer.
  Une colonne indique la provenance (spec-writer, Jira, les deux). Cliquer sur un élément affiche
  sa description, que l'utilisateur peut modifier avec l'aide de l'IA.
- Q: Quand l'utilisateur modifie avec l'IA la description d'un ticket présent dans Jira, que doit
  faire l'outil ? → A: créer ou mettre à jour la spécification dans spec-writer (provenance « les
  deux »), puis proposer de pousser la mise à jour vers Jira, avec confirmation et détection des
  modifications faites entre-temps dans Jira.
- Q: Que recouvre le terme « Gherkin » dans ce projet ? → A: uniquement le style de rédaction
  « En tant que… / Quand… / Alors… », lisible par des humains. Le Gherkin exécutable (fichiers
  `.feature` pour l'automatisation des tests) n'est pas nécessaire. Terme retenu dans la spec :
  « scénario d'acceptation ».
- Q: Où l'outil doit-il conserver les jetons Jira et GitHub Copilot sur le poste ? → A: dans le
  trousseau de clés du système d'exploitation.
- Q: Quand l'utilisateur crée un ticket Jira à partir d'une spécification terminée, comment le
  ticket doit-il être structuré ? → A: un seul ticket, au type choisi dans l'aperçu (Story par
  défaut), avec toute la spécification mise en forme dans la description.
- Q: Comment l'utilisateur doit-il connecter son compte GitHub Copilot dans l'écran de
  configuration ? → A: par une connexion GitHub depuis le navigateur, le jeton obtenu étant
  stocké dans le trousseau. Le spike S1 a établi que le SDK utilise une page d'autorisation OAuth
  avec retour automatique, et non un code court (FR-030a reformulé, research.md R4).

### Session 2026-10-31

- Q: Comment une description Jira s'affiche-t-elle dans spec-writer ? → A: sa syntaxe Jira est
  interprétée pour une liste fermée d'éléments (tableaux, liens, citations, panneaux…). Les images,
  pièces jointes et mentions sont remplacées par un renvoi vers le ticket. Tout autre bloc reste
  affiché tel quel (FR-043a, US6-3).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configurer les accès à GitHub Copilot et à Jira (Priority: P1)

Avant toute chose, l'utilisateur ouvre un écran de configuration. Il y connecte son compte
GitHub Copilot, indispensable au questionnaire et à la génération. S'il le souhaite, il y
renseigne aussi l'accès à son serveur Jira on premise : adresse du serveur, jeton utilisateur
personnel et projet Jira par défaut. L'outil vérifie chaque accès et affiche son état.

**Why this priority**: sans accès à GitHub Copilot, le parcours principal ne fonctionne pas.
La configuration Jira est facultative et n'empêche pas d'utiliser l'outil.

**Independent Test**: renseigner des accès valides puis invalides, lancer le test de connexion,
et vérifier l'état affiché, les messages d'erreur, et que les jetons ne réapparaissent jamais en
clair après enregistrement.

**Acceptance Scenarios**:

1. **Given** aucun accès GitHub Copilot n'est configuré, **When** l'utilisateur lance l'outil,
   **Then** il est dirigé vers l'écran de configuration avec une explication de ce qui est requis,
   et ne peut pas démarrer de questionnaire.
2. **Given** l'écran de configuration, **When** l'utilisateur clique sur « Se connecter à
   GitHub », **Then** l'outil affiche les permissions demandées et ouvre la page d'autorisation
   de GitHub ; une fois l'accès autorisé, l'état passe à « connecté » avec le login du compte,
   sans autre manipulation, et le jeton est stocké dans le trousseau du système.
3. **Given** le compte GitHub connecté ne dispose pas d'un abonnement Copilot actif, **When** la
   connexion aboutit, **Then** l'outil l'indique clairement et explique comment obtenir l'accès.
4. **Given** l'écran de configuration Jira, **When** l'utilisateur saisit une adresse de serveur,
   un jeton et un projet, puis lance le test de connexion, **Then** l'outil indique si le serveur
   répond, si le jeton est accepté et si le projet existe et est accessible en écriture.
5. **Given** un jeton enregistré, **When** l'utilisateur revient sur l'écran de configuration,
   **Then** le jeton est masqué ; il peut le remplacer ou le supprimer, mais pas l'afficher.
6. **Given** un accès Jira invalide (serveur injoignable, jeton refusé, projet inconnu), **When**
   le test échoue, **Then** l'outil indique laquelle de ces trois causes est en jeu et l'action à
   mener.

---

### User Story 2 - Du besoin brut à une spécification avec scénarios d'acceptation (Priority: P1)

Un utilisateur (product owner, analyste, développeur) décrit en quelques phrases un besoin en
langage naturel. L'outil analyse ce besoin, pose une série de questions ciblées pour lever les
ambiguïtés (acteurs, règles métier, cas d'erreur, contraintes), puis produit une spécification
contenant des use cases et des scénarios d'acceptation rattachés à ces use cases.

**Why this priority**: c'est la raison d'être du produit. Sans ce parcours, aucune valeur n'est
délivrée ; à lui seul, il constitue un MVP utilisable.

**Independent Test**: saisir un besoin de référence, répondre aux questions posées, et vérifier
qu'une spécification est produite, que ses scénarios respectent la structure attendue, et que
chaque scénario est rattaché
à un use case lui-même rattaché au besoin ou à une réponse.

**Acceptance Scenarios**:

1. **Given** l'outil est lancé sur le poste de l'utilisateur, **When** l'utilisateur saisit un
   besoin initial, **Then** l'outil affiche une première question liée au contenu de ce besoin,
   accompagnée de la raison pour laquelle elle est posée et d'un indicateur de progression.
2. **Given** le besoin mentionne déjà un élément (par exemple l'acteur principal), **When** l'outil
   construit son questionnaire, **Then** aucune question ne porte sur cet élément déjà connu.
3. **Given** l'utilisateur a répondu à toutes les questions, **When** il demande la génération,
   **Then** l'outil produit une liste de use cases et, pour chacun, au moins un scénario
   d'acceptation
   (cas nominal) et au moins un scénario d'erreur ou de cas limite lorsque les réponses en
   décrivent.
4. **Given** une spécification générée, **When** l'utilisateur la consulte, **Then** chaque
   hypothèse faite par l'outil (non issue du besoin ou des réponses) est explicitement signalée et
   listée dans une section dédiée.
5. **Given** une question, **When** l'utilisateur choisit de la passer, **Then** l'outil retient une
   valeur par défaut, l'affiche, et la trace comme hypothèse dans la spécification générée.

---

### User Story 3 - Revenir sur ses réponses et reprendre une session (Priority: P2)

L'utilisateur peut corriger une réponse déjà donnée, interrompre son travail et le reprendre plus
tard exactement là où il s'était arrêté, y compris après fermeture de l'outil.

**Why this priority**: un questionnaire de qualité prend du temps ; perdre son travail ou ne pas
pouvoir corriger une erreur fait abandonner l'utilisateur (principe I de la constitution).

**Independent Test**: démarrer une session, répondre à une partie des questions, fermer l'outil,
le relancer, reprendre la session, modifier une réponse antérieure et vérifier que l'état et les
questions suivantes sont cohérents.

**Acceptance Scenarios**:

1. **Given** une session en cours, **When** l'utilisateur ferme l'outil puis le relance, **Then**
   la liste de ses sessions est proposée et il peut reprendre la session à la question où il
   s'était arrêté, avec toutes ses réponses conservées.
2. **Given** une session avancée, **When** l'utilisateur modifie une réponse antérieure, **Then**
   l'outil réévalue les questions suivantes : celles devenues sans objet sont retirées, les
   nouvelles questions nécessaires sont ajoutées, et les réponses toujours valides sont conservées.
3. **Given** une spécification déjà générée, **When** l'utilisateur modifie une réponse et
   régénère, **Then** une nouvelle version de la spécification est produite et la version
   précédente reste consultable.

---

### User Story 4 - Exporter la spécification (Priority: P3)

L'utilisateur exporte la spécification générée sous forme de document Markdown lisible par les
parties prenantes, pour la partager ou l'intégrer à sa documentation.

**Why this priority**: la spécification n'a de valeur durable que si elle sort de l'outil, mais le
parcours P1 reste démontrable sans export (consultation à l'écran).

**Independent Test**: exporter une spécification générée et vérifier que le document Markdown
contient le résumé, les use cases, les scénarios d'acceptation et les hypothèses.

**Acceptance Scenarios**:

1. **Given** une spécification générée, **When** l'utilisateur demande l'export, **Then** il obtient
   un fichier Markdown à l'emplacement de son choix sur son poste.
2. **Given** un fichier de destination existant, **When** l'utilisateur exporte vers ce même
   emplacement, **Then** l'outil demande une confirmation avant d'écraser.

---

### User Story 5 - Créer un ticket Jira à partir de la spécification (Priority: P3)

Une fois la spécification terminée, l'utilisateur choisit de créer un ticket dans le projet Jira
configuré. L'outil prépare le ticket à partir de la spécification, l'utilisateur le relit, puis
confirme la création. L'outil affiche alors la clé et le lien du ticket créé.

**Why this priority**: c'est l'intégration la plus attendue avec l'outillage de l'équipe, mais
l'export (P3) permet déjà de transférer la spécification manuellement.

**Independent Test**: générer une spécification, créer le ticket sur un serveur Jira de test,
et vérifier son contenu, sa clé et le lien enregistré dans la session.

**Acceptance Scenarios**:

1. **Given** une spécification générée et un accès Jira valide, **When** l'utilisateur choisit de
   créer un ticket, **Then** l'outil affiche un aperçu du ticket unique (projet, type modifiable,
   Story par défaut, titre, description contenant toute la spécification mise en forme) et ne
   crée rien sans confirmation explicite.
2. **Given** l'utilisateur confirme la création, **When** Jira accepte le ticket, **Then** l'outil
   affiche la clé du ticket et un lien vers celui-ci, et l'enregistre dans la session.
3. **Given** Jira n'est pas configuré, **When** l'utilisateur consulte une spécification, **Then**
   l'option de création de ticket est indisponible et explique comment configurer Jira.
4. **Given** Jira refuse la création (droits, champ obligatoire manquant, serveur injoignable),
   **When** l'erreur survient, **Then** l'outil en indique la cause, ne perd pas la spécification
   et permet de réessayer.

---

### User Story 6 - Rechercher dans spec-writer et Jira, puis faire évoluer une spécification (Priority: P2)

L'utilisateur recherche par mots-clés dans un écran unique. Les résultats regroupent les
spécifications stockées dans spec-writer et, si Jira est configuré, les tickets du serveur Jira.
Une colonne indique la provenance de chaque élément : spec-writer, Jira, ou les deux. En cliquant
sur un élément, l'utilisateur consulte sa description et peut la faire évoluer avec l'aide de
l'IA.

**Why this priority**: retrouver et améliorer l'existant est aussi fréquent que créer une
spécification de zéro, et évite les doublons entre l'outil et Jira.

**Independent Test**: avec trois éléments de référence (une spécification locale seule, un ticket
Jira seul, une spécification liée à un ticket), lancer une recherche qui les couvre et vérifier la
liste, la colonne de provenance, l'ouverture du détail et la modification assistée.

**Acceptance Scenarios**:

1. **Given** Jira n'est pas configuré, **When** l'utilisateur lance une recherche, **Then** seules
   les spécifications de spec-writer sont listées, avec une mention indiquant que Jira n'est pas
   interrogé.
2. **Given** Jira est configuré, **When** l'utilisateur lance une recherche, **Then** la liste
   regroupe les résultats des deux sources avec les mêmes colonnes, et un élément présent dans les
   deux sources n'apparaît qu'une fois, avec la provenance « les deux ».
3. **Given** un résultat dans la liste, dont la description Jira contient un tableau et un lien,
   **When** l'utilisateur clique dessus, **Then** l'outil affiche la description complète, avec le
   tableau rendu en tableau (en-têtes compris) et le lien cliquable (FR-043a).
4. **Given** la description affichée, **When** l'utilisateur demande une modification à l'IA (par
   exemple « ajoute un cas d'erreur sur le paiement refusé »), **Then** l'outil propose une
   nouvelle version, montre les différences avec la version actuelle, et n'applique rien sans
   validation de l'utilisateur.
5. **Given** un ticket présent uniquement dans Jira, **When** l'utilisateur valide une
   modification proposée par l'IA, **Then** une spécification liée à ce ticket est créée dans
   spec-writer, sa provenance devient « les deux », et Jira n'est pas encore modifié.
6. **Given** une spécification liée modifiée localement, **When** l'utilisateur choisit de pousser
   la mise à jour vers Jira, **Then** l'outil montre la description qui sera envoyée et ne met à
   jour le ticket qu'après confirmation.
7. **Given** le ticket a été modifié dans Jira depuis sa dernière lecture, **When** l'utilisateur
   pousse sa mise à jour, **Then** l'outil signale le conflit, affiche les deux versions et laisse
   l'utilisateur choisir (écraser, reprendre la version Jira, ou fusionner avec l'aide de l'IA).
8. **Given** Jira est injoignable pendant une recherche, **When** les résultats s'affichent,
   **Then** les résultats spec-writer sont présentés et un message signale que Jira n'a pas
   répondu.

---

### Edge Cases

- Le besoin initial est vide, trop court (moins de 10 mots) ou hors sujet : l'outil explique ce
  qui manque et propose un exemple de besoin bien formulé, sans démarrer le questionnaire.
- Le besoin initial dépasse la taille maximale autorisée : l'outil refuse la saisie avec un message
  indiquant la limite.
- Le besoin contient des éléments contradictoires : l'outil pose une question explicite pour
  trancher la contradiction avant de générer.
- L'utilisateur passe toutes les questions : la génération reste possible, mais l'outil avertit
  que la spécification reposera majoritairement sur des hypothèses et les liste toutes.
- Le moteur de questions ou de génération est indisponible ou échoue en cours de route : les
  réponses déjà saisies sont conservées et l'utilisateur peut relancer l'étape.
- Le texte saisi contient des instructions visant à détourner l'outil (par exemple « ignore les
  consignes précédentes ») : il est traité comme du contenu du besoin, jamais comme une
  instruction.
- Le texte saisi contient des caractères ayant un sens en Markdown ou dans la mise en forme Jira
  (`|`, `*`, `#`, `{`, `[`) : la spécification affichée, exportée ou envoyée à Jira reste
  correctement mise en forme.
- Le fichier de sauvegarde d'une session est corrompu : l'outil le signale, ne plante pas, et
  laisse accès aux autres sessions.
- Le trousseau de clés est verrouillé, absent ou refuse l'accès : l'outil le signale et propose
  de saisir le jeton pour la seule durée de la session.
- Le jeton Jira expire ou est révoqué pendant une recherche ou un envoi : l'outil l'indique,
  conserve le travail en cours et dirige vers l'écran de configuration.
- Fermeture brutale de l'outil pendant la saisie : au plus la dernière réponse en cours de saisie
  est perdue.

## Requirements *(mandatory)*

### Functional Requirements

**Recueil du besoin et questionnaire**

- **FR-001**: Le système DOIT permettre de saisir un besoin initial en texte libre, d'une longueur
  maximale de 10 000 caractères.
- **FR-002**: Le système DOIT déterminer les questions à poser à partir du contenu du besoin initial
  et des réponses déjà données, et NE DOIT PAS poser de question dont la réponse est déjà présente
  ou déductible.
- **FR-003**: Le système DOIT couvrir au minimum, lorsqu'ils ne sont pas déjà connus : les acteurs,
  l'objectif métier, le parcours nominal, les règles métier, les cas d'erreur, les cas limites, les
  contraintes non fonctionnelles.
- **FR-004**: Le système DOIT présenter les questions une par une ou par lots thématiques de 5
  questions au maximum, avec un indicateur de progression.
- **FR-005**: Chaque question DOIT être accompagnée d'une courte justification et, lorsque c'est
  pertinent, de réponses suggérées et d'une valeur par défaut.
- **FR-006**: L'utilisateur DOIT pouvoir passer une question ; la valeur par défaut retenue DOIT
  alors être tracée comme hypothèse.
- **FR-007**: L'utilisateur DOIT pouvoir modifier une réponse antérieure ; le système DOIT alors
  réévaluer les questions dépendantes en conservant les réponses toujours valides.
- **FR-008**: Le système DOIT détecter les contradictions entre le besoin et les réponses et poser
  une question pour les résoudre avant la génération.
- **FR-009**: Le questionnaire DOIT pouvoir être conclu à tout moment par l'utilisateur, la
  génération restant possible avec les informations disponibles.

**Génération**

- **FR-010**: Le système DOIT générer une spécification comprenant : un résumé du besoin, une liste
  de use cases (identifiant, titre, acteur, objectif, préconditions, déroulement nominal,
  alternatives), des scénarios d'acceptation, une liste d'hypothèses, et une séparation entre exigences
  fonctionnelles et exigences techniques.
- **FR-011**: Chaque scénario d'acceptation DOIT référencer le use case dont il découle, et chaque use
  case DOIT référencer le passage du besoin ou la réponse dont il découle.
- **FR-012**: Chaque scénario d'acceptation DOIT suivre la structure « En tant que <acteur>,
  [Étant donné <contexte>,] Quand <action>, Alors <résultat attendu> », où seul « Étant donné »
  est facultatif. Un contrôle de structure DOIT être appliqué avant présentation ; un scénario
  incomplet NE DOIT PAS être présenté comme valide.
- **FR-012a**: Le Gherkin exécutable (fichiers `.feature`, mots-clés `Feature` / `Scenario`) est
  hors périmètre.
- **FR-013**: Le système NE DOIT PAS introduire de comportement métier absent du besoin et des
  réponses sans le marquer comme hypothèse.
- **FR-014**: À entrées identiques (besoin, réponses, version de l'outil), la structure de la
  spécification générée (use cases, scénarios, identifiants) DOIT être identique.
- **FR-015**: Le système DOIT générer la spécification dans la langue du besoin initial, au minimum
  en français et en anglais, les mots-clés des scénarios étant ceux de cette langue (« En tant
  que / Étant donné / Quand / Alors » ou « As a / Given / When / Then »).

**Sessions et versions**

- **FR-016**: Le système DOIT sauvegarder automatiquement la session après chaque réponse.
- **FR-017**: L'utilisateur DOIT pouvoir lister, reprendre, renommer et supprimer ses sessions.
- **FR-018**: Chaque génération dont les entrées (besoin, réponses, langue) ont changé depuis la
  dernière version DOIT créer une nouvelle version de la spécification. À entrées identiques, la
  version existante DOIT être renvoyée sans nouvelle génération. Les versions précédentes d'une
  session DOIVENT rester consultables.

**Export**

- **FR-019**: L'utilisateur DOIT pouvoir exporter une spécification en Markdown vers un emplacement
  de son choix.
- **FR-020**: Le système DOIT demander confirmation avant d'écraser un fichier existant.

**Fonctionnement local et confidentialité**

- **FR-021**: Le système DOIT fonctionner sur le poste de l'utilisateur, pour un seul utilisateur,
  sans compte ni authentification.
- **FR-022**: Les sessions, réponses et spécifications DOIVENT être stockées uniquement sur le poste
  de l'utilisateur, dans un emplacement accessible au seul compte système de cet utilisateur.
  Les seules sorties de données prévues sont les appels à GitHub Copilot (FR-023, FR-024) et les
  échanges avec Jira déclenchés par l'utilisateur (recherche, création, envoi de mise à jour).
- **FR-023**: Le moteur de questions et de génération DOIT s'appuyer sur le service de modèle de
  langage distant auquel l'utilisateur accède avec son propre compte (GitHub Copilot). Le contenu
  du besoin et des réponses quitte donc le poste lors des appels à ce service. Les identifiants de
  ce compte NE DOIVENT jamais être affichés, journalisés ni écrits dans les exports.
- **FR-023a**: Si le service est injoignable, si le compte n'est pas connecté ou si ses droits ne
  suffisent pas, le système DOIT l'indiquer avec l'action à mener (se connecter, réessayer),
  conserver toutes les réponses déjà saisies et permettre de relancer l'étape.
- **FR-024**: Si une donnée doit quitter le poste, le système DOIT en informer l'utilisateur et
  obtenir son accord explicite avant le premier envoi, accord révocable à tout moment.
- **FR-025**: Les journaux techniques NE DOIVENT contenir ni le texte du besoin, ni les réponses,
  ni les spécifications générées.
- **FR-026**: Le contenu saisi par l'utilisateur DOIT toujours être traité comme une donnée et
  jamais comme une instruction pour le moteur de questions ou de génération.

**Configuration des accès**

- **FR-030**: Le système DOIT fournir un écran de configuration des accès à GitHub Copilot et à
  Jira on premise.
- **FR-030a**: La connexion à GitHub Copilot DOIT se faire par une page d'autorisation GitHub
  ouverte dans le navigateur, avec un retour automatique vers l'outil. L'utilisateur NE DOIT PAS
  avoir à créer ni à copier un jeton. Les permissions demandées par GitHub DOIVENT être affichées
  avant la redirection. Si l'autorisation n'aboutit pas dans les 15 minutes ou est refusée,
  l'outil DOIT permettre de recommencer. Le compte GitHub effectivement connecté DOIT être
  affiché.
- **FR-030b**: L'utilisateur DOIT pouvoir se déconnecter de GitHub Copilot ; le jeton est alors
  supprimé du trousseau.
- **FR-030c**: L'utilisateur DOIT pouvoir choisir, dans l'écran de configuration, le modèle de
  langage utilisé parmi ceux que son organisation autorise ; un modèle par défaut DOIT être
  proposé. Si le modèle choisi devient indisponible ou si le quota du compte est épuisé, l'outil
  DOIT l'indiquer avec l'action à mener, sans perdre le travail en cours.
- **FR-031**: Pour Jira, l'utilisateur DOIT pouvoir renseigner l'adresse du serveur, un jeton
  utilisateur personnel et un projet par défaut.
- **FR-032**: Le système DOIT proposer un test de connexion pour chaque service et afficher l'état
  de chaque accès (configuré et valide, invalide, non configuré).
- **FR-033**: Une fois enregistré, un jeton NE DOIT plus jamais être affiché en clair ; il peut
  seulement être remplacé ou supprimé.
- **FR-033a**: Les jetons et identifiants des services DOIVENT être conservés uniquement dans le
  trousseau de clés du système d'exploitation ; ils NE DOIVENT jamais être écrits dans un fichier
  de l'outil (configuration, session, cache). Les autres paramètres (adresse du serveur, projet
  par défaut) peuvent être stockés dans la configuration locale.
- **FR-033b**: Si le trousseau de clés est indisponible ou en refuse l'accès, le système DOIT
  l'indiquer et proposer une utilisation sans enregistrement : le jeton est alors gardé en mémoire
  le temps de la session et redemandé au lancement suivant.
- **FR-034**: Les jetons NE DOIVENT jamais apparaître dans les journaux, les messages d'erreur, les
  sessions sauvegardées ni les exports.
- **FR-035**: L'adresse du serveur Jira DOIT être validée avant enregistrement ; une adresse non
  chiffrée (sans HTTPS) DOIT déclencher un avertissement explicite.

**Intégration Jira**

- **FR-036**: Le système DOIT permettre de créer un ticket Jira unique à partir d'une
  spécification générée, dans le projet configuré. Le titre du ticket reprend celui de la
  spécification, et sa description contient l'intégralité de la spécification (résumé, use cases,
  scénarios d'acceptation, hypothèses, exigences) convertie dans la mise en forme Jira, sans pièce
  jointe.
- **FR-036a**: Le type de ticket DOIT pouvoir être choisi dans l'aperçu parmi les types disponibles
  dans le projet ; « Story » est proposé par défaut lorsqu'il existe.
- **FR-036b**: Si le projet impose des champs obligatoires non renseignables à partir de la
  spécification, l'aperçu DOIT permettre de les remplir avant la création.
- **FR-037**: La création d'un ticket DOIT être précédée d'un aperçu et d'une confirmation
  explicite de l'utilisateur.
- **FR-038**: Après création, la clé et le lien du ticket DOIVENT être affichés et enregistrés
  dans la session.
- **FR-039**: Lorsque Jira est configuré, le système DOIT permettre de rechercher des tickets Jira
  par mots-clés.

**Recherche et modification assistée**

- **FR-040**: Le système DOIT offrir une recherche native par mots-clés sur les spécifications
  stockées dans spec-writer (titre, besoin, use cases, scénarios).
- **FR-041**: Les résultats des deux sources DOIVENT être présentés dans une liste unique, avec les
  mêmes colonnes (au minimum : identifiant, titre, provenance, date de dernière modification).
- **FR-042**: La colonne provenance DOIT valoir « spec-writer », « Jira » ou « les deux ». Un
  élément vaut « les deux » lorsqu'une spécification est liée à un ticket Jira (Ticket Jira lié) ;
  il n'apparaît alors qu'une fois dans la liste.
- **FR-043**: Un clic sur un résultat DOIT afficher sa description complète.
- **FR-043a**: Une description Jira DOIT être affichée en interprétant sa syntaxe (wiki markup)
  pour les éléments suivants, sans perte de contenu :
  - titres, listes, gras, italique, texte barré, souligné, code en ligne ;
  - tableaux : une ligne d'en-tête `||` devient l'en-tête du tableau ; un tableau sans ligne
    d'en-tête reçoit un en-tête vide ; un en-tête placé en première colonne reste en gras ; un
    retour à la ligne dans une cellule (`\\`) est conservé ;
  - liens `[texte|url]` et `[url]` : seuls les protocoles `http`, `https` et `mailto` sont
    cliquables, ouverts dans un nouvel onglet avec `rel="noopener noreferrer"` ; les autres
    restent du texte ;
  - citations (`bq.` et `{quote}`), blocs `{code}` et `{noformat}`, panneaux `{panel}` (encadré,
    avec leur titre) ;
  - `{color}` : la couleur est ignorée, le texte est conservé.

  Les images, pièces jointes (`!fichier!`) et mentions (`[~identifiant]`) sont remplacées par un
  texte qui les nomme, avec un renvoi vers le ticket dans Jira : aucun contenu n'est téléchargé.
  Tout autre bloc est affiché tel quel, en bloc de code. La même conversion alimente l'import, la
  fusion par l'IA et l'affichage d'un conflit (FR-046, FR-048).
- **FR-044**: L'utilisateur DOIT pouvoir demander à l'IA de modifier une description en langage
  naturel ; le système DOIT présenter la proposition et les différences avec la version actuelle,
  et n'appliquer la modification qu'après validation explicite.
- **FR-045**: Si Jira ne répond pas ou renvoie une erreur, la recherche DOIT tout de même afficher
  les résultats spec-writer et signaler l'indisponibilité de Jira.
- **FR-046**: Une modification validée d'un élément issu de Jira DOIT être enregistrée d'abord
  dans spec-writer, sous forme de spécification liée au ticket ; le ticket Jira NE DOIT PAS être
  modifié à cette étape.
- **FR-047**: L'utilisateur DOIT pouvoir pousser vers Jira la mise à jour d'une spécification liée ;
  l'envoi DOIT être précédé d'un aperçu et d'une confirmation explicite.
- **FR-048**: Avant de pousser une mise à jour, le système DOIT vérifier si le ticket a été modifié
  dans Jira depuis sa dernière lecture ; si c'est le cas, il NE DOIT PAS écraser sans le choix
  explicite de l'utilisateur (écraser, reprendre la version Jira, fusionner avec l'aide de l'IA).

**Interface et accessibilité**

- **FR-027**: L'utilisateur interagit avec l'outil via une interface web locale ouverte dans son
  navigateur. Cette interface NE DOIT être accessible que depuis le poste lui-même, jamais depuis
  le réseau.
- **FR-027a**: L'interface DOIT être moderne et agréable à utiliser : mise en page aérée, retour
  visuel immédiat à chaque action, état d'avancement du questionnaire toujours visible, rendu
  lisible des scénarios d'acceptation (mise en évidence des mots-clés En tant que / Étant donné /
  Quand / Alors).
- **FR-028**: Les messages d'erreur DOIVENT indiquer ce qui s'est passé et l'action à mener, sans
  trace technique brute.
- **FR-029**: L'interface DOIT respecter le niveau WCAG 2.1 AA et être entièrement utilisable au
  clavier.

### Key Entities

- **Session** : un travail de spécification en cours ou terminé. Attributs : nom, date de
  création, date de dernière modification, état (questionnaire en cours, généré), langue.
  Contient un besoin, des questions/réponses et des versions de spécification.
- **Besoin initial** : texte libre saisi par l'utilisateur, point de départ de la session.
- **Question** : élément du questionnaire. Attributs : thème (acteurs, règles, erreurs…),
  libellé, justification, réponses suggérées, valeur par défaut, dépendances vers d'autres
  questions ou réponses.
- **Réponse** : réponse de l'utilisateur à une question, ou valeur par défaut retenue si la
  question a été passée (dans ce cas marquée comme hypothèse).
- **Spécification** : version datée et numérotée du résultat de génération pour une session.
  Contient use cases, scénarios, hypothèses, exigences fonctionnelles et techniques.
- **Use case** : identifiant, titre, acteur, objectif, préconditions, déroulement nominal,
  alternatives ; référence vers sa source (passage du besoin ou réponse).
- **Scénario d'acceptation** : titre, acteur (« En tant que »), contexte facultatif (« Étant
  donné »), action (« Quand »), résultat attendu (« Alors ») ; référence vers son use case.
- **Configuration d'accès** : paramètres de connexion à un service externe. Pour GitHub Copilot :
  compte connecté, état de l'abonnement Copilot, jeton obtenu par autorisation (secret, conservé
  dans le trousseau). Pour Jira : adresse du serveur, jeton (secret, jamais restitué),
  projet par défaut, date du dernier test réussi.
- **Ticket Jira lié** : clé, lien, date de création ou d'import, version de spécification
  associée, date de dernière modification du ticket connue par l'outil (sert à détecter les
  modifications faites entre-temps dans Jira), date du dernier envoi vers Jira.
- **Hypothèse** : affirmation retenue par l'outil sans source explicite, à valider par
  l'utilisateur.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un utilisateur novice en rédaction de spécifications obtient une spécification complète pour un besoin
  simple (une fonctionnalité, un à trois acteurs) en moins de 15 minutes, du lancement de l'outil
  à l'export.
- **SC-002**: 100 % des scénarios générés sur le jeu de besoins de référence passent le contrôle de
  structure (acteur, action et résultat attendu présents).
- **SC-003**: 100 % des scénarios générés sont rattachés à un use case, et 100 % des use cases à une
  source (besoin ou réponse).
- **SC-004**: Sur le jeu de besoins de référence, au plus 10 % des questions posées sont jugées
  redondantes ou hors sujet par un relecteur.
- **SC-005**: 90 % des utilisateurs de test terminent le parcours P1 au premier essai sans aide.
- **SC-006**: Après une fermeture brutale, 100 % des réponses validées sont retrouvées à la reprise.
- **SC-007**: Le passage d'une question à la suivante prend moins de 3 secondes dans 95 % des cas.
- **SC-008**: Aucune donnée saisie par l'utilisateur ne quitte le poste sans accord explicite
  préalable, vérifié par un test de surveillance des sorties réseau.
- **SC-009**: Aucun jeton n'est retrouvé en clair dans les fichiers de l'outil, ses journaux ou ses
  exports, vérifié par une recherche automatisée après un parcours complet.

## Assumptions

- Un seul utilisateur, sur son propre poste : pas de comptes, pas de partage, pas de
  collaboration simultanée (hors périmètre de cette version).
- La confidentialité repose sur les protections du système d'exploitation : session utilisateur
  et droits sur les fichiers pour les sessions et spécifications, trousseau de clés du système
  pour les jetons. Aucun chiffrement applicatif supplémentaire n'est requis en v1.
- Les données sont conservées tant que l'utilisateur ne les supprime pas ; pas de purge
  automatique.
- Le français et l'anglais sont les deux langues prises en charge en v1 ; d'autres langues
  pourront suivre.
- Un besoin traité par session correspond à une fonctionnalité ; découper un besoin couvrant
  plusieurs fonctionnalités indépendantes en plusieurs sessions est à la charge de l'utilisateur.
- Hors périmètre v1 : import de documents existants (PDF), intégration avec d'autres outils de
  gestion de projet que Jira on premise, génération de code de test (step definitions).
- Jira on premise désigne Jira Server ou Data Center, accessible avec un jeton d'accès personnel.
- L'utilisateur dispose d'un compte GitHub Copilot actif qui lui donne accès aux modèles de
  langage. Le consentement de FR-024 porte sur l'envoi des données à ce service.
- Choix techniques imposés, à reprendre dans le plan : frontend en Angular. Il faut aussi
  identifier un skill de design d'interface pour obtenir une UI moderne (FR-027a).
- Les utilisateurs cibles savent décrire un besoin par écrit mais ne connaissent pas
  nécessairement la rédaction de use cases ou de scénarios d'acceptation.
