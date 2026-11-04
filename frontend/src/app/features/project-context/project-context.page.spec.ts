import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { provideMarkdown } from 'ngx-markdown';
import { Project } from '../../core/projects/projects.model';
import { ProjectContextPage } from './project-context.page';

const FACT: Project = {
  id: 'p1',
  name: 'Facturation',
  description: null,
  jiraProjectKey: 'FACT',
  effectiveJiraProjectKey: 'FACT',
  jiraKeyWarning: null,
  sessionCount: 0,
  context: '## Acteurs\n- Le comptable',
  contextUpdatedAt: '2026-11-02T10:00:00Z',
  docSources: [
    { id: 's1', kind: 'URL', location: 'https://docs.example.com/billing/', lastReadAt: '2026-11-02T10:00:00Z', pagesRead: 30, pagesInError: 1 },
  ],
};

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

function button(root: Element | Document, label: string): HTMLButtonElement | undefined {
  return Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.trim() === label);
}

function type(field: HTMLTextAreaElement | HTMLInputElement, value: string): void {
  field.value = value;
  field.dispatchEvent(new Event('input'));
}

describe('ProjectContextPage', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<ProjectContextPage>;
  let el: HTMLElement;

  async function open(project: Project): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideMarkdown(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProjectContextPage);
    fixture.componentRef.setInput('id', 'p1');
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/projects').flush([project]);
    await settle(fixture);
  }

  afterEach(() => ctrl.verify());

  it('présente une seule fiche générale et les sources, sans proposition de l’IA', async () => {
    await open(FACT);
    expect(el.querySelector('h1')?.textContent).toContain('Facturation');
    expect(el.querySelector('[role="tab"]')).toBeNull();
    expect(el.querySelector<HTMLTextAreaElement>('textarea[name="context"]')!.value).toBe('## Acteurs\n- Le comptable');
    expect(el.textContent).toContain('Modifiée le');
    expect(el.textContent).toContain("Elle l'emporte sur la documentation");
    expect(el.textContent).toContain('30 pages lues, 1 en erreur');
    expect(button(el, 'Proposer une fiche')).toBeUndefined();
  });

  it('enregistre la fiche générale rédigée à la main', async () => {
    await open({ ...FACT, context: '', contextUpdatedAt: null });
    expect(el.textContent).toContain('Aucune fiche générale pour ce projet.');
    type(el.querySelector<HTMLTextAreaElement>('textarea[name="context"]')!, '## Architecture\n- Quarkus');
    await settle(fixture);
    button(el, 'Enregistrer la fiche')!.click();
    const req = ctrl.expectOne('/api/projects/p1/context');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ markdown: '## Architecture\n- Quarkus' });
    req.flush({ ...FACT, context: '## Architecture\n- Quarkus', contextUpdatedAt: '2026-11-04T08:00:00Z' });
    await settle(fixture);
    expect(el.textContent).toContain('Fiche générale enregistrée.');
    expect(button(el, 'Enregistrer la fiche')!.disabled).toBe(true);
  });

  it('ajoute puis retire une source', async () => {
    await open(FACT);
    type(el.querySelector<HTMLInputElement>('input[name="location"]')!, 'https://docs.example.com/rh/');
    await settle(fixture);
    button(el, 'Ajouter')!.click();
    const add = ctrl.expectOne('/api/projects/p1/doc-sources');
    expect(add.request.body).toEqual({ kind: 'URL', location: 'https://docs.example.com/rh/' });
    add.flush({ id: 's2', kind: 'URL', location: 'https://docs.example.com/rh/', lastReadAt: null, pagesRead: null, pagesInError: null });
    await settle(fixture);
    ctrl.expectOne('/api/projects').flush([FACT]);
    await settle(fixture);
    el.querySelector<HTMLButtonElement>('[aria-label="Retirer la source https://docs.example.com/billing/"]')!.click();
    ctrl.expectOne((r) => r.method === 'DELETE' && r.url === '/api/projects/p1/doc-sources/s1').flush(null);
    await settle(fixture);
    ctrl.expectOne('/api/projects').flush([{ ...FACT, docSources: [] }]);
    await settle(fixture);
    expect(el.textContent).toContain('Aucune source.');
  });
});
