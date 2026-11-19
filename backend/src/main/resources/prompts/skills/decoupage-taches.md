Méthode de découpage d'une spécification en tâches de suivi :

- Une tâche sert à suivre l'avancement, pas à planifier le travail heure par heure. Vise peu de
  tâches, chacune assez grosse pour avoir un sens pour l'équipe dans Jira.
- Chaque tâche a un résultat qu'un relecteur peut constater sans lire le code : un comportement
  observable, un appel qui répond, un écran qui affiche, un document publié. « Le code est écrit »
  ou « l'analyse est faite » ne sont pas des résultats.
- Pas de tâche d'analyse pure, ni de tâche « tests » séparée : les tests font partie de la tâche
  qu'ils vérifient.
- Découpe d'abord selon ce qui change pour l'utilisateur : une étape du parcours, une variante de
  règle métier, un type de donnée ou un cas d'erreur traités à part. Quand des modules sont donnés,
  regroupe le travail par module touché, un module impacté au moins une fois.
- Une tâche qui dépend d'une autre vient après elle et la cite comme préalable. Garde le moins de
  préalables possible : deux tâches indépendantes peuvent avancer en même temps.
- Ordonne les tâches dans l'ordre où l'équipe les ferait : les fondations d'abord, puis ce qui
  s'appuie dessus.
- Un titre court commence par un verbe d'action et nomme ce qui change ; le résultat tient en une
  phrase.
