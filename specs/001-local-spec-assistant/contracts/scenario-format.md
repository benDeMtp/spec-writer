# Contrat : format des scénarios d'acceptation

Applique le principe II de la constitution, FR-012 et FR-015. Le format est destiné à des
lecteurs humains. Ce n'est pas du Gherkin exécutable : le projet ne produit ni fichier `.feature`
ni mots-clés `Feature` / `Scenario`.

## Structure

```text
Scénario <code> : <titre>   [Hypothèse]?
En tant que <acteur>
(Étant donné <contexte>
 (Et <contexte>)*)?
Quand <action>
(Et <action>)*
Alors <résultat attendu>
(Et <résultat attendu>)*
```

Mots-clés selon la langue de la session :

| Rôle | fr | en |
|---|---|---|
| acteur | En tant que | As a |
| contexte (facultatif) | Étant donné | Given |
| action | Quand | When |
| résultat | Alors | Then |
| suite | Et | And |

## Règles du contrôle de structure

1. `asA` est présent, et non vide une fois les espaces retirés.
2. `when` contient au moins une entrée non vide.
3. `then` contient au moins une entrée non vide.
4. `given` est facultatif, mais s'il est présent, aucune de ses entrées n'est vide.
5. `useCaseCode` désigne un use case de la même version.
6. Le code suit `SC-<nn>-<nn>` et est unique dans la version.
7. Aucun champ ne dépasse 1 000 caractères.
8. Un scénario `isAssumption = true` s'affiche avec la mention « Hypothèse ».

En cas de violation, l'outil n'affiche pas la version : il réessaie la génération ou affiche une
erreur (voir [data-model.md](../data-model.md), AcceptanceScenario).

## Rendu Markdown (export et Jira)

```markdown
#### SC-01-02 : Paiement refusé
- **En tant que** client connecté
- **Étant donné** un panier de 3 articles
- **Quand** je valide le paiement
- **Et** ma banque refuse la transaction
- **Alors** un message m'indique que le paiement a échoué
- **Et** mon panier est conservé
```

Le texte saisi par l'utilisateur est échappé avant le rendu : en Markdown pour l'affichage et
l'export, puis en wiki markup Jira lors de la conversion (edge case « caractères spéciaux »).
