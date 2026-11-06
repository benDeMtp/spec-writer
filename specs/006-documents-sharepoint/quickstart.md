# Quickstart : valider les documents SharePoint par dossier synchronisé

Prérequis : environnement du `CLAUDE.md` (JDK 25, `env -u JAVA_TOOL_OPTIONS`).

## Jeu de test

`backend/src/test/resources/knowledge/documents/` (fichiers générés par un utilitaire de test,
pas de document réel) :

- `Specs/Aurora.docx` (3 titres, un tableau), `Suivi.xlsx` (2 feuilles), `Exploitation.pdf`
  (2 pages), `Accueil.html` ;
- `Decisions/0017-ihm-nginx.docx` (ADR, « Statut : accepté ») ;
- `Archives/2023.pdf`, `vieux.doc`, `~$Aurora.docx`, `protege.docx` (conteneur OLE),
  `scan.pdf` (sans texte).

## Tests automatisés

```bash
cd backend
../mvnw -B -o -q test -DskipDependencyCheck -Djacoco.skip=true -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest='ExclusionPatternsTest,DocSourceTest,OoxmlTextTest,PdfTextTest,LocalDocumentFolderTest,SynchronizeKnowledgeTest,SelectKnowledgeTest,ContextResourceIT'
npx --prefix ../frontend ng test --watch=false
```

| Scénario | Test | Attendu |
|---|---|---|
| US1-1 | `LocalDocumentFolderTest`, `SynchronizeKnowledgeTest` | 4 formats lus, sections, réf. `docs:<id>/…` |
| US1-2 | `LocalDocumentFolderTest` | `vieux.doc` ignoré, `~$…` non compté, avertissement `IGNORED_FORMAT` |
| US1-3 | `OoxmlTextTest`, `PdfTextTest` | `PROTECTED`, `NO_TEXT`, bombe zip → `TOO_LARGE` |
| US1-4 | `SynchronizeKnowledgeTest` | fichier inchangé non relu ; modifié relu ; supprimé retiré |
| US1-5 | `SynchronizeKnowledgeTest` | ADR Word reconnu (numéro 17, accepté) |
| US2-1, 3 | `ExclusionPatternsTest` | `*`, `**`, `?`, `[…]`, casse ; refus motivés |
| US2-2 | `ContextResourceIT` | aperçu : retenus, exclus, ignorés, exemples |
| US2-4 | `SynchronizeKnowledgeTest` | fichier nouvellement exclu retiré à la réindexation |
| US3-1, 2, 3 | `SelectKnowledgeTest` | document entier relu, coupé, budget ; repli sur les passages ; déterminisme |
| FR-005 | `SynchronizeKnowledgeTest`, E2E | aucun fichier copié dans le dossier de données |

E2E (`us8-project-context.spec.ts`) : déclarer le dossier du jeu de test en « dossier de
documents » avec le motif `Archives/**`, aperçu, indexation, recherche d'essai d'un mot du PDF ;
axe sans violation ; aucun fichier `.docx`/`.pdf`/`.xlsx` dans le dossier de données.

## Évaluation (texte transmis à l'IA modifié)

Avec l'accord de l'utilisateur (environ 150 requêtes Copilot) :

```bash
env -u JAVA_TOOL_OPTIONS ./mvnw -B -pl backend verify -Peval -DskipDependencyCheck
bash ci/check-eval.sh
```

## Essai manuel (projet de l'utilisateur)

1. Synchroniser le dossier SharePoint partagé (aide de la page Contexte).
2. Le déclarer en « dossier de documents », motifs au besoin, aperçu, puis indexation.
3. Génération touchant un sujet de ces documents : le document apparaît dans la connaissance
   transmise.
