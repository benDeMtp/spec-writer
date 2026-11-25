import { LiveAnnouncer } from '@angular/cdk/a11y';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { InterviewPreparationPage } from './interview-preparation.page';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 4; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

function button(root: Element, label: string): HTMLButtonElement | undefined {
  return Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.trim() === label);
}

const SPEC = (code: string) => ({
  number: 1,
  assumptions: [
    { code: 'H-01', statement: `Montant TTC (${code})`, origin: 'TOOL_INFERENCE', questionId: null, anchor: { element: 'UC-01', step: 2, alternative: false } },
  ],
  openQuestions: [{ code: 'Q-01', question: 'Qui valide ?', owner: null, due: null, anchor: null }],
  sourceDeviations: [],
});

describe('InterviewPreparationPage (021, US5)', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<InterviewPreparationPage>;
  let el: HTMLElement;
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    ctrl = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(LiveAnnouncer), 'announce').mockResolvedValue();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) as unknown as ReturnType<typeof vi.fn>;
    fixture = TestBed.createComponent(InterviewPreparationPage);
    fixture.componentRef.setInput('id', 'i1');
    el = fixture.nativeElement;
    fixture.detectChanges();
    await settle(fixture);
    ctrl.expectOne('/api/interviews/i1').flush({
      id: 'i1',
      state: 'PREPARED',
      participants: ['Julie M.'],
      specs: [
        { sessionId: 's1', name: 'Avoirs', latestVersion: 1, excluded: [] },
        { sessionId: 's2', name: 'Relances', latestVersion: 1, excluded: ['Q-01'] },
      ],
      stops: [],
      counts: { handled: 0, notes: 0, pending: 3 },
    });
    await settle(fixture);
    ctrl.expectOne('/api/sessions/s1/versions/1').flush(SPEC('a'));
    ctrl.expectOne('/api/sessions/s2/versions/1').flush(SPEC('b'));
    await settle(fixture);
    ctrl.expectOne('/api/sessions/s1/interviews').flush([
      { id: 'i0', state: 'FINISHED', participants: ['Julie M.', 'Paul D.'], specifications: [], points: 2 },
    ]);
    await settle(fixture);
  });

  it('points avec leur élément ; un point écarté se reprend', () => {
    const specs = el.querySelectorAll('.specs > li');
    expect(specs[0].textContent).toContain('« Avoirs »');
    expect(specs[0].querySelector('.points li')?.textContent).toContain('H-01');
    expect(specs[0].querySelector('.points li .element')?.textContent).toBe('UC-01, étape 2');
    expect(specs[1].querySelectorAll('.points li')[1].classList).toContain('excluded');
    expect(button(specs[1], 'Reprendre dans la séance')).toBeTruthy();
  });

  it('ordre par « Monter » et « Descendre », participants proposés, puis « Enregistrer »', async () => {
    const specs = el.querySelectorAll('.specs > li');
    expect(button(specs[0], 'Monter')?.disabled).toBe(true);
    button(specs[0], 'Descendre')!.click();
    await settle(fixture);
    expect(el.querySelectorAll('.specs > li')[0].textContent).toContain('« Relances »');

    // participants : la dernière séance propose ceux qui manquent
    button(el, 'Paul D.')!.click();
    await settle(fixture);
    expect(el.querySelector('.chips')?.textContent).toContain('Paul D.');

    button(el.querySelectorAll('.specs > li')[1], 'Écarter de cette séance')!.click();
    await settle(fixture);
    button(el, 'Enregistrer')!.click();
    const put = ctrl.expectOne({ method: 'PUT', url: '/api/interviews/i1' });
    expect(put.request.body).toEqual({
      specs: [
        { sessionId: 's2', excluded: ['Q-01'] },
        { sessionId: 's1', excluded: ['H-01'] },
      ],
      participants: ['Julie M.', 'Paul D.'],
    });
    put.flush({ id: 'i1' });
    await settle(fixture);
    expect(navigate).toHaveBeenCalledWith(['/sessions']);
  });

  it('« Démarrer maintenant » enregistre, démarre et ouvre la séance', async () => {
    button(el, 'Démarrer maintenant')!.click();
    ctrl.expectOne({ method: 'PUT', url: '/api/interviews/i1' }).flush({ id: 'i1' });
    await settle(fixture);
    ctrl.expectOne({ method: 'POST', url: '/api/interviews/i1/start' }).flush({ id: 'i1' });
    await settle(fixture);
    expect(navigate).toHaveBeenCalledWith(['/seances', 'i1']);
  });
});
