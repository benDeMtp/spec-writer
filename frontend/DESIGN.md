# Direction visuelle de spec-writer (T034, FR-027a)

## Sujet

Un atelier d'écriture de spécifications. L'objet central n'est pas un tableau de bord mais une
phrase bien construite : « En tant que… Étant donné… Quand… Alors… ». L'interface met en scène
cette grammaire, comme la marge d'un manuscrit annoté où chaque rôle de la phrase est repéré.

## Palette (contraste AA vérifié, texte normal ≥ 4,5:1 sur le fond)

| Nom | Clair | Sombre | Rôle |
|---|---|---|---|
| Papier | `#F3F5F2` | `#151A1F` | fond de page, légèrement vert-gris (papier technique) |
| Feuille | `#FFFFFF` | `#1D242B` | surface du document en cours |
| Encre | `#1B2430` | `#E4E8EB` | texte |
| Crayon | `#1F6F5C` | `#6FD3B5` | couleur principale, actions, et mot-clé « Quand » |
| Rôle | `#5B4B8A` | `#B9A9F0` | mot-clé « En tant que » |
| Contexte | `#5C6670` | `#A9B3BC` | mot-clé « Étant donné » et « Et » |
| Résultat | `#8A5A00` | `#E8B860` | mot-clé « Alors », hypothèses |

Rapports calculés (formule WCAG) sur Papier clair : Encre 14,3:1, Crayon 5,5:1, Rôle 6,8:1,
Contexte 5,3:1, Résultat 5,4:1. Sur Papier sombre : Encre 14,2:1, Crayon 9,7:1, Rôle 8,3:1,
Contexte 8,2:1, Résultat 9,6:1. Texte blanc sur Crayon : 6,0:1.

## Typographie

- **Atkinson Hyperlegible Next** (variable) pour toute l'interface : conçue pour la lisibilité,
  elle sert aussi l'accessibilité.
- **Literata** (variable) pour le texte des spécifications et du besoin : une serif de lecture
  longue, qui distingue le document produit de l'outil qui le produit.
- Échelle 1,25 : 0,8 / 1 / 1,25 / 1,563 / 1,953 rem. Corps 1 rem, interligne 1,55 (serif 1,65).
- Polices auto-hébergées (@fontsource) : aucune requête vers un CDN, l'outil reste local.

## Mise en page

```
┌────────┬──────────────────────────────────────────────┐
│ rail   │  En-tête de page (titre, état)               │
│ nav    │ ┌──────────────────────────────────────────┐ │
│        │ │ Feuille : contenu ≤ 72 caractères        │ │
│        │ │                                          │ │
│        │ │   En tant que │ client connecté          │ │
│        │ │  Étant donné  │ un panier de 3 articles  │ │
│        │ │        Quand  │ je valide le paiement    │ │
│        │ │        Alors  │ un message m'informe…    │ │
│        │ └──────────────────────────────────────────┘ │
└────────┴──────────────────────────────────────────────┘
```

Alignement à gauche partout. Une seule feuille par écran : pas de grille de cartes identiques.

## Principes

1. **L'élément mémorable** est la gouttière des scénarios : mots-clés alignés à droite dans une
   colonne de marge, chacun dans la couleur de son rôle, avec un filet vertical de la même couleur.
   Le reste de l'interface reste sobre.
2. La couleur encode le rôle dans la phrase, jamais la décoration ; le texte du mot-clé porte
   toujours l'information (pas de couleur seule).
3. Rayon de 12 px pour la feuille, 8 px pour les champs, 999 px pour les puces : la hiérarchie
   se lit dans les rayons.
4. Mouvement : uniquement en réponse à une action (ouverture, validation). `prefers-reduced-motion`
   le supprime.
5. Focus clavier toujours visible : contour de 3 px couleur Crayon, décalé de 2 px.
