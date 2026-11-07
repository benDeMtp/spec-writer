# Quickstart : mention d'un document avec @

## Prérequis

Gates habituelles (CLAUDE.md). Jeu E2E : le dossier `e2e/fixtures/documents/Aurora` de la 006,
déclaré en dossier de documents dans US8, et la documentation Antora du jeu fictif.

## Scénarios automatisés

1. `MentionsTest` (domaine) : grammaire (début de texte, après blanc ou ponctuation, adresse
   électronique ignorée, `@ 10 h` ignoré), plus long nom (noms à espaces), casse et extension,
   ambiguïté, introuvable, ordre besoin → réponses → instruction, doublons.
2. `SelectKnowledgeTest` : document mentionné transmis avant la recherche et marqué ; page Antora
   recomposée ; ADR remplacé → successeur ; 4 au plus, 12 000 / 8 000 ; la fiche l'emporte ;
   mention ambiguë non transmise ; déterminisme.
3. `MentionQueriesTest` : classement des suggestions (R5), 8 au plus, états et part estimée.
4. `SqliteKnowledgeIndexIT.mentionables` : un élément par document, taille, titre.
5. `PromptInjectionIT` : `designe="oui"` rendu ; contenu d'un document désigné échappé.
6. Front (Vitest) : directive (ouverture après `@`, clavier, Échap, insertion au curseur,
   adresse électronique), état des mentions, bouton de levée d'ambiguïté.
7. E2E US8 (P11) : dans une réponse, taper « @Suiv », choisir « Suivi » au clavier, générer :
   la connaissance transmise contient `Suivi.xlsx` désigné ; mention inconnue signalée ; axe.

## Essai manuel (projet de l'utilisateur)

1. Rouvrir la session « planning intelligent » de la 006, dont une réponse contient
   « @Aurora_Matrice_Regles_Gestion_Activites_1 » : l'état sous la réponse indique la matrice,
   transmise en partie.
2. Régénérer la spécification : la matrice est dans la connaissance transmise ; l'hypothèse
   « contenu non disponible » disparaît.
3. Taper « @arch » dans l'instruction d'une modification : la page « Architecture technique du
   MVP » et des ADR sont proposés.
