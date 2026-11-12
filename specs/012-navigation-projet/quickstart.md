# Quickstart : valider la navigation par projet (012)

## Prérequis

- Environnement de `CLAUDE.md` : JDK 25 Mandrel en tête du `PATH`, `env -u JAVA_TOOL_OPTIONS`.
- Jar construit : `./mvnw -B package -DskipTests`.
- E2E : `npm --prefix e2e test`. Le jar tourne sur 47699, WireMock Jira sur 47698. Le LLM est
  rejoué : aucun appel au vrai Copilot.
- Données E2E : le projet par défaut (`00000000-…-0001`, clé `FACT`) et un second projet
  « Recette » sans clé Jira, créé par le test.

## Scénarios

Chaque scénario correspond à un test de `e2e/tests/us15-navigation.spec.ts`.

1. **Menu en deux blocs** (US1-1, FR-001, FR-002)
   - Action : ouvrir `/`.
   - Résultat :
     - le groupe « Projet » contient le sélecteur et cinq entrées, dont « Tickets Jira, projet
       FACT » ;
     - le groupe « Application » contient Projets, Configuration (avec deux pastilles) et
       Journal ;
     - axe ne relève aucune violation.
2. **Titres** (US1-2, US1-3, FR-003)
   - Action : ouvrir Spécifications, puis choisir « Recette » dans le sélecteur.
   - Résultat :
     - le `h1` passe de « Spécifications · <projet> » à « Spécifications · Recette » ;
     - le titre de l'onglet contient « Recette ».
3. **Entrée active sur les pages filles** (US1-4, FR-004)
   - Action : ouvrir `/jira/FACT-1`, puis une spécification.
   - Résultat : « Tickets Jira », puis « Spécifications », portent `aria-current="page"`.
4. **Menu replié** (US1-5, FR-005)
   - Action : fenêtre de 600 px de large, bouton « Menu », choix de « Journal ».
   - Résultat :
     - le panneau s'ouvre (`aria-expanded="true"`), puis se ferme après le choix ;
     - axe ne relève aucune violation, menu ouvert comme fermé.
5. **Réglages du projet** (US2-1, US2-2, FR-006)
   - Action :
     - ouvrir « Réglages du projet » ;
     - modifier la clé Jira de Recette en `REC` (WireMock : projet inconnu) ;
     - enregistrer.
   - Résultat :
     - les quatre sections sont présentes ;
     - l'avertissement de clé inconnue s'affiche ;
     - le menu affiche « REC ».
6. **Réglages non enregistrés** (FR-013, cas limite)
   - Action :
     - saisir une description sans enregistrer ;
     - choisir un autre projet dans le sélecteur ;
     - annuler.
   - Résultat :
     - le dialogue « Quitter sans enregistrer ? » s'affiche ;
     - après l'annulation, le sélecteur revient au projet courant.
7. **Connaissance du projet** (US2-3, FR-007)
   - Action : ouvrir « Connaissance du projet ».
   - Résultat : les compteurs, la recherche d'essai et les liens « ADR du projet » et « Réglages
     du projet » sont présents.
8. **Ancienne adresse** (US2-7, FR-009)
   - Action : ouvrir `/projets/<id de Recette>/contexte` depuis le projet par défaut.
   - Résultat :
     - l'adresse devient `/projet/connaissance` ;
     - Recette est le projet courant ;
     - l'annonce « Projet courant : Recette » s'affiche.
9. **Tickets Jira sans clé** (US2-4, FR-010)
   - Action : avec Recette sans clé et sans clé par défaut, ouvrir Tickets Jira.
   - Résultat : le lien « Renseigner la clé Jira du projet » mène à `/projet/reglages#jira`, et le
     champ « Clé Jira » est visible.
10. **Projet de destination** (US2-6, FR-011)
    - Action : ouvrir `/jira/FACT-1`.
    - Résultat : le texte « La spécification sera ajoutée au projet <projet par défaut>. »
      s'affiche.
11. **Bascule** (US3-1 à US3-3, FR-012)
    - Action :
      - avec Recette courant, ouvrir l'adresse d'une spécification du projet par défaut ;
      - puis ouvrir Spécifications.
    - Résultat :
      - l'annonce « Projet courant : <projet par défaut> » s'affiche et le sélecteur suit ;
      - la liste est celle du projet par défaut ;
      - rouvrir la même spécification ne produit aucune annonce.
12. **Proposition en attente et sélecteur** (US3-4)
    - Action : avec une proposition de la discussion en attente (fixtures de la 011), choisir un
      autre projet.
    - Résultat : le dialogue « Quitter sans appliquer la proposition ? » s'affiche ; après
      l'annulation, rien ne change.
13. **Configuration** (US4-1 à US4-4, FR-014, FR-015, FR-017)
    - Action :
      - ouvrir Configuration ;
      - enregistrer Jira sans clé par défaut ;
      - tester la connexion avec le projet par défaut courant.
    - Résultat :
      - les sections « Connexions », puis « Avancé », s'affichent, avec les phrases de portée ;
      - l'enregistrement est accepté ;
      - le résultat indique « Projet Jira vérifié : FACT ».
14. **Clé par défaut en repli** (US4-5, FR-016)
    - Action : clé par défaut `FACT`, Recette sans clé courant, ouvrir Tickets Jira.
    - Résultat : « clé par défaut de l'application » s'affiche.
15. **Session perdue** (US5-1, FR-018)
    - Action : nouveau contexte de navigateur sans cookie, ouvrir `/`.
    - Résultat :
      - seul le titre « Relancez spec-writer depuis son raccourci » s'affiche, sans navigation
        principale ;
      - axe ne relève aucune violation.

## Gates (avant commit)

```bash
export JAVA_HOME=~/.sdkman/candidates/java/25.0.2.r25-mandrel PATH=$JAVA_HOME/bin:$PATH
rm -f backend/target/jacoco.exec
env -u JAVA_TOOL_OPTIONS ./mvnw -B verify -DskipDependencyCheck
env -u JAVA_TOOL_OPTIONS ./mvnw -B package -DskipTests
npm --prefix e2e test
```

## Essai manuel

1. Relancer le jar installé, puis l'ouvrir depuis son raccourci.
2. Parcourir les scénarios 1, 5, 11 et 13 avec de vrais projets.
3. Fermer le jar, puis recharger la fenêtre : l'écran « Relancez spec-writer » s'affiche.
