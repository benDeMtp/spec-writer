import { Location } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink } from '@angular/router';
import { ConversationsApi } from '../../core/conversations/conversations.api';
import {
  ConversationDetail,
  ConversationMessage,
  ConversationSummary,
  NeedDraft,
} from '../../core/conversations/conversations.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { ElapsedComponent } from '../../shared/ui/elapsed.component';
import { ConversationListComponent } from './conversation-list.component';
import { ConversationThreadComponent, reason } from './conversation-thread.component';
import { NeedDraftComponent } from './need-draft.component';
import { ProjectHeadingComponent } from '../../shared/ui/project-heading.component';

export { MAX_MESSAGE } from './conversation-thread.component';

/**
 * Conversation avec l'agent, écran d'accueil (010, US1, US4) : liste des conversations, en-tête,
 * exemples de questions, brouillon de besoin ; le fil lui-même est `sw-conversation-thread`, partagé
 * avec la discussion d'une spécification (011).
 */
@Component({
  selector: 'sw-conversation-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ProjectHeadingComponent,
    ConversationListComponent,
    ConversationThreadComponent,
    ElapsedComponent,
    MatButtonModule,
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
          @if (detail(); as d) {
            <h1 id="thread-title">{{ d.name }}</h1>
          } @else {
            <sw-project-heading label="Conversation" headingId="thread-title" />
          }
          @if (detail() && messages().length && !draft()) {
            <span class="draft-action">
              <button
                mat-stroked-button
                type="button"
                [disabled]="running() || drafting()"
                (click)="proposeNeed()"
              >
                {{ drafting() ? 'L’agent prépare le besoin…' : 'Rédiger une spécification' }}
              </button>
              <sw-elapsed class="hint" [running]="drafting()" />
            </span>
          }
        </header>
        @if (detail()?.specification; as spec) {
          <p class="discussed">
            Discussion de la spécification « {{ spec.name }} »
            @if (spec.sessionId) {
              : <a [routerLink]="['/sessions', spec.sessionId]">Ouvrir la spécification</a>
            } @else {
              (spécification supprimée)
            }
          </p>
        }
        @if (detail()?.sessions?.length) {
          <p class="linked">
            Spécifications issues de cette conversation :
            @for (s of detail()!.sessions; track s.sessionId; let last = $last) {
              <a [routerLink]="['/sessions', s.sessionId]">{{ s.name }}</a
              >{{ last ? '' : ', ' }}
            }
          </p>
        }

        @if (!messages().length) {
          <div class="welcome">
            <p class="lead">
              Posez une question sur le projet{{ projectName() ? ' ' + projectName() : '' }}.
              L’agent cherche dans ses tickets, sa documentation, ses ADR et ses spécifications,
              puis répond en citant ses sources.
            </p>
            @if (examples().length) {
              <p class="hint">D’après ce que l’agent sait du projet, par exemple :</p>
              <ul class="examples">
                @for (e of examples(); track e) {
                  <li>
                    <button mat-button type="button" (click)="useExample(e)">{{ e }}</button>
                  </li>
                }
              </ul>
            }
          </div>
        }

        <sw-conversation-thread
          #thread
          [conversationId]="detail()?.id ?? null"
          [projectId]="projectId()"
          [memoryUntil]="detail()?.memoryUntil ?? null"
          [prepare]="create"
          [(messages)]="messages"
          (sent)="onSent()"
          (finished)="onFinished($event)"
        >
          @if (draft(); as d) {
            <sw-need-draft
              [conversationId]="detail()!.id"
              [draft]="d"
              (started)="openSpecification($event)"
              (cancelled)="draft.set(null)"
            />
          }
        </sw-conversation-thread>
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
    sw-project-heading {
      --sw-heading-size: var(--sw-step-2);
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
    .draft-action {
      display: inline-flex;
      align-items: baseline;
      gap: 0.6rem;
    }
    .hint {
      color: var(--sw-ink-soft);
    }
    .discussed {
      margin: -0.75rem 0 1.25rem;
      color: var(--sw-ink-soft);
    }
    @media (max-width: 60rem) {
      .conversation {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
})
export class ConversationPage {
  /** Conversation ouverte (`/conversations/:cid`) ; aucune à l'accueil. */
  readonly cid = input<string | undefined>(undefined);

  private readonly api = inject(ConversationsApi);
  private readonly projects = inject(ProjectsStore);
  private readonly consent = inject(ConsentGate);
  private readonly location = inject(Location);
  private readonly router = inject(Router);
  private readonly thread = viewChild.required<ConversationThreadComponent>('thread');

  /** Exemples tirés de la connaissance du projet courant ; aucun tant que rien n'est indexé. */
  protected readonly examples = signal<string[]>([]);
  protected readonly projectId = this.projects.currentId;
  protected readonly projectName = computed(() => this.projects.current()?.name ?? '');
  protected readonly threads = signal<ConversationSummary[]>([]);
  protected readonly detail = signal<ConversationDetail | null>(null);
  protected readonly messages = signal<ConversationMessage[]>([]);
  protected readonly draft = signal<NeedDraft | null>(null);
  protected readonly drafting = signal(false);
  protected readonly running = computed(() => this.messages().some((m) => m.state === 'RUNNING'));

  /** Premier envoi depuis l'accueil : la conversation est créée, et l'adresse suit. */
  protected readonly create = async (): Promise<string | null> => {
    const projectId = this.projectId();
    if (!projectId) {
      return null;
    }
    const created = await this.api.create(projectId);
    this.detail.set({ ...created, messages: [], sessions: [], memoryUntil: null });
    this.location.replaceState(`/conversations/${created.id}`);
    return created.id;
  };

  constructor() {
    effect(() => {
      const projectId = this.projectId();
      const cid = this.cid();
      untracked(() => void this.load(projectId, cid ?? null));
    });
  }

  protected useExample(example: string): void {
    this.thread().prefill(example);
  }

  protected onSent(): void {
    const projectId = this.projectId();
    if (projectId) {
      void this.refreshThreads(projectId);
    }
  }

  protected onFinished(detail: ConversationDetail): void {
    if (this.detail()?.id === detail.id) {
      this.detail.set(detail);
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
    this.thread().reset();
    this.detail.set(null);
    this.messages.set([]);
    this.location.replaceState('/');
    await this.router.navigateByUrl('/');
    this.thread().focus();
  }

  /** L'agent propose un besoin et des points établis, que l'utilisateur relit avant de démarrer. */
  protected async proposeNeed(): Promise<void> {
    const conversation = this.detail();
    if (!conversation || this.running() || this.drafting()) {
      return;
    }
    this.thread().fail('');
    if (!(await this.consent.ensure('COPILOT'))) {
      this.thread().fail('Sans votre accord, la conversation n’est pas envoyée à GitHub Copilot.');
      return;
    }
    this.drafting.set(true);
    this.thread().say('L’agent prépare le besoin.');
    try {
      this.draft.set(await this.api.needDraft(conversation.id));
      this.thread().say('Brouillon du besoin prêt à relire.');
    } catch (e) {
      this.thread().fail(reason(e, 'Le besoin n’a pas pu être préparé. Réessayez.'));
    } finally {
      this.drafting.set(false);
    }
  }

  protected async openSpecification(sessionId: string): Promise<void> {
    this.draft.set(null);
    await this.router.navigate(['/sessions', sessionId]);
  }

  private async load(projectId: string | null, cid: string | null): Promise<void> {
    this.thread().reset();
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
        this.thread().resume(detail.id, running.id);
      }
    } catch {
      this.detail.set(null);
      this.messages.set([]);
      this.thread().fail(
        'Cette conversation n’existe plus : choisissez-en une autre dans la liste, ou posez une nouvelle question.',
      );
    }
  }

  private async refreshThreads(projectId: string): Promise<void> {
    this.threads.set(await this.api.list(projectId).catch(() => []));
  }
}
