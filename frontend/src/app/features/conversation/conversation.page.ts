import { HttpErrorResponse } from '@angular/common/http';
import { Location } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';
import { MarkdownComponent } from 'ngx-markdown';
import { ConversationsApi, TurnStream } from '../../core/conversations/conversations.api';
import {
  CitedSource,
  ConversationDetail,
  ConversationMessage,
  ConversationSummary,
  NeedDraft,
  SourceKind,
  TurnEvent,
} from '../../core/conversations/conversations.model';
import { isProblem } from '../../core/http/problem';
import { ProjectsStore } from '../../core/projects/projects.store';
import { AttachmentsComponent } from '../../shared/ui/attachments.component';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { MentionDirective } from '../../shared/ui/mention.directive';
import { ConversationListComponent } from './conversation-list.component';
import { NeedDraftComponent } from './need-draft.component';

export const MAX_MESSAGE = 8_000;

const KINDS: Record<SourceKind, string> = {
  TICKET: 'Ticket',
  DOC_SECTION: 'Page',
  ADR: 'ADR',
  ARCH_ELEMENT: "Élément d'architecture",
  SPECIFICATION: 'Spécification',
  ATTACHMENT: 'Document joint',
};

/**
 * Conversation avec l'agent, écran d'accueil (010, US1, US4) : la réponse est une note rédigée, qui
 * appelle ses sources par leur numéro et les liste en notes ; les recherches de l'agent forment un
 * petit journal au-dessus. Le fil n'est pas relu par les lecteurs d'écran : une région séparée
 * annonce l'étape en cours puis la réponse complète (FR-022).
 */
@Component({
  selector: 'sw-conversation-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AttachmentsComponent,
    ConversationListComponent,
    FormsModule,
    MatButtonModule,
    MatProgressBarModule,
    MarkdownComponent,
    MentionDirective,
    NeedDraftComponent,
    RouterLink,
  ],
  template: `
    <div class="conversation">
      <nav class="threads" aria-labelledby="threads-title">
        <sw-conversation-list
          [threads]="threads()"
          [currentId]="detail()?.id ?? null"
          (created)="startNew()"
          (changed)="onListChanged($event)"
        />
      </nav>

      <section class="thread" aria-labelledby="thread-title">
        <header class="head">
          <h1 id="thread-title">{{ detail()?.name ?? 'Conversation' }}</h1>
          @if (detail() && messages().length && !draft()) {
            <button mat-stroked-button type="button" [disabled]="running() || drafting()" (click)="proposeNeed()">
              {{ drafting() ? 'L’agent prépare le besoin…' : 'Rédiger une spécification' }}
            </button>
          }
        </header>
        @if (detail()?.sessions?.length) {
          <p class="linked">
            Spécifications issues de cette conversation :
            @for (s of detail()!.sessions; track s.sessionId; let last = $last) {
              <a [routerLink]="['/sessions', s.sessionId]">{{ s.name }}</a>{{ last ? '' : ', ' }}
            }
          </p>
        }

        @if (!messages().length) {
          <div class="welcome">
            <p class="lead">
              Posez une question sur le projet{{ projectName() ? ' ' + projectName() : '' }}. L’agent cherche dans ses
              tickets, sa documentation, ses ADR et ses spécifications, puis répond en citant ses sources.
            </p>
            @if (examples().length) {
              <p class="hint">D’après ce que l’agent sait du projet, par exemple :</p>
              <ul class="examples">
                @for (e of examples(); track e) {
                  <li><button mat-button type="button" (click)="useExample(e)">{{ e }}</button></li>
                }
              </ul>
            }
          </div>
        }

        <div role="log" aria-live="off" aria-label="Échanges avec l’agent">
          <ol class="log">
            @for (m of messages(); track m.id) {
              @if (m.role === 'USER') {
                <li class="question">
                  <p>{{ m.text }}</p>
                </li>
              } @else {
                <li class="answer" [attr.aria-busy]="m.state === 'RUNNING'">
                  @if (m.steps.length) {
                    <details class="steps" [open]="m.state === 'RUNNING'">
                      <summary>
                        {{ m.steps.length }} recherche{{ m.steps.length > 1 ? 's' : '' }} dans la connaissance du projet
                      </summary>
                      <ol>
                        @for (s of m.steps; track s.ordinal) {
                          <li>« {{ s.query }} » : {{ s.hits }} élément{{ s.hits > 1 ? 's' : '' }} trouvé{{ s.hits > 1 ? 's' : '' }}</li>
                        }
                      </ol>
                    </details>
                  }
                  @if (m.text) {
                    <article class="note">
                      <markdown [data]="rendered(m)" />
                    </article>
                  } @else if (m.state === 'RUNNING') {
                    @if (compacting() === m.id) {
                      <div class="working memory">
                        <p>L’agent résume les échanges les plus anciens pour garder le fil de la conversation…</p>
                        <mat-progress-bar mode="indeterminate" aria-hidden="true" />
                      </div>
                    } @else {
                      <p class="working">{{ m.steps.length ? 'L’agent rédige sa réponse…' : 'L’agent cherche dans le projet…' }}</p>
                    }
                  }
                  @if (m.state === 'INCOMPLETE') {
                    <p class="incomplete">
                      Réponse incomplète.
                      <button mat-button type="button" [disabled]="running()" (click)="retry(m)">Réessayer</button>
                    </p>
                  }
                  @if (m.historyTruncated) {
                    <p class="hint">Les échanges les plus anciens n’ont pas tous été transmis à l’agent.</p>
                  }
                  @if (m.sources.length) {
                    <ol class="sources" aria-label="Sources de la réponse">
                      @for (s of m.sources; track s.n) {
                        <li [value]="s.n" [id]="sourceId(m, s)" tabindex="-1">
                          <span class="kind">{{ kind(s) }}</span>
                          @if (internal(s); as path) {
                            <a [routerLink]="path">{{ s.title }}</a>
                          } @else if (s.url) {
                            <a [href]="s.url" target="_blank" rel="noopener noreferrer"
                              >{{ s.title }}<span class="sw-visually-hidden"> (nouvel onglet)</span></a
                            >
                          } @else {
                            {{ s.title }}
                          }
                        </li>
                      }
                    </ol>
                  }
                </li>
              }
              @if (m.ordinal && m.ordinal === detail()?.memoryUntil && !$last) {
                <li class="memory-mark">
                  <p>Les échanges ci-dessus sont transmis à l’agent sous forme de résumé.</p>
                </li>
              }
            }
          </ol>
        </div>

        @if (draft(); as d) {
          <sw-need-draft
            [conversationId]="detail()!.id"
            [draft]="d"
            (started)="openSpecification($event)"
            (cancelled)="draft.set(null)"
          />
        }

        <p class="sw-visually-hidden announce" aria-live="polite">{{ announce() }}</p>
        @if (problem()) {
          <p class="problem" role="alert">{{ problem() }}</p>
        }

        <form class="composer" (ngSubmit)="send()" novalidate>
          <label for="composer-text" class="sw-visually-hidden">Votre question</label>
          <textarea
            #composer
            id="composer-text"
            name="text"
            rows="3"
            [swMention]="projectId()"
            [(ngModel)]="text"
            [maxlength]="max"
            placeholder="Votre question sur le projet…"
            autocomplete="off"
            aria-describedby="composer-help"
            (keydown)="onKey($event)"
          ></textarea>
          @if (detail(); as d) {
            <sw-attachments [projectId]="projectId()" [conversationId]="d.id" [fields]="composerFields()" />
          }
          <div class="bar">
            <span id="composer-help" class="hint">
              Entrée envoie, Maj+Entrée va à la ligne. {{ text().length }} / {{ max }}
            </span>
            @if (running()) {
              <button mat-stroked-button type="button" (click)="stop()">Arrêter</button>
            } @else {
              <button mat-flat-button type="submit" [disabled]="!text().trim()">Envoyer</button>
            }
          </div>
        </form>
      </section>
    </div>
  `,
  styles: `
    .conversation {
      display: grid;
      grid-template-columns: 15rem minmax(0, 1fr);
      gap: 2rem;
      align-items: start;
    }
    .thread {
      max-width: calc(var(--sw-measure) + 2rem);
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      justify-content: space-between;
      gap: 0.5rem 1rem;
      margin: 0 0 1.25rem;
    }
    .linked {
      margin: -0.75rem 0 1.25rem;
      color: var(--sw-ink-soft);
    }
    h1 {
      margin: 0;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-2);
      font-weight: 600;
    }
    .lead {
      margin: 0 0 0.75rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-1);
      line-height: 1.55;
    }
    .examples {
      list-style: none;
      margin: 0 0 1.5rem;
      padding: 0;
    }
    .examples button {
      /* un exemple cite un titre réel : il passe à la ligne au lieu de déborder */
      height: auto;
      padding-block: 0.4rem;
      line-height: 1.4;
      text-align: start;
      white-space: normal;
    }
    .log {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .question {
      margin: 1.75rem 0 0.75rem;
      padding-left: 0.9rem;
      border-left: 3px solid var(--sw-pencil);
    }
    .question p {
      margin: 0;
      font-weight: 600;
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }
    .log :focus-visible {
      /* le champ de saisie reste collé en bas : il ne cache pas l'élément qui a le focus */
      scroll-margin-bottom: 12rem;
    }
    .answer {
      margin: 0 0 1rem;
    }
    .steps {
      margin: 0 0 0.75rem;
      color: var(--sw-ink-soft);
      font-size: var(--sw-step--1);
    }
    .steps summary {
      cursor: pointer;
    }
    .steps ol {
      margin: 0.4rem 0 0;
      padding-left: 1.5rem;
    }
    .note {
      padding: 1.25rem 1.5rem;
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-sheet);
      font-family: var(--sw-font-doc);
      line-height: 1.7;
      overflow-wrap: anywhere;
    }
    .note :first-child {
      margin-top: 0;
    }
    .note :last-child {
      margin-bottom: 0;
    }
    .note a {
      color: var(--sw-pencil);
      font-family: var(--sw-font-ui);
      font-size: 0.8em;
      vertical-align: super;
      line-height: 0;
      text-decoration: none;
    }
    .note a:hover,
    .note a:focus-visible {
      text-decoration: underline;
    }
    .sources {
      margin: 0.6rem 0 0;
      padding-left: 2rem;
      font-size: var(--sw-step--1);
      color: var(--sw-ink-soft);
    }
    .sources li:focus-visible {
      outline: 2px solid var(--sw-pencil);
      outline-offset: 2px;
    }
    .sources .kind {
      margin-right: 0.4rem;
    }
    .memory {
      max-width: 36rem;
    }
    .memory p {
      margin: 0 0 0.4rem;
    }
    @media (prefers-reduced-motion: reduce) {
      .memory mat-progress-bar {
        display: none;
      }
    }
    .memory-mark {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin: 1.5rem 0;
      color: var(--sw-ink-soft);
      font-size: var(--sw-step--1);
    }
    .memory-mark::before,
    .memory-mark::after {
      content: '';
      flex: 1;
      border-top: 1px dashed var(--sw-rule);
    }
    .memory-mark p {
      margin: 0;
      text-align: center;
    }
    .working,
    .hint {
      color: var(--sw-ink-soft);
    }
    .incomplete {
      color: var(--sw-outcome);
    }
    .problem {
      padding: 0.5rem 0.75rem;
      border-left: 4px solid var(--mat-sys-error, #b3261e);
    }
    .composer {
      position: sticky;
      bottom: 0;
      margin-top: 1.5rem;
      padding: 0.75rem 0 1rem;
      background: var(--sw-paper);
    }
    textarea {
      box-sizing: border-box;
      width: 100%;
      padding: 0.75rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
      background: var(--sw-sheet);
      color: var(--sw-ink);
      font: inherit;
      resize: vertical;
    }
    .bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      margin-top: 0.4rem;
      font-size: var(--sw-step--1);
    }
    @media (max-width: 60rem) {
      .conversation {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
})
export class ConversationPage implements OnDestroy {
  /** Conversation ouverte (`/conversations/:cid`) ; aucune à l'accueil. */
  readonly cid = input<string | undefined>(undefined);

  private readonly api = inject(ConversationsApi);
  private readonly projects = inject(ProjectsStore);
  private readonly consent = inject(ConsentGate);
  private readonly location = inject(Location);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly composer = viewChild<ElementRef<HTMLTextAreaElement>>('composer');

  protected readonly max = MAX_MESSAGE;
  /** Exemples tirés de la connaissance du projet courant ; aucun tant que rien n'est indexé. */
  protected readonly examples = signal<string[]>([]);
  protected readonly projectId = this.projects.currentId;
  protected readonly projectName = computed(() => this.projects.current()?.name ?? '');
  protected readonly threads = signal<ConversationSummary[]>([]);
  protected readonly detail = signal<ConversationDetail | null>(null);
  protected readonly messages = signal<ConversationMessage[]>([]);
  protected readonly text = signal('');
  protected readonly announce = signal('');
  protected readonly problem = signal('');
  /** La question en cours : ses mentions @ comptent avec les documents joints. */
  protected readonly composerFields = computed(() => [{ field: 'NEED' as const, text: this.text() }]);
  protected readonly draft = signal<NeedDraft | null>(null);
  protected readonly drafting = signal(false);
  /** Tour dont l'agent résume les échanges anciens (compactage de la mémoire). */
  protected readonly compacting = signal<string | null>(null);
  protected readonly running = computed(() => this.messages().some((m) => m.state === 'RUNNING'));
  private stream: TurnStream | null = null;

  constructor() {
    effect(() => {
      const projectId = this.projectId();
      const cid = this.cid();
      untracked(() => void this.load(projectId, cid ?? null));
    });
  }

  ngOnDestroy(): void {
    this.stream?.close();
  }

  protected kind(s: CitedSource): string {
    return KINDS[s.kind];
  }

  /** Adresse dans l'outil (version d'une spécification) : ouverte par le routeur. */
  protected internal(s: CitedSource): string | null {
    return s.url?.startsWith('/') ? s.url : null;
  }

  protected sourceId(m: ConversationMessage, s: CitedSource): string {
    return `source-${m.id}-${s.n}`;
  }

  /**
   * Les appels de note `[n]` deviennent des liens : vers la source quand elle a une adresse, sinon
   * vers sa note sous la réponse (document joint, élément d'architecture).
   */
  protected rendered(m: ConversationMessage): string {
    if (!m.sources.length) {
      return m.text;
    }
    return m.text.replace(/\[(\d{1,3})\](?!\()/g, (all, n: string) => {
      const source = m.sources.find((s) => s.n === Number(n));
      if (!source) {
        return all;
      }
      const target = source.url ?? `#${this.sourceId(m, source)}`;
      return `[[${n}]](${target} "${source.title.replace(/"/g, '')}")`;
    });
  }

  /**
   * Lien d'une note : dans l'outil par le routeur, ailleurs dans un nouvel onglet. Écouté sur l'hôte :
   * Entrée sur un lien produit aussi un clic, le clavier suit le même chemin.
   */
  @HostListener('click', ['$event'])
  protected openLink(event: MouseEvent): void {
    const link = (event.target as HTMLElement | null)?.closest('.note a');
    const href = link?.getAttribute('href');
    if (!href) {
      return;
    }
    event.preventDefault();
    if (href.startsWith('#')) {
      const note = this.host.nativeElement.querySelector<HTMLElement>(`[id="${href.slice(1)}"]`);
      note?.focus();
    } else if (href.startsWith('/')) {
      void this.router.navigateByUrl(href);
    } else {
      window.open(href, '_blank', 'noopener');
    }
  }

  protected useExample(example: string): void {
    this.text.set(example);
    this.composer()?.nativeElement.focus();
  }

  protected onKey(event: KeyboardEvent): void {
    const target = event.target as HTMLElement;
    // la liste des mentions @ ouverte garde Entrée pour choisir un document
    if (event.defaultPrevented || target.getAttribute('aria-controls')) {
      return;
    }
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      void this.send();
    } else if (event.key === 'Escape' && this.running()) {
      event.preventDefault();
      void this.stop();
    }
  }

  protected async onListChanged(change: { deleted?: string }): Promise<void> {
    const projectId = this.projectId();
    if (change.deleted && change.deleted === this.detail()?.id) {
      await this.startNew();
    } else if (projectId) {
      await this.refreshThreads(projectId);
      const current = this.detail();
      const renamed = this.threads().find((t) => t.id === current?.id);
      if (current && renamed) {
        this.detail.set({ ...current, name: renamed.name });
      }
    }
  }

  protected async startNew(): Promise<void> {
    this.stream?.close();
    this.detail.set(null);
    this.messages.set([]);
    this.problem.set('');
    this.location.replaceState('/');
    await this.router.navigateByUrl('/');
    this.composer()?.nativeElement.focus();
  }

  protected async send(): Promise<void> {
    const text = this.text().trim();
    const projectId = this.projectId();
    if (!text || this.running() || !projectId) {
      return;
    }
    this.problem.set('');
    if (!(await this.consent.ensure('COPILOT'))) {
      this.problem.set('Sans votre accord, la question n’est pas envoyée à GitHub Copilot.');
      return;
    }
    try {
      let conversation = this.detail();
      if (!conversation) {
        const created = await this.api.create(projectId);
        conversation = { ...created, messages: [], sessions: [], memoryUntil: null };
        this.detail.set(conversation);
        this.location.replaceState(`/conversations/${created.id}`);
      }
      const started = await this.api.send(conversation.id, text);
      this.text.set('');
      this.messages.update((list) => [
        ...list,
        message(started.userMessageId, 'USER', text, 'COMPLETE'),
        message(started.turnId, 'AGENT', '', 'RUNNING'),
      ]);
      this.follow(conversation.id, started.turnId);
      void this.refreshThreads(projectId);
    } catch (e) {
      this.problem.set(reason(e));
    }
  }

  protected async stop(): Promise<void> {
    const conversation = this.detail();
    const turn = this.messages().find((m) => m.state === 'RUNNING');
    if (conversation && turn) {
      await this.api.stop(conversation.id, turn.id).catch(() => undefined);
    }
  }

  protected async retry(m: ConversationMessage): Promise<void> {
    const conversation = this.detail();
    const list = this.messages();
    const question = list[list.indexOf(m) - 1];
    if (!conversation || !question || this.running()) {
      return;
    }
    this.problem.set('');
    try {
      const started = await this.api.retry(conversation.id, question.id);
      this.patch(started.turnId, (x) => ({ ...x, text: '', state: 'RUNNING', steps: [], sources: [] }));
      this.follow(conversation.id, started.turnId);
    } catch (e) {
      this.problem.set(reason(e));
    }
  }

  /** L'agent propose un besoin et des points établis, que l'utilisateur relit avant de démarrer. */
  protected async proposeNeed(): Promise<void> {
    const conversation = this.detail();
    if (!conversation || this.running() || this.drafting()) {
      return;
    }
    this.problem.set('');
    if (!(await this.consent.ensure('COPILOT'))) {
      this.problem.set('Sans votre accord, la conversation n’est pas envoyée à GitHub Copilot.');
      return;
    }
    this.drafting.set(true);
    this.announce.set('L’agent prépare le besoin.');
    try {
      this.draft.set(await this.api.needDraft(conversation.id));
      this.announce.set('Brouillon du besoin prêt à relire.');
    } catch (e) {
      this.problem.set(reason(e, 'Le besoin n’a pas pu être préparé. Réessayez.'));
    } finally {
      this.drafting.set(false);
    }
  }

  protected async openSpecification(sessionId: string): Promise<void> {
    this.draft.set(null);
    await this.router.navigate(['/sessions', sessionId]);
  }

  private follow(conversationId: string, turnId: string): void {
    this.stream?.close();
    this.announce.set('L’agent cherche dans le projet.');
    this.stream = this.api.follow(conversationId, turnId, (event) => this.onEvent(conversationId, turnId, event));
  }

  private onEvent(conversationId: string, turnId: string, event: TurnEvent): void {
    switch (event.type) {
      case 'step':
        this.patch(turnId, (m) =>
          m.steps.some((s) => s.ordinal === event.step.ordinal) ? m : { ...m, steps: [...m.steps, event.step] },
        );
        this.announce.set(`Recherche : ${event.step.query}, ${event.step.hits} élément${event.step.hits > 1 ? 's' : ''}.`);
        break;
      case 'delta':
        this.patch(turnId, (m) => ({ ...m, text: m.text + event.text }));
        break;
      case 'sources':
        break;
      case 'memory':
        this.compacting.set(event.phase === 'STARTED' ? turnId : null);
        this.announce.set(
          event.phase === 'STARTED'
            ? 'L’agent résume les échanges les plus anciens.'
            : event.phase === 'DONE'
              ? 'Échanges anciens résumés.'
              : 'Le résumé des échanges anciens a échoué : l’agent répond sans eux.',
        );
        break;
      case 'error':
        this.problem.set(event.userMessage);
        void this.finish(conversationId, turnId);
        break;
      default:
        void this.finish(conversationId, turnId);
    }
  }

  /** Fin du tour : la conversation enregistrée fait foi (texte, sources et leurs adresses). */
  private async finish(conversationId: string, turnId: string): Promise<void> {
    this.stream?.close();
    this.stream = null;
    this.compacting.set(null);
    const detail = await this.api.get(conversationId).catch(() => null);
    if (detail && this.detail()?.id === conversationId) {
      this.detail.set(detail);
      this.messages.set(detail.messages);
      const answer = detail.messages.find((m) => m.id === turnId);
      this.announce.set(
        answer?.state === 'COMPLETE'
          ? `Réponse de l’agent : ${plain(answer.text)}`
          : 'La réponse de l’agent est incomplète.',
      );
    }
  }

  private patch(id: string, change: (m: ConversationMessage) => ConversationMessage): void {
    this.messages.update((list) => list.map((m) => (m.id === id ? change(m) : m)));
  }

  private async load(projectId: string | null, cid: string | null): Promise<void> {
    this.stream?.close();
    this.stream = null;
    this.problem.set('');
    this.draft.set(null);
    this.examples.set([]);
    if (!projectId) {
      return;
    }
    await this.refreshThreads(projectId);
    void this.api.suggestions(projectId).then(
      (list) => this.examples.set(list),
      () => this.examples.set([]),
    );
    if (!cid) {
      this.detail.set(null);
      this.messages.set([]);
      return;
    }
    try {
      const detail = await this.api.get(cid);
      if (detail.projectId !== projectId) {
        // conversation d'un autre projet : l'accueil du projet courant
        await this.router.navigateByUrl('/');
        return;
      }
      this.detail.set(detail);
      this.messages.set(detail.messages);
      const running = detail.messages.find((m) => m.state === 'RUNNING');
      if (running) {
        this.follow(detail.id, running.id);
      }
    } catch {
      this.detail.set(null);
      this.messages.set([]);
      this.problem.set('Cette conversation n’existe plus : choisissez-en une autre dans la liste, ou posez une nouvelle question.');
    }
  }

  private async refreshThreads(projectId: string): Promise<void> {
    this.threads.set(await this.api.list(projectId).catch(() => []));
  }
}

function message(id: string, role: 'USER' | 'AGENT', text: string, state: 'COMPLETE' | 'RUNNING'): ConversationMessage {
  return { id, ordinal: 0, role, text, state, historyTruncated: false, createdAt: new Date().toISOString(), steps: [],
    sources: [] };
}

/** Texte annoncé : sans les marques Markdown, que le lecteur d'écran lirait telles quelles. */
function plain(markdown: string): string {
  return markdown.replace(/[*_`#>]+/g, '').replace(/\s+/g, ' ').trim();
}

function reason(e: unknown, fallback = 'Le message n’a pas pu être envoyé. Réessayez.'): string {
  if (e instanceof HttpErrorResponse && isProblem(e.error)) {
    return e.error.userMessage;
  }
  return fallback;
}
