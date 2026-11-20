# Quickstart : valider l'interview d'une spécification

Prérequis : dev mode (`quarkus_start`, http://127.0.0.1:47625, LLM rejoué depuis
`e2e/fixtures/llm`) ou jar E2E ; un projet avec une source Confluence indexée (fixtures WireMock
des E2E) ; une spécification générée avec au moins deux hypothèses rattachées, dont une sur un use
case qui cite une page Confluence. Les rejeux de 021 (`e2e/fixtures/llm`, marqueur
`IT-INTERVIEW`) produisent ces cas.

## V1. Séance d'une spécification (US1)

1. Page de la spécification : « Faire le point (n) » affiche le bon nombre.
2. Démarrer : menu masqué, grand texte, la spécification entière en document.
3. « Prochain point à traiter » : la carte de `H-…` sous son étape prend le focus. « Confirmer ».
4. Sur une étape sans point, « Noter », « Commenter » : « validation du manager au-delà de 500 € ».
5. « Noter », « Question… » : « Qui valide en l'absence du manager ? », responsable « Paul D. ».
6. Sélectionner un passage d'une autre étape, « Remplacer » par un autre texte : la remarque cite
   le passage, surligné dans le document.
7. Fermer l'onglet, rouvrir l'outil : « Reprendre la séance » ramène au dernier point consulté,
   quatre points présents.
8. Pendant la séance, déclencher une synchronisation (« Synchroniser » d'une source depuis un
   autre onglet) : aucune notification sur l'écran de séance.

## V2. Compte rendu (US2)

1. « Terminer » : le récapitulatif s'affiche.
2. « Copier le compte rendu », coller dans un mail (Outlook ou Gmail) et dans Teams : titres et
   listes gardés ; les trois points y sont mot pour mot, avec « Paul D. ».
3. Navigateur qui refuse le presse-papiers (page ouverte sans focus) : le texte s'affiche
   sélectionné.

## V3. Révision (US3)

1. « Proposer la nouvelle version » : la discussion de la spécification montre le message de
   séance, la proposition se rédige, le diff s'affiche.
2. Appliquer : `H-…` confirmée a disparu ; l'étape est modifiée ; « Qui valide… » est un point
   ouvert `Q-01`, responsable Paul D. ; la source de la phrase modifiée est « Séance du … ».
3. Copilot non autorisé (retirer le consentement) : la séance et le compte rendu marchent, la
   révision dit pourquoi elle est indisponible.

## V4. Rattachement (US4)

1. Générer une spécification depuis un besoin du golden set : chaque hypothèse indique son
   élément sur la page.
2. Ouvrir une spécification créée avant 021 : ses hypothèses sont en fin de parcours, dans « Non
   rattachés ».
3. Export Markdown : rubrique « Points ouverts » avec responsable.

## V5. Préparation (US5)

1. Page « Spécifications » : compteurs par spécification ; filtre « Avec des points à traiter ».
2. Cocher deux spécifications, « Préparer une séance (2) », écarter un point, inverser l'ordre,
   enregistrer.
3. Quitter et relancer l'outil : la séance préparée est en tête ; « Démarrer » : les deux
   spécifications se suivent, sans le point écarté.
4. « Terminer » : compte rendu en deux parties, une révision par spécification.
5. Démarrer une autre séance sur l'une des deux pendant la première : refus qui nomme la séance
   en cours.

## V6. Sources à mettre à jour (US6)

1. Après la révision de V3 : la page Confluence citée par le use case modifié est « à mettre à
   jour », avec le texte de la décision ; elle figure dans le compte rendu.
2. Modifier la page dans le WireMock (nouvelle version), resynchroniser la source : « Source
   modifiée, à vérifier ».
3. « Marquer comme reporté » : l'élément disparaît de la page.
4. Journal des requêtes WireMock : aucun appel d'écriture vers Confluence ni Jira pendant tout
   V1 à V6.

## Gates

`./mvnw -B verify -DskipDependencyCheck`, E2E sur le jar (`us21-interview` avec axe), évaluation
(`ci/check-eval.sh` ; empreinte nouvelle : évaluation `live` relancée par le mainteneur, critères
`anchoredRate` ≥ 0,90 et `decidedRemoved` = 1,0).

## Résultats (20/11/2026, jar construit par `verify`)

| Scénario | Résultat | Preuve |
|---|---|---|
| V1 séance d'une spécification | OK | `us21-interview.spec.ts` V1 |
| V2 compte rendu copié | OK | `us21-interview.spec.ts` V2 |
| V3 révision d'après la séance | OK sur les rejeux (`47c-revise-seance.json`) | `us21-interview.spec.ts` V3 |
| V4 points rattachés à leur élément | OK sur les rejeux (`00b-`, `00c-ancres-`) | `us21-interview.spec.ts` V4 |
| V5 préparation de plusieurs spécifications | OK ; la relance de l'instance est remplacée par un rechargement | `us21-interview.spec.ts` V5 |
| V6 sources à mettre à jour | OK, aucune écriture dans Confluence ni Jira | `us16-confluence.spec.ts` F8 |

- Bundle initial : 499,62 kB (129,50 kB transférés), sous le seuil de 500 kB ; la séance, sa
  préparation et son récapitulatif sont chargés à la demande. La marge est quasi nulle.
- Évaluation : à relancer sur le vrai Copilot par le mainteneur (T094) ; `ci/check-eval.sh` échoue
  tant que `last-run.json` n'a pas la nouvelle empreinte (consignes de révision et de génération,
  schéma de sortie, golden set).
