# Data Model: Périmètre des modules touchés (013)

## Domaine (`domain/sources`, classes pures)

### SourceRoot

Une racine de sources d'un projet (R1).

| Champ | Type | Règle |
|---|---|---|
| `id` | UUID | |
| `projectId` | UUID | Projet existant. Au plus `MAX_PER_PROJECT = 10` racines par projet. |
| `location` | String | Chemin absolu, 1 à 2000 caractères. Doit exister, être un dossier, lisible et traversable (mêmes contrôles que `DocSource` FOLDER). |
| `label` | String | Nom du dossier, dérivé de `location`. Affiché dans les analyses. |
| `exclusions` | `ExclusionPatterns` | Réutilisé de la 006 : au plus 50 motifs de 200 caractères, sans chemin absolu ni `..`. |
| `createdAt` | Instant | `ClockPort`. |

Une même `location`, après normalisation, ne peut être déclarée qu'une fois par projet. Une
racine imbriquée dans une autre racine du projet est refusée, pour éviter de compter les
occurrences en double.

### SourceExclusions

Règles fixes (R3), qui s'appliquent avant toute autre :

- `ignoredDirectory(name)` : segment qui commence par `.`, ou nom parmi `target`,
  `node_modules`, `build`, `dist`, `out`, `bin`, `coverage`, `__pycache__`, `vendor`.
- `sensitive(fileName)` : fichiers sensibles listés en R3.

### GitIgnoreRules

Règles d'un `.gitignore`, rattachées au dossier qui le contient (R3).

- `GitIgnoreRules.parse(String baseDir, String content)` lit les motifs. Il ignore les lignes
  vides et les commentaires, et traite les échappements `\#` et `\!`.
- `Match match(String relativePath, boolean directory)` renvoie `IGNORED`, `INCLUDED` (pour une
  négation) ou `NONE`.
- `GitIgnoreStack` empile les règles des dossiers parents. Le dernier motif qui correspond
  l'emporte, et un dossier ignoré coupe la descente.

### Module

| Champ | Type | Règle |
|---|---|---|
| `rootId` | UUID | |
| `path` | String | Relatif à la racine, séparateur `/`. Vide pour le pseudo-module de la racine. |
| `name` | String | Nom tiré du descripteur, sinon nom du dossier (R4). 1 à 200 caractères. |
| `kind` | `ModuleKind` | `MAVEN`, `NPM`, `HELM` ou `ROOT`. |
| `coordinates` | String? | `groupId:artifactId` (MAVEN seulement). |

L'identité d'un module est `rootId + path`. On l'écrit `ModuleId`, et sa forme texte est
`<rootId>/<path>`.

### ModuleDescriptor (MAVEN)

`coordinates`, `parent?`, `dependencies: Set<String>`, `bomImports: Set<String>`,
`aggregated: List<String>` (chemins relatifs de `<modules>`), `unresolved: int`.

### ModuleGraph

- `ModuleGraph.of(Map<ModuleId, ModuleDescriptor>)` construit les arêtes `X → Y` (X dépend de
  Y) à partir des dépendances, du parent et des imports de BOM, sans les coordonnées hors de
  l'arbre.
- `Map<ModuleId, List<ModuleId>> dependents(Set<ModuleId> seeds)` fait un parcours en largeur
  inverse. Il renvoie la chaîne la plus courte de chaque dépendant, depuis la graine, et tolère
  les cycles.
- `Set<ModuleId> aggregators(Set<ModuleId>)` renvoie les poms qui listent un module trouvé dans
  `<modules>`.

### PerimeterTerms

Les termes de 1 à 10, de 2 à 100 caractères chacun, débarrassés des espaces en tête et en fin,
sans doublon (casse ignorée). Un terme hors limites est retiré, et le retrait est signalé dans
l'analyse. Si aucun terme valide ne reste, l'analyse est refusée.

### PerimeterAnalysis

| Champ | Type | Règle |
|---|---|---|
| `ordinal` | int | 1 à 5, partagé avec les `SearchStep` du message. |
| `terms` | List<String> | Les `PerimeterTerms` retenus. |
| `rejectedTerms` | List<String> | Termes retirés, avec leur raison. |
| `roots` | List<RootStatus> | `{rootId, label, state: SCANNED, MISSING ou UNREADABLE}`. |
| `state` | `COMPLETE` ou `INCOMPLETE` | |
| `incompleteReason` | enum? | `MAX_FILES`, `MAX_DURATION`, `ROOT_MISSING`, `MAX_MODULES` ou `CANCELLED`. |
| `filesScanned` | int | |
| `filesSkipped` | int | Fichiers trop gros ou binaires. Les fichiers trop gros sont nommés, 20 au plus. |
| `durationMs` | long | |
| `modules` | List<PerimeterModule> | **Jamais filtrée** (FR-014). Triée par raison (directe, puis dépendance, puis architecture), puis par occurrences décroissantes, puis par chemin. |
| `architectureOnly` | List<ArchLink> | Éléments du modèle reliés, sans module correspondant. |

Au-delà de 2 000 modules, l'analyse est `INCOMPLETE (MAX_MODULES)`. Cette limite est une
protection de l'affichage.

### PerimeterModule

| Champ | Type | Règle |
|---|---|---|
| `module` | Module | |
| `reasons` | List<Reason> | Au moins une. Une seule ligne par module (FR-020). |
| `occurrences` | int | Total exact, tous termes confondus. |
| `samples` | List<Occurrence> | Au plus 20 par terme. |
| `qualification` | `Qualification` | `UNQUALIFIED` au départ. |
| `justification` | String? | 300 caractères au plus. |
| `isNew` | boolean | Absent de l'analyse précédente de la conversation (FR-015). |
| `archStatus` | `MATCHED <élément>` ou `ABSENT` | US4. Seuls les modules MAVEN, NPM et HELM sont concernés. |

**Reason** (union) :
- `Term(term, count)` ;
- `Dependency(chain: List<ModuleId>)`, avec une chaîne `graine → … → module` ;
- `Aggregator(of: ModuleId)` ;
- `Architecture(element, relatedElement, description, technology)`.

**Qualification** : `IMPACTED`, `TO_CHECK`, `NOT_IMPACTED`, `UNQUALIFIED`.

Règles de qualification (R7) :
- Un module sans raison `Term` ne peut pas être `IMPACTED`. Il est ramené à `TO_CHECK`.
- Un module que le LLM n'a pas qualifié reste `UNQUALIFIED`.
- Avant qualification, un module trouvé seulement par propagation part en `TO_CHECK`.

### Occurrence

`path` (relatif à la racine), `line` (à partir de 1), `term`, et `excerpt`. L'extrait fait au
plus 200 caractères, centré sur le terme, avec les espaces de tête retirés et les caractères de
contrôle remplacés.

### Évolutions existantes

- `ArchElement` reçoit un champ `moduleHint`, nullable (R8).
- `ConversationLlm.Action` reçoit `PERIMETER`. `Plan` reçoit `terms`.
- `ConversationLlm.qualify(QualifyRequest)` renvoie `List<ModuleQualification>`.
- `SourceKind` reçoit `MODULE`, une source citable de la réponse (R7).
- `TurnEvents.Event` reçoit `Perimeter(PerimeterAnalysis)`.

## Ports (`domain/port`)

- `SourceRootRepository` : `save`, `find(projectId, id)`, `list(projectId)`, `delete`.
- `SourceTree` :
  - `Preview preview(SourceRoot, Limits)` renvoie les modules détectés, le nombre de fichiers
    retenus, exclus et sensibles écartés, `limitReached` et `problem` ;
  - `Scan search(List<SourceRoot>, PerimeterTerms, Limits, CancelToken)` renvoie les modules,
    les descripteurs, les occurrences et l'état par racine.
- `PerimeterRepository` :
  - `save(messageId, PerimeterAnalysis)` ;
  - `update(messageId, ordinal, qualifications)` ;
  - `list(messageId)` ;
  - `last(conversationId)` ;
  - `snapshot(sessionId)` et `saveSnapshot(sessionId, PerimeterSnapshot)`.

## Application

- `ManageSourceRoots` : `list`, `add(projectId, location, exclusions)`,
  `setExclusions`, `preview(projectId, location, exclusions)` et `delete`. Il n'y a ni
  indexation ni appel au LLM.
- `AnalyzePerimeter` :
  - `analyze(projectId, terms, previous?)` fait le recensement, la propagation Maven, puis la
    propagation par l'architecture, et marque `isNew` ;
  - `qualify(analysis, question, exchanges)` appelle le LLM et applique les règles de
    qualification.
- `Converse` : action `PERIMETER` (R6). Le tour publie `Perimeter` deux fois, puis ajoute les
  sources `MODULE`.
- `StartFromConversation` : copie `last(conversationId)` dans l'instantané de la session (R11).
- `GenerateSpecification` (et la révision) : ajoute les `Assumption` `TOOL_INFERENCE` des
  modules à vérifier. Le rendu reçoit l'instantané.

## Persistance (V24)

```text
V24__source_roots_perimeter.sql
  project_source_root(id PK, project_id FK→project CASCADE, location TEXT 1..2000,
                      exclusions TEXT NULL (JSON), created_at TEXT,
                      UNIQUE(project_id, location))
  perimeter_analysis(id PK, message_id FK→conversation_message CASCADE, ordinal INTEGER 1..5,
                     terms TEXT (JSON), rejected_terms TEXT (JSON), roots TEXT (JSON),
                     state TEXT CHECK IN ('COMPLETE','INCOMPLETE'), incomplete_reason TEXT NULL,
                     files_scanned INTEGER, files_skipped INTEGER, skipped_examples TEXT (JSON),
                     duration_ms INTEGER, architecture_only TEXT (JSON),
                     UNIQUE(message_id, ordinal))
  perimeter_module(id PK, analysis_id FK CASCADE, position INTEGER, root_id TEXT, path TEXT,
                   name TEXT 1..200, kind TEXT CHECK IN ('MAVEN','NPM','HELM','ROOT'),
                   occurrences INTEGER, reasons TEXT (JSON),
                   qualification TEXT CHECK IN ('IMPACTED','TO_CHECK','NOT_IMPACTED','UNQUALIFIED'),
                   justification TEXT NULL CHECK (length <= 300), is_new INTEGER 0|1,
                   arch_element TEXT NULL)
  perimeter_occurrence(module_id FK CASCADE, position INTEGER, path TEXT, line INTEGER,
                       term TEXT, excerpt TEXT CHECK (length <= 200), PK(module_id, position))
  session_perimeter(session_id PK FK→session CASCADE, snapshot TEXT (JSON), taken_at TEXT)
  ALTER TABLE knowledge_item ADD COLUMN module_hint TEXT NULL
```

`root_id` n'est pas une clé étrangère : une analyse doit rester lisible après la suppression de
sa racine. Le libellé de la racine est conservé dans `perimeter_analysis.roots`.

`conversation_source.kind` n'a pas de contrainte CHECK (V18) : `MODULE` s'y enregistre sans
migration.

## Transitions

```text
PerimeterModule.qualification :
  UNQUALIFIED ──qualify(IMPACTED, avec Term)──▶ IMPACTED
  UNQUALIFIED ──qualify(IMPACTED, sans Term)──▶ TO_CHECK
  UNQUALIFIED ──qualify(TO_CHECK | NOT_IMPACTED)──▶ idem
  UNQUALIFIED ──(absent de la réponse / LLM en échec)──▶ UNQUALIFIED
PerimeterAnalysis.state : fixé à la fin du recensement, jamais modifié par la qualification.
```

Si la qualification échoue (LLM indisponible), l'analyse reste affichée sans qualification, et
la réponse le dit. Le recensement n'est jamais perdu.
