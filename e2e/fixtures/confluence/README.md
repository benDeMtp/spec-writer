# Confluence simulé (016)

Arbre de démonstration servi par le WireMock des E2E (même instance que Jira, port 47698), sous le
préfixe `/confluence`. Les mappings sont générés dans `../jira/mappings/confluence.json` par :

```bash
python3 e2e/fixtures/confluence/generate.py e2e/fixtures/jira/mappings/confluence.json
```

- Serveur : `http://127.0.0.1:47698/confluence` ; jeton quelconque (`Authorization: Bearer …`),
  sans jeton : `401`.
- Racine : `pageId=100` « Remboursements », espace `REMB` (aussi par
  `/display/REMB/Remboursements`).
- 12 pages sur trois niveaux (100 ; 101 à 104 ; 105 à 111), descendance paginée par 5
  (`content/search?cql=ancestor=100…`, `start` = 0, 5, 10, `totalSize` = 11).
- Page `111` : listée mais restreinte (`403` sur son contenu), comptée comme page en erreur.
- Page `900` : existe, hors de l'arbre ; jamais indexée.
- Page `103` « Notes de frais » : plafond de 1 000 euros par mois (interprétation), contredit par
  la demande SharePoint de `../documents` (500 euros, référence) pour US3.
- Page `105` « Frais kilométriques » : page de troisième niveau, terme « barème kilométrique ».
- Page `110` : section adressée à une IA (test d'injection).

Le parcours `e2e/tests/us16-confluence.spec.ts` déclare ce serveur dans Configuration, ajoute la
racine `100`, vérifie que la page `900` n'est jamais demandée et qu'une resynchronisation sans
changement ne relit aucun contenu. La demande SharePoint (500 euros) est écrite par le test dans un
dossier temporaire, déclaré comme dossier de documents.
