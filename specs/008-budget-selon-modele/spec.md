# Feature Specification: Budget de contexte selon le modèle

**Feature Branch**: `feature/011-budget-selon-modele`

**Created**: 2026-11-07

**Status**: Draft

**Input**: User description: "Budget de contexte selon le modèle choisi : morceau 2 de la conception docs/superpowers/specs/2026-11-06-documents-designes-design.md (section « Morceau 2 »). L'écran Configuration › Modèle affiche pour chaque modèle Copilot sa limite de prompt et son coefficient de facturation (fournis par le SDK Copilot : ModelCapabilities.limits.maxPromptTokens, maxContextWindowTokens, ModelBilling.multiplier). Le budget de la connaissance transmise (aujourd'hui fixe : 35 000 caractères au total, 12 000 pour les ADR, 8 000 pour les documents trouvés, 12 000 pour les documents désignés avec @) découle de la limite du modèle choisi, avec une marge pour le prompt système, le besoin, les réponses et la sortie ; les plafonds actuels restent des minimums, et un modèle sans limite connue garde le comportement d'aujourd'hui. Un seul modèle pour tout l'outil (réglage existant). Avant une génération dont des documents désignés sont coupés faute de place, l'outil signale si un modèle de plus grande capacité les transmettrait en entier. Cas réel : la matrice de 6 feuilles (~30 000 caractères) mentionnée avec @ n'est transmise qu'en partie. Hors périmètre : classeur en base de la spec, joindre un fichier."

Prolonge `specs/007-mention-documents/` (documents désignés avec @, état des mentions),
`specs/006-documents-sharepoint/` et `specs/004-adr-du-projet/` (plafonds de la connaissance
transmise), dont les décisions restent valables. Conception d'ensemble :
`docs/superpowers/specs/2026-11-06-documents-designes-design.md`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - La connaissance transmise suit la capacité du modèle (Priority: P1) 🎯 MVP

L'utilisateur a choisi dans la configuration un modèle capable de lire de longs textes. Quand il
génère une spécification, l'outil transmet à l'IA autant de connaissance du projet que ce modèle
peut en recevoir, au lieu des 35 000 caractères fixes d'aujourd'hui : la matrice de règles de
gestion mentionnée avec @ (environ 30 000 caractères, 6 feuilles) arrive en entier, au lieu de ses
premières feuilles. Avec un modèle plus petit, ou dont la capacité n'est pas connue, l'outil se
comporte comme aujourd'hui.

**Why this priority**: c'est le blocage du cas réel : la place, pas le modèle, limite ce que l'IA
reçoit.

**Independent Test**: avec un modèle dont la limite connue est grande, mentionner un document de
30 000 caractères puis générer : le document est transmis en entier ; avec un modèle sans limite
connue, il est transmis en partie comme aujourd'hui.

**Acceptance Scenarios**:

1. **Given** un modèle choisi dont la limite de prompt est grande (par exemple 128 000 tokens),
   **When** l'utilisateur génère une spécification qui mentionne un document de 30 000
   caractères, **Then** le document est transmis en entier et l'état de la mention l'indique.
2. **Given** un modèle sans limite connue, ou aucun modèle choisi (défaut du compte), **When** la
   même génération est lancée, **Then** les plafonds d'aujourd'hui s'appliquent à l'identique.
3. **Given** un modèle dont la limite est plus petite que ce que les plafonds d'aujourd'hui
   supposent, **When** une génération est lancée, **Then** les plafonds d'aujourd'hui
   s'appliquent (ils sont des minimums) et la génération n'est pas bloquée.
4. **Given** un grand modèle, **When** la connaissance est préparée, **Then** chaque catégorie
   (ADR, documents trouvés, documents désignés, tickets) grandit dans les mêmes proportions
   qu'aujourd'hui, et la fiche générale garde la priorité.
5. **Given** l'utilisateur change de modèle, **When** il régénère sans autre changement, **Then**
   la connaissance transmise peut changer et la version n'est pas réutilisée à tort.

---

### User Story 2 - Voir la capacité et le coût de chaque modèle (Priority: P2)

Dans la configuration, la liste des modèles montre pour chacun sa capacité de lecture (la taille
de texte qu'il accepte) et son coût relatif (son coefficient de facturation Copilot). L'utilisateur
choisit en connaissance de cause : un modèle capable de lire une grosse matrice coûte peut-être
plus cher par requête.

**Why this priority**: le choix du modèle devient décisif avec l'US1 ; sans ces informations,
l'utilisateur choisit au hasard. L'US1 fonctionne toutefois sans cet affichage.

**Independent Test**: ouvrir la configuration : chaque modèle affiche sa capacité (ou « capacité
inconnue ») et son coefficient (ou « coût inconnu ») ; le modèle choisi indique la place qu'il
donne à la connaissance du projet.

**Acceptance Scenarios**:

1. **Given** des modèles dont Copilot fournit la limite et le coefficient, **When** l'utilisateur
   ouvre la liste, **Then** chacun montre sa capacité en clair (par exemple « environ 128 000
   tokens, soit environ 380 000 caractères ») et son coefficient (« ×1 », « ×0,33 »).
2. **Given** un modèle sans ces informations, **Then** la liste indique « capacité inconnue » ou
   « coût inconnu », sans erreur.
3. **Given** le modèle choisi, **Then** l'écran indique la place qu'il donne à la connaissance du
   projet (par exemple « connaissance transmise : jusqu'à 140 000 caractères »).
4. **Given** la liste, **When** elle est parcourue au clavier et au lecteur d'écran, **Then** ces
   informations sont lues avec chaque modèle et l'écran ne présente aucune violation
   d'accessibilité.

---

### User Story 3 - Être averti qu'un autre modèle transmettrait le document en entier (Priority: P3)

Quand l'état d'une mention indique qu'un document désigné ne sera transmis qu'en partie ou pas
du tout faute de place, l'outil dit si un autre modèle autorisé pour le compte le transmettrait en
entier, et lequel (le moins coûteux qui suffit). L'utilisateur peut alors changer de modèle avant
de générer.

**Why this priority**: elle évite de découvrir après coup qu'un document a été coupé ; mais l'US1
et l'US2 suffisent à corriger le cas réel.

**Independent Test**: avec un petit modèle choisi, mentionner la matrice : l'état indique
« transmis en partie » et propose un modèle plus grand qui la transmettrait en entier ; sans
modèle assez grand, rien n'est proposé.

**Acceptance Scenarios**:

1. **Given** un document désigné transmis en partie avec le modèle choisi, et un modèle autorisé
   plus grand qui le transmettrait en entier, **When** l'utilisateur regarde l'état de la mention,
   **Then** il lit « transmis en entier avec <modèle> (×<coefficient>) ».
2. **Given** plusieurs modèles qui suffisent, **Then** le moins coûteux est proposé ; à coût égal,
   le plus petit qui suffit.
3. **Given** aucun modèle autorisé assez grand, **Then** rien n'est proposé, et l'état reste
   « transmis en partie ».
4. **Given** la suggestion, **When** l'utilisateur ne change rien, **Then** la génération se fait
   avec le modèle choisi : l'outil ne change jamais de modèle de lui-même.

---

### Edge Cases

- **Liste des modèles indisponible** (Copilot non connecté, hors ligne) : la limite du modèle choisi
  est inconnue, les plafonds d'aujourd'hui s'appliquent.
- **Limite fournie aberrante** (nulle, négative, ou absurde) : traitée comme inconnue.
- **Limite très grande** (un million de tokens et plus) : la connaissance transmise reste plafonnée
  à un maximum raisonnable, pour ne pas noyer le besoin ni allonger les réponses sans fin.
- **Modèle choisi retiré du compte** : comme aujourd'hui pour le choix du modèle ; la limite est
  inconnue, les plafonds d'aujourd'hui s'appliquent.
- **Fiche générale longue** : la fiche garde la priorité, et la place restante grandit avec le
  modèle comme le reste.
- **Opérations autres que la rédaction** (questions, réévaluation, modification, fusion) : même
  budget que la rédaction pour un même modèle, pour que les questions voient les mêmes documents
  que la spécification.
- **Évaluation automatique** : elle tourne avec le modèle par défaut du compte ; si sa limite est
  connue, la connaissance transmise peut grandir, ce que les scores doivent accepter.

## Requirements *(mandatory)*

### Functional Requirements

**Capacité des modèles**

- **FR-001**: L'outil MUST lire, pour chaque modèle proposé par Copilot, sa limite de prompt, sa
  fenêtre de contexte et son coefficient de facturation quand Copilot les fournit.
- **FR-002**: Une limite absente, nulle, négative ou supérieure à 10 millions de tokens MUST être
  traitée comme inconnue ; un coefficient absent ou négatif, comme inconnu.

**Budget de la connaissance**

- **FR-003**: La place de la connaissance transmise MUST découler de la limite de prompt du modèle
  choisi : la limite, moins une réserve pour les consignes, le besoin, les réponses et la réponse
  de l'IA, convertie en caractères de façon prudente pour le français.
- **FR-004**: Les plafonds d'aujourd'hui (35 000 caractères au total ; 12 000 pour les ADR ;
  8 000 pour les documents trouvés ; 12 000 pour les documents désignés, 8 000 par document ;
  3 000 pour les tickets, l'architecture et les rôles ; 1 500 pour les sous-tâches) MUST rester
  des minimums : un modèle plus petit, sans limite connue, ou aucun modèle choisi garde exactement
  le comportement d'aujourd'hui.
- **FR-005**: Avec un modèle plus grand, chaque plafond MUST grandir dans la même proportion que
  le total, et le total MUST rester plafonné à 200 000 caractères.
- **FR-006**: Le nombre d'éléments par catégorie (4 documents désignés, 4 ADR, 2 documents trouvés,
  3 tickets, 15 sous-tâches) MUST rester celui d'aujourd'hui : seule la taille grandit.
- **FR-007**: La fiche générale MUST garder la priorité sur toute la connaissance (FR-017 de la
  004), et sa taille maximale (20 000 caractères) ne change pas.
- **FR-008**: Le même budget MUST s'appliquer à toutes les opérations de l'IA pour un même modèle
  (questions, réévaluation, rédaction, modification, fusion, structuration d'un texte importé).
- **FR-009**: Changer de modèle MUST être pris en compte dans l'empreinte des entrées d'une
  génération quand la connaissance transmise change, pour qu'une version ne soit pas réutilisée
  à tort (FR de la 004 sur la réutilisation).
- **FR-010**: L'état des mentions (007) MUST être calculé avec le budget du modèle choisi.

**Affichage**

- **FR-011**: La liste des modèles de la configuration MUST montrer pour chacun sa capacité en
  tokens et en caractères approximatifs, et son coefficient de facturation, ou « inconnu ».
- **FR-012**: L'écran MUST indiquer, pour le modèle choisi, la place maximale donnée à la
  connaissance du projet.
- **FR-013**: Ces informations MUST être accessibles (lues avec chaque option au lecteur d'écran,
  aucune violation d'accessibilité).

**Suggestion d'un modèle**

- **FR-014**: Quand un document désigné est transmis en partie ou pas du tout faute de place,
  l'état de la mention MUST indiquer le modèle autorisé le moins coûteux qui le transmettrait en
  entier (à coût égal, le plus petit qui suffit), s'il en existe un.
- **FR-015**: L'outil MUST ne jamais changer de modèle sans action de l'utilisateur.

### Key Entities *(include if feature involves data)*

- **Capacité d'un modèle** : limite de prompt et fenêtre de contexte (en tokens), coefficient de
  facturation ; chacune connue ou inconnue.
- **Budget de la connaissance** : place totale en caractères et sa répartition par catégorie,
  calculées pour un modèle ; égal aux plafonds d'aujourd'hui pour un modèle inconnu ou petit.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Dans le cas réel, avec un modèle de 128 000 tokens de prompt ou plus, la matrice de
  30 000 caractères mentionnée avec @ est transmise en entier (100 % de son texte indexé).
- **SC-002**: Avec un modèle sans limite connue, la connaissance transmise est identique, au
  caractère près, à celle d'aujourd'hui pour les mêmes entrées.
- **SC-003**: 100 % des modèles de la liste affichent leur capacité et leur coût, ou « inconnu ».
- **SC-004**: Chaque document désigné coupé faute de place, pour lequel un modèle autorisé
  suffirait, est accompagné de la suggestion de ce modèle avant la génération.
- **SC-005**: La pertinence des questions et la structure des scénarios de l'évaluation réelle ne
  reculent pas (seuils de `baseline.json`).

## Assumptions

- Copilot fournit la limite de prompt et le coefficient pour la plupart des modèles ; à défaut,
  le comportement d'aujourd'hui s'applique.
- La conversion tokens → caractères est prudente (de l'ordre de 3 caractères par token pour le
  français) : mieux vaut transmettre un peu moins que dépasser la limite du modèle.
- La réserve couvre les consignes (prompt système), le besoin (2 000 caractères au plus), les
  réponses, la fiche générale et la réponse attendue de l'IA ; sa valeur exacte est fixée par le
  plan.
- Un seul modèle pour tout l'outil, choisi dans la configuration (réglage existant de la 001) ;
  aucun choix par spécification.
- Aucun prompt ne change ; mais la connaissance transmise grandit avec un grand modèle :
  l'évaluation réelle est relancée avec l'accord du mainteneur (constitution IV).
- Hors périmètre : classeur traité en entier comme base de la spécification, joindre un fichier
  hors du dossier synchronisé (features suivantes).
