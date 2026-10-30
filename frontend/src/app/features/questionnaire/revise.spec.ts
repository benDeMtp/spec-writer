import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { SessionDetail } from '../../core/sessions/sessions.model';
import { QuestionnairePage, summarize } from './questionnaire.page';

const READY: SessionDetail = {
  id: 's1',
  name: 'Salles',
  state: 'GENERATED',
  updatedAt: '2026-10-30T09:00:00Z',
  latestVersion: 1,
  jiraKey: null,
  readable: true,
  need: 'Besoin',
  language: 'fr',
  progress: { answered: 2, skipped: 0, total: 2 },
  currentBatch: [],
  answeredQuestions: [
    { id: 'q1', theme: 'GOAL', label: 'Objectif ?', rationale: 'r', suggestions: [], defaultValue: null, state: 'ANSWERED', answer: 'Gagner du temps' },
    { id: 'q2', theme: 'ERRORS', label: 'Salle prise ?', rationale: 'r', suggestions: [], defaultValue: null, state: 'ANSWERED', answer: 'Proposer une autre' },
  ],
  warnings: [],
};

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

describe('Modifier une réponse (US3-2)', () => {
  it('résume les questions retirées et ajoutées', () => {
    expect(summarize({ removed: ['Salle prise ?'], added: ['Combien ?', 'Où ?'] })).toBe(
      'Réponse modifiée. 1 question retirée : Salle prise ?. 2 questions ajoutées : Combien ? ; Où ?.',
    );
    expect(summarize({ removed: [], added: [] })).toBe('Réponse modifiée. Les autres réponses restent valables.');
  });

  it('modifie une réponse donnée et annonce le résultat', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    const ctrl = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(QuestionnairePage);
    fixture.componentRef.setInput('id', 's1');
    const el: HTMLElement = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/sessions/s1').flush(READY);
    await settle(fixture);

    const modify = el.querySelector<HTMLButtonElement>('button[aria-label="Modifier la réponse à : Objectif ?"]')!;
    modify.click();
    await settle(fixture);
    const textarea = el.querySelector<HTMLTextAreaElement>('.answered textarea')!;
    expect(textarea.value).toBe('Gagner du temps');
    textarea.value = 'Éviter les doublons';
    textarea.dispatchEvent(new Event('input'));
    await settle(fixture);
    (Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Enregistrer la modification') as HTMLElement).click();
    const put = ctrl.expectOne({ method: 'PUT', url: '/api/sessions/s1/questions/q1/answer' });
    expect(put.request.body).toEqual({ value: 'Éviter les doublons' });
    put.flush({
      ...READY,
      state: 'QUESTIONING',
      currentBatch: [{ id: 'q3', theme: 'BUSINESS_RULES', label: 'Combien ?', rationale: 'r', suggestions: [], defaultValue: null, state: 'PENDING', answer: null }],
      answeredQuestions: [{ ...READY.answeredQuestions[0], answer: 'Éviter les doublons' }],
      revision: { removed: ['Salle prise ?'], added: ['Combien ?'] },
    });
    await settle(fixture);
    expect(el.querySelector('.announce')?.textContent).toContain('1 question retirée : Salle prise ?');
    expect(el.textContent).not.toContain('Proposer une autre');
    expect(document.activeElement?.textContent).toBe('Combien ?');
  });
});
