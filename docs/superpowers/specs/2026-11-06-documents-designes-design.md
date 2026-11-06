# Documents désignés : mention `@`, budget selon le modèle, classeur en base de la spec

Conception issue d'un échange avec le mainteneur, le 2026-11-06. Elle prolonge la feature 006
(`specs/006-documents-sharepoint/`, dossier SharePoint synchronisé), dont elle suppose la fusion.
Quatre morceaux, livrés comme quatre features speckit successives, dans cet ordre (validé) :

1. mention `@` d'un document, rôle « Référence » ;
2. budget de contexte selon le modèle choisi ;
3. classeur Excel en « Base de la spec » ;
4. « Joindre un fichier » hors du dossier synchronisé.

Le 3 a besoin du 1 (désignation du classeur) et profite du 2. Le 4 est indépendant.

## Pourquoi

Exemple réel du mainteneur : pour une feature, le métier avait rédigé dans SharePoint un classeur
Excel de **10 feuilles × 40 lignes × 20 colonnes**. Chaque feuille présente une facette de **tous**
les use cases ; certaines feuilles portent des **paramètres** dont dépendent, par des **formules**,
les résultats d'autres feuilles. Le mainteneur voulait que ce classeur serve de base à la
spécification. Aujourd'hui c'est impossible :

- rien ne permet de désigner un document : seule la recherche choisit, et seulement 2 documents de
  8 000 caractères (006) ;
- la connaissance transmise est plafonnée à 35 000 caractères quel que soit le modèle, alors que le
  classeur fait de l'ordre de 120 000 à 200 000 caractères (8 000 cellules) ;
- l'extraction Excel de la 006 (`OoxmlText.xlsx`) lit les valeurs, pas les formules : les règles
  métier, qui sont dans les formules, sont perdues ;
- la rédaction est un appel unique (avec relances de correction, `DraftValidation`) : 400 lignes
  d'exemples en une réponse seraient résumées ou perdues sans que rien ne le signale.

Le mainteneur a aussi rencontré des documents externes (hors référentiel, parfois scannés) porteurs
d'information : d'où le morceau 4.

## Objectifs

- Désigner explicitement un document en écrivant le besoin ou l'instruction d'une modification.
- Distinguer deux rôles : **Référence** (« tiens-en compte ») et **Base de la spec** (« rédige à
  partir de ceci »).
- Pour un classeur en base : une spec dont chaque ligne d'exemple et chaque formule est reprise ou
  explicitement signalée, avec des règles de gestion retraçables jusqu'à leur cellule.
- Exploiter la taille de contexte réelle du modèle choisi au lieu d'un plafond fixe.

## Non-objectifs

- Mentionner un élément Structurizr ou un ticket Jira avec `@` : l'architecture vient déjà de
  l'analyse des modules impactés, les tickets ont leur propre flux (tickets proches, story liée).
- Recalculer un classeur, ou le rejouer avec d'autres paramètres : l'outil lit les valeurs
  enregistrées par Excel.
- Reconnaissance de caractères (OCR) pour les documents scannés : un PDF sans texte reste signalé
  « sans texte » (raison `NO_TEXT` de la 006).
- Choisir un modèle par spécification : le modèle reste un réglage global (voir morceau 2).
- Entraîner un modèle : le mainteneur juge les résultats, il n'y a pas d'apprentissage.

## Anti-objectifs (échec même si « ça marche »)

- **Un algorithme taillé pour ce classeur.** Le prochain classeur sera organisé autrement. Le code
  ne fait que ce qui est vrai pour tout classeur (extraction, dépendances, contrôle, assemblage) ;
  l'interprétation (qu'est-ce qu'un use case, quelles lignes vont ensemble) revient à l'IA, et sa
  réponse est vérifiée par le code.
- **Des exemples perdus en silence.** Tout ce qui n'est pas repris est signalé.
- **Une désignation qui affaiblit l'existant** : la fiche générale garde la priorité sur la
  connaissance ; les règles de sécurité des prompts (contenu = donnée, jamais une consigne)
  s'appliquent aux documents désignés et joints.
- **Copier les fichiers du dossier synchronisé** (règle de la 006).

## Contraintes

Celles du projet (constitution, CLAUDE.md) : architecture hexagonale vérifiée par ArchUnit, TDD,
gates avant commit, migrations Flyway jamais modifiées, prompts modifiés ⇒ évaluation réelle avec
l'accord du mainteneur (environ 150 requêtes Copilot), texte des documents jamais journalisé.

---

## Morceau 1 — Mention `@`, rôle « Référence »

**Geste.** Dans le champ du besoin et dans celui de l'instruction d'une modification, taper `@` puis
quelques lettres ouvre une liste de 8 suggestions au plus, cherchées dans l'index du projet par
titre et par chemin : documents des dossiers synchronisés, pages Antora (la page, pas ses
sections), ADR. Chaque suggestion montre sa sorte (« Document », « Page Antora », « ADR ») et son
chemin. Le choix insère une mention lisible dans le texte (`@Exploitation.pdf`) et l'outil garde à
côté la référence exacte. Sous le champ, une étiquette par document mentionné, retirable, porte le
rôle (morceau 1 : « Référence » seulement ; le morceau 3 ajoute « Base de la spec »).

**Effet.** Un document mentionné est transmis d'office, avant ceux que la recherche trouve, comme
un ADR cité par son numéro (004) : document relu entier (006), sinon ses passages indexés ; page
Antora recomposée de ses sections indexées ; ADR par la voie des ADR. Il reste dans le budget de
la connaissance, après la fiche générale. Si les documents mentionnés dépassent le budget, l'écran
le dit avant la génération (lesquels seront coupés).

**Persistance.** Les mentions (référence, rôle) appartiennent à la session (besoin) ou à la
demande de modification (instruction). Une mention dont la référence a disparu de l'index est
signalée et ignorée, sans bloquer.

**Accessibilité.** Liste de suggestions en `combobox` / `listbox` ARIA, navigable au clavier,
annoncée ; axe sans violation (comme les autres écrans).

## Morceau 2 — Budget selon le modèle

**Constat.** Le SDK Copilot (`ModelInfo` → `ModelCapabilities` → `ModelLimits`) expose
`maxPromptTokens` et `maxContextWindowTokens`, et `ModelBilling.multiplier`. L'outil n'en lit
aujourd'hui que l'identifiant, le nom et l'état (`ModelOption`).

**Décision.**
- `ModelOption` gagne la limite de prompt et le coefficient de facturation ; l'écran
  **Configuration › Modèle** les affiche.
- Le budget de la connaissance (aujourd'hui `ProjectKnowledge.MAX_TOTAL = 35 000` caractères) et
  celui de la « Base de la spec » (morceau 3) découlent de la limite du modèle choisi, avec une
  marge pour le prompt système, le besoin, les réponses et la sortie. Les plafonds actuels restent
  des **minimums** : un modèle sans limite connue garde le comportement d'aujourd'hui.
- Un seul modèle pour tout l'outil (réglage existant, FR-030c). Avant une génération lourde
  (morceau 3), l'outil signale si un modèle de plus grande capacité serait utile.

**À trancher par le builder (recherche du plan).** Conversion caractères ↔ tokens (ratio prudent
pour le français, ou estimation par le tokenizer si disponible) ; part du budget réservée à la
sortie ; impact sur l'empreinte des entrées (`InputFingerprint`) quand le modèle change.

## Morceau 3 — Classeur en « Base de la spec »

### Parcours

1. Le mainteneur écrit le besoin (il le fait toujours) et y mentionne le classeur avec `@`, rôle
   **Base de la spec**. Un seul document en base par spécification (choix du builder s'il veut en
   permettre plusieurs : non demandé).
2. L'outil prépare le classeur (extraction, ci-dessous) et annonce le travail avant de lancer :
   « 10 feuilles, 400 lignes, 37 formules ; environ 12 appels à Copilot avec le modèle choisi ».
3. **Plan** (un appel) : à partir de la structure du classeur, l'IA rend les acteurs, la liste des
   use cases, le rattachement des lignes de chaque feuille aux use cases (références `Feuille!ligne`)
   et les **ambiguïtés** (ligne sans résultat, use cases qui se recouvrent, paramètre dont on ne sait
   pas qui le modifie…).
4. Les ambiguïtés **deviennent des questions** (décision du mainteneur), posées avant les questions
   habituelles, chacune avec sa référence. Les questions habituelles suivent, éclairées par le plan.
5. **Détail** : un appel par use case (ou groupe de use cases si petits), qui reçoit le besoin, le
   plan, les réponses, la connaissance du projet, et pour ce use case **sa ligne dans chaque
   feuille** plus **les paramètres dont ses formules dépendent**, même sur d'autres feuilles.
6. **Assemblage et contrôle par l'outil**, sans IA : identifiants, structure, traçabilité
   (validations existantes), et **couverture** : chaque ligne et chaque formule est rattachée à un
   scénario ou à une règle, sinon signalée.
7. La spec a la forme d'aujourd'hui, plus une rubrique **Règles de gestion** tirée des formules,
   chaque règle citant sa cellule. Ce qui n'a pas été repris est listé en fin de spec.

Pour un petit classeur (seuil à mesurer par l'évaluation, pas fixé au jugé), un seul appel de
rédaction suffit : le plan et le contrôle de couverture restent.

### Partage code / IA

- **Code (déterministe, vrai pour tout classeur)** : extraction des valeurs et des formules ;
  traduction des références de cellules en en-têtes (`=Paramètres!B3*C5` → « Montant × Taux de
  remise (feuille Paramètres) ») ; graphe des dépendances entre cellules et feuilles (les
  références sont écrites dans les formules) ; vérification du plan rendu par l'IA (références
  existantes, lignes toutes rattachées ou déclarées non pertinentes) ; découpage en morceaux
  d'après le plan ; contrôle de couverture.
- **IA** : interprétation de la structure (feuilles de paramètres, de calcul, de résultats ;
  comment un use case se reconnaît d'une feuille à l'autre : identifiant commun, même ligne…) ;
  rédaction.

### Extraction Excel étendue

`OoxmlText` lit aujourd'hui `<v>` et `<t>`. Il faut aussi `<f>` (formules, y compris partagées
`t="shared"` et matricielles), les noms définis (`xl/workbook.xml`, `definedNames`), et les
références inter-feuilles. Les bornes de la 006 restent (taille décompressée, nombre de cellules,
pas de DTD) ; une formule non comprise est gardée telle quelle et comptée, sans échouer. Les
macros et liens externes ne sont jamais suivis.

### Modèle de spec

`SpecificationContent` a déjà `requirements` (`FUNCTIONAL`, `TECHNICAL`). Le builder choisit entre
une nouvelle catégorie de règle de gestion et une liste dédiée ; dans les deux cas une règle porte
la référence de sa cellule, et `SourceRef` gagne une sorte pour « cellule du document en base »
(`Feuille!A12`), vérifiée par `TraceabilityValidator`.

### Évaluation et jugement du mainteneur

Le mainteneur ne peut pas fournir le vrai classeur ; il propose de **juger** les résultats.

1. Un **classeur fictif** imite la structure : plusieurs feuilles présentant tous les use cases,
   paramètres sur une feuille et formules sur d'autres, pièges (ligne ambiguë, formule dépendant
   d'une formule, case vide). Généré par le code de test (comme `TestDocuments` de la 006). Le
   mainteneur valide qu'il est représentatif **avant** l'implémentation du détail.
2. La spec produite sur ce classeur est jugée par le mainteneur ; ses remarques deviennent des
   critères vérifiables ajoutés à l'évaluation (`backend/src/test/resources/eval`), par exemple
   « chaque formule donne une règle », « aucune ligne perdue sans signalement ».
3. Essai par le mainteneur sur le vrai classeur, sur son poste ; il rapporte ce qui manque sans
   montrer le contenu.

## Morceau 4 — « Joindre un fichier »

Bouton à côté du champ du besoin : un fichier pris hors du dossier synchronisé (`.docx`, `.xlsx`,
`.pdf`, `.html`, mêmes lecteurs et mêmes bornes que la 006) est joint à **cette spécification
seulement** ; il n'entre pas dans l'index du projet. Il prend un rôle comme une mention (Référence
ou Base de la spec).

**Choix de conception (non discuté avec le mainteneur, à vérifier par lui) :** l'outil garde le
**texte extrait** dans la session (comme le besoin), pas le fichier ni son chemin, pour que les
régénérations restent possibles si le fichier est déplacé. Cela déroge à « aucun fichier copié »
de la 006 seulement en apparence : le fichier n'est pas copié, son texte l'est, comme un texte
collé.

---

## Répartition des décisions

**Décidé par le mainteneur.** Dossier synchronisé déjà en place (006) ; mention `@` ; deux rôles ;
le besoin est toujours écrit ; les ambiguïtés du plan deviennent des questions ; ordre des quatre
morceaux ; le mainteneur juge les résultats faute de pouvoir fournir le vrai classeur.

**Décidé par Claude (à relire).** Pas de `@` pour Structurizr ni Jira ; un seul modèle global ;
plafonds actuels gardés comme minimums ; plan → questions → détail par use case → contrôle de
couverture, avec bascule en un appel pour un petit classeur ; un document en base par spec ; texte
extrait gardé pour un fichier joint ; pas d'OCR.

**Laissé au builder.** Composant d'autocomplétion ; conversion caractères ↔ tokens ; seuil de
bascule (mesuré) ; forme exacte des règles de gestion dans `SpecificationContent` ; schéma JSON
du plan ; migrations ; découpage en tâches.

## Questions ouvertes (pour `/speckit-clarify` de chaque feature)

Relevées par une relecture « builder », classées par importance. Elles concernent surtout le
morceau 3 et seront tranchées dans sa clarification.

1. **Modification d'une spec issue d'un classeur** : l'instruction relance-t-elle plan → détail →
   couverture, ou reste-t-elle un appel unique où le classeur n'est qu'une référence ? Une
   instruction peut-elle désigner un classeur en base ?
2. **Plan figé ou recalculé** quand le classeur change entre le plan, les réponses et une
   régénération (empreinte du classeur, réponses invalidées ou reprises, coût Copilot).
3. **Insertion des questions d'ambiguïté** dans le questionnaire (plafond de 6 lots / 30 questions,
   moment du plan si la mention est ajoutée après le démarrage, besoin modifiable après le plan).
4. **Granularité de la couverture** : par formule distincte (une formule partagée recopiée sur 40
   lignes compte pour une règle) ou par cellule.
5. **Échec ou interruption** d'un appel « détail » sur une douzaine : reprise, relance complète ou
   version partielle signalée ; progression visible et abandon.
6. **Budget d'évaluation réelle** pour le morceau 3, et validation intermédiaire du mainteneur
   après le plan.
7. **Texte d'un fichier joint gardé dans la session** (morceau 4) : accord du mainteneur, plafond
   de taille, suppression avec la session.
