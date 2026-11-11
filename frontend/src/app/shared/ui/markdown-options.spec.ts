import { marked } from 'marked';
import { markedOptions } from './markdown-options';

describe('markedOptions', () => {
  // comme ngx-markdown : les options sont passées à chaque rendu
  const render = (md: string) => marked.parse(md, markedOptions()) as string;

  it('ouvre les liens http, https et mailto dans un nouvel onglet, sans accès à la page', () => {
    expect(render('[doc](https://doc.example/a_b "Aide")')).toContain(
      '<a href="https://doc.example/a_b" title="Aide" target="_blank" rel="noopener noreferrer">doc</a>',
    );
    expect(render('<http://ex.example>')).toContain('target="_blank" rel="noopener noreferrer"');
    expect(render('[écrire](mailto:a@ex.example)')).toContain('href="mailto:a@ex.example"');
  });

  it('les autres protocoles restent du texte', () => {
    const html = render('[piège](javascript:alert(1)) et [local](file:///etc/passwd)');
    expect(html).not.toContain('<a');
    expect(html).toContain('piège');
  });

  it('seuls les liens de l’outil posés par la conversation restent dans l’onglet', () => {
    const id = '0b6a0f1e-2a33-4c44-9d55-66e77f88a999';
    expect(render(`[[2]](/sessions/${id}/versions/3 "Saisir un avoir")`)).toContain(
      `<a href="/sessions/${id}/versions/3" title="Saisir un avoir">[2]</a>`,
    );
    expect(render(`[[1]](#source-${id}-1)`)).toContain(`<a href="#source-${id}-1">[1]</a>`);
    expect(render(`[[2]](#perimeter-${id}-1-0)`)).toContain(`<a href="#perimeter-${id}-1-0">[2]</a>`);
    expect(render(`[[3]](#perimeter-x)`)).not.toContain('<a');
    // 011 : une section de la spécification discutée
    expect(render(`[[4]](/sessions/${id}/versions/3#UC-01 "UC-01")`)).toContain(
      `<a href="/sessions/${id}/versions/3#UC-01" title="UC-01">[4]</a>`,
    );
    expect(render(`[x](/sessions/${id}/versions/3#"onclick)`)).not.toContain('<a');
    const html = render('[ailleurs](/configuration) et [ancre](#haut) et [proche](//evil.example/x)');
    expect(html).not.toContain('<a');
  });

  it('les tableaux sont rendus avec leurs en-têtes', () => {
    const html = render('| A | B |\n| --- | --- |\n| 1 | 2 |\n');
    expect(html).toContain('<th>A</th>');
    expect(html).toContain('<td>2</td>');
    expect(html).toMatch(/^<div class="sw-table" role="region" tabindex="0" aria-label="Tableau"><table>/);
  });
});
