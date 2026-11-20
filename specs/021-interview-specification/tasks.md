---
description: "Tâches de la feature 021 : interview d'une spécification avec les sachants"
---

# Tasks: Interview d'une spécification avec les sachants

**Input**: `specs/021-interview-specification/`. Les documents de conception :

- plan.md ;
- spec.md (US1 à US6) ;
- research.md, R1 à R12 ;
- data-model.md ;
- contracts/rest-api.md, contracts/llm-output.md et contracts/ui.md ;
- quickstart.md, V1 à V6 ;
- la conception validée : `docs/superpowers/specs/2026-11-20-interview-specification-design.md`.

**Tests**: obligatoires (constitution III, TDD). Chaque test est écrit et vu échouer avant le code.

- JaCoCo ne compte pas les `@QuarkusTest`. `domain/interview`, `application/interview` et les
  ajouts de `domain/specification` sont donc couverts par des tests unitaires, avec des doubles de
  l'horloge, des identifiants, des dépôts et du LLM.
- Les règles ArchUnit se lancent sous Maven.
- Tout nouveau `@QuarkusTest` porte `@Isolated`.

**Évaluation LLM**: concernée.
- **US3 et US4** changent cinq consignes et le schéma de sortie, donc l'empreinte de
  `ci/check-eval.sh`.
- **Nouveaux critères** : `anchoredRate` ≥ 0,90 et `decidedRemoved` = 1,0. Les critères existants
  ne doivent pas reculer.
- **Évaluation `live`** : relancée par le mainteneur avant la fusion (T094).

**Organisation**:
- La phase 2 pose le format (ancre, point ouvert, code d'écart), le domaine de la séance, sa
  persistance et le mode présentation du shell.
- **US1, US2 et US3 (P1)** forment le MVP : une séance d'une spécification, son compte rendu et sa
  révision. Sans ancres, les points y sont présentés dans « Non rattachés ».
- **US4 (P2)** fait produire les ancres par le modèle.
- **US5 (P2)** ajoute la préparation et les séances de plusieurs spécifications.
- **US6 (P3)** ajoute les « à reporter ».

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallélisable (fichiers différents, aucune dépendance sur une tâche non finie).
- Abréviations des chemins :
  - `B/` = `backend/src/main/java/fr/kawamind/` ;
  - `BT/` = `backend/src/test/java/fr/kawamind/` ;
  - `R/` = `backend/src/main/resources/` ;
  - `F/` = `frontend/src/app/`.

---

## Phase 1: Setup

- [X] T001 Écrire la migration `R/db/migration/V30__interview.sql` (data-model.md) :
  - les tables `interview`, `interview_spec`, `interview_point` et `source_update`, avec leurs
    cascades vers `session` ;
  - l'index unique partiel `interview_spec(session_id) WHERE in_progress = 1` ;
  - la colonne nulle `conversation_message.interview_id`.

  Bornes à poser en `CHECK` :
  - `state IN ('PREPARED','IN_PROGRESS','FINISHED')` ;
  - `origin IN ('ASSUMPTION','SOURCE_DEVIATION','OPEN_QUESTION','REMARK','QUESTION')` ;
  - `outcome IN ('CONFIRMED','CHANGE','REMOVE','DEFERRED')` ou nul ;
  - `source_update.state IN ('OPEN','CLOSED')`.

  Vérifier ensuite qu'une base neuve migre jusqu'à 30, sans toucher aux migrations appliquées.
- [X] T002 [P] Pointer `CLAUDE.md` sur la feature en cours, `specs/021-interview-specification/`,
  en une phrase de contexte, comme pour les features précédentes.

---

## Phase 2: Foundational (bloque toutes les US)

### Format de la spécification (research.md R1, R2)

- [X] T003 [P] Test `BT/domain/specification/AnchorTest.java` :
  - une ancre valide contre une version : `UC-01` étape 2 sur un use case de 3 étapes,
    alternative 1, `SC-01-02`, `EF-03`, `SUMMARY` ;
  - une ancre invalide : code inconnu, étape 0 ou 4 sur 3 étapes, étape sur un scénario.
- [X] T004 [P] Implémenter `B/domain/specification/Anchor.java` :
  - `record Anchor(String element, Integer step, boolean alternative)` ;
  - `boolean resolves(SpecificationContent)` et `static Anchor orNull(Anchor, SpecificationContent)`.
- [X] T005 [P] Test `BT/domain/specification/OpenQuestionTest.java`, sur les bornes :
  - « question 1 à 1 000 caractères, owner ≤ 120, 50 au plus par version » ;
  - une question vide est refusée par `DomainException` `VALIDATION_FAILED`.
- [X] T006 Implémenter `B/domain/specification/OpenQuestion.java` :
  - `record OpenQuestion(String code, String question, String owner, LocalDate due, Anchor anchor,
    String fromCode)` ;
  - ajouter `openQuestions` à `B/domain/specification/SpecificationContent.java` (liste vide si nulle,
    `MAX_OPEN_QUESTIONS = 50`), et mettre à jour tous les appels du constructeur.
- [X] T007 Test `BT/domain/specification/SpecificationContentCompatibilityTest.java` : un JSON de
  version antérieure (sans `openQuestions`, `anchor`, `fromCode`, ni code d'écart) se lit par le
  `JsonMapper` de `SpecificationJpaRepository`. On obtient alors :
  - des listes vides et des ancres nulles ;
  - les écarts numérotés `EC-01`… par position.
- [X] T008 Ajouter `anchor` et `fromCode` (nuls) à `B/domain/specification/Assumption.java`, puis
  `code`, `anchor` et `fromCode` à `B/domain/specification/SourceDeviation.java`. Le code d'un écart
  sans code est attribué par position dans le constructeur compact de `SpecificationContent`. Faire
  passer T007.

### Domaine de la séance (data-model.md)

- [X] T009 [P] Test `BT/domain/interview/InterviewTest.java` :
  - transitions `PREPARED` → `IN_PROGRESS` → `FINISHED` ; une transition interdite lève une
    `DomainException` `CONFLICT` ;
  - « ≤ 20 participants de 1 à 120 caractères » ;
  - une séance terminée refuse tout nouveau point.
- [X] T010 [P] Test `BT/domain/interview/InterviewPointTest.java` :
  - un texte de 5 000 caractères au plus, obligatoire sauf pour `CONFIRMED` et `REMOVE` ;
  - `QUESTION` est toujours `DEFERRED` ;
  - `origin_code` est obligatoire pour `ASSUMPTION`, `SOURCE_DEVIATION` et `OPEN_QUESTION` ;
  - responsable de 120 caractères au plus ;
  - « au plus 200 points par spécification et par séance ».
- [X] T011 Implémenter dans `B/domain/interview/` les records `Interview`, `InterviewSpec` et
  `InterviewPoint`, ainsi que les énumérations `State`, `PointOrigin` et `Outcome`, d'après
  data-model.md. Faire passer T009 et T010.
- [X] T012 [P] Test `BT/domain/interview/WalkthroughTest.java`, sur l'ordre de parcours (FR-003) :
  - résumé ; pour chaque use case, le use case, ses étapes, ses alternatives, puis ses scénarios ;
  - les exigences EF puis ET ; enfin « Non rattachés », qui regroupe les points sans ancre valide ;
  - les points écartés sont absents, et « prochain point à traiter » saute les éléments sans point
    non traité ;
  - plusieurs spécifications se suivent dans leur rang.
- [X] T013 Implémenter `B/domain/interview/Walkthrough.java`, qui construit la liste des arrêts
  depuis le contenu de chaque version relue, ses points et ses exclusions. Faire passer T012.
- [X] T014 Déclarer les ports `B/domain/port/InterviewRepository.java` (séance, spécifications,
  points, curseur, séance en cours d'une spécification). `SourceUpdateRepository` est déclaré avec
  US6 (T084).

### Persistance et câblage

- [X] T015 Test `BT/infrastructure/persistence/interview/InterviewPersistenceIT.java` (`@Isolated`) :
  - aller-retour d'une séance, de ses spécifications et de ses points ;
  - la cascade à la suppression d'une spécification ;
  - le refus d'une seconde séance en cours sur la même spécification, par l'index partiel.
- [X] T016 Implémenter `B/infrastructure/persistence/interview/SqliteInterviewRepository.java` en
  JDBC (`Session.doWork`), sans entité JPA : le pilote SQLite refuse les nulls liés sans type. Faire passer T015.
- [X] T017 Câbler les dépôts et les futurs cas d'usage de séance dans
  `B/infrastructure/config/UseCaseProducer.java`, puis lancer `HexagonalArchitectureTest` sous
  Maven : `domain/interview` ne doit dépendre d'aucun framework.

### Mode présentation (research.md R6)

- [X] T018 [P] Test `F/app.spec.ts`. Quand la route active porte `data.presentation`, le rail
  (`sw-main-nav`, la marque) et `sw-project-notice` sont absents, et le lien d'évitement mène au
  contenu. Le shell normal ne change pas ailleurs.
- [X] T019 [P] Test `F/core/events/app-events.service.spec.ts` : aucune `Notification` n'est créée
  tant qu'une route de présentation est active, et rien n'est rejoué après.
- [X] T020 Implémenter `F/core/interviews/presentation.ts` : un signal `presentation()` dérivé des
  données de la route active. Masquer le rail dans `F/app.ts` et suspendre les notifications dans
  `F/core/events/app-events.service.ts`. Faire passer T018 et T019.
- [X] T021 [P] Créer `F/core/interviews/interviews.model.ts` (types de contracts/rest-api.md :
  `InterviewDetail`, `PointInput`, `PointView`, `InterviewSummary`) et
  `F/core/interviews/interviews.api.ts` (méthodes vides typées, complétées par story).

**Checkpoint**: format compatible, domaine et persistance de la séance, mode présentation.
`./mvnw -B verify -DskipDependencyCheck` est vert.

---

## Phase 3: User Story 1 - Relire une spécification en séance et noter les points (Priority: P1) 🎯 MVP

**Goal**: « Faire le point » sur une spécification ouvre une séance en présentation. On y parcourt
la spécification, on répond aux points et on note des remarques et des questions, avec un
enregistrement au fil de l'eau et une reprise sans perte.

**Independent Test**: quickstart V1.

### Tests US1

- [X] T022 [P] [US1] Test `BT/application/interview/RunInterviewTest.java`, avec des doubles :
  - `startForSpecification` crée une séance `IN_PROGRESS` et fixe la version relue ;
  - refus `INTERVIEW_BUSY` si la spécification a déjà une séance en cours (le message la nomme) ;
  - refus `OPERATION_IN_PROGRESS` pendant une génération ou une révision (`OperationRegistry`) ;
  - `savePoint` : création et modification, avec une ancre validée contre la version relue (422
    sinon) ;
  - `moveCursor` ; `deletePoint`, limité aux `REMARK` et aux `QUESTION`.
- [X] T023 [P] [US1] Test `BT/infrastructure/web/InterviewResourceIT.java` (`@Isolated`), sur les
  routes de US1 de contracts/rest-api.md :
  - `POST /api/sessions/{sid}/interviews`, `GET /api/interviews/{id}`, `PUT …/cursor`,
    `POST/PUT/DELETE …/points` ;
  - les statuts 201, 200, 204, 409 et 422 ;
  - 403 sans session ;
  - un corps trop gros refusé.
- [X] T024 [P] [US1] Test `F/features/interview/walkthrough.spec.ts` : même ordre que T012 côté
  front, navigation ↑ ↓ ← →, et « prochain point » (N).
- [X] T025 [P] [US1] Test `F/features/interview/interview.page.spec.ts` :
  - l'élément courant est annoncé ;
  - chaque point affiche un libellé textuel ;
  - `R` et `Q` ouvrent la saisie, et les raccourcis sont inactifs quand un champ a le focus ;
  - l'enregistrement appelle l'API et confirme « Enregistré » ;
  - la progression affiche « 2 décidés · 1 remarque · 3 restants » ;
  - le bandeau de version plus récente s'affiche.
- [X] T026 [P] [US1] Test `F/features/interview/point-editor.component.spec.ts` :
  - les issues sont des boutons radio ;
  - le responsable et l'échéance n'apparaissent que pour « Reporté » ;
  - les bornes sont appliquées à la saisie, avec un message ;
  - Ctrl+Entrée enregistre.

### Implémentation US1

- [X] T027 [US1] Implémenter `B/application/interview/RunInterview.java` (démarrage depuis une
  spécification, lecture, curseur, points) avec `OperationRegistry` et `ClockPort`. Faire passer
  T022.
- [X] T028 [US1] Implémenter `B/infrastructure/web/InterviewResource.java` pour les routes de US1,
  avec des vues qui incluent la `SpecificationView` de la version relue et `latestVersion`. Faire
  passer T023.
- [X] T029 [P] [US1] Implémenter `F/features/interview/walkthrough.ts`. Faire passer T024.
- [X] T030 [US1] Implémenter `F/features/interview/point-editor.component.ts`. Faire passer T026.
- [X] T031 [US1] Implémenter `F/features/interview/interview.page.ts` (contracts/ui.md, « Séance »).
  - Présentation : typographie de base 1,25 rem, élément courant mis en avant, région `polite`.
  - Raccourcis : ↑ ↓ ← → N R Q Entrée et `?`.
  - Progression, bandeau de version, et `PUT …/cursor` à chaque déplacement.
  - Faire passer T025.
- [X] T032 [US1] Déclarer la route `/seances/:id` (chargée à la demande, `data: { presentation:
  true }`) dans `F/app.routes.ts`, et compléter `F/core/interviews/interviews.api.ts`.
- [X] T033 [US1] Sur `F/features/specification/specification.page.ts` :
  - ajouter « Faire le point (n) », ou « Reprendre la séance » si une séance est en cours
    (`GET /api/sessions/{sid}/interviews`) ;
  - le rendre indisponible pendant une génération ou une révision, avec la raison ;
  - compléter les tests dans `specification.page.spec.ts`.
- [X] T034 [US1] E2E `e2e/tests/us21-interview.spec.ts`, partie « séance » (quickstart V1, étapes
  1 à 6) :
  - démarrer la séance, répondre à une hypothèse, noter une remarque et une question ;
  - recharger la page et reprendre la séance ;
  - contrôle axe sur l'écran de séance.

**Checkpoint**: une séance se mène et se reprend. Gates verts.

---

## Phase 4: User Story 2 - Terminer la séance et copier le compte rendu (Priority: P1)

**Goal**: « Terminer » clôt la séance. Le compte rendu, construit sans modèle et regroupé par
spécification, se copie en HTML et en texte, et reste consultable plus tard.

**Independent Test**: quickstart V2.

### Tests US2

- [X] T035 [P] [US2] Test `BT/domain/interview/InterviewReportTest.java` :
  - le titre porte la date et les participants ;
  - les décisions citent leur élément et leur issue ;
  - « À vérifier » porte le responsable et l'échéance ;
  - le texte est repris mot pour mot ;
  - le contenu est regroupé par spécification ;
  - « Aucun point noté » quand il n'y a rien ;
  - l'HTML est échappé (`<script>` dans un point) ;
  - le texte brut utilise des puces « • ».
- [X] T036 [P] [US2] Ajouter à `BT/application/interview/RunInterviewTest.java` :
  - `finish` passe la séance en `FINISHED` et lève le verrou `in_progress` ;
  - une séance terminée refuse tout point (409).
- [X] T037 [P] [US2] Ajouter à `BT/infrastructure/web/InterviewResourceIT.java` :
  `POST …/finish`, `GET …/report` (`html`, `text`), et la liste des séances passées d'une
  spécification.
- [X] T038 [P] [US2] Test `F/features/interview/interview-recap.page.spec.ts` :
  - « Copier le compte rendu » appelle `navigator.clipboard.write` avec `text/html` et `text/plain` ;
  - la confirmation est annoncée ;
  - repli : une zone sélectionnée et une consigne quand le presse-papiers est refusé.

### Implémentation US2

- [X] T039 [US2] Implémenter `B/domain/interview/InterviewReport.java` (research.md R7), en
  français et en anglais selon la langue de la spécification. Faire passer T035.
- [X] T040 [US2] Ajouter `finish` à `RunInterview`, et les routes `finish` et `report` à
  `InterviewResource`, ainsi que `GET /api/sessions/{sid}/interviews`. Faire passer T036 et T037.
- [X] T041 [US2] Implémenter `F/features/interview/interview-recap.page.ts` (route
  `/seances/:id/recapitulatif`, shell normal), bloc « Compte rendu ». Faire passer T038.
- [X] T042 [US2] Dans `F/features/interview/interview.page.ts`, faire que « Terminer » demande
  confirmation quand des points de la spécification restent non traités (en les nommant), puis
  mène au récapitulatif.
- [X] T043 [US2] Sur la page d'une spécification, ajouter un bloc « Séances » : la liste des séances
  passées, avec leur compte rendu à revoir et à copier (FR-012).
- [X] T044 [US2] E2E `us21-interview.spec.ts`, partie « compte rendu » (V2) :
  - terminer la séance, puis copier avec les permissions du presse-papiers accordées au contexte
    Playwright ;
  - relire le presse-papiers et y retrouver les trois points mot pour mot ;
  - contrôle axe sur le récapitulatif.

**Checkpoint**: une séance complète donne un compte rendu copiable. Gates verts.

---

## Phase 5: User Story 3 - Réviser la spécification d'après la séance (Priority: P1)

**Goal**: pour chaque spécification d'une séance terminée, une nouvelle version est rédigée par la
révision de la 011, relue en diff et appliquée. Les points tranchés en disparaissent et les points
reportés y deviennent des points ouverts, de façon garantie.

**Independent Test**: quickstart V3.

### Tests US3

- [X] T045 [P] [US3] Test `BT/domain/interview/InterviewOutcomeTest.java` (research.md R3) :
  - une hypothèse et un point ouvert dont le `fromCode` est tranché sont retirés ;
  - un écart tranché est retiré ;
  - un point `DEFERRED` est ajouté comme `OpenQuestion`, avec son responsable et son échéance, et
    son ancre traduite vers le code équivalent quand il existe (nulle sinon) ;
  - pas de doublon quand il est déjà repris par `fromCode` ;
  - les codes `H-` et `Q-` sont renumérotés ;
  - une hypothèse sans `fromCode` est gardée.
- [X] T046 [P] [US3] Test `BT/domain/specification/MarkdownRendererTest.java` (ajouts) :
  - la rubrique « Points ouverts » / « Open questions » suit « Hypothèses », avec le code, la
    question, le responsable et l'échéance ;
  - elle est omise quand la liste est vide.
- [X] T047 [P] [US3] Test `BT/application/interview/InterviewRevisionTest.java` :
  - la discussion de la spécification est créée si elle n'existe pas ;
  - un seul message de séance est ajouté, au format de contracts/llm-output.md, et marqué de
    `interview_id` ;
  - la limite de 20 changements ne s'applique pas ;
  - `ProposeRevision` reçoit l'`outcome` ;
  - la proposition porte l'`interviewId` ;
  - Copilot non consenti : refus avec la raison (R12) ;
  - la révision part de la dernière version (R11).
- [X] T048 [P] [US3] Ajouter à `BT/application/conversation/ProposeRevisionTest.java` :
  - l'`outcome` est appliqué avant le dépôt de la proposition ;
  - `title(...)` d'un message de séance donne « Séance du 20/11/2026 (Julie M., Paul D.) ».
- [X] T049 [P] [US3] Test `BT/application/interview/ApplyInterviewRevisionTest.java` :
  l'application d'une proposition qui porte un `interviewId` enregistre `applied_version` sur
  `interview_spec`, et la révision se relance tant qu'elle n'est pas appliquée (FR-014).
- [X] T050 [P] [US3] Test d'injection
  `BT/infrastructure/llm/InterviewMessageInjectionTest.java` : un texte de point qui imite une
  consigne (« Ignore les instructions… ») reste dans le message utilisateur, jamais dans les
  consignes système (constitution V).

### Implémentation US3

- [X] T051 [US3] Implémenter `B/domain/interview/InterviewOutcome.java`. Faire passer T045.
- [X] T052 [US3] Rendre la rubrique « Points ouverts » dans
  `B/domain/specification/MarkdownRenderer.java`, ce qui l'ajoute aussi à l'export Jira, qui passe
  par le Markdown. Faire passer T046.
- [X] T053 [US3] Côté modèle (contracts/llm-output.md) :
  - ajouter `fromCode` et `DraftOpenQuestion` à `B/domain/llm/DraftSpecification.java` ;
  - les reprendre dans `B/domain/specification/IdentifierAssigner.java` : codes `Q-nn`, `fromCode`
    inconnu de la base ignoré ;
  - étendre le schéma de `B/infrastructure/llm/LlmSchemas.java`.
- [X] T054 [US3] Ajouter la consigne `fromCode` et `openQuestions` à
  `R/prompts/revise-specification.txt` : reprise des codes de base, nouvelles questions en suspens,
  questions reportées à ne pas répéter. Ajouter `fromCode` à `R/prompts/edit-specification.txt`.
- [X] T055 [US3] Ajouter l'`outcome` (facultatif) et le titre de séance à
  `B/application/conversation/ProposeRevision.java`. Ajouter `interviewId` à
  `B/application/specification/ProposalStore.java`, puis enregistrer `applied_version` à
  l'application (`DiscussionProposalResource` → cas d'usage). Faire passer T048 et T049.
- [X] T056 [US3] Implémenter `B/application/interview/InterviewRevision.java` et la route
  `POST /api/interviews/{id}/specs/{sid}/revision`. Ajouter `revisionAvailable` et sa raison à la
  vue. Faire passer T047 et T050.
- [X] T057 [US3] Rejeux LLM `e2e/fixtures/llm` (marqueur `IT-INTERVIEW`) pour la révision de séance :
  hypothèse confirmée retirée, étape modifiée, nouvelle question en suspens. Suivre
  `e2e/fixtures/llm/README.md`.
- [X] T058 [US3] Côté front :
  - bloc « Révision » du récapitulatif : « Proposer la nouvelle version » mène à la page de la
    spécification, discussion ouverte et tour en cours ; le bloc affiche la raison quand la
    révision est indisponible, et « Version n appliquée » une fois faite ;
  - rubrique « Points ouverts » sur `F/features/specification/specification.page.ts`, avec leurs
    tests.
- [X] T059 [US3] Golden set : ajouter un cas de révision de séance dans
  `backend/src/test/resources/eval/discussion/` et le critère `decidedRemoved` dans
  `BT/eval/DiscussionEval.java` (valeur attendue 1,0).
- [X] T060 [US3] E2E `us21-interview.spec.ts`, partie « révision » (V3) :
  - proposer, relire le diff, appliquer ;
  - vérifier que l'hypothèse a disparu, que `Q-01` porte « Paul D. », et que la source est
    « Séance du … ».

**Checkpoint**: MVP complet (US1 à US3). Gates verts, et l'évaluation rejouée sur les rejeux.

---

## Phase 6: User Story 4 - Voir chaque point à l'endroit qu'il concerne (Priority: P2)

**Goal**: la génération, la révision, l'édition, la structuration et la fusion de conflit produisent
une ancre pour chaque hypothèse, écart et point ouvert. Chaque point s'affiche sur son élément.

**Independent Test**: quickstart V4.

### Tests US4

- [X] T061 [P] [US4] Ajouter à `BT/domain/specification/IdentifierAssignerTest.java` :
  - `DraftAnchor(USE_CASE, 1, 4, false)` devient `Anchor("UC-02", 4, false)` ;
  - le scénario par position devient `SC-…`, l'exigence par position `EF-…` ou `ET-…`, `SUMMARY`
    reste `SUMMARY` ;
  - une position ou une étape hors limites donne une ancre nulle, sans violation.
- [X] T062 [P] [US4] Ajouter à `BT/domain/specification/MarkdownRendererTest.java` : chaque
  hypothèse, écart et point ouvert rattaché affiche son élément (« UC-02, étape 4 »).
- [X] T063 [P] [US4] Ajouter à `F/features/specification/specification.page.spec.ts` : chaque point
  affiche son élément avec un lien qui y mène (ancre HTML de l'étape).

### Implémentation US4

- [X] T064 [US4] Ajouter `DraftAnchor` à `DraftSpecification`, puis la traduction dans
  `IdentifierAssigner`, le retrait des ancres invalides et le schéma `LlmSchemas` (anchor sur les
  hypothèses, les écarts et les points ouverts). Faire passer T061.
- [X] T065 [US4] Décrire l'ancre dans `R/prompts/generate-specification.txt`,
  `revise-specification.txt`, `edit-specification.txt`, `structure-imported-text.txt` et
  `merge-conflict.txt`, avec un exemple court par consigne, sans toucher au reste du format.
- [X] T066 [US4] Afficher l'élément de chaque point dans `MarkdownRenderer` et sur la page de la
  spécification, avec des ancres HTML sur les étapes. Faire passer T062 et T063.
- [X] T067 [US4] Mettre à jour les rejeux LLM existants qui produisent des hypothèses
  (`e2e/fixtures/llm/*-specification.json`, révisions et éditions), avec leurs ancres. Ajouter le
  critère `anchoredRate` ≥ 0,90 à `BT/eval/SpecificationStructureEval.java`, puis mettre à jour
  `backend/src/test/resources/eval/baseline.json`.
  *Aucun rejeu existant ne rend d'hypothèse du modèle : rien à mettre à jour ; les rejeux
  `00b-` et `00c-ancres-` servent V4.*
- [X] T068 [US4] E2E `us21-interview.spec.ts` (V4) :
  - une spécification générée affiche ses hypothèses rattachées, que la séance place sur leur
    élément ;
  - une spécification sans ancres présente ses points dans « Non rattachés ».

**Checkpoint**: ancres produites et affichées. L'évaluation rejouée ne recule sur aucun critère.

---

## Phase 7: User Story 5 - Préparer une séance sur plusieurs spécifications (Priority: P2)

**Goal**: la page « Spécifications » montre les points à traiter, filtre, sélectionne et prépare
une séance de plusieurs spécifications. La séance préparée attend en tête de la page.

**Independent Test**: quickstart V5.

### Tests US5

- [X] T069 [P] [US5] Test `BT/domain/interview/PendingPointsTest.java` (FR-025) : décompte par sorte
  depuis la dernière version, moins les points tranchés d'une séance terminée sans
  `applied_version`.
- [X] T070 [P] [US5] Test `BT/application/interview/PrepareInterviewTest.java` :
  - créer, modifier et supprimer une séance `PREPARED` (409 si elle n'est plus préparée) ;
  - les exclusions doivent être des codes de la dernière version ;
  - démarrer fixe la version de chaque spécification et refuse si l'une est occupée ;
  - une spécification supprimée sort de la séance ;
  - une séance préparée vide est supprimée.
- [X] T071 [P] [US5] Ajouter à `BT/infrastructure/web/InterviewResourceIT.java` les routes de
  préparation (`GET ?state=PREPARED`, `POST`, `PUT`, `DELETE`, `POST …/start`), et `pending` dans
  `GET /api/sessions`.
- [X] T072 [P] [US5] Ajouter à `F/features/sessions/sessions.page.spec.ts` :
  - les compteurs sont lus avec le nom de la spécification ;
  - le filtre annonce le nombre affiché ;
  - les cases à cocher font apparaître « Préparer une séance (n) » ;
  - la séance préparée s'affiche en tête, avec « Démarrer », « Modifier » et « Supprimer »
    (confirmation).
- [X] T073 [P] [US5] Test `F/features/interview/interview-preparation.page.spec.ts` :
  - ordre modifiable par « Monter » et « Descendre » ;
  - participants proposés d'après la dernière séance ;
  - « Écarter de cette séance » ;
  - « Enregistrer » et « Démarrer maintenant ».

### Implémentation US5

- [X] T074 [US5] Implémenter `B/domain/interview/PendingPoints.java`, puis `pending` dans
  `B/infrastructure/web/SessionViews.java` et dans la liste des sessions. Faire passer T069.
- [X] T075 [US5] Implémenter `B/application/interview/PrepareInterview.java` et ses routes dans
  `InterviewResource`. Gérer le cas d'une spécification supprimée, en cascade, dans le cas d'usage
  de suppression existant. Faire passer T070 et T071.
- [X] T076 [US5] Sur `F/features/sessions/sessions.page.ts`, ajouter les compteurs, le filtre, la
  sélection et les séances préparées. Faire passer T072.
- [X] T077 [US5] Implémenter `F/features/interview/interview-preparation.page.ts` (route
  `/seances/:id/preparation`, shell normal). Faire passer T073. Dans la séance, afficher
  « Spéc. n sur m », et rendre le récapitulatif avec une révision par spécification.
- [X] T078 [US5] E2E `us21-interview.spec.ts`, partie « préparation » (V5) :
  - préparer une séance sur deux spécifications en écartant un point, relancer l'instance, puis
    démarrer ; *la relance est remplacée par un rechargement de la page : la séance préparée est
    en base, et le jar E2E est partagé par toute la suite ;*
  - vérifier que le point écarté est absent, que le compte rendu est en deux parties, et qu'une
    seconde séance sur une spécification occupée est refusée ;
  - contrôle axe sur la préparation.

**Checkpoint**: séances de plusieurs spécifications préparées. Gates verts.

---

## Phase 8: User Story 6 - Garder visibles les sources de vérité à mettre à jour (Priority: P3)

**Goal**: les décisions qui touchent une source de vérité laissent un « à reporter » visible, qui
est signalé quand la source change et clos par le mainteneur. L'outil n'écrit jamais dans les
sources.

**Independent Test**: quickstart V6.

### Tests US6

- [X] T079 [P] [US6] Test `BT/application/interview/SourceUpdatesTest.java` :
  - à l'application d'une révision de séance, un « à reporter » est créé pour chaque point `CHANGE`
    ou `REMOVE` dont le use case cite un `DOC_PAGE`, un `JIRA_ISSUE` ou un `ARCH_ELEMENT` (pas un
    document joint), pour chaque écart tranché, et pour chaque source désignée à la main ;
  - le texte est celui de la décision, avec l'empreinte de la source ;
  - l'état est `SOURCE_CHANGED` quand l'empreinte diffère ou que la source n'est plus indexée ;
  - la clôture fonctionne ;
  - rien n'est créé pour une proposition rejetée.
- [X] T080 [P] [US6] Test `BT/infrastructure/knowledge/KnowledgeFingerprintIT.java` :
  `fingerprint(projectId, kind, ref)` est stable pour les mêmes extraits, change quand un extrait
  change, et est vide pour une référence absente.
- [X] T081 [P] [US6] Test `BT/infrastructure/web/SourceUpdateResourceIT.java` (`@Isolated`) :
  - `GET /api/sessions/{sid}/source-updates` et `POST …/close` ;
  - 422 pour une source désignée absente de la connaissance du projet ;
  - aucune écriture : les clients Jira et Confluence (WireMock) ne reçoivent aucune requête autre
    que `GET` pendant un parcours complet (FR-030).
- [X] T082 [P] [US6] Tests front :
  - `F/features/specification/specification.page.spec.ts` : bloc « Sources à mettre à jour »,
    « Marquer comme reporté », état « Source modifiée, à vérifier », surimpression dans « Écarts
    entre sources » ;
  - `point-editor.component.spec.ts` : « Source à mettre à jour… » cherche dans la connaissance du
    projet.

### Implémentation US6

- [X] T083 [US6] Ajouter `fingerprint` au port `B/domain/port/KnowledgeIndex.java` et
  l'implémenter dans `B/infrastructure/knowledge/SqliteKnowledgeIndex.java` : empreinte des
  `contentHash` triés des extraits de la référence. Faire passer T080. *Méthode par défaut du
  port, sur `fingerprints` : une seule implémentation pour SQLite et la mémoire.*
- [X] T084 [US6] Implémenter `B/domain/interview/SourceUpdate.java`,
  `B/application/interview/SourceUpdates.java` et le dépôt JPA (*en JDBC, comme les séances*). Brancher la création à
  l'application d'une proposition de séance (T055). Faire passer T079.
- [X] T085 [US6] Implémenter `B/infrastructure/web/SourceUpdateResource.java` et la source désignée
  dans `PointInput` (validée contre la connaissance du projet). Faire passer T081.
- [X] T086 [US6] Côté front :
  - bloc « Sources à mettre à jour » de la page de la spécification ;
  - surimpression dans « Écarts entre sources » ;
  - « Source à mettre à jour… » dans `point-editor` ;
  - bloc « Sources à mettre à jour » du récapitulatif et section du compte rendu (`InterviewReport`).

  Faire passer T082.
- [X] T087 [US6] E2E `us21-interview.spec.ts`, partie « sources » (V6) : *écrite comme F8 de
  `us16-confluence.spec.ts`, qui a déjà Confluence configuré, indexé, et une spécification citant la
  page 103 :*
  - la page Confluence (WireMock) à mettre à jour s'affiche après la révision ;
  - après modification de la page et resynchronisation : « Source modifiée, à vérifier », puis
    clôture ;
  - aucune requête d'écriture dans le journal WireMock.

**Checkpoint**: toutes les US sont livrées. Gates verts.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T088 [P] README : nouvelle section « Faire le point avec les sachants », qui couvre la
  préparation, la séance, le compte rendu, la révision et les sources à mettre à jour. Mettre à jour
  la description de la page « Spécifications » et de la page d'une spécification.
- [X] T089 [P] `CLAUDE.md` : ajouter les pièges de la 021, à savoir les ancres par position, le
  post-traitement `InterviewOutcome`, le mode présentation, et le fait que le message de séance
  passe par la discussion.
- [X] T090 [P] Visite guidée (018) : ajouter un arrêt « Faire le point » sur la page d'une
  spécification (cible `data-tour`), si la visite couvre cette page.
- [X] T091 Mesurer le bundle initial : il doit rester sous 500 kB, avec les écrans de séance
  chargés à la demande.
- [X] T092 Dérouler quickstart.md (V1 à V6) sur le jar, puis consigner les résultats datés dans
  quickstart.md.
- [X] T093 Avant le commit final, lancer les gates : `./mvnw -B verify -DskipDependencyCheck` (après
  suppression de `jacoco.exec`), les E2E sur le jar et `ci/check-eval.sh`.
- [ ] T094 Évaluation `live` par le mainteneur (nouvelle empreinte) : `anchoredRate` ≥ 0,90,
  `decidedRemoved` = 1,0 et aucun recul ailleurs. Mettre ensuite à jour `last-run.json` et la
  baseline.


---

## Phase 10: Séance en lecture du document (reprise de US1, conception du 20/11/2026)

**Goal**: la séance déroule la spécification verticalement, comme un document ; une hypothèse se
confirme d'un clic ou se réécrit ; un passage sélectionné se commente, se remplace ou se supprime
(`docs/superpowers/specs/2026-11-20-seance-en-lecture-du-document-design.md`).

**Independent Test**: démarrer une séance, confirmer une hypothèse d'un clic, réécrire une autre,
sélectionner un passage d'une étape et le remplacer, fermer l'onglet, rouvrir : la séance reprend
au dernier point, le passage cité figure dans le compte rendu et le message de révision.

### Tests (écrits d'abord)

- [X] T095 [P] [US1] `InterviewPointTest` et `InterviewPointRepositoryIT` : `quote` facultatif, au
  plus 1 000 caractères, enregistré et relu.
- [X] T096 [P] [US1] `InterviewReportTest` : « Sur « passage » : … » pour une remarque citée ;
  `InterviewRevisionTest` : « remplacer « passage » par … », « supprimer « passage » ».
- [X] T097 [P] [US1] `interview-document.component.spec.ts` : rendu complet (story, résumé, détails, use
  cases, scénarios Soit / Quand / Alors, exigences) et emplacement par élément reçu avec son ancre.
- [X] T098 [P] [US1] `point-card.component.spec.ts` : Confirmer en un clic, Réécrire prérempli,
  Retirer, Reporter, carte repliée et « Modifier » ; point ouvert sans réponse proposée.
- [X] T099 [P] [US1] `selection.spec.ts` : sélection ramenée au premier élément touché,
  Commenter / Remplacer / Supprimer, passage cité ; bouton « Noter » au clavier.
- [X] T100 [US1] `interview.page.spec.ts` réécrit : document vertical, ↑ ↓ entre cartes, prochain
  point à traiter, curseur = ancre de la dernière carte focalisée, spécification suivante.
- [X] T101 [US1] E2E `us21-interview.spec.ts` : scénarios de séance repris sur le nouvel écran (V1
  à V6), plus la sélection d'un passage et axe sans violation.

### Implémentation

- [X] T102 [US1] Migration V31 `interview_point.quote` ; `InterviewPoint`, dépôt JDBC,
  `PointInput` / `PointView` REST et modèle front.
- [X] T103 [US1] `InterviewReport` et message de séance (`InterviewRevision`) : passage cité.
- [X] T104 [US1] `sw-interview-document` (emplacement par élément, ancre `data-anchor`) ;
  `storyParts` partagé avec la page de la spécification.
- [X] T105 [US1] `sw-point-card` (réutilise `PointEditor` pour le formulaire déplié) et barre de
  sélection (`role="toolbar"`, surlignage du passage cité).
- [X] T106 [US1] Réécrire `interview.page.ts` sur le document ; supprimer `walkthrough.ts`, les
  touches N, R, Q et ← →.
- [X] T107 [US1] Visite guidée, README et quickstart (V1) : décrire le nouvel écran.
- [X] T108 [US1] Gates : `verify` (après suppression de `jacoco.exec`), E2E sur le jar, bundle
  initial sous 500 kB, `ci/check-eval.sh`.

---

## Dependencies & Execution Order

### Phases

- **Setup (T001-T002)**, puis **Foundational (T003-T021)**, qui bloquent toutes les US.
- **US1 (T022-T034)**, puis **US2 (T035-T044)** : le compte rendu s'appuie sur la séance.
- **US3 (T045-T060)** : elle dépend de US2, puisque la révision se lance depuis le récapitulatif
  d'une séance terminée.
- **US4 (T061-T068)** : elle dépend seulement de la phase 2. Elle peut avancer en parallèle de US1 à
  US3, mais touche les mêmes consignes que T054 : faire T054 avant T065.
- **US5 (T069-T078)** : elle dépend de US1 et US2 (démarrage, compte rendu par spécification).
- **US6 (T079-T087)** : elle dépend de US3 (application d'une révision de séance) et de T085 pour la
  source désignée.
- **Polish (T088-T094)** : à la fin.

### Dans chaque story

Tests (vus échouer), puis domaine, application, web, front, et enfin E2E. Un commit par groupe
logique, gates verts.

### Parallel Opportunities

- **Phase 2** : T003, T005, T009, T010, T012, T018 et T019 (tests de fichiers distincts), puis
  T004 et T021.
- **US1** : T022 à T026 ensemble, puis T029 en parallèle de T027 et T028.
- **US3** : T045 à T050 ensemble.
- **US4** : T061 à T063 ensemble ; la phase entière peut avancer en parallèle de US2 et US3 (hors
  consignes).
- **US5 et US6** : leurs tests (T069 à T073, T079 à T082) ensemble.

---

## Parallel Example: User Story 1

```text
Ensemble :
  T022 RunInterviewTest
  T023 InterviewResourceIT
  T024 walkthrough.spec.ts
  T025 interview.page.spec.ts
  T026 point-editor.component.spec.ts
Puis : T027 RunInterview → T028 InterviewResource, en parallèle de T029 walkthrough.ts
```

---

## Implementation Strategy

### MVP (US1 à US3)

1. Phases 1 et 2.
2. **US1** : une séance d'une spécification. Les points sont dans « Non rattachés » tant que US4
   n'est pas faite, ce qui est déjà utilisable.
3. **US2** : le compte rendu copiable.
4. **US3** : la révision garantie.
5. **Arrêt et validation** : quickstart V1 à V3, gates, et démonstration au mainteneur sur une
   vraie spécification.

### Incréments suivants

6. **US4** : les ancres. L'évaluation est à relancer en `live` par le mainteneur.
7. **US5** : la préparation et les séances de plusieurs spécifications.
8. **US6** : les sources à mettre à jour.

Chaque incrément est fusionnable seul. L'ordre US4, puis US5, puis US6 peut changer selon les
retours du mainteneur après le MVP.
