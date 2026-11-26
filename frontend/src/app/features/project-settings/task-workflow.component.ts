import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { isProblem } from '../../core/http/problem';
import { KnownStatus, TaskWorkflowApi, TaskWorkflowView } from '../../core/projects/task-workflow.api';

const GROUPS: { category: KnownStatus['category']; label: string }[] = [
  { category: 'new', label: 'À faire' },
  { category: 'indeterminate', label: 'En cours' },
  { category: 'done', label: 'Terminé' },
  { category: null, label: 'Plus vus dans l’index' },
];

/** Même nom de statut, sans la casse ni les accents (023, research R3). */
function same(a: string, b: string): boolean {
  const n = (s: string) => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
  return n(a) === n(b);
}

/**
 * Rubrique « Suivi des tâches dans Jira » (023, US3) : statut où arrive une tâche implémentée par un
 * agent, et statuts où une tâche compte comme finie ; choisis parmi les statuts vus dans l'index.
 */
@Component({
  selector: 'sw-task-workflow',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule],
  template: `
    <p class="hint">
      Quand un agent de code termine une tâche, spec-writer passe la sous-tâche au statut d’arrivée, puis sa story
      quand toutes ses tâches sont finies. Sans réglage, la tâche passe à « terminé », ce qui la ferme.
    </p>
    @if (view(); as v) {
      @if (!v.known.length) {
        <p class="hint">Les statuts du projet apparaîtront ici après la synchronisation de Jira.</p>
      } @else {
        <div class="columns">
          <fieldset>
            <legend>Statut d’arrivée d’une tâche implémentée</legend>
            @for (g of groups(); track g.label) {
              <h4>{{ g.label }}</h4>
              @for (s of g.statuses; track s.name) {
                <label>
                  <input
                    type="radio"
                    name="arrival"
                    [value]="s.name"
                    [checked]="arrival() === s.name"
                    [disabled]="s.category === 'new'"
                    (change)="arrival.set(s.name)"
                  />
                  {{ s.name }}
                </label>
              }
            }
          </fieldset>
          <fieldset>
            <legend>Statuts où une tâche est finie</legend>
            @for (g of groups(); track g.label) {
              <h4>{{ g.label }}</h4>
              @for (s of g.statuses; track s.name) {
                <label>
                  <input
                    type="checkbox"
                    [value]="s.name"
                    [checked]="always(s) || isChecked(s.name)"
                    [disabled]="always(s)"
                    (change)="toggle(s.name)"
                  />
                  {{ s.name }}
                  @if (s.category === 'done') {
                    <span class="note">(toujours fini)</span>
                  } @else if (isArrival(s.name)) {
                    <span class="note">(statut d’arrivée)</span>
                  } @else if (!s.seen) {
                    <span class="note">(plus vu dans l’index)</span>
                  }
                </label>
              }
            }
          </fieldset>
        </div>
        <div class="actions">
          <button mat-flat-button type="button" [disabled]="!arrival() || busy()" (click)="save()">Enregistrer</button>
          @if (v.arrivalStatus) {
            <button mat-button type="button" [disabled]="busy()" (click)="remove()">Retirer le réglage</button>
          }
        </div>
      }
    }
    @if (problem()) {
      <p class="problem" role="alert">{{ problem() }}</p>
    }
    <p class="sw-visually-hidden" role="status" aria-live="polite">{{ announce() }}</p>
  `,
  styles: `
    .columns {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr));
      gap: 1rem;
    }
    fieldset {
      margin: 0;
      padding: 0.75rem 1rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
    }
    legend {
      font-weight: 600;
      padding: 0 0.25rem;
    }
    h4 {
      margin: 0.75rem 0 0.25rem;
      font-size: var(--sw-step--1);
      color: var(--sw-ink-soft);
    }
    label {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.15rem 0;
    }
    .note,
    .hint {
      color: var(--sw-ink-soft);
    }
    .note {
      font-size: var(--sw-step--1);
    }
    .actions {
      display: flex;
      gap: 0.5rem;
      margin-top: 0.75rem;
    }
    .problem {
      padding: 0.5rem 0.75rem;
      border-left: 4px solid var(--mat-sys-error, #b3261e);
    }
  `,
})
export class TaskWorkflowComponent {
  private readonly api = inject(TaskWorkflowApi);
  readonly projectId = input.required<string>();

  protected readonly view = signal<TaskWorkflowView | null>(null);
  protected readonly arrival = signal<string | null>(null);
  protected readonly finished = signal<string[]>([]);
  protected readonly busy = signal(false);
  protected readonly problem = signal('');
  protected readonly announce = signal('');

  protected readonly groups = computed(() => {
    const known = this.view()?.known ?? [];
    return GROUPS.map((g) => ({ label: g.label, statuses: known.filter((s) => s.category === g.category) })).filter(
      (g) => g.statuses.length,
    );
  });

  constructor() {
    effect(() => {
      const id = this.projectId();
      void this.api.get(id).then((v) => this.show(v));
    });
  }

  private show(v: TaskWorkflowView): void {
    this.view.set(v);
    this.arrival.set(v.arrivalStatus);
    this.finished.set(v.finishedStatuses.filter((s) => !v.arrivalStatus || !same(s, v.arrivalStatus)));
  }

  protected isArrival(name: string): boolean {
    const a = this.arrival();
    return !!a && same(a, name);
  }

  /** Toujours fini : la catégorie « terminé » et le statut d'arrivée. */
  protected always(s: KnownStatus): boolean {
    return s.category === 'done' || this.isArrival(s.name);
  }

  protected isChecked(name: string): boolean {
    return this.finished().some((f) => same(f, name));
  }

  protected toggle(name: string): void {
    this.finished.update((all) => (all.some((f) => same(f, name)) ? all.filter((f) => !same(f, name)) : [...all, name]));
  }

  protected async save(): Promise<void> {
    const arrival = this.arrival();
    if (!arrival) {
      return;
    }
    this.busy.set(true);
    this.problem.set('');
    try {
      const finished = this.finished().filter((f) => !same(f, arrival));
      this.show(await this.api.save(this.projectId(), arrival, finished));
      this.announce.set('Réglage enregistré.');
    } catch (e) {
      this.problem.set(
        e instanceof HttpErrorResponse && isProblem(e.error) ? e.error.userMessage : 'Le réglage n’a pas pu être enregistré.',
      );
    } finally {
      this.busy.set(false);
    }
  }

  protected async remove(): Promise<void> {
    this.busy.set(true);
    try {
      await this.api.remove(this.projectId());
      const v = this.view();
      this.show({ arrivalStatus: null, finishedStatuses: [], known: (v?.known ?? []).filter((s) => s.seen) });
      this.announce.set('Réglage retiré : une tâche terminée passe à « terminé ».');
    } finally {
      this.busy.set(false);
    }
  }
}
