# Feature Specification: Interview d'une spécification avec les sachants

**Feature Branch**: `feature/025-interview-specification`

**Created**: 2026-11-20

**Status**: Draft

**Input**: User description: "Feature 021 : mode « Interview » à partir d'une spécification, pour faire le point avec les sachants (demandeur, experts métier) en partageant son écran, et répondre aux hypothèses et points ouverts. Conception validée le 20/11 (docs/superpowers/specs/2026-11-20-interview-specification-design.md, approche B) : préparer une séance sur une ou plusieurs spécifications depuis la page Spécifications (sans nouvelle entrée de menu, préparation non assistée, sans ordre du jour) ; parcourir toute la spécification élément par élément, avec arrêt sur chaque hypothèse, écart entre sources et point ouvert, et remarque possible sur n'importe quel élément ; noter les questions qui émergent avec un responsable ; appliquer les changements à la fin, en une seule révision relue en diff ; compte rendu à copier-coller (mail, Teams) ; nouveaux points ouverts dans la spécification ; hypothèses, écarts et points ouverts rattachés précisément à un élément ; « à reporter » vers les sources de vérité (CWiki, OneDrive, Jira) sans jamais y écrire. Prioritaire sur le livrable Linux (020)."

## Contexte

Une spécification rédigée par l'outil contient souvent des hypothèses (une réponse supposée faute
d'information) et des points pas clairs. Le mainteneur les lève en réunion avec les sachants : le
demandeur et les experts métier. Il partage son écran, parcourt la spécification avec eux, et note
ce qui est décidé.

Aujourd'hui, rien dans l'outil ne sert cette réunion :
- les hypothèses sont listées en bloc à la fin de la spécification, sans lien avec le passage
  qu'elles concernent ;
- une question sans réponse ni valeur par défaut n'a pas de place : elle finit en hypothèse ;
- les réponses se notent ailleurs, puis se recopient dans « Discuter avec l'IA » ;
- rien ne dit qu'une décision prise en réunion contredit une page CWiki ou un document OneDrive :
  la spécification s'éloigne alors des sources de vérité, en silence.

Cette feature ajoute la séance de relecture avec les sachants, de sa préparation au compte rendu, et
garde visible tout écart qu'elle crée avec les sources de vérité.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Relire une spécification en séance et noter les points (Priority: P1)

Depuis la page d'une spécification, le mainteneur lance « Faire le point ». L'écran passe en mode
séance, lisible sur un écran partagé : la spécification se parcourt dans l'ordre, élément par
élément (résumé, use cases et leurs étapes, scénarios, exigences). Chaque hypothèse, écart entre
sources et point ouvert s'affiche là où il s'applique, et appelle une réponse. Sur n'importe quel
élément, le mainteneur peut aussi noter une remarque (« le demandeur veut une validation du
manager ») ou une question qu'on ne sait pas trancher, avec la personne qui doit y répondre. Tout
est enregistré au fil de l'eau.

**Why this priority**: c'est le cœur de la demande : faire le point avec les sachants sans quitter
la spécification ni prendre de notes ailleurs. Seule, elle donne déjà une séance utile, dont les
réponses restent dans l'outil.

**Independent Test**: sur une spécification qui a des hypothèses, lancer « Faire le point »,
répondre à une hypothèse, noter une remarque sur une étape et une question avec un responsable,
fermer l'onglet, rouvrir : la séance reprend avec tous les points notés.

**Acceptance Scenarios**:

1. **Given** une spécification avec 3 hypothèses et 2 points ouverts, **When** le mainteneur ouvre
   sa page, **Then** l'en-tête propose « Faire le point (5) ».
2. **Given** une séance en cours, **When** le mainteneur avance élément par élément au clavier,
   **Then** chaque hypothèse, écart et point ouvert apparaît sur l'élément auquel il est rattaché,
   et la progression indique où il en est (« UC-2 · 4 sur 12 », « 2 décidés · 3 restants »).
3. **Given** une hypothèse affichée, **When** le mainteneur saisit la réponse et choisit son issue
   (confirmée, à modifier, à retirer, reportée), **Then** le point est enregistré avec cette issue.
4. **Given** une étape de use case sans aucun point, **When** le mainteneur note une remarque,
   **Then** la remarque est rattachée à cette étape et compte parmi les points de la séance.
5. **Given** une question qu'on ne sait pas trancher, **When** le mainteneur la note avec « Paul D. »
   comme responsable, **Then** elle est enregistrée comme reportée, avec son responsable.
6. **Given** une séance avec des points notés, **When** l'onglet est fermé ou l'outil quitté, puis
   la spécification rouverte, **Then** « Reprendre la séance » ramène à l'élément où elle en était,
   sans perte.
7. **Given** une séance en cours, **When** une synchronisation des sources se termine ou un autre
   événement de l'application survient, **Then** rien ne s'affiche sur l'écran de séance.

---

### User Story 2 - Terminer la séance et copier le compte rendu (Priority: P1)

À la fin, le mainteneur obtient un compte rendu de la séance : la spécification, la date, les
participants, les décisions, les questions reportées avec leur responsable, et les sources de
vérité à mettre à jour. Il le copie en un clic, puis le colle dans un mail ou dans Teams, où sa mise
en forme est gardée.

**Why this priority**: le compte rendu est ce que les sachants reçoivent. Avec la story 1, il fait
une séance complète, même avant toute révision de la spécification.

**Independent Test**: terminer une séance avec deux décisions et une question reportée, copier le
compte rendu, le coller dans un mail : titres et listes sont conservés, et le texte reprend mot pour
mot ce qui a été noté.

**Acceptance Scenarios**:

1. **Given** une séance avec des points notés, **When** le mainteneur choisit « Terminer », **Then**
   un récapitulatif montre le compte rendu, la révision à proposer et les sources à mettre à jour.
2. **Given** le récapitulatif, **When** le mainteneur choisit « Copier le compte rendu », **Then** le
   presse-papiers reçoit le compte rendu mis en forme et en texte brut, et un message confirme la
   copie.
3. **Given** un compte rendu, **When** il est lu, **Then** chaque décision cite l'élément concerné
   (« H-3 », « UC-2 »), chaque question reportée nomme son responsable, et le texte des points est
   celui noté en séance, sans reformulation.
4. **Given** une séance terminée, **When** le mainteneur rouvre la spécification plus tard, **Then**
   il peut revoir et recopier le compte rendu de chaque séance passée.

---

### User Story 3 - Réviser la spécification d'après la séance (Priority: P1)

Depuis le récapitulatif, le mainteneur propose la nouvelle version : l'outil la rédige d'après les
points décidés, et le mainteneur la relit en diff avant de l'appliquer, comme pour une révision
depuis la discussion. La nouvelle version ne contient plus les hypothèses et points ouverts
tranchés, contient les questions reportées comme points ouverts, et signale les nouvelles questions
que les réponses laissent en suspens.

**Why this priority**: sans révision, la séance ne change pas la spécification ; c'est ce qui
transforme la réunion en une meilleure spécification.

**Independent Test**: terminer une séance où une hypothèse est confirmée, une étape à modifier et
une question reportée ; proposer la nouvelle version ; relire le diff ; appliquer : l'hypothèse a
disparu, l'étape est modifiée, la question est un point ouvert avec son responsable.

**Acceptance Scenarios**:

1. **Given** une séance terminée avec des points décidés, **When** le mainteneur choisit « Proposer
   la nouvelle version », **Then** la proposition est rédigée d'après ces points et présentée en
   diff, sans rien changer avant « Appliquer ».
2. **Given** la nouvelle version appliquée, **When** elle est lue, **Then** chaque hypothèse et point
   ouvert tranché en séance en a disparu, et chaque question reportée y figure comme point ouvert
   avec son responsable, quoi qu'ait rédigé l'outil.
3. **Given** une réponse qui laisse une question en suspens (« validation du manager », sans dire
   qui valide en son absence), **When** la nouvelle version est rédigée, **Then** cette question
   apparaît comme nouveau point ouvert, rattaché à l'élément concerné.
4. **Given** une phrase de la nouvelle version issue d'une décision de séance, **When** sa source
   est affichée, **Then** elle indique la séance (date et participants).
5. **Given** une proposition rejetée, **When** le mainteneur revient à la spécification, **Then** il
   peut proposer à nouveau la révision de cette séance depuis sa page.

---

### User Story 4 - Voir chaque point à l'endroit qu'il concerne (Priority: P2)

Les hypothèses, écarts entre sources et points ouverts d'une spécification sont rattachés à
l'élément précis qu'ils concernent : une étape d'un use case, un scénario, une exigence ou le
résumé. La spécification affiche une nouvelle rubrique « Points ouverts » à côté des « Hypothèses »,
et chaque point indique l'élément auquel il se rattache.

**Why this priority**: sans rattachement, la séance doit regrouper les points en fin de
spécification, hors de leur contexte. Les stories 1 à 3 fonctionnent sans (repli « Non rattachés »),
mais la relecture est bien meilleure avec.

**Independent Test**: générer une spécification à partir d'un besoin qui laisse des questions
passées : chaque hypothèse indique l'étape ou l'élément concerné, et en séance elle s'affiche sur
cet élément.

**Acceptance Scenarios**:

1. **Given** une spécification nouvellement générée ou révisée, **When** elle est affichée, **Then**
   chaque hypothèse, écart entre sources et point ouvert indique l'élément auquel il se rattache.
2. **Given** un rattachement qui ne désigne aucun élément de la version (étape inexistante),
   **When** la version est enregistrée, **Then** le rattachement est retiré et le point reste, non
   rattaché.
3. **Given** une spécification écrite avant cette feature, **When** elle est relue en séance,
   **Then** ses points apparaissent dans un arrêt « Non rattachés » à la fin de la spécification, et
   la révision de fin de séance les rattache.
4. **Given** une spécification avec des points ouverts, **When** elle est exportée (Markdown, Jira),
   **Then** la rubrique « Points ouverts » y figure, avec le responsable de chaque point.

---

### User Story 5 - Préparer une séance sur plusieurs spécifications (Priority: P2)

Sur la page « Spécifications », le mainteneur voit pour chacune ses points à traiter, filtre celles
qui en ont, en coche plusieurs et prépare une séance : ordre de passage, participants, points à
écarter pour cette séance. La séance préparée l'attend en tête de la page, jusqu'à ce qu'il la
démarre. La séance enchaîne alors les spécifications, et le compte rendu les regroupe.

**Why this priority**: une réunion avec les sachants couvre souvent plusieurs sujets ; préparer
évite de chercher en direct. La story 1 permet déjà une séance d'une spécification.

**Independent Test**: cocher deux spécifications, préparer la séance en écartant un point, quitter
l'outil, le relancer, démarrer la séance : les deux spécifications se suivent dans l'ordre choisi,
sans le point écarté, et le compte rendu a une partie par spécification.

**Acceptance Scenarios**:

1. **Given** la page « Spécifications », **When** elle s'affiche, **Then** chaque spécification
   indique ses points à traiter (« 3 hypothèses · 2 points ouverts · 1 écart entre sources », ou
   « aucun point à traiter »), et un filtre ne garde que celles qui en ont.
2. **Given** deux spécifications cochées, **When** le mainteneur choisit « Préparer une séance (2) »,
   **Then** il peut ordonner les spécifications, saisir les participants et écarter des points.
3. **Given** une séance préparée, **When** la page « Spécifications » s'affiche, **Then** la séance
   y apparaît en tête, avec son nombre de spécifications et de points, et se démarre, se modifie ou
   se supprime.
4. **Given** une séance démarrée sur deux spécifications, **When** le mainteneur avance, **Then** la
   progression indique la spécification en cours (« Spéc. 1 sur 2 »), et les points écartés ne
   s'affichent pas ; ils restent ouverts dans leur spécification.
5. **Given** cette séance terminée, **When** le compte rendu est copié, **Then** il est regroupé par
   spécification, et une révision est proposée pour chacune.

---

### User Story 6 - Garder visibles les sources de vérité à mettre à jour (Priority: P3)

Quand une décision de séance modifie ou retire ce qu'un use case tire d'une page CWiki, d'un
document OneDrive ou d'un ticket Jira, ou tranche un écart entre sources, l'outil note cette source
« à mettre à jour », avec le texte de la décision. Le mainteneur peut aussi désigner une source à
la main en séance. Ces « à reporter » restent affichés sur la page de la spécification jusqu'à ce
qu'il les close ; quand la source change après la séance, l'outil le signale.

**Why this priority**: c'est la réponse au risque de s'écarter des sources de vérité. Elle
s'appuie sur les stories 1 à 3.

**Independent Test**: dans une séance, modifier une étape d'un use case qui cite une page CWiki ;
appliquer la révision : la page apparaît « à mettre à jour » sur la spécification et dans le compte
rendu ; modifier la page dans CWiki et resynchroniser : l'« à reporter » passe à « source
modifiée, à vérifier » ; le clore.

**Acceptance Scenarios**:

1. **Given** une décision « à modifier » sur un use case qui cite une page CWiki, **When** la
   révision est appliquée, **Then** la page est listée « à mettre à jour » sur la spécification,
   avec le texte de la décision.
2. **Given** un point sur un élément qui ne cite aucune source, **When** le mainteneur désigne une
   page de la connaissance du projet comme source à mettre à jour, **Then** un « à reporter » est
   créé pour cette page.
3. **Given** un « à reporter » ouvert, **When** la source change dans la connaissance du projet
   après la séance, **Then** il est signalé « source modifiée, à vérifier », sans être clos
   automatiquement.
4. **Given** un « à reporter », **When** le mainteneur le clôt, **Then** il disparaît de la page de
   la spécification et reste dans l'historique de la séance.
5. **Given** n'importe quel « à reporter », **When** il est créé, affiché ou clos, **Then** l'outil
   n'écrit jamais dans la source (CWiki, OneDrive, SharePoint, Jira).

---

### Edge Cases

- Une nouvelle version de la spécification est créée (par la discussion) pendant une séance en
  cours : la séance le signale ; ses points restent rattachés par code d'élément, et la révision
  part de la dernière version. Un point rattaché à un élément qui n'existe plus passe dans « Non
  rattachés ».
- Deux séances voudraient relire la même spécification en même temps : une seule séance en cours
  par spécification ; la seconde est refusée avec un message qui nomme la séance en cours.
- Une spécification incluse dans une séance préparée est supprimée : elle sort de la séance, qui le
  signale ; une séance préparée sans spécification est supprimée.
- Une séance terminée sans aucun point : le compte rendu le dit (« aucun point noté ») et aucune
  révision n'est proposée.
- Copilot n'est pas disponible (consentement retiré, non configuré, hors ligne) : la séance et le
  compte rendu fonctionnent ; seule la révision est indisponible, avec la raison et la marche à
  suivre.
- Le presse-papiers est refusé par le navigateur : le compte rendu s'affiche sélectionné, prêt à
  être copié au clavier.
- Une génération ou une révision est en cours sur la spécification : « Faire le point » est
  indisponible, avec la raison.
- Plus de points qu'une révision ordinaire n'en accepte : la révision de séance les transmet tous.
- Un nom de participant ou un texte de point très long : bornés, avec un message clair à la saisie.

## Requirements *(mandatory)*

### Functional Requirements

**Séance**

- **FR-001**: L'outil MUST permettre de démarrer une séance sur une spécification depuis sa page
  (« Faire le point », avec le nombre de points à traiter), sans nouvelle entrée dans le menu.
- **FR-002**: L'écran de séance MUST être conçu pour un écran partagé : grands caractères, un
  élément mis en avant à la fois, menu masqué, aucune notification ni contenu étranger à la séance.
- **FR-003**: La séance MUST parcourir toute la spécification dans l'ordre de son rendu (résumé, use
  cases avec étapes et alternatives, scénarios, exigences, puis « Non rattachés »), au clavier, avec
  un raccourci vers le prochain point à traiter.
- **FR-004**: Chaque hypothèse, écart entre sources et point ouvert MUST s'afficher sur l'élément
  auquel il est rattaché et pouvoir recevoir une réponse et une issue : confirmé, à modifier (avec
  le texte voulu), à retirer, reporté (avec un responsable et une échéance facultative).
- **FR-005**: Le mainteneur MUST pouvoir noter, sur n'importe quel élément, une remarque (avec la
  même issue qu'un point) ou une question reportée avec son responsable.
- **FR-006**: La séance MUST être enregistrée à chaque point noté, et reprendre à l'élément en cours
  après fermeture de l'onglet ou arrêt de l'outil.
- **FR-007**: Une spécification MUST avoir au plus une séance en cours.
- **FR-008**: La séance MUST afficher sa progression (élément courant, points décidés, points
  restants, spécification courante pour une séance de plusieurs spécifications).

**Fin de séance et compte rendu**

- **FR-009**: « Terminer » MUST clore la séance et ouvrir un récapitulatif : compte rendu, révision
  à proposer par spécification, sources à mettre à jour.
- **FR-010**: Le compte rendu MUST contenir la ou les spécifications, la date, les participants, les
  décisions (avec l'élément concerné), les questions reportées (avec responsable et échéance) et les
  sources à mettre à jour ; il MUST reprendre le texte des points tel que noté, sans modèle.
- **FR-011**: Le compte rendu MUST se copier en un geste dans le presse-papiers, mis en forme (titres
  et listes gardés en le collant dans un mail ou dans Teams) et en texte brut.
- **FR-012**: Le compte rendu de chaque séance passée MUST rester consultable et copiable depuis la
  page de chaque spécification concernée.

**Révision**

- **FR-013**: Le mainteneur MUST pouvoir proposer, pour chaque spécification d'une séance terminée,
  une nouvelle version rédigée d'après les points décidés, relue en diff et appliquée ou rejetée
  comme une révision depuis la discussion (011) ; rien n'est modifié avant « Appliquer ».
- **FR-014**: La révision MUST pouvoir être proposée plus tard et relancée depuis la page de la
  spécification, tant que les points de la séance n'ont pas été appliqués.
- **FR-015**: Dans la nouvelle version, les hypothèses et points ouverts tranchés en séance MUST
  avoir disparu et les questions reportées MUST figurer comme points ouverts avec leur responsable,
  de façon garantie, indépendamment de la rédaction de l'outil.
- **FR-016**: La révision MUST signaler comme nouveaux points ouverts les questions que les réponses
  laissent en suspens.
- **FR-017**: Une phrase issue d'une décision de séance MUST citer la séance (date, participants)
  comme source.

**Spécification : points ouverts et rattachement**

- **FR-018**: Une spécification MUST pouvoir contenir des points ouverts : une question sans réponse
  ni valeur par défaut, avec un code, un rattachement et un responsable facultatif ; ils sont
  affichés dans une rubrique « Points ouverts » et exportés (Markdown, Jira).
- **FR-019**: Les hypothèses, écarts entre sources et points ouverts MUST pouvoir être rattachés à
  un élément de la spécification (étape nominale ou alternative d'un use case, scénario, exigence,
  résumé) ; la génération, la révision, l'édition par l'IA et la structuration d'un texte importé
  MUST produire ce rattachement.
- **FR-020**: Un rattachement qui ne désigne aucun élément de la version MUST être retiré à
  l'enregistrement, le point restant non rattaché.
- **FR-021**: Les spécifications existantes MUST rester lisibles ; leurs points, non rattachés, sont
  présentés en fin de parcours.

**Préparation**

- **FR-022**: La page « Spécifications » MUST indiquer, pour chaque spécification, ses points à
  traiter par sorte, et filtrer celles qui en ont.
- **FR-023**: Le mainteneur MUST pouvoir sélectionner une ou plusieurs spécifications et préparer
  une séance : ordre de passage, participants, points écartés.
- **FR-024**: Une séance préparée MUST être enregistrée, affichée en tête de la page
  « Spécifications », et pouvoir être démarrée, modifiée ou supprimée.
- **FR-025**: Le nombre de points à traiter MUST exclure les points déjà tranchés dans une séance
  dont la révision n'a pas été appliquée.

**Sources de vérité**

- **FR-026**: Un « à reporter » MUST être créé, à l'application de la révision, pour chaque point
  décidé « à modifier » ou « à retirer » sur un use case (ou l'une de ses étapes) qui cite une
  source de vérité, et pour chaque écart entre sources tranché ; il contient la source et le texte
  de la décision tel que noté.
- **FR-027**: Le mainteneur MUST pouvoir désigner en séance une source de la connaissance du projet
  à mettre à jour, sur n'importe quel point.
- **FR-028**: Les « à reporter » ouverts MUST être affichés sur la page de la spécification et dans
  le compte rendu, et pouvoir être clos par le mainteneur.
- **FR-029**: Un « à reporter » MUST être signalé « source modifiée, à vérifier » quand la source a
  changé dans la connaissance du projet depuis la décision ; il n'est jamais clos automatiquement.
- **FR-030**: L'outil MUST NOT écrire dans une source de vérité (Confluence, OneDrive, SharePoint,
  Jira) à cause d'une séance.

**Transverse**

- **FR-031**: Tout le parcours (préparation, séance, récapitulatif) MUST être utilisable au clavier
  et conforme WCAG 2.1 AA.
- **FR-032**: Les noms des participants et le texte des points restent sur le poste ; ils ne sont
  transmis au modèle que pour la révision, avec le consentement Copilot déjà requis.

### Key Entities

- **Séance**: une relecture d'une ou plusieurs spécifications avec des sachants ; état (préparée, en
  cours, terminée), dates, participants, spécifications dans l'ordre, version relue de chacune,
  points écartés.
- **Point de séance**: ce qui est noté en séance ; spécification, élément de rattachement, origine
  (hypothèse, écart entre sources, point ouvert, remarque, question), texte, issue, responsable,
  échéance, source désignée à la main.
- **Point ouvert**: nouvel élément d'une spécification ; question sans réponse ni valeur par défaut,
  code, rattachement, responsable facultatif.
- **Rattachement**: lien d'une hypothèse, d'un écart ou d'un point ouvert vers un élément de la
  spécification (code de l'élément, et étape pour un use case).
- **À reporter**: une source de vérité à mettre à jour après une décision ; spécification, point
  d'origine, source, texte de la décision, état (ouvert, source modifiée, clos).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Pendant une séance, aucune action (avancer, répondre, noter) ne fait attendre plus
  d'une seconde : aucune attente du modèle n'a lieu avant « Terminer ».
- **SC-002**: Une séance de 10 points se mène entièrement au clavier, sans quitter l'écran de
  séance.
- **SC-003**: Le compte rendu est copié en un geste et, collé dans un mail ou dans Teams, garde ses
  titres et ses listes ; il reprend 100 % des points notés, mot pour mot.
- **SC-004**: Après application de la révision, 100 % des hypothèses et points ouverts tranchés ont
  disparu, et 100 % des questions reportées figurent comme points ouverts avec leur responsable.
- **SC-005**: Fermer l'onglet ou quitter l'outil pendant une séance ne perd aucun point noté.
- **SC-006**: Toute décision qui touche un use case appuyé sur une source de vérité laisse un « à
  reporter » visible tant qu'il n'est pas clos ; aucune écriture n'a lieu dans les sources.
- **SC-007**: Sur les spécifications générées ou révisées après cette feature, au moins 90 % des
  hypothèses sont rattachées à un élément (mesuré par l'évaluation de la génération).
- **SC-008**: L'évaluation de la génération ne régresse pas sur ses critères existants.

## Assumptions

- Une seule personne pilote l'outil pendant la séance ; les sachants regardent l'écran partagé.
- Les participants sont des noms saisis librement, sans compte ni annuaire.
- La préparation est faite par le mainteneur seul, sans assistance du modèle ni ordre du jour.
- Une séance relit la dernière version de chaque spécification au moment de son démarrage.
- Le texte d'un « à reporter » est la décision telle que notée ; l'outil ne rédige pas le texte à
  écrire dans la source.
- La comparaison « source modifiée » s'appuie sur la connaissance du projet indexée et sa
  synchronisation (017) ; une source hors connaissance ne peut pas être suivie.
- Les « à reporter » sont propres à l'outil : ils ne sont pas écrits dans les versions de la
  spécification, donc ni dans le Markdown ni dans l'export Jira, mais dans le compte rendu.
- La conception et les choix faits sans le mainteneur sont dans
  `docs/superpowers/specs/2026-11-20-interview-specification-design.md`.

## Hors périmètre

- Écrire dans Confluence, OneDrive, SharePoint ou Jira à partir d'une séance.
- Envoyer le compte rendu (mail, Teams) depuis l'outil.
- Plusieurs personnes connectées à la même séance.
- Transcription audio ou prise de notes automatique de la réunion.
- Ordre du jour envoyé aux sachants avant la séance.
- Production de points ouverts par la génération initiale hors séance (à évaluer plus tard).
