import { LiveAnnouncer } from '@angular/cdk/a11y';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { InterviewDetail, Stop } from '../../core/interviews/interviews.model';
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

describe('InterviewPage (021, US1)', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<InterviewPage>;
  let el: HTMLElement;
  let announced: string[];

  async function open(d: InterviewDetail): Promise<void> {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
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
    el = fixture.nativeElement;
  }

  function key(k: string): void {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true }));
    fixture.detectChanges();
  }

  it('l’élément courant est mis en avant et annoncé ; ↓ avance et enregistre le curseur', async () => {
    await open(detail());
    expect(el.querySelector('h1')?.textContent).toContain('« Avoirs »');
    expect(el.querySelector('[aria-current="step"]')?.textContent).toContain('Résumé');
    expect(announced.at(-1)).toBe('Résumé : Émission des avoirs.');
    key('ArrowDown');
    await settle(fixture);
    const req = ctrl.expectOne('/api/interviews/i1/cursor');
    expect(req.request.body).toEqual({ sessionId: 's1', element: 'UC-01', step: null, alternative: false });
    req.flush(null);
    // le bloc du use case : le use case et ses étapes
    expect(el.querySelectorAll('.block > li')).toHaveLength(3);
    expect(el.querySelector('[aria-current="step"]')?.textContent).toContain('Émettre un avoir');
  });

  it('reprise : la séance s’ouvre sur l’élément du curseur', async () => {
    await open(detail({ cursor: { sessionId: 's1', anchor: { element: 'UC-01', step: 2, alternative: false } } }));
    expect(el.querySelector('[aria-current="step"]')?.textContent).toContain('Saisir le montant');
    expect(el.querySelector('.where')?.textContent).toContain('4 sur 4');
  });

  it('N mène au prochain point ; chaque point a un libellé textuel et « Répondre »', async () => {
    await open(detail());
    key('n');
    ctrl.expectOne('/api/interviews/i1/cursor').flush(null);
    await settle(fixture);
    const item = el.querySelector('[aria-current="step"] .items li')!;
    expect(item.querySelector('.kind')?.textContent).toBe('Hypothèse H-01');
    expect(item.querySelector('button')?.textContent).toContain('Répondre');
    expect(announced.at(-1)).toBe('UC-01, étape 2 : Saisir le montant. 1 point à traiter.');
  });

  it('Entrée ouvre la saisie ; l’enregistrement appelle l’API et confirme « Enregistré »', async () => {
    await open(detail({ cursor: { sessionId: 's1', anchor: { element: 'UC-01', step: 2, alternative: false } } }));
    key('Enter');
    await settle(fixture);
    expect(el.querySelector('sw-point-editor')).toBeTruthy();
    // les raccourcis sont inactifs pendant la saisie
    key('ArrowUp');
    ctrl.expectNone('/api/interviews/i1/cursor');
    const radio = Array.from(el.querySelectorAll<HTMLElement>('mat-radio-button')).find((r) => r.textContent?.includes('Confirmé'))!;
    radio.querySelector('input')!.click();
    await settle(fixture);
    el.querySelector<HTMLButtonElement>('sw-point-editor button[type="submit"]')!.click();
    await settle(fixture);
    const post = ctrl.expectOne('/api/interviews/i1/points');
    expect(post.request.body).toMatchObject({
      sessionId: 's1',
      origin: 'ASSUMPTION',
      originCode: 'H-01',
      outcome: 'CONFIRMED',
      anchor: { element: 'UC-01', step: 2, alternative: false },
    });
    post.flush({});
    await settle(fixture);
    expect(announced).toContain('Enregistré.');
    ctrl.expectOne('/api/interviews/i1').flush(
      detail({
        cursor: { sessionId: 's1', anchor: { element: 'UC-01', step: 2, alternative: false } },
        counts: { handled: 1, notes: 0, pending: 0 },
      }),
    );
    await settle(fixture);
    expect(el.querySelector('.progress')?.textContent?.replace(/\s+/g, ' ')).toContain('1 traité · 0 note · 0 restant');
  });

  it('R : une remarque sur l’élément courant', async () => {
    await open(detail());
    key('r');
    await settle(fixture);
    expect(el.querySelector('sw-point-editor .heading')?.textContent).toBe('Remarque sur Résumé');
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
});
