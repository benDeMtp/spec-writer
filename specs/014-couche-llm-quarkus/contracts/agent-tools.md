# Contrat : outils de l'agent conversationnel

AI Service `AgentAi.converse`, opération `converse-agent`. Les outils sont des méthodes
`@Tool` du bean `AgentTools` ; leur contexte de tour (`TurnTools`, voir
[data-model.md](../data-model.md)) arrive par `InvocationParameters` ou, en flux, par le
`ScopedValue` `AgentTools.CURRENT` (research R5, constats de l'implémentation).

## `search_knowledge`

| Champ | Valeur |
|---|---|
| Description (vue par le modèle) | Recherche dans la connaissance du projet : tickets Jira, documentation, ADR, architecture, spécifications, pièces jointes, discussion. Renvoie des sources numérotées à citer par leur numéro. |
| Paramètres | `query` (texte, 1 à 200 caractères, obligatoire) ; `kinds` (liste facultative parmi `TICKET`, `DOC_SECTION`, `ADR`, `ARCH_ELEMENT`, `SPECIFICATION`, `ATTACHMENT`, `DISCUSSION`) |
| Exécution | `TurnTools.search(query, kinds)` : recherche de la 010 (R3), sources ajoutées au tour avec la numérotation continue, étape `SearchStep` enregistrée et publiée. |
| Résultat (donnée) | `<sources>` numérotées, même format qu'aujourd'hui (`<source n="3" ref="…" titre="…">…</source>`), ou `<sources/>` si rien n'est trouvé. |

## `analyze_perimeter`

| Champ | Valeur |
|---|---|
| Description | Analyse exhaustive des sources du code du projet pour une évolution : recense les modules qui contiennent les termes, propage par les dépendances, qualifie chaque module. À utiliser pour une question de périmètre ou de modules touchés. |
| Paramètres | `terms` (liste de 1 à 12 termes, 2 à 80 caractères chacun : identifiants, classes, clés de configuration, topics, endpoints) |
| Exécution | `TurnTools.perimeter(terms)` : `AnalyzePerimeter.analyze` puis `qualify` (013), événements `perimeter` avant et après la qualification, modules ajoutés comme sources `MODULE`. Une analyse au plus par tour. |
| Résultat (donnée) | Sources `MODULE` numérotées, avec qualification et justification ; ou `<perimetre-indisponible>oui</perimetre-indisponible>` si le projet n'a pas de racine de sources. |

## Règles communes

- **Plafond** : `SearchCap` (`ToolInputGuardrail`) compte les appels du tour. Au 6e appel, aucune
  exécution ; résultat : `<recherche-arretee>oui</recherche-arretee>` et la consigne demande de
  répondre avec les sources trouvées en le disant. Butée dure : `maxToolCallingRoundTrips = 7`.
- **Analyse de périmètre** : un second appel dans le même tour renvoie un refus sans exécution.
- **Arguments invalides** : refus par le garde-fou d'entrée, résultat d'erreur sans détail
  interne ; l'appel compte dans le plafond.
- **Outil inconnu** : `hallucinatedToolNameStrategy` renvoie
  `<erreur>outil inconnu</erreur>` sans exécution.
- **Arrêt** : si `TurnTools.cancelled()`, l'outil ne s'exécute pas et renvoie un résultat vide ;
  le flux est arrêté par `StreamingHandle.cancel()`.
- **Erreur d'exécution** (recherche indisponible, racine absente) : résultat
  `<erreur>…message utilisateur…</erreur>`, jamais de pile ni de chemin.
- **Journal** : nom de l'outil et durée, jamais la requête ni les termes.
