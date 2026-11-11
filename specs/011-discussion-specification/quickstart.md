# Guide de validation : discuter d'une spécification avec l'agent

## Prérequis

- Dev mode (CLAUDE.md) ou jar.
- LLM rejoué : fixtures `converse-plan`, `converse-answer` et `revise-specification`, dont une
  réponse `changed: false`.
- Projet Aurora de test indexé, avec un ADR qui contredit un délai de la spécification.
- Une spécification générée en version 3, avec un document joint ; une seconde spécification
  implémentée.

## Scénarios

1. **Ouvrir** (US1) : sur la spécification, cliquer « Discuter avec l'IA ».
   - Le panneau s'ouvre à droite, avec « Contexte : <nom>, version 3 ».
   - Le champ a le focus.
   - Au-dessous de 64 rem, le panneau passe sous le document.
2. **Question** : « Quels scénarios couvrent l'annulation ? ».
   - Les étapes, le titre d'activité et le chronomètre s'affichent.
   - La réponse cite la spécification ; la citation fait défiler le document jusqu'à la section.
3. **Incohérence** : « La spécification est-elle cohérente avec les ADR ? ». L'écart est signalé
   avec la section de la spécification et l'ADR.
4. **Documents** : mention @ d'une page et document joint de la spécification. Leur état s'affiche,
   et ils sont cités.
5. **Version ancienne** : afficher la version 2. Le panneau dit que la discussion porte sur la
   version 3.
6. **Proposer** (US2) : après « Ajoutons une erreur quand la salle est déjà prise » et la
   confirmation, cliquer « Proposer une nouvelle version ».
   - Le chronomètre et la réflexion s'affichent pendant la rédaction.
   - Le diff n'ajoute que ce cas.
   - « Appliquer » crée la version 4, « Issue de la discussion », avec un lien vers la
     conversation.
   - Le fil montre « Contexte : version 4 ».
7. **Rien à changer** : dans une discussion qui n'établit rien, demander une proposition. L'agent
   dit qu'aucun changement n'a été établi, et aucune version n'est créée.
8. **Abandonner** : proposer, puis abandonner. Aucune version n'est créée, et la discussion
   continue.
9. **Reprise** (US3) : redémarrer l'outil, puis rouvrir la spécification. La discussion se
   retrouve.
10. **Nouvelle discussion** : le panneau repart vide. Dans « Conversation », l'ancienne et la
    nouvelle portent « Spécification : <nom> ».
11. **Verrou** : sur la spécification implémentée, « Discuter avec l'IA » est indisponible.
    « Créer une évolution » est indiqué. Ses discussions passées restent lisibles dans la liste.
12. **Suppression** : supprimer une spécification qui a deux discussions.
    - La case « Supprimer aussi ses discussions » est cochée par défaut.
    - Décochée, les deux conversations restent, avec « spécification supprimée », et se
      poursuivent.
13. **Jira** (US4) : sur un ticket, « Discuter avec l'IA » importe le ticket et ouvre la
    spécification, avec le panneau ouvert. Un second clic ouvre la même spécification.
14. **Conflit Jira** : la fusion par l'IA s'affiche en relecture directe, sans discussion.
15. **Accessibilité** : ouvrir, discuter, proposer et appliquer au clavier seul. axe ne signale
    aucune violation.

## Commandes

```bash
export JAVA_HOME=~/.sdkman/candidates/java/25.0.2.r25-mandrel PATH=$JAVA_HOME/bin:$PATH
rm -f backend/target/jacoco.exec
env -u JAVA_TOOL_OPTIONS ./mvnw -B verify -DskipDependencyCheck
env -u JAVA_TOOL_OPTIONS ./mvnw -B package -DskipTests && npm --prefix e2e test   # us14-discussion
# évaluation réelle, avec l'accord du mainteneur
env -u JAVA_TOOL_OPTIONS ./mvnw -pl backend verify -Peval && bash ci/check-eval.sh
```
