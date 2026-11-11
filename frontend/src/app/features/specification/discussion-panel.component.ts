import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ConversationsApi, TurnStream } from '../../core/conversations/conversations.api';
import {
  ConversationDetail,
  ConversationMessage,
  DiscussionView,
  TurnEvent,
} from '../../core/conversations/conversations.model';
import { Proposal } from '../../core/jira/jira.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { ElapsedComponent } from '../../shared/ui/elapsed.component';
import { ConversationThreadComponent, reason } from '../conversation/conversation-thread.component';

/**
 * Discussion d'une spécification avec l'agent (011, US1, US3), à côté du document : contexte
 * (spécification et version discutées), fil de la conversation, nouvelle discussion. La discussion
 * porte toujours sur la dernière version ; le panneau le dit quand une version plus ancienne est
 * affichée. « Proposer une nouvelle version » fait réécrire la spécification d'après la discussion,
 * sans saisie (FR-008a) : la rédaction se suit ici, la relecture se fait à côté du document.
 */
@Component({
  selector: 'sw-discussion-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ConversationThreadComponent, ElapsedComponent, MatButtonModule],
  template: `
    <section class="discussion" aria-labelledby="discussion-title">
      <header class="top">
        <h2 id="discussion-title">Discussion avec l’IA</h2>
        <div class="tools">
          <button mat-button type="button" [disabled]="busy() || running()" (click)="fresh()">
            Nouvelle discussion
          </button>
          <button
            mat-button
            type="button"
            [disabled]="proposing()"
            [attr.aria-describedby]="proposing() ? 'drafting-hint' : null"
            (click)="closed.emit()"
          >
            Fermer
          </button>
        </div>
      </header>
      @if (proposing()) {
        <p id="drafting-hint" class="hint">
          L’agent rédige une nouvelle version : le panneau pourra se fermer quand elle sera prête.
        </p>
      }
      @if (view(); as d) {
        <p class="context">Contexte : {{ d.specificationName }}, version {{ d.version }}</p>
        @if (displayedVersion() !== d.version) {
          <p class="older" role="note">
            Vous consultez la version {{ displayedVersion() }}. La discussion porte sur la version
            {{ d.version }}, la plus récente.
          </p>
        }
      } @else if (!problem()) {
        <p class="hint" role="status">Ouverture de la discussion…</p>
      }
      @if (problem()) {
        <p class="problem" role="alert">{{ problem() }}</p>
      }
      <div class="propose">
        <button
          mat-stroked-button
          type="button"
          [disabled]="!canPropose()"
          [attr.aria-describedby]="proposeHint() ? 'propose-hint' : null"
          (click)="propose()"
        >
          Proposer une nouvelle version
        </button>
        @if (proposeHint(); as hint) {
          <p id="propose-hint" class="hint">{{ hint }}</p>
        }
      </div>
      @if (proposing()) {
        <details class="thinking live">
          <summary>
            <span class="activity">L’agent rédige une nouvelle version</span>
            <sw-elapsed [since]="proposingSince()" />
          </summary>
          <div class="reasoning">
            @if (proposingReasoning()) {
              <p>{{ proposingReasoning() }}</p>
            } @else {
              <p class="hint">Le modèle n’a pas encore partagé sa réflexion.</p>
            }
            <p class="hint">Résumé de sa réflexion rédigé par le modèle, souvent en anglais.</p>
          </div>
        </details>
      }
      @if (unchanged(); as why) {
        <p class="unchanged" role="note">
          Aucun changement établi par la discussion : {{ sentence(why) }} Précisez ce qui doit
          changer dans un message, puis proposez à nouveau.
        </p>
      }
      <sw-conversation-thread
        #thread
        [conversationId]="view()?.conversationId ?? null"
        [projectId]="projectId()"
        [memoryUntil]="memoryUntil()"
        [internalLink]="internalLink()"
        [(messages)]="messages"
        placeholder="Votre question sur la spécification…"
        (finished)="onFinished($event)"
      />
    </section>
  `,
  styles: `
    .discussion {
      padding: 1rem 1.25rem 0;
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-left: 3px solid var(--sw-pencil);
      border-radius: var(--sw-radius-sheet);
    }
    .top {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      justify-content: space-between;
      gap: 0.25rem 1rem;
    }
    h2 {
      margin: 0;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-1);
    }
    .tools {
      display: flex;
      gap: 0.25rem;
    }
    .context {
      margin: 0.25rem 0 0.5rem;
      font-weight: 600;
      overflow-wrap: anywhere;
    }
    .older {
      margin: 0 0 0.5rem;
      padding-left: 0.6rem;
      border-left: 3px solid var(--sw-outcome);
    }
    .hint {
      color: var(--sw-ink-soft);
    }
    .propose {
      margin: 0 0 0.75rem;
    }
    .propose .hint {
      margin: 0.25rem 0 0;
      font-size: var(--sw-step--1, 0.875rem);
    }
    .thinking {
      margin: 0 0 0.75rem;
      padding: 0.5rem 0.75rem;
      border-left: 3px solid var(--sw-pencil);
      background: var(--sw-paper, transparent);
    }
    .thinking summary {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      cursor: pointer;
      font-weight: 600;
    }
    .reasoning p {
      margin: 0.5rem 0 0;
      white-space: pre-wrap;
    }
    .unchanged {
      margin: 0 0 0.75rem;
      padding-left: 0.6rem;
      border-left: 3px solid var(--sw-outcome);
    }
    .problem {
      padding: 0.5rem 0.75rem;
      border-left: 4px solid var(--mat-sys-error, #b3261e);
    }
  `,
})
export class DiscussionPanelComponent implements OnInit, OnDestroy {
  readonly sessionId = input.required<string>();
  readonly projectId = input.required<string | null>();
  /** Version affichée à côté du panneau. */
  readonly displayedVersion = input.required<number>();
  /** Lien de citation vers l'outil : vrai si l'écran l'a traité (section du document affiché). */
  readonly internalLink = input<((href: string) => boolean) | null>(null);
  readonly closed = output<void>();
  /** Nouvelle version proposée d'après la discussion, à relire à côté du document. */
  readonly proposed = output<Proposal>();
  /** Rédaction en cours : la page ne ferme pas le panneau et prévient avant de quitter. */
  readonly drafting = output<boolean>();

  private readonly api = inject(ConversationsApi);
  private readonly consent = inject(ConsentGate);
  private stream: TurnStream | null = null;
  private readonly thread = viewChild.required<ConversationThreadComponent>('thread');

  protected readonly view = signal<DiscussionView | null>(null);
  protected readonly messages = signal<ConversationMessage[]>([]);
  protected readonly memoryUntil = signal<number | null>(null);
  protected readonly problem = signal('');
  protected readonly busy = signal(false);
  protected readonly proposing = signal(false);
  protected readonly proposingSince = signal<number | null>(null);
  protected readonly proposingReasoning = signal('');
  protected readonly unchanged = signal<string | null>(null);
  /** Une proposition attend sa relecture : la page la signale par `reviewing`. */
  readonly reviewing = input(false);

  protected running(): boolean {
    return this.messages().some((m) => m.state === 'RUNNING');
  }

  protected canPropose(): boolean {
    return !this.proposeHint() && !this.busy() && !this.proposing() && !!this.view();
  }

  /** Pourquoi « Proposer une nouvelle version » est indisponible ; vide sinon. */
  protected proposeHint(): string {
    if (this.reviewing()) {
      return 'Une proposition attend votre relecture à côté du document.';
    }
    if (this.running()) {
      return 'Disponible quand l’agent aura fini de répondre.';
    }
    if (!this.messages().some((m) => m.role === 'USER')) {
      return 'Discutez d’abord de ce qui doit changer : la proposition se fonde sur la discussion.';
    }
    return '';
  }

  /** Rend le focus à la saisie de la discussion. */
  focus(): void {
    this.thread().focus();
  }

  /** Après l'application d'une version : la discussion passe à cette version (FR-011). */
  async refresh(): Promise<void> {
    const current = await this.api.discussion(this.sessionId()).catch(() => null);
    if (current && current.conversationId === this.view()?.conversationId) {
      this.view.set(current);
      this.thread().say(`La discussion porte maintenant sur la version ${current.version}.`);
    }
  }

  protected async propose(): Promise<void> {
    const view = this.view();
    if (!view || !this.canPropose() || !(await this.consent.ensure('COPILOT'))) {
      return;
    }
    this.problem.set('');
    this.unchanged.set(null);
    this.proposingReasoning.set('');
    this.proposingSince.set(Date.now());
    this.proposing.set(true);
    this.drafting.emit(true);
    this.thread().say('L’agent rédige une nouvelle version de la spécification.');
    try {
      const { turnId } = await this.api.propose(view.conversationId);
      this.stream = this.api.follow(view.conversationId, turnId, (e) => void this.onProposal(e));
    } catch (e) {
      this.endProposal();
      this.problem.set(reason(e, 'La proposition n’a pas pu démarrer. Réessayez.'));
    }
  }

  private async onProposal(event: TurnEvent): Promise<void> {
    switch (event.type) {
      case 'started':
        this.proposingSince.set(Date.parse(event.at));
        return;
      case 'reasoning':
        this.proposingReasoning.update((t) => t + event.text);
        return;
      case 'done':
        this.endProposal();
        if (event.proposalId) {
          try {
            this.proposed.emit(await this.api.proposal(this.sessionId(), event.proposalId));
            this.thread().say('Nouvelle version proposée : relisez les différences.');
          } catch (e) {
            this.problem.set(reason(e, 'La proposition n’a pas pu être lue. Réessayez.'));
          }
        } else if (event.unchanged) {
          this.unchanged.set(event.reason ?? '');
          this.thread().say('Aucun changement établi par la discussion.');
        }
        return;
      case 'error':
        this.endProposal();
        this.problem.set(event.userMessage);
        return;
      case 'gone':
        this.endProposal();
        this.problem.set('Le suivi de la proposition a été interrompu. Réessayez.');
        return;
      default:
        return;
    }
  }

  /** La raison du modèle, terminée par un point, avant la phrase qui suit. */
  protected sentence(text: string): string {
    const t = text.trim();
    return !t || /[.!?…]$/.test(t) ? t : `${t}.`;
  }

  private endProposal(): void {
    if (this.proposing()) {
      this.drafting.emit(false);
    }
    this.proposing.set(false);
    this.stream?.close();
    this.stream = null;
  }

  async ngOnInit(): Promise<void> {
    await this.open(false);
  }

  ngOnDestroy(): void {
    this.stream?.close();
  }

  protected async fresh(): Promise<void> {
    await this.open(true);
  }

  /** Après chaque réponse : la discussion peut porter sur une version plus récente. */
  protected async onFinished(detail: ConversationDetail): Promise<void> {
    this.memoryUntil.set(detail.memoryUntil);
    const current = await this.api.discussion(this.sessionId()).catch(() => null);
    if (current && current.conversationId === this.view()?.conversationId) {
      this.view.set(current);
    }
  }

  private async open(fresh: boolean): Promise<void> {
    this.busy.set(true);
    this.problem.set('');
    this.thread().reset();
    try {
      const view = await this.api.openDiscussion(this.sessionId(), fresh);
      const detail = await this.api.get(view.conversationId);
      this.view.set(view);
      this.messages.set(detail.messages);
      this.memoryUntil.set(detail.memoryUntil);
      const running = detail.messages.find((m) => m.state === 'RUNNING');
      if (running) {
        this.thread().resume(view.conversationId, running.id);
      }
      this.thread().say(
        fresh
          ? 'Nouvelle discussion ouverte.'
          : `Discussion de la version ${view.version} ouverte.`,
      );
      setTimeout(() => this.thread().focus());
    } catch (e) {
      this.problem.set(reason(e, 'La discussion n’a pas pu être ouverte. Réessayez.'));
    } finally {
      this.busy.set(false);
    }
  }
}
