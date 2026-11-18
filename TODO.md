# À faire

## Sécurité

- [ ] **GHSA-fc5q-39xc-vjpf : picomatch ReDoS** (avis haut, ignoré le 2026-11-18).
  - Paquet : picomatch `<=4.0.7`, tiré par l'outillage de build du front (`@angular/build`, `vite`,
    `@angular/cli`, `vitest`) ; rien de livré dans le jar (`npm audit --omit=dev` : 0).
  - Aucune version corrigée publiée au 2026-11-18 : l'avis est ignoré par `ci/npm-audit.sh`
    (`ci/npm-audit-ignore.txt`).
  - À faire dès qu'une version corrigée sort : mettre à jour les dépendances du front
    (`npm --prefix frontend update` ou montée d'Angular), retirer la ligne de
    `ci/npm-audit-ignore.txt`, relancer `ci/npm-audit.sh`. Le script signale l'avis devenu inutile.
  - Suivi : https://github.com/advisories/GHSA-fc5q-39xc-vjpf
