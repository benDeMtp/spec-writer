# Quickstart : valider la feature 016

## 1. Gates automatiques

```bash
rm -f backend/target/jacoco.exec
./mvnw -B verify -DskipDependencyCheck
./mvnw -B package -DskipTests && npm --prefix e2e test
```

Attendu, en plus des tests existants :
- `ConfluenceRestClientIT` (WireMock) : résolution des trois formes d'adresse, liste paginée par
  `ancestor=`, contenu rendu, `401`, `403` sur une page, proxy et certificat ;
- `ConfluenceIndexationTest` : arbre complet indexé, page commune à deux racines une fois,
  réindexation sans changement sans relecture, page modifiée seule relue, page sortie retirée,
  erreur sans perte (SC-001, SC-002) ;
- `ReservedPlacesTest` : au plus deux remplacements, ordre gardé, rien sans référence ni niveaux
  uniformes (FR-019, FR-020) ;
- `UserMessagesTest` / `AgentToolsTest` : `source`, `maj` (ou `inconnue`) et `fiabilite` sur chaque
  extrait (SC-004) ;
- `MarkdownRendererTest` : rubrique « Écarts entre sources », omise sans écart ;
- E2E `us16-confluence.spec.ts` : Confluence simulé (WireMock) avec un arbre de pages, ajout d'une
  racine (aperçu : titre et nombre de pages), indexation, recherche d'essai, niveaux dans les
  réglages, étape de recherche de l'agent avec source et date, spécification avec un écart
  (LLM rejoué).

## 2. Confluence (US1), en dev mode

1. Démarrer le WireMock des E2E avec l'arbre de démonstration (`e2e/fixtures/confluence/`).
2. Configuration › Confluence : adresse du WireMock, jeton de test, HTTP confirmé ; « Tester la
   connexion » nomme le compte.
3. Réglages du projet › Documentation : ajouter `…/pages/viewpage.action?pageId=100`. Attendu :
   titre de la page et « 12 pages » avant indexation.
4. Connaissance du projet : réindexer Confluence ; compteurs à 12 pages ; une recherche d'essai
   trouve une page de troisième niveau, aucune page hors de l'arbre.
5. Modifier une page dans le WireMock (version + 1), réindexer : une seule page relue (journal de
   progression).

## 3. Source et date (US2)

Poser à l'agent une question couverte par Jira, SharePoint et Confluence : l'étape de recherche
montre la source et la date de chaque résultat ; le rejeu (`e2e/fixtures/llm`) reçoit les attributs
`source`, `maj`, `fiabilite`.

## 4. Fiabilité (US3)

1. Réglages du projet : SharePoint « Référence », les autres « Interprétation » ; passer une racine
   Confluence à « À confirmer ».
2. Avec une demande SharePoint (plafond 500 €) et une page Confluence (plafond 1 000 €) : la
   réponse suit 500 €, signale l'incohérence et cite les deux ; la recherche retient la demande
   même si les pages Confluence sont mieux classées.
3. Générer une spécification sur ce besoin : rubrique « Écarts entre sources », reprise à l'export
   et dans l'aperçu Jira.

## 5. Évaluation réelle

```bash
./mvnw -pl backend verify -Peval -DskipDependencyCheck
ci/check-eval.sh
```

Attendu : `sourceConflictFlagged` ≥ 0,9, `referenceRetained` = 1,0, aucun indicateur existant sous
sa base ; `last-run.json` commité.

## 6. Vérification manuelle (mainteneur)

Sur le Confluence de l'entreprise, avec un jeton personnel : une racine réelle de quelques dizaines
de pages, proxy d'entreprise actif ; vérifier le temps d'indexation et une réponse de l'agent qui
cite une page Confluence avec sa date.
