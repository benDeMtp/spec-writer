import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { provideMarkdown } from 'ngx-markdown';
import { SessionDetail, Specification } from '../../core/sessions/sessions.model';
import { SpecificationPage } from './specification.page';

const SESSION = {
  id: 's1',
  projectId: 'p0',
  name: 'Avoirs',
  state: 'GENERATED',
  updatedAt: '2026-11-04T08:00:00Z',
  latestVersion: 1,
  jiraKey: null,
  linkedTicket: null,
  origin: 'LOCAL',
  readable: true,
  need: 'Saisir un avoir',
  language: 'fr',
  progress: { answered: 0, skipped: 0, total: 0 },
  currentBatch: [],
  answeredQuestions: [],
  warnings: [],
} as SessionDetail;

const ADR = 'http://docs/facturation/adr/adr-005.html';

function useCase(code: string, kind: 'JIRA_ISSUE' | 'DOC_PAGE' | 'ARCH_ELEMENT', ref: string) {
  return {
    code,
    title: `Cas ${code}`,
    actor: 'Gestionnaire',
    goal: 'Émettre un avoir',
    preconditions: [],
    nominalFlow: ['Saisir'],
    alternatives: [],
    source: { kind, questionId: null, excerpt: '', ref },
  };
}

const SPEC: Specification = {
  number: 1,
  origin: 'GENERATED',
  createdAt: '2026-11-04T08:00:00Z',
  title: 'Saisir un avoir',
  summary: 'Résumé',
  details: '',
  useCases: [useCase('UC-1', 'JIRA_ISSUE', 'FACT-7'), useCase('UC-2', 'DOC_PAGE', ADR), useCase('UC-3', 'ARCH_ELEMENT', 'Avoirs')],
  scenarios: [],
  assumptions: [],
  requirements: [],
  markdown: '# Saisir un avoir',
  adrDeviations: [{ adrRef: ADR, decision: 'Les avoirs passent par Kafka', deviation: 'Appel direct demandé' }],
  architecture: {
    modules: [
      { name: 'Avoirs', scope: 'PROJECT' },
      { name: 'Référentiel client', scope: 'COMMON' },
    ],
    exchanges: [
      { source: 'Avoirs', destination: 'Comptabilité', kind: 'INDIRECT', via: 'avoirs-emis' },
      { source: 'Avoirs', destination: 'Référentiel client', kind: 'MISSING', via: null },
    ],
    deploymentFacts: [
      { kind: 'NOT_DEPLOYED', element: 'Avoirs', other: null },
      { kind: 'CROSS_INFRASTRUCTURE', element: 'Avoirs', other: 'Comptabilité' },
    ],
  },
  links: { 'FACT-7': 'http://jira/browse/FACT-7', [ADR]: ADR },
};

describe('SpecificationPage, connaissance du projet (T116)', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<SpecificationPage>;
  let el: HTMLElement;

  beforeEach(async () => {
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
    fixture = TestBed.createComponent(SpecificationPage);
    fixture.componentRef.setInput('id', 's1');
    fixture.componentRef.setInput('n', '1');
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/sessions/s1/versions/1').flush(SPEC);
    ctrl.expectOne('/api/sessions/s1').flush(SESSION);
    ctrl.expectOne('/api/sessions/s1/versions').flush([]);
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  });

  const text = (node: Element) => (node.textContent ?? '').replace(/\s+/g, ' ');

  function section(id: string): HTMLElement {
    return el.querySelector(`section[aria-labelledby="${id}"]`)!;
  }

  it('cite les tickets, ADR et éléments avec leur lien', () => {
    const sources = Array.from(el.querySelectorAll('dd.source'));
    expect(sources[0].textContent).toContain('Ticket');
    expect(sources[0].querySelector('a')?.getAttribute('href')).toBe('http://jira/browse/FACT-7');
    expect(sources[1].textContent).toContain('ADR');
    expect(sources[1].querySelector('a')?.textContent).toContain('adr-005');
    expect(sources[1].querySelector('a')?.getAttribute('href')).toBe(ADR);
    expect(sources[2].textContent).toContain("Élément d'architecture");
    expect(sources[2].textContent).toContain('Avoirs');
    expect(sources[2].querySelector('a')).toBeNull();
  });

  it('affiche les rubriques Architecture, Écarts aux ADR et Efforts supplémentaires', () => {
    const architecture = section('architecture');
    expect(text(architecture)).toContain('Avoirs (propre au projet)');
    expect(text(architecture)).toContain('Référentiel client (commun)');
    expect(text(architecture)).toContain('Avoirs → Comptabilité : flux existant, via avoirs-emis');
    expect(architecture.querySelector('strong')?.textContent).toBe('flux manquant, à créer');

    const deviations = section('deviations');
    expect(deviations.querySelector('h2')?.textContent).toBe('Écarts aux ADR');
    expect(deviations.querySelector('a')?.getAttribute('href')).toBe(ADR);
    expect(text(deviations)).toContain('Les avoirs passent par Kafka');
    expect(text(deviations)).toContain('Appel direct demandé');

    const efforts = section('efforts');
    expect(efforts.querySelector('h2')?.textContent).toBe('Efforts supplémentaires (déploiement)');
    expect(text(efforts)).toContain('Avoirs : aucune instance en production, à déployer');
    expect(text(efforts)).toContain('Avoirs → Comptabilité : traverse des infrastructures');
  });
});
