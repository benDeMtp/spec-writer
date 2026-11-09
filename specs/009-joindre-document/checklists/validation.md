# Validation : joindre un document (009)

## Gates (T022, 2026-11-09)

- [X] `./mvnw -B verify -DskipDependencyCheck` (JaCoCo, ArchUnit, SpotBugs, PMD, front) : vert
- [X] `./mvnw -B package -DskipTests` puis `npm --prefix e2e test` : 61 E2E verts, dont `us12-joindre-document`
- [X] `bash ci/check-eval.sh` : vert sans nouvelle évaluation (prompts et `infrastructure/llm` inchangés)

## Essai manuel (T023, par le mainteneur)

- [ ] Joindre un document réel reçu par mail, hors du dossier synchronisé, sur l'écran du besoin
- [ ] Son état s'affiche (transmis, en partie) ; générer : son contenu est exploité
- [ ] La spécification cite « Document joint <nom> », sans extrait ; idem dans l'export
- [ ] Supprimer le fichier du disque, régénérer : même transmission, version réutilisée si rien n'a changé
- [ ] Joindre puis retirer depuis le questionnaire et la spécification
