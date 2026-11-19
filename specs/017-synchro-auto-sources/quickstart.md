# Quickstart : valider la 017

Prérequis : boucle de développement de `CLAUDE.md`, avec `quarkus_restart` après l'ajout de
`quarkus-scheduler` au pom.
- Dev mode sur 47625, front sur 4200.
- Un projet avec Jira (WireMock ou réel), une source de documentation et un modèle d'architecture.

## 1. Vue Jira triée depuis l'index (US1)

1. Synchroniser Jira une fois (Connaissance, « Resynchroniser »).
2. Ouvrir « Tickets Jira ».

Attendu :
- colonnes Ticket, Titre, Statut, Modifié le, Synchro, Spécification ;
- « Dernière synchronisation de Jira : … » en tête.

3. Cliquer sur l'en-tête « Statut », puis « Synchro », puis de nouveau « Synchro ».

Attendu :
- tri sur toute la liste ;
- l'URL porte `tri` et `ordre`, et la page revient à 1 ;
- les conflits viennent en premier, puis en dernier ;
- `aria-sort` est sur la colonne active.

4. Changer de page, quitter la vue, y revenir : le tri est conservé.

5. Contrôle SC-002 : avec WireMock, `POST /__admin/requests/count` sur `/rest/api/2/search.*`
   reste inchangé pendant les tris et les changements de page (E2E `us10` J2).

## 2. Synchronisation automatique par source (US2)

1. Réglages du projet, section des sources : donner 15 minutes à Jira.

Attendu :
- « Prochaine synchronisation vers … » ;
- les autres sources restent « Désactivée ».

2. Pour ne pas attendre, antidater `source_sync.last_run_at` de 20 minutes dans la base de dev
   (`quarkus-agroal_executeSQL`). Déclencher ensuite la tâche `source-sync` par l'outil Dev MCP du
   scheduler (`quarkus_searchTools` « scheduler »), ou laisser passer le prochain tick de 30 s.

Attendu, en moins d'une minute :
- une indexation INCREMENTAL de la seule source `jira` (journal) ;
- aucune requête vers la documentation (SC-004) ;
- la source affiche sa date, puis « n nouveaux, n modifiés, n retirés » ou « Aucun changement ».

3. Lancer « Tout réindexer » pendant qu'une échéance arrive : l'échéance attend la fin et ne part
   qu'une fois (journal).

4. Couper WireMock Jira, puis provoquer une échéance.

Attendu :
- « Échec », avec la date de la dernière réussite ;
- une entrée au journal ;
- pas de relance avant le délai suivant.

5. Redémarrer l'outil avec une échéance dépassée : elle part dans les 10 s qui suivent le démarrage.

## 3. Notifications du navigateur (US3)

1. Configuration, carte « Notifications » : activer. Le navigateur demande la permission ;
   l'accorder.
2. Modifier un ticket dans WireMock (ou une page du dossier de documentation), puis
   « Resynchroniser ».

Attendu, en moins de 10 s : une notification « Jira « SW » mise à jour », « Projet : 1 modifié ».

3. Ouvrir un deuxième onglet et recommencer : une seule notification. Fermer le premier onglet et
   recommencer : le second prend le relais.
4. Cliquer sur la notification : l'onglet passe au premier plan sur « Connaissance » du bon projet.
5. Resynchroniser sans changement : aucune notification. Désactiver : plus aucune notification.
6. Refuser la permission (réglages du site) puis réactiver : message expliquant comment l'accorder,
   réglage non enregistré.

Ces parcours sont automatisés : `e2e/tests/us10-jira-paging.spec.ts` (J1, J2) et
`e2e/tests/us17-synchro.spec.ts` (S1 à S3, N1 à N3).

## 4. Gates

```bash
rm -f backend/target/jacoco.exec
./mvnw -B verify -DskipDependencyCheck
npm --prefix e2e test -- us17-synchro
```

`ci/check-eval.sh` ne doit pas changer d'empreinte : aucun prompt ni code LLM touché.
