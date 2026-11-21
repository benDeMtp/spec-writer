import { MAX_QUOTE, passageOf } from './selection';

describe('passageOf (021, sélection d’un passage)', () => {
  let root: HTMLElement;

  beforeEach(() => {
    root = document.createElement('div');
    root.innerHTML = `
      <h2 id="titre">Résumé</h2>
      <li data-anchor="UC-01:1"><span id="a">Ouvrir la facture</span><div data-card><span id="card">Hypothèse H-01</span></div></li>
      <li data-anchor="UC-01:2"><span id="b">Saisir le montant</span></li>
      <h3 id="sous-titre">Répartition IA / algorithme</h3>
      <table><tr><td id="cellule">Délai demandé</td></tr></table>`;
    document.body.appendChild(root);
  });

  afterEach(() => root.remove());

  function select(startId: string, start: number, endId: string, end: number): Selection {
    const range = document.createRange();
    range.setStart(root.querySelector(`#${startId}`)!.firstChild!, start);
    range.setEnd(root.querySelector(`#${endId}`)!.firstChild!, end);
    const sel = window.getSelection()!;
    sel.removeAllRanges();
    sel.addRange(range);
    return sel;
  }

  it('cite le passage et l’ancre de son élément', () => {
    expect(passageOf(root, select('a', 0, 'a', 6))).toEqual({ key: 'UC-01:1', quote: 'Ouvrir' });
  });

  it('une sélection sur deux éléments se rattache au premier et cite tout, sans le texte des cartes', () => {
    expect(passageOf(root, select('a', 7, 'b', 6))).toEqual({ key: 'UC-01:1', quote: 'la facture Saisir' });
  });

  it('une sélection tirée jusque dans la carte de l’élément s’arrête avant elle', () => {
    expect(passageOf(root, select('a', 7, 'card', 5))).toEqual({ key: 'UC-01:1', quote: 'la facture' });
  });

  it('tout texte s’annote : un titre ou un tableau hors élément se rattache à l’élément qui le précède', () => {
    expect(passageOf(root, select('sous-titre', 0, 'sous-titre', 11))).toEqual({ key: 'UC-01:2', quote: 'Répartition' });
    expect(passageOf(root, select('cellule', 0, 'cellule', 5))).toEqual({ key: 'UC-01:2', quote: 'Délai' });
  });

  it('avant tout élément, la sélection vise le premier élément qu’elle touche', () => {
    expect(passageOf(root, select('titre', 0, 'b', 6))).toEqual({ key: 'UC-01:1', quote: 'Résumé Ouvrir la facture Saisir' });
  });

  it('rien pour une sélection vide, dans une carte, ou hors du document', () => {
    expect(passageOf(root, select('a', 2, 'a', 2))).toBeNull();
    expect(passageOf(root, select('card', 0, 'card', 5))).toBeNull();
    const outside = document.createElement('p');
    outside.textContent = 'ailleurs';
    document.body.appendChild(outside);
    const range = document.createRange();
    range.selectNodeContents(outside);
    window.getSelection()!.removeAllRanges();
    window.getSelection()!.addRange(range);
    expect(passageOf(root, window.getSelection())).toBeNull();
    outside.remove();
  });

  it('le passage cité est borné', () => {
    root.querySelector('#b')!.textContent = 'x'.repeat(MAX_QUOTE + 50);
    expect(passageOf(root, select('b', 0, 'b', MAX_QUOTE + 50))!.quote).toHaveLength(MAX_QUOTE);
  });
});
