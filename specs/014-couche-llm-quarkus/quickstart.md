# Quickstart : vérifier la feature 014

Prérequis : JDK 25 (`export JAVA_HOME=~/.sdkman/candidates/java/25.0.2.r25-mandrel PATH=$JAVA_HOME/bin:$PATH`),
lancer Maven avec `env -u JAVA_TOOL_OPTIONS`, `copilot login` fait pour l'évaluation et les
essais.

## 1. Renommage (US4)

```bash
git grep -n "dev\.specwriter" -- backend/src frontend/src CONTRIBUTING.md CLAUDE.md pom.xml backend/pom.xml frontend/pom.xml
```

Attendu : seules les clés `%dev.specwriter.` de `application.properties` (profil dev) sortent.
`jreleaser.yml` garde `packageIdentifier: dev.specwriter.app` et a `groupId: fr.kawamind`.
`HexagonalArchitectureTest` vérifie que toutes les classes sont sous `fr.kawamind`.

Sur une installation existante (outil installé, port 47615) : installer la nouvelle version,
vérifier que les spécifications, les réglages et le jeton Jira sont là ; la carte
« Accès des agents » affiche `fr.kawamind.mcp.McpBridge` dans la commande à recopier.

## 2. Gates (US1, US5)

```bash
rm -f backend/target/jacoco.exec; rm -rf backend/target/classes backend/target/test-classes
./mvnw -B verify -DskipDependencyCheck
./mvnw -B package -DskipTests
npm --prefix e2e test
```

Attendu : tout passe, hors ligne, sans crédit Copilot. Les parcours de conversation (recherches
visibles, flux, citations, plafond, arrêt, périmètre, discussion) passent sur les nouvelles
fixtures `converse-agent`.

## 3. Évaluation réelle (US1, US2) — avec l'accord du mainteneur

```bash
./mvnw -B -pl backend verify -Peval -DskipDependencyCheck
ci/check-eval.sh
```

Attendu : chaque métrique au moins à son minimum, dont `injectionResistance = 1.0`. L'empreinte
change (consignes et couche LLM).

## 4. Sécurité de la session (US2)

- `CopilotSdkChatModelTest` : pour une requête sans outil, `availableTools` vide ; avec outils,
  exactement leurs noms ; permissions refusées ; skills désactivés ; dossier de travail vide.
- Essai réel (facultatif, crédits) :
  `./mvnw -B -pl backend verify -Pspike -Dtest=CopilotToolsSpikeIT -Dsurefire.failIfNoSpecifiedTests=false -DskipDependencyCheck`,
  journal dans `$TMPDIR/spike-tools.log`.

## 5. Ajout d'un outil sans toucher à la boucle (US3)

`AgentToolsExtensionTest` déclare un outil fictif dans un AI Service de test, rejoué : l'outil
est appelé, l'étape est publiée, le plafond s'applique ; aucune classe de boucle n'est modifiée.

## 6. Vérification manuelle (dev mode `live`)

`quarkus_start` avec `-Dquarkus.profile=dev,live`, puis dans le front :
1. poser une question de connaissance : étapes de recherche, réponse en flux, citations ;
2. demander « quels modules sont touchés par … » : analyse de périmètre affichée, réponse sans
   modules non impactés nommés ;
3. cliquer « Arrêter » pendant une réponse : la partie affichée reste, état « incomplet » ;
4. consulter le journal : lignes `LLM converse-agent : … ms, … tentative(s), outils : …`, sans
   texte de la question.
