<!--
Sync Impact Report
==================
Version change: 1.0.0 → 2.0.0
Bump rationale: MAJOR, car le principe II est redéfini de façon incompatible (abandon du Gherkin
exécutable et du parser Gherkin au profit de scénarios « En tant que / Quand / Alors »).

Principes modifiés :
- II. Spécifications Gherkin valides et traçables → II. Scénarios d'acceptation structurés et
  traçables (format En tant que / [Étant donné] / Quand / Alors, contrôle de structure au lieu du
  parser Gherkin, export Markdown seul, rationale réécrite)
- I. Expérience utilisateur guidée : mention du Gherkin remplacée (sans changement de règle)
- III. Test-First : périmètre de couverture renommé « générateur de use cases et de scénarios »
- IV. Tests de bout en bout : « parser Gherkin » → « contrôle de structure »

Sections modifiées :
- Exigences de sécurité > Secrets : trousseau de clés du système d'exploitation cité comme
  gestionnaire de secrets attendu en local ; interdiction d'écrire un secret dans un fichier de
  l'application.

Sections ajoutées : aucune
Sections supprimées : aucune

Artefacts à réaligner : specs/001-local-spec-assistant/spec.md est déjà conforme (FR-012,
FR-012a, FR-019, FR-033a).

TODO différés : aucun.
-->

# Spec Writer Constitution

## Core Principles

### I. Expérience utilisateur guidée

L'outil accompagne l'utilisateur, depuis un besoin exprimé en langage naturel jusqu'à une
spécification exploitable, sans exiger de compétence préalable en rédaction de scénarios d'acceptation ou de use
cases.

- Les questions posées DOIVENT être déduites du besoin initial et des réponses précédentes. Une
  question dont la réponse est déjà connue ou déductible NE DOIT PAS être posée.
- Les questions DOIVENT être présentées une par une ou par petits lots thématiques (5 au maximum),
  avec un indicateur de progression visible.
- Chaque question DOIT expliquer brièvement pourquoi elle est posée et, lorsque c'est possible,
  proposer des réponses suggérées ainsi qu'une valeur par défaut raisonnable.
- L'utilisateur DOIT pouvoir revenir sur une réponse, passer une question (l'hypothèse retenue
  est alors tracée), interrompre puis reprendre sa session sans perte de données.
- Les messages d'erreur DOIVENT dire ce qui s'est passé et quoi faire ensuite, dans un vocabulaire
  métier, sans trace technique brute.
- Toute interface graphique DOIT respecter le niveau WCAG 2.1 AA (navigation clavier, contrastes,
  libellés accessibles).

Rationale : la valeur du produit tient à la qualité du dialogue. Un questionnaire long, répétitif
ou opaque fait abandonner l'utilisateur avant la génération de la spécification.

### II. Scénarios d'acceptation structurés et traçables

- Les scénarios produits sont des scénarios d'acceptation en langage naturel, au format
  « En tant que <acteur>, [Étant donné <contexte>,] Quand <action>, Alors <résultat attendu> ».
  Seul « Étant donné » est facultatif. Le Gherkin exécutable (fichiers `.feature`, mots-clés
  `Feature` / `Scenario`) est hors périmètre.
- Chaque scénario DOIT passer un contrôle de structure (acteur, action et résultat attendu
  présents et non vides) avant d'être présenté à l'utilisateur ; un scénario incomplet NE DOIT
  PAS être présenté comme valide.
- Chaque scénario DOIT être rattaché à un use case identifié, et chaque use case DOIT être
  rattaché au besoin initial ou à une réponse précise du questionnaire.
- Aucun comportement NE DOIT être inventé : toute hypothèse faite par l'outil DOIT être marquée
  explicitement comme telle dans la sortie et listée pour validation.
- La sortie DOIT distinguer les exigences fonctionnelles des exigences techniques.
- La génération DOIT être reproductible : à entrées identiques (besoin, réponses, version de
  l'outil), la structure de la spécification produite DOIT être identique.
- Les exports DOIVENT exister au minimum en Markdown.

Rationale : les spécifications sont lues par des humains (métier, développement, recette). Un
format simple et régulier se relit sans formation, et la traçabilité permet de vérifier qu'aucun
scénario n'est inventé.

### III. Test-First (NON NÉGOCIABLE)

- Le TDD est obligatoire : test écrit, test qui échoue, implémentation, refactoring.
- Aucun code de production NE DOIT être fusionné sans test qui en couvre le comportement.
- Chaque correction de bug DOIT commencer par un test qui reproduit le bug.
- La couverture de lignes DOIT rester supérieure ou égale à 80 % sur le cœur métier (moteur de
  questions, générateur de use cases et de scénarios). Une baisse bloque la fusion.
- Les tests DOIVENT être déterministes : toute dépendance non déterministe (horloge, aléa, modèle
  de langage, réseau) DOIT être isolée derrière une interface et simulée dans les tests unitaires.

Rationale : le produit génère des artefacts sur lesquels d'autres équipes s'appuient ; une
régression silencieuse se propage dans leurs spécifications.

### IV. Tests de bout en bout et qualité des sorties

- Chaque parcours utilisateur principal (besoin → questions → génération → export) DOIT être
  couvert par au moins un test de bout en bout automatisé.
- Un jeu de besoins de référence (golden set) DOIT être maintenu, avec les spécifications
  attendues ; toute modification du moteur de questions ou du générateur DOIT être validée
  contre ce jeu.
- Chaque sortie générée dans les tests DOIT passer le contrôle de structure et les contrôles de
  traçabilité du principe II.
- Si un modèle de langage est utilisé, une suite d'évaluation dédiée DOIT mesurer la pertinence
  des questions et la qualité des scénarios ; un recul des scores bloque la fusion.

Rationale : les tests unitaires ne suffisent pas à garantir qu'un dialogue complet aboutit à une
spécification utile. Seuls les tests de bout en bout et le golden set le vérifient.

### V. Sécurité et confidentialité par conception

- Les besoins et réponses saisis DOIVENT être traités comme des données confidentielles, car ils
  peuvent décrire des projets internes, des données personnelles ou des secrets d'entreprise.
- Aucune donnée saisie NE DOIT apparaître dans les logs, la télémétrie ou les messages d'erreur
  sans masquage.
- Toute entrée utilisateur DOIT être validée et bornée (taille, encodage, format), et toute sortie
  rendue DOIT être échappée selon son contexte (HTML, Markdown, fichier).
- Si un modèle de langage est utilisé, le contenu utilisateur DOIT être séparé des instructions
  système, et les risques d'injection de prompt DOIVENT être couverts par des tests.
- Aucune donnée NE DOIT être transmise à un service tiers sans que l'utilisateur en soit informé
  et l'ait accepté.

Rationale : un outil de rédaction de spécifications reçoit par nature des informations sensibles
sur les systèmes et les projets de ses utilisateurs.

## Exigences de sécurité

- **Secrets** : aucun secret (clé d'API, mot de passe, jeton) NE DOIT être versionné. Les secrets
  DOIVENT être conservés dans un gestionnaire de secrets (pour une application locale : le
  trousseau de clés du système d'exploitation) ou fournis par variables d'environnement, et
  jamais écrits dans un fichier de l'application.
- **Dépendances** : les dépendances DOIVENT être scannées pour les vulnérabilités connues à chaque
  build. Une vulnérabilité de sévérité haute ou critique bloque la fusion, sauf dérogation
  documentée et datée.
- **Analyse statique** : un outil SAST DOIT être exécuté en CI ; ses alertes bloquantes DOIVENT être
  corrigées ou justifiées.
- **Authentification et isolation** : si l'outil est multi-utilisateur, chaque session et chaque
  spécification DOIVENT être isolées par utilisateur, et l'accès à une ressource d'un autre
  utilisateur DOIT être couvert par un test négatif.
- **Stockage** : les données persistées DOIVENT être chiffrées au repos lorsque l'hébergement le
  permet, avec une durée de rétention définie et une suppression possible à la demande de
  l'utilisateur.
- **Référentiel** : les contrôles de sécurité DOIVENT couvrir au minimum les catégories pertinentes
  de l'OWASP Top 10 et, si un modèle de langage est utilisé, de l'OWASP Top 10 for LLM Applications.

## Workflow de développement et quality gates

- Chaque fonctionnalité suit le cycle Spec Kit : `/speckit-specify` → `/speckit-clarify` →
  `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`.
- Chaque plan DOIT contenir une vérification explicite de conformité à cette constitution
  (Constitution Check), refaite après la phase de conception.
- Une fusion DOIT satisfaire toutes les conditions suivantes :
  1. tous les tests passent (unitaires, intégration, bout en bout, golden set) ;
  2. seuil de couverture respecté ;
  3. scan de dépendances et SAST sans alerte bloquante ;
  4. revue de code approuvée, portant explicitement sur l'UX, les tests et la sécurité ;
  5. toute modification visible par l'utilisateur est accompagnée d'une mise à jour de la
     documentation utilisateur.
- Toute complexité supplémentaire (nouvelle dépendance, nouveau service, nouvelle couche) DOIT être
  justifiée dans le plan, avec l'alternative plus simple qui a été écartée.

## Governance

- Cette constitution prévaut sur toute autre pratique du projet. En cas de conflit, elle
  s'applique, ou bien elle est amendée.
- Un amendement DOIT être proposé par écrit, avec sa justification et l'impact sur les
  fonctionnalités existantes, puis approuvé par le mainteneur du projet avant d'être appliqué.
- Versionnement sémantique de la constitution :
  - MAJOR : suppression ou redéfinition incompatible d'un principe ou d'une règle de gouvernance ;
  - MINOR : ajout d'un principe, d'une section, ou extension significative d'une règle ;
  - PATCH : clarification, reformulation, correction sans changement de sens.
- Chaque revue de code et chaque `/speckit-analyze` DOIVENT vérifier la conformité aux principes.
  Une violation DOIT être corrigée ou faire l'objet d'une dérogation justifiée dans le plan.
- La conformité de la constitution est réexaminée à chaque version majeure du produit.

**Version**: 2.0.0 | **Ratified**: 2026-10-30 | **Last Amended**: 2026-10-30
