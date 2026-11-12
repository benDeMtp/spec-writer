import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Project } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { ProjectKnowledgePage } from './project-knowledge.page';

const AURORA = { id: 'p1', name: 'Aurora' } as Project;
const STATUS = { tickets: 6, docSections: 7, adrs: 3, architectureElements: 10, syncedAt: '2026-11-04T08:00:00Z', partial: false, model: 'READY' };

describe('ProjectKnowledgePage', () => {
  it('nomme le projet courant, montre sa connaissance et mène aux ADR et aux réglages', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const ctrl = TestBed.inject(HttpTestingController);
    TestBed.inject(ProjectsStore).current.set(AURORA);
    const fixture = TestBed.createComponent(ProjectKnowledgePage);
    fixture.detectChanges();
    ctrl.expectOne('/api/projects/p1/knowledge').flush(STATUS);
    ctrl.expectOne('/api/projects/p1/indexations/current').flush(null, { status: 404, statusText: 'Not Found' });
    for (let i = 0; i < 3; i++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
      await fixture.whenStable();
      fixture.detectChanges();
    }
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('h1')?.textContent).toBe('Connaissance du projet · Aurora');
    expect(el.querySelector('a[href="/projet/connaissance/adr"]')?.textContent).toBe('ADR du projet');
    expect(el.querySelector('a[href="/projet/reglages"]')?.textContent).toBe('Réglages du projet');
    expect(el.querySelector('sw-knowledge-panel')?.textContent).toContain('3 ADR');
    ctrl.verify();
  });
});
