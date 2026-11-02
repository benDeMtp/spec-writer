import { TestBed } from '@angular/core/testing';
import { UnifiedDiffComponent, parseUnifiedDiff } from './unified-diff.component';

const DIFF = '--- v1\n+++ proposition\n@@ -1,3 +1,3 @@\n # Titre\n-ancienne ligne\n+nouvelle ligne\n+autre ajout\n';

describe('UnifiedDiffComponent', () => {
  it('lit un diff unifié sans ses en-têtes', () => {
    expect(parseUnifiedDiff(DIFF)).toEqual([
      { kind: 'hunk', text: 'À partir de la ligne 1' },
      { kind: 'context', text: '# Titre' },
      { kind: 'removed', text: 'ancienne ligne' },
      { kind: 'added', text: 'nouvelle ligne' },
      { kind: 'added', text: 'autre ajout' },
    ]);
  });

  it('dit les ajouts et suppressions en toutes lettres, dans une zone atteignable au clavier', () => {
    const fixture = TestBed.createComponent(UnifiedDiffComponent);
    fixture.componentRef.setInput('diff', DIFF);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.totals')?.textContent).toBe('2 lignes ajoutées, 1 ligne supprimée.');
    const region = el.querySelector('[role="region"]')!;
    expect(region.getAttribute('tabindex')).toBe('0');
    expect(region.getAttribute('aria-label')).toBe('Différences avec la version actuelle');
    expect(el.querySelector('li.removed')?.textContent).toContain('Supprimé : ');
    expect(el.querySelector('li.added')?.textContent).toContain('Ajouté : ');
  });

  it('sans différence, le dit', () => {
    const fixture = TestBed.createComponent(UnifiedDiffComponent);
    fixture.componentRef.setInput('diff', '');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Aucune différence avec la version actuelle.');
    expect(fixture.nativeElement.querySelector('[role="region"]')).toBeNull();
  });
});
