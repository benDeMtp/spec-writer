import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { SearchItem, SearchResult } from '../../core/search/search.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { itemLink } from './results-table.component';
import { SearchPage } from './search.page';

const ITEMS: SearchItem[] = [
  { id: 's1', title: 'Réservation', provenance: 'BOTH', jiraKey: 'PROJ-2', sessionId: 's1', latestVersion: 2, updatedAt: '2026-10-30T10:00:00Z', excerpt: '…salle…' },
  { id: 'PROJ-1', title: 'Ticket seul', provenance: 'JIRA', jiraKey: 'PROJ-1', sessionId: null, latestVersion: null, updatedAt: '2026-10-30T09:00:00Z', excerpt: null },
  { id: 's3-uuid-long', title: 'Facturation', provenance: 'SPEC_WRITER', jiraKey: null, sessionId: 's3-uuid-long', latestVersion: null, updatedAt: '2026-10-29T09:00:00Z', excerpt: null },
];

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

describe('SearchPage', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<SearchPage>;
  let el: HTMLElement;
  const ensure = vi.fn(async () => true);

  beforeEach(() => {
    ensure.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ConsentGate, useValue: { ensure } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SearchPage);
    el = fixture.nativeElement;
  });

  function page(items: SearchItem[], extra: Partial<SearchResult> = {}): SearchResult {
    return {
      items,
      position: items.length ? `1 à ${items.length} sur ${items.length}` : 'Aucun résultat',
      approximateTotal: false,
      nextCursor: null,
      jiraStatus: 'OK',
      jiraError: null,
      ...extra,
    };
  }

  async function searchFor(q: string, result: SearchResult): Promise<void> {
    fixture.componentRef.setInput('q', q);
    fixture.detectChanges();
    const req = ctrl.expectOne((r) => r.url === '/api/search');
    expect(req.request.params.get('q')).toBe(q);
    expect(req.request.params.has('cursor')).toBe(false);
    req.flush(result);
    await settle(fixture);
  }

  function button(text: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes(text))!;
  }

  it('affiche une liste unique avec identifiant, titre, provenance en texte et date', async () => {
    await searchFor('salle', page(ITEMS));
    const headers = Array.from(el.querySelectorAll('th')).map((th) => th.textContent?.trim());
    expect(headers).toEqual(['Identifiant', 'Titre', 'Provenance', 'Modifié le']);
    const rows = el.querySelectorAll('tr.mat-mdc-row');
    expect(rows).toHaveLength(3);
    expect(rows[0].textContent).toContain('PROJ-2');
    expect(rows[0].textContent).toContain('les deux');
    expect(rows[1].textContent).toContain('Jira');
    expect(rows[2].textContent).toContain('spec-writer');
    expect(rows[2].textContent).toContain('s3-uuid-');
    expect(rows[0].querySelector('a')?.getAttribute('href')).toBe('/sessions/s1/versions/2');
    expect(rows[1].querySelector('a')?.getAttribute('href')).toBe('/jira/PROJ-1');
    expect(el.querySelector('caption')?.textContent).toContain('Résultats pour « salle »');
    expect(el.querySelector('[role="status"]')?.textContent).toBe('3 résultats.');
    expect(el.querySelector('.notice')).toBeNull();
  });

  it('Jira en panne : résultats locaux et bandeau avec « Réessayer »', async () => {
    await searchFor('salle', page([ITEMS[2]], { jiraStatus: 'UNAVAILABLE' }));
    expect(el.querySelector('.notice')?.textContent).toContain("Jira n'a pas répondu");
    expect(el.querySelectorAll('tr.mat-mdc-row')).toHaveLength(1);
    button('Réessayer').click();
    ctrl.expectOne((r) => r.url === '/api/search').flush(page(ITEMS));
    await settle(fixture);
    expect(el.querySelectorAll('tr.mat-mdc-row')).toHaveLength(3);
  });

  it('sans accord pour Jira, le demande puis relance la recherche', async () => {
    await searchFor('salle', page([], { jiraStatus: 'CONSENT_REQUIRED' }));
    expect(el.textContent).toContain('Aucun résultat pour « salle »');
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Autoriser'))!.click();
    await settle(fixture);
    expect(ensure).toHaveBeenCalledWith('JIRA');
    ctrl.expectOne((r) => r.url === '/api/search').flush(page(ITEMS));
    await settle(fixture);
    expect(el.querySelector('.notice')).toBeNull();
  });

  it('Jira non configuré : lien vers la configuration', async () => {
    await searchFor('salle', page([ITEMS[2]], { jiraStatus: 'NOT_CONFIGURED' }));
    expect(el.querySelector('.notice a')?.getAttribute('href')).toBe('/configuration');
  });

  it('pages suivante et précédente par curseur, position annoncée', async () => {
    await searchFor('salle', page(ITEMS, { position: '1 à 25 sur 70', nextCursor: 'c1' }));
    expect(el.querySelector('nav[aria-label="Pages de résultats"]')?.textContent).toContain('1 à 25 sur 70');
    expect(button('précédente').disabled).toBe(true);

    button('suivante').click();
    let req = ctrl.expectOne((r) => r.url === '/api/search');
    expect(req.request.params.get('cursor')).toBe('c1');
    req.flush(page([ITEMS[1]], { position: '26 à 50 sur 70', nextCursor: 'c2' }));
    await settle(fixture);
    expect(el.querySelector('sw-pager .position')?.textContent?.trim()).toBe('26 à 50 sur 70');

    button('précédente').click();
    req = ctrl.expectOne((r) => r.url === '/api/search');
    expect(req.request.params.has('cursor')).toBe(false);
    req.flush(page(ITEMS, { position: '1 à 25 sur 70', nextCursor: 'c1' }));
    await settle(fixture);
    expect(el.querySelectorAll('tr.mat-mdc-row')).toHaveLength(3);
    expect(button('précédente').disabled).toBe(true);
  });

  it('Jira coupé sur une page suivante : la page locale et le message de Jira', async () => {
    await searchFor('salle', page(ITEMS, { position: '1 à 25 sur 70', nextCursor: 'c1' }));
    button('suivante').click();
    ctrl.expectOne((r) => r.url === '/api/search').flush(
      page([ITEMS[2]], {
        position: '26 à 26 sur environ 70',
        approximateTotal: true,
        nextCursor: 'c2',
        jiraStatus: 'UNAVAILABLE',
        jiraError: "Jira n'a pas répondu : seuls les résultats spec-writer sont affichés.",
      }),
    );
    await settle(fixture);
    expect(el.querySelector('.notice')?.textContent).toContain("Jira n'a pas répondu");
    button('Réessayer').click();
    expect(ctrl.expectOne((r) => r.url === '/api/search').request.params.get('cursor')).toBe('c1');
  });

  it('une page qui ne se charge pas laisse la page affichée', async () => {
    await searchFor('salle', page(ITEMS, { position: '1 à 25 sur 70', nextCursor: 'c1' }));
    button('suivante').click();
    ctrl.expectOne((r) => r.url === '/api/search').flush({ code: 'VALIDATION_FAILED' }, { status: 422, statusText: 'KO' });
    await settle(fixture);
    expect(el.querySelectorAll('tr.mat-mdc-row')).toHaveLength(3);
    expect(el.querySelector('[role="status"]')?.textContent).toContain("n'a pas pu être chargée");
    expect(button('précédente').disabled).toBe(true);
  });

  it('refuse une recherche trop courte sans appeler l’API', async () => {
    fixture.detectChanges();
    const input = el.querySelector<HTMLInputElement>('input[name="q"]')!;
    input.value = 'a';
    input.dispatchEvent(new Event('input'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await settle(fixture);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('au moins 2 caractères');
    ctrl.expectNone('/api/search');
  });

  it('lien d’un résultat selon sa nature', () => {
    expect(itemLink(ITEMS[0])).toEqual(['/sessions', 's1', 'versions', '2']);
    expect(itemLink(ITEMS[1])).toEqual(['/jira', 'PROJ-1']);
    expect(itemLink(ITEMS[2])).toEqual(['/sessions', 's3-uuid-long']);
  });
});
