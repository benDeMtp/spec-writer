# Research : réindexation par source

## R1 — Une étape en échec n'arrête pas les suivantes

**Constat** : `SynchronizeKnowledge.run` enchaîne modèle, Jira, documentation, architecture puis
vecteurs. Chaque étape traite ses erreurs prévues (`IssueTrackerException`, page illisible, DSL
invalide) ; toute autre exception remonte à `Indexations.Indexation.run`, qui termine
l'indexation en `FAILED` sans exécuter les étapes suivantes ni enregistrer les repères. C'est ce
qu'a montré l'essai de la 003 (`NoSuchMethodException` sur `searchLenient`).

**Décision** : une garde par source dans `SynchronizeKnowledge` :

- Jira, chaque source de documentation (une à une), l'architecture, puis le calcul des vecteurs.
- La garde attrape `RuntimeException` et `LinkageError`, sauf `IndexationProgress.Cancelled`, et
  laisse passer toute exception si l'annulation est demandée (FR-005).
- Elle journalise l'erreur avec sa pile (`LOG.error`, sans contenu) et ajoute une
  `IndexationError` du type de la source (`JIRA`, `SOURCE` avec l'emplacement, `ARCHITECTURE`,
  `MODEL`), raison `UNEXPECTED`, message « Erreur inattendue (NomDeLaClasse) ».
- Repères (FR-003) : Jira en échec garde le repère d'avant l'étape, l'architecture en échec
  garde son empreinte, une source de documentation en échec rend la lecture incomplète (aucun
  retrait, comme une source illisible aujourd'hui).
- L'indexation se termine `SUCCEEDED` avec des erreurs : le panneau affiche déjà « terminée, avec
  des erreurs » dès qu'une erreur n'est pas un avertissement, et la connaissance est marquée
  partielle.
- Restent en `FAILED` les erreurs hors étape : projet introuvable, lecture ou écriture des
  repères (`sources.find` / `sources.save`).

**Alternatives écartées** : une garde dans `Indexations` autour de chaque étape (elle ne connaît
pas les étapes) ; reprendre l'étape en échec (une erreur inattendue ne se corrige pas en
réessayant).

## R2 — Désigner les sources choisies

**Constat** : une source de documentation a un identifiant (`DocSource.id`) ; Jira et le modèle
d'architecture sont uniques par projet.

**Décision** : valeur `IndexationScope(boolean jira, Set<UUID> docSources, boolean architecture)`
dans `application/knowledge`, avec `IndexationScope.ALL`. À l'API, une liste de chaînes :
`"JIRA"`, `"ARCHITECTURE"` et l'identifiant de chaque source de documentation. Liste absente :
toutes les sources (comportement actuel). Liste vide, valeur inconnue ou source de documentation
d'un autre projet : 422 `VALIDATION_FAILED`.

Une liste de sources n'est acceptée qu'en mode `FULL` : forcer la relecture d'une source, c'est
la réindexer entièrement (spec, Assumptions). En `INCREMENTAL` avec une liste : 422.

## R3 — Ce que « réindexer entièrement » veut dire pour chaque source

- **Jira** : le mode `FULL` actuel (règles de la 003 : tickets fermés gardés sans relecture,
  tickets supprimés retirés).
- **Documentation** : l'étape lit déjà toutes les pages à chaque indexation ; seuls les éléments
  dont le contenu a changé sont réécrits. Rien à changer, sauf le retrait : il ne se fait que si
  **toutes** les sources de documentation ont été lues complètement. Les éléments de
  documentation ne portent pas leur source (référence relative pour un dossier), et un retrait
  limité à la source choisie effacerait les autres (spec, Assumptions).
- **Architecture** : le mode `FULL` ignore déjà l'empreinte et réanalyse le modèle.

## R4 — Repères après une réindexation de sources choisies

`KnowledgeSource.synced(mark, at)` avance le repère Jira et la date « Synchronisée le ». Après une
réindexation choisie :

- le repère Jira avance si Jira était choisi et n'a pas échoué ;
- l'empreinte d'architecture est remplacée si l'architecture était choisie et lue ;
- la date « Synchronisée le » ne change pas (spec, Assumptions) : nouvelle méthode
  `KnowledgeSource.withJiraMark(Instant)`.

## R5 — Sources proposées au panneau

Le panneau ne connaît que l'identifiant du projet. Le statut de la connaissance
(`GET /knowledge`) gagne la liste des sources réindexables :

- Jira si l'accès est configuré, consenti, et qu'une clé de projet s'applique (mêmes conditions
  que l'étape Jira), libellé « Jira AURORA » ;
- chaque source de documentation, libellé = son emplacement ;
- le modèle d'architecture si un workspace est déclaré, libellé = le workspace.

L'indexation (`IndexationView`) gagne `sources` : la liste demandée, ou `null` pour toutes. Le
bilan nomme les sources réindexées à partir des libellés du statut.

## R6 — Interface

Un troisième bouton « Réindexer des sources… » à côté de « Resynchroniser » et « Tout
réindexer ». Il ouvre, dans le panneau, un groupe de cases à cocher (`fieldset` avec `legend`
« Sources à réindexer »), rien de coché, puis « Réindexer la sélection » (désactivé sans case
cochée) et « Annuler ». Trois actions au plus : ouvrir, cocher, lancer (SC-003). Accessibilité :
cases natives Material, focus porté sur la légende à l'ouverture, retour au bouton à la
fermeture.
