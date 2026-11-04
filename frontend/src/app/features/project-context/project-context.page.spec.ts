import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { provideMarkdown } from 'ngx-markdown';
import { ContextGeneration, ContextProposal, Project } from '../../core/projects/projects.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { CONTEXT_CLOCK, CONTEXT_POLL_MS, ProjectContextPage, formatDuration } from './project-context.page';

const FACT: Project = {
  id: 'p1',
  name: 'Facturation',
  description: null,
  jiraProjectKey: 'FACT',
  effectiveJiraProjectKey: 'FACT',
  jiraKeyWarning: null,
  sessionCount: 0,
  functionalContext: { markdown: '## Acteurs\n- Le comptable', origin: 'EDITED', updatedAt: '2026-11-02T10:00:00Z' },
  technicalContext: { markdown: '', origin: 'NONE', updatedAt: null },
  docSources: [
    { id: 's1', kind: 'URL', location: 'https://docs.example.com/billing/', lastReadAt: '2026-11-02T10:00:00Z', pagesRead: 30, pagesInError: 1 },
  ],
};

const CURRENT = '/api/projects/p1/context-generations/current';
const QUESTIONS = '/api/projects/p1/context-questions';
const PROPOSAL: ContextProposal = {
  proposalId: 'x1',
  functional: {
    markdown: '## Acteurs\n- Le comptable lettre. [source : FACT-1]',
    unifiedDiff: '--- actuelle\n+++ proposition\n@@ -1,2 +1,2 @@\n ## Acteurs\n-- Le comptable\n+- Le comptable lettre. [source : FACT-1]\n',
    changed: true,
    accepted: false,
  },
  technical: {
    markdown: '## Architecture\n- Une file JMS relie les services. [source : FACT-2]',
    unifiedDiff: '--- actuelle\n+++ proposition\n@@ -0,0 +1,2 @@\n+## Architecture\n+- Une file JMS relie les services. [source : FACT-2]\n',
    changed: true,
    accepted: false,
  },
  sources: ['FACT-1', 'FACT-2'],
  unread: [],
  questions: [
    { id: 'q1', sheet: 'TECHNICAL', label: 'Quelle base de données ?', rationale: 'Aucune source ne la cite.', suggestions: ['PostgreSQL', 'SQLite'], state: 'OPEN', number: null, answer: null },
    { id: 'q2', sheet: 'FUNCTIONAL', label: 'Quelle est la vision du produit ?', rationale: 'La section est vide.', suggestions: [], state: 'OPEN', number: null, answer: null },
  ],
};

function generation(over: Partial<ContextGeneration>): ContextGeneration {
  return {
    status: 'RUNNING',
    step: 'READING_DOCS',
    current: null,
    done: 0,
    total: 0,
    startedAt: '2026-11-03T10:00:00Z',
    stepStartedAt: null,
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

function type(field: HTMLTextAreaElement | HTMLInputElement, value: string): void {
  field.value = value;
  field.dispatchEvent(new Event('input'));
}

function providers(consent: () => boolean) {
  return [
    provideRouter([]),
    provideHttpClient(),
    provideHttpClientTesting(),
    provideMarkdown(),
    { provide: ConsentGate, useValue: { ensure: async () => consent() } },
    { provide: CONTEXT_POLL_MS, useValue: 0 },
    { provide: CONTEXT_CLOCK, useValue: () => Date.parse('2026-11-03T10:01:05Z') },
    { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
  ];
}

describe('ProjectContextPage', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<ProjectContextPage>;
  let el: HTMLElement;
  let consent: boolean;

  beforeEach(async () => {
    consent = true;
    TestBed.configureTestingModule({ providers: providers(() => consent) });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProjectContextPage);
    fixture.componentRef.setInput('id', 'p1');
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/projects').flush([FACT]);
    await settle(fixture);
    ctrl.expectOne(CURRENT).flush(null, { status: 404, statusText: 'Not Found' });
    ctrl.expectOne(QUESTIONS).flush([]);
    await settle(fixture);
  });

  afterEach(() => ctrl.verify());

  async function openTab(label: string): Promise<void> {
    Array.from(el.querySelectorAll<HTMLElement>('[role="tab"]')).find((t) => t.textContent?.trim() === label)!.click();
    await settle(fixture);
  }

  it('présente les deux fiches en onglets, chacune avec son origine', async () => {
    expect(el.querySelector('h1')?.textContent).toContain('Facturation');
    expect(Array.from(el.querySelectorAll('[role="tab"]')).map((t) => t.textContent?.trim())).toEqual(['Fonctionnel', 'Technique']);
    expect(el.querySelector<HTMLTextAreaElement>('textarea')!.value).toBe('## Acteurs\n- Le comptable');
    expect(el.textContent).toContain('Modifiée à la main le');
    expect(el.textContent).toContain('30 pages lues, 1 en erreur');
    await openTab('Technique');
    expect(el.textContent).toContain('Aucune fiche technique pour ce projet.');
    expect(el.textContent).toContain('Transmise aussi aux agents de code.');
  });

  it('enregistre la fiche technique modifiée à la main', async () => {
    await openTab('Technique');
    const area = el.querySelector<HTMLTextAreaElement>('textarea[name="context-TECHNICAL"]')!;
    type(area, '## Architecture\n- Quarkus');
    await settle(fixture);
    button(el, 'Enregistrer la fiche')!.click();
    const req = ctrl.expectOne('/api/projects/p1/context/technical');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ markdown: '## Architecture\n- Quarkus' });
    req.flush({ ...FACT, technicalContext: { markdown: '## Architecture\n- Quarkus', origin: 'EDITED', updatedAt: '2026-11-04T08:00:00Z' } });
    await settle(fixture);
    expect(el.textContent).toContain('Fiche technique enregistrée.');
  });

  it('ajoute puis retire une source', async () => {
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

  async function generate(final: Partial<ContextGeneration>): Promise<void> {
    button(el, 'Proposer une fiche')!.click();
    ctrl.expectOne('/api/projects/p1/context-proposals/preview').flush({
      pages: 32,
      tickets: 120,
      unreadable: [
        { location: 'https://down.example.com/', reason: 'UNREACHABLE' },
        { location: '/Users/moi/projet/docs', reason: 'ADOC_SOURCES' },
      ],
    });
    await settle(fixture);
    expect(el.textContent).toContain('Seront envoyés à Copilot : 32 pages de documentation et 120 tickets Jira.');
    expect(el.textContent).toContain('/Users/moi/projet/docs : sources AsciiDoc (.adoc) et non site généré');
    button(el, 'Envoyer à Copilot')!.click();
    await settle(fixture);
    ctrl.expectOne((r) => r.method === 'POST' && r.url === '/api/projects/p1/context-generations').flush(generation({ step: null }));
    await settle(fixture);
    ctrl.expectOne(CURRENT).flush(
      generation({ current: 'https://docs.example.com/billing/tva.html', done: 12, total: 32, stepStartedAt: '2026-11-03T10:00:50Z' }),
    );
    await settle(fixture);
    expect(el.textContent).toContain('page 12 sur 32');
    expect(el.textContent).toContain('En cours : https://docs.example.com/billing/tva.html');
    expect(el.querySelector('[role="timer"]')?.textContent?.trim()).toBe('Temps écoulé : 1 min 05 s · étape en cours depuis 15 s');
    ctrl.expectOne(CURRENT).flush(generation({ status: 'SUCCEEDED', step: 'BUILDING', done: 1, total: 1, finishedAt: '2026-11-03T10:01:00Z', ...final }));
    await settle(fixture);
    ctrl.expectOne('/api/projects').flush([FACT]);
    await settle(fixture);
    expect(el.textContent).toContain('Durée : 1 min 00 s');
    expect(el.querySelector('[role="timer"]')).toBeNull();
  }

  it('propose deux fiches : accepter l’une, garder l’autre, avec le détail des erreurs', async () => {
    await generate({
      proposal: PROPOSAL,
      errors: [{ kind: 'PAGE', location: 'https://docs.example.com/billing/tarifs.html', reason: 'HTTP_ERROR', httpStatus: 500, message: null }],
    });
    expect(el.textContent).toContain('Fiche fonctionnelle proposée');
    expect(el.textContent).toContain('Fiche technique proposée');
    expect(el.textContent).toContain('Page non lue https://docs.example.com/billing/tarifs.html : erreur du serveur (HTTP 500)');
    // une proposition en attente n'empêche pas d'en demander une nouvelle
    expect(button(el, 'Proposer une fiche')).toBeTruthy();

    button(el, 'Accepter la fiche fonctionnelle')!.click();
    const acc = ctrl.expectOne('/api/projects/p1/context-proposals/x1/accept');
    expect(acc.request.body).toEqual({ sheets: ['FUNCTIONAL'] });
    acc.flush({ ...FACT, functionalContext: { markdown: PROPOSAL.functional.markdown, origin: 'GENERATED', updatedAt: '2026-11-03T10:02:00Z' } });
    await settle(fixture);
    expect(el.textContent).toContain('Fiche fonctionnelle acceptée et enregistrée.');
    expect(el.textContent).toContain("Proposée par l'IA et acceptée le");

    el.querySelector<HTMLButtonElement>('[aria-label="Garder la mienne : fiche technique actuelle"]')!.click();
    await settle(fixture);
    expect(el.textContent).toContain('Fiche technique actuelle conservée.');
    expect(button(el, 'Accepter la fiche technique')).toBeUndefined();
  });

  it('répond, passe, puis révise la proposition avec les réponses', async () => {
    await generate({ proposal: PROPOSAL });
    expect(el.textContent).toContain("Questions de l'assistant");
    expect(button(el, 'Réviser la proposition')!.disabled).toBe(true);

    button(el, 'PostgreSQL')!.click();
    await settle(fixture);
    button(el, 'Répondre')!.click();
    const a1 = ctrl.expectOne('/api/projects/p1/context-proposals/x1/questions/q1');
    expect(a1.request.body).toEqual({ answer: 'PostgreSQL', skip: false });
    a1.flush({ number: 1, sheet: 'TECHNICAL', label: 'Quelle base de données ?', rationale: '', answer: 'PostgreSQL', state: 'ANSWERED', askedAt: '', answeredAt: '' });
    await settle(fixture);
    ctrl.expectOne(QUESTIONS).flush([{ number: 1, sheet: 'TECHNICAL', label: 'Quelle base de données ?', rationale: '', answer: 'PostgreSQL', state: 'ANSWERED', askedAt: '', answeredAt: '' }]);
    await settle(fixture);
    expect(el.textContent).toContain('Réponse Q-1 enregistrée.');
    expect(el.textContent).toContain('Réponses de clarification');

    el.querySelector<HTMLButtonElement>('[aria-label="Passer la question « Quelle est la vision du produit ? »"]')!.click();
    const a2 = ctrl.expectOne('/api/projects/p1/context-proposals/x1/questions/q2');
    expect(a2.request.body).toEqual({ answer: null, skip: true });
    a2.flush({ number: 2, sheet: 'FUNCTIONAL', label: 'Quelle est la vision du produit ?', rationale: '', answer: '', state: 'SKIPPED', askedAt: '', answeredAt: '' });
    await settle(fixture);
    ctrl.expectOne(QUESTIONS).flush([]);
    await settle(fixture);
    expect(el.textContent).toContain('Question passée : elle ne sera plus posée.');

    button(el, 'Réviser la proposition')!.click();
    await settle(fixture);
    ctrl.expectOne((r) => r.method === 'POST' && r.url === '/api/projects/p1/context-proposals/x1/revise')
      .flush(generation({ step: 'BUILDING', total: 1 }));
    await settle(fixture);
    expect(el.textContent).toContain('Rédaction de la fiche');
    const revised: ContextProposal = {
      ...PROPOSAL,
      proposalId: 'x2',
      technical: { ...PROPOSAL.technical, markdown: '## Contraintes techniques\n- PostgreSQL. [source : Q-1]' },
      questions: [],
    };
    ctrl.expectOne(CURRENT).flush(generation({ status: 'SUCCEEDED', step: 'BUILDING', done: 1, total: 1, finishedAt: '2026-11-03T10:03:00Z', proposal: revised }));
    await settle(fixture);
    ctrl.expectOne('/api/projects').flush([FACT]);
    await settle(fixture);
    expect(el.textContent).toContain('[source : Q-1]');
    expect(el.textContent).not.toContain("Questions de l'assistant");
  });

  it('modifie une réponse de l’historique', async () => {
    await generate({ proposal: { ...PROPOSAL, questions: [] } });
    // l'historique se recharge quand une réponse change ; ici, on le simule par une réponse précédente
    fixture.componentInstance['history'].set([
      { number: 3, sheet: 'TECHNICAL', label: 'Quelle base ?', rationale: '', answer: 'SQLite', state: 'ANSWERED', askedAt: '', answeredAt: '' },
    ]);
    await settle(fixture);
    el.querySelector<HTMLButtonElement>('[aria-label="Modifier la réponse Q-3"]')!.click();
    await settle(fixture);
    type(el.querySelector<HTMLTextAreaElement>('textarea[name="history-3"]')!, 'PostgreSQL 16');
    await settle(fixture);
    button(el, 'Enregistrer')!.click();
    const put = ctrl.expectOne('/api/projects/p1/context-questions/3');
    expect(put.request.body).toEqual({ answer: 'PostgreSQL 16', skip: false });
    put.flush({ number: 3, sheet: 'TECHNICAL', label: 'Quelle base ?', rationale: '', answer: 'PostgreSQL 16', state: 'ANSWERED', askedAt: '', answeredAt: '' });
    await settle(fixture);
    ctrl.expectOne(QUESTIONS).flush([]);
    await settle(fixture);
    expect(el.textContent).toContain('Réponse Q-3 modifiée.');
  });

  it("interrompt la génération, et n'envoie rien sans accord", async () => {
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
    ctrl.expectOne(CURRENT).flush(generation({ done: 3, total: 32 }));
    await settle(fixture);
    button(el, 'Interrompre')!.click();
    ctrl.expectOne((r) => r.method === 'DELETE' && r.url === CURRENT).flush(null);
    await settle(fixture);
    // l'état est relu aussitôt ; la réponse de l'interrogation déjà partie est ignorée
    const [late, fresh] = ctrl.match(CURRENT);
    late.flush(generation({ done: 4, total: 32 }));
    fresh.flush(generation({ status: 'CANCELLED', finishedAt: '2026-11-03T10:00:30Z' }));
    await settle(fixture);
    ctrl.expectOne('/api/projects').flush([FACT]);
    await settle(fixture);
    expect(el.textContent).toContain("Génération interrompue. Rien n'a été proposé ni enregistré.");
    expect(button(el, 'Proposer une fiche')).toBeTruthy();
  });
});

describe('ProjectContextPage, retour sur la page', () => {
  it('reprend une génération en cours, puis affiche son échec', async () => {
    TestBed.configureTestingModule({ providers: providers(() => true) });
    const ctrl = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ProjectContextPage);
    fixture.componentRef.setInput('id', 'p1');
    fixture.detectChanges();
    ctrl.expectOne('/api/projects').flush([FACT]);
    await settle(fixture);
    ctrl.expectOne(QUESTIONS).flush([]);
    ctrl.expectOne(CURRENT).flush(generation({ step: 'SUMMARIZING', done: 1, total: 2 }));
    await settle(fixture);
    const el: HTMLElement = fixture.nativeElement;
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

describe('formatDuration', () => {
  it('écrit les secondes, les minutes puis les heures', () => {
    expect(formatDuration(12_400)).toBe('12 s');
    expect(formatDuration(65_000)).toBe('1 min 05 s');
    expect(formatDuration(3_720_000)).toBe('1 h 02 min');
    expect(formatDuration(-5)).toBe('0 s');
  });
});
