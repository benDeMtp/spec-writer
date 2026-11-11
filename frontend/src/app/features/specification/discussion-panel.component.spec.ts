import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { provideMarkdown } from 'ngx-markdown';
import { TURN_STREAM } from '../../core/conversations/conversations.api';
import { ConversationMessage, TurnEvent } from '../../core/conversations/conversations.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { MENTION_DELAYS } from '../../shared/ui/mention.directive';
import { DiscussionPanelComponent } from './discussion-panel.component';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

function message(
  id: string,
  ordinal: number,
  role: 'USER' | 'AGENT',
  state: 'COMPLETE' | 'RUNNING' = 'COMPLETE',
): ConversationMessage {
  return {
    id,
    ordinal,
    role,
    text: role === 'USER' ? 'Qui émet l’avoir ?' : 'Le service comptable.',
    state,
    historyTruncated: false,
    createdAt: 'x',
    steps: [],
    sources: [],
    specificationVersion: role === 'AGENT' ? 3 : null,
  };
}

function detail(id: string, messages: ConversationMessage[]) {
  return {
    id,
    projectId: 'p1',
    name: 'Discussion : Avoirs',
    createdAt: 'x',
    updatedAt: 'x',
    sessions: [],
    memoryUntil: null,
    specification: { sessionId: 's1', name: 'Avoirs' },
    messages,
  };
}

describe('DiscussionPanelComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<DiscussionPanelComponent>;
  let el: HTMLElement;
  let opened: string[];
  let listeners: ((e: TurnEvent) => void)[];

  beforeEach(() => {
    opened = [];
    listeners = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideMarkdown(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: MENTION_DELAYS, useValue: { suggest: 0, resolve: 0 } },
        { provide: ConsentGate, useValue: { ensure: async () => true } },
        {
          provide: TURN_STREAM,
          useValue: (url: string, onEvent: (e: TurnEvent) => void) => {
            opened.push(url);
            listeners.push(onEvent);
            return { close: () => undefined };
          },
        },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DiscussionPanelComponent);
    fixture.componentRef.setInput('sessionId', 's1');
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.componentRef.setInput('displayedVersion', 3);
    el = fixture.nativeElement;
    document.body.appendChild(el);
  });

  afterEach(() => el.remove());

  async function answer(
    conversationId: string,
    version: number,
    messages: ConversationMessage[],
    fresh = false,
  ) {
    const open = ctrl.expectOne({ method: 'POST', url: '/api/sessions/s1/discussion' });
    expect(open.request.body).toEqual({ fresh });
    open.flush({ conversationId, specificationName: 'Avoirs', version });
    await settle(fixture);
    ctrl.expectOne(`/api/conversations/${conversationId}`).flush(detail(conversationId, messages));
    await settle(fixture);
    ctrl.match((r) => r.url.includes('/attachments')).forEach((r) => r.flush([]));
    await settle(fixture);
  }

  it('reprend la discussion de la spécification, avec son contexte, et place le focus sur la saisie', async () => {
    await settle(fixture);
    await answer('c1', 3, [message('u1', 1, 'USER'), message('a1', 2, 'AGENT')]);

    expect(el.textContent).toContain('Contexte : Avoirs, version 3');
    expect(el.querySelector('.older')).toBeNull();
    expect(el.textContent).toContain('Le service comptable.');
    expect(el.textContent).toContain('Contexte : version 3 de la spécification');
    expect(document.activeElement?.id).toBe('composer-text');
  });

  it('signale qu’une version plus ancienne est affichée', async () => {
    fixture.componentRef.setInput('displayedVersion', 2);
    await settle(fixture);
    await answer('c1', 3, []);

    expect(el.querySelector('.older')?.textContent).toContain(
      'Vous consultez la version 2. La discussion porte sur la version 3, la plus récente.',
    );
  });

  it('« Nouvelle discussion » repart d’un fil vide', async () => {
    await settle(fixture);
    await answer('c1', 3, [message('u1', 1, 'USER'), message('a1', 2, 'AGENT')]);

    const fresh = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Nouvelle discussion'),
    )!;
    fresh.click();
    await settle(fixture);
    await answer('c2', 3, [], true);

    expect(el.textContent).not.toContain('Le service comptable.');
    expect(el.querySelector('.announce')?.textContent).toContain('Nouvelle discussion ouverte.');
  });

  it('reprend le suivi d’une réponse encore en cours', async () => {
    await settle(fixture);
    await answer('c1', 3, [message('u1', 1, 'USER'), message('a1', 2, 'AGENT', 'RUNNING')]);

    expect(opened).toEqual(['/api/conversations/c1/turns/a1/events']);
  });

  it('dit pourquoi la discussion n’a pas pu s’ouvrir', async () => {
    await settle(fixture);
    ctrl.expectOne({ method: 'POST', url: '/api/sessions/s1/discussion' }).flush(
      {
        code: 'SPECIFICATION_LOCKED',
        userMessage: 'Cette spécification est implémentée : elle ne se discute plus.',
      },
      { status: 409, statusText: 'Conflict' },
    );
    await settle(fixture);

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('elle ne se discute plus');
  });

  function button(label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label)!;
  }

  it('« Proposer une nouvelle version » attend une discussion, puis suit la rédaction jusqu’à la relecture', async () => {
    await settle(fixture);
    await answer('c1', 3, []);
    expect(button('Proposer une nouvelle version').disabled).toBe(true);
    expect(el.querySelector('#propose-hint')?.textContent).toContain('Discutez d’abord');

    fixture.componentInstance['messages'].set([
      message('u1', 1, 'USER'),
      message('a1', 2, 'AGENT'),
    ]);
    await settle(fixture);
    const proposed: unknown[] = [];
    fixture.componentInstance.proposed.subscribe((p) => proposed.push(p));
    const drafting: boolean[] = [];
    fixture.componentInstance.drafting.subscribe((d) => drafting.push(d));
    button('Proposer une nouvelle version').click();
    await settle(fixture);
    ctrl
      .expectOne({ method: 'POST', url: '/api/conversations/c1/proposals' })
      .flush({ turnId: 't9' });
    await settle(fixture);

    expect(opened).toContain('/api/conversations/c1/turns/t9/events');
    const send = listeners.at(-1)!;
    send({ type: 'started', at: '2026-11-11T09:00:00Z' });
    send({ type: 'stage', stage: 'REVISE' });
    send({ type: 'reasoning', text: 'Je reprends l’annulation établie.' });
    await settle(fixture);
    expect(el.querySelector('.thinking .activity')?.textContent).toBe(
      'L’agent rédige une nouvelle version',
    );
    expect(el.querySelector('.thinking')?.textContent).toContain(
      'Je reprends l’annulation établie.',
    );
    expect(button('Proposer une nouvelle version').disabled).toBe(true);
    // fermer perdrait la rédaction : « Fermer » attend qu'elle soit finie
    expect(button('Fermer').disabled).toBe(true);
    expect(el.querySelector('#drafting-hint')?.textContent).toContain('quand elle sera prête');
    expect(drafting).toEqual([true]);

    send({
      type: 'done',
      state: 'COMPLETE',
      historyTruncated: false,
      durationMs: 20,
      proposalId: 'p1',
    });
    await settle(fixture);
    ctrl
      .expectOne('/api/sessions/s1/proposals/p1')
      .flush({ proposalId: 'p1', baseVersion: 3, proposed: { title: 'Avoirs' }, unifiedDiff: '' });
    await settle(fixture);

    expect(proposed).toHaveLength(1);
    expect(el.querySelector('.thinking')).toBeNull();
    expect(button('Fermer').disabled).toBe(false);
    expect(drafting).toEqual([true, false]);
    // pendant la relecture, aucune autre proposition
    fixture.componentRef.setInput('reviewing', true);
    await settle(fixture);
    expect(el.querySelector('#propose-hint')?.textContent).toContain('attend votre relecture');
  });

  it('dit quand la discussion n’a établi aucun changement, et pourquoi', async () => {
    await settle(fixture);
    await answer('c1', 3, [message('u1', 1, 'USER'), message('a1', 2, 'AGENT')]);

    button('Proposer une nouvelle version').click();
    await settle(fixture);
    ctrl
      .expectOne({ method: 'POST', url: '/api/conversations/c1/proposals' })
      .flush({ turnId: 't9' });
    await settle(fixture);
    listeners.at(-1)!({
      type: 'done',
      state: 'COMPLETE',
      historyTruncated: false,
      durationMs: 20,
      unchanged: true,
      reason: 'La discussion n’a posé que des questions',
    });
    await settle(fixture);

    expect(el.querySelector('.unchanged')?.textContent).toContain(
      // la raison du modèle reçoit son point final avant la phrase suivante
      'Aucun changement établi par la discussion : La discussion n’a posé que des questions. Précisez',
    );
    expect(button('Proposer une nouvelle version').disabled).toBe(false);
  });

  it('une rédaction en échec est annoncée', async () => {
    await settle(fixture);
    await answer('c1', 3, [message('u1', 1, 'USER'), message('a1', 2, 'AGENT')]);

    button('Proposer une nouvelle version').click();
    await settle(fixture);
    ctrl
      .expectOne({ method: 'POST', url: '/api/conversations/c1/proposals' })
      .flush({ turnId: 't9' });
    await settle(fixture);
    listeners.at(-1)!({
      type: 'error',
      code: 'LLM_INVALID_OUTPUT',
      userMessage: 'La réponse de l’IA était inexploitable. Réessayez.',
    });
    await settle(fixture);

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('inexploitable');
  });
});
