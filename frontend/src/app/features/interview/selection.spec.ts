import { MAX_QUOTE, passageOf } from './selection';

describe('passageOf (021, sélection d’un passage)', () => {
  let root: HTMLElement;

  beforeEach(() => {
    root = document.createElement('div');
    root.innerHTML = `
      <h2 id="titre">Résumé</h2>
      <li data-anchor="UC-01:1"><span id="a">Ouvrir la facture</span><div data-card><span id="card">Hypothèse H-01</span></div></li>
      <li data-anchor="UC-01:2"><span id="b">Saisir le montant</span></li>`;
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

  it('une sélection sur deux éléments est ramenée au premier, sans le texte des cartes', () => {
    expect(passageOf(root, select('a', 7, 'b', 6))).toEqual({ key: 'UC-01:1', quote: 'la facture' });
  });

  it('une sélection tirée jusque dans la carte de l’élément s’arrête avant elle', () => {
    expect(passageOf(root, select('a', 7, 'card', 5))).toEqual({ key: 'UC-01:1', quote: 'la facture' });
  });

  it('une sélection commencée hors élément vise le premier élément touché', () => {
    expect(passageOf(root, select('titre', 0, 'b', 6))).toEqual({ key: 'UC-01:1', quote: 'Ouvrir la facture' });
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
