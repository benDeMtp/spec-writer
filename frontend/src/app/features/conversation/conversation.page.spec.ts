import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Router, provideRouter } from '@angular/router';
import { provideMarkdown } from 'ngx-markdown';
import { TURN_STREAM } from '../../core/conversations/conversations.api';
import { ConversationDetail, TurnEvent } from '../../core/conversations/conversations.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { MENTION_DELAYS } from '../../shared/ui/mention.directive';
import { ConversationPage } from './conversation.page';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

const P = 'p1';

function detail(over: Partial<ConversationDetail> = {}): ConversationDetail {
  return {
    id: 'c1',
    projectId: P,
    name: 'Comment sont émis les avoirs ?',
    createdAt: '2026-11-09T09:00:00Z',
    updatedAt: '2026-11-09T09:00:00Z',
    sessions: [],
    messages: [
      { id: 'u1', role: 'USER', text: 'Comment sont émis les avoirs ?', state: 'COMPLETE', historyTruncated: false,
        createdAt: '2026-11-09T09:00:00Z', steps: [], sources: [] },
      { id: 'a1', role: 'AGENT', text: 'Par le service Avoirs [1], voir la spécification [2].', state: 'COMPLETE',
        historyTruncated: false, createdAt: '2026-11-09T09:00:01Z',
        steps: [{ ordinal: 1, query: 'émission des avoirs', kinds: [], hits: 6 }],
        sources: [
          { n: 1, kind: 'TICKET', ref: 'FACT-7', title: 'Émettre un avoir', url: 'http://jira/browse/FACT-7' },
          { n: 2, kind: 'SPECIFICATION', ref: 'spec:s9@v2', title: 'Saisir un avoir', url: '/sessions/s9/versions/2' },
        ] },
    ],
    ...over,
  };
}

describe('ConversationPage', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<ConversationPage>;
  let el: HTMLElement;
  let emit: (e: TurnEvent) => void;
  let opened: string[];
  let consent: boolean;

  beforeEach(() => {
    suggestions = ['Où en est AUR-31 « Lettrage manuel », et qu’en dit la documentation ?'];
    opened = [];
    consent = true;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideMarkdown(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: MENTION_DELAYS, useValue: { suggest: 0, resolve: 0 } },
        { provide: ConsentGate, useValue: { ensure: async () => consent } },
        {
          provide: TURN_STREAM,
          useValue: (url: string, onEvent: (e: TurnEvent) => void) => {
            opened.push(url);
            emit = onEvent;
            return { close: () => undefined };
          },
        },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    TestBed.inject(ProjectsStore).current.set({ id: P, name: 'Aurora' } as never);
  });

  let suggestions: string[];

  async function open(cid?: string): Promise<void> {
    fixture = TestBed.createComponent(ConversationPage);
    if (cid) {
      fixture.componentRef.setInput('cid', cid);
    }
    el = fixture.nativeElement;
    fixture.detectChanges();
    await settle(fixture);
    ctrl.expectOne(`/api/projects/${P}/conversations`).flush([{ id: 'c1', projectId: P, name: 'Avoirs',
      createdAt: '2026-11-09T09:00:00Z', updatedAt: '2026-11-09T09:00:00Z' }]);
    await settle(fixture);
    ctrl.expectOne(`/api/projects/${P}/conversations/suggestions`).flush(suggestions);
    await settle(fixture);
  }

  function type(text: string): void {
    const area = el.querySelector('textarea')!;
    area.value = text;
    area.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function enter(): void {
    el.querySelector('textarea')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
  }

  it('accueil : question, étapes, réponse en flux, puis sources citées', async () => {
    await open();
    expect(el.querySelector('h1')?.textContent).toContain('Conversation');
    expect(el.textContent).toContain('Posez une question sur le projet Aurora');
    // les exemples viennent de la connaissance du projet
    expect(el.querySelector('.examples')?.textContent).toContain('AUR-31 « Lettrage manuel »');
    expect(el.querySelector('nav[aria-labelledby="threads-title"]')?.textContent).toContain('Avoirs');

    type('Comment sont émis les avoirs ?');
    enter();
    await settle(fixture);
    ctrl.expectOne({ method: 'POST', url: `/api/projects/${P}/conversations` })
      .flush({ id: 'c1', projectId: P, name: 'Nouvelle conversation', createdAt: 'x', updatedAt: 'x' });
    await settle(fixture);
    const send = ctrl.expectOne({ method: 'POST', url: '/api/conversations/c1/messages' });
    expect(send.request.body).toEqual({ text: 'Comment sont émis les avoirs ?' });
    expect(send.request.headers.get('X-Sw-Silent')).toBe('1');
    send.flush({ userMessageId: 'u1', turnId: 'a1' });
    await settle(fixture);
    ctrl.match(`/api/projects/${P}/conversations`).forEach((r) => r.flush([]));
    expect(opened).toEqual(['/api/conversations/c1/turns/a1/events']);
    expect(el.querySelector('textarea')!.value).toBe('');
    expect(el.querySelector('.question')?.textContent).toContain('Comment sont émis les avoirs ?');
    expect(el.textContent).toContain('L’agent cherche dans le projet');
    expect(el.textContent).toContain('Arrêter');

    emit({ type: 'step', step: { ordinal: 1, query: 'émission des avoirs', kinds: [], hits: 6 } });
    emit({ type: 'delta', text: 'Par le service ' });
    emit({ type: 'delta', text: 'Avoirs [1]' });
    fixture.detectChanges();
    await settle(fixture);
    expect(el.querySelector('.steps summary')?.textContent).toContain('1 recherche');
    expect(el.querySelector('.steps')?.textContent).toContain('« émission des avoirs » : 6 éléments trouvés');
    expect(el.querySelector('.note')?.textContent).toContain('Par le service Avoirs');
    expect(el.querySelector('.announce')?.textContent).toContain('Recherche : émission des avoirs, 6 éléments');
    // le fil n'est pas annoncé fragment par fragment
    expect(el.querySelector('[role="log"]')?.getAttribute('aria-live')).toBe('off');

    emit({ type: 'done', state: 'COMPLETE', historyTruncated: false });
    await settle(fixture);
    ctrl.expectOne('/api/conversations/c1').flush(detail());
    await settle(fixture);
    const note = el.querySelector('.note')!;
    expect(note.querySelector('a[href="http://jira/browse/FACT-7"]')?.textContent).toBe('[1]');
    expect(note.querySelector('a[href="/sessions/s9/versions/2"]')).toBeTruthy();
    const sources = el.querySelectorAll('.sources li');
    expect(sources.length).toBe(2);
    expect(sources[0].textContent).toContain('Ticket');
    expect(sources[1].querySelector('a')?.getAttribute('href')).toBe('/sessions/s9/versions/2');
    expect(el.querySelector('.announce')?.textContent).toContain('Réponse de l’agent : Par le service Avoirs');
    expect(el.textContent).toContain('Envoyer');
  });

  it('reprise : réponse en cours suivie, arrêt, réponse incomplète réessayée', async () => {
    await open('c1');
    const running = detail();
    running.messages[1] = { ...running.messages[1], text: 'Début', state: 'RUNNING', sources: [] };
    ctrl.expectOne('/api/conversations/c1').flush(running);
    await settle(fixture);
    expect(opened).toEqual(['/api/conversations/c1/turns/a1/events']);

    el.querySelector('textarea')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    await settle(fixture);
    ctrl.expectOne({ method: 'DELETE', url: '/api/conversations/c1/turns/a1' }).flush(null);
    emit({ type: 'done', state: 'INCOMPLETE', historyTruncated: false });
    await settle(fixture);
    const stopped = detail();
    stopped.messages[1] = { ...stopped.messages[1], text: 'Début', state: 'INCOMPLETE', sources: [] };
    ctrl.expectOne('/api/conversations/c1').flush(stopped);
    await settle(fixture);
    expect(el.textContent).toContain('Réponse incomplète');

    (Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Réessayer') as HTMLButtonElement).click();
    await settle(fixture);
    ctrl.expectOne({ method: 'POST', url: '/api/conversations/c1/messages/u1/retry' }).flush({ userMessageId: 'u1', turnId: 'a1' });
    await settle(fixture);
    expect(opened.length).toBe(2);
  });

  it('projet sans connaissance indexée : aucun exemple', async () => {
    suggestions = [];
    await open();
    expect(el.querySelector('.examples')).toBeNull();
    expect(el.textContent).not.toContain('par exemple');
  });

  it('refus annoncés : accord refusé, Copilot indisponible ; conversation d’un autre projet', async () => {
    await open();
    consent = false;
    type('Une question ?');
    enter();
    await settle(fixture);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Sans votre accord');
    ctrl.expectNone({ method: 'POST', url: `/api/projects/${P}/conversations` });

    consent = true;
    enter();
    await settle(fixture);
    ctrl.expectOne({ method: 'POST', url: `/api/projects/${P}/conversations` })
      .flush({ id: 'c2', projectId: P, name: 'Nouvelle conversation', createdAt: 'x', updatedAt: 'x' });
    await settle(fixture);
    ctrl.expectOne({ method: 'POST', url: '/api/conversations/c2/messages' }).flush(
      { code: 'COPILOT_NOT_CONNECTED', userMessage: 'Connectez GitHub Copilot dans la configuration.', action: 'OPEN_SETTINGS' },
      { status: 503, statusText: 'Unavailable' },
    );
    await settle(fixture);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Connectez GitHub Copilot');
    expect(el.querySelector('textarea')!.value).toBe('Une question ?');
  });

  it('rédiger : brouillon relu, point retiré, spécification démarrée et liée', async () => {
    await open('c1');
    ctrl.expectOne('/api/conversations/c1').flush(detail({ sessions: [{ sessionId: 's8', name: 'Annuler un avoir' }] }));
    await settle(fixture);
    expect(el.querySelector('.linked a')?.getAttribute('href')).toBe('/sessions/s8');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    button('Rédiger une spécification').click();
    await settle(fixture);
    ctrl.expectOne({ method: 'POST', url: '/api/conversations/c1/need-draft' }).flush({
      need: 'Permettre au gestionnaire d’annuler un avoir émis par erreur.',
      points: [
        { theme: 'ACTORS', question: 'Qui annule ?', answer: 'Le gestionnaire.', basis: 'USER', sourceRef: null },
        { theme: 'BUSINESS_RULES', question: 'Délai ?', answer: '48 heures.', basis: 'SOURCE', sourceRef: 'FACT-7' },
      ],
    });
    await settle(fixture);
    const draft = el.querySelector('sw-need-draft')!;
    expect(document.activeElement?.id).toBe('draft-title');
    expect(draft.querySelectorAll('fieldset').length).toBe(2);
    expect(draft.querySelector('legend')?.textContent).toContain('Acteurs');
    expect(draft.textContent).toContain('Établi par la source FACT-7');
    expect(el.querySelector('.announce')?.textContent).toContain('Brouillon du besoin prêt');

    (draft.querySelectorAll('fieldset')[1].querySelector('button') as HTMLButtonElement).click();
    const answer = draft.querySelector('#a-0') as HTMLTextAreaElement;
    answer.value = 'Le gestionnaire ou son responsable.';
    answer.dispatchEvent(new Event('input'));
    await settle(fixture);
    button('Démarrer la spécification').click();
    await settle(fixture);
    const specify = ctrl.expectOne({ method: 'POST', url: '/api/conversations/c1/specifications' });
    expect(specify.request.body).toEqual({
      need: 'Permettre au gestionnaire d’annuler un avoir émis par erreur.',
      points: [{ theme: 'ACTORS', question: 'Qui annule ?', answer: 'Le gestionnaire ou son responsable.', basis: 'USER',
        sourceRef: null }],
    });
    specify.flush({ id: 's9' });
    await settle(fixture);
    expect(navigate).toHaveBeenCalledWith(['/sessions', 's9']);
  });

  it('une source sans adresse : l’appel mène à sa note, focalisée ; l’annonce est sans Markdown', async () => {
    await open('c1');
    const d = detail();
    d.messages[1] = { ...d.messages[1], text: '**Écart** : 7 jours [1].',
      sources: [{ n: 1, kind: 'ATTACHMENT', ref: 'joint:parking.html', title: 'Parking', url: null }] };
    ctrl.expectOne('/api/conversations/c1').flush(d);
    await settle(fixture);
    // dans le document : le focus n'est possible que sur un élément affiché
    document.body.appendChild(el);
    const call = el.querySelector('.note a') as HTMLAnchorElement;
    expect(call.getAttribute('href')).toBe('#source-a1-1');
    call.click();
    await settle(fixture);
    expect(document.activeElement?.id).toBe('source-a1-1');
    el.remove();
  });

  function button(label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label) as HTMLButtonElement;
  }
});
