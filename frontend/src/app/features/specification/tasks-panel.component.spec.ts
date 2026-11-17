import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SpecTask, TaskProposal, TasksView } from '../../core/tasks/tasks.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { TasksPanelComponent } from './tasks-panel.component';

const URL = '/api/sessions/s1/tasks';

function task(over: Partial<SpecTask>): SpecTask {
  return {
    id: 't1',
    position: 0,
    title: 'Exposer l’endpoint',
    outcome: 'POST renvoie 201',
    basis: { kind: 'USE_CASE', ref: 'UC-01' },
    origin: 'PROPOSED',
    creation: 'CREATED',
    key: 'FACT-124',
    url: 'https://jira/browse/FACT-124',
    status: 'TODO',
    jiraStatusName: 'À faire',
    heldBy: null,
    heldSince: null,
    holderClosed: false,
    pending: null,
    report: null,
    dependsOn: [],
    ...over,
  };
}

function view(over: Partial<TasksView>): TasksView {
  return {
    linkedIssueKey: 'FACT-120',
    canPropose: true,
    proposeBlocked: null,
    tracking: 'ACTIVE',
    progress: { done: 0, total: 0 },
    lastReadAt: null,
    readProblem: null,
    pendingSync: 0,
    creationProblem: null,
    hasProposal: false,
    tasks: [],
    ...over,
  };
}

const PROPOSAL: TaskProposal = {
  basis: 'USE_CASES',
  existing: [],
  tasks: [
    { title: 'A', outcome: 'R A', basis: { kind: 'USE_CASE', ref: 'UC-01' }, dependsOn: [], manual: false },
    { title: 'B', outcome: 'R B', basis: { kind: 'USE_CASE', ref: 'UC-01' }, dependsOn: [0], manual: false },
  ],
};

describe('TasksPanelComponent', () => {
  let ctrl: HttpTestingController;
  let changed: TasksView[];

  async function settle(fixture: { whenStable(): Promise<unknown>; detectChanges(): void }): Promise<void> {
    for (let i = 0; i < 4; i++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
      await fixture.whenStable();
      fixture.detectChanges();
    }
  }

  async function open(body: TasksView) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ConsentGate, useValue: { ensure: () => Promise.resolve(true) } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(TasksPanelComponent);
    changed = [];
    fixture.componentInstance.changed.subscribe((v) => changed.push(v));
    fixture.componentRef.setInput('sessionId', 's1');
    fixture.detectChanges();
    ctrl.expectOne(URL).flush(body);
    await settle(fixture);
    if (body.hasProposal) {
      ctrl.expectOne(`${URL}/proposal`).flush(PROPOSAL);
      await settle(fixture);
    }
    return fixture;
  }

  afterEach(() => ctrl.verify());

  it('sans ticket lié, la proposition est indisponible et dit pourquoi', async () => {
    const f = await open(view({ canPropose: false, proposeBlocked: 'NO_LINKED_TICKET', linkedIssueKey: null }));
    const el: HTMLElement = f.nativeElement;
    const button = el.querySelector('button') as HTMLButtonElement;
    expect(button.textContent).toContain('Proposer des tâches');
    expect(button.disabled).toBe(true);
    expect(el.querySelector('#tasks-blocked')?.textContent).toContain("Envoyez d'abord la spécification vers Jira");
  });

  it('propose, montre la relecture, puis valide', async () => {
    const f = await open(view({}));
    const el: HTMLElement = f.nativeElement;
    (el.querySelector('button') as HTMLButtonElement).click();
    await settle(f);
    ctrl.expectOne({ method: 'POST', url: `${URL}/proposal` }).flush(PROPOSAL);
    await settle(f);
    ctrl.expectOne({ method: 'GET', url: URL }).flush(view({ hasProposal: true }));
    await settle(f);
    expect(el.textContent).toContain('Tâches proposées');
    expect(el.textContent).toContain('après la tâche 1');
    const validate = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Valider et créer dans Jira'),
    ) as HTMLButtonElement;
    validate.click();
    await settle(f);
    ctrl.expectOne({ method: 'POST', url: `${URL}/proposal/validate` }).flush(
      view({ progress: { done: 0, total: 2 }, tasks: [task({}), task({ id: 't2', key: 'FACT-125', title: 'B' })] }),
    );
    await settle(f);
    expect(el.textContent).not.toContain('Tâches proposées');
    expect(el.textContent).toContain('0 tâche sur 2 terminée');
    expect(el.querySelector('a')?.getAttribute('href')).toBe('https://jira/browse/FACT-124');
    expect(changed.at(-1)?.progress.total).toBe(2);
  });

  it('une correction enregistre la liste entière', async () => {
    const f = await open(view({ hasProposal: true }));
    const el: HTMLElement = f.nativeElement;
    const remove = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Supprimer')) as HTMLButtonElement;
    remove.click();
    await settle(f);
    const req = ctrl.expectOne({ method: 'PUT', url: `${URL}/proposal` });
    expect(req.request.body.tasks.map((t: { title: string }) => t.title)).toEqual(['B']);
    expect(req.request.body.tasks[0].dependsOn).toEqual([]);
    req.flush({ ...PROPOSAL, tasks: req.request.body.tasks });
    await settle(f);
  });

  it('montre l’avancement, l’agent, la synchronisation en attente et le suivi suspendu', async () => {
    const f = await open(
      view({
        progress: { done: 3, total: 5 },
        tracking: 'SUSPENDED',
        pendingSync: 1,
        readProblem: 'JIRA_UNREACHABLE',
        lastReadAt: '2026-11-17T10:12:00Z',
        tasks: [
          task({ status: 'IN_PROGRESS', heldBy: 'claude-code', heldSince: '2026-11-17T10:00:00Z', holderClosed: true }),
          task({ id: 't2', key: 'FACT-125', status: 'DONE', pending: 'DONE', report: 'Tests verts' }),
        ],
      }),
    );
    const el: HTMLElement = f.nativeElement;
    expect(el.textContent).toContain('3 tâches sur 5 terminées');
    expect(el.textContent).toContain('Prise par claude-code');
    expect(el.textContent).toContain('sa conversation est fermée');
    expect(el.textContent).toContain('à synchroniser');
    expect(el.textContent).toContain('Suivi suspendu');
    expect(el.textContent).toContain("Jira n'a pas pu être relu");
    expect(el.textContent).toContain('Compte rendu de l');
    const sync = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Synchroniser') as HTMLButtonElement;
    sync.click();
    await settle(f);
    ctrl.expectOne({ method: 'POST', url: `${URL}/sync` }).flush(view({ progress: { done: 3, total: 5 } }));
    await settle(f);
    expect(el.textContent).not.toContain('à synchroniser');
    const release = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Libérer'));
    expect(release).toBeUndefined();
  });
});
