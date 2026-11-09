# Recherche : budget de contexte selon le modèle

## R1 — Ce que Copilot dit des modèles

**Constat** : le SDK Copilot (`copilot-sdk-java` 1.0.28-preview.0) rend, pour chaque `ModelInfo` :
`getCapabilities().getLimits()` → `ModelLimits.getMaxPromptTokens()` (`Integer`, peut être nul) et
`getMaxContextWindowTokens()` ; `getBilling()` → `ModelBilling.getMultiplierOpt()`. L'outil n'en
garde aujourd'hui que l'identifiant, le nom et l'état (`GitHubAuthenticator.ModelOption`), et ne
persiste que les identifiants (`CopilotSettings.availableModels`, colonne JSON `available_models`),
mis à jour à la connexion (`PollCopilotConnection`).

**Décision** : `ModelOption` gagne `promptTokens`, `contextTokens` (`Integer`, nuls si inconnus) et
`multiplier` (`Double`). `CopilotSettings` gagne `capacities` (liste de `ModelCapacity`), rangée
avec les identifiants, à la même occasion ; migration `V16__model_capacities.sql` (colonne
`model_capacities TEXT NOT NULL DEFAULT '[]'`). Valeurs aberrantes (≤ 0, > 10 000 000 de tokens,
coefficient négatif) → inconnues (FR-002).

**Alternatives** : interroger Copilot à chaque génération (rejeté : `listModels` attend jusqu'à
60 s, et une génération ne doit pas dépendre d'un appel de plus) ; cache en mémoire seul (rejeté :
perdu au redémarrage, budget différent avant et après).

## R2 — Calcul du budget

**Décision** : `KnowledgeBudget` (domaine), avec `STANDARD` = les plafonds d'aujourd'hui, et
`forPromptTokens(OptionalInt)` :

- caractères disponibles = `promptTokens × 3` (conversion prudente pour le français) ;
- réserve = `60 000` caractères (consignes d'environ 12 000, besoin 2 000, réponses, version en
  cours d'une modification jusqu'à 20 000, marge) ;
- total = `(disponibles − réserve) / 2`, borné entre `35 000` (minimum, FR-004) et `200 000`
  (maximum, FR-005) ; la moitié laisse la place au besoin, aux réponses et à la marge d'erreur de
  la conversion ;
- facteur = `total / 35 000` ; chaque plafond de taille (rôles, ADR et ADR par document, documents
  trouvés et par document, documents désignés et par document, tickets, architecture,
  sous-tâches) = plafond d'aujourd'hui × facteur, arrondi à l'entier inférieur ; les nombres
  d'éléments ne changent pas (FR-006) ; la fiche générale reste à 20 000 (FR-007).

Exemples : 128 000 tokens → total 162 000, facteur 4,63, document désigné jusqu'à 37 000 (la
matrice de 30 000 passe, SC-001) ; 64 000 tokens → total 66 000, document désigné jusqu'à
15 000 ; 32 000 tokens ou inconnu → `STANDARD`.

**Alternatives** : budget proportionnel sans plafond (rejeté : un modèle d'un million de tokens
noierait le besoin et allongerait les réponses) ; conversion à 4 caractères par token (rejeté :
trop optimiste pour le français accentué).

## R3 — Qui fournit le budget

**Décision** : port `domain/port/ModelCapacities` : `OptionalInt promptTokens()` du modèle choisi,
et `List<ModelCapacity> all()` des modèles autorisés. Adaptateur
`infrastructure/github/SettingsModelCapacities` (lit `CopilotSettings` : modèle choisi et
capacités rangées). Modèle nul (défaut du compte) ou absent des capacités → inconnu → `STANDARD`.
`SelectKnowledge` reçoit le port et calcule le budget au début de chaque sélection ; ses
constantes d'aujourd'hui deviennent les valeurs de `KnowledgeBudget.STANDARD` (les tests qui les
citent restent valables). `MentionQueries` utilise le même budget (FR-010).

**Évaluation** : ni les prompts ni `infrastructure/llm` ne changent ; l'adaptateur et la lecture des
capacités vivent dans `infrastructure/github`. L'empreinte de
l'évaluation (`ci/check-eval.sh`) est donc inchangée, et l'évaluation tourne avec le modèle par
défaut du compte, donc `STANDARD` : la connaissance transmise y est identique (SC-002, SC-005).
À vérifier par `bash ci/check-eval.sh` dans les gates.

## R4 — Empreinte des entrées

**Constat** : l'empreinte d'une génération (`ProjectKnowledge.fingerprint`) suit les éléments
transmis et leur empreinte de contenu ; celle d'un ADR entier suit ses morceaux, pas la coupe :
un ADR coupé plus loin avec un grand modèle garderait la même empreinte.

**Décision** : `ProjectKnowledge` porte le total du budget ; `fingerprint()` ajoute une ligne
`budget=<total>` seulement quand il diffère de `STANDARD`. Les empreintes existantes ne changent
pas (aucune régénération forcée), et un changement de modèle qui change le budget change
l'empreinte (FR-009).

## R5 — Suggestion d'un modèle (US3)

**Décision** : `MentionQueries.resolve`, pour une mention `PARTIAL` ou `NO_ROOM`, rejoue le plan de
place (007, R8) avec le budget de chaque modèle autorisé de capacité connue, et retient ceux qui
transmettent ce document en entier ; le moins coûteux (coefficient inconnu compté comme le plus
cher), puis la plus petite limite. `MentionState` gagne `betterModel` (`id`, `name`,
`multiplier`), nul sinon. Aucun changement de modèle automatique (FR-015).

## R6 — Affichage (US2)

**Décision** : `SettingsResource.CopilotView` gagne `models` (`id`, `name`, `promptTokens`,
`contextTokens`, `multiplier`, `knowledgeChars`) et `knowledgeChars` du modèle choisi ;
`availableModels` reste pour la compatibilité. La carte Copilot de la configuration montre, dans
chaque option du choix du modèle, « environ N tokens (≈ M caractères) · ×coefficient » ou
« capacité inconnue », et sous le champ « Connaissance transmise : jusqu'à N caractères ».
