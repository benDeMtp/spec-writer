import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Router, provideRouter } from '@angular/router';
import { provideMarkdown } from 'ngx-markdown';
import { SessionDetail, Specification } from '../../core/sessions/sessions.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { LeaveGuard } from '../../core/navigation/leave-guard';
import { Project } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { Settings } from '../../core/settings/settings.model';
import { SettingsStore } from '../../core/settings/settings.store';
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
  implementation: { status: 'DRAFT', implementedAt: null, evolvedFromSessionId: null },
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
  useCases: [
    useCase('UC-1', 'JIRA_ISSUE', 'FACT-7'),
    useCase('UC-2', 'DOC_PAGE', ADR),
    useCase('UC-3', 'ARCH_ELEMENT', 'Avoirs'),
    useCase('UC-4', 'DOC_PAGE', 'joint:CR COPIL.pdf'),
  ],
  scenarios: [],
  assumptions: [],
  requirements: [],
  markdown: '# Saisir un avoir',
  adrDeviations: [
    { adrRef: ADR, decision: 'Les avoirs passent par Kafka', deviation: 'Appel direct demandé' },
  ],
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

  it('sans clé Jira pour son projet, la création de ticket mène aux réglages du projet (012)', async () => {
    TestBed.inject(SettingsStore).settings.set({
      copilot: {},
      jira: { enabled: true, tokenSet: true, defaultProjectKey: null },
    } as unknown as Settings);
    TestBed.inject(ProjectsStore).projects.set([{ id: 'p0', name: 'Aurora', effectiveJiraProjectKey: null } as Project]);
    await open(SESSION, [{ number: 1 }]);
    const hint = el.querySelector('#jira-missing')!;
    expect(text(hint)).toContain('renseignez la clé Jira du projet');
    expect(hint.querySelector('a')?.getAttribute('href')).toBe('/projet/reglages#jira');

    TestBed.inject(SettingsStore).settings.set({ copilot: {}, jira: null } as unknown as Settings);
    fixture.detectChanges();
    expect(text(el.querySelector('#jira-missing')!)).toContain('le serveur et le jeton Jira');
    expect(el.querySelector('#jira-missing a')?.getAttribute('href')).toBe('/configuration');
  });

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
    // document joint (009) : cité par son nom, sans lien
    expect(text(sources[3])).toContain('Document joint CR COPIL.pdf');
    expect(sources[3].querySelector('a')).toBeNull();
  });

  it('documents joints de la session : liste, ajout désactivé sur une feature implémentée', async () => {
    await open(implemented, [{ number: 1 }]);
    ctrl
      .expectOne('/api/sessions/s1/attachments')
      .flush([{ id: 'a1', title: 'CR COPIL', fileName: 'CR COPIL.pdf', format: 'PDF', chars: 12 }]);
    await settle();
    const attachments = el.querySelector('sw-attachments')!;
    expect(attachments.textContent).toContain('CR COPIL.pdf');
    expect(button('Joindre un document').disabled).toBe(true);
    expect(
      (attachments.querySelector('button[aria-label="Retirer CR COPIL"]') as HTMLButtonElement)
        .disabled,
    ).toBe(true);
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
    return Array.from(document.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === label,
    )!;
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
    linkedTicket: {
      issueKey: 'FACT-20',
      issueUrl: 'http://jira/browse/FACT-20',
      syncState: 'IN_SYNC',
      linkedVersionNumber: 1,
    },
  } as SessionDetail;

  it('discussion avec l’IA à côté du document : contexte, version récente, citation d’une section', async () => {
    await open(SESSION, [{ number: 1 }, { number: 2 }]);
    // le focus ne se vérifie que sur un élément du document
    document.body.appendChild(el);
    const navigateTo = vi.spyOn(TestBed.inject(Router), 'navigate');
    const discuss = button("Discuter avec l'IA");
    expect(discuss.getAttribute('aria-expanded')).toBe('false');
    discuss.click();
    await settle();
    // le panneau ouvert est dans l'adresse : un rechargement le rouvre
    expect(navigateTo.mock.calls[0][1]?.queryParams).toEqual({ discussion: 1, edit: null });

    ctrl
      .expectOne({ method: 'POST', url: '/api/sessions/s1/discussion' })
      .flush({ conversationId: 'c1', specificationName: 'Avoirs', version: 2 });
    await settle();
    ctrl.expectOne('/api/conversations/c1').flush({
      id: 'c1',
      projectId: 'p1',
      name: 'Discussion : Avoirs',
      createdAt: 'x',
      updatedAt: 'x',
      sessions: [],
      memoryUntil: null,
      specification: { sessionId: 's1', name: 'Avoirs' },
      messages: [
        {
          id: 'u1',
          ordinal: 1,
          role: 'USER',
          text: 'Qui émet ?',
          state: 'COMPLETE',
          historyTruncated: false,
          createdAt: 'x',
          steps: [],
          sources: [],
        },
        {
          id: 'a1',
          ordinal: 2,
          role: 'AGENT',
          text: 'Voir [1].',
          state: 'COMPLETE',
          historyTruncated: false,
          createdAt: 'x',
          steps: [],
          specificationVersion: 2,
          sources: [
            {
              n: 1,
              kind: 'SPECIFICATION',
              ref: 'spec:s1@v2#UC-1',
              title: 'Avoirs, UC-1',
              url: '/sessions/s1/versions/2#UC-1',
            },
          ],
        },
      ],
    });
    await settle();
    ctrl.match((r) => r.url.includes('/attachments')).forEach((r) => r.flush([]));
    await settle();

    expect(discuss.getAttribute('aria-expanded')).toBe('true');
    const panel = el.querySelector('sw-discussion-panel')!;
    expect(text(panel)).toContain('Contexte : Avoirs, version 2');
    expect(text(panel)).toContain(
      'Vous consultez la version 1. La discussion porte sur la version 2, la plus récente.',
    );
    expect(text(panel)).toContain('Contexte : version 2 de la spécification');
    expect(document.activeElement?.id).toBe('composer-text');

    // la citation d'une section fait défiler le document jusqu'à elle, sans changer de page
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    panel.querySelector<HTMLAnchorElement>('.note a')!.click();
    expect(navigate).not.toHaveBeenCalled();
    expect(document.activeElement?.id).toBe('UC-1');

    button('Fermer').click();
    await settle();
    await settle();
    expect(el.querySelector('sw-discussion-panel')).toBeNull();
    expect(document.activeElement).toBe(discuss);
    expect(navigateTo.mock.calls.at(-1)![1]?.queryParams).toEqual({ discussion: null, edit: null });
    el.remove();
  });

  it('une proposition en attente n’est pas quittée sans confirmation', async () => {
    await open(SESSION, [{ number: 1 }]);
    expect(await fixture.componentInstance.canLeave()).toBe(true);

    fixture.componentInstance['discussionProposal'].set({
      proposalId: 'p1',
      baseVersion: 1,
      proposed: SPEC,
      unifiedDiff: '',
    });
    await settle();
    // 012 : le sélecteur de projet passe par la même vérification que la garde de la route
    const leaving = TestBed.inject(LeaveGuard).confirm();
    await settle();
    expect(document.querySelector('mat-dialog-container')?.textContent).toContain(
      'Quitter sans appliquer la proposition ?',
    );
    confirmButton('Annuler').click();
    expect(await leaving).toBe(false);
  });

  it("supprime une version après une confirmation qui la nomme et prévient que Jira n'est pas modifié", async () => {
    await open(linked, [{ number: 1 }, { number: 3 }]);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    button('Supprimer cette version').click();
    await settle();
    const dialog = document.querySelector('mat-dialog-container')!;
    expect(dialog.textContent).toContain('Supprimer la version 1 ?');
    expect(dialog.textContent).toContain(
      'La version 1 de « Avoirs » sera supprimée définitivement.',
    );
    expect(dialog.textContent).toContain(
      "Elle est liée au ticket FACT-20 : Jira n'est pas modifié.",
    );

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
    expect(el.querySelector('.announce')?.textContent).toContain(
      "Version 1 supprimée. Jira n'a pas été modifié.",
    );
  });

  it('la dernière version supprimée ramène au questionnaire ; la spécification entière à la liste', async () => {
    await open(SESSION, [{ number: 1 }]);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    button('Supprimer cette version').click();
    await settle();
    expect(document.querySelector('mat-dialog-container')?.textContent).toContain(
      'Le besoin et les réponses sont gardés',
    );
    confirmButton('Supprimer la version').click();
    await settle();
    ctrl.expectOne({ method: 'DELETE', url: '/api/sessions/s1/versions/1' }).flush(null);
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/sessions', 's1']);

    button('Supprimer la spécification').click();
    await settle();
    ctrl.expectOne('/api/sessions/s1/discussions/count').flush({ count: 1 });
    await settle();
    const dialog = document.querySelector('mat-dialog-container')!;
    expect(dialog.textContent).toContain(
      '« Avoirs », ses réponses et sa version seront supprimés définitivement. Elle a une discussion avec l’IA.',
    );
    // 011 : la case est cochée par défaut ; décochée, la discussion est gardée
    const keep = dialog.querySelector<HTMLInputElement>('input[type="checkbox"]')!;
    expect(keep.checked).toBe(true);
    expect(dialog.textContent).toContain('Supprimer aussi ses discussions');
    keep.click();
    await settle();
    confirmButton('Supprimer la spécification').click();
    await settle();
    ctrl.expectOne({ method: 'DELETE', url: '/api/sessions/s1?discussions=keep' }).flush(null);
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/sessions']);
  });

  const implemented = {
    ...SESSION,
    implementation: {
      status: 'IMPLEMENTED',
      implementedAt: '2026-11-02T10:00:00Z',
      evolvedFromSessionId: null,
    },
  } as SessionDetail;

  it('implémentée : actions verrouillées avec leur raison, et « Créer une évolution »', async () => {
    vi.spyOn(TestBed.inject(ConsentGate), 'ensure').mockResolvedValue(true);
    await open(implemented, [{ number: 1 }]);
    const reason = el.querySelector('#locked-reason')!;
    // le mois dépend de la locale du test
    expect(text(reason)).toMatch(
      /Implémentée le 2 \S+ 2026 : cette spécification est verrouillée\./,
    );
    for (const label of ["Discuter avec l'IA", 'Créer un ticket Jira', 'Supprimer cette version']) {
      expect(button(label).disabled).toBe(true);
      expect(button(label).getAttribute('aria-describedby')).toBe('locked-reason');
    }
    expect(button('Supprimer la spécification').disabled).toBe(false);

    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    button('Créer une évolution').click();
    await settle();
    ctrl
      .expectOne({ method: 'POST', url: '/api/sessions/s1/evolutions' })
      .flush({ ...SESSION, id: 's2' });
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/sessions', 's2']);
  });

  it('quitter « implémentée » demande une confirmation qui rappelle la date', async () => {
    await open(implemented, [{ number: 1 }]);
    (el.querySelector('.mat-mdc-select-trigger') as HTMLElement).click();
    await settle();
    (
      Array.from(document.querySelectorAll('mat-option')).find((o) =>
        o.textContent?.includes('En cours'),
      ) as HTMLElement
    ).click();
    await settle();
    const dialog = document.querySelector('mat-dialog-container')!;
    expect(dialog.textContent).toContain('Rétablir un statut antérieur ?');
    expect(dialog.textContent).toContain('a été implémentée le 2 novembre 2026');
    confirmButton('Rétablir').click();
    await settle();
    const req = ctrl.expectOne({ method: 'PUT', url: '/api/sessions/s1/status' });
    expect(req.request.body).toEqual({ status: 'IN_PROGRESS', confirmUnlock: true });
    req.flush({ status: 'IN_PROGRESS', implementedAt: null, evolvedFromSessionId: null });
    await settle();
    expect(el.querySelector('#locked-reason')).toBeNull();
    expect(button("Discuter avec l'IA").disabled).toBe(false);
    expect(el.querySelector('.announce')?.textContent).toContain('Statut : En cours.');
  });
});
