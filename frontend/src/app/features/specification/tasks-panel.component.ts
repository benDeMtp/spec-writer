import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TasksApi } from '../../core/tasks/tasks.api';
import {
  OUTCOME_MAX,
  ProposedTask,
  SpecTask,
  TASK_STATUS_LABELS,
  TITLE_MAX,
  TaskProposal,
  TasksView,
} from '../../core/tasks/tasks.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { ElapsedComponent } from '../../shared/ui/elapsed.component';
import { TaskProposalComponent } from './task-proposal.component';

/**
 * Panneau « Tâches » d'une spécification (015) : proposition et validation (US1), avancement relu
 * dans Jira, tâches tenues par les agents, synchronisation et suivi suspendu (US3).
 */
@Component({
  selector: 'sw-tasks-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, ElapsedComponent, MatButtonModule, MatFormFieldModule, MatInputModule, TaskProposalComponent],
  template: `
    <section aria-labelledby="tasks-title" class="tasks" data-tour="tasks-panel">
      <h2 id="tasks-title">Tâches</h2>
      @if (view(); as v) {
        @if (v.progress.total) {
          <p class="progress" aria-live="polite">
            <strong>{{ v.progress.done }} {{ v.progress.done > 1 ? 'tâches' : 'tâche' }} sur {{ v.progress.total }}
              {{ v.progress.done > 1 ? 'terminées' : 'terminée' }}</strong>
            <progress [value]="v.progress.done" [max]="v.progress.total" aria-hidden="true"></progress>
          </p>
        }
        @if (v.readProblem) {
          <p class="warning" role="note">
            Jira n'a pas pu être relu : statuts connus{{
              v.lastReadAt ? ' au ' + (v.lastReadAt | date: 'd MMM y, HH:mm') : ''
            }}.
          </p>
        }
        @if (v.tracking === 'SUSPENDED') {
          <div class="note" role="note">
            <p>
              Suivi suspendu : la spécification a été repassée en Brouillon ou en Prête. Les agents ne
              peuvent plus prendre ni terminer de tâche.
            </p>
            <button mat-stroked-button type="button" [disabled]="busy()" (click)="act(api.resume(sessionId()))">
              Reprendre le suivi
            </button>
          </div>
        }
        @if (v.pendingSync) {
          <div class="note" role="note">
            <p>
              {{ v.pendingSync }} {{ v.pendingSync > 1 ? 'tâches' : 'tâche' }} à synchroniser : Jira n'a pas
              encore reçu leur dernière transition.
            </p>
            <button mat-stroked-button type="button" [disabled]="busy()" (click)="act(api.sync(sessionId()))">
              Synchroniser
            </button>
          </div>
        }
        @if (v.creationProblem) {
          <p class="warning" role="alert">{{ v.creationProblem }}</p>
        }
        @if (v.tasks.length) {
          <ol class="list">
            @for (t of v.tasks; track t.id) {
              <li class="task" [class.missing]="t.status === 'MISSING'">
                <p class="title">
                  @if (t.url) {
                    <a [href]="t.url" target="_blank" rel="noopener noreferrer"
                      >{{ t.key }}<span class="sw-visually-hidden"> (nouvel onglet)</span></a
                    >
                  }
                  {{ t.title }}
                  <span class="badge" [class]="'badge ' + t.status">{{ statusLabel(t) }}</span>
                  @if (t.pending) {
                    <span class="pending">à synchroniser</span>
                  }
                </p>
                @if (t.outcome) {
                  <p class="outcome">{{ t.outcome }}</p>
                }
                @if (t.heldBy) {
                  <p class="holder">
                    Prise par {{ t.heldBy }}{{ t.heldSince ? ' le ' + (t.heldSince | date: 'd MMM y, HH:mm') : '' }}
                    @if (t.holderClosed) {
                      — sa conversation est fermée : la prochaine reprendra cette tâche.
                    }
                    <button mat-button type="button" [disabled]="busy()" (click)="act(api.release(sessionId(), t.id))">
                      Libérer<span class="sw-visually-hidden"> la tâche {{ t.key }}</span>
                    </button>
                  </p>
                }
                @if (t.report) {
                  <details class="report">
                    <summary>Compte rendu de l'agent</summary>
                    <p>{{ t.report }}</p>
                  </details>
                }
                @if (t.creation === 'NOT_CREATED') {
                  <p class="hint">
                    Pas encore créée dans Jira.
                    <button mat-button type="button" [disabled]="busy()" (click)="act(api.remove(sessionId(), t.id))">
                      Retirer<span class="sw-visually-hidden"> la tâche {{ t.title }}</span>
                    </button>
                  </p>
                }
              </li>
            }
          </ol>
          @if (notCreated()) {
            <button mat-stroked-button type="button" [disabled]="busy()" (click)="act(api.createRemaining(sessionId()))">
              Créer les tâches restantes
            </button>
          }
        }
        @if (proposal(); as p) {
          <sw-task-proposal
            [proposal]="p"
            [busy]="busy()"
            (changed)="save($event)"
            (validate)="validate()"
            (discard)="discard()"
          />
        } @else {
          <div class="actions">
            <button
              mat-flat-button
              type="button"
              [disabled]="busy() || !v.canPropose"
              [attr.aria-describedby]="v.canPropose ? null : 'tasks-blocked'"
              (click)="propose()"
            >
              {{ v.tasks.length ? 'Proposer des tâches complémentaires' : 'Proposer des tâches' }}
            </button>
            <sw-elapsed class="hint" [running]="proposing()" />
          </div>
          @if (!v.canPropose) {
            <p id="tasks-blocked" class="hint">Envoyez d'abord la spécification vers Jira : les tâches deviennent des
              sous-tâches de sa story.</p>
          }
          @if (v.tasks.length) {
            <form class="add" (submit)="addTask($event)">
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>Ajouter une tâche</mat-label>
                <input matInput name="title" [attr.maxlength]="titleMax" [value]="newTitle()"
                  (input)="newTitle.set($any($event.target).value)" />
              </mat-form-field>
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>Son résultat vérifiable</mat-label>
                <input matInput name="outcome" [attr.maxlength]="outcomeMax" [value]="newOutcome()"
                  (input)="newOutcome.set($any($event.target).value)" />
              </mat-form-field>
              <button mat-stroked-button type="submit" [disabled]="busy() || !newTitle().trim() || !newOutcome().trim()">
                Ajouter et créer dans Jira
              </button>
            </form>
          }
        }
      } @else {
        <p class="hint" role="status">Chargement des tâches…</p>
      }
    </section>
  `,
  styles: `
    .list {
      padding-inline-start: 1.25rem;
    }
    .task {
      margin-block: 0.5rem;
    }
    .task p {
      margin: 0.125rem 0;
    }
    .task.missing .title {
      text-decoration: line-through;
    }
    .badge {
      margin-inline-start: 0.5rem;
      font-size: 0.875rem;
      padding: 0 0.375rem;
      border: 1px solid currentColor;
      border-radius: 0.25rem;
    }
    .warning {
      padding-left: 0.5rem;
      border-left: 3px solid var(--sw-outcome);
    }
    .pending {
      margin-inline-start: 0.5rem;
      font-style: italic;
    }
    .outcome,
    .holder,
    .hint {
      color: var(--sw-ink-soft);
    }
    .progress progress {
      display: block;
      width: 100%;
      max-width: 24rem;
    }
    .note {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
    }
    .actions,
    .add {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      align-items: center;
      margin-block: 0.5rem;
    }
  `,
})
export class TasksPanelComponent {
  protected readonly api = inject(TasksApi);
  private readonly consent = inject(ConsentGate);

  readonly sessionId = input.required<string>();
  /** Après chaque lecture ou action : le statut de la spécification peut avoir suivi les tâches. */
  readonly changed = output<TasksView>();

  protected readonly view = signal<TasksView | null>(null);
  protected readonly proposal = signal<TaskProposal | null>(null);
  protected readonly busy = signal(false);
  protected readonly proposing = signal(false);
  protected readonly newTitle = signal('');
  protected readonly newOutcome = signal('');
  protected readonly titleMax = TITLE_MAX;
  protected readonly outcomeMax = OUTCOME_MAX;
  protected readonly notCreated = computed(() => this.view()?.tasks.some((t) => t.creation === 'NOT_CREATED') ?? false);

  constructor() {
    effect(() => {
      const id = this.sessionId();
      untracked(() => void this.load(id));
    });
  }

  private async load(id: string): Promise<void> {
    try {
      const view = await this.api.list(id);
      this.show(view);
      this.proposal.set(view.hasProposal ? await this.api.proposal(id) : null);
    } catch {
      this.view.set(null);
    }
  }

  private show(view: TasksView): void {
    this.view.set(view);
    this.changed.emit(view);
  }

  /** Une action qui rend la vue des tâches ; l'erreur est affichée par le bandeau commun. */
  protected async act(action: Promise<TasksView>): Promise<void> {
    this.busy.set(true);
    try {
      this.show(await action);
    } catch {
      // bandeau d'erreur commun
    } finally {
      this.busy.set(false);
    }
  }

  protected async propose(): Promise<void> {
    if (!(await this.consent.ensure('COPILOT'))) {
      return;
    }
    this.busy.set(true);
    this.proposing.set(true);
    try {
      this.proposal.set(await this.api.propose(this.sessionId()));
      // les sous-tâches déjà présentes dans Jira ont pu être reprises
      this.show(await this.api.list(this.sessionId()));
    } catch {
      // bandeau d'erreur commun
    } finally {
      this.busy.set(false);
      this.proposing.set(false);
    }
  }

  protected async save(tasks: ProposedTask[]): Promise<void> {
    const before = this.proposal();
    if (before) {
      this.proposal.set({ ...before, tasks });
    }
    try {
      this.proposal.set(await this.api.saveProposal(this.sessionId(), tasks));
    } catch {
      this.proposal.set(before);
    }
  }

  protected async validate(): Promise<void> {
    if (!(await this.consent.ensure('JIRA'))) {
      return;
    }
    await this.act(this.api.validate(this.sessionId()));
    if (!this.view()?.hasProposal) {
      this.proposal.set(null);
    }
  }

  protected async discard(): Promise<void> {
    await this.api.discardProposal(this.sessionId()).catch(() => undefined);
    this.proposal.set(null);
  }

  protected async addTask(event: Event): Promise<void> {
    event.preventDefault();
    const title = this.newTitle().trim();
    const outcome = this.newOutcome().trim();
    if (!title || !outcome) {
      return;
    }
    await this.act(this.api.add(this.sessionId(), title, outcome));
    this.newTitle.set('');
    this.newOutcome.set('');
  }

  protected statusLabel(t: SpecTask): string {
    return t.jiraStatusName && t.status !== 'MISSING'
      ? `${TASK_STATUS_LABELS[t.status]} (${t.jiraStatusName})`
      : TASK_STATUS_LABELS[t.status];
  }
}
