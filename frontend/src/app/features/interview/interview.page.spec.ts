import { LiveAnnouncer } from '@angular/cdk/a11y';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Router, provideRouter } from '@angular/router';
import { InterviewDetail, Stop } from '../../core/interviews/interviews.model';
import { provideMarkdown } from 'ngx-markdown';
import { Specification } from '../../core/sessions/sessions.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { InterviewPage } from './interview.page';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 4; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

function stop(element: string, step: number | null, kind: Stop['kind'], text: string, items: Stop['items'] = []): Stop {
  const label = element === 'SUMMARY' ? 'Résumé' : step ? `${element}, étape ${step}` : element;
  return { sessionId: 's1', anchor: { element, step, alternative: false }, kind, label, text, items };
}

const H1 = { origin: 'ASSUMPTION' as const, code: 'H-01', statement: 'Le montant est TTC.', point: null, pending: true };

function detail(overrides: Partial<InterviewDetail> = {}): InterviewDetail {
  return {
    id: 'i1',
    state: 'IN_PROGRESS',
    participants: ['Julie M.'],
    createdAt: '2026-11-20T09:00:00Z',
    startedAt: '2026-11-20T09:00:00Z',
    finishedAt: null,
    cursor: null,
    specs: [
      {
        sessionId: 's1',
        name: 'Avoirs',
        rank: 1,
        version: 1,
        latestVersion: 1,
        excluded: [],
        appliedVersion: null,
        revisionAvailable: false,
        revisionUnavailableReason: null,
      },
    ],
    stops: [
      stop('SUMMARY', null, 'SUMMARY', 'Émission des avoirs.'),
      stop('UC-01', null, 'USE_CASE', 'Émettre un avoir'),
      stop('UC-01', 1, 'STEP', 'Ouvrir la facture'),
      stop('UC-01', 2, 'STEP', 'Saisir le montant', [H1]),
    ],
    counts: { handled: 0, notes: 0, pending: 1 },
    ...overrides,
  };
}

const SPEC = {
  number: 1,
  origin: 'GENERATED',
  createdAt: '2026-11-20T09:00:00Z',
  title: 'Avoirs',
  summary: 'Émission des avoirs.',
  details: '',
  useCases: [
    {
      code: 'UC-01',
      title: 'Émettre un avoir',
      actor: 'Gestionnaire',
      goal: 'Rembourser',
      preconditions: [],
      nominalFlow: ['Ouvrir la facture', 'Saisir le montant'],
      alternatives: [],
      source: null,
    },
  ],
  scenarios: [],
  assumptions: [],
  requirements: [],
  markdown: '',
} as unknown as Specification;

const REMARK = {
        origin: 'REMARK' as const,
        code: null,
        statement: null,
        pending: false,
        point: {
          id: 'p9', sessionId: 's1', anchor: { element: 'SUMMARY', step: null, alternative: false }, origin: 'REMARK' as const,
          originCode: null, text: 'À revoir', outcome: 'CHANGE' as const, owner: null, due: null, source: null,
          quote: 'Émission', createdAt: '2026-11-20T09:00:00Z', updatedAt: '2026-11-20T09:00:00Z',
        },
      };

describe('InterviewPage (021, séance en lecture du document)', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<InterviewPage>;
  let el: HTMLElement;
  let announced: string[];

  async function open(d: InterviewDetail): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', children: [] }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideMarkdown(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    announced = [];
    vi.spyOn(TestBed.inject(LiveAnnouncer), 'announce').mockImplementation(async (m) => {
      announced.push(String(m));
    });
    fixture = TestBed.createComponent(InterviewPage);
    fixture.componentRef.setInput('id', 'i1');
    fixture.detectChanges();
    await settle(fixture);
    ctrl.expectOne('/api/interviews/i1').flush(d);
    await settle(fixture);
    if (d.state !== 'FINISHED') {
      ctrl.expectOne('/api/sessions/s1/versions/1').flush(SPEC);
      ctrl.expectOne('/api/sessions/s1').flush({ id: 's1', language: 'fr' });
      await settle(fixture);
    }
    el = fixture.nativeElement;
  }

  function key(k: string): void {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true }));
    fixture.detectChanges();
  }

  function button(label: string, root: ParentNode = el): HTMLButtonElement {
    return Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.trim().startsWith(label))!;
  }

  function cursorRequests(): void {
    ctrl.match('/api/interviews/i1/cursor').forEach((r) => r.flush(null));
  }

  it('déroule toute la spécification, l’hypothèse en carte sous son étape', async () => {
    await open(detail());
    expect(el.querySelector('h1')?.textContent).toContain('« Avoirs »');
    const text = el.querySelector('.paper')?.textContent ?? '';
    expect(text).toContain('Émission des avoirs.');
    expect(text).toContain('Ouvrir la facture');
    const step = el.querySelector('[data-anchor="UC-01:2"]')!;
    expect(step.querySelector('sw-point-card')?.textContent).toContain('Le montant est TTC.');
    expect(button('Confirmer', step)).toBeTruthy();
  });

  it('« Confirmer » enregistre d’un clic, sur l’ancre de l’étape, et met à jour la progression', async () => {
    await open(detail());
    button('Confirmer', el.querySelector('[data-anchor="UC-01:2"]')!).click();
    await settle(fixture);
    const post = ctrl.expectOne('/api/interviews/i1/points');
    expect(post.request.body).toMatchObject({
      sessionId: 's1',
      origin: 'ASSUMPTION',
      originCode: 'H-01',
      outcome: 'CONFIRMED',
      text: '',
      quote: null,
      anchor: { element: 'UC-01', step: 2, alternative: false },
    });
    post.flush({});
    await settle(fixture);
    expect(announced).toContain('Enregistré : confirmé.');
    ctrl.expectOne('/api/interviews/i1').flush(detail({ counts: { handled: 1, notes: 0, pending: 0 } }));
    await settle(fixture);
    cursorRequests();
    expect(el.querySelector('.progress')?.textContent?.replace(/\s+/g, ' ')).toContain('1 traité · 0 note · 0 à traiter');
  });

  it('reprise : le focus va à la carte de l’élément du curseur, et ↑ ↓ passent d’une carte à l’autre', async () => {
    const d = detail({ cursor: { sessionId: 's1', anchor: { element: 'UC-01', step: 2, alternative: false } } });
    d.stops[1].items = [{ origin: 'OPEN_QUESTION', code: 'Q-01', statement: 'Qui valide ?', point: null, pending: true }];
    await open(d);
    const cards = el.querySelectorAll<HTMLElement>('sw-point-card');
    expect(document.activeElement).toBe(cards[1]);
    key('ArrowUp');
    await settle(fixture);
    expect(document.activeElement).toBe(cards[0]);
    const req = ctrl.match('/api/interviews/i1/cursor').at(-1)!;
    expect(req.request.body).toEqual({ sessionId: 's1', element: 'UC-01', step: null, alternative: false });
    cursorRequests();
  });

  it('« Prochain point à traiter » mène à la prochaine carte en attente', async () => {
    await open(detail());
    button('Prochain point à traiter').click();
    await settle(fixture);
    expect(document.activeElement?.tagName).toBe('SW-POINT-CARD');
    expect(document.activeElement?.closest('[data-anchor]')?.getAttribute('data-anchor')).toBe('UC-01:2');
    cursorRequests();
  });

  function selectIn(selector: string, start: number, end: number): void {
    const node = el.querySelector(selector)!;
    const text = node.firstChild!.nodeType === 3 ? node.firstChild! : node.querySelector('*')!.firstChild!;
    const range = document.createRange();
    range.setStart(text, start);
    range.setEnd(text, end);
    window.getSelection()!.removeAllRanges();
    window.getSelection()!.addRange(range);
    node.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
  }

  function toolbar(): HTMLElement {
    return el.querySelector<HTMLElement>('aside [role="toolbar"]')!;
  }

  it('un passage sélectionné se remplace depuis la colonne de notes : rien n’est inséré dans le document', async () => {
    await open(detail());
    expect(el.querySelector('aside')?.textContent).toContain('Sélectionnez un passage');
    selectIn('[data-anchor="UC-01:1"] .line', 7, 17);
    await settle(fixture);
    expect(toolbar().getAttribute('aria-label')).toBe('Noter sur « la facture »');
    expect(el.querySelector('.paper [role="toolbar"]')).toBeNull();
    button('Remplacer', toolbar()).click();
    await settle(fixture);
    expect(el.querySelector('aside sw-point-editor .heading')?.textContent).toBe('Remplacer « la facture »');
    const text = el.querySelector<HTMLTextAreaElement>('aside sw-point-editor textarea')!;
    expect(text.value).toBe('la facture');
    text.value = 'l’avoir';
    text.dispatchEvent(new Event('input'));
    await settle(fixture);
    el.querySelector<HTMLButtonElement>('aside sw-point-editor button[type="submit"]')!.click();
    await settle(fixture);
    expect(ctrl.expectOne('/api/interviews/i1/points').request.body).toMatchObject({
      origin: 'REMARK',
      outcome: 'CHANGE',
      text: 'l’avoir',
      quote: 'la facture',
      anchor: { element: 'UC-01', step: 1, alternative: false },
    });
  });

  it('tout le texte s’annote : un titre hors élément se rattache à l’élément qui le précède', async () => {
    await open(detail());
    selectIn('h3', 0, 10);
    await settle(fixture);
    expect(toolbar().getAttribute('aria-label')).toBe('Noter sur « Déroulemen »');
    button('Supprimer', toolbar()).click();
    await settle(fixture);
    expect(ctrl.expectOne('/api/interviews/i1/points').request.body).toMatchObject({
      origin: 'REMARK',
      outcome: 'REMOVE',
      quote: 'Déroulemen',
      anchor: { element: 'UC-01', step: null, alternative: false },
    });
  });

  it('« Question… » sur un passage demande le responsable', async () => {
    await open(detail());
    selectIn('[data-anchor="UC-01:2"] .line', 0, 6);
    await settle(fixture);
    button('Question…', toolbar()).click();
    await settle(fixture);
    expect(el.querySelector('aside sw-point-editor .heading')?.textContent).toBe('Question sur « Saisir »');
    expect(el.querySelector('aside sw-point-editor input[name="owner"]')).not.toBeNull();
  });

  it('les notes sont listées dans la colonne, pas dans le document ; elles se modifient et se suppriment', async () => {
    const d = detail();
    d.stops[0].items = [REMARK];
    await open(d);
    expect(el.querySelector('.paper sw-point-card')?.textContent).toContain('H-01');
    expect(el.querySelectorAll('.paper sw-point-card')).toHaveLength(1);
    const note = el.querySelector('aside .note-list li')!;
    expect(note.textContent).toContain('Résumé');
    expect(note.textContent).toContain('« Émission »');
    expect(note.textContent).toContain('À modifier');
    expect(el.querySelector('#notes-title')?.textContent).toBe('Notes de séance (1)');
    button('Modifier', note).click();
    await settle(fixture);
    expect(el.querySelector<HTMLTextAreaElement>('aside sw-point-editor textarea')!.value).toBe('À revoir');
    button('Annuler', el.querySelector('aside')!).click();
    await settle(fixture);
    button('Supprimer la remarque', el.querySelector('aside .note-list li')!).click();
    await settle(fixture);
    ctrl.expectOne((r) => r.method === 'DELETE' && r.url === '/api/interviews/i1/points/p9').flush(null);
    await settle(fixture);
    expect(announced).toContain('Remarque supprimée.');
  });

  it('après une première remarque, on en note une autre à la suite', async () => {
    await open(detail());
    async function remark(text: string): Promise<void> {
      selectIn('[data-anchor="UC-01:1"] .line', 0, 6);
      await settle(fixture);
      button('Commenter', toolbar()).click();
      await settle(fixture);
      const area = el.querySelector<HTMLTextAreaElement>('aside sw-point-editor textarea')!;
      area.value = text;
      area.dispatchEvent(new Event('input'));
      await settle(fixture);
      el.querySelector<HTMLButtonElement>('aside sw-point-editor button[type="submit"]')!.click();
      await settle(fixture);
    }
    await remark('Première');
    ctrl.expectOne('/api/interviews/i1/points').flush({});
    await settle(fixture);
    const after = detail();
    after.stops[2].items = [{ ...REMARK, point: { ...REMARK.point, anchor: { element: 'UC-01', step: 1, alternative: false }, text: 'Première' } }];
    ctrl.expectOne('/api/interviews/i1').flush(after);
    await settle(fixture);
    cursorRequests();
    expect(el.querySelector('aside sw-point-editor')).toBeNull();
    await remark('Seconde');
    expect(ctrl.expectOne('/api/interviews/i1/points').request.body).toMatchObject({ origin: 'REMARK', text: 'Seconde', quote: 'Ouvrir' });
  });

  it('une version plus récente est signalée', async () => {
    const d = detail();
    d.specs[0].latestVersion = 2;
    await open(d);
    expect(el.querySelector('.banner')?.textContent).toContain('v2');
  });

  it('une séance terminée mène au récapitulatif', async () => {
    await open(detail({ state: 'FINISHED' }));
    expect(TestBed.inject(Router).url).toBe('/seances/i1/recapitulatif');
  });

  it('« Terminer » avec des points non traités demande confirmation, puis termine', async () => {
    await open(detail({ counts: { handled: 1, notes: 0, pending: 1 } }));
    button('Terminer').click();
    await settle(fixture);
    const dialog = document.querySelector('mat-dialog-container')!;
    expect(dialog.textContent).toContain('1 point reste à traiter');
    button('Terminer la séance', dialog).click();
    await settle(fixture);
    ctrl.expectOne('/api/interviews/i1/finish').flush(detail({ state: 'FINISHED' }));
    await settle(fixture);
    expect(TestBed.inject(Router).url).toBe('/seances/i1/recapitulatif');
  });


  it('« Prendre en compte les notes » termine sans dialogue et lance la révision, même avec des points ouverts', async () => {
    const d = detail({ counts: { handled: 0, notes: 1, pending: 1 } });
    d.stops[0].items = [REMARK];
    await open(d);
    vi.spyOn(TestBed.inject(ConsentGate), 'ensure').mockResolvedValue(true);
    button('Prendre en compte les notes').click();
    await settle(fixture);
    expect(document.querySelector('mat-dialog-container')).toBeNull();
    ctrl.expectOne('/api/interviews/i1/finish').flush(detail({ state: 'FINISHED' }));
    await settle(fixture);
    const req = ctrl.expectOne('/api/interviews/i1/specs/s1/revision');
    req.flush({ turnId: 't1', conversationId: 'c1' });
    await settle(fixture);
    expect(TestBed.inject(Router).url).toBe('/sessions/s1/versions/1?discussion=1&tour=t1');
  });

  it('sans note ni décision, « Prendre en compte les notes » est indisponible', async () => {
    await open(detail());
    expect(button('Prendre en compte les notes').disabled).toBe(true);
  });

  it('zoom propre à la relecture : agrandi par défaut, réglable, retenu, sans toucher au reste du site', async () => {
    localStorage.removeItem('sw.relectureZoom');
    await open(detail());
    const seance = el.querySelector<HTMLElement>('.seance')!;
    expect(seance.style.zoom).toBe('1.25');
    expect(el.querySelector('.zoom-level')?.textContent?.trim()).toBe('125 %');
    button('A+').click();
    await settle(fixture);
    expect(seance.style.zoom).toBe('1.5');
    expect(localStorage.getItem('sw.relectureZoom')).toBe('1.5');
    button('A−').click();
    button('A−').click();
    button('A−').click();
    await settle(fixture);
    expect(seance.style.zoom).toBe('1');
    expect(button('A−').disabled).toBe(true);
    // rien sur la page entière : la page de la spécification garde sa taille normale
    expect(document.documentElement.style.zoom).toBe('');
    expect(document.body.style.zoom).toBe('');
    localStorage.removeItem('sw.relectureZoom');
  });

  it('« Terminer » sans rien de noté : ni confirmation ni récapitulatif, retour à la spécification', async () => {
    await open(detail());
    button('Terminer').click();
    await settle(fixture);
    expect(document.querySelector('mat-dialog-container')).toBeNull();
    ctrl.expectOne('/api/interviews/i1/finish').flush(null, { status: 204, statusText: 'No Content' });
    await settle(fixture);
    expect(TestBed.inject(Router).url).toBe('/sessions/s1/versions/1');
    expect(announced).toContain('Rien n’a été noté : la relecture n’est pas gardée.');
  });
});
