import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subtask, SubtaskReport } from '../../core/sessions/sessions.model';
import { SubtasksPanelComponent } from './subtasks-panel.component';

const URL = '/api/sessions/s1/versions/3/subtasks';

function subtask(over: Partial<Subtask>): Subtask {
  return {
    key: 'FACT-21',
    title: 'API de paiement',
    url: 'https://jira.example.com/browse/FACT-21',
    status: 'En cours',
    statusCategory: 'indeterminate',
    statusFresh: true,
    statusDate: null,
    useCase: { code: 'UC-01', title: 'Payer' },
    alternative: null,
    closeness: 'VERY_CLOSE',
    change: 'CHANGED',
    state: 'TO_REVIEW',
    ...over,
  };
}

const REPORT: SubtaskReport = {
  storyKey: 'FACT-20',
  parentKey: null,
  matching: 'SEMANTIC',
  matchingProblem: null,
  subtasks: [
    subtask({}),
    subtask({
      key: 'FACT-22',
      title: 'Écran du remboursement',
      status: 'Terminé',
      statusCategory: 'done',
      useCase: { code: 'UC-02', title: 'Rembourser' },
      closeness: 'CLOSE',
      change: 'REMOVED',
      state: 'DELIVERED_TO_REWORK',
    }),
    subtask({
      key: 'FACT-24',
      title: 'Historique',
      useCase: { code: 'UC-03', title: 'Suivre' },
      alternative: { code: 'UC-01', title: 'Payer' },
      change: null,
      state: 'CONSISTENT',
    }),
    subtask({
      key: 'FACT-23',
      title: 'Dépendances',
      useCase: null,
      closeness: null,
      change: null,
      state: 'UNMATCHED',
    }),
  ],
};

describe('SubtasksPanelComponent', () => {
  let ctrl: HttpTestingController;

  async function open(body: SubtaskReport | null, status = 200): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    ctrl = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SubtasksPanelComponent);
    fixture.componentRef.setInput('sessionId', 's1');
    fixture.componentRef.setInput('versionNumber', 3);
    fixture.detectChanges();
    ctrl.expectOne(URL).flush(body, { status, statusText: status === 204 ? 'No Content' : 'OK' });
    for (let i = 0; i < 3; i++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
      await fixture.whenStable();
      fixture.detectChanges();
    }
    return fixture.nativeElement;
  }

  afterEach(() => ctrl.verify());

  it('montre les sous-tâches à revoir et à reprendre, et replie les autres', async () => {
    const el = await open(REPORT);
    expect(el.querySelector('h2')?.textContent).toBe('Sous-tâches de FACT-20');
    const headings = Array.from(el.querySelectorAll('h3')).map((h) => h.textContent?.trim());
    expect(headings).toEqual(['À revoir', 'Livrées, à reprendre']);
    expect(el.textContent).toContain('UC-01 a changé.');
    expect(el.textContent).toContain('Probable : très proche de UC-01 « Payer ».');
    expect(el.textContent).toContain('UC-02 a été supprimé.');
    expect(el.textContent).toContain('proche de UC-02 « Rembourser »');
    const details = Array.from(el.querySelectorAll('details'));
    expect(details.map((d) => d.querySelector('summary')?.textContent?.trim())).toEqual([
      'Cohérentes (1)',
      'Non rattachées (1)',
    ]);
    expect(details.every((d) => !d.open)).toBe(true);
    expect(details[0].textContent).toContain('ou de UC-01 « Payer »');
    expect(el.querySelector('a')?.getAttribute('href')).toBe(
      'https://jira.example.com/browse/FACT-21',
    );
    expect(el.textContent).not.toContain('travaux en cours');
  });

  it('dit pourquoi le rattachement est indisponible, et le statut indexé', async () => {
    const el = await open({
      ...REPORT,
      matching: 'UNAVAILABLE',
      matchingProblem: 'modèle absent',
      subtasks: [
        subtask({
          useCase: null,
          change: null,
          state: 'UNMATCHED',
          statusFresh: false,
          statusDate: '2026-10-01T10:00:00Z',
        }),
      ],
    });
    expect(el.textContent).toContain("Rattachement aux cas d'usage indisponible : modèle absent");
    expect(el.textContent).toContain("Jira n'a pas pu être relu");
  });

  it('montre la story parente', async () => {
    const el = await open({ ...REPORT, parentKey: 'FACT-10', subtasks: [] });
    expect(el.textContent).toContain('Story parente : FACT-10');
    expect(el.textContent).toContain('Aucune sous-tâche.');
  });

  it("n'affiche rien sans sous-tâche (204)", async () => {
    const el = await open(null, 204);
    expect(el.querySelector('section')).toBeNull();
  });
});
