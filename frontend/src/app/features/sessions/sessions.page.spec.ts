import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Router, provideRouter } from '@angular/router';
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

  /** La liste et les séances préparées, relues ensemble. */
  function list(sessions: SessionSummary[], prepared: unknown[] = []): void {
    ctrl.expectOne('/api/sessions').flush(sessions);
    ctrl.expectOne((r) => r.url === '/api/interviews' && r.params.get('state') === 'PREPARED').flush(prepared);
  }

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
    list(LIST);
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
    list([LIST[0], { ...LIST[1], name: 'Paiement en ligne' }]);
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
    list(LIST.slice(1));
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

  describe('séances (021, US5)', () => {
    const WITH: SessionSummary[] = [
      { ...LIST[0], pending: { assumptions: 3, openQuestions: 2, sourceDeviations: 1 } },
      { ...LIST[1], id: 's4', name: 'Avoirs', latestVersion: 1, pending: { assumptions: 0, openQuestions: 0, sourceDeviations: 0 } },
      LIST[1],
    ];

    async function reopen(prepared: unknown[] = []): Promise<void> {
      fixture = TestBed.createComponent(SessionsPage);
      el = fixture.nativeElement;
      document.body.appendChild(el);
      fixture.detectChanges();
      list(WITH, prepared);
      await settle(fixture);
    }

    afterEach(() => el.remove());

    it('les points à traiter se lisent avec le nom de la spécification', async () => {
      await reopen();
      const first = el.querySelectorAll('.sessions li')[0];
      expect(first.querySelector('.pending')?.textContent).toBe('3 hypothèses · 2 points ouverts · 1 écart entre sources');
      expect(first.querySelector('a.name')?.getAttribute('aria-describedby')).toBe('pending-s1');
      expect(el.querySelectorAll('.sessions li')[1].querySelector('.pending')?.textContent).toBe('Aucun point à traiter');
      // sans version, ni compteur ni case
      expect(el.querySelectorAll('.sessions li')[2].querySelector('.pending, .pick')).toBeNull();
    });

    it('le filtre annonce le nombre affiché', async () => {
      await reopen();
      const filter = Array.from(el.querySelectorAll<HTMLLabelElement>('label')).find((l) =>
        l.textContent?.includes('Avec des points à traiter'),
      )!;
      filter.querySelector('input')!.click();
      await settle(fixture);
      expect(el.querySelectorAll('.sessions li')).toHaveLength(1);
      expect(el.querySelector('.sheet .announce')?.textContent).toBe('1 spécification affichée.');
    });

    it('cocher fait apparaître « Préparer une séance (n) », qui prépare puis ouvre la préparation', async () => {
      await reopen();
      expect(button(el, 'Préparer une séance (1)')).toBeUndefined();
      const boxes = el.querySelectorAll<HTMLInputElement>('input.pick');
      expect(boxes[0].getAttribute('aria-label')).toBe('Sélectionner « Réservation de salles » pour une séance');
      boxes[1].click();
      boxes[0].click();
      await settle(fixture);
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      button(el, 'Préparer une séance (2)')!.click();
      const post = ctrl.expectOne({ method: 'POST', url: '/api/interviews' });
      expect(post.request.body.specs.map((x: { sessionId: string }) => x.sessionId)).toEqual(['s1', 's4']);
      post.flush({ id: 'i1' });
      await settle(fixture);
      expect(navigate).toHaveBeenCalledWith(['/seances', 'i1', 'preparation']);
    });

    it('la séance préparée en tête : « Démarrer », « Modifier » et « Supprimer » après confirmation', async () => {
      const prepared = {
        id: 'i1',
        state: 'PREPARED',
        participants: [],
        createdAt: 'x',
        startedAt: null,
        finishedAt: null,
        specifications: [
          { sessionId: 's1', name: 'Réservation de salles' },
          { sessionId: 's4', name: 'Avoirs' },
        ],
        points: 7,
      };
      await reopen([prepared]);
      const head = el.querySelector('.prepared')!;
      expect(head.textContent).toContain('Séance préparée : 2 spécifications, 7 points');
      expect(button(head, 'Modifier')?.getAttribute('href')).toBe('/seances/i1/preparation');
      button(head, 'Supprimer')!.click();
      await settle(fixture);
      const dialog = document.querySelector('sw-confirm-dialog')!;
      expect(dialog.textContent).toContain('« Réservation de salles », « Avoirs »');
      button(dialog, 'Supprimer la séance')!.click();
      await settle(fixture);
      ctrl.expectOne({ method: 'DELETE', url: '/api/interviews/i1' }).flush(null);
      await settle(fixture);
      list(WITH, []);
      await settle(fixture);
      expect(el.querySelector('.prepared')).toBeNull();

      await reopen([prepared]);
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      button(el.querySelector('.prepared')!, 'Démarrer')!.click();
      ctrl.expectOne({ method: 'POST', url: '/api/interviews/i1/start' }).flush({ id: 'i1' });
      await settle(fixture);
      expect(navigate).toHaveBeenCalledWith(['/seances', 'i1']);
    });
  });
});

