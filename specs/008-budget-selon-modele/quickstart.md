# Quickstart : budget de contexte selon le modèle

## Scénarios automatisés

1. `KnowledgeBudgetTest` : inconnu, 32 000 et 64 000 tokens, 128 000 (matrice de 30 000 transmise),
   10 millions et plus (inconnu), plafond de 200 000 ; nombres d'éléments inchangés.
2. `SelectKnowledgeTest` : avec un grand modèle, document désigné de 30 000 transmis en entier,
   proportions gardées, fiche prioritaire ; avec `STANDARD`, sélection identique à aujourd'hui
   (tous les tests existants inchangés) ; empreinte `budget=` hors `STANDARD`.
3. `MentionQueriesTest` : `betterModel` le moins coûteux qui suffit ; aucun si aucun ne suffit.
4. `SdkCopilotAccountGateway` / `PollCopilotConnection` : capacités lues et rangées, aberrantes
   traitées comme inconnues ; `SettingsPersistenceIT` : colonne `model_capacities`.
5. Front (Vitest) : options avec capacité et coefficient, « capacité inconnue », ligne du budget ;
   suggestion dans l'état d'une mention.
6. E2E : configuration (modèles simulés avec capacités, axe) ; `bash ci/check-eval.sh` reste vert.

## Essai manuel

1. Configuration : choisir un modèle d'au moins 128 000 tokens ; lire sa capacité, son coût et
   « Connaissance transmise : jusqu'à … caractères ».
2. Session « planning intelligent » : la mention de la matrice indique « transmis à l'IA » ;
   régénérer : la spécification exploite toutes les feuilles.
3. Revenir à un petit modèle : la mention indique « transmis en partie » et propose le grand.
