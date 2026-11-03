import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { provideMarkdown } from 'ngx-markdown';
import { Project } from '../../core/projects/projects.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
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
  contextOrigin: 'EDITED',
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

  it("annonce l'envoi, demande l'accord, puis accepte la proposition", async () => {
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
    ctrl.expectOne((r) => r.method === 'POST' && r.url === '/api/projects/p1/context-proposals').flush({
      proposalId: 'x1',
      markdown: '## Acteurs\n- Le comptable lettre. [source : FACT-1]',
      unifiedDiff: '--- actuelle\n+++ proposition\n@@ -1,2 +1,2 @@\n ## Acteurs\n-- Le comptable\n+- Le comptable lettre. [source : FACT-1]\n',
      sources: ['FACT-1'],
      unread: [{ location: 'https://docs.example.com/billing/tarifs.html', reason: 'HTTP_ERROR' }],
    });
    await settle(fixture);
    ctrl.expectOne('/api/projects').flush([FACT]);
    await settle(fixture);
    expect(el.textContent).toContain('Fiche proposée');
    expect(el.textContent).toContain('tarifs.html : erreur du serveur');

    button(el, 'Accepter')!.click();
    ctrl.expectOne('/api/projects/p1/context-proposals/x1/accept').flush({
      ...FACT,
      context: '## Acteurs\n- Le comptable lettre. [source : FACT-1]',
      contextOrigin: 'GENERATED',
    });
    await settle(fixture);
    expect(el.textContent).toContain("Proposée par l'IA et acceptée le");
    expect(button(el, 'Accepter')).toBeUndefined();
  });

  it("n'envoie rien sans accord, et « Garder la mienne » ne change rien", async () => {
    button(el, 'Proposer une fiche')!.click();
    ctrl.expectOne('/api/projects/p1/context-proposals/preview').flush({ pages: 1, tickets: 0, unreadable: [] });
    await settle(fixture);
    consent = false;
    button(el, 'Envoyer à Copilot')!.click();
    await settle(fixture);
    ctrl.expectNone('/api/projects/p1/context-proposals');

    consent = true;
    button(el, 'Envoyer à Copilot')!.click();
    await settle(fixture);
    ctrl.expectOne('/api/projects/p1/context-proposals').flush({ proposalId: 'x2', markdown: 'm', unifiedDiff: '', sources: [], unread: [] });
    await settle(fixture);
    ctrl.expectOne('/api/projects').flush([FACT]);
    await settle(fixture);
    button(el, 'Garder la mienne')!.click();
    await settle(fixture);
    expect(el.textContent).toContain('Fiche actuelle conservée.');
    expect(el.querySelector<HTMLTextAreaElement>('textarea')!.value).toBe('## Acteurs\n- Le comptable');
  });
});
