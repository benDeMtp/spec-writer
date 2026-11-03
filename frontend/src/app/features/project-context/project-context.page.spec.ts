import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { provideMarkdown } from 'ngx-markdown';
import { ContextGeneration, Project } from '../../core/projects/projects.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { CONTEXT_POLL_MS, ProjectContextPage } from './project-context.page';

const FACT: Project = {
  id: 'p1',
  name: 'Facturation',
  description: null,
  jiraProjectKey: 'FACT',
  effectiveJiraProjectKey: 'FACT',
  jiraKeyWarning: null,
  sessionCount: 0,
  context: '## Acteurs\n- Le comptable',
  contextOrigin: 'EDITED',
  contextUpdatedAt: '2026-11-02T10:00:00Z',
  docSources: [
    { id: 's1', kind: 'URL', location: 'https://docs.example.com/billing/', lastReadAt: '2026-11-02T10:00:00Z', pagesRead: 30, pagesInError: 1 },
  ],
};

const CURRENT = '/api/projects/p1/context-generations/current';
const PROPOSAL = {
  proposalId: 'x1',
  markdown: '## Acteurs\n- Le comptable lettre. [source : FACT-1]',
  unifiedDiff: '--- actuelle\n+++ proposition\n@@ -1,2 +1,2 @@\n ## Acteurs\n-- Le comptable\n+- Le comptable lettre. [source : FACT-1]\n',
  sources: ['FACT-1'],
  unread: [],
};

function generation(over: Partial<ContextGeneration>): ContextGeneration {
  return {
    status: 'RUNNING',
    step: 'READING_DOCS',
    current: null,
    done: 0,
    total: 0,
    startedAt: '2026-11-03T10:00:00Z',
    finishedAt: null,
    errors: [],
    proposal: null,
    ...over,
  };
}

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

describe('ProjectContextPage', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<ProjectContextPage>;
  let el: HTMLElement;
  let consent: boolean;

  beforeEach(async () => {
    consent = true;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideMarkdown(),
        { provide: ConsentGate, useValue: { ensure: async () => consent } },
        { provide: CONTEXT_POLL_MS, useValue: 0 },
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProjectContextPage);
    fixture.componentRef.setInput('id', 'p1');
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/projects').flush([FACT]);
    await settle(fixture);
    ctrl.expectOne(CURRENT).flush(null, { status: 404, statusText: 'Not Found' });
    await settle(fixture);
  });

  afterEach(() => ctrl.verify());

  it('affiche la fiche, son origine et le résultat de la dernière lecture des sources', () => {
    expect(el.querySelector('h1')?.textContent).toContain('Facturation');
    expect(el.querySelector<HTMLTextAreaElement>('textarea')!.value).toBe('## Acteurs\n- Le comptable');
    expect(el.textContent).toContain('Modifiée à la main le');
    expect(el.textContent).toContain('30 pages lues, 1 en erreur');
    expect(button(el, 'Enregistrer la fiche')!.disabled).toBe(true);
  });

  it('enregistre la fiche modifiée à la main', async () => {
    const area = el.querySelector<HTMLTextAreaElement>('textarea')!;
    area.value = '## Acteurs\n- Le gestionnaire';
    area.dispatchEvent(new Event('input'));
    await settle(fixture);
    button(el, 'Enregistrer la fiche')!.click();
    const req = ctrl.expectOne('/api/projects/p1/context');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ markdown: '## Acteurs\n- Le gestionnaire' });
    req.flush({ ...FACT, context: '## Acteurs\n- Le gestionnaire' });
    await settle(fixture);
    expect(el.textContent).toContain('Fiche de contexte enregistrée.');
  });

  it('ajoute puis retire une source', async () => {
    const input = el.querySelector<HTMLInputElement>('input[name="location"]')!;
    input.value = 'https://docs.example.com/rh/';
    input.dispatchEvent(new Event('input'));
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

  async function startGeneration(): Promise<void> {
    button(el, 'Proposer une fiche')!.click();
    ctrl.expectOne('/api/projects/p1/context-proposals/preview').flush({
      pages: 32,
      tickets: 120,
      unreadable: [{ location: 'https://down.example.com/', reason: 'UNREACHABLE' }],
    });
    await settle(fixture);
    expect(el.textContent).toContain('Seront envoyés à Copilot : 32 pages de documentation et 120 tickets Jira.');
    expect(el.textContent).toContain('https://down.example.com/ : injoignable');
    button(el, 'Envoyer à Copilot')!.click();
    await settle(fixture);
    ctrl.expectOne((r) => r.method === 'POST' && r.url === '/api/projects/p1/context-generations').flush(generation({ step: null }));
    await settle(fixture);
  }

  it("suit la progression, détaille les erreurs, puis accepte la proposition", async () => {
    await startGeneration();
    ctrl.expectOne(CURRENT).flush(
      generation({ current: 'https://docs.example.com/billing/tva.html', done: 12, total: 32 }),
    );
    await settle(fixture);
    expect(el.textContent).toContain('Lecture de la documentation');
    expect(el.textContent).toContain('page 12 sur 32');
    expect(el.textContent).toContain('En cours : https://docs.example.com/billing/tva.html');
    expect(el.querySelector('progress')!.getAttribute('max')).toBe('32');
    expect(el.textContent).toContain('Lecture de la documentation…');

    ctrl.expectOne(CURRENT).flush(generation({ step: 'READING_TICKETS', current: 'FACT-71', done: 50, total: 120 }));
    await settle(fixture);
    expect(el.textContent).toContain('ticket 50 sur 120');

    ctrl.expectOne(CURRENT).flush(
      generation({
        status: 'SUCCEEDED',
        step: 'BUILDING',
        done: 1,
        total: 1,
        finishedAt: '2026-11-03T10:01:00Z',
        proposal: PROPOSAL,
        errors: [
          { kind: 'PAGE', location: 'https://docs.example.com/billing/tarifs.html', reason: 'HTTP_ERROR', httpStatus: 500, message: null },
          { kind: 'CORRECTION', location: null, reason: 'rédaction de la fiche', httpStatus: null, message: 'Essai 1 sur 3 : la référence X ne correspond à aucune source lue' },
        ],
      }),
    );
    await settle(fixture);
    ctrl.expectOne('/api/projects').flush([FACT]);
    await settle(fixture);
    expect(el.textContent).toContain('Fiche proposée');
    expect(el.textContent).toContain('Détail des erreurs (2)');
    expect(el.textContent).toContain('Page non lue https://docs.example.com/billing/tarifs.html : erreur du serveur (HTTP 500)');
    expect(el.textContent).toContain("Correction demandée à l'IA (rédaction de la fiche) : Essai 1 sur 3");
    expect(el.querySelector('a[href="/journal"]')).toBeTruthy();

    button(el, 'Accepter')!.click();
    ctrl.expectOne('/api/projects/p1/context-proposals/x1/accept').flush({
      ...FACT,
      context: PROPOSAL.markdown,
      contextOrigin: 'GENERATED',
    });
    await settle(fixture);
    expect(el.textContent).toContain("Proposée par l'IA et acceptée le");
    expect(button(el, 'Accepter')).toBeUndefined();
  });

  it('interrompt la génération : rien n’est proposé', async () => {
    await startGeneration();
    ctrl.expectOne(CURRENT).flush(generation({ done: 3, total: 32 }));
    await settle(fixture);
    button(el, 'Interrompre')!.click();
    ctrl.expectOne((r) => r.method === 'DELETE' && r.url === CURRENT).flush(null);
    await settle(fixture);
    ctrl.expectOne(CURRENT).flush(generation({ status: 'CANCELLED', finishedAt: '2026-11-03T10:00:30Z' }));
    await settle(fixture);
    ctrl.expectOne('/api/projects').flush([FACT]);
    await settle(fixture);
    expect(el.textContent).toContain("Génération interrompue. Rien n'a été proposé ni enregistré.");
    expect(el.textContent).not.toContain('Fiche proposée');
    expect(button(el, 'Proposer une fiche')).toBeTruthy();
  });

  it("n'envoie rien sans accord, et « Garder la mienne » ne change rien", async () => {
    button(el, 'Proposer une fiche')!.click();
    ctrl.expectOne('/api/projects/p1/context-proposals/preview').flush({ pages: 1, tickets: 0, unreadable: [] });
    await settle(fixture);
    consent = false;
    button(el, 'Envoyer à Copilot')!.click();
    await settle(fixture);
    ctrl.expectNone('/api/projects/p1/context-generations');

    consent = true;
    button(el, 'Envoyer à Copilot')!.click();
    await settle(fixture);
    ctrl.expectOne('/api/projects/p1/context-generations').flush(generation({}));
    await settle(fixture);
    ctrl.expectOne(CURRENT).flush(generation({ status: 'SUCCEEDED', finishedAt: '2026-11-03T10:01:00Z', proposal: PROPOSAL }));
    await settle(fixture);
    ctrl.expectOne('/api/projects').flush([FACT]);
    await settle(fixture);
    button(el, 'Garder la mienne')!.click();
    await settle(fixture);
    expect(el.textContent).toContain('Fiche actuelle conservée.');
    expect(el.querySelector<HTMLTextAreaElement>('textarea')!.value).toBe('## Acteurs\n- Le comptable');
  });
});

describe('ProjectContextPage, retour sur la page', () => {
  it('reprend une génération en cours', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideMarkdown(),
        { provide: CONTEXT_POLL_MS, useValue: 0 },
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    const ctrl = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ProjectContextPage);
    fixture.componentRef.setInput('id', 'p1');
    fixture.detectChanges();
    ctrl.expectOne('/api/projects').flush([FACT]);
    await settle(fixture);
    ctrl.expectOne(CURRENT).flush(generation({ step: 'SUMMARIZING', done: 1, total: 2 }));
    await settle(fixture);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Extraction des faits');
    expect(el.textContent).toContain('lot 1 sur 2');
    expect(button(el, 'Proposer une fiche')).toBeUndefined();
    ctrl.expectOne(CURRENT).flush(generation({ status: 'FAILED', finishedAt: '2026-11-03T10:02:00Z', errors: [
      { kind: 'FAILURE', location: null, reason: 'LLM_INVALID_OUTPUT', httpStatus: null, message: "La réponse de l'IA est invalide." },
    ] }));
    await settle(fixture);
    ctrl.expectOne('/api/projects').flush([FACT]);
    await settle(fixture);
    expect(el.textContent).toContain("La génération a échoué : La réponse de l'IA est invalide.");
    ctrl.verify();
  });
});
