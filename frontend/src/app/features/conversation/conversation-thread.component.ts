import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  OnDestroy,
  afterRenderEffect,
  computed,
  inject,
  input,
  model,
  output,
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
  SourceKind,
  ThinkingPart,
  ThinkingStage,
  TurnEvent,
} from '../../core/conversations/conversations.model';
import { isProblem } from '../../core/http/problem';
import { AttachmentsComponent } from '../../shared/ui/attachments.component';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { ElapsedComponent, formatDuration } from '../../shared/ui/elapsed.component';
import { MentionDirective } from '../../shared/ui/mention.directive';
import { PerimeterStepComponent, perimeterAnchor } from './perimeter-step.component';

export const MAX_MESSAGE = 8_000;

/** En deçà de cette distance (px) entre la fin du fil et le champ de saisie, le fil suit les messages. */
export const FOLLOW_TOLERANCE = 80;

const STAGES: Record<ThinkingStage, string> = {
  MEMORY: 'Résumé des échanges anciens',
  PLAN: 'Choix d’une recherche',
  ANSWER: 'Rédaction de la réponse',
  REVISE: 'Rédaction d’une nouvelle version',
};

const KINDS: Record<SourceKind, string> = {
  TICKET: 'Ticket',
  DOC_SECTION: 'Page',
  ADR: 'ADR',
  ARCH_ELEMENT: "Élément d'architecture",
  SPECIFICATION: 'Spécification',
  ATTACHMENT: 'Document joint',
  DISCUSSION: 'Discussion',
  MODULE: 'Module',
};

/**
 * Fil d'une conversation avec l'agent (010), partagé par l'écran Conversation et la discussion d'une
 * spécification (011) : messages, étapes de recherche, réflexion, réponse en flux, sources citées,
 * champ de saisie avec mentions et documents joints, arrêt et nouvel essai. Le fil n'est pas relu
 * par les lecteurs d'écran : une région séparée annonce l'étape en cours puis la réponse complète.
 */
@Component({
  selector: 'sw-conversation-thread',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AttachmentsComponent,
    ElapsedComponent,
    FormsModule,
    MatButtonModule,
    MatProgressBarModule,
    MarkdownComponent,
    MentionDirective,
    PerimeterStepComponent,
    RouterLink,
  ],
  template: `
    <div role="log" aria-live="off" aria-label="Échanges avec l’agent">
      <ol class="log">
        @for (m of messages(); track m.id) {
          @if (m.role === 'USER') {
            <li class="question">
              <p>{{ m.text }}</p>
            </li>
          } @else {
            @if (contextMark($index); as v) {
              <li class="context-mark">
                <p>Contexte : version {{ v }} de la spécification</p>
              </li>
            }
            <li class="answer" [attr.aria-busy]="m.state === 'RUNNING'">
              @if (m.state === 'RUNNING' || m.thinking?.parts?.length) {
                <details class="thinking" [class.live]="m.state === 'RUNNING'">
                  <summary>
                    <span class="activity">{{
                      m.state === 'RUNNING' ? activity(m) : 'Réflexion de l’agent'
                    }}</span>
                    @if (m.state === 'RUNNING') {
                      <sw-elapsed [since]="turnStart()" />
                    } @else if (m.thinking) {
                      <span class="took">{{ took(m.thinking.durationMs) }}</span>
                    }
                  </summary>
                  <div class="reasoning">
                    <div>
                      @for (part of shownParts(m); track $index) {
                        <h3>{{ stageLabel(part.stage) }}</h3>
                        @if (part.text) {
                          <p>{{ part.text }}</p>
                        } @else {
                          <p class="hint">
                            Le modèle n’a pas encore partagé sa réflexion pour cette étape.
                          </p>
                        }
                      } @empty {
                        <p class="hint">Le modèle n’a pas encore partagé sa réflexion.</p>
                      }
                      <p class="hint about">
                        Résumé de sa réflexion rédigé par le modèle, souvent en anglais.
                      </p>
                    </div>
                  </div>
                </details>
              } @else if (m.thinking) {
                <p class="took-alone">Répondu en {{ took(m.thinking.durationMs) }}</p>
              }
              @if (m.steps.length) {
                <details class="steps" [open]="m.state === 'RUNNING'">
                  <summary>
                    {{ m.steps.length }} recherche{{ m.steps.length > 1 ? 's' : '' }} dans la
                    connaissance du projet
                  </summary>
                  <ol>
                    @for (s of m.steps; track s.ordinal) {
                      <li>
                        « {{ s.query }} » : {{ s.hits }} élément{{ s.hits > 1 ? 's' : '' }} trouvé{{
                          s.hits > 1 ? 's' : ''
                        }}
                      </li>
                    }
                  </ol>
                </details>
              }
              @for (p of m.perimeters ?? []; track p.ordinal) {
                <sw-perimeter-step
                  [analysis]="p"
                  [messageId]="m.id"
                  [live]="m.state === 'RUNNING'"
                />
              }
              @if (m.text) {
                <article class="note">
                  <markdown [data]="rendered(m)" />
                </article>
              } @else if (m.state === 'RUNNING' && compacting() === m.id) {
                <div class="memory">
                  <p>Pour garder le fil, l’agent résume les échanges les plus anciens.</p>
                  <mat-progress-bar mode="indeterminate" aria-hidden="true" />
                </div>
              }
              @if (m.state === 'INCOMPLETE') {
                <p class="incomplete">
                  Réponse incomplète.
                  <button mat-button type="button" [disabled]="running()" (click)="retry(m)">
                    Réessayer
                  </button>
                </p>
              }
              @if (m.historyTruncated) {
                <p class="hint">
                  Les échanges les plus anciens n’ont pas tous été transmis à l’agent.
                </p>
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
          @if (m.ordinal && m.ordinal === memoryUntil() && !$last) {
            <li class="memory-mark">
              <p>Les échanges ci-dessus sont transmis à l’agent sous forme de résumé.</p>
            </li>
          }
        }
      </ol>
      <div #end class="end"></div>
    </div>

    <ng-content />

    <p class="sw-visually-hidden announce" aria-live="polite">{{ announce() }}</p>
    @if (problem()) {
      <p class="problem" role="alert">{{ problem() }}</p>
    }

    <div #dock class="dock">
    @if (!following() && messages().length) {
      <button mat-stroked-button type="button" class="to-end" (click)="scrollToEnd(true)">
        Aller au dernier message
      </button>
    }
    <ng-content select="[swComposerTop]" />
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
        [placeholder]="placeholder()"
        autocomplete="off"
        aria-describedby="composer-help"
        (keydown)="onKey($event)"
      ></textarea>
      @if (conversationId(); as cid) {
        <sw-attachments
          [projectId]="projectId()"
          [conversationId]="cid"
          [fields]="composerFields()"
        />
      }
      <div class="bar">
        <span id="composer-help" class="hint">
          Entrée envoie, Maj+Entrée va à la ligne. {{ text().length }} / {{ max }}
        </span>
        <span class="actions">
          <ng-content select="[swComposerAction]" />
          @if (running()) {
            <button mat-stroked-button type="button" (click)="stop()">Arrêter</button>
          } @else {
            <button mat-flat-button type="submit" [disabled]="!text().trim()">Envoyer</button>
          }
        </span>
      </div>
    </form>
    </div>
  `,
  styles: `
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
    .thinking {
      margin: 0 0 0.75rem;
      color: var(--sw-ink-soft);
      font-size: var(--sw-step--1);
    }
    .thinking summary {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0 0.6rem;
      cursor: pointer;
    }
    .thinking summary::before {
      content: '▸';
      transition: transform 120ms ease-out;
    }
    .thinking[open] summary::before {
      transform: rotate(90deg);
    }
    .thinking summary::-webkit-details-marker {
      display: none;
    }
    .thinking.live .activity {
      color: var(--sw-ink);
    }
    .thinking.live .activity::after {
      content: '…';
    }
    .thinking sw-elapsed,
    .took {
      color: var(--sw-ink-soft);
    }
    /* la réflexion arrive par le bas : la colonne inversée garde la fin visible sans script */
    .reasoning {
      display: flex;
      flex-direction: column-reverse;
      max-height: 18rem;
      overflow-y: auto;
      margin: 0.5rem 0 0;
      padding: 0.25rem 0.9rem 0.5rem;
      border-left: 2px solid var(--sw-rule);
    }
    .reasoning h3 {
      margin: 0.6rem 0 0.2rem;
      font-size: inherit;
      font-weight: 600;
    }
    .reasoning p {
      margin: 0;
      white-space: pre-wrap;
      overflow-wrap: anywhere;
      line-height: 1.55;
    }
    .reasoning .about {
      margin-top: 0.6rem;
      font-style: italic;
    }
    .took-alone {
      margin: 0 0 0.5rem;
      color: var(--sw-ink-soft);
      font-size: var(--sw-step--1);
    }
    @media (prefers-reduced-motion: reduce) {
      .thinking summary::before {
        transition: none;
      }
    }
    .memory {
      max-width: 36rem;
      color: var(--sw-ink-soft);
    }
    .memory p {
      margin: 0 0 0.4rem;
    }
    @media (prefers-reduced-motion: reduce) {
      .memory mat-progress-bar {
        display: none;
      }
    }
    .memory-mark,
    .context-mark {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin: 1.5rem 0;
      color: var(--sw-ink-soft);
      font-size: var(--sw-step--1);
    }
    .memory-mark::before,
    .memory-mark::after,
    .context-mark::before,
    .context-mark::after {
      content: '';
      flex: 1;
      border-top: 1px dashed var(--sw-rule);
    }
    .memory-mark p,
    .context-mark p {
      margin: 0;
      text-align: center;
    }
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
    /* le champ de saisie, et ce que l'écran y ajoute (proposition d'une version), restent visibles */
    .to-end {
      position: absolute;
      bottom: calc(100% + 0.5rem);
      left: 50%;
      transform: translateX(-50%);
      background: var(--sw-sheet);
      box-shadow: 0 1px 4px color-mix(in srgb, var(--sw-ink) 25%, transparent);
    }
    .dock {
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
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      margin-top: 0.4rem;
      font-size: var(--sw-step--1);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 0.5rem;
    }
  `,
})
export class ConversationThreadComponent implements OnDestroy {
  readonly conversationId = input<string | null>(null);
  readonly projectId = input<string | null>(null);
  /** Les messages jusqu'à ce numéro d'ordre sont transmis à l'agent sous forme de résumé. */
  readonly memoryUntil = input<number | null>(null);
  readonly placeholder = input('Votre question sur le projet…');
  /**
   * Crée la conversation au premier envoi, quand il n'y en a pas encore ; renvoie son identifiant,
   * ou rien pour ne pas envoyer.
   */
  readonly prepare = input<(() => Promise<string | null>) | null>(null);
  /** Lien de citation vers l'outil : vrai si l'écran l'a traité lui-même (section affichée). */
  readonly internalLink = input<((href: string) => boolean) | null>(null);
  readonly messages = model<ConversationMessage[]>([]);
  /** Un message est parti. */
  readonly sent = output<void>();
  /** Fin d'un tour : la conversation relue. */
  readonly finished = output<ConversationDetail>();

  private readonly api = inject(ConversationsApi);
  private readonly consent = inject(ConsentGate);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly composer = viewChild<ElementRef<HTMLTextAreaElement>>('composer');
  private readonly end = viewChild<ElementRef<HTMLElement>>('end');
  private readonly dock = viewChild<ElementRef<HTMLElement>>('dock');
  /**
   * Le fil suit les nouveaux messages et la réponse en flux tant que la personne est en bas ; il
   * cesse quand elle remonte, et reprend quand elle redescend, envoie un message ou demande le
   * dernier message.
   */
  protected readonly following = signal(true);
  private readonly onScroll = (): void => this.following.set(this.atEnd());

  protected readonly max = MAX_MESSAGE;
  protected readonly text = signal('');
  protected readonly announce = signal('');
  protected readonly problem = signal('');
  /** La question en cours : ses mentions @ comptent avec les documents joints. */
  protected readonly composerFields = computed(() => [
    { field: 'NEED' as const, text: this.text() },
  ]);
  /** Tour dont l'agent résume les échanges anciens (compactage de la mémoire). */
  protected readonly compacting = signal<string | null>(null);
  /** Début du tour en cours (heure du serveur quand elle est connue), pour le chronomètre. */
  protected readonly turnStart = signal<number | null>(null);
  /** Tâche en cours de l'agent, qui donne son titre au panneau de réflexion. */
  protected readonly stage = signal<ThinkingStage | null>(null);
  readonly running = computed(() => this.messages().some((m) => m.state === 'RUNNING'));
  private stream: TurnStream | null = null;

  constructor() {
    // tout défilement compte : celui de la page, ou celui du panneau qui contient le fil
    document.addEventListener('scroll', this.onScroll, { capture: true, passive: true });
    inject(DestroyRef).onDestroy(() =>
      document.removeEventListener('scroll', this.onScroll, { capture: true }),
    );
    afterRenderEffect(() => {
      this.messages();
      this.stage();
      this.compacting();
      if (untracked(this.following)) {
        this.reveal();
      }
    });
  }

  /** Va au dernier message et reprend le suivi ; `focus` rend la main au champ de saisie. */
  scrollToEnd(focus = false): void {
    this.following.set(true);
    this.reveal();
    if (focus) {
      this.focus();
    }
  }

  /** Vrai si la fin du fil est visible au-dessus du champ de saisie, à la tolérance près. */
  private atEnd(): boolean {
    const end = this.end()?.nativeElement;
    const dock = this.dock()?.nativeElement;
    if (!end || !dock) {
      return true;
    }
    return end.getBoundingClientRect().top - dock.getBoundingClientRect().top <= FOLLOW_TOLERANCE;
  }

  /** Amène la fin du fil juste au-dessus du champ de saisie, qui reste collé en bas. */
  private reveal(): void {
    const end = this.end()?.nativeElement;
    if (!end || typeof end.scrollIntoView !== 'function') {
      return;
    }
    end.style.scrollMarginBottom = `${(this.dock()?.nativeElement.offsetHeight ?? 0) + 8}px`;
    end.scrollIntoView({ block: 'end' });
  }

  ngOnDestroy(): void {
    this.stream?.close();
  }

  /** Place un texte dans le champ, puis lui donne le focus (exemple de question). */
  prefill(text: string): void {
    this.text.set(text);
    this.focus();
  }

  focus(): void {
    this.composer()?.nativeElement.focus();
  }

  /** Annonce aux lecteurs d'écran. */
  say(text: string): void {
    this.announce.set(text);
  }

  /** Problème affiché sous le fil ; vide pour l'effacer. */
  fail(text: string): void {
    this.problem.set(text);
  }

  /** Ferme le flux en cours ; le fil repart vide. */
  reset(): void {
    this.stream?.close();
    this.stream = null;
    this.problem.set('');
    this.compacting.set(null);
    this.stage.set(null);
  }

  /** Se rattache à un tour en cours, après un rechargement : l'heure de début arrive avec le flux. */
  resume(conversationId: string, turnId: string): void {
    this.turnStart.set(null);
    this.follow(conversationId, turnId);
  }

  protected kind(s: CitedSource): string {
    return KINDS[s.kind];
  }

  /** Adresse dans l'outil (version d'une spécification) : ouverte par le routeur. */
  protected internal(s: CitedSource): string | null {
    return s.url?.startsWith('/') ? s.url : null;
  }

  /** Version de la spécification discutée, devant le premier message de l'agent qui la reçoit. */
  protected contextMark(index: number): number | null {
    const list = this.messages();
    const version = list[index]?.specificationVersion ?? null;
    if (version === null || list[index].role !== 'AGENT') {
      return null;
    }
    const before = list
      .slice(0, index)
      .filter((m) => m.role === 'AGENT' && m.specificationVersion != null)
      .at(-1);
    return before?.specificationVersion === version ? null : version;
  }

  /** Titre du panneau de réflexion pendant le tour : ce que fait l'agent à cet instant. */
  protected activity(m: ConversationMessage): string {
    const last = m.steps.at(-1);
    switch (this.stage()) {
      case 'MEMORY':
        return 'L’agent résume les échanges anciens';
      case 'PLAN':
        return last
          ? `L’agent examine les résultats de « ${last.query} »`
          : 'L’agent analyse la question';
      case 'ANSWER':
        return 'L’agent rédige sa réponse';
      case 'REVISE':
        return 'L’agent rédige une nouvelle version';
      default:
        return 'L’agent lit la question';
    }
  }

  protected stageLabel(stage: ThinkingStage): string {
    return STAGES[stage];
  }

  /** Étapes déjà réfléchies, et l'étape en cours même sans réflexion encore. */
  protected shownParts(m: ConversationMessage): ThinkingPart[] {
    const parts = m.thinking?.parts ?? [];
    return parts.filter((p, i) => p.text || (m.state === 'RUNNING' && i === parts.length - 1));
  }

  protected took(ms: number): string {
    return formatDuration(ms);
  }

  protected sourceId(m: ConversationMessage, s: CitedSource): string {
    return `source-${m.id}-${s.n}`;
  }

  /** Ligne du module dans l'étape « Analyse du périmètre » du message, pour une source MODULE. */
  private moduleAnchor(m: ConversationMessage, s: CitedSource): string | null {
    if (s.kind !== 'MODULE') {
      return null;
    }
    const id = s.ref.replace(/^module:/, '');
    for (const p of [...(m.perimeters ?? [])].reverse()) {
      const position = p.modules.findIndex((x) => x.id === id);
      if (position >= 0) {
        return perimeterAnchor(m.id, p.ordinal, position);
      }
    }
    return null;
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
      const target = source.url ?? `#${this.moduleAnchor(m, source) ?? this.sourceId(m, source)}`;
      return `[[${n}]](${target} "${source.title.replace(/"/g, '')}")`;
    });
  }

  /**
   * Lien d'une note : dans l'outil par le routeur (ou par l'écran, pour une section affichée),
   * ailleurs dans un nouvel onglet. Écouté sur l'hôte : Entrée sur un lien produit aussi un clic.
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
      if (!this.internalLink()?.(href)) {
        void this.router.navigateByUrl(href);
      }
    } else {
      window.open(href, '_blank', 'noopener');
    }
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

  protected async send(): Promise<void> {
    const text = this.text().trim();
    if (!text || this.running() || !this.projectId()) {
      return;
    }
    this.problem.set('');
    if (!(await this.consent.ensure('COPILOT'))) {
      this.problem.set('Sans votre accord, la question n’est pas envoyée à GitHub Copilot.');
      return;
    }
    try {
      const conversationId = this.conversationId() ?? (await this.prepare()?.()) ?? null;
      if (!conversationId) {
        return;
      }
      this.turnStart.set(Date.now());
      const started = await this.api.send(conversationId, text);
      this.following.set(true);
      this.text.set('');
      this.messages.update((list) => [
        ...list,
        message(started.userMessageId, 'USER', text, 'COMPLETE'),
        message(started.turnId, 'AGENT', '', 'RUNNING'),
      ]);
      this.follow(conversationId, started.turnId);
      this.sent.emit();
    } catch (e) {
      this.problem.set(reason(e));
    }
  }

  protected async stop(): Promise<void> {
    const conversationId = this.conversationId();
    const turn = this.messages().find((m) => m.state === 'RUNNING');
    if (conversationId && turn) {
      await this.api.stop(conversationId, turn.id).catch(() => undefined);
    }
  }

  protected async retry(m: ConversationMessage): Promise<void> {
    const conversationId = this.conversationId();
    const list = this.messages();
    const question = list[list.indexOf(m) - 1];
    if (!conversationId || !question || this.running()) {
      return;
    }
    this.problem.set('');
    try {
      this.turnStart.set(Date.now());
      const started = await this.api.retry(conversationId, question.id);
      this.patch(started.turnId, (x) => ({
        ...x,
        text: '',
        state: 'RUNNING',
        steps: [],
        sources: [],
        thinking: null,
      }));
      this.follow(conversationId, started.turnId);
    } catch (e) {
      this.problem.set(reason(e));
    }
  }

  private follow(conversationId: string, turnId: string): void {
    this.stream?.close();
    this.stage.set(null);
    this.announce.set('L’agent cherche dans le projet.');
    this.stream = this.api.follow(conversationId, turnId, (event) =>
      this.onEvent(conversationId, turnId, event),
    );
  }

  private onEvent(conversationId: string, turnId: string, event: TurnEvent): void {
    switch (event.type) {
      case 'started':
        this.turnStart.set(Date.parse(event.at));
        break;
      case 'stage':
        this.stage.set(event.stage);
        this.patch(turnId, (m) => ({
          ...m,
          thinking: {
            parts: [...(m.thinking?.parts ?? []), { stage: event.stage, text: '' }],
            durationMs: 0,
          },
        }));
        if (event.stage === 'ANSWER') {
          this.announce.set('L’agent rédige sa réponse.');
        }
        break;
      case 'reasoning':
        this.patch(turnId, (m) => {
          const parts = m.thinking?.parts ?? [];
          const last = parts.at(-1);
          return last
            ? {
                ...m,
                thinking: {
                  parts: [...parts.slice(0, -1), { ...last, text: last.text + event.text }],
                  durationMs: 0,
                },
              }
            : m;
        });
        break;
      case 'step':
        this.patch(turnId, (m) =>
          m.steps.some((s) => s.ordinal === event.step.ordinal)
            ? m
            : { ...m, steps: [...m.steps, event.step] },
        );
        this.announce.set(
          `Recherche : ${event.step.query}, ${event.step.hits} élément${event.step.hits > 1 ? 's' : ''}.`,
        );
        break;
      case 'perimeter':
        this.patch(turnId, (m) => ({
          ...m,
          perimeters: [
            ...(m.perimeters ?? []).filter((p) => p.ordinal !== event.analysis.ordinal),
            event.analysis,
          ].sort((a, b) => a.ordinal - b.ordinal),
        }));
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
    this.stage.set(null);
    const detail = await this.api.get(conversationId).catch(() => null);
    if (detail && this.conversationId() === conversationId) {
      this.messages.set(detail.messages);
      const answer = detail.messages.find((m) => m.id === turnId);
      this.announce.set(
        answer?.state === 'COMPLETE'
          ? `Réponse de l’agent : ${plain(answer.text)}`
          : 'La réponse de l’agent est incomplète.',
      );
      this.finished.emit(detail);
    }
  }

  private patch(id: string, change: (m: ConversationMessage) => ConversationMessage): void {
    this.messages.update((list) => list.map((m) => (m.id === id ? change(m) : m)));
  }
}

function message(
  id: string,
  role: 'USER' | 'AGENT',
  text: string,
  state: 'COMPLETE' | 'RUNNING',
): ConversationMessage {
  return {
    id,
    ordinal: 0,
    role,
    text,
    state,
    historyTruncated: false,
    createdAt: new Date().toISOString(),
    steps: [],
    sources: [],
    thinking: null,
  };
}

/** Texte annoncé : sans les marques Markdown, que le lecteur d'écran lirait telles quelles. */
function plain(markdown: string): string {
  return markdown
    .replace(/[*_`#>]+/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

export function reason(
  e: unknown,
  fallback = 'Le message n’a pas pu être envoyé. Réessayez.',
): string {
  if (e instanceof HttpErrorResponse && isProblem(e.error)) {
    return e.error.userMessage;
  }
  return fallback;
}
