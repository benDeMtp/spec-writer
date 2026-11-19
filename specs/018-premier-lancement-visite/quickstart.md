# Quickstart : valider l'assistant et la visite

Prérequis communs : JDK 25 en tête du `PATH`, jar construit par `./mvnw -B verify
-DskipDependencyCheck`. Les simulateurs (GitHub, Copilot rejoué, Jira WireMock) sont ceux des E2E.

## Q1. Installation vierge, parcours complet (US1, SC-001)

1. Lancer le jar avec un dossier de données neuf et le profil `e2e` en forçant
   `-Dspecwriter.onboarding.auto-open=true`, puis ouvrir l'URL de lancement.
2. Attendu : l'assistant s'ouvre sur l'accueil (étapes, facultatives signalées, durée indicative).
3. Copilot : « Suivant » refusé tant que la connexion n'est pas faite ; connexion simulée, modèle
   choisi, accord donné → étape faite.
4. Jira : adresse du WireMock, jeton, test réussi → étape faite. Confluence : « Passer ».
5. Projet : nommer « Aurora », clé `FACT` → le « Projet par défaut » est renommé, aucun second
   projet dans l'écran Projets.
6. Sources : ajouter le site Antora de démonstration (`e2e/fixtures/jira/__files/antora`).
7. Indexation : progression visible source par source ; « Ouvrir la conversation » actif avant
   la fin.
8. Attendu : conversation du projet Aurora ; la visite est proposée.

## Q2. Tout passer (US1, SC-002)

Même départ que Q1, connexion Copilot faite, puis « Passer » à chaque étape. L'étape
d'indexation explique que l'agent ne connaîtra pas le projet et où ajouter des sources. Fin sur la
conversation en moins de 3 minutes.

## Q3. Reprise (US1-9, SC-004)

Pendant Q1, à l'étape Sources : fermer l'onglet, arrêter puis relancer le jar, rouvrir l'URL de
lancement. Attendu : assistant à l'étape Sources, Copilot et Jira marqués faits, Confluence passée.

## Q4. Installation déjà utilisée (US1-10, SC-006)

Lancer le jar sur une copie de la base d'une installation existante (au moins une spécification).
Attendu : pas d'assistant, l'outil s'ouvre sur la conversation. `GET /api/onboarding` →
`completed: true`. Configuration › « Relancer l'assistant » l'ouvre à l'accueil, chaque étape
montrant l'état réel.

## Q5. Visite guidée (US2, SC-003)

1. Menu › « Visite guidée ». Attendu : arrêt 1 sur 11, bulle sur la zone de saisie, focus dans la
   bulle, annonce lue par le lecteur d'écran.
2. Avancer au clavier jusqu'au bout : chaque arrêt ouvre son écran et désigne un élément visible ;
   sur un projet sans spécification, les arrêts 4 à 6 restent sur « Spécifications » avec le
   texte de remplacement.
3. Relancer, avancer jusqu'à l'arrêt 7, Échap : retour à l'écran de départ.
4. Onglet réseau ouvert pendant toute la visite : aucune requête autre que `GET`.

## Q6. Installation depuis les sources (US3, SC-005)

Sur un clone neuf, dans un compte sans JDK 25 ni Copilot CLI, suivre seulement la section
« Installer depuis les sources » du README jusqu'à l'assistant. Noter chaque écart (commande,
message, version) et corriger le guide.

## Tests automatisés

- Backend : `OnboardingTest` (domaine), `ManageOnboardingTest`, `OnboardingSqliteRepositoryIT`,
  `OnboardingResourceIT`, `V29MigrationIT` (installation vierge contre installation utilisée).
- Front : specs Vitest de l'assistant, de chaque étape, de `TourService` (ordre, replis, Échap,
  aucune écriture) et des composants extraits.
- E2E : `us18-premiers-pas.spec.ts` (Q1 à Q3 sur une seconde instance, Q4 sur l'instance
  partagée) et `us18-visite.spec.ts` (Q5, `axe` à chaque arrêt).
