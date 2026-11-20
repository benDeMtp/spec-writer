import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Project } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { ProjectStepComponent } from './project-step.component';

const DEFAULT: Project = {
  id: '00000000-0000-0000-0000-000000000001',
  name: 'Projet par défaut',
  description: null,
  jiraProjectKey: null,
  effectiveJiraProjectKey: null,
  jiraKeyWarning: null,
  sessionCount: 0,
  context: '',
  contextUpdatedAt: null,
  docSources: [],
};

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

describe('ProjectStepComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<ProjectStepComponent>;
  let el: HTMLElement;

  async function open(jiraConnected: boolean): Promise<void> {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    ctrl = TestBed.inject(HttpTestingController);
    TestBed.inject(ProjectsStore).current.set(DEFAULT);
    fixture = TestBed.createComponent(ProjectStepComponent);
    fixture.componentRef.setInput('jiraConnected', jiraConnected);
    el = fixture.nativeElement;
    await settle(fixture);
  }

  afterEach(() => ctrl.verify());

  it('sans Jira connecté, la clé Jira n’est pas proposée', async () => {
    await open(false);
    expect(el.querySelector('input[name="jiraProjectKey"]')).toBeNull();
  });

  it('renomme le projet par défaut, sans en créer un autre, puis le signale', async () => {
    await open(true);
    let saved = false;
    fixture.componentInstance.saved.subscribe(() => (saved = true));
    const name = el.querySelector<HTMLInputElement>('input[name="name"]')!;
    name.value = 'Aurora';
    name.dispatchEvent(new Event('input'));
    const key = el.querySelector<HTMLInputElement>('input[name="jiraProjectKey"]')!;
    key.value = 'FACT';
    key.dispatchEvent(new Event('input'));
    await settle(fixture);
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Enregistrer')!.click();
    const put = ctrl.expectOne((r) => r.method === 'PUT' && r.url === `/api/projects/${DEFAULT.id}`);
    expect(put.request.body).toEqual({ name: 'Aurora', description: null, jiraProjectKey: 'FACT' });
    put.flush({ ...DEFAULT, name: 'Aurora', jiraProjectKey: 'FACT' });
    await settle(fixture);
    ctrl.expectOne('/api/projects').flush([{ ...DEFAULT, name: 'Aurora' }]);
    ctrl.expectOne('/api/projects/current').flush({ ...DEFAULT, name: 'Aurora' });
    await settle(fixture);
    expect(saved).toBe(true);
    expect(el.textContent).toContain('Projet « Aurora » enregistré.');
  });
});
