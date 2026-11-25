# Interview d'une spécification avec les sachants — conception

Date : 20/11/2026. État : à valider par le mainteneur.

## Intention

Une spécification rédigée par l'outil contient des hypothèses, et souvent des choses qui ne sont pas
claires. Le mainteneur veut les lever en réunion avec les sachants (le demandeur, les experts
métier) : partager son écran, parcourir la spécification ensemble, répondre aux points ouverts, et
noter toute remarque sur n'importe quel passage, même sans hypothèse.

Ce qui en sort doit rester honnête envers les sources de vérité (CWiki / Confluence, documents
OneDrive / SharePoint, Jira) : une décision prise en séance ne doit pas éloigner la spécification
de ces sources en silence.

## Objectifs

- Préparer une séance : voir les spécifications qui ont des points à traiter, en choisir une ou
  plusieurs, les ordonner, écarter des points.
- Parcourir toute la spécification, dans l'ordre, sur un écran partagé, lisible à distance.
- S'arrêter obligatoirement sur chaque hypothèse, chaque écart entre sources et chaque point ouvert.
- Noter une remarque sur n'importe quel élément (use case, étape, scénario, exigence, résumé).
- Noter une question nouvelle qu'on ne sait pas trancher, avec la personne qui doit y répondre.
- Réviser la spécification en une seule fois à la fin, par un diff relu, pas pendant la séance.
- Produire un compte rendu à copier dans un mail ou dans Teams.
- Savoir, pour chaque décision, quelle source de vérité elle contredit ou complète, et le garder
  visible tant que la source n'est pas mise à jour.

## Hors objectifs

- Écrire dans Confluence, OneDrive ou SharePoint. L'outil reste en lecture seule sur ces sources.
- Envoyer le compte rendu (mail, Teams) : l'outil le copie dans le presse-papiers, c'est tout.
- Plusieurs personnes connectées à la même séance : un seul écran, piloté par le mainteneur.
- Ordre du jour envoyé aux sachants avant la séance : la préparation reste au mainteneur, sans
  assistance du modèle.
- Transcription audio ou prise de notes automatique de la réunion.

## Anti-objectifs (échec même si « ça marche »)

- Faire attendre la réunion après chaque réponse (appel au modèle en direct).
- Une spécification modifiée en séance sans trace de qui a décidé quoi, quand.
- Un écart avec une source de vérité qui disparaît de la vue parce que la spécification a été
  révisée.
- Une nouvelle entrée dans le menu.
- Afficher sur l'écran partagé autre chose que la séance (notification de synchronisation, menu,
  autre projet, jeton, journal).

## Contraintes

- Architecture existante : hexagonale (`domain`, `application`, `infrastructure`), ArchUnit, SQLite
  avec Flyway (nouvelle migration, jamais de modification d'une migration appliquée).
- Front Angular, budget du bundle initial (500 kB, actuellement à la limite) : l'écran de séance se
  charge à la demande.
- Accessibilité WCAG 2.1 AA, tout au clavier.
- La spécification est versionnée (`SpecificationVersion`) ; une version n'est jamais modifiée,
  une révision en crée une nouvelle.

## Approches envisagées

**A. La séance comme une discussion « Discuter avec l'IA » (011).** Chaque point est un message ;
la fin déclenche la proposition de révision. Peu de code, mais aucune structure : ni ancrage sur un
élément, ni issue, ni responsable. Le compte rendu et les sources à mettre à jour devraient être
reconstruits par le modèle. Écartée.

**B. Une séance structurée, révisée à la fin par le mécanisme existant.** Choisie par le
mainteneur. La séance enregistre des points ancrés sur les éléments de la spécification, avec leur
issue ; le compte rendu et les sources à mettre à jour en sont tirés sans modèle ; les points
décidés alimentent la révision existante (011), relue en diff.

**C. Modifications directes, sans modèle.** Le mainteneur réécrit chaque élément pendant la
séance. Lourd en réunion, et rien ne propage un changement aux scénarios et exigences liés.
Écartée.

## Ce qui a été décidé avec le mainteneur

1. **Écran partagé** : la séance est conçue pour être projetée devant les sachants.
2. **Entrée depuis la page de la spécification**, pas de nouvelle entrée de menu.
3. **Parcours de toute la spécification**, pas seulement des hypothèses : une remarque peut porter
   sur un élément qui n'en avait pas.
4. **Changements appliqués à la fin**, en une seule révision relue en diff.
5. **Questions qui émergent** : notées comme points ouverts, avec un responsable ; les réponses qui
   soulèvent de nouvelles questions sont repérées par la révision de fin, pas en direct.
6. **Compte rendu à copier-coller** (mail, Teams).
7. **Approche B.**
8. **Préparation** : voir les spécifications avec des points à traiter, en sélectionner plusieurs
   pour une même séance ; préparation non assistée, sans ordre du jour.
9. **Rattachement précis** : chaque hypothèse, écart entre sources et point ouvert est rattaché à
   un élément de la spécification (étape d'un use case, scénario, exigence, résumé), dans le format
   de la spécification. Il est produit par la génération et la révision.
10. **« À reporter »** : créés d'eux-mêmes pour une décision sur un use case qui cite une source de
    vérité, ou qui tranche un écart ; le mainteneur peut aussi désigner une source à la main en
    séance ; le texte à reporter est la décision telle que notée, sans réécriture par le modèle.

## Conception

### Vocabulaire

- **Séance** : un passage en revue d'une ou plusieurs spécifications, dans un ordre choisi, à une
  date, avec des participants (noms saisis librement, pas des comptes). Elle est d'abord
  *préparée*, puis *en cours*, puis *terminée*.
- **Point** : ce qu'on note en séance, ancré sur un élément de la spécification. Cinq origines :
  - une **hypothèse** existante (`H-x`) ;
  - un **écart entre sources** existant (016) ;
  - un **point ouvert** existant (voir plus bas) ;
  - une **remarque** créée en séance sur n'importe quel élément ;
  - une **question** créée en séance.
- **Issue d'un point** : `CONFIRMÉ` (le texte reste), `À MODIFIER` (avec ce qu'on veut à la place),
  `À RETIRER`, `REPORTÉ` (avec la personne qui doit répondre, et une échéance facultative), ou pas
  encore traité.
- **À reporter** : une source de vérité qu'une décision contredit ou complète, avec ce qu'il faut y
  changer.

### Préparation (page « Spécifications »)

Pas de nouvelle entrée de menu : la préparation se fait sur la page « Spécifications » existante.

- Sous chaque spécification, un résumé de ses points à traiter : « 3 hypothèses · 2 points
  ouverts · 1 écart entre sources ». Un filtre « Avec des points à traiter ».
- Une case à cocher par spécification ; dès qu'une est cochée, « Préparer une séance (n) ».
- L'écran de préparation : ordre de passage des spécifications, participants, et pour chaque
  spécification la liste de ses points, avec la possibilité d'en écarter (ils restent ouverts,
  simplement hors de cette séance).
- Une séance préparée est enregistrée et apparaît en tête de la page « Spécifications »
  (« Séance préparée : 2 spécifications, 7 points · Démarrer · Modifier · Supprimer »).
- « Faire le point » sur la page d'une spécification crée et démarre directement une séance d'une
  seule spécification, sans passer par la préparation.

```
Spécifications                         [Préparer une séance (2)]
☐ Avec des points à traiter
────────────────────────────────────────────────────────────────
▸ Séance préparée : 2 spécifications, 7 points   [Démarrer]
☑ Émission des avoirs     3 hypothèses · 2 points ouverts
☑ Relance des impayés     1 hypothèse · 1 écart entre sources
☐ Export comptable        aucun point à traiter
```

### Rattachement des points dans la spécification

Aujourd'hui `Assumption` et `SourceDeviation` sont des listes globales, sans ancre ; seul un use
case peut citer une hypothèse comme source. Le format gagne une **ancre** sur les hypothèses, les
écarts et les points ouverts : le code de l'élément (`UC-2`, `SA-2.3`, `EX-4`, ou le résumé) et,
pour un use case, le numéro de l'étape nominale ou de l'alternative.

- La génération, la révision, l'édition par l'IA et la structuration d'un texte importé
  produisent l'ancre ; les consignes changent, et l'évaluation (`ci/check-eval.sh`) est refaite.
- Une ancre qui ne désigne aucun élément de la version (élément retiré, numéro d'étape hors
  limites) est retirée par le code, comme les citations invalides aujourd'hui.
- Les versions antérieures n'ont pas d'ancre : leurs points sont présentés dans un arrêt
  « Non rattachés » en fin de spécification. La révision de fin de séance les rattache.

### Nouveauté dans la spécification : le point ouvert

Aujourd'hui une spécification n'a que des hypothèses (une réponse supposée). Il lui faut aussi des
**points ouverts** : une question sans réponse ni valeur par défaut, ancrée sur un élément, avec un
responsable facultatif. Ils s'affichent dans une rubrique « Points ouverts » à côté des
« Hypothèses », avec un code (`Q-x`). Ils sont produits :
- par la séance (les points `REPORTÉ`) ;
- par la révision de fin de séance, quand une réponse laisse une question en suspens ;
- à terme par la génération elle-même (hors périmètre de cette feature, à évaluer).

Le format de la spécification (`SpecificationContent`, rendu Markdown, export Jira) gagne donc une
liste `openQuestions`. Les versions antérieures se lisent avec une liste vide.

### Parcours

Les spécifications se suivent dans l'ordre de la préparation. Dans chacune, l'ordre suit la
spécification rendue : résumé, puis chaque use case (titre, étapes nominales, alternatives), ses
scénarios, puis les exigences, puis l'arrêt « Non rattachés » s'il y a lieu. Un élément porte ses
hypothèses, écarts et points ouverts, d'après leur ancre. Les points écartés à la préparation ne
s'affichent pas. Le curseur avance élément par élément (↑ ↓), saute d'un use case à l'autre (← →), et
« prochain point à traiter » amène au suivant non traité.

### Écran de séance

Entrées : « Démarrer » sur une séance préparée, ou « Faire le point » dans l'en-tête de la page
d'une spécification, avec le nombre de points à traiter (« Faire le point (5) »). Ce bouton est
toujours présent, même sans point (une relecture reste utile), sauf pendant une génération ou une
révision en cours.

L'écran occupe toute la page (menu masqué), en gros caractères :

```
┌──────────────────────────────────────────────────────────────┐
│ Spéc. 1/2 « Émission des avoirs »   UC-2 · 4/12   [Terminer] │
├──────────────────────────────────────────────────────────────┤
│ UC-2  Émettre un avoir partiel        source : CWiki p. 4512 │
│  1. Le gestionnaire ouvre la facture.                        │
│  2. Il choisit les lignes à créditer.                        │
│ ▶3. Il saisit le montant.                                    │
│      💬 Remarque : validation manager au-delà de 500 €       │
│  4. ⚠ H-3 Le montant ne dépasse pas le facturé  [Répondre]   │
│  5. L'avoir est émis et envoyé au client.                    │
│                                                              │
│ ↑↓ élément · R remarque · Q question · ↵ répondre · → UC     │
│ Points : 2 décidés · 1 remarque · 3 ouverts restants         │
└──────────────────────────────────────────────────────────────┘
```

- Au début : les participants de la préparation, modifiables (saisie facultative, proposée d'après
  la séance précédente).
- Répondre à un point : un champ de texte sous le point, et le choix de l'issue au clavier.
- Une séance est enregistrée au fil de l'eau : fermer l'onglet ou quitter l'outil ne perd rien, et
  la séance reprend où elle en était.
- Pendant la séance, les notifications de l'application (synchronisation 017, etc.) ne
  s'affichent pas.

### Fin de séance

« Terminer » ouvre un récapitulatif, en trois blocs :

1. **Compte rendu**, avec « Copier » : en HTML (titres et listes gardés par Outlook et Teams) et en
   texte brut, dans le presse-papiers. Un seul compte rendu pour la séance, regroupé par
   spécification. Construit sans modèle depuis les points :

   ```
   Point sur « Émission des avoirs » — 20/11, avec Julie M., Paul D.
   Décisions
    • H-3 : un avoir partiel est plafonné au montant TTC facturé.
    • UC-2 : validation par le manager au-delà de 500 €.
   À vérifier
    • Qui valide quand le manager est absent ? — Paul D.
   Sources à mettre à jour
    • CWiki « Règles de facturation » : ajouter le plafond des avoirs partiels.
   ```

2. **Révision**, une par spécification de la séance : « Proposer la nouvelle version ». Les points `À MODIFIER`, `À RETIRER` et
   `CONFIRMÉ` (une hypothèse confirmée cesse d'être une hypothèse) deviennent des points établis.
   Ils entrent dans la discussion de la spécification (011) comme un seul message, daté et signé
   des participants (« Séance du 20/11 avec Julie M., Paul D. : … »), puis la révision existante
   rédige la nouvelle version, relue en diff dans l'écran existant. Les points `REPORTÉ` deviennent
   des points ouverts de la nouvelle version. La révision signale aussi les questions que les
   réponses laissent en suspens, qui deviennent des points ouverts.

3. **Sources à mettre à jour** : la liste des « à reporter » (voir plus bas).

On peut terminer la séance sans proposer la révision tout de suite (le compte rendu suffit, la
révision se fait plus tard depuis la page de la spécification).

### Sources de vérité : les « à reporter »

Un use case cite déjà sa source (`SourceRef` : page CWiki, document, ticket Jira, élément
d'architecture) ; les autres éléments n'en citent pas. Un **à reporter** est créé :
- pour un point décidé (`À MODIFIER`, `À RETIRER`) sur un use case, une étape ou une alternative
  dont le use case cite une source de vérité ;
- pour un écart entre sources tranché en séance (sa référence) ;
- à la main : en séance, le mainteneur peut désigner une source de la connaissance du projet sur
  n'importe quel point.

Il contient la source et le texte de la décision tel que noté en séance, sans réécriture par le
modèle.

- Les « à reporter » sont listés sur la page de la spécification tant qu'ils sont ouverts, et dans
  le compte rendu.
- Quand la synchronisation (017) voit que la source a changé depuis la séance, l'« à reporter »
  passe à « source modifiée, à vérifier » ; le mainteneur le clôt d'un clic. L'outil ne décide pas
  seul qu'une source modifiée dit bien la même chose.
- Une décision devient une source citée dans la nouvelle version (le message de séance de la
  discussion, sorte `DISCUSSION` existante), si bien que la spécification dit d'où vient chaque
  phrase : « décidé en séance le 20/11 ».
- L'écart reste visible : une décision qui contredit une source de référence apparaît dans la
  rubrique « Écarts entre sources » (016) tant que l'« à reporter » est ouvert.

### Données

- `interview_session` : état (préparée, en cours, terminée), dates, participants.
- `interview_session_spec` : séance, spécification, ordre, version relue, points écartés.
- `interview_point` : séance, spécification, ancre (code de l'élément, et étape ou passage cité si
  précisé), origine, texte, issue, responsable, échéance, source désignée à la main.
- `pending_source_update` (« à reporter ») : spécification, point d'origine, référence de la
  source, empreinte de la source au moment de la décision, texte proposé, état.
- `SpecificationContent.openQuestions` : nouvelle liste dans le JSON de la version ; ancre sur
  `Assumption`, `SourceDeviation` et les points ouverts.

### Ce qui ne change pas

La révision (011), le diff, l'écran de relecture, la connaissance du projet et la synchronisation
sont réutilisés. Changent : le format de la spécification (points ouverts, ancres) et les consignes
qui le produisent.

## Choix faits sans le mainteneur (à vérifier)

Questions du relecteur tranchées sans changer ce que le mainteneur verra :

- **Révision** : si la spécification n'a pas encore de discussion (011), la séance la crée. Les
  points d'une spécification forment un seul message de séance ; la limite actuelle de 20
  changements de 1 000 caractères ne s'applique pas à ce message (un point a sa propre limite). Un
  point « À RETIRER » sans texte passe comme « retirer <élément> ».
- **Naissance des « à reporter »** : à l'application de la nouvelle version, pas à la décision en
  séance ; une proposition rejetée n'en crée aucun. Avant l'application, le compte rendu les montre
  déjà, tirés des points.
- **Clôture de la séance** : à « Terminer ». La révision peut être proposée plus tard, et
  relancée, depuis la page de la spécification tant que les points de la séance n'ont pas été
  appliqués.
- **Points reportés et points tranchés** : déterministes. Le code ajoute les points `REPORTÉ`
  comme points ouverts (`Q-x`, code attribué par `IdentifierAssigner`) et retire les hypothèses et
  points ouverts tranchés de la nouvelle version, sans dépendre du modèle.
- **Version relue** : une séance relit la dernière version de chaque spécification au moment où
  elle démarre. Si une nouvelle version apparaît avant la fin (par la discussion), la séance le
  signale ; ses points restent rattachés par code d'élément, et la révision part de la dernière
  version. Une seule séance en cours par spécification.
- **Compteur « points à traiter »** : les hypothèses, écarts et points ouverts de la dernière
  version, moins ceux déjà tranchés dans une séance dont la révision n'a pas été appliquée.
- **« Source modifiée depuis la séance »** : l'« à reporter » garde l'empreinte de l'élément de la
  connaissance (page, document, ticket) au moment de la décision ; la comparaison avec l'empreinte
  courante de l'index se fait à l'affichage. Aucun changement dans la synchronisation (017).
- **« À reporter » ouverts et rubrique « Écarts entre sources »** : affichés en surimpression sur
  la page de la spécification, calculés depuis les « à reporter » ; ils ne sont pas écrits dans les
  versions (immuables), donc ni dans le Markdown ni dans l'export Jira, mais dans le compte rendu.

## Laissé au constructeur

- Le découpage exact de l'écran en composants, et la façon de masquer menu et notifications.
- La forme de l'ancre quand une remarque porte sur une phrase sélectionnée (texte cité, ou
  position dans l'élément).
- La rédaction des consignes pour produire les ancres et les points ouverts, et leur évaluation.
- Le rendu des points ouverts dans l'export Jira.
