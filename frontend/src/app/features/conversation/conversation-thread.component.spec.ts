import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Router, provideRouter } from '@angular/router';
import { provideMarkdown } from 'ngx-markdown';
import { TURN_STREAM } from '../../core/conversations/conversations.api';
import {
  ConversationMessage,
  PerimeterView,
  TurnEvent,
} from '../../core/conversations/conversations.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { MENTION_DELAYS } from '../../shared/ui/mention.directive';
import { ConversationThreadComponent, demoteHeadings } from './conversation-thread.component';

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
  let emit: (event: TurnEvent) => void;

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
          useValue: (url: string, onEvent: (event: TurnEvent) => void) => {
            opened.push(url);
            emit = onEvent;
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

  it('suit les nouveaux messages tant qu’on est en bas, et propose d’y revenir sinon', async () => {
    document.body.appendChild(el);
    const scrolled: Element[] = [];
    const original = Element.prototype.scrollIntoView;
    Element.prototype.scrollIntoView = function (this: Element) {
      scrolled.push(this);
    };
    let endTop = 0;
    const rect = (top: number) => ({ top }) as DOMRect;
    try {
      fixture.componentRef.setInput('conversationId', 'c1');
      fixture.componentInstance.messages.set([user('u1', 1), agent('a1', 2, null)]);
      await settle(fixture);
      const end = el.querySelector<HTMLElement>('.end')!;
      end.getBoundingClientRect = () => rect(endTop);
      el.querySelector<HTMLElement>('.dock')!.getBoundingClientRect = () => rect(500);
      expect(scrolled.at(-1)).toBe(end);

      // un contenu qui change de hauteur sans geste de la personne ne coupe pas le suivi
      endTop = 900;
      document.dispatchEvent(new Event('scroll'));
      await settle(fixture);
      expect(el.textContent).not.toContain('Aller au dernier message');

      // un clic sur un lien, qui défile pour le montrer, n'est pas un geste de défilement
      el.querySelector('textarea')!.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }));
      document.dispatchEvent(new Event('scroll'));
      await settle(fixture);
      expect(el.textContent).not.toContain('Aller au dernier message');

      // la personne remonte : le fil ne bouge plus, le bouton apparaît
      document.dispatchEvent(new Event('wheel'));
      document.dispatchEvent(new Event('scroll'));
      scrolled.length = 0;
      fixture.componentInstance.messages.update((l) => [...l, user('u2', 3)]);
      await settle(fixture);
      expect(scrolled).toEqual([]);
      const toEnd = Array.from(el.querySelectorAll('button')).find(
        (b) => b.textContent?.trim().startsWith('Aller au dernier message'),
      )!;
      expect(toEnd).toBeTruthy();
      // il compte les messages arrivés depuis que la personne a remonté le fil
      expect(toEnd.textContent?.trim()).toBe('Aller au dernier message (1 nouveau)');

      // le bouton ramène en bas et rend la main au champ de saisie
      toEnd.click();
      endTop = 450;
      await settle(fixture);
      expect(scrolled).toContain(end);
      expect(document.activeElement?.id).toBe('composer-text');
      expect(el.textContent).not.toContain('Aller au dernier message');

      // redescendre à la main reprend aussi le suivi
      endTop = 900;
      document.dispatchEvent(new Event('wheel'));
      document.dispatchEvent(new Event('scroll'));
      await settle(fixture);
      expect(el.textContent).toContain('Aller au dernier message');
      endTop = 560;
      document.dispatchEvent(new Event('scroll'));
      await settle(fixture);
      expect(el.textContent).not.toContain('Aller au dernier message');
    } finally {
      Element.prototype.scrollIntoView = original;
      el.remove();
    }
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

  it('affiche l’analyse du périmètre et mène une citation de module à sa ligne', async () => {
    const analysis = (qualified: boolean): PerimeterView => ({
      ordinal: 1,
      terms: ['ContractStatus'],
      rejectedTerms: [],
      roots: [{ rootId: 'r1', label: 'rv', state: 'SCANNED' }],
      state: 'COMPLETE',
      incompleteReason: null,
      filesScanned: 12,
      filesSkipped: 0,
      skippedExamples: [],
      durationMs: 300,
      qualified,
      summary: { impacted: qualified ? 1 : 0, toCheck: 0, notImpacted: 0, unqualified: qualified ? 0 : 1 },
      modules: [
        {
          id: 'r1/libs/common-contract',
          rootLabel: 'rv',
          path: 'libs/common-contract',
          name: 'common-contract',
          kind: 'MAVEN',
          occurrences: 1,
          isNew: false,
          qualification: qualified ? 'IMPACTED' : 'UNQUALIFIED',
          justification: null,
          reasons: [],
          architecture: null,
          archStatus: null,
          samples: [],
        },
      ],
      architectureOnly: [],
      unavailable: null,
    });
    fixture.componentRef.setInput('conversationId', 'c1');
    fixture.componentInstance.messages.set([user('u1', 1)]);
    fixture.componentInstance.resume('c1', 'a1');
    fixture.componentInstance.messages.update((list) => [
      ...list,
      { ...agent('a1', 2, null, ''), state: 'RUNNING' },
    ]);
    emit({ type: 'perimeter', analysis: analysis(false) });
    emit({ type: 'perimeter', analysis: analysis(true) });
    await settle(fixture);
    expect(el.querySelectorAll('sw-perimeter-step')).toHaveLength(1);
    expect(el.querySelector('sw-perimeter-step h3')?.textContent).toContain('Impactés (1)');

    fixture.componentInstance.messages.update((list) =>
      list.map((m) =>
        m.id === 'a1'
          ? {
              ...m,
              state: 'COMPLETE' as const,
              text: 'Touché : [1].',
              sources: [
                { n: 1, kind: 'MODULE' as const, ref: 'module:r1/libs/common-contract', title: 'common-contract', url: null },
              ],
            }
          : m,
      ),
    );
    await settle(fixture);
    const link = el.querySelector<HTMLAnchorElement>('.note a')!;
    expect(link.getAttribute('href')).toBe('#perimeter-a1-1-0');
    link.click();
    expect(document.activeElement?.id).toBe('perimeter-a1-1-0');
  });
});

describe('demoteHeadings', () => {
  it('place les titres d’une réponse sous ceux de la page, sans toucher au code', () => {
    const text = '## Ce qui change\ntexte\n# Titre\n```bash\n# commentaire\n```\n#### Détail\n#pas un titre';
    expect(demoteHeadings(text)).toBe(
      '### Ce qui change\ntexte\n### Titre\n```bash\n# commentaire\n```\n##### Détail\n#pas un titre',
    );
  });
});
