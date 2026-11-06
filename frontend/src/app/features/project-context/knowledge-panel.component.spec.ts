import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { Indexation, KnowledgeStatus } from '../../core/projects/projects.model';
import { formatDuration } from '../../shared/text';
import { KNOWLEDGE_CLOCK, KNOWLEDGE_POLL_MS, KnowledgePanelComponent } from './knowledge-panel.component';

const STATUS: KnowledgeStatus = {
  tickets: 6,
  docSections: 7,
  adrs: 3,
  architectureElements: 1,
  syncedAt: '2026-11-04T08:00:00Z',
  partial: false,
  model: 'READY',
  sources: [],
};
const SOURCES: KnowledgeStatus['sources'] = [
  { id: 'JIRA', kind: 'JIRA', label: 'Jira FACT' },
  { id: 'd1', kind: 'DOCUMENTATION', label: '/docs/antora' },
  { id: 'ARCHITECTURE', kind: 'ARCHITECTURE', label: '/c4/workspace.dsl' },
];
const CURRENT = '/api/projects/p1/indexations/current';

function indexation(over: Partial<Indexation>): Indexation {
  return {
    status: 'RUNNING',
    mode: 'INCREMENTAL',
    step: 'JIRA',
    current: null,
    done: 0,
    total: 0,
    startedAt: '2026-11-04T10:00:00Z',
    stepStartedAt: null,
    finishedAt: null,
    errors: [],
    tickets: null,
    sources: null,
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

function button(root: Element, label: string): HTMLButtonElement | undefined {
  return Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.trim() === label);
}

describe('KnowledgePanelComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<KnowledgePanelComponent>;
  let el: HTMLElement;

  async function open(status: KnowledgeStatus, current: Indexation | null): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: KNOWLEDGE_POLL_MS, useValue: 0 },
        { provide: KNOWLEDGE_CLOCK, useValue: () => Date.parse('2026-11-04T10:01:05Z') },
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(KnowledgePanelComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/projects/p1/knowledge').flush(status);
    const req = ctrl.expectOne(CURRENT);
    if (current) {
      req.flush(current);
    } else {
      req.flush(null, { status: 404, statusText: 'Not Found' });
    }
    await settle(fixture);
  }

  afterEach(() => ctrl.verify());

  it('« N ADR » mène à la page des ADR ; « 0 ADR » non (004, FR-002)', async () => {
    await open(STATUS, null);
    const link = el.querySelector<HTMLAnchorElement>('.counts a');
    expect(link?.textContent?.trim()).toBe('3 ADR');
    expect(link?.getAttribute('href')).toBe('/projets/p1/contexte/adr');
    expect(el.textContent).toContain("6 tickets · 7 sections de documentation · 3 ADR · 1 élément d'architecture");
  });

  it('pas de lien sans ADR', async () => {
    await open({ ...STATUS, adrs: 0 }, null);
    expect(el.querySelector('.counts a')).toBeNull();
    expect(el.textContent).toContain('0 ADR');
  });

  it('montre les compteurs, la synchronisation et le modèle', async () => {
    await open({ ...STATUS, partial: true, model: 'TERMS_ONLY' }, null);
    expect(el.textContent).toContain("6 tickets · 7 sections de documentation · 3 ADR · 1 élément d'architecture");
    expect(el.textContent).toContain('Résultats partiels');
    expect(el.textContent).toContain('Synchronisée le');
    expect(el.textContent).toContain('indisponible : la recherche se fait par les termes');
  });

  it('suit une indexation : étape, élément en cours, minuteur, puis fin avec ses erreurs', async () => {
    await open(STATUS, null);
    button(el, 'Resynchroniser')!.click();
    const start = ctrl.expectOne('/api/projects/p1/indexations');
    expect(start.request.body).toEqual({ mode: 'INCREMENTAL' });
    start.flush(indexation({ step: null }));
    await settle(fixture);
    ctrl.expectOne(CURRENT).flush(
      indexation({ step: 'DOCUMENTATION', current: 'facturation/adr/adr-005.html', done: 3, total: 7, stepStartedAt: '2026-11-04T10:00:50Z' }),
    );
    await settle(fixture);
    expect(el.textContent).toContain('Lecture de la documentation');
    expect(el.textContent).toContain('3 sur 7');
    expect(el.textContent).toContain('En cours : facturation/adr/adr-005.html');
    expect(el.querySelector('[role="timer"]')?.textContent?.trim()).toBe('Temps écoulé : 1 min 05 s · étape en cours depuis 15 s');
    expect(button(el, 'Resynchroniser')).toBeUndefined();
    ctrl.expectOne(CURRENT).flush(
      indexation({
        status: 'SUCCEEDED',
        step: 'EMBEDDING',
        finishedAt: '2026-11-04T10:01:00Z',
        errors: [{ kind: 'ARCHITECTURE', location: '/c4/workspace.dsl:4', reason: 'INCLUDE_NOT_FOUND', httpStatus: null, message: null }],
      }),
    );
    await settle(fixture);
    ctrl.expectOne('/api/projects/p1/knowledge').flush({ ...STATUS, tickets: 8 });
    await settle(fixture);
    expect(el.textContent).toContain('Indexation terminée en 1 min 00 s, avec des erreurs.');
    expect(el.textContent).toContain("Modèle d'architecture : /c4/workspace.dsl:4 (INCLUDE_NOT_FOUND)");
    expect(el.querySelector('summary')?.textContent?.trim()).toBe('Détail (1 erreur)');
    expect(el.textContent).toContain('8 tickets');
    expect(el.querySelector('[role="timer"]')).toBeNull();
  });

  it("une erreur inattendue d'étape est dite en clair, l'indexation finit avec des erreurs (005, US1)", async () => {
    const error = { kind: 'JIRA' as const, location: 'AURORA', reason: 'UNEXPECTED', httpStatus: null, message: 'Erreur inattendue (NoSuchMethodError)' };
    await open(STATUS, indexation({ status: 'SUCCEEDED', finishedAt: '2026-11-04T10:01:00Z', errors: [error] }));
    expect(el.textContent).toContain('Indexation terminée en 1 min 00 s, avec des erreurs.');
    expect(el.textContent).toContain('Jira : AURORA (Erreur inattendue (NoSuchMethodError) ; les autres sources ont été indexées)');
    expect(el.textContent).not.toContain('UNEXPECTED');
  });

  it("raisons d'un dossier de documents dites en clair (006)", async () => {
    const errors = [
      { kind: 'PAGE' as const, location: 'Exploitation.pdf', reason: 'UNAVAILABLE', httpStatus: null, message: null },
      { kind: 'PAGE' as const, location: 'protege.docx', reason: 'PROTECTED', httpStatus: null, message: null },
      { kind: 'WARNING' as const, location: '/docs', reason: 'IGNORED_FORMAT', httpStatus: null, message: '2 fichier(s) ignoré(s) : format non pris en charge' },
    ];
    await open(STATUS, indexation({ status: 'SUCCEEDED', finishedAt: '2026-11-04T10:01:00Z', errors }));
    expect(el.textContent).toContain('Page non lue : Exploitation.pdf (non disponible, fichier OneDrive non téléchargé hors ligne ?)');
    expect(el.textContent).toContain('Page non lue : protege.docx (protégé par mot de passe)');
    expect(el.textContent).toContain('Avertissement : /docs (formats non pris en charge, 2 fichier(s) ignoré(s)');
  });

  it('des scripts ignorés sont des avertissements, pas des erreurs', async () => {
    const warning = (line: number) => ({
      kind: 'WARNING' as const,
      location: `/c4/workspace.dsl:${line}`,
      reason: 'SCRIPT_IGNORED',
      httpStatus: null,
      message: '!script ignoré',
    });
    await open(STATUS, indexation({ status: 'SUCCEEDED', finishedAt: '2026-11-04T10:01:00Z', errors: [warning(4), warning(10)] }));
    expect(el.textContent).toContain('Indexation terminée en 1 min 00 s, avec des avertissements.');
    expect(el.textContent).not.toContain('avec des erreurs');
    expect(el.querySelector('summary')?.textContent?.trim()).toBe('Détail (2 avertissements)');
    expect(el.textContent).toContain('Avertissement : /c4/workspace.dsl:4 (SCRIPT_IGNORED, !script ignoré)');
  });

  it('bilan des tickets en fin d\'indexation : relus, fermés gardés, retirés (003, FR-009)', async () => {
    const done = { status: 'SUCCEEDED' as const, finishedAt: '2026-11-04T10:01:00Z' };
    await open(STATUS, indexation({ ...done, mode: 'FULL', tickets: { read: 20, keptClosed: 80, removed: 1 } }));
    expect(el.querySelector('.tickets')?.textContent?.trim()).toBe(
      '20 tickets relus, 80 tickets fermés gardés sans relecture, 1 retiré',
    );
  });

  it('bilan incrémental sans fermés gardés, et rien sans étape Jira', async () => {
    await open(STATUS, indexation({ status: 'FAILED', mode: 'INCREMENTAL', tickets: { read: 1, keptClosed: 0, removed: 0 } }));
    expect(el.querySelector('.tickets')?.textContent?.trim()).toBe('1 ticket relu, 0 retiré');
  });

  it('pas de bilan des tickets sans étape Jira, ni pendant l\'indexation', async () => {
    await open(STATUS, indexation({ status: 'SUCCEEDED', mode: 'FULL', finishedAt: '2026-11-04T10:01:00Z' }));
    expect(el.querySelector('.tickets')).toBeNull();
  });

  it('bilan complet avec zéro fermé gardé (premier passage)', async () => {
    await open(STATUS, indexation({ status: 'CANCELLED', mode: 'FULL', tickets: { read: 11, keptClosed: 0, removed: 0 } }));
    expect(el.querySelector('.tickets')?.textContent?.trim()).toBe(
      '11 tickets relus, 0 ticket fermé gardé sans relecture, 0 retiré',
    );
  });

  it('interrompt une indexation reprise au retour sur la page', async () => {
    await open(STATUS, indexation({ mode: 'FULL', step: 'MODEL' }));
    expect(el.textContent).toContain('Préparation du modèle de recherche');
    ctrl.expectOne(CURRENT).flush(indexation({ mode: 'FULL', step: 'MODEL' }));
    await settle(fixture);
    button(el, 'Interrompre')!.click();
    ctrl.expectOne((r) => r.method === 'DELETE' && r.url === CURRENT).flush(null);
    await settle(fixture);
    // la lecture du suivi en cours est remplacée par celle de l'interruption ; seule la dernière compte
    ctrl
      .match((r) => r.method === 'GET' && r.url === CURRENT)
      .forEach((r) => r.flush(indexation({ status: 'CANCELLED', finishedAt: '2026-11-04T10:00:10Z' })));
    await settle(fixture);
    ctrl.expectOne('/api/projects/p1/knowledge').flush(STATUS);
    await settle(fixture);
    expect(el.textContent).toContain('Indexation interrompue');
    expect(button(el, 'Tout réindexer')).toBeDefined();
  });

  it('réindexe les seules sources cochées (005, US2)', async () => {
    await open({ ...STATUS, sources: SOURCES }, null);
    button(el, 'Réindexer des sources…')!.click();
    await settle(fixture);
    const group = el.querySelector('fieldset.choice')!;
    expect(group.querySelector('legend')?.textContent?.trim()).toBe('Sources à réindexer');
    const boxes = Array.from(group.querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
    expect(boxes.map((b) => b.closest('label')?.textContent?.trim())).toEqual([
      'Jira FACT',
      'Documentation : /docs/antora',
      "Modèle d'architecture : /c4/workspace.dsl",
    ]);
    expect(boxes.every((b) => !b.checked)).toBe(true);
    expect(button(el, 'Réindexer la sélection')!.disabled).toBe(true);

    boxes[1].click();
    await settle(fixture);
    expect(button(el, 'Réindexer la sélection')!.disabled).toBe(false);
    button(el, 'Réindexer la sélection')!.click();
    const req = ctrl.expectOne((r) => r.method === 'POST' && r.url === '/api/projects/p1/indexations');
    expect(req.request.body).toEqual({ mode: 'FULL', sources: ['d1'] });
    req.flush(indexation({ mode: 'FULL', step: 'DOCUMENTATION', sources: ['d1'] }));
    await settle(fixture);
    expect(el.querySelector('fieldset.choice')).toBeNull();
    ctrl.expectOne(CURRENT).flush(indexation({ status: 'SUCCEEDED', mode: 'FULL', finishedAt: '2026-11-04T10:01:00Z', sources: ['d1'] }));
    await settle(fixture);
    ctrl.expectOne('/api/projects/p1/knowledge').flush({ ...STATUS, sources: SOURCES });
    await settle(fixture);
    expect(el.textContent).toContain('Sources réindexées : Documentation : /docs/antora');
  });

  it('le choix des sources se referme sans rien lancer', async () => {
    await open({ ...STATUS, sources: SOURCES }, null);
    button(el, 'Réindexer des sources…')!.click();
    await settle(fixture);
    expect(document.activeElement?.textContent?.trim()).toBe('Sources à réindexer');
    button(el, 'Annuler')!.click();
    await settle(fixture);
    expect(el.querySelector('fieldset.choice')).toBeNull();
    expect(document.activeElement).toBe(button(el, 'Réindexer des sources…'));
  });

  it('pas de choix de sources sans source déclarée', async () => {
    await open(STATUS, null);
    expect(button(el, 'Réindexer des sources…')).toBeUndefined();
  });

  it("recherche d'essai par type, avec le lien du ticket", async () => {
    await open(STATUS, null);
    const input = el.querySelector<HTMLInputElement>('input[name="q"]')!;
    input.value = 'annuler une facture';
    input.dispatchEvent(new Event('input'));
    await settle(fixture);
    button(el, 'Rechercher')!.click();
    const req = ctrl.expectOne((r) => r.url === '/api/projects/p1/knowledge/search');
    expect(req.request.params.get('q')).toBe('annuler une facture');
    expect(req.request.params.getAll('kinds')).toBeNull();
    req.flush([
      { kind: 'TICKET', ref: 'FACT-7', title: 'Annuler une facture', status: 'Terminé', scope: null, excerpt: 'Avoir total.', url: 'https://jira.example.com/browse/FACT-7' },
      { kind: 'ARCH_ELEMENT', ref: 'Facturation/Service Avoirs', title: 'Service Avoirs', status: null, scope: 'PROJECT', excerpt: 'Émet les avoirs', url: null },
    ]);
    await settle(fixture);
    const link = el.querySelector<HTMLAnchorElement>('a[href="https://jira.example.com/browse/FACT-7"]')!;
    expect(link.textContent).toBe('FACT-7');
    expect(el.textContent).toContain('· propre au projet');
    expect(el.textContent).toContain("Élément d'architecture");
  });
});

describe('formatDuration', () => {
  it('écrit les secondes, les minutes puis les heures', () => {
    expect(formatDuration(12_400)).toBe('12 s');
    expect(formatDuration(65_000)).toBe('1 min 05 s');
    expect(formatDuration(3_720_000)).toBe('1 h 02 min');
  });
});
