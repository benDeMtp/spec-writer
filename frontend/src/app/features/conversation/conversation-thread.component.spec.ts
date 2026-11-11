import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Router, provideRouter } from '@angular/router';
import { provideMarkdown } from 'ngx-markdown';
import { TURN_STREAM } from '../../core/conversations/conversations.api';
import { ConversationMessage } from '../../core/conversations/conversations.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { MENTION_DELAYS } from '../../shared/ui/mention.directive';
import { ConversationThreadComponent } from './conversation-thread.component';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

function agent(
  id: string,
  ordinal: number,
  version: number | null,
  text = 'Réponse.',
): ConversationMessage {
  return {
    id,
    ordinal,
    role: 'AGENT',
    text,
    state: 'COMPLETE',
    historyTruncated: false,
    createdAt: 'x',
    steps: [],
    sources: [],
    specificationVersion: version,
  };
}

function user(id: string, ordinal: number): ConversationMessage {
  return { ...agent(id, ordinal, null, 'Question ?'), role: 'USER' };
}

describe('ConversationThreadComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<ConversationThreadComponent>;
  let el: HTMLElement;
  let opened: string[];

  beforeEach(() => {
    opened = [];
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
          useValue: (url: string) => {
            opened.push(url);
            return { close: () => undefined };
          },
        },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ConversationThreadComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    el = fixture.nativeElement;
  });

  it('repère « Contexte : version N » devant la première réponse de chaque version', async () => {
    fixture.componentRef.setInput('conversationId', 'c1');
    fixture.componentInstance.messages.set([
      user('u1', 1),
      agent('a1', 2, 3),
      user('u2', 3),
      agent('a2', 4, 3),
      user('u3', 5),
      agent('a3', 6, 4),
    ]);
    await settle(fixture);

    const marks = Array.from(el.querySelectorAll('.context-mark')).map((m) =>
      m.textContent?.trim(),
    );
    expect(marks).toEqual([
      'Contexte : version 3 de la spécification',
      'Contexte : version 4 de la spécification',
    ]);
  });

  it('une citation vers l’outil est d’abord proposée à l’écran, puis ouverte par le routeur', async () => {
    const handled: string[] = [];
    fixture.componentRef.setInput('conversationId', 'c1');
    fixture.componentRef.setInput('internalLink', (href: string) => {
      handled.push(href);
      return href.includes('#');
    });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.componentInstance.messages.set([
      user('u1', 1),
      {
        ...agent('a1', 2, 1, 'Voir [1] et [2].'),
        sources: [
          {
            n: 1,
            kind: 'SPECIFICATION',
            ref: 'spec:s1@v1#UC-01',
            title: 'UC-01',
            url: '/sessions/s1/versions/1#UC-01',
          },
          {
            n: 2,
            kind: 'SPECIFICATION',
            ref: 'spec:s2@v2',
            title: 'Autre',
            url: '/sessions/s2/versions/2',
          },
        ],
      },
    ]);
    await settle(fixture);

    el.querySelector<HTMLAnchorElement>('.note a[href="/sessions/s1/versions/1#UC-01"]')!.click();
    el.querySelector<HTMLAnchorElement>('.note a[href="/sessions/s2/versions/2"]')!.click();

    expect(handled).toEqual(['/sessions/s1/versions/1#UC-01', '/sessions/s2/versions/2']);
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith('/sessions/s2/versions/2');
  });

  it('crée la conversation au premier envoi, puis suit le tour', async () => {
    let prepared = 0;
    fixture.componentRef.setInput('prepare', async () => {
      prepared++;
      fixture.componentRef.setInput('conversationId', 'c9');
      return 'c9';
    });
    let sent = 0;
    fixture.componentInstance.sent.subscribe(() => sent++);
    await settle(fixture);
    fixture.componentInstance.prefill('Que dit la spécification ?');
    await settle(fixture);
    el.querySelector<HTMLFormElement>('form.composer')!.dispatchEvent(new Event('submit'));
    await settle(fixture);

    ctrl
      .expectOne({ method: 'POST', url: '/api/conversations/c9/messages' })
      .flush({ userMessageId: 'u1', turnId: 'a1' });
    await settle(fixture);
    expect(prepared).toBe(1);
    expect(sent).toBe(1);
    expect(opened).toEqual(['/api/conversations/c9/turns/a1/events']);
    expect(fixture.componentInstance.running()).toBe(true);
  });
});
