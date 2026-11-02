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

  it('les tableaux sont rendus avec leurs en-têtes', () => {
    const html = render('| A | B |\n| --- | --- |\n| 1 | 2 |\n');
    expect(html).toContain('<th>A</th>');
    expect(html).toContain('<td>2</td>');
    expect(html).toMatch(/^<div class="sw-table" role="region" tabindex="0" aria-label="Tableau"><table>/);
  });
});
