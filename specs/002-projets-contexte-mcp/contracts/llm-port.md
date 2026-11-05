# Contrat : port LLM, ajouts de la feature 002

Complète `specs/001-local-spec-assistant/contracts/llm-port.md`, dont toutes les règles
s'appliquent : instructions en message système, données entre balises en message utilisateur,
sortie JSON validée, jamais de journalisation du contenu, session Copilot sans outil, délai
d'appel par opération (180 s pour les opérations interactives).

> Les opérations `summarizeProjectSources` et `buildProjectContext` (fiches générées) sont
> retirées le 2026-11-04 (research.md R28).

## Connaissance du projet dans les opérations existantes (research.md R24)

| Opération | Entrée ajoutée |
|---|---|
| `proposeQuestions`, `reassessAfterChange`, `generateSpecification`, `editSpecification`, `mergeConflict`, `structureImportedText` | `knowledge` : la sélection de connaissance de la session (fiche générale, vocabulaire et vue d'architecture, ADR, tickets proches retenus, faits d'architecture calculés), 35 000 caractères au plus en tout |

- `<architecture>` n'est transmise qu'aux opérations qui produisent une spécification (research.md
  R24, T110) : `identifyImpact` n'est pas appelée pour les questions.
- Balise `<connaissance-projet>` après `<langue>`, contenu échappé, omise si vide :
  - `<fiche-generale>` : la fiche, qui l'emporte sur toutes les autres sources ;
  - `<vocabulaire ref="…">` et `<vue-architecture ref="…">` ;
  - `<adr ref="…" statut="ACCEPTED|PROPOSED">` (un ADR remplacé n'est jamais transmis) ;
  - `<ticket ref="FACT-12" statut="…" ferme="oui|non">`, avec `lien="bloque PAY-10"` quand il est
    lié explicitement à la story de la session ; jamais un ticket de la famille de la story ;
  - `<sous-taches story="PAY-10">` : `<sous-tache ref statut ferme>titre</sous-tache>`, 15 au
    plus, 1 500 caractères en tout (research.md R29). La règle du prompt : les sous-tâches font
    partie de la story, ne sont jamais des travaux concurrents ; une modification qui touche ce
    qu'elles semblent couvrir peut être mentionnée comme impact ;
  - `<architecture>` : modules impactés (`<module nom portee="PROJECT|COMMON">`), échanges et leur
    existence (`<echange de vers existe="oui|non" via="…">`), constats de déploiement.
- La règle de sécurité de chaque prompt liste ces balises parmi les données.
- **Questions** : une information présente dans la connaissance n'est pas demandée. Un écart entre
  le besoin et un ADR, ou une contradiction avec la fiche, est renvoyé dans `contradictions`
  (FR-037).
- **Spécification** :
  - une source peut être `PROJECT_CONTEXT` (`excerpt` exact de la fiche), `JIRA_ISSUE` (`ref` :
    clé), `DOC_PAGE` (`ref` : URL) ou `ARCH_ELEMENT` (`ref` : nom canonique d'un `<module>`). Une
    référence hors de la sélection est ramenée par le code ; restée inconnue, elle provoque une
    `correction`, comme un extrait de la fiche introuvable ;
  - `adrDeviations: [{adrRef, decision, deviation}]` ; un écart à un ADR non transmis est retiré ;
  - les rubriques « Architecture » et « Efforts supplémentaires (déploiement) » ne sont **pas**
    rédigées par le LLM : le code les ajoute à partir des faits calculés.

## Nouvelles opérations

### `judgeRelatedIssues` (research.md R23)

- **Entrée** : `language`, `need`, `candidates` : 20 au plus, `[{key, title, status, excerpt}]`
  (`excerpt` : 600 caractères) ; balises `<besoin>` et `<candidats><ticket ref statut>…</ticket>`.
- **Sortie** : `{ related: [{ key, reason }] }`, 5 au plus, les plus proches d'abord ; liste vide
  si aucun ne traite du même problème.
- **Règles** : une clé hors des candidats est ignorée ; `reason` tient en une phrase et ne recopie
  pas le ticket.

### `compareWithIssue` (research.md R23)

- **Entrée** : `language`, `need`, `issue` : `{key, title, description, status}` ; balises
  `<besoin>` et `<ticket ref>`.
- **Sortie** : `{ common: [texte], different: [texte], suggestion: SAME|DIFFERENT }`, 8 points au
  plus par liste.
- **Règles** : la suggestion n'engage pas l'utilisateur, qui tranche.

### `identifyImpact` (research.md R22)

- **Entrée** : `language`, `need`, `answers`, `candidates` : 15 éléments d'architecture au plus
  `[{name, type, description, technology, scope}]` ; balises `<besoin>`, `<reponses>` et
  `<elements><element nom type portee>…</element></elements>`.
- **Sortie** : `{ modules: [name], exchanges: [{ from, to, reason }] }`, 8 modules et 8 échanges
  au plus.
- **Règles** : `name`, `from` et `to` sont des noms de candidats, recopiés exactement ; un nom
  inconnu est ramené ou retiré par le code (`Citations`). Le LLM ne dit jamais si un échange
  existe : le code le vérifie sur le modèle.
