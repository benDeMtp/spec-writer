# Guide de validation : agent conversationnel

## Prérequis

Dev mode (CLAUDE.md) ou jar, LLM rejoué (fixtures `converse-plan`, `converse-answer`,
`propose-need`), projet Aurora de test indexé (tickets, pages, ADR, un document du dossier
synchronisé, une spécification générée).

## Scénarios

1. **Accueil** : ouvrir l'outil. La conversation est affichée, le projet courant est visible et le
   champ a le focus. Le menu commence par « Conversation » ; « Recherche » et « Nouveau besoin » n'y
   sont plus.
2. **Question** (US1) : « Comment sont émis les avoirs ? ». Les étapes s'affichent (« Recherche :
   émission des avoirs, N éléments »), puis le texte s'écrit au fil de l'eau. Les sources `[n]`
   s'ouvrent ; une spécification citée s'ouvre sur sa version.
3. **Relance** : « Et qui consomme l'événement ? ». La réponse s'appuie sur l'échange.
4. **Inconnu** : question hors du projet. L'agent dit ne pas savoir.
5. **Arrêt** : « Arrêter » pendant l'écriture. Le texte est gardé et marqué incomplet ;
   « Réessayer » relance.
6. **Incohérence** (US3) : deux sources contradictoires sur le délai de lettrage. L'écart est
   signalé avec les deux sources.
7. **Rédiger** (US2) : « Rédigeons la spécification ». Le besoin et les points s'affichent ; on en
   corrige un et en retire un, puis on valide. Le questionnaire montre les points comme répondus et
   ne repose pas leurs questions. La spécification générée cite un point comme réponse. Les liens
   conversation ↔ spécification sont visibles des deux côtés.
8. **Reprise** (US4) : redémarrer l'outil. La conversation se retrouve dans la liste, se poursuit,
   se renomme et se supprime.
9. **Projet** : changer de projet. Seules ses conversations sont listées.
10. **Documents** : mention @ et document joint dans la conversation, avec leur état. Ils sont repris
    dans la spécification.
11. **Sécurité** : le journal ne contient aucun texte de message. Une source hostile reste une
    donnée.
12. **Accessibilité** : clavier seul (envoi, arrêt, liste, citations). Annonces du lecteur d'écran
    sans relecture du fil. Axe sans violation. Revue `web-design-guidelines` sans écart bloquant.
13. **Évaluation** : `ci/check-eval.sh` vert après l'évaluation réelle autorisée par le mainteneur.

## Essai manuel

Sur le vrai projet Aurora avec le vrai Copilot :

- une question documentaire ;
- une analyse de cohérence ;
- une rédaction de spécification depuis la conversation.
