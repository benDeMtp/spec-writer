---
description: "Tâches de la feature 009 : joindre un document à une spécification"
---

# Tasks: Joindre un document à une spécification

**Input**: `specs/009-joindre-document/` (plan.md, spec.md, research.md, data-model.md,
contracts/api.md, quickstart.md)

**Tests**: obligatoires (constitution III, TDD) : chaque test est écrit et vu échouer avant le
code. `domain` et `application` sont couverts par des tests unitaires (JaCoCo ne compte pas les
`@QuarkusTest`).

**Organization**: US1 (joindre au besoin, MVP), US2 (régénérer sans le fichier d'origine), US3
(gérer les documents d'une session en cours).

## Format: `[ID] [P?] [Story] Description`

- **[P]** : parallélisable (fichiers différents, aucune dépendance sur une tâche non finie)
- Chemins : `backend/src/main/java/dev/specwriter/` noté `BM/`, `backend/src/test/java/dev/specwriter/`
  noté `BT/`, `frontend/src/app/` noté `FA/`

---

## Phase 1: Setup

- [ ] T001 Migration `backend/src/main/resources/db/migration/V17__session_attachment.sql` (data-model.md) : table `session_attachment (id TEXT PRIMARY KEY, session_id TEXT NOT NULL REFERENCES session(id) ON DELETE CASCADE, ordinal INTEGER NOT NULL, title TEXT NOT NULL, file_name TEXT NOT NULL CHECK (length(file_name) BETWEEN 1 AND 255), format TEXT NOT NULL CHECK (format IN ('DOCX','XLSX','PDF','HTML')), chars INTEGER NOT NULL, content_hash TEXT NOT NULL, text TEXT NOT NULL CHECK (length(text) <= 200000), added_at TEXT NOT NULL, UNIQUE (session_id, content_hash), UNIQUE (session_id, file_name))`

---

## Phase 2: Foundational (bloque toutes les user stories)

- [ ] T002 [P] Tests d'abord `BT/domain/attachment/AttachmentsTest.java` puis `BM/domain/attachment/Attachment.java`, `AttachmentDraft.java`, `Attachments.java` (data-model.md) : `fileName` sans chemin (séparateurs retirés), 1 à 255 caractères ; `format` d'après l'extension `.docx`/`.xlsx`/`.pdf`/`.html`/`.htm`, sinon `UNSUPPORTED_FORMAT` ; titre = titre du document, sinon nom sans extension ; `contentHash` SHA-256 du texte ; `MAX_PER_SESSION = 5` (`ATTACHMENT_LIMIT`), même contenu ou même nom (`ATTACHMENT_DUPLICATE`) ; `ref()` = `joint:<fileName>` ; nouveaux codes dans `BM/domain/error/ErrorCode.java` (`UNSUPPORTED_FORMAT`, `DOCUMENT_UNREADABLE`, `ATTACHMENT_LIMIT`, `ATTACHMENT_DUPLICATE`), messages sans texte du document
- [ ] T003 [P] Port `BM/domain/port/AttachmentReader.java` (`Reading read(String fileName, byte[] content)`, `record Reading(String title, String text, UnreadReason failure)`) ; `BM/infrastructure/docs/DocumentExtraction.java` (extraction partagée par format, déplacée de `LocalDocumentFolder.extract`, comportement inchangé) ; `BM/infrastructure/docs/TempFileAttachmentReader.java` (fichier temporaire du dossier de données, permissions du propriétaire, bornes `DocumentFolder.Limits.DEFAULT` : 20 Mo, 200 000 caractères, délai 60 s sur fil virtuel ; fichier supprimé dans `finally`) ; tests `BT/infrastructure/docs/TempFileAttachmentReaderTest.java` (4 formats via `TestDocuments`, protégé, scanné `NO_TEXT`, bombe zip `TOO_LARGE`, format refusé, aucun fichier temporaire restant) ; `LocalDocumentFolderTest` inchangé et vert
- [ ] T004 Port `BM/domain/port/AttachmentRepository.java` (`list(sessionId)` avec texte dans l'ordre d'ajout, `views(sessionId)` sans texte, `add`, `remove(sessionId, id)`, `count(sessionId)`) ; `BM/infrastructure/persistence/attachment/` (entité, dépôt JPA) ; test `BT/infrastructure/persistence/AttachmentPersistenceIT.java` (aller-retour, ordre, unicités, suppression en cascade avec la session)
- [ ] T005 [P] Limite de corps (research.md R1) : `quarkus.http.limits.max-body-size=21M` dans `backend/src/main/resources/application.properties` et `BM/infrastructure/web/BodySizeFilter.java` (filtre de route Vert.x : au-delà de 256 Ko, ou sans longueur annoncée, `413` sauf `POST /api/attachment-drafts` et `POST /api/sessions/{id}/attachments`) ; IT `BT/infrastructure/web/BodySizeFilterIT.java`
- [ ] T006 [P] `MentionField.ATTACHMENT` (`BM/domain/knowledge/MentionField.java`) et `MentionKind.ATTACHMENT` (`BM/domain/knowledge/MentionKind.java`) ; front `FA/core/projects/projects.model.ts` (`MentionField`, `MentionKind`) ; aucun effet sur `Mentions.find` (un document joint n'est pas une mention écrite)

**Checkpoint** : un fichier se lit sans rien laisser, un document joint se range et se supprime avec sa session.

---

## Phase 3: User Story 1 - Joindre un document au besoin (Priority: P1) 🎯 MVP

**Goal** : un document joint sur l'écran du besoin est transmis à l'IA comme un document désigné, avec son état.

**Independent Test** : quickstart.md scénarios 1, 2, 3 et 8.

- [ ] T007 [US1] Tests d'abord `BT/application/attachment/AttachmentDraftsTest.java` puis `BM/application/attachment/AttachmentDrafts.java` (research.md R3) : lecture par `AttachmentReader`, brouillon en mémoire 60 min (horloge injectée), 200 brouillons au plus (le plus ancien oublié), `take(ids)` pour le démarrage (inconnu ou expiré : `VALIDATION_FAILED`), `forget(id)` ; refus avec la raison de lecture (`DOCUMENT_UNREADABLE` + raison) ; câblage `BM/infrastructure/config/UseCaseProducer.java`
- [ ] T008 [US1] `BM/infrastructure/web/AttachmentResource.java` : `POST /api/attachment-drafts?name=` (corps `application/octet-stream`, `201` et la vue), `DELETE /api/attachment-drafts/{id}` (`204`) (contracts/api.md) ; IT `BT/infrastructure/web/AttachmentResourceIT.java` (PDF accepté, `.doc` refusé, PDF protégé, vue sans texte)
- [ ] T009 [US1] Tests d'abord dans `BT/application/session/` (test du démarrage) puis `BM/application/session/StartSession.java` et `BM/infrastructure/web/SessionResource.java` (`CreateRequest.attachmentIds`, 5 au plus) : brouillons rattachés à la session (ordre d'envoi, doublons refusés) avant le premier lot de questions, puis oubliés
- [ ] T010 [US1] Tests d'abord dans `BT/application/knowledge/SelectKnowledgeTest.java` puis `BM/application/knowledge/SelectKnowledge.java` (research.md R5, clarification 4) : documents joints de la session (`AttachmentRepository`) insérés parmi les documents désignés dans l'ordre mentions du besoin, documents joints, mentions des réponses, de l'instruction ; 4 au plus ensemble, part `mentionedText`/`mentionedPart` du budget (008), coupe à une section ; `Entry(DOC_SECTION, role null, ref joint:…, titre, texte, empreinte).withDesignated()` ; pour les questions, la spécification et la modification ; nouveau constructeur câblé dans `UseCaseProducer`
- [ ] T011 [US1] Tests d'abord dans `BT/application/knowledge/MentionQueriesTest.java` puis `BM/application/knowledge/MentionQueries.java`, `BM/infrastructure/web/KnowledgeResource.java` (`MentionsInput.attachmentIds`) : état de chaque document joint (brouillons reçus ou documents de la session) avec `field ATTACHMENT`, `document.kind ATTACHMENT`, `path` = nom du fichier, `transmittedShare`, `betterModel`, calculé avec les mêmes règles et le même ordre que T010
- [ ] T012 [US1] Tests d'abord dans `BT/domain/specification/MarkdownRendererTest.java` et `BT/application/specification/DraftValidationTest.java` puis `BM/domain/specification/MarkdownRenderer.java` (source `DOC_PAGE` de référence `joint:` rendue « Document joint <nom> », FR et EN, sans extrait), `BM/application/specification/DraftValidation.java` (extrait retiré pour une référence `joint:`), `BM/infrastructure/web/SourceLinks.java` (aucun lien) (research.md R6, FR-007)
- [ ] T013 [US1] Tests d'abord `FA/shared/ui/attachments.component.spec.ts` et `FA/features/need/need.page.spec.ts` puis `FA/shared/ui/attachments.component.ts` (bouton « Joindre un document » ouvrant le sélecteur, liste titre, nom, taille, état et suggestion de modèle, « Retirer », refus annoncés en `aria-live`), `FA/core/sessions/attachments.api.ts` (envoi en corps brut, `name`), `FA/features/need/need.page.ts` (brouillons, `attachmentIds` au démarrage, états via `resolveMentions` avec `attachmentIds`)
- [ ] T014 [US1] Test d'injection dans `BT/infrastructure/llm/PromptInjectionIT.java` (ou l'IT existant) : le texte d'un document joint contenant une consigne reste dans le bloc `<document … designe="oui">` échappé

**Checkpoint** : le compte rendu joint au besoin nourrit les questions et la spécification.

---

## Phase 4: User Story 2 - Régénérer sans le fichier d'origine (Priority: P2)

**Goal** : le texte gardé suffit ; il disparaît avec la session.

**Independent Test** : quickstart.md scénarios 4 et 7.

- [ ] T015 [US2] Tests d'abord dans `BT/application/specification/` (génération) : documents joints identiques → même empreinte d'entrée, version réutilisée ; aucun accès au fichier d'origine après l'ajout
- [ ] T016 [US2] `GET /api/sessions/{id}/attachments` dans `BM/infrastructure/web/AttachmentResource.java` (vues sans texte, ordre d'ajout) ; IT : liste, puis suppression de la session (suppression locale de la 002) et plus aucune ligne `session_attachment` ni texte dans la base (SC-003)

**Checkpoint** : régénération reproductible, suppression complète.

---

## Phase 5: User Story 3 - Gérer les documents joints d'une session en cours (Priority: P3)

**Goal** : joindre et retirer depuis le questionnaire et la spécification.

**Independent Test** : quickstart.md scénario 5.

- [ ] T017 [US3] Tests d'abord `BT/application/attachment/ManageAttachmentsTest.java` puis `BM/application/attachment/ManageAttachments.java` : ajouter (lecture, plafond de 5, doublons) et retirer ; refus `SPECIFICATION_LOCKED` sur une feature implémentée (`SpecificationLock`) ; versions existantes inchangées
- [ ] T018 [US3] `POST /api/sessions/{id}/attachments?name=` et `DELETE /api/sessions/{id}/attachments/{attachmentId}` dans `BM/infrastructure/web/AttachmentResource.java` ; IT (plafond, doublon, verrou)
- [ ] T019 [US3] Front : `sw-attachments` en mode session (liste depuis `GET`, ajout, retrait, désactivé sur une feature implémentée) dans `FA/features/questionnaire/questionnaire.page.ts` et `FA/features/specification/specification.page.ts` ; tests des deux écrans

**Checkpoint** : les documents suivent la vie de la session.

---

## Phase 6: Polish & Cross-Cutting

- [ ] T020 E2E `e2e/tests/us11-joindre-document.spec.ts` (fichiers générés par `TestDocuments` hors du dossier synchronisé, LLM rejoué avec une fixture qui contient le texte du document joint) : joindre au besoin, état, démarrage, génération, « Document joint <nom> » dans la spécification et l'export sans extrait ; joindre et retirer depuis le questionnaire ; refus d'un `.doc` ; axe sans violation
- [ ] T021 [P] Contrats `specs/001-local-spec-assistant/contracts/openapi.yaml` (brouillons, documents de la session, `attachmentIds`) et `specs/002-projets-contexte-mcp/contracts/openapi.yaml` (`attachmentIds`, `ATTACHMENT`) ; README (joindre un document : formats, limites, texte gardé avec la session, jamais exporté)
- [ ] T022 Gates avant commit (CLAUDE.md) : `rm -f backend/target/jacoco.exec`, `./mvnw -B verify -DskipDependencyCheck`, `./mvnw -B package -DskipTests`, `npm --prefix e2e test` ; `bash ci/check-eval.sh` vert sans relancer l'évaluation (prompts et `infrastructure/llm` inchangés)
- [ ] T023 Essai manuel par l'utilisateur (quickstart.md, « Essai manuel ») ; résultat dans `specs/009-joindre-document/checklists/validation.md`

---

## Dependencies & Execution Order

- **Setup (T001)** → **Foundational** : T002, T003, T005, T006 en parallèle ; T004 après T001 et T002.
- **US1** : T007 → T008 → T009 ; T010 → T011 → T012 ; T013 après T008 et T011 ; T014 après T010.
- **US2** : après T009 et T010 ; T015 → T016.
- **US3** : après US1 ; T017 → T018 → T019.
- **Polish** : T020 après US1–US3 ; T021 à tout moment ; T022 avant chaque commit ; T023 en dernier.

## Parallel Example

```text
Foundational : T002 (règles) + T003 (lecture) + T005 (limite de corps) + T006 (énumérations)
US1          : T012 (rendu sans extrait) en parallèle de T007–T009 (brouillons, démarrage)
```

## Implementation Strategy

1. **MVP = US1** : joindre au besoin, transmission et état ; commit après les gates.
2. US2 (reproductibilité, suppression), puis US3 (session en cours), chacune commitée après les gates.
3. E2E, contrats, essai manuel, PR.
