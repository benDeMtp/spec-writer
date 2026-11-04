import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Router, provideRouter } from '@angular/router';
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

  async function open(session: SessionDetail, versions: { number: number }[]): Promise<void> {
    fixture = TestBed.createComponent(SpecificationPage);
    fixture.componentRef.setInput('id', 's1');
    fixture.componentRef.setInput('n', '1');
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/sessions/s1/versions/1').flush(SPEC);
    ctrl.expectOne('/api/sessions/s1').flush(session);
    ctrl.expectOne('/api/sessions/s1/versions').flush(versions);
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }

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
  });

  const text = (node: Element) => (node.textContent ?? '').replace(/\s+/g, ' ');

  function section(id: string): HTMLElement {
    return el.querySelector(`section[aria-labelledby="${id}"]`)!;
  }

  it('cite les tickets, ADR et éléments avec leur lien', async () => {
    await open(SESSION, []);
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

  it('affiche les rubriques Architecture, Écarts aux ADR et Efforts supplémentaires', async () => {
    await open(SESSION, []);
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

  function button(label: string): HTMLButtonElement {
    return Array.from(document.querySelectorAll('button')).find((b) => b.textContent?.trim() === label)!;
  }

  /** Bouton de la boîte de confirmation ouverte : « Supprimer la spécification » existe aussi sur la page. */
  function confirmButton(label: string): HTMLButtonElement {
    return Array.from(document.querySelectorAll('mat-dialog-container button')).find(
      (b) => b.textContent?.trim() === label,
    ) as HTMLButtonElement;
  }

  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const linked = {
    ...SESSION,
    linkedTicket: { issueKey: 'FACT-20', issueUrl: 'http://jira/browse/FACT-20', syncState: 'IN_SYNC', linkedVersionNumber: 1 },
  } as SessionDetail;

  it("supprime une version après une confirmation qui la nomme et prévient que Jira n'est pas modifié", async () => {
    await open(linked, [{ number: 1 }, { number: 3 }]);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    button('Supprimer cette version').click();
    await settle();
    const dialog = document.querySelector('mat-dialog-container')!;
    expect(dialog.textContent).toContain('Supprimer la version 1 ?');
    expect(dialog.textContent).toContain('La version 1 de « Avoirs » sera supprimée définitivement.');
    expect(dialog.textContent).toContain("Elle est liée au ticket FACT-20 : Jira n'est pas modifié.");

    // annuler ne supprime rien
    confirmButton('Annuler').click();
    await settle();
    ctrl.expectNone({ method: 'DELETE' });

    button('Supprimer cette version').click();
    await settle();
    confirmButton('Supprimer la version').click();
    await settle();
    ctrl.expectOne({ method: 'DELETE', url: '/api/sessions/s1/versions/1' }).flush(null);
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/sessions', 's1', 'versions', '3']);
    expect(el.querySelector('.announce')?.textContent).toContain("Version 1 supprimée. Jira n'a pas été modifié.");
  });

  it('la dernière version supprimée ramène au questionnaire ; la spécification entière à la liste', async () => {
    await open(SESSION, [{ number: 1 }]);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    button('Supprimer cette version').click();
    await settle();
    expect(document.querySelector('mat-dialog-container')?.textContent).toContain('Le besoin et les réponses sont gardés');
    confirmButton('Supprimer la version').click();
    await settle();
    ctrl.expectOne({ method: 'DELETE', url: '/api/sessions/s1/versions/1' }).flush(null);
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/sessions', 's1']);

    button('Supprimer la spécification').click();
    await settle();
    expect(document.querySelector('mat-dialog-container')?.textContent).toContain(
      '« Avoirs », ses réponses et sa version seront supprimés définitivement.',
    );
    confirmButton('Supprimer la spécification').click();
    await settle();
    ctrl.expectOne({ method: 'DELETE', url: '/api/sessions/s1' }).flush(null);
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/sessions']);
  });
});
