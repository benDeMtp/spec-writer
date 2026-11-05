# Research: ADR du projet

Prolonge `specs/002-projets-contexte-mcp/research.md` (R3 lecture Antora, R20 indexation, R24
connaissance transmise) et le correctif adr-tools de la 003 (T030). Aucun point de la spec n'était
à clarifier ; les décisions portent sur la manière.

## R1. Compteurs (FR-001)

**Constat** : `KnowledgeIndex.count(projectId, kind)` compte les lignes de `knowledge_item`, donc les
morceaux : un ADR long en compte 2 ou 3 (21 ADR → 57), une section longue aussi.

**Decision** : les compteurs comptent les éléments, c'est-à-dire les lignes de morceau 0
(`chunk = 0`). Chaque élément découpé garde un morceau 0 ; un ADR est un seul élément (002, R20),
une section de documentation aussi (sa référence porte l'ancre de la section).

**Alternatives considered** : `count(distinct ref)` : même résultat, plus coûteux, et faux pour les
tickets et éléments d'architecture si deux morceaux portaient un jour des références différentes.

## R2. Liste des ADR (FR-003 à FR-009)

**Decision** : nouveau cas d'usage `ListAdrs` (application/knowledge) qui lit
`index.all(projectId, ADR)`, regroupe par référence (morceau 0 : titre, statut, successeur), puis
calcule pour chaque ADR :

- **numéro** : nouvelle valeur du domaine `AdrNumber`, extraite de la référence puis du titre, avec
  les règles déjà présentes dans `SelectKnowledge` (`ADR-5`, `adr_005`, `0005-titre.html`,
  « 5. Titre ») ; `SelectKnowledge` l'utilise aussi (déplacement, pas de règle nouvelle). Valeur
  entière : « ADR-5 », « 0005 » et « 5. » sont le même numéro.
- **doublon** : plus d'un ADR avec la même valeur de numéro.
- **successeur** : le numéro cité (`supersededBy`), et s'il existe parmi les ADR du projet.
- **transmis à l'IA** : faux pour un ADR remplacé ou obsolète (règle de la 002).
- **adresse** : R3.

Tri : numéro croissant, puis titre ; sans numéro à la fin, par titre. Résumé : nombre par statut
(ACCEPTED, PROPOSED, SUPERSEDED, DEPRECATED, UNKNOWN). Le filtre par statut est appliqué par le
front, le résumé porte toujours sur tous les ADR (FR-007).

**Alternatives considered** : *requête SQL dédiée* : le regroupement et le numéro sont des règles du
domaine, testables sans base ; le volume (quelques centaines d'ADR au plus) le permet.

## R3. Lien vers la page de documentation (FR-006)

**Constat** : pour une source de type URL, la référence est l'adresse de la page (lien direct). Pour
un dossier local (cas de l'utilisateur), la référence est le chemin relatif au dossier
(`aurora/adr/0001-….html`) : aucun lien n'existe aujourd'hui, et un lien `file://` est bloqué par le
navigateur depuis une page `http://127.0.0.1`.

**Decision** : l'outil sert lui-même les fichiers des dossiers déclarés :
`GET /api/projects/{id}/doc-files/{chemin}`. Le lien de l'ADR pointe vers cette adresse ; les liens
relatifs de la page (CSS, images, autres pages) s'y résolvent naturellement.

Sécurité (constitution V, OWASP A01/A03) :
- seules les sources **FOLDER** du projet sont servies ; le chemin est normalisé, résolu dans chaque
  dossier déclaré et refusé s'il en sort (`..`, lien symbolique sortant, chemin absolu) ;
- extensions permises : `html`, `htm`, `css`, `png`, `jpg`, `jpeg`, `gif`, `svg`, `webp`, `woff`,
  `woff2` ; autre extension, fichier absent ou dossier : 404 sans détail ;
- en-têtes : `Content-Security-Policy: sandbox allow-same-origin; script-src 'none'; object-src 'none'; default-src 'self' 'unsafe-inline'` (aucun script de
  la documentation ne s'exécute : `sandbox` sans `allow-scripts`, doublé de `script-src 'none'` ;
  `allow-same-origin` garde l'origine de l'outil pour que CSS et images partent avec le cookie de
  session, sans danger tant que `allow-scripts` est absent), `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` ;
- même authentification que le reste de l'API (cookie de session) ; le contenu n'est jamais
  journalisé, seul le refus l'est, sans le chemin.

**Alternatives considered** :
- *Pas de lien pour les dossiers* : contraire à la demande (le cas de l'utilisateur est un dossier).
- *Lien `file://`* : bloqué par les navigateurs depuis une origine http.
- *Montrer le texte indexé dans la page* : écarté par l'utilisateur (Clarifications).

## R4. ADR entier dans la génération (FR-010 à FR-012)

**Constat** : `SelectKnowledge.adrs` retient jusqu'à 4 morceaux d'ADR (dédoublonnés par référence et
morceau) et coupe chacun à `6 000 / 4` caractères : l'IA reçoit un fragment.

**Decision** :
1. Les résultats de la recherche (morceaux) sont ramenés à leur ADR, dans l'ordre de leur meilleur
   rang, une fois chacun ; la règle de succession de la 002 s'applique ensuite.
2. Le texte d'un ADR est la concaténation de ses morceaux dans l'ordre, lus dans `index.all(ADR)`
   (déjà chargé pour la succession).
3. Budget : **6 000 caractères au total** (inchangé), **4 000 au plus par ADR**, 4 ADR au plus. Les
   ADR passent entiers dans l'ordre tant que le total le permet ; le premier qui ne tient pas arrête
   la liste. Un ADR de plus de 4 000 caractères est coupé à la dernière ligne de titre
   (`#`, rendue par `StructuredText`) qui précède la limite, sinon au dernier paragraphe, et finit par
   « [ADR tronqué] ».
4. Le texte est déterministe à entrées égales (ordre des morceaux, ordre des ADR).

**Rationale** : 4 000 laisse passer en entier les ADR habituels (contexte, décision, conséquences :
1 500 à 3 500 caractères chez l'utilisateur) ; le total de 6 000 garde la taille du prompt de la 002.

**Alternatives considered** :
- *Seconde passe où l'IA demande les sources manquantes* : une requête de plus, proposée à part.
- *Outil « lire le document » donné à l'IA* : contraire aux sessions sans outil (contracts/llm-port.md,
  règle 6a) et à la reproductibilité.

## R5. Évaluation (SC-004)

**Constat** : l'empreinte vérifiée par `ci/check-eval.sh` ne couvre pas `application/knowledge` ; la
gate ne forcera pas une nouvelle évaluation.

**Decision** : l'évaluation réelle (`./mvnw -pl backend verify -Peval`) est relancée quand même,
avec l'accord de l'utilisateur (environ 150 requêtes Copilot), puisque le texte transmis change
pour le cas `golden/07-avoir-adr-architecture` ; les scores doivent rester ≥ `baseline.json`. Les
réponses rejouées des E2E et du golden set sont vérifiées : leurs `userContains` ne doivent pas
dépendre d'un fragment d'ADR coupé.
