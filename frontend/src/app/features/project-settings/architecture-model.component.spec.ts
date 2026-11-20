import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Project } from '../../core/projects/projects.model';
import { ArchitectureModelComponent } from './architecture-model.component';

const FACT: Project = {
  id: 'p1',
  name: 'Facturation',
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

describe('ArchitectureModelComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<ArchitectureModelComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ArchitectureModelComponent);
    fixture.componentRef.setInput('project', FACT);
    el = fixture.nativeElement;
    await settle(fixture);
  });

  afterEach(() => ctrl.verify());

  it('enregistre le workspace et relit le projet courant ; la saisie n’est plus en attente', async () => {
    const workspace = el.querySelector<HTMLInputElement>('input[name="workspace"]')!;
    workspace.value = '/Users/moi/C4/workspace.dsl';
    workspace.dispatchEvent(new Event('input'));
    await settle(fixture);
    expect(fixture.componentInstance.dirty()).toBe(true);
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Enregistrer')!.click();
    const put = ctrl.expectOne('/api/projects/p1/architecture-source');
    expect(put.request.body).toEqual({ workspace: '/Users/moi/C4/workspace.dsl', ownership: null });
    put.flush({ ...FACT, architectureWorkspace: '/Users/moi/C4/workspace.dsl' });
    await settle(fixture);
    const saved = { ...FACT, architectureWorkspace: '/Users/moi/C4/workspace.dsl' };
    ctrl.expectOne('/api/projects').flush([saved]);
    ctrl.expectOne('/api/projects/current').flush(saved);
    fixture.componentRef.setInput('project', saved);
    await settle(fixture);
    expect(el.querySelector('[role="status"]')?.textContent).toBe("Modèle d'architecture enregistré. Il est lu en arrière-plan.");
    expect(fixture.componentInstance.dirty()).toBe(false);
    expect(el.querySelector('[aria-label="Retirer le modèle d\'architecture"]')).not.toBeNull();
  });
});
