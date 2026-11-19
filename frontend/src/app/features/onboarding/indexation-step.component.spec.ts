import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Indexation, Project } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { KNOWLEDGE_POLL_MS } from '../project-knowledge/knowledge-panel.component';
import { IndexationStepComponent } from './indexation-step.component';

const BASE: Project = {
  id: 'p1',
  name: 'Aurora',
  description: null,
  jiraProjectKey: null,
  effectiveJiraProjectKey: null,
  jiraKeyWarning: null,
  sessionCount: 0,
  context: '',
  contextUpdatedAt: null,
  docSources: [],
};

const RUNNING: Indexation = {
  status: 'RUNNING',
  mode: 'INCREMENTAL',
  step: 'DOCUMENTATION',
  current: null,
  done: 1,
  total: 4,
  startedAt: '2026-11-20T08:00:00Z',
  stepStartedAt: null,
  finishedAt: null,
  errors: [],
  tickets: null,
  sources: null,
};

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

describe('IndexationStepComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<IndexationStepComponent>;
  let el: HTMLElement;

  async function open(project: Project): Promise<void> {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: KNOWLEDGE_POLL_MS, useValue: 60_000 }],
    });
    ctrl = TestBed.inject(HttpTestingController);
    TestBed.inject(ProjectsStore).current.set(project);
    fixture = TestBed.createComponent(IndexationStepComponent);
    el = fixture.nativeElement;
    fixture.detectChanges();
    await settle(fixture);
  }

  afterEach(() => {
    fixture.destroy();
    ctrl.verify();
  });

  it("suit l'indexation déjà lancée par l'ajout d'une source", async () => {
    await open({ ...BASE, docSources: [{ id: 's1', kind: 'URL', location: 'https://docs/', lastReadAt: null, pagesRead: null, pagesInError: null, exclusions: [] }] });
    ctrl.expectOne('/api/projects/p1/indexations/current').flush(RUNNING);
    await settle(fixture);
    expect(el.querySelector('sw-indexation-progress')).not.toBeNull();
    expect(el.textContent).toContain('1 sur 4');
  });

  it('lance une synchronisation incrémentale quand rien ne tourne et que le projet a une clé Jira', async () => {
    await open({ ...BASE, jiraProjectKey: 'FACT', effectiveJiraProjectKey: 'FACT' });
    ctrl.expectOne('/api/projects/p1/indexations/current').flush(null, { status: 204, statusText: 'No Content' });
    await settle(fixture);
    const start = ctrl.expectOne('/api/projects/p1/indexations');
    expect(start.request.body).toEqual({ mode: 'INCREMENTAL' });
    start.flush(RUNNING);
    await settle(fixture);
    expect(el.textContent).toContain('sans attendre la fin');
  });

  it("explique qu'il n'y a rien à indexer, et la conversation reste accessible", async () => {
    await open(BASE);
    ctrl.expectOne('/api/projects/p1/indexations/current').flush(null, { status: 204, statusText: 'No Content' });
    await settle(fixture);
    expect(el.querySelector('[data-testid="nothing-to-index"]')?.textContent).toContain("l'agent ne connaîtra pas le projet");
    let opened = false;
    fixture.componentInstance.openConversation.subscribe(() => (opened = true));
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Ouvrir la conversation')!.click();
    expect(opened).toBe(true);
  });
});
