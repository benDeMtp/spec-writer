Méthode de rédaction de l'énoncé de story :

- L'énoncé dit qui a besoin de quoi et pourquoi : un rôle, une capacité, un bénéfice. Il se lit
  « En tant que <rôle>, je veux <capacité>, afin de <bénéfice> » : écris chaque partie pour qu'elle
  s'insère dans cette phrase, sans répéter les mots de liaison.
- Le rôle est une personne ou un système qui utilise le résultat, nommé comme l'équipe le nomme :
  un métier, un profil, un service. Jamais « l'utilisateur » quand le besoin permet d'être précis,
  jamais l'équipe de développement.
- La capacité décrit ce que le rôle pourra faire une fois la story livrée, du point de vue de ce
  rôle, sans solution technique : pas d'écran, de table ni de technologie quand le besoin n'en
  impose pas.
- Le bénéfice est la raison métier : ce qui s'améliore pour le rôle ou pour l'organisation. Il
  répond à « pourquoi le rôle veut-il cette capacité ? ». Il ne reprend ni le verbe ni l'objet de
  la capacité, ni une étape de son déroulement : « être prévenu quand la salle est prise » n'est
  pas le bénéfice de « réserver une salle », « ne pas se déplacer pour rien » l'est. Relis-le en
  enlevant la capacité : s'il dit encore la même chose, réécris-le.
- Tire chaque partie du besoin, des réponses ou des documents joints. Une partie que rien n'appuie
  est supposée : déclare-la dans "assumed", elle sera montrée comme une hypothèse. Le bénéfice est
  le plus souvent dans ce cas : quand le besoin et les réponses ne disent pas pourquoi, écris le
  bénéfice le plus simple qui en découle et mets BENEFIT dans "assumed". N'y ajoute aucun détail
  absent du besoin (montant, délai, traçabilité, contrainte).
- Une bonne story se livre seule, laisse la solution ouverte à la discussion, apporte une valeur
  visible, se laisse estimer, tient dans une itération et se vérifie par ses scénarios
  d'acceptation.
- Une story trop large mêle plusieurs rôles, plusieurs parcours indépendants ou plusieurs
  variantes de règle qui pourraient être livrées séparément. Signale-la en proposant de 2 à 5
  stories plus petites dans "splitProposal", chacune par un titre court qui nomme sa valeur. Ne
  découpe pas la spécification toi-même : les use cases et les scénarios couvrent tout le besoin.
- Quand une spécification existe déjà, garde son énoncé, sauf si le changement demandé touche le
  rôle, la capacité ou le bénéfice.
