# Contrat : périmètre des modules touchés (UI)

## Réglages du projet : section « Sources du code »

Elle est placée après « Sources de documentation » dans `project-settings.page`, sous forme de
composant `sw-source-roots`, avec l'ancre `#sources-code`.

- **Titre** : `h2` « Sources du code ».
- **Texte d'information** (FR-026), toujours visible : « Lors d'une analyse de périmètre,
  spec-writer envoie à GitHub Copilot les noms des modules et de courts extraits des lignes
  trouvées. Le code n'est ni copié ni indexé. Les fichiers sensibles (.env, clés, settings.xml…)
  ne sont jamais lus. »
- **Liste des racines** : une ligne par racine.
  - Libellé, chemin, et état en texte (« Introuvable », « Illisible »), sans repère de couleur
    seule.
  - Motifs d'exclusion modifiables : zone de texte, une ligne par motif, bouton « Enregistrer
    les exclusions ».
  - « Détecter les modules » (T061), nommé « Détecter les modules de la racine rv », avec
    `aria-expanded` : déplie sous la racine le même aperçu que le formulaire d'ajout, lu avec son
    chemin et ses motifs enregistrés ; un second clic le replie.
  - « Supprimer la racine », avec une confirmation nommée : « Supprimer la racine rv ? Les
    analyses passées restent lisibles. »
- **Formulaire d'ajout**, affiché tant qu'il y a moins de 10 racines.
  - Champ « Chemin du dossier » (absolu).
  - « Motifs d'exclusion (facultatif) », une ligne par motif. L'aide donne des exemples :
    `legacy/**`, `**/*.generated.java`.
  - Bouton « Détecter les modules ». Le résultat s'affiche en `role="status"` : « 14 modules détectés ·
    3 210 fichiers retenus · 120 exclus par vos motifs · 410 ignorés par .gitignore · 2 fichiers
    sensibles écartés », suivi de la liste des modules (nom, chemin, sorte). Au-delà de
    500 modules, le texte ajoute « (500 premiers affichés) ».
  - Bouton « Ajouter la racine ».
- **Erreurs** : bandeau commun, comme les autres réglages du projet (chemin absent, racine imbriquée,
  motif refusé, avec la raison). Écart au plan initial, qui prévoyait l'erreur sous le champ.

## Conversation : étape « Analyse du périmètre »

C'est le composant `sw-perimeter-step`. Il est **ouvert par défaut**, alors que les recherches
sont repliées. Écart au plan initial : chaque analyse est un bloc à part, sous le résumé replié
des recherches, et les analyses sont rangées par ordinal. Les intercaler entre les recherches
aurait déplié ce résumé.

```text
┌ Analyse du périmètre · 3 termes · 2 racines · 18 402 fichiers · 4,2 s ─────────┐
│ Termes : ContractStatus, CONTRACT_STATUS, contract.status                      │
│ 12 modules : 4 impactés, 6 à vérifier, 1 non impacté, 1 non qualifié            │
│ ⚠ Analyse incomplète : durée maximale atteinte, une partie de rv non parcourue │
│                                                                                 │
│ Impactés (4)                                                                    │
│ Module                 Raisons                          Justification           │
│ common-contract        ContractStatus ×12               Définit l'énumération…  │
│   libs/common-contract  [Occurrences ▸]                                          │
│ …                                                                               │
│ À vérifier (6)                                                                  │
│ relance  Nouveau       dépend de facturation ← quarkus-contract ← common…       │
│ notification           relié à facturation (REST, « envoie les relances »)       │
│ …                                                                               │
│ Non qualifiés (1)  — l'agent ne s'est pas prononcé                              │
│ Éléments d'architecture sans module (1)                                         │
└─────────────────────────────────────────────────────────────────────────────────┘
```

- **En-tête** :
  - « Analyse du périmètre », termes, nombre de racines, fichiers parcourus, durée ;
  - une racine introuvable est nommée : « Racine pipeline introuvable : non analysée » ;
  - les termes retirés sont listés, avec leur raison.
- **Résumé** : une ligne en texte. Tant que la qualification est en cours, il dit
  « Qualification en cours… » (`aria-busy="true"` sur la section).
- **Groupes** : un `<section>` par qualification, avec un `h3` qui donne le nombre de modules.
  Ordre : Impactés, À vérifier, Non impactés, Non qualifiés.
- **Tableau** de chaque groupe : `<table>` avec `<caption>` (masqué visuellement) et des
  colonnes `th scope="col"` :
  - Module : nom et chemin, avec le badge texte « Nouveau » si `isNew` ;
  - Raisons ;
  - Justification.
- **Raisons** :
  - `ContractStatus ×12` ;
  - `dépend de X` suivi de la chaîne, en liste ordonnée accessible ;
  - `agrège X` ;
  - `relié à Y (technologie, « description »)` ;
  - `absent du modèle d'architecture`, en texte discret.
- **Occurrences** : un `<details>` par module, avec le résumé « Occurrences (12) ». La liste
  contient `chemin:ligne` et l'extrait en `<code>`. Quand l'échantillon est plus petit que le
  total, une ligne le dit : « 20 premières sur 340 pour ContractStatus. Affinez le terme pour un
  détail complet. »
- **Qualification** : écrite en texte dans le titre du groupe. Une icône décorative
  (`aria-hidden`) peut l'accompagner. Les contrastes sont AA.
- **Fin de l'analyse** : annoncée une fois par `LiveAnnouncer` (« Analyse du périmètre
  terminée : 12 modules »), puis une seconde fois après la qualification (« Modules qualifiés »).
- **Aucun résultat** : « Aucun module ne contient ces termes. » L'agent propose d'autres termes
  dans sa réponse.
- **Projet sans racine** : la section « Analyse du périmètre » dit « Aucune source du code n'est
  déclarée pour ce projet. » et donne le lien « Déclarer les sources du code », vers
  `/projet/reglages#sources-code`.
- **Citations** : dans la réponse, `[n]` vers une source `MODULE` mène à la ligne du module dans
  l'étape (ancre `#perimeter-<message>-<ordinal>-<position>`), qui reçoit le focus.

- **Espace étroit** (moins de 40rem, comme le panneau de discussion d'une spécification) : chaque
  module devient un bloc (module, puis « Raisons », puis « Justification »). Les rôles ARIA explicites
  du tableau gardent sa lecture par colonnes. Les colonnes ont la même largeur d'un groupe à
  l'autre, et un chemin se coupe après un `/`.
- **Panneau de discussion d'une spécification** (011) : une poignée (`role="separator"`) entre le
  document et la discussion règle sa largeur, à la souris ou au clavier (flèche gauche élargit,
  flèche droite réduit, Origine et Fin aux bornes : 352 px, 70 % de l'espace). La largeur est gardée
  dans le navigateur. Sous 64rem, le panneau passe sous le document et la poignée disparaît.

## Spécification

- La rubrique « Modules touchés » est rendue dans le document : listes par qualification, avec
  les preuves en sous-liste.
- Le sommaire des rubriques (feature 011) reçoit l'entrée « Modules touchés ».
- Le panneau « Brouillon de besoin » (010) affiche, si une analyse existe : « La spécification
  reprendra le périmètre analysé (12 modules). »
