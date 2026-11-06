import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AdrList, AdrSummary } from '../../core/projects/projects.model';
import { AdrsPage } from './adrs.page';

function adr(over: Partial<AdrSummary>): AdrSummary {
  return {
    number: 1,
    title: '1. Découpage',
    status: 'ACCEPTED',
    supersededBy: null,
    successorFound: false,
    duplicate: false,
    sentToAi: true,
    url: '/api/projects/p1/doc-files/adr/0001-decoupage.html',
    ...over,
  };
}

const LIST: AdrList = {
  adrs: [
    adr({}),
    adr({ number: 2, title: '2. Schéma', status: 'SUPERSEDED', supersededBy: 'ADR-0005', successorFound: true, sentToAi: false }),
    adr({ number: 5, title: '5. Bounded contexts', duplicate: true }),
    adr({ number: 5, title: '5. Trois domaines', status: 'PROPOSED', duplicate: true, url: null }),
    adr({ number: 9, title: '9. Cache', status: 'SUPERSEDED', supersededBy: 'ADR-0042', sentToAi: false }),
    adr({ number: null, title: 'Sans numéro', status: 'UNKNOWN' }),
  ],
  byStatus: { ACCEPTED: 2, PROPOSED: 1, SUPERSEDED: 2, UNKNOWN: 1 },
  indexing: false,
};

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

describe('AdrsPage', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<AdrsPage>;
  let el: HTMLElement;

  async function open(list: AdrList): Promise<void> {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AdrsPage);
    fixture.componentRef.setInput('id', 'p1');
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/projects').flush([{ id: 'p1', name: 'Aurora' }]);
    ctrl.expectOne('/api/projects/p1/knowledge/adrs').flush(list);
    await settle(fixture);
  }

  function rows(): HTMLTableRowElement[] {
    return Array.from(el.querySelectorAll<HTMLTableRowElement>('tbody tr'));
  }

  afterEach(() => ctrl.verify());

  it('une ligne par ADR, dans l’ordre reçu, avec le résumé par statut', async () => {
    await open(LIST);
    expect(el.querySelector('h1')?.textContent).toContain('ADR du projet Aurora');
    expect(rows()).toHaveLength(6);
    expect(rows()[0].textContent).toContain('0001');
    expect(rows()[5].querySelector('td')?.textContent?.trim()).toBe('—');
    expect(el.querySelector('.summary')?.textContent?.trim()).toBe(
      '6 ADR : 2 acceptés, 1 proposé, 2 remplacés, 1 inconnu',
    );
  });

  it('signale ce qui n’est pas transmis à l’IA, les doublons, les successeurs et les statuts inconnus', async () => {
    await open(LIST);
    const [, replaced, dup, , lost, unknown] = rows();
    expect(replaced.classList).toContain('retired');
    expect(replaced.textContent).toContain("non transmis à l'IA");
    expect(replaced.textContent).toContain('ADR-0005');
    expect(replaced.textContent).not.toContain('introuvable');
    expect(lost.textContent).toContain('ADR-0042 (introuvable)');
    expect(dup.textContent).toContain('numéro en double');
    expect(unknown.textContent).toContain('Inconnu');
    expect(rows()[0].classList).not.toContain('retired');
  });

  it('le titre ouvre la page de l’ADR dans un nouvel onglet ; sans adresse, pas de lien', async () => {
    await open(LIST);
    const link = rows()[0].querySelector('a')!;
    expect(link.getAttribute('href')).toBe('/api/projects/p1/doc-files/adr/0001-decoupage.html');
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toBe('noopener noreferrer');
    expect(link.getAttribute('aria-label')).toBe('1. Découpage (nouvel onglet)');
    expect(rows()[3].querySelector('a')).toBeNull();
    expect(rows()[3].textContent).toContain('5. Trois domaines');
  });

  it('le filtre par statut ne change pas le résumé', async () => {
    await open(LIST);
    const select = el.querySelector<HTMLSelectElement>('select')!;
    select.value = 'SUPERSEDED';
    select.dispatchEvent(new Event('change'));
    await settle(fixture);
    expect(rows().map((r) => r.querySelector('.title')?.textContent?.trim())).toEqual(['2. Schéma', '9. Cache']);
    expect(el.querySelector('.summary')?.textContent).toContain('6 ADR');
  });

  it('projet sans ADR : message et retour au contexte ; indexation en cours signalée', async () => {
    await open({ adrs: [], byStatus: {}, indexing: true });
    expect(el.textContent).toContain("Aucun ADR n'a été trouvé dans la documentation de ce projet");
    expect(el.querySelector('table')).toBeNull();
    expect(el.querySelector('a[href="/projets/p1/contexte"]')).not.toBeNull();
    expect(el.textContent).toContain('Indexation en cours');
  });
});
