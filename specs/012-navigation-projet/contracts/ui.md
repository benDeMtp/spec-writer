# Contrat : navigation par projet et réglages de l'application (UI)

Adresses, menu, textes et comportements que les tests E2E vérifient.

## Adresses

| Adresse | Page | Bloc | Entrée active | Titre de l'onglet |
|---|---|---|---|---|
| `/` | Conversation (accueil) | Projet | Conversation | `Conversation · <projet> · spec-writer` |
| `/conversations/:cid` | Conversation | Projet | Conversation | idem |
| `/sessions` | Spécifications | Projet | Spécifications | `Spécifications · <projet> · spec-writer` |
| `/nouveau` | Nouvelle spécification | Projet | Spécifications | `Nouvelle spécification · <projet> · spec-writer` |
| `/sessions/:id` | Questionnaire | Projet | Spécifications | `Questionnaire · <projet> · spec-writer` |
| `/sessions/:id/versions/:n` | Spécification | Projet | Spécifications | `Spécification · <projet> · spec-writer` |
| `/jira` | Tickets Jira | Projet | Tickets Jira | `Tickets Jira · <projet> · spec-writer` |
| `/jira/:key` | Ticket Jira | Projet | Tickets Jira | `Ticket Jira · <projet> · spec-writer` |
| `/projet/connaissance` | Connaissance du projet | Projet | Connaissance du projet | `Connaissance du projet · <projet> · spec-writer` |
| `/projet/connaissance/adr` | ADR du projet | Projet | Connaissance du projet | `ADR · <projet> · spec-writer` |
| `/projet/reglages` | Réglages du projet | Projet | Réglages du projet | `Réglages du projet · <projet> · spec-writer` |
| `/projets` | Projets | Application | Projets | `Projets · spec-writer` |
| `/configuration` | Configuration | Application | Configuration | `Configuration · spec-writer` |
| `/journal` | Journal | Application | Journal | `Journal · spec-writer` |
| `/projets/:id/contexte` | redirection | | | rend `:id` courant, puis mène à `/projet/connaissance` |
| `/projets/:id/contexte/adr` | redirection | | | rend `:id` courant, puis mène à `/projet/connaissance/adr` |

## Menu

```text
<nav aria-label="Navigation principale">
  groupe « Projet »
    [sélecteur, libellé « Projet », valeur : nom du projet courant]
    Conversation
    Spécifications
    Tickets Jira  FACT          ← nom accessible « Tickets Jira, projet FACT » ; rien sans clé effective
    Connaissance du projet
    Réglages du projet
  groupe « Application »
    Projets
    Configuration
      ● Copilot : connecté      ← texte visible, pastille décorative
      ● Jira : non configuré
    Journal
```

- **Entrée active** : une seule entrée porte `aria-current="page"` à la fois. Elle est choisie
  selon le tableau des adresses.
- **Moins de 48 rem** :
  - le bouton « Menu » (`aria-expanded`, `aria-controls`) ouvre le panneau des deux groupes ;
  - le panneau se ferme au choix d'une page, sur Échap (focus rendu au bouton) ou par le bouton ;
  - au-delà de 48 rem, le bouton est absent.
- **Sélecteur**, quand une page refuse de quitter (proposition en attente, réglages non
  enregistrés) :
  - la confirmation de cette page s'affiche ;
  - si l'utilisateur annule, la valeur du sélecteur revient au projet courant.

## Titres visibles

- Les pages du bloc Projet qui sont des listes ou des réglages ont pour `h1`
  `<page> · <projet>` : Conversation (accueil), Spécifications, Tickets Jira, Connaissance du
  projet, Réglages du projet, ADR.
- Une spécification ou un questionnaire garde son nom en `h1`.

## Bascule du projet courant

- Une spécification, un questionnaire ou une conversation d'un autre projet, ouverts par leur
  adresse, rendent ce projet courant.
- Le shell affiche alors le texte « Projet courant : <nom> », annoncé aux lecteurs d'écran.
- « Déplacer vers… » rend aussi courant le projet de destination.
- Le texte disparaît après 6 s ou quand on quitte la page.
- Un projet déjà courant ne produit aucune annonce.

## Réglages du projet (`/projet/reglages`)

Les sections apparaissent dans cet ordre, chacune avec un `h2` :
1. **Identité** : champs « Nom », « Description » et « Clé Jira » (`id="jira"`), bouton
   « Enregistrer ».
   - L'avertissement de clé inconnue s'affiche en `role="alert"`.
   - Sans clé, l'aide indique « Clé par défaut de l'application : PROJ » si elle existe.
2. **Fiche générale**.
3. **Sources de documentation**.
4. **Modèle d'architecture**.

Un lien « Voir la connaissance du projet » mène à `/projet/connaissance`.

Si l'utilisateur quitte la page avec une saisie non enregistrée, ou change de projet, le dialogue
« Quitter sans enregistrer ? » propose « Annuler » et « Quitter sans enregistrer ».

## Connaissance du projet (`/projet/connaissance`)

La page reprend le panneau de connaissance actuel : compteurs, indexation, réindexation par source
et recherche d'essai. Elle ajoute les liens « ADR du projet » et « Réglages du projet ».

## Tickets Jira (`/jira`)

| État | Contenu |
|---|---|
| Clé du projet | « Projet Jira FACT, du plus récemment modifié au plus ancien. » |
| Clé par défaut | « Projet Jira PROJ (clé par défaut de l'application), … » |
| Aucune clé, Jira configuré | « Aucun projet Jira n'est associé à <projet>. » et un lien « Renseigner la clé Jira du projet » vers `/projet/reglages#jira` |
| Jira non configuré | « Jira n'est pas configuré. » et un lien « Ouvrir la configuration » vers `/configuration` |

## Ticket Jira (`/jira/:key`)

Le texte « La spécification sera ajoutée au projet <projet>. » s'affiche avant le bouton
« Discuter avec l'IA ».

## Configuration (`/configuration`)

- La section `h2` « Connexions » contient les cartes GitHub Copilot et Jira.
  - Le champ « Clé Jira par défaut (facultatif) » a pour aide « Utilisée pour les projets sans clé
    Jira ».
  - Après un test, le résultat indique « Projet Jira vérifié : FACT », ou le message
    `OK_NO_PROJECT`.
- La section `h2` « Avancé » contient deux cartes :
  - Modèle de recherche, avec la portée « S'applique à tous les projets. Changer de modèle demande
    de réindexer chaque projet. » ;
  - Accès des agents, avec la portée « Les agents de code accèdent à tous les projets. ».

## Bandeau d'erreur

L'action `OPEN_PROJECT_SETTINGS` affiche le bouton « Ouvrir les réglages du projet », qui mène à
`/projet/reglages#jira`.

## Session perdue

Si l'échange du jeton échoue ou qu'une réponse `SESSION_REQUIRED` arrive, l'application n'affiche
plus que cet écran :
- un `h1` « Relancez spec-writer depuis son raccourci » ;
- une explication : « Cette fenêtre n'est plus reliée à spec-writer, par exemple après un
  redémarrage. Vos données sont conservées. » ;
- ni menu ni bandeau.
