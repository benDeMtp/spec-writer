# Data model : cycle de vie de l'outil

Aucune migration : rien n'est gardé en base. Les nouveaux éléments sont des fichiers du dossier de
données et des états en mémoire.

## Fichiers du dossier de données

| Fichier | Contenu | Vie |
|---|---|---|
| `instance.lock` | vide ; porte le verrou exclusif (R1) | créé au premier lancement, jamais supprimé ; seul le verrou compte |
| `instance.sock` | socket Unix du canal (R2) | créé par l'instance qui tient le verrou, supprimé à l'arrêt propre, remplacé au lancement suivant après un arrêt brutal |

Les deux fichiers héritent des droits du dossier (`rwx------`). Aucun secret n'y est écrit.

## États en mémoire

### Instance (lanceur)

```
DÉMARRAGE ─ verrou obtenu ──────────────────▶ INSTANCE ─ Quitter/⌘Q/Ctrl+C ─▶ ARRÊT ─▶ fin
    │
    └─ verrou pris ─▶ SECOND LANCEMENT
                        ├─ OPENED ou PRINT ─────▶ fin (code 0)
                        ├─ pas de canal ou STOPPING ─▶ nouvel essai pendant 15 s ─▶ DÉMARRAGE
                        └─ pas de réponse en 5 s ─▶ message ─▶ fin (code 3)
```

### Travail en cours (`RunningWork.Item`)

- `kind` : `INDEXATION`, `AGENT_REPLY`, `REVISION` ou `GENERATION`.
- `projectName` : nom du projet, jamais son contenu.
- `conversationTitle` : facultatif.

Sources des travaux en cours :
- `Indexations` : instantanés au statut `RUNNING` ;
- `TurnEvents` : tours ouverts, avec leur nature, enregistrée à `open` (ajout) ;
- `LongWorkTracker` : générations synchrones en cours, comptées par le filtre `@LongWork`.

### Arrêt (`GracefulStop`)

`EN MARCHE → ARRÊT DEMANDÉ → TRAVAUX ANNULÉS → (attente ≤ 5 s) → SERVEUR ARRÊTÉ`.

`ARRÊT DEMANDÉ` est atteint une seule fois : un second Quitter, ou un Quitter pendant ⌘Q, est sans
effet.

### Onglet (`LifecycleStore`, front)

`running | stopped`, plus les valeurs `os` et `installation` lues par `GET /api/application`. Le
passage à `stopped` est définitif pour l'onglet.
