import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { Project } from '../../core/projects/projects.model';
import { ProjectsPage } from './projects.page';

const DEFAULT: Project = {
  id: 'p0',
  name: 'Projet par défaut',
  description: null,
  jiraProjectKey: null,
  effectiveJiraProjectKey: 'PROJ',
  jiraKeyWarning: null,
  sessionCount: 3,
  context: '',
  contextUpdatedAt: null,
  docSources: [],
};
const FACT: Project = { ...DEFAULT, id: 'p1', name: 'Facturation', jiraProjectKey: 'FACT', effectiveJiraProjectKey: 'FACT', sessionCount: 2 };

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

function button(root: Element | Document, label: string): HTMLButtonElement | undefined {
  return Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.trim() === label);
}

function type(input: HTMLInputElement | HTMLTextAreaElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

describe('ProjectsPage', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<ProjectsPage>;
  let el: HTMLElement;

  function flushLoad(projects: Project[], current: Project): void {
    ctrl.expectOne('/api/projects').flush(projects);
    ctrl.expectOne('/api/projects/current').flush(current);
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
    fixture = TestBed.createComponent(ProjectsPage);
    el = fixture.nativeElement;
    fixture.detectChanges();
    flushLoad([FACT, DEFAULT], DEFAULT);
    await settle(fixture);
  });

  afterEach(() => ctrl.verify());

  it('liste les projets, leur clé Jira effective et le projet courant', () => {
    const items = el.querySelectorAll('li');
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('Facturation');
    expect(items[0].textContent).toContain('2 spécifications');
    expect(items[0].textContent).toContain('Jira : FACT');
    expect(items[1].textContent).toContain('PROJ (projet par défaut)');
    expect(items[1].textContent).toContain('Projet courant');
    expect(button(items[0], 'Rendre courant')).toBeTruthy();
    expect(button(items[1], 'Rendre courant')).toBeUndefined();
  });

  it('crée un projet et signale une clé Jira introuvable sans bloquer', async () => {
    button(el, 'Nouveau projet')!.click();
    await settle(fixture);
    type(el.querySelector<HTMLInputElement>('input[name="name"]')!, ' Paie ');
    type(el.querySelector<HTMLInputElement>('input[name="jiraProjectKey"]')!, 'NOPE');
    await settle(fixture);
    button(el, 'Enregistrer')!.click();
    const req = ctrl.expectOne((r) => r.method === 'POST' && r.url === '/api/projects');
    expect(req.request.body).toEqual({ name: 'Paie', description: null, jiraProjectKey: 'NOPE' });
    req.flush({ ...DEFAULT, id: 'p2', name: 'Paie', jiraProjectKey: 'NOPE', jiraKeyWarning: 'Le projet Jira NOPE est introuvable.' });
    await settle(fixture);
    flushLoad([FACT, { ...DEFAULT, id: 'p2', name: 'Paie' }, DEFAULT], DEFAULT);
    await settle(fixture);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('NOPE est introuvable');
    expect(el.querySelector('[role="status"]')?.textContent).toContain('Projet « Paie » créé.');
  });

  it('supprime un projet en déplaçant ses spécifications', async () => {
    button(el.querySelectorAll('li')[0], 'Supprimer')!.click();
    await settle(fixture);
    const dialog = document.querySelector('sw-delete-project-dialog')!;
    expect(dialog.textContent).toContain('Supprimer le projet « Facturation » ?');
    expect(dialog.textContent).toContain('2 spécifications');
    expect(dialog.textContent).toContain("Jira n'est pas modifié.");
    button(dialog, 'Supprimer le projet')!.click();
    await settle(fixture);
    const req = ctrl.expectOne((r) => r.method === 'DELETE' && r.url === '/api/projects/p1');
    expect(req.request.params.get('sessions')).toBe('MOVE');
    expect(req.request.params.get('targetProjectId')).toBe('p0');
    req.flush(null);
    await settle(fixture);
    flushLoad([DEFAULT], DEFAULT);
    await settle(fixture);
    expect(el.querySelectorAll('li')).toHaveLength(1);
    expect(button(el, 'Supprimer')?.disabled).toBe(true);
    expect(el.textContent).toContain('Le dernier projet restant ne peut pas être supprimé.');
  });

  it('change le projet courant', async () => {
    button(el.querySelectorAll('li')[0], 'Rendre courant')!.click();
    const req = ctrl.expectOne((r) => r.method === 'PUT' && r.url === '/api/projects/current');
    expect(req.request.body).toEqual({ projectId: 'p1' });
    req.flush(null);
    await settle(fixture);
    flushLoad([FACT, DEFAULT], FACT);
    await settle(fixture);
    expect(el.querySelectorAll('li')[0].textContent).toContain('Projet courant');
  });
});
