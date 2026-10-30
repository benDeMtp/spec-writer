import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Router, provideRouter } from '@angular/router';
import { Question, SessionDetail } from '../../core/sessions/sessions.model';
import { QuestionnairePage } from './questionnaire.page';

function question(id: string, over: Partial<Question> = {}): Question {
  return {
    id,
    theme: 'GOAL',
    label: `Question ${id} ?`,
    rationale: `Parce que ${id}`,
    suggestions: ['Oui', 'Non'],
    defaultValue: 'Oui',
    state: 'PENDING',
    answer: null,
    ...over,
  };
}

function session(over: Partial<SessionDetail> = {}): SessionDetail {
  return {
    id: 's1',
    name: 'Réservation de salles',
    state: 'QUESTIONING',
    updatedAt: '2026-10-30T09:00:00Z',
    latestVersion: null,
    jiraKey: null,
    need: 'Les employés doivent pouvoir réserver une salle.',
    language: 'fr',
    progress: { answered: 1, skipped: 0, total: 3 },
    currentBatch: [question('q2'), question('q3', { theme: 'CONTRADICTION', suggestions: [], defaultValue: null })],
    answeredQuestions: [question('q1', { state: 'ANSWERED', answer: 'Gagner du temps' })],
    warnings: [],
    ...over,
  };
}

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

function button(el: HTMLElement, label: string): HTMLButtonElement | undefined {
  return Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.trim() === label);
}

describe('QuestionnairePage', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<QuestionnairePage>;
  let el: HTMLElement;

  async function open(detail: SessionDetail): Promise<void> {
    fixture = TestBed.createComponent(QuestionnairePage);
    fixture.componentRef.setInput('id', 's1');
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/sessions/s1').flush(detail);
    await settle(fixture);
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
  });

  it('affiche le lot, la justification, les suggestions, la valeur par défaut et la progression', async () => {
    await open(session());
    const cards = el.querySelectorAll('sw-question-card');
    expect(cards).toHaveLength(2);
    expect(cards[0].querySelector('h2')?.textContent).toBe('Question q2 ?');
    expect(cards[0].textContent).toContain('Parce que q2');
    expect(Array.from(cards[0].querySelectorAll('mat-chip-option')).map((c) => c.textContent?.trim())).toEqual(['Oui', 'Non']);
    expect(cards[0].textContent).toContain('spec-writer retiendra « Oui » comme hypothèse');
    expect(button(cards[0] as HTMLElement, 'Passer cette question')).toBeTruthy();
    // une contradiction ne peut pas être passée
    expect(button(cards[1] as HTMLElement, 'Passer cette question')).toBeUndefined();

    const bar = el.querySelector('[role="progressbar"]')!;
    expect(bar.getAttribute('aria-valuenow')).toBe('1');
    expect(bar.getAttribute('aria-valuemax')).toBe('3');
    expect(bar.getAttribute('aria-valuetext')).toBe('1 question traitée sur 3');
  });

  it('place le focus sur la première question du lot', async () => {
    await open(session());
    expect(document.activeElement?.textContent).toBe('Question q2 ?');
  });

  it('une suggestion cliquée remplit la réponse, puis la réponse est envoyée', async () => {
    await open(session());
    const card = el.querySelector('sw-question-card') as HTMLElement;
    (card.querySelector('mat-chip-option') as HTMLElement).click();
    await settle(fixture);
    expect(card.querySelector('textarea')!.value).toBe('Oui');
    button(card, 'Enregistrer la réponse')!.click();
    const put = ctrl.expectOne({ method: 'PUT', url: '/api/sessions/s1/questions/q2/answer' });
    expect(put.request.body).toEqual({ value: 'Oui' });
    put.flush(session({ currentBatch: [session().currentBatch[1]], progress: { answered: 2, skipped: 0, total: 3 } }));
    await settle(fixture);
    expect(el.querySelectorAll('sw-question-card')).toHaveLength(1);
    expect(document.activeElement?.textContent).toBe('Question q3 ?');
  });

  it('passe une question', async () => {
    await open(session());
    button(el.querySelector('sw-question-card') as HTMLElement, 'Passer cette question')!.click();
    const put = ctrl.expectOne({ method: 'PUT', url: '/api/sessions/s1/questions/q2/answer' });
    expect(put.request.body).toEqual({ skip: true });
  });

  it("termine le questionnaire, affiche l'avertissement puis génère", async () => {
    await open(session());
    button(el, 'Terminer le questionnaire')!.click();
    ctrl.expectOne({ method: 'POST', url: '/api/sessions/s1/conclude' }).flush(
      session({
        state: 'READY_TO_GENERATE',
        currentBatch: [],
        progress: { answered: 1, skipped: 2, total: 3 },
        warnings: ['La spécification reposera majoritairement sur des hypothèses : elles seront toutes listées et signalées.'],
      }),
    );
    await settle(fixture);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('majoritairement sur des hypothèses');
    expect(document.activeElement?.id).toBe('ready-title');

    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    button(el, 'Générer la spécification')!.click();
    await settle(fixture);
    expect(el.textContent).toContain('GitHub Copilot rédige la spécification');
    ctrl.expectOne({ method: 'POST', url: '/api/sessions/s1/versions' }).flush({ number: 1 });
    await settle(fixture);
    expect(navigate).toHaveBeenCalledWith(['/sessions', 's1', 'versions', 1]);
  });

  it("si le lot suivant n'arrive pas, la session est relue et la réponse reste affichée", async () => {
    await open(session({ currentBatch: [question('q2')] }));
    const card = el.querySelector('sw-question-card') as HTMLElement;
    const textarea = card.querySelector('textarea')!;
    textarea.value = 'Une réponse';
    textarea.dispatchEvent(new Event('input'));
    await settle(fixture);
    button(card, 'Enregistrer la réponse')!.click();
    await settle(fixture);
    expect(el.textContent).toContain('GitHub Copilot prépare les questions suivantes');
    ctrl.expectOne({ method: 'PUT', url: '/api/sessions/s1/questions/q2/answer' }).flush(
      { code: 'LLM_UNAVAILABLE', userMessage: 'GitHub Copilot ne répond pas.', action: 'RETRY' },
      { status: 503, statusText: 'x' },
    );
    await settle(fixture);
    ctrl.expectOne('/api/sessions/s1').flush(
      session({
        currentBatch: [],
        answeredQuestions: [question('q2', { state: 'ANSWERED', answer: 'Une réponse' })],
        progress: { answered: 1, skipped: 0, total: 1 },
      }),
    );
    await settle(fixture);
    expect(el.textContent).toContain('Une réponse');
  });
});
