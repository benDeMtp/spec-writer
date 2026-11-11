# Research: Périmètre des modules touchés (013)

Ce document fait l'inventaire de l'existant, puis présente les décisions. Chaque décision donne
son motif et les alternatives écartées.

## Existant

- **Conversation** (`application/conversation/Converse.java`, feature 010) :
  - Boucle d'étapes. `llm.plan()` (prompt `converse-plan`, schéma strict `CONVERSE_PLAN`) répond
    `SEARCH {query, kinds}` ou `ANSWER`.
  - Au plus `SearchStep.MAX_STEPS = 5` recherches, puis `converse-answer` en flux, avec des
    citations `[n]` résolues par `Citations.resolve`.
  - Chaque étape est enregistrée (`conversation_step`, `ordinal CHECK 1..5`, V18) et publiée sur
    le flux SSE (`TurnEvents.Event.Step`, nom `step`).
  - Le front l'affiche dans `conversation-thread.component.ts` (`<details class="steps">`).
- **Outils de Copilot désactivés** : `CopilotRuntime.toolLessSession()` (aucun outil, permissions
  refusées), conformément à R1 de la 010.
- **Protection contre l'injection** : messages système et utilisateur séparés. Les blocs balisés
  sont échappés (`UserMessages.block`) et décrits comme données par la « Règle de sécurité » des
  prompts.
- **Rejeu du LLM** :
  - `ReplayChatModel` choisit le premier fichier `*.json` dont l'`operation` correspond et dont
    les fragments `userContains` figurent dans le message.
  - Dossiers : `e2e/fixtures/llm` en dev et en E2E, `src/test/resources/llm-replay` en test.
- **Sources de documentation** (feature 006) :
  - Table `project_doc_source` (V8, V15). Type `DOCUMENTS` avec des `exclusions` en JSON.
  - Classes : `DocSource`, `ExclusionPatterns` (glob validé, sans négation, `alwaysIgnored`
    écarte les segments commençant par `.` et les `~$*`).
  - `ManageDocSources.preview`, `ContextResource` `/doc-sources/preview`.
  - Lecture : `LocalDocumentFolder` (`walkFileTree`, fichiers réguliers seulement, liens non
    suivis) et `FolderDocumentFiles` (`toRealPath` et `startsWith(root)`).
  - Ajouter une source déclenche une indexation complète. **Ce comportement ne convient pas aux
    sources de code** (FR-007).
- **Architecture** :
  - Les relations sont déjà stockées (`arch_relation`, V10) et lues par
    `KnowledgeIndex.architecture(projectId)`, qui renvoie un `ArchitectureModel`.
  - `ArchRelation.links(a, b)` couvre les deux sens et les descendants.
  - Aucune requête par élément : le filtrage se fait en mémoire (`FeatureKnowledge`).
- **Spécification** :
  - `MarkdownRenderer.render(spec, language, KnowledgeSelection)` produit la rubrique
    « Architecture » (« Modules impactés », « Flux ») **à partir de faits calculés, jamais du LLM**
    (FR-039 à FR-041 de la 002).
  - `Assumption.Origin.TOOL_INFERENCE` existe.
- **Brouillon depuis la conversation** :
  - `DraftNeed` (prompt `propose-need`) produit le besoin et les points.
  - `StartFromConversation` crée la session et la relie à la conversation (`conversations.link`).
- **Consentement** : `ConsentPolicy.requireGranted(COPILOT)` est déjà appelé par `Converse`. Aucun
  texte ne parle du code source.
- **Dépendances** : pas de JGit, pas de bibliothèque glob ou gitignore, pas de ripgrep.
  `structurizr-dsl`, `jsoup` et `pdfbox` sont présents.
- **Dernière migration** : `V23__jira_default_key_optional.sql`.

## R1. Stockage des racines de sources

**Décision** : une nouvelle table `project_source_root` (V24), un enregistrement de domaine
`SourceRoot` et un cas d'usage `ManageSourceRoots`. Le type `DocSource` n'est pas réutilisé.

Les exclusions reprennent `ExclusionPatterns` (mêmes règles et même validation que la 006,
FR-002). Une racine se déclare par son chemin absolu, et un projet en a au plus 10.

**Motif** : une `DocSource` est indexée dès son ajout, sa liste est servie aux agents MCP et elle
alimente les compteurs de connaissance. Une racine de sources n'est jamais indexée (FR-007). La
mêler aux sources de documentation obligerait à des exceptions partout.

**Alternatives écartées** :
- Un type `SOURCES` dans `project_doc_source` : la contrainte CHECK et une reconstruction de table
  sont nécessaires, et il faut des exceptions dans l'indexation, la réindexation par source (005)
  et les compteurs.

## R2. Parcours et recherche

**Décision** : Java pur, dans un adaptateur `LocalSourceTree` (`infrastructure/sources`).

- **Parcours** : `Files.walkFileTree` sans `FOLLOW_LINKS`, fichiers réguliers seulement. Les
  liens symboliques ne sont jamais suivis, et chaque chemin est vérifié sous la racine réelle
  (`toRealPath`). On reprend le schéma de `LocalDocumentFolder`, avec un prédicat de fichier
  propre au code.
- **Une seule passe** sur l'arbre pour tous les termes. Chaque fichier est lu en UTF-8, avec un
  repli ISO-8859-1 si le décodage échoue. Il est ignoré :
  - s'il contient un octet nul dans ses 8 premiers Ko (fichier binaire) ;
  - s'il dépasse 1 Mo (il est alors compté et nommé).
- **Correspondance** : littérale et **insensible à la casse**, ligne par ligne, avec
  `indexOf` sur la ligne en minuscules (`Locale.ROOT`).
  - L'exhaustivité prime : `contractStatus` doit trouver `ContractStatus`.
  - Les caractères spéciaux d'un terme n'ont aucun sens (FR « termes cherchés littéralement »).
- **Parallélisme** : les fichiers sont lus par un exécuteur à threads virtuels, borné par un
  sémaphore (16 lectures). Les résultats sont triés à la fin (racine, module, chemin, ligne) :
  le résultat est déterministe quel que soit l'ordre de lecture (FR-017, SC-007).
- **Limites** (`SourceTree.Limits`) :
  - 50 000 fichiers retenus par analyse ;
  - 120 s par analyse ;
  - 1 Mo par fichier ;
  - 10 termes, de 2 à 100 caractères chacun.

  Une limite atteinte arrête le parcours, et l'analyse est marquée `INCOMPLETE` avec sa raison
  (FR-016).

**Motif** : pas de nouvelle dépendance et pas de processus lancé (FR-022). Lire 20 000 fichiers
de taille moyenne (environ 200 Mo) prend quelques secondes en lecture parallèle sur SSD
(SC-003 : moins de 30 s).

**Alternatives écartées** :
- **ripgrep** : un binaire externe qu'il faudrait installer et lancer. FR-022 l'interdit.
- **Index de recherche** (FTS5) sur le code : il vieillit à chaque commit, et la spec l'exclut.
- **Aho-Corasick** : inutile pour 10 termes au plus. `indexOf` suffit.

## R3. Exclusions : gestionnaire de versions, fichiers générés, fichiers sensibles

**Décision** : trois filtres, appliqués dans cet ordre.

1. **Toujours exclus** (`domain/sources/SourceExclusions`, règles fixes, sans exception possible)
   :
   - Les segments commençant par `.`, comme dans `ExclusionPatterns.alwaysIgnored` : `.git`,
     `.idea`, `.env*`, `.npmrc`, etc.
   - Les dossiers générés : `target`, `node_modules`, `build`, `dist`, `out`, `bin`, `.angular`,
     `coverage`, `__pycache__`, `vendor`.
   - **Fichiers sensibles (FR-004)** :
     - `settings.xml`, `settings-security.xml` ;
     - `*.pem`, `*.key`, `*.p12`, `*.pfx`, `*.jks`, `*.keystore`, `*.truststore`, `*.kdbx` ;
     - `id_rsa*`, `id_ed25519*`, `*.ppk` ;
     - `credentials*`, `*secret*`, `*.tfvars`, `kubeconfig*`.

     Ces règles s'appliquent avant les motifs de l'utilisateur, qui ne peuvent donc pas les
     réintégrer : `ExclusionPatterns` n'a pas de négation.
2. **`.gitignore`** : `GitIgnoreRules` (domaine, classe pure) lit le `.gitignore` de chaque
   dossier parcouru, dossiers imbriqués compris, et applique la sémantique git :
   - motifs ancrés (`/x`) ou non ;
   - dossiers (`x/`) ;
   - `*`, `**`, `?`, classes `[...]` ;
   - négation `!` ;
   - le dernier motif qui correspond l'emporte ;
   - un dossier exclu n'est pas parcouru, et un `!` ne peut pas y réintégrer de fichier.

   `.git/info/exclude` et le fichier global de git ne sont pas lus.
3. **Motifs de l'utilisateur** : `ExclusionPatterns` de la racine.

**Motif** : la sémantique `.gitignore` tient en une centaine de lignes testables. JGit
apporterait plusieurs mégaoctets, une surface de CVE et un accès au dépôt dont on n'a pas besoin.

**Alternatives écartées** :
- **JGit** (`IgnoreNode`) : trop lourd pour ce besoin.
- **Ignorer `.gitignore`** : les dossiers générés non standards (`generated/`, `gen-src/`)
  pollueraient le périmètre.

## R4. Détection des modules

**Décision** : un module est un dossier qui contient un descripteur :

| Descripteur | Sorte | Nom du module |
|---|---|---|
| `pom.xml` | `MAVEN` | `artifactId` (repli : nom du dossier) |
| `package.json` hors `node_modules` | `NPM` | `name` (repli : nom du dossier) |
| `Chart.yaml` | `HELM` | `name:` (repli : nom du dossier) |

- Un fichier appartient au **module le plus profond** qui le contient.
- Un fichier hors de tout module appartient au pseudo-module de la racine, nommé d'après le
  dossier de la racine (cas limite de la spec).
- L'identifiant stable d'un module est `racine + chemin relatif`. Deux modules peuvent porter le
  même nom, d'où l'affichage `nom (chemin)` quand il y a ambiguïté.
- La détection se fait pendant le parcours, sans passe supplémentaire.
- L'aperçu des réglages fait un parcours **sans lecture** des fichiers (sauf les descripteurs),
  borné aux mêmes limites.

**Lecture des descripteurs** :
- `package.json` : avec Jackson, déjà présent.
- `Chart.yaml` : on lit seulement la ligne `name:` de premier niveau, ce qui évite une dépendance
  YAML.
- `pom.xml` : voir R5.

## R5. Graphe des dépendances Maven

**Décision** : `MavenPomReader` (infrastructure) lit chaque `pom.xml` avec le DOM du JDK, en
**sécurisant le parseur** : `disallow-doctype-decl`, pas d'entités externes ni de XInclude
(OWASP XXE). Il en tire un `ModuleDescriptor` :

- les coordonnées `groupId:artifactId`, avec un `groupId` hérité du `<parent>` s'il est absent ;
- le parent `groupId:artifactId` ;
- les dépendances `groupId:artifactId` de `<dependencies>`, y compris celles des profils ;
- les imports de BOM : `<dependencyManagement>` avec `<scope>import</scope>` ;
- les `<modules>` agrégés.

Les versions et les propriétés `${...}` ne sont pas résolues : seules les coordonnées comptent.
Une valeur contenant `${` est ignorée et comptée comme « non résolue ».

Le **graphe** (`domain/sources/ModuleGraph`, classe pure) contient une arête `X → Y` quand X
dépend de Y : dépendance, parent ou import de BOM. Une coordonnée hors de l'arbre est ignorée
(US3, scénario 3).

**Propagation** : un parcours en largeur inverse depuis les modules trouvés par les termes, avec
un ensemble des modules visités, ce qui gère les cycles (scénario 4).

- Chaque dépendant reçoit une raison `DEPENDENCY` portant la chaîne la plus courte
  (`A → B → C`) et le statut initial « à vérifier ».
- **Agrégateur** : un pom qui liste un module trouvé dans `<modules>` reçoit une raison
  `AGGREGATOR`, sans propagation plus loin. C'est le cas du projet `aggregates` de rv, à mettre à
  jour à l'ajout d'un module.

**Alternatives écartées** :
- **Maven Resolver** ou `mvn dependency:tree` : il faut le réseau, le dépôt local, ou un
  processus lancé (FR-022).
- **Résolution des propriétés** : coûteuse et inutile pour relier des modules de l'arbre, qui ont
  des coordonnées littérales dans l'immense majorité des cas.

## R6. Place de l'analyse dans la conversation

**Décision** : une troisième action du plan, `PERIMETER`, à côté de `SEARCH` et `ANSWER`.

- **Schéma `CONVERSE_PLAN`** :
  - `action` passe à l'énumération `SEARCH | PERIMETER | ANSWER` ;
  - un champ `terms` est ajouté : tableau de 1 à 10 chaînes de 2 à 100 caractères, ignoré pour
    les autres actions.
- **Prompt `converse-plan`** :
  - choisir `PERIMETER` quand la question porte sur l'impact, les modules, le périmètre ou les
    tâches techniques ;
  - proposer des identifiants techniques plutôt que des mots courants ;
  - la disponibilité est signalée par `<perimetre-disponible>oui|non</perimetre-disponible>`.
    Écart à l'implémentation : l'action reste proposée sans racine, pour qu'une question de
    périmètre mène la personne aux réglages plutôt qu'à une réponse sans le code.
- **Étapes** :
  - une analyse **consomme une étape** dans `MAX_STEPS` (toujours 5) ;
  - il y a **au plus une analyse par tour**, et une seconde demande du plan dans le même tour est
    traitée comme `ANSWER`, ce qui borne le coût ;
  - l'ordinal est partagé avec les recherches, ce qui conserve l'ordre d'affichage ;
  - `Converse` reçoit un nouveau collaborateur, `AnalyzePerimeter` (application).
- **Sans racine** : si le plan choisit `PERIMETER` alors que le projet n'a aucune racine,
  l'analyse n'a pas lieu. La réponse reçoit
  `<perimetre-indisponible>` et le front affiche le lien vers les réglages (US2, scénario 5).
- **Relance** (FR-015) :
  - le plan reçoit les termes de la **dernière analyse de la conversation**
    (`<dernier-perimetre>`), ce qui lui permet d'ajouter les termes demandés ;
  - chaque module de la nouvelle analyse est marqué `isNew` s'il était absent de la précédente.

**Motif** : on réutilise la boucle, le rejeu, le flux et l'affichage des étapes. La décision de
lancer l'analyse reste à l'agent, comme pour une recherche. L'exhaustivité vient de l'outil, pas
du plan.

**Alternatives écartées** :
- **Outil SDK Copilot** : écarté par R1 de la 010.
- **Analyse systématique à chaque question** : trop coûteux, et hors sujet pour la plupart des
  questions.
- **Bouton « Analyser le périmètre » hors conversation** : l'utilisateur a choisi la
  conversation.

## R7. Qualification par le LLM

**Décision** : un appel à part, après le recensement : le prompt `qualify-perimeter` avec le
schéma strict `PERIMETER_QUALIFICATION`.

```text
{ modules: [{ id, qualification: IMPACTED | TO_CHECK | NOT_IMPACTED, justification ≤ 300 }] }
```

**Entrée, balisée et échappée** :
- la question et l'échange récent ;
- les termes ;
- pour chaque module : son `id`, son nom, sa sorte, ses raisons (nombre d'occurrences par terme,
  chaîne de dépendance, relation d'architecture) et **au plus 5 extraits** de 200 caractères
  chacun.

Le volume total est borné à 40 000 caractères. Au-delà, les modules suivants sont envoyés sans
extrait. Ajout à l'implémentation : une limite ferme de 120 000 caractères. Au-delà, les modules
restants ne sont pas envoyés, et le message porte `<modules-non-transmis nombre="N"/>`. Ces
modules restent affichés, `UNQUALIFIED`. L'ordre est déterministe : impact direct d'abord, puis par nombre d'occurrences
décroissant.

**Règles côté outil** :
- Un `id` inconnu renvoyé par le LLM est ignoré.
- Un module absent de la réponse reste `UNQUALIFIED` (FR-013).
- Rien ne retire un module de l'analyse (FR-014) : la qualification n'est qu'un attribut.
- Un module trouvé uniquement par propagation ne peut pas être qualifié `IMPACTED` sans
  occurrence. Une telle réponse est rabaissée à `TO_CHECK`, ce qui garde la preuve cohérente.

**Réponse** :
- `converse-answer` reçoit l'analyse qualifiée comme sources : chaque module devient une source
  citable `MODULE`, avec `ref = module:<rootId>/<chemin>`.
- La réponse cite les modules par `[n]`.
- **Le texte des extraits n'est pas renvoyé à `converse-answer`** : seulement le nom, la
  qualification et la justification. Le volume reste borné.

**Motif** : séparer la qualification de la réponse rend la qualification structurée,
vérifiable, rejouable en E2E et évaluable (SC-006). La réponse en flux reste libre.

**Alternatives écartées** :
- **Qualification dans le texte de `converse-answer`** : non structurée, donc impossible de
  garantir la cohérence avec la liste.

## R8. Relations d'architecture (US4)

**Décision** : `AnalyzePerimeter` charge `KnowledgeIndex.architecture(projectId)`, une fois par
analyse.

**Correspondance module → élément** (dans cet ordre) :
1. une propriété Structurizr `spec-writer.module`, qui vaut le nom ou le chemin du module ;
2. sinon, l'égalité des noms normalisés : minuscules, `-`, `_`, `.` et espaces retirés, entre le
   nom du module et le nom court d'un conteneur ou d'un composant.

**Pour chaque module trouvé qui correspond à un élément** :
- les `ArchRelation` où l'élément figure (`links`, dans les deux sens) donnent les éléments
  reliés ;
- un élément relié qui correspond à un module de l'arbre ajoute ce module avec la raison
  `ARCHITECTURE` (relation, description et technologie) ;
- un élément sans module correspondant est listé « à vérifier » dans une liste
  `architectureOnly`, affichée à part : il n'y a pas de fichier à montrer.

Un module sans élément correspondant porte la mention « absent du modèle d'architecture »
(scénario 2). Cette propagation ne s'enchaîne pas : on ne prend que les relations directes.

Précisions à l'implémentation :
- seuls les modules trouvés par un terme propagent par l'architecture. Un module ajouté par
  dépendance ne propage pas, ce qui évite un second saut déguisé ;
- sans modèle d'architecture pour le projet, aucun module n'est dit « absent du modèle » ;
- le pseudo-module de la racine n'a pas de statut d'architecture ;
- un composant relié se rattache au module de son conteneur le plus proche.

**Motif** : propager sur plusieurs sauts par l'architecture noierait le périmètre. Une relation
directe suffit à signaler le consommateur ou le fournisseur.

**Stockage de l'indice** : `StructurizrReader` ne garde aujourd'hui que la propriété
d'appartenance (`OWNERSHIP`). Il lira aussi `spec-writer.module`, et `ArchElement` reçoit un champ
`moduleHint`. La valeur est stockée dans une colonne nullable `knowledge_item.module_hint`
(V24, `ALTER TABLE ... ADD COLUMN`, sans reconstruction) sur l'élément `chunk = 0`, puis relue
par `SqliteKnowledgeIndex.architecture()`.

Les modèles déjà indexés n'ont pas d'indice tant que la source d'architecture n'a pas été
relue. La correspondance par le nom s'applique alors.

## R9. Persistance de l'analyse

**Décision** : migration V24, dans le même fichier que les racines. Tables :

- `perimeter_analysis` :
  - `message_id` (FK `conversation_message`, cascade) et `ordinal` ;
  - `terms` (JSON) ;
  - `state` (`COMPLETE` / `INCOMPLETE`), `incomplete_reason` ;
  - `files_scanned`, `files_skipped` ;
  - `roots` (JSON : id, libellé, absente ou non) ;
  - `duration_ms`.
- `perimeter_module` :
  - `analysis_id` (FK cascade), `position` ;
  - `root_id`, `path`, `name`, `kind` (`MAVEN` / `NPM` / `HELM` / `ROOT`) ;
  - `occurrences` (total exact) ;
  - `reasons` (JSON : termes avec leurs nombres, chaînes, relations) ;
  - `qualification`, `justification`, `is_new`, `arch_status`.
- `perimeter_occurrence` :
  - `module_id` (FK cascade), `path`, `line`, `term` ;
  - `excerpt` (≤ 200 caractères) ;
  - au plus 20 échantillons par module et par terme.

La conversation garde ces données comme ses messages. Elles sont supprimées avec elle (cascade).
Aucun contenu de fichier n'est stocké, sauf les extraits bornés (FR-007).

La limite `conversation_step.ordinal CHECK 1..5` ne change pas : l'analyse a son propre ordinal,
dans le même espace que les recherches.

**Alternatives écartées** :
- Garder l'analyse seulement en mémoire (événements du tour) : l'historique de la conversation
  la perdrait au rechargement, et US5 ne pourrait pas la reprendre.

## R10. Flux SSE et affichage

**Décision** : un nouvel événement `TurnEvents.Event.Perimeter(PerimeterView)`, nommé
`perimeter`.

- Il est publié deux fois avec le même `ordinal` : après le recensement (`UNQUALIFIED` partout),
  puis après la qualification.
- Le front remplace l'analyse de même ordinal, ce qui rend l'événement idempotent, comme `step`.
- Le détail de la conversation (`GET /api/conversations/{cid}`) renvoie `perimeters` pour chaque
  message de l'agent.
- **Front** : un composant `sw-perimeter-step`, inséré dans la liste des étapes de
  `conversation-thread`, selon l'ordinal. Il contient :
  - l'en-tête : termes, racines, fichiers parcourus, durée, état ;
  - un résumé en texte : « 12 modules : 4 impactés, 6 à vérifier, 1 non impacté, 1 non
    qualifié » ;
  - un tableau des modules, groupés par qualification, chaque groupe en `<section>` titrée. Pour
    chaque module : nom, chemin, qualification en texte et en icône, justification, raisons, et
    un `<details>` des occurrences (`fichier:ligne`, extrait en `<code>`) ;
  - la liste `architectureOnly`.
- **Accessibilité** :
  - la qualification est écrite en texte, la couleur n'est qu'un complément ;
  - le tableau est un vrai `<table>` avec `<th scope>` ;
  - la fin de l'analyse est annoncée par `LiveAnnouncer`.

## R11. Spécification rédigée depuis la discussion (US5)

**Décision** : la rubrique « Modules touchés » est **rendue par l'outil, jamais par le LLM**,
comme la rubrique « Architecture » de la 002.

1. `StartFromConversation` copie la **dernière analyse** de la conversation dans un instantané
   rattaché à la session (`session_perimeter`, JSON en V24) : modules, qualifications,
   justifications, raisons et au plus 3 occurrences par module.
2. `MarkdownRenderer.render` reçoit l'instantané et écrit `## Modules touchés` (EN :
   `## Affected modules`, ancre `perimeter`) après « Architecture ».
   - Une sous-rubrique par qualification. Les modules non qualifiés vont avec ceux à vérifier.
   - Pour chaque module : nom, chemin, justification et preuves (`fichier:ligne` ou chaîne).
3. À la génération, chaque module `TO_CHECK` ou `UNQUALIFIED` devient une `Assumption`
   `TOOL_INFERENCE` : « Le module X est à vérifier : <raison> ». Elle est ajoutée après la
   réponse du LLM, puis numérotée par `IdentifierAssigner` (FR-021).
4. Sans analyse dans la conversation, il n'y a pas de rubrique (scénario 3).

Les prompts de génération ne changent pas, et le rendu reste reproductible (principe II).

**Alternatives écartées** :
- **Donner l'analyse au LLM de génération** : il pourrait omettre ou inventer un module, ce qui
  contredit FR-014 et le principe II.

## R12. Information de l'utilisateur et confidentialité

**Décision** :
- **Information** :
  - La section « Sources du code » des réglages affiche, en permanence : « Lors d'une analyse de
    périmètre, spec-writer envoie à GitHub Copilot les noms des modules et de courts extraits des
    lignes trouvées. Le code n'est ni copié ni indexé. » (FR-026)
  - Le consentement COPILOT existant reste exigé : `Converse` l'appelle déjà.
- **Journal** : on n'écrit que des compteurs (fichiers, modules, durée, état), jamais un chemin
  de fichier, un terme ou un extrait. Les termes peuvent décrire le projet, ils sont donc traités
  comme du contenu utilisateur (principe V).
- **Injection** : extraits et chemins sont transmis dans des blocs `<extrait>` échappés, sous la
  règle de sécurité. Un test de rejeu place une instruction dans un commentaire de code et
  vérifie que la liste des modules ne change pas.

## R13. Jeux d'essai, E2E et évaluation

**Arbre de démonstration** : `e2e/fixtures/sources/demo-rv/`, d'une quinzaine de modules.
- Un pom agrégateur, un parent et une BOM.
- `libs/common-contract` (définit `ContractStatus`).
- `libs/quarkus-contract` (dépend de common).
- `modules/facturation` (dépend de quarkus-contract), `modules/relance` (dépend de facturation).
- `modules/annuaire` (sans lien).
- `modules/batch-suspension` (contient `contract_suspended`, pour la relance avec un nouveau
  terme).
- `legacy/ancien` (contient le terme, pour le motif `legacy/**`).
- `front/` (`package.json`, utilise `CONTRACT_STATUS`).
- `charts/facturation` (`Chart.yaml`, `values.yaml` avec `contract.status`).
- Des pièges qui ne doivent jamais apparaître :
  - un `.env` et un `settings.xml` contenant le terme ;
  - `node_modules/` et `target/` contenant le terme ;
  - un dossier exclu par `.gitignore`, avec une négation `!` ;
  - un fichier binaire.
- Le lien symbolique vers l'extérieur est créé au démarrage du test, car les liens versionnés
  sont fragiles sous Windows.
- Un modèle C4 de démonstration (`e2e/fixtures/c4`, déjà présent, à compléter) relie
  `facturation` à `notification`.

**Rejeu du LLM** (`e2e/fixtures/llm` et `llm-replay`) :
- `converse-plan` → `PERIMETER` ;
- `qualify-perimeter`, avec une réponse qui **omet** un module, pour tester FR-014 ;
- `converse-answer`.

**Évaluation** : `PerimeterEval` (tag `eval`), avec des cas dans
`eval/perimeter/cases.json` : discussion, arbre et modules attendus.
- Métrique `PERIMETER_TERM_RECALL` : part des modules attendus retrouvés par les termes que
  propose le vrai plan. Seuil : 0,9 (SC-006).
- Métrique `PERIMETER_QUALIFICATION` : accord avec la qualification attendue.
- Les deux entrent dans `baseline.json`. **Le lancement réel se fait avec l'accord de
  l'utilisateur.**

**Mesure de performance** : un test `@Tag("perf")`, exclu par défaut, génère un arbre de
60 modules et 20 000 fichiers dans un dossier temporaire et vérifie SC-003.
