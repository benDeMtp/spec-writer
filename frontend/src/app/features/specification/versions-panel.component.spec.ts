import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { VersionsPanelComponent } from './versions-panel.component';

describe('VersionsPanelComponent (sélecteur de version)', () => {
  function open(current: number, versions: unknown[]) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    const fixture = TestBed.createComponent(VersionsPanelComponent);
    fixture.componentRef.setInput('sessionId', 's1');
    fixture.componentRef.setInput('current', current);
    fixture.componentRef.setInput('versions', versions);
    fixture.detectChanges();
    return fixture;
  }

  const V = [
    { number: 1, origin: 'GENERATED', createdAt: '2026-10-30T09:00:00Z', pushedToJira: false },
    { number: 2, origin: 'DISCUSSION', createdAt: '2026-11-10T10:00:00Z', pushedToJira: false, conversationId: 'c1' },
  ];

  it('un seul bouton « Version 2 » ; le menu liste les versions, la version affichée marquée', async () => {
    const fixture = open(2, V);
    const el: HTMLElement = fixture.nativeElement;
    const trigger = el.querySelector<HTMLButtonElement>('button.version-menu')!;
    expect(trigger.textContent?.replace(/\s+/g, ' ').trim()).toBe('Version 2 ▾');
    expect(trigger.getAttribute('aria-label')).toBe('Version 2, changer de version');
    trigger.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const items = Array.from(document.querySelectorAll<HTMLAnchorElement>('.version-items a[mat-menu-item]'));
    expect(items.map((a) => a.getAttribute('href'))).toEqual(['/sessions/s1/versions/1', '/sessions/s1/versions/2']);
    expect(items[1].getAttribute('aria-current')).toBe('page');
    expect(items[0].textContent).toContain('Générée');
    expect(items[1].textContent).toContain('Issue de la discussion');
    expect(items[1].textContent).toContain('10 nov. 2026');
  });

  it('la version affichée issue d’une discussion mène à sa conversation', () => {
    const el: HTMLElement = open(2, V).nativeElement;
    const link = el.querySelector('a.origin')!;
    expect(link.getAttribute('href')).toBe('/conversations/c1');
    expect(link.textContent?.trim()).toBe('Discussion d’origine');
  });

  it('sans conversation (supprimée, ou version générée), pas de lien', () => {
    expect(open(1, V).nativeElement.querySelector('a.origin')).toBeNull();
  });
});
