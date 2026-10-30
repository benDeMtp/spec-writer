# Quickstart : validation de bout en bout

Ce guide sert à démontrer que la feature fonctionne. Il ne remplace pas les tests automatisés.
Les références renvoient à [spec.md](spec.md), [data-model.md](data-model.md) et
[contracts/](contracts/).

## Prérequis

**Développement**
- JDK 25 et Maven 3.9.x (ou le wrapper `./mvnw`).
- Node.js 24 LTS. `frontend-maven-plugin` l'installe aussi localement.
- Un compte GitHub avec un abonnement Copilot actif.
- Uniquement si le spike S1 retient la voie B : le `client_id` de la GitHub App du projet
  (`SPEC_WRITER_GITHUB_CLIENT_ID`). Voir research.md R4.
- Une organisation GitHub qui autorise l'usage de Copilot hors IDE (politique Copilot) et, le
  cas échéant, l'application (restriction des applications tierces).
- Facultatif : un Jira Server ou Data Center ≥ 8.14 de test, un projet et un jeton personnel.
  À défaut, les tests utilisent WireMock.

**Utilisateur final** : aucun. L'installeur embarque le runtime Java.

## Commandes

```bash
# Build complet : front + back + tests unitaires et d'intégration + couverture
./mvnw verify

# Mode développement : backend avec rechargement à chaud, et front séparé avec proxy
./mvnw -pl backend quarkus:dev
npm --prefix frontend start

# Tests E2E et accessibilité (jar démarré par Playwright, LLM et Jira simulés)
npm --prefix e2e test

# Suite d'évaluation LLM (appels réels à Copilot, consomme des AI Credits)
./mvnw -pl backend verify -Peval

# Installeur de l'OS courant (JReleaser + jpackage)
./mvnw -Pdist package
```

## Scénarios de validation

| # | Scénario | Attendu | Réf. |
|---|---|---|---|
| V1 | Premier lancement de l'installeur | Le navigateur s'ouvre sur `http://127.0.0.1:47615`, l'écran de configuration s'affiche, et le questionnaire est bloqué tant que Copilot n'est pas connecté. | US1-1 |
| V2 | Cliquer sur « Se connecter à GitHub » et saisir le code sur github.com (avec le SSO de l'entreprise si demandé) | L'état passe à « connecté » sans autre manipulation. Le jeton est dans le trousseau du système (entrée du Copilot CLI en voie A, service `spec-writer` en voie B) et absent du dossier de données. | US1-2, FR-033a |
| V3 | Renseigner Jira avec un jeton faux, puis avec le bon | La première tentative affiche « jeton refusé » avec l'action à mener. La seconde affiche « OK » et la version du serveur. | US1-4, US1-6 |
| V4 | Saisir un besoin de 5 mots | Refus, avec un exemple de besoin bien formulé. | Edge cases |
| V5 | Saisir le besoin de référence `golden/01-reservation-salle.md` | Une première question s'affiche avec sa justification et la progression. Aucune question ne porte sur l'acteur déjà cité. Le consentement Copilot est demandé avant le premier envoi. | US2-1, US2-2, FR-024 |
| V6 | Passer une question, répondre aux autres, générer | Des use cases et des scénarios « En tant que / Quand / Alors » sont produits. La question passée apparaît dans les hypothèses. | US2-3 à US2-5 |
| V7 | Générer à nouveau sans rien changer | La même version est renvoyée (réponse 200, pas de nouvel appel LLM). | FR-014 |
| V8 | Fermer brutalement l'outil pendant le questionnaire, le relancer | La session est reprise à la même question, avec toutes les réponses validées. | US3-1, SC-006 |
| V9 | Modifier une réponse antérieure | Les questions devenues sans objet disparaissent, les autres réponses sont conservées. | US3-2 |
| V10 | Exporter en Markdown vers un fichier existant | Confirmation demandée avant d'écraser. Le fichier contient le résumé, les use cases, les scénarios et les hypothèses. | US4 |
| V11 | Créer le ticket Jira | L'aperçu propose Story par défaut, rien n'est créé avant confirmation, puis la clé et le lien s'affichent. | US5 |
| V12 | Rechercher un mot présent localement et dans Jira | Liste unique avec la colonne de provenance. Un élément lié n'apparaît qu'une fois, avec la provenance « les deux ». | US6-1, US6-2 |
| V13 | Modifier dans Jira le ticket lié, puis pousser une mise à jour locale | Un conflit est signalé, avec les deux versions et les trois choix. | US6-7, FR-048 |
| V14 | Couper le réseau vers Jira, puis rechercher | Les résultats spec-writer s'affichent, avec un message « Jira n'a pas répondu ». | US6-8 |
| V15 | Depuis une autre page web, envoyer un `POST` vers `http://127.0.0.1:47615/api/sessions` | Réponse 403 (Origin ou cookie absent). | research.md R14 |
| V16 | Après V1 à V14 : `grep -rE 'gho_|ghu_|<jeton Jira de test>'` sur le dossier de données, les journaux et les exports | Aucun résultat. | SC-009 |

## Emplacement des données (pour V2 et V16)

| OS | Dossier |
|---|---|
| macOS | `~/Library/Application Support/spec-writer/` |
| Windows | `%LOCALAPPDATA%\spec-writer\` |
| Linux | `~/.local/share/spec-writer/` et `~/.config/spec-writer/` |
