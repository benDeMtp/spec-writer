# Contrat REST : application

Les deux routes suivent les règles de toute l'API (R14 de 001) :
- cookie de session obligatoire ;
- `Origin` local ;
- `Content-Type: application/json` pour les écritures.

## `GET /api/application`

L'état de l'outil, utile pour quitter et pour l'écran d'arrêt. Il sert aussi de sonde de vie aux
onglets (R7).

```json
{
  "os": "MACOS",
  "installation": "INSTALLER",
  "work": [
    { "kind": "INDEXATION", "project": "Aurora" },
    { "kind": "AGENT_REPLY", "project": "Aurora", "conversation": "Facturation des avoirs" }
  ]
}
```

- **`os`** : `MACOS`, `WINDOWS` ou `LINUX`.
- **`installation`** : `INSTALLER` quand la propriété `jpackage.app-path` est présente, `SOURCES`
  sinon. Le dev mode répond aussi `SOURCES`.
- **`work`** : les travaux en cours, vides s'il n'y en a pas. `kind` vaut :
  - `INDEXATION` : indexation ou synchronisation automatique ;
  - `AGENT_REPLY` : réponse de l'agent ;
  - `REVISION` : proposition d'une nouvelle version dans une discussion ;
  - `GENERATION` : génération d'une spécification ou d'une version.
- **`project`** et **`conversation`** : des noms, affichés dans la confirmation. `conversation` est
  absent quand il ne s'applique pas. Aucun contenu saisi n'est renvoyé.

## `POST /api/application/quit`

Corps vide (`{}`). La réponse est `202 Accepted`, avec `{ "stopping": true }`. L'arrêt est planifié
après la réponse (R4) :
1. annulation des travaux en cours ;
2. attente de leur fin, 5 s au plus ;
3. arrêt du serveur.

Un deuxième appel pendant l'arrêt répond aussi `202`, sans rien relancer (cas limite « double
clic »). En test, l'arrêt du processus passe par un `ExitPort` simulé.

Aucun journal ne contient le nom des conversations. La ligne journalisée est seulement :
`Arrêt demandé depuis l'interface (n travaux interrompus)`.
