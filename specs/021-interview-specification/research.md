# Research: Interview d'une spécification avec les sachants

Conception validée avec le mainteneur :
`docs/superpowers/specs/2026-11-20-interview-specification-design.md` (approche B). Les décisions
ci-dessous portent sur la façon de la construire dans le code existant.

## R1. Rattachement des points dans le format de la spécification

**Décision** : un record `Anchor(String element, Integer step, boolean alternative)` ajouté,
facultatif, à `Assumption`, `SourceDeviation` et au nouveau `OpenQuestion`.
- `element` : code d'un élément de la même version : `UC-01`, `SC-01-02`, `EF-03`, `ET-01`, ou
  `SUMMARY`.
- `step` : pour un use case seulement, le numéro (1 à n) de l'étape du déroulement nominal, ou de
  l'alternative si `alternative` est vrai ; nul pour viser le use case entier.

Côté modèle, la sortie (`DraftSpecification`) porte un `DraftAnchor(target, index, step,
alternative)` par **positions** (`target` ∈ `USE_CASE`, `SCENARIO`, `REQUIREMENT`, `SUMMARY` ;
`index` = position dans la liste de la sortie), car les codes sont attribués après coup par
`IdentifierAssigner`, qui traduit la position en code. Une ancre qui ne désigne rien (position ou
étape hors limites, cible inconnue) est retirée par le code et le point reste non rattaché
(FR-020), sans violation bloquante.

**Pourquoi** : les codes n'existent pas encore quand le modèle rédige ; les positions sont ce
qu'il voit. `IdentifierAssigner` et le schéma de sortie (`LlmSchemas`) sont l'endroit où tous les
autres renvois (scénario → use case par `useCaseIndex`) sont déjà résolus de cette façon.

**Alternatives** : faire citer les codes au modèle (il devrait prédire la numérotation : fragile) ;
rattacher par texte cité (ambigu quand deux étapes se ressemblent).

**Compatibilité** : les versions sont stockées en JSON (Jackson). Les champs absents se lisent
nuls ; les constructeurs compacts les normalisent (ancre nulle, liste vide). Aucune migration de
données.

## R2. Le point ouvert

**Décision** : record `OpenQuestion(String code, String question, String owner, LocalDate due,
Anchor anchor, String fromCode)` dans `domain/specification`, liste `openQuestions` de
`SpecificationContent` (vide par défaut). Codes `Q-01`… attribués par `IdentifierAssigner`.
`MarkdownRenderer` ajoute la rubrique « Points ouverts » (« Open questions » en anglais) après
« Hypothèses », omise si vide ; l'export Jira passant par le Markdown, il en hérite.

Bornes : question ≤ 1 000 caractères, responsable ≤ 120, 50 points ouverts au plus par version.

## R3. Garantie : points tranchés retirés, points reportés ajoutés (FR-015)

La révision fait réécrire toute la spécification par le modèle, puis réattribue les codes par
position : `H-03` de la nouvelle version n'est pas forcément `H-03` de la version relue.

**Décision** :
1. La sortie de révision porte, pour chaque hypothèse et point ouvert repris de la version de
   base, `fromCode` (son code dans la base) ; les consignes de révision l'exigent.
2. Après validation, un post-traitement déterministe, `InterviewOutcome.apply(content, decisions)`
   (domaine, sans modèle) :
   - retire toute hypothèse et tout point ouvert dont `fromCode` est tranché en séance
     (confirmé, à modifier, à retirer) ;
   - ajoute un `OpenQuestion` pour chaque point reporté de la séance (texte, responsable,
     échéance, ancre traduite vers le code équivalent de la nouvelle version quand il existe),
     sauf s'il y figure déjà par `fromCode` ;
   - renumérote les codes `H-` et `Q-`.
3. Un écart entre sources tranché en séance est retiré de la même façon. Les écarts gagnent pour
   cela un code (`EC-01`…) et `fromCode` ; dans les versions antérieures, sans code, il est
   attribué à la lecture par position.

**Risque résiduel** : le modèle réécrit une hypothèse tranchée sans `fromCode`. Mesuré par
l'évaluation (critère « points tranchés retirés »), et visible au diff avant « Appliquer ».

**Alternatives** : rapprochement par similarité de texte (non déterministe) ; interdire au modèle
de toucher aux hypothèses (il doit pouvoir les reformuler et en créer).

## R4. Transport de la séance vers la révision (011)

**Décision** : la révision d'une spécification de la séance réutilise `ProposeRevision` :
1. si la spécification n'a pas de discussion (`specification_discussion`), elle est créée par le
   chemin existant de `DiscussSpecification` ;
2. les points décidés de cette spécification entrent dans la discussion comme **un seul message de
   l'utilisateur**, rédigé par le code (« Séance du 20/11/2026 avec Julie M., Paul D. : … » puis
   une ligne par point : élément, issue, texte), marqué de l'identifiant de séance
   (`conversation_message.interview_id`) ;
3. `ProposeRevision.propose(conversationId, List.of(), outcome)` rédige ; `outcome` (les
   décisions) est appliqué par R3 avant de déposer la proposition ;
4. la relecture en diff et « Appliquer » sont ceux de la discussion
   (`DiscussionProposalResource`), sans changement d'écran.

La limite de `ProposeRevision` (20 changements de 1 000 caractères) vise les cases cochées par
l'utilisateur ; le message de séance ne passe pas par `retained()` et a sa propre borne (200
points de 1 000 caractères, soit le plafond d'un message de discussion relevé si nécessaire).

`ProposeRevision.title(...)` reconnaît un message de séance et l'écrit « Séance du 20/11/2026
(Julie M., Paul D.) » : c'est ce que la spécification cite comme source (FR-017, sorte
`DISCUSSION` existante).

**Alternatives** : nouveau cas d'usage de révision dédié (dupliquerait le tour, le flux, la
proposition et la relecture) ; changements cochés (limite de 20, et perte de l'ancrage).

## R5. Persistance de la séance

**Décision** : migration `V30__interview.sql`.
- `interview` : id, état (`PREPARED`, `IN_PROGRESS`, `FINISHED`), participants (JSON, ≤ 20 noms de
  ≤ 120 caractères), dates de création, début et fin, curseur (spécification et élément courants).
- `interview_spec` : interview, session (spécification), rang, version relue (fixée au démarrage),
  points écartés (JSON de codes), révision appliquée (numéro de version, nul sinon).
- `interview_point` : id, interview, session, ancre (élément, étape, alternative), origine
  (`ASSUMPTION`, `SOURCE_DEVIATION`, `OPEN_QUESTION`, `REMARK`, `QUESTION`), code d'origine (pour
  les trois premières), texte (≤ 5 000), issue (`CONFIRMED`, `CHANGE`, `REMOVE`, `DEFERRED`, nulle si
  non traité), responsable, échéance, source désignée (sorte et référence), dates.
- Une seule séance `IN_PROGRESS` par spécification : vérifiée par le cas d'usage dans une
  transaction, et par un index unique partiel sur `interview_spec(session_id)` limité aux séances
  en cours (colonne dénormalisée `in_progress`).
- Suppression d'une spécification : ses lignes `interview_spec` et `interview_point` sont
  supprimées (cascade) ; une séance préparée sans spécification est supprimée.

Chaque point est enregistré à la saisie (FR-006) : une requête par point, sans lot.

## R6. Écran de présentation

**Décision** : route `/seances/:id`, chargée à la demande, avec `data: { presentation: true }`.
- Le shell (`app.ts`) masque le rail (menu, marque) et l'avis de projet quand la route active
  porte `presentation` ; le lien d'évitement mène directement au contenu.
- `AppEventsService` ne montre aucune notification du navigateur tant qu'une route de
  présentation est active (FR-002, scénario 7 de US1) ; les événements sont gardés et rien n'est
  rejoué après.
- Typographie de présentation : taille de base relevée (1.25rem), contraste AA, focus visible.
- Clavier : ↑ ↓ élément, ← → use case, `N` prochain point, `R` remarque, `Q` question, `Entrée`
  répondre, `Échap` fermer la saisie ; raccourcis désactivés quand un champ a le focus. Liste des
  raccourcis accessible (`?`).

**Alternatives** : mode plein écran du navigateur (API Fullscreen : refusée sans geste, et sort
au moindre `Échap`) ; fenêtre séparée (ouverture bloquée, jeton de session à partager).

## R7. Compte rendu

**Décision** : construit côté serveur par le domaine (`InterviewReport`), sans modèle, en deux
formes : HTML (titres `h2`/`h3`, listes, texte échappé) et texte brut (puces `•`). Le front copie
les deux d'un geste avec `navigator.clipboard.write([new ClipboardItem({'text/html', 'text/plain'})])`.
Si le presse-papiers est refusé, le texte s'affiche dans une zone sélectionnée (repli clavier).

Contenu, dans l'ordre : titre (« Point sur « … » — 20/11/2026, avec … »), puis par spécification :
décisions (code de l'élément, issue, texte), à vérifier (texte, responsable, échéance), sources à
mettre à jour (titre de la source, texte de la décision). « Aucun point noté » si vide.

## R8. « À reporter »

**Décision** : table `source_update` (même migration V30) : id, session, point d'origine,
sorte et référence de la source, titre, empreinte au moment de la décision, texte de la décision,
état (`OPEN`, `CLOSED`), dates.
- **Création** : à « Appliquer » d'une proposition issue d'une séance (la proposition garde
  l'identifiant de séance), pour chaque point `CHANGE` ou `REMOVE` dont l'élément est un use case
  (ou une étape) cité à une source de sorte `DOC_PAGE`, `JIRA_ISSUE` ou `ARCH_ELEMENT` (hors
  documents joints), pour chaque écart tranché (sa référence), et pour chaque source désignée à la
  main.
- **Empreinte** : nouvelle méthode du port `KnowledgeIndex.fingerprint(projectId, kind, ref)` :
  empreinte des empreintes de contenu des extraits de cette référence, triées ; vide si la source
  n'est plus indexée.
- **« Source modifiée, à vérifier »** : calculé à la lecture en comparant à l'empreinte courante.
  Aucun changement dans la synchronisation (017).
- **Affichage** : bloc « Sources à mettre à jour » sur la page de la spécification, et
  surimpression dans la rubrique « Écarts entre sources » ; jamais écrit dans une version.
- Aucun appel d'écriture vers Confluence, OneDrive, SharePoint ou Jira (FR-030) : vérifié par un
  test qui compte les appels des clients.

## R9. Points à traiter et page « Spécifications »

**Décision** : `SessionSummary` gagne `pending` (nombre d'hypothèses, d'écarts et de points
ouverts de la dernière version, moins ceux déjà tranchés dans une séance terminée dont la révision
n'est pas appliquée, FR-025). Calculé à la lecture de la liste depuis le JSON de la dernière
version (les tailles de listes), sans colonne dénormalisée : quelques dizaines de spécifications
par projet.

La sélection, le filtre et le bouton « Préparer une séance (n) » sont dans `sessions.page.ts` ; la
préparation est un écran (`/seances/:id/preparation`) avec le shell normal.

## R10. Consignes et évaluation

**Décision** : les consignes `generate-specification`, `revise-specification`,
`edit-specification`, `structure-imported-text` et `merge-conflict` décrivent l'ancre ; seule
`revise-specification` produit des points ouverts et `fromCode`. Le schéma de sortie
(`LlmSchemas`) gagne `anchor`, `openQuestions` et `fromCode`.

Évaluation (`ci/check-eval.sh`, golden set) : nouveaux critères
- `anchoredRate` ≥ 0,90 : part des hypothèses rattachées (SC-007) ;
- `decidedRemoved` = 1,0 sur un cas de révision de séance (SC-004) ;
- les critères existants ne baissent pas (SC-008).

Les rejeux E2E (`e2e/fixtures/llm`) sont complétés pour les nouvelles sorties ; l'empreinte
d'évaluation change avec les consignes, et l'évaluation est relancée en `live` par le mainteneur
avant la fusion.

## R11. Version qui change pendant une séance

**Décision** : `interview_spec.version` est fixée au démarrage. À la reprise ou à « Terminer », si
la dernière version est plus récente, l'écran le signale. Les points gardent leur ancre par code ;
la révision part de la dernière version, et un point dont l'élément n'y existe plus est transmis
sans ancre (« Non rattachés »).

## R12. Copilot indisponible

La séance, la préparation et le compte rendu n'appellent pas le modèle. Seule la révision exige le
consentement Copilot (contrôle existant de `ProposeRevision`) ; le récapitulatif affiche alors la
raison et la marche à suivre, et la révision reste proposable plus tard (FR-014).
