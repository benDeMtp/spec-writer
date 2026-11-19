# Data Model: Assistant de premier lancement et visite guidée

## Migration V29 `onboarding.sql`

```text
onboarding (une seule ligne, id = 1)
├── id            INTEGER PRIMARY KEY CHECK (id = 1)
├── current_step  TEXT NOT NULL CHECK (current_step IN ('WELCOME','COPILOT','JIRA','CONFLUENCE',
│                                                     'PROJECT','SOURCES','INDEXATION','DONE'))
├── completed_at  TEXT            -- ISO-8601, NULL tant que l'assistant n'est pas terminé
└── updated_at    TEXT NOT NULL

onboarding_step
├── step   TEXT PRIMARY KEY CHECK (step IN ('COPILOT','JIRA','CONFLUENCE','PROJECT','SOURCES','INDEXATION'))
└── state  TEXT NOT NULL CHECK (state IN ('DONE','SKIPPED'))
    -- absence de ligne = à faire
```

Remplissage (R1) : une ligne `onboarding` à `WELCOME`, puis, si l'installation est déjà utilisée,
`current_step = 'DONE'` et `completed_at = now` :

```text
déjà utilisée ⇔ copilot_settings.connected_login IS NOT NULL
             ∨ EXISTS jira_settings ∨ EXISTS confluence_settings
             ∨ EXISTS session ∨ EXISTS conversation ∨ EXISTS project_doc_source
             ∨ (SELECT count(*) FROM project) > 1
```

Pas de clé étrangère : le projet configuré est toujours le projet courant (`app_settings`).

## Domaine `domain/onboarding/`

### `OnboardingStep` (enum)

| Valeur | Obligatoire | Fait quand… |
|---|---|---|
| `WELCOME` | — | (écran d'accueil, sans état) |
| `COPILOT` | oui | Copilot `CONNECTED`, modèle choisi, accord Copilot donné |
| `JIRA` | non | test de connexion réussi (carte Jira) |
| `CONFLUENCE` | non | test de connexion réussi (carte Confluence) |
| `PROJECT` | non | nom (et clé Jira éventuelle) du projet courant enregistrés |
| `SOURCES` | non | au moins une source de documentation ou un modèle d'architecture enregistré |
| `INDEXATION` | non | indexation lancée ou constatée en cours, ou rien à indexer |
| `DONE` | — | (fin, propose la visite) |

`next()` et `previous()` suivent l'ordre ci-dessus.

### `StepState` (enum) : `TODO`, `DONE`, `SKIPPED`

### `Onboarding` (record)

- `currentStep: OnboardingStep`, `states: Map<OnboardingStep, StepState>`,
  `completedAt: Instant | null`.
- `completed()` ⇔ `completedAt != null`.
- `markDone(step)`, `skip(step)`, `goTo(step)` rendent une nouvelle valeur.
- Règles :
  - `skip(COPILOT)` → `DomainException(ONBOARDING_COPILOT_REQUIRED)` (FR-003).
  - `goTo(s)` avec `s` après `COPILOT` alors que `COPILOT` n'est pas `DONE` → même erreur.
  - `goTo(s)` sur une étape antérieure est toujours permis (FR-004) et ne change pas les états.
  - `complete(now)` exige `COPILOT = DONE` ; pose `currentStep = DONE` et `completedAt`.
  - Relance (R5) : aucune opération ; le front navigue vers `WELCOME`, les états restent.

### Port `OnboardingRepository`

`Onboarding load()`, `void save(Onboarding)`.

## Application `application/onboarding/`

### `ManageOnboarding(OnboardingRepository, SettingsRepository, ClockPort, boolean autoOpen)`

- `state()` → `OnboardingView(onboarding, autoOpen && !completed)`.
- `markDone(step)` : pour `COPILOT`, vérifie l'état réel (connecté, modèle, accord) par
  `SettingsRepository` ; sinon `ONBOARDING_COPILOT_REQUIRED`. Avance `currentStep` à l'étape
  suivante.
- `skip(step)`, `goTo(step)`, `complete()` : délèguent au domaine, enregistrent, avancent.

Les autres étapes ne sont pas vérifiées côté serveur : leurs écrans appellent les API existantes
(connexions, projet, sources, indexation), qui font leurs propres contrôles. L'assistant marque
l'étape faite après le succès de l'appel.

## Codes d'erreur

| Code | HTTP | Message |
|---|---|---|
| `ONBOARDING_COPILOT_REQUIRED` | 409 | « Connectez GitHub Copilot pour continuer : c'est la seule étape obligatoire. » |
| `ONBOARDING_UNKNOWN_STEP` | 400 | « Étape de l'assistant inconnue. » |

## Front (aucune persistance)

### Arrêt de la visite (`TourStop`)

| Champ | Rôle |
|---|---|
| `id` | identifiant stable (`conversation`, `specification`, …) |
| `route` | URL à ouvrir, ou fonction qui la calcule (spécification la plus récente) |
| `target` | valeur de `data-tour` de l'élément désigné |
| `title`, `text` | contenu de la bulle |
| `fallbackTarget`, `fallbackText` | quand l'élément n'existe pas encore (FR-015) |

L'état de la visite (arrêt courant, URL de départ) vit dans `TourService` pendant la visite et
n'est jamais enregistré.
