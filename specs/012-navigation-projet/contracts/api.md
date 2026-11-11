# Contrat : navigation par projet et réglages de l'application (API)

Ce document liste les ajouts et changements par rapport à
`specs/002-projets-contexte-mcp/contracts/openapi.yaml` et aux contrats des features 010 et 011.
Aucune nouvelle route n'est créée.

## Réglages Jira

- `PUT /api/settings/jira` `{ serverUrl, defaultProjectKey?, token?, caCertificatePath?,
  acceptInsecureHttp?, persistence? }`.
  - `defaultProjectKey` devient facultatif : absent, `null` ou vide, il est enregistré à `null`.
  - Une clé non vide doit respecter `[A-Z][A-Z0-9_]+`, sinon
    `422 VALIDATION_FAILED` (message actuel).
- `GET /api/settings` : `jira.defaultProjectKey` peut valoir `null`.
- `POST /api/settings/jira/test` renvoie `200 { result, serverVersion, userMessage, projectKey }`.
  - `projectKey` est la clé effective du projet courant qui a été vérifiée : sa propre clé, sinon
    la clé par défaut.
  - Le nouveau `result` `OK_NO_PROJECT` signifie que le serveur et le jeton sont valides, mais
    qu'aucune clé effective n'existe. `projectKey` vaut alors `null`.
  - Les autres résultats sont inchangés. `PROJECT_NOT_FOUND` et `MISSING_PERMISSION` portent sur
    `projectKey`.

## Projet Jira manquant

Ces opérations peuvent désormais renvoyer `409 { code: "JIRA_PROJECT_MISSING", action:
"OPEN_PROJECT_SETTINGS", userMessage }` quand Jira est configuré mais que le projet de la
spécification n'a pas de clé effective :
- `POST /api/sessions/{id}/jira/preview` ;
- `POST /api/sessions/{id}/jira` (création d'un ticket) ;
- `GET .../jira/issue-types` et la lecture des champs obligatoires (`TicketForm`).

L'ordre des refus est inchangé : accord (`428`), puis configuration (`JIRA_NOT_CONFIGURED`), puis
jeton (`JIRA_TOKEN_REJECTED`), puis projet (`JIRA_PROJECT_MISSING`).

La recherche unifiée ne renvoie pas d'erreur sans clé effective : elle omet la part Jira, comme
lorsque Jira n'est pas configuré.

## Session perdue

Toute route `/api/**` hors agents et hors `/api/session-auth` répond, sans cookie de session valide :
`403 { code: "SESSION_REQUIRED", action: "NONE", userMessage }`.
- C'est le code qu'il faut détecter pour afficher l'écran de session perdue.
- `POST /api/session-auth` répond `403 SESSION_REQUIRED` si le jeton de lancement est refusé.
- Les refus d'hôte ou d'origine gardent `403 FORBIDDEN`.
- L'API des agents garde son propre contrôle (`AgentSecretFilter`, `FORBIDDEN`).

## Inchangé

- `PUT /api/projects/current` `{ projectId }` sert à toutes les bascules :
  - le sélecteur ;
  - l'ouverture d'une page d'un autre projet ;
  - les anciennes adresses.
- `PUT /api/projects/{id}` : nom, description, clé Jira, avec l'avertissement `jiraKeyWarning`.
- `GET /api/projects` et `GET /api/projects/current` : `effectiveJiraProjectKey` peut valoir
  `null` quand il n'y a ni clé de projet ni clé par défaut.
