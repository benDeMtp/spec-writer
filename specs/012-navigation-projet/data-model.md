# Data Model: Navigation par projet et réglages de l'application (012)

Aucune nouvelle table. Une table est reconstruite, deux énumérations et un résultat de test gagnent
des valeurs, et le front gagne quelques états.

## Persistance

### `jira_settings` (reconstruite par V23)

| Colonne | Avant (V2) | Après (V23) |
|---|---|---|
| `default_project_key` | `TEXT NOT NULL` | `TEXT` (nullable) |

Les autres colonnes et contraintes ne changent pas. La ligne existante est copiée telle quelle.

Étapes de `V23__jira_default_key_optional.sql` : `CREATE TABLE jira_settings_new (...)`, puis
`INSERT INTO jira_settings_new SELECT * FROM jira_settings`, puis `DROP TABLE jira_settings`, puis
`ALTER TABLE jira_settings_new RENAME TO jira_settings`.

## Domaine

### `JiraSettings.defaultProjectKey`

- Nullable.
- `create(...)` :
  - une clé vide ou blanche devient `null` ;
  - une clé non vide doit respecter `[A-Z][A-Z0-9_]+`, avec le message actuel.

### Clé Jira effective (inchangée, désormais possiblement absente)

```
effective(project) = project.jiraProjectKey ?? settings.jira.defaultProjectKey ?? none
```

- `JiraAccess.Target.projectKey` peut être `null`.
- `Target.requireProject()` renvoie la clé, ou lève `JIRA_PROJECT_MISSING`.

### `ErrorCode`, nouvelles valeurs

| Code | HTTP | Action | Message |
|---|---|---|---|
| `JIRA_PROJECT_MISSING` | 409 | `OPEN_PROJECT_SETTINGS` | « Aucun projet Jira n'est associé à ce projet. Renseignez sa clé Jira dans les réglages du projet. » |
| `SESSION_REQUIRED` | 403 | `NONE` | « Cette fenêtre n'est plus reliée à spec-writer. Relancez-la depuis son raccourci. » |

### `ErrorAction`, nouvelle valeur

`OPEN_PROJECT_SETTINGS`. Le front l'envoie vers `/projet/reglages#jira`.

### `JiraTestResult`, nouvelle valeur

`OK_NO_PROJECT` : « Connexion à Jira réussie. Aucun projet Jira n'est associé au projet courant :
renseignez sa clé dans les réglages du projet. »

### `TestJiraConnection.Outcome`

Le champ `projectKey` est ajouté. C'est la clé vérifiée, ou `null` avec `OK_NO_PROJECT` et les
échecs des deux premières étapes.

## Front

### `ProjectsStore`

Inchangé. `current` reste la seule source du projet courant.

### `ProjectFollower`

| Champ ou méthode | Rôle |
|---|---|
| `notice: Signal<string \| null>` | Annonce « Projet courant : <nom> », effacée après 6 s ou à la navigation suivante. |
| `follow(projectId): Promise<void>` | Ne fait rien si `projectId` est le projet courant. Sinon, appelle `select`, puis `notice`. |

### `LeaveGuard`

| Champ ou méthode | Rôle |
|---|---|
| `register(check): () => void` | Enregistre la vérification d'une page et renvoie de quoi la retirer. |
| `confirm(): Promise<boolean>` | Appelle la vérification enregistrée s'il y en a une, sinon `true`. |

### `NavEntry` (pure, `features/shell/nav.ts`)

`'conversation' | 'specifications' | 'jira' | 'knowledge' | 'project-settings' | 'projects' |
'configuration' | 'journal' | null`, calculée par `activeEntry(path)`. Les préfixes sont dans
research.md R2.

### `LaunchState`

Inchangé (`'ready' | 'exchanged' | 'rejected'`). `'rejected'` est aussi atteint sur réponse
`SESSION_REQUIRED`.

### Données des routes

`data.scope = 'project'` sur les routes `''`, `conversations/:cid`, `sessions`, `jira`,
`projet/reglages`, `projet/connaissance` et `projet/connaissance/adr`. La `TitleStrategy` les lit.

## Transitions du projet courant

```
[A courant] --sélecteur (LeaveGuard.confirm = vrai)---> [B courant]   navigation vers la liste si page adressée par id
[A courant] --sélecteur (confirm = faux)--------------> [A courant]   valeur du select restaurée
[A courant] --ouverture spec/questionnaire/conv. de B-> [B courant]   annonce « Projet courant : B »
[A courant] --ancienne adresse projets/B/contexte-----> [B courant]   redirection /projet/connaissance
[A courant] --suppression de A (page Projets)---------> [premier projet par nom]   règle existante
```
