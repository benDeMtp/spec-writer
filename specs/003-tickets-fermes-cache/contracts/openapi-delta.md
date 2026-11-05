# Contrat : bilan dans l'état d'indexation (delta de `002/contracts/openapi.yaml`)

`GET /api/projects/{id}/indexations/current` et la réponse 202 de `POST /api/projects/{id}/indexations`
renvoient le schéma `Indexation`, qui gagne une propriété :

```yaml
Indexation:
  properties:
    # … propriétés de la 002 inchangées
    tickets:
      type: object
      description: >
        Bilan de l'étape Jira (FR-009 de 003). Absent si l'étape n'a pas tourné. Nombres seuls,
        sans clé ni contenu de ticket.
      properties:
        read: { type: integer, minimum: 0, description: Tickets lus dans Jira }
        keptClosed: { type: integer, minimum: 0, description: "Tickets fermés gardés sans relecture (0 en incrémental)" }
        removed: { type: integer, minimum: 0, description: Tickets retirés de l'index }
```

## Affichage (panneau de connaissance)

Quand `status` vaut `SUCCEEDED`, `FAILED` ou `CANCELLED` et que `tickets` est présent, une ligne :
« {read} ticket(s) relu(s), {keptClosed} ticket(s) fermé(s) gardé(s) sans relecture,
{removed} retiré(s) ». Le segment `keptClosed` est omis s'il vaut 0 en mode incrémental.

## Tickets proches et sous-tâches

Schémas inchangés. Pour un ticket indexé fermé, `fresh` vaut `true` et le statut est le statut
indexé (`updatedAt` / `statusDate` : date de modification indexée).

## Résumé de `POST /api/projects/{id}/indexations`

« FULL relit tout » devient : FULL relit les tickets ouverts, modifiés ou absents de l'index, et
tout le reste des sources ; les tickets fermés indexés sont gardés (FR-002).
