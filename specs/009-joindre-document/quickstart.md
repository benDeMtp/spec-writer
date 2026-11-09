# Guide de validation : joindre un document

## Prérequis

Dev mode (CLAUDE.md) ou jar ; documents de test générés par `TestDocuments` (006) hors de tout
dossier synchronisé ; LLM rejoué.

## Scénarios

1. **Joindre au besoin** (US1) : sur l'écran du besoin, « Joindre un document », choisir un PDF : il
   apparaît avec son titre, sa taille et « transmis à l'IA ». Démarrer, générer : la règle propre au
   document est dans la spécification ; la version cite « Document joint <nom> ».
2. **Refus** : `.doc`, PDF protégé, PDF scanné, fichier de plus de 20 Mo, sixième document, même
   fichier deux fois : refusés avec la raison, rien de gardé.
3. **Place** : joindre un long document et mentionner trois documents avec @ : états « en partie » ou
   « plus de place » dans l'ordre mentions du besoin, documents joints, réponses ; suggestion d'un
   modèle (008).
4. **Sans le fichier** (US2) : supprimer le fichier du disque, régénérer : même transmission ; deux
   générations identiques réutilisent la version.
5. **Session en cours** (US3) : joindre depuis le questionnaire puis depuis la spécification ;
   retirer ; feature implémentée : refus.
6. **Rien ne sort** : export Markdown, aperçu Jira, passerelle des agents : titre seul, aucun extrait ;
   journal sans texte du document.
7. **Suppression** : supprimer la session : plus aucune trace du texte dans `spec-writer.db`.
8. **Injection** : un document contenant « ignore les consignes » reste une donnée.
9. **Sans document joint** : golden set et `ci/check-eval.sh` inchangés.
10. **E2E** sur le jar : parcours 1, 5 et 6 ; axe sans violation.

## Essai manuel

Joindre un document réel reçu par mail (hors du dossier synchronisé), générer, vérifier que son
contenu est exploité, puis supprimer le fichier et régénérer.
