import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { SessionSummary } from '../../core/sessions/sessions.model';
import { SessionsPage } from './sessions.page';

const LIST: SessionSummary[] = [
  { id: 's1', name: 'Réservation de salles', state: 'GENERATED', updatedAt: '2026-10-30T10:00:00Z', latestVersion: 2, jiraKey: null, readable: true },
  { id: 's2', name: 'Paiement', state: 'QUESTIONING', updatedAt: '2026-10-29T10:00:00Z', latestVersion: null, jiraKey: null, readable: true },
  { id: 's3', name: 'Ancienne', state: 'QUESTIONING', updatedAt: '2026-10-28T10:00:00Z', latestVersion: null, jiraKey: null, readable: false },
];

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

function button(root: Element | Document, label: string): HTMLElement | undefined {
  return Array.from(root.querySelectorAll<HTMLElement>('button, a')).find((b) => b.textContent?.trim() === label);
}

describe('SessionsPage', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<SessionsPage>;
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SessionsPage);
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/sessions').flush(LIST);
    await settle(fixture);
  });

  it('liste les sessions avec leur état et propose de les reprendre', () => {
    const items = el.querySelectorAll('li');
    expect(items).toHaveLength(3);
    expect(items[0].textContent).toContain('Spécification générée, version 2');
    expect(items[0].querySelector('a.name')?.getAttribute('href')).toBe('/sessions/s1');
    expect(button(items[1], 'Reprendre')?.getAttribute('href')).toBe('/sessions/s2');
  });

  it('signale une session illisible, sans lien, mais supprimable', () => {
    const damaged = el.querySelectorAll('li')[2];
    expect(damaged.textContent).toContain('Illisible');
    expect(damaged.querySelector('a')).toBeNull();
    expect(button(damaged, 'Reprendre')).toBeUndefined();
    expect(button(damaged, 'Supprimer')).toBeTruthy();
  });

  it('renomme en ligne', async () => {
    button(el.querySelectorAll('li')[1], 'Renommer')!.click();
    await settle(fixture);
    const input = el.querySelector<HTMLInputElement>('input[name="name"]')!;
    expect(input.value).toBe('Paiement');
    input.value = 'Paiement en ligne';
    input.dispatchEvent(new Event('input'));
    await settle(fixture);
    button(el, 'Enregistrer')!.click();
    const patch = ctrl.expectOne({ method: 'PATCH', url: '/api/sessions/s2' });
    expect(patch.request.body).toEqual({ name: 'Paiement en ligne' });
    patch.flush({ ...LIST[1], name: 'Paiement en ligne' });
    await settle(fixture);
    ctrl.expectOne('/api/sessions').flush([LIST[0], { ...LIST[1], name: 'Paiement en ligne' }]);
    await settle(fixture);
    expect(el.querySelector('.sheet .announce')?.textContent).toContain('Spécification renommée en « Paiement en ligne »');
  });

  it('supprime après confirmation dans un dialogue', async () => {
    button(el.querySelectorAll('li')[0], 'Supprimer')!.click();
    await settle(fixture);
    ctrl.expectOne('/api/sessions/s1/discussions/count').flush({ count: 0 });
    await settle(fixture);
    const dialog = document.querySelector('sw-confirm-dialog')!;
    expect(dialog.textContent).toContain('« Réservation de salles » et toutes ses versions seront supprimées définitivement');
    button(dialog, 'Annuler')!.click();
    await settle(fixture);
    ctrl.expectNone({ method: 'DELETE' });

    button(el.querySelectorAll('li')[0], 'Supprimer')!.click();
    await settle(fixture);
    ctrl.expectOne('/api/sessions/s1/discussions/count').flush({ count: 0 });
    await settle(fixture);
    button(document.querySelector('sw-confirm-dialog')!, 'Supprimer définitivement')!.click();
    await settle(fixture);
    ctrl.expectOne({ method: 'DELETE', url: '/api/sessions/s1?discussions=delete' }).flush(null);
    await settle(fixture);
    ctrl.expectOne('/api/sessions').flush(LIST.slice(1));
    await settle(fixture);
    expect(el.querySelectorAll('li')).toHaveLength(2);
  });

  it('« Spécifications » : nouvelle spécification et recherche dans le même écran', async () => {
    expect(el.querySelector('h1')?.textContent).toContain('Spécifications');
    expect(el.querySelector('a[href="/nouveau"]')?.textContent).toContain('Nouvelle spécification');
    expect(el.querySelector('form[role="search"]')).toBeTruthy();
    expect(el.querySelector('#search-title')?.tagName).toBe('H2');
    expect(el.querySelector('#list-title')?.textContent).toContain('Vos spécifications');
  });
});
