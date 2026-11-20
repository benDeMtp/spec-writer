import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { Project } from '../../core/projects/projects.model';
import { DocSourcesComponent } from './doc-sources.component';

const FACT: Project = {
  id: 'p1',
  name: 'Facturation',
  description: null,
  jiraProjectKey: 'FACT',
  effectiveJiraProjectKey: 'FACT',
  jiraKeyWarning: null,
  sessionCount: 0,
  context: '',
  contextUpdatedAt: null,
  docSources: [
    { id: 's1', kind: 'URL', location: 'https://docs.example.com/billing/', lastReadAt: null, pagesRead: null, pagesInError: null, exclusions: [] },
  ],
};

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

describe('DocSourcesComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<DocSourcesComponent>;
  let el: HTMLElement;

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
    fixture = TestBed.createComponent(DocSourcesComponent);
    fixture.componentRef.setInput('project', FACT);
    el = fixture.nativeElement;
    await settle(fixture);
  });

  afterEach(() => ctrl.verify());

  it('liste les sources du projet, jamais lues', () => {
    expect(el.querySelector('.location')?.textContent).toBe('https://docs.example.com/billing/');
    expect(el.textContent).toContain('Lien · jamais lue');
  });

  it('ajoute une source, relit le projet courant et annonce l’ajout', async () => {
    const field = el.querySelector<HTMLInputElement>('input[name="location"]')!;
    field.value = 'https://docs.example.com/rh/';
    field.dispatchEvent(new Event('input'));
    await settle(fixture);
    expect(fixture.componentInstance.dirty()).toBe(true);
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Ajouter')!.click();
    const add = ctrl.expectOne('/api/projects/p1/doc-sources');
    expect(add.request.body).toEqual({ kind: 'URL', location: 'https://docs.example.com/rh/' });
    add.flush({ id: 's2' });
    await settle(fixture);
    ctrl.expectOne('/api/projects').flush([FACT]);
    ctrl.expectOne('/api/projects/current').flush(FACT);
    await settle(fixture);
    expect(el.querySelector('[role="status"]')?.textContent).toBe('Source ajoutée. Elle est lue en arrière-plan.');
    expect(fixture.componentInstance.dirty()).toBe(false);
  });
});
