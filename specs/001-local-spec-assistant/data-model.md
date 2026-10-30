# Data Model : Assistant local de rédaction de spécifications

**Date** : 2026-10-30 | **Plan** : [plan.md](plan.md) | **Spec** : [spec.md](spec.md)

Le modèle est stocké dans une base SQLite locale (voir [research.md](research.md) R7), à
l'exception des secrets, qui restent dans le trousseau du système (R8). Les identifiants internes
sont des UUID. Les dates sont en UTC, au format ISO 8601.

## Vue d'ensemble

```text
Session 1───* Question 1───0..1 Answer
   │
   ├──1───* SpecificationVersion 1───* UseCase 1───* AcceptanceScenario
   │                               ├──* Assumption
   │                               └──* Requirement
   └──0..1 LinkedJiraTicket

ServiceSettings (singleton) ── CopilotSettings, JiraSettings
ConsentRecord (historique)
SecretStore (trousseau du système, hors base) ── clés : copilot.access, copilot.refresh, jira.pat
```

## Session

Un travail de spécification. Contient le besoin initial.

| Champ | Type | Règles |
|---|---|---|
| id | UUID | |
| name | texte | 1 à 120 caractères ; par défaut, les premiers mots du besoin. Renommable (FR-017). |
| need | texte | 10 mots minimum, 10 000 caractères maximum (FR-001, edge cases). Non modifiable après le début du questionnaire : pour le changer, on crée une nouvelle session. Pour une session `JIRA_IMPORT`, `need` est la description Jira convertie en Markdown, tronquée à 10 000 caractères avec un avertissement. |
| language | `fr` ou `en` | Détectée à partir du besoin (FR-015), modifiable avant la génération. |
| origin | `LOCAL` ou `JIRA_IMPORT` | `JIRA_IMPORT` quand la session est créée à partir d'un ticket (FR-046). |
| state | enum | voir le cycle de vie ci-dessous |
| createdAt, updatedAt | date | `updatedAt` est mis à jour à chaque réponse (FR-016). |

**Cycle de vie**

```text
QUESTIONING ──(l'utilisateur conclut ou toutes les questions sont traitées)──▶ READY_TO_GENERATE
READY_TO_GENERATE ──(génération réussie)──▶ GENERATED
GENERATED ──(modification d'une réponse)──▶ QUESTIONING
any ──(suppression confirmée)──▶ [supprimée physiquement, ainsi que ses versions]
```

## Question

| Champ | Type | Règles |
|---|---|---|
| id | UUID | |
| sessionId | UUID | |
| order | entier | Ordre d'affichage, stable. |
| theme | enum | `ACTORS`, `GOAL`, `NOMINAL_FLOW`, `BUSINESS_RULES`, `ERRORS`, `EDGE_CASES`, `NON_FUNCTIONAL`, `CONTRADICTION` (FR-003, FR-008) |
| batch | entier | Lot thématique de 5 questions au maximum (FR-004). |
| label | texte | 500 caractères maximum. |
| rationale | texte | Pourquoi la question est posée (FR-005). Obligatoire. |
| suggestions | liste de textes | 0 à 5 entrées. |
| defaultValue | texte, nullable | Valeur retenue si la question est passée (FR-006). |
| dependsOn | liste d'UUID | Questions et réponses dont dépend cette question (FR-007). |
| state | enum | `PENDING`, `ANSWERED`, `SKIPPED`, `OBSOLETE` |

**Transitions** : `PENDING` passe à `ANSWERED` ou `SKIPPED`. `ANSWERED` et `SKIPPED` repassent à
`PENDING` quand l'utilisateur revient sur la question. Tout état devient `OBSOLETE` quand une
réponse dont la question dépend change et la rend sans objet (FR-007). Les questions `OBSOLETE`
sont conservées pour l'historique, mais ne sont plus affichées.

## Answer

| Champ | Type | Règles |
|---|---|---|
| questionId | UUID | Une réponse au plus par question. |
| value | texte | 5 000 caractères maximum. |
| isAssumption | booléen | `true` si la question a été passée : la valeur par défaut est alors retenue (FR-006). |
| answeredAt | date | |

## SpecificationVersion

Résultat d'une génération (FR-018). Une version n'est jamais modifiée : toute modification crée
une nouvelle version.

| Champ | Type | Règles |
|---|---|---|
| id | UUID | |
| sessionId | UUID | |
| number | entier | 1, 2, 3… par session |
| title | texte | 120 caractères maximum. |
| summary | texte | Résumé du besoin. |
| inputFingerprint | texte | SHA-256 de (besoin, réponses, version de l'outil, langue). À empreinte identique, l'outil réutilise la version existante (FR-014, R12). |
| origin | enum | `GENERATED`, `AI_EDIT` (FR-044), `JIRA_IMPORT`, `CONFLICT_MERGE` |
| markdown | texte | Rendu Markdown déterministe, calculé à partir de la structure. Sert à l'export (FR-019) et à l'indexation plein texte. |
| createdAt | date | |

Contient des **UseCase**, des **Assumption** et des **Requirement**.

Une version `JIRA_IMPORT` (import d'un ticket, ou résolution de conflit `TAKE_JIRA`) est produite
par l'opération `structureImportedText` du port LLM
([contracts/llm-port.md](contracts/llm-port.md)). Elle passe le même contrôle de structure que
les autres versions. Si la structuration échoue après les tentatives, l'import est refusé avec
l'erreur `IMPORT_UNSTRUCTURABLE`, et Jira n'est pas modifié.

## UseCase

| Champ | Type | Règles |
|---|---|---|
| code | texte | `UC-01`, `UC-02`…, attribué par le code (R12). |
| title, actor, goal | texte | Obligatoires. |
| preconditions | liste de textes | |
| nominalFlow | liste ordonnée de textes | Une étape au minimum. |
| alternatives | liste de textes | |
| sourceRef | SourceRef | Obligatoire (FR-011). |

**SourceRef** : `{ kind: NEED_EXCERPT | ANSWER | ASSUMPTION, questionId?, excerpt }`. Un use case
dont la source est une hypothèse doit aussi apparaître dans la liste des hypothèses.

## AcceptanceScenario

Format défini dans [contracts/scenario-format.md](contracts/scenario-format.md).

| Champ | Type | Règles |
|---|---|---|
| code | texte | `SC-01-01` (use case 01, scénario 01), attribué par le code. |
| useCaseCode | texte | Doit référencer un UseCase de la même version (FR-011). |
| title | texte | Obligatoire. |
| kind | enum | `NOMINAL`, `ERROR`, `EDGE` |
| asA | texte | « En tant que ». Obligatoire, non vide. |
| given | liste de textes | « Étant donné ». Facultatif. |
| when | liste de textes | « Quand ». Au moins une entrée non vide. |
| then | liste de textes | « Alors ». Au moins une entrée non vide. |
| isAssumption | booléen | `true` si le scénario repose sur une hypothèse (FR-013). |

**Validation** (FR-012) : si un scénario échoue au contrôle de structure, toute la génération est
rejetée. L'outil fait alors une nouvelle tentative avec le message d'erreur (2 au maximum), puis
affiche une erreur à l'utilisateur. Il ne présente jamais de scénario invalide.

## Assumption

| Champ | Type | Règles |
|---|---|---|
| code | texte | `H-01`… |
| statement | texte | Obligatoire. |
| origin | enum | `SKIPPED_QUESTION` (avec questionId), `TOOL_INFERENCE` |

## Requirement

| Champ | Type | Règles |
|---|---|---|
| code | texte | `EF-01` (exigence fonctionnelle) ou `ET-01` (exigence technique) |
| category | enum | `FUNCTIONAL`, `TECHNICAL` (FR-010) |
| statement | texte | Obligatoire. |

## LinkedJiraTicket

Lien entre une session et un ticket Jira (FR-038, FR-042, FR-046 à FR-048). Un ticket au plus par
session, et une session au plus par ticket : la clé est unique.

| Champ | Type | Règles |
|---|---|---|
| sessionId | UUID | unique |
| issueKey | texte | Format `[A-Z][A-Z0-9_]+-\d+`, unique. |
| issueUrl | texte | Construite à partir de l'adresse du serveur configuré. |
| issueType | texte | |
| linkedVersionNumber | entier | Version de la spécification envoyée ou importée en dernier. |
| lastKnownUpdated | date | Champ `updated` de Jira à la dernière lecture ou au dernier envoi. |
| lastKnownDescriptionHash | texte | SHA-256 de la description Jira à la dernière lecture ou au dernier envoi. |
| createdOrImportedAt, lastPushedAt | date | |
| syncState | enum | `IN_SYNC`, `LOCAL_AHEAD` (version locale plus récente que la dernière envoyée), `CONFLICT` |

**Détection de conflit** (FR-048) : avant chaque envoi, l'outil relit `updated` et la description.
Si l'un ou l'autre diffère des valeurs mémorisées, l'état passe à `CONFLICT` et l'utilisateur
choisit : `OVERWRITE`, `TAKE_JIRA` (crée une version `JIRA_IMPORT`) ou `AI_MERGE` (crée une
version `CONFLICT_MERGE`, à valider).

## ServiceSettings (singleton, hors secrets)

**CopilotSettings**

| Champ | Type | Règles |
|---|---|---|
| connectedLogin | texte, nullable | Login GitHub après connexion. |
| subscriptionState | enum | `UNKNOWN`, `ACTIVE`, `NO_COPILOT` (User Story 1, scénario 3) |
| model | texte, nullable | Identifiant du modèle choisi (FR-030c). |
| availableModels | liste de textes | Modèles autorisés pour le compte, relus à chaque connexion et à chaque test. |
| modelPreference | liste de textes | Ordre de préférence pour le choix par défaut (configuration de l'application). |
| tokenExpiresAt | date, nullable | |
| authMode | enum | `COPILOT_CLI` (voie A : jeton détenu par le Copilot CLI, jamais lu par spec-writer) ou `GITHUB_APP` (voie B : jetons dans le trousseau, clés `copilot.access` et `copilot.refresh`). Voir research.md R4. |
| secretPersistence | enum | `KEYCHAIN`, `MEMORY_ONLY` (FR-033b, voie B) |

**JiraSettings**

| Champ | Type | Règles |
|---|---|---|
| enabled | booléen | |
| serverUrl | URL | `https://` recommandé. `http://` est accepté après un avertissement confirmé (FR-035). Pas de chemin de requête ni de fragment. |
| defaultProjectKey | texte | `[A-Z][A-Z0-9_]+` |
| caCertificatePath | chemin, nullable | Certificat PEM d'une autorité interne (R10). |
| serverVersion | texte, nullable | Lue au test de connexion ; inférieure à 8.14, elle est refusée. |
| lastTest | `{at, result: OK | UNREACHABLE | TOKEN_REJECTED | PROJECT_NOT_FOUND | MISSING_PERMISSION | UNSUPPORTED_VERSION}` | FR-032 |
| secretPersistence | enum | `KEYCHAIN`, `MEMORY_ONLY` |

Le jeton Jira et les jetons GitHub ne sont **jamais** dans ces tables (FR-033a). Ils sont dans le
trousseau, sous le service `spec-writer` et les clés `jira.pat`, plus `copilot.access` et
`copilot.refresh` en voie B. En voie A, le jeton GitHub est géré par le Copilot CLI.

## ConsentRecord

Consentement à l'envoi de données à un service tiers (FR-024).

| Champ | Type | Règles |
|---|---|---|
| service | `COPILOT` ou `JIRA` | |
| granted | booléen | |
| at | date | Historique conservé. Un retrait de consentement bloque les appels suivants. |

Le consentement Copilot est demandé avant le premier envoi d'un besoin. Le consentement Jira est
demandé à la première activation de Jira.

## Index plein texte

Table virtuelle FTS5 `spec_search(session_id, title, need, markdown)`, alimentée par des triggers
sur Session et sur la dernière SpecificationVersion de chaque session (FR-040). Tokenizer
`unicode61 remove_diacritics 2`, pour qu'une recherche « regle » trouve aussi « règle ».
