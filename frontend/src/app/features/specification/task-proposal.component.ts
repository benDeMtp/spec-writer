import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { OUTCOME_MAX, ProposedTask, TITLE_MAX, TaskProposal } from '../../core/tasks/tasks.model';
import * as ops from './task-proposal.ops';

/**
 * Relecture d'une proposition de tâches (015, US1, FR-004) : renommer, préciser le résultat,
 * fusionner, supprimer, réordonner par boutons, ajouter. Chaque correction est enregistrée ; rien
 * n'est envoyé à Jira avant « Valider ».
 */
@Component({
  selector: 'sw-task-proposal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    @let p = proposal();
    <section class="proposal" aria-labelledby="proposal-title">
      <h3 id="proposal-title">Tâches proposées</h3>
      <p class="hint">
        Découpage fondé sur {{ p.basis === 'MODULES' ? 'les modules touchés' : 'les cas d’usage' }}.
        Corrigez la liste, puis validez : chaque tâche deviendra une sous-tâche Jira.
      </p>
      @if (p.existing.length) {
        <p class="hint">Déjà suivies : {{ existingLabel() }}.</p>
      }
      <ol class="items">
        @for (t of p.tasks; track $index; let i = $index; let last = $last) {
          <li class="item">
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="title">
              <mat-label>Titre de la tâche {{ i + 1 }}</mat-label>
              <input
                matInput
                [value]="t.title"
                [attr.maxlength]="titleMax"
                (change)="apply(rename(i, $any($event.target).value))"
              />
            </mat-form-field>
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="outcome">
              <mat-label>Résultat vérifiable</mat-label>
              <textarea
                matInput
                rows="2"
                [value]="t.outcome"
                [attr.maxlength]="outcomeMax"
                (change)="apply(outcome(i, $any($event.target).value))"
              ></textarea>
            </mat-form-field>
            <p class="meta">
              {{ basisLabel(t) }}
              @if (t.dependsOn.length) {
                · après {{ dependsLabel(t) }}
              }
            </p>
            <div class="row-actions">
              <button mat-button type="button" [disabled]="busy() || i === 0" (click)="apply(moved(i, i - 1))">
                Monter<span class="sw-visually-hidden"> la tâche {{ i + 1 }}</span>
              </button>
              <button mat-button type="button" [disabled]="busy() || last" (click)="apply(moved(i, i + 1))">
                Descendre<span class="sw-visually-hidden"> la tâche {{ i + 1 }}</span>
              </button>
              <button mat-button type="button" [disabled]="busy() || last" (click)="apply(merged(i))">
                Fusionner avec la suivante<span class="sw-visually-hidden"> (tâche {{ i + 1 }})</span>
              </button>
              <button mat-button type="button" [disabled]="busy()" (click)="apply(removed(i))">
                Supprimer<span class="sw-visually-hidden"> la tâche {{ i + 1 }}</span>
              </button>
            </div>
          </li>
        }
      </ol>
      <form class="add" (submit)="addTask($event)">
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Nouvelle tâche</mat-label>
          <input matInput name="title" [attr.maxlength]="titleMax" [value]="newTitle()"
            (input)="newTitle.set($any($event.target).value)" />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Son résultat vérifiable</mat-label>
          <input matInput name="outcome" [attr.maxlength]="outcomeMax" [value]="newOutcome()"
            (input)="newOutcome.set($any($event.target).value)" />
        </mat-form-field>
        <button mat-stroked-button type="submit" [disabled]="busy() || !newTitle().trim() || !newOutcome().trim()">
          Ajouter
        </button>
      </form>
      <div class="actions">
        <button mat-flat-button type="button" [disabled]="busy() || !p.tasks.length" (click)="validate.emit()">
          Valider et créer dans Jira
        </button>
        <button mat-button type="button" [disabled]="busy()" (click)="discard.emit()">Abandonner</button>
      </div>
    </section>
  `,
  styles: `
    .items {
      padding-inline-start: 1.25rem;
    }
    .item {
      margin-block: 0.75rem;
    }
    .title,
    .outcome {
      display: block;
      width: 100%;
      margin-block-end: 0.25rem;
    }
    .meta,
    .hint {
      color: var(--sw-ink-soft);
      margin: 0.125rem 0;
    }
    .row-actions,
    .actions,
    .add {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      align-items: center;
    }
    .actions {
      margin-block-start: 0.75rem;
    }
  `,
})
export class TaskProposalComponent {
  readonly proposal = input.required<TaskProposal>();
  readonly busy = input(false);
  /** La liste corrigée, à enregistrer. */
  readonly changed = output<ProposedTask[]>();
  readonly validate = output<void>();
  readonly discard = output<void>();

  protected readonly titleMax = TITLE_MAX;
  protected readonly outcomeMax = OUTCOME_MAX;
  protected readonly newTitle = signal('');
  protected readonly newOutcome = signal('');

  private get tasks(): ProposedTask[] {
    return this.proposal().tasks;
  }

  protected rename(i: number, title: string): ProposedTask[] {
    return ops.rename(this.tasks, i, title.trim());
  }

  protected outcome(i: number, outcome: string): ProposedTask[] {
    return ops.editOutcome(this.tasks, i, outcome.trim());
  }

  protected moved(from: number, to: number): ProposedTask[] {
    return ops.move(this.tasks, from, to);
  }

  protected merged(i: number): ProposedTask[] {
    return ops.mergeWithNext(this.tasks, i);
  }

  protected removed(i: number): ProposedTask[] {
    return ops.remove(this.tasks, i);
  }

  protected apply(tasks: ProposedTask[]): void {
    this.changed.emit(tasks);
  }

  protected addTask(event: Event): void {
    event.preventDefault();
    const title = this.newTitle().trim();
    const outcome = this.newOutcome().trim();
    if (!title || !outcome) {
      return;
    }
    this.apply(ops.add(this.tasks, title, outcome));
    this.newTitle.set('');
    this.newOutcome.set('');
  }

  protected basisLabel(t: ProposedTask): string {
    switch (t.basis.kind) {
      case 'MODULE':
        return `Module ${t.basis.ref}`;
      case 'USE_CASE':
        return `Cas d’usage ${t.basis.ref}`;
      default:
        return 'Ajoutée à la main';
    }
  }

  protected dependsLabel(t: ProposedTask): string {
    return t.dependsOn.map((d) => `la tâche ${d + 1}`).join(', ');
  }

  protected existingLabel(): string {
    return this.proposal()
      .existing.map((e) => `${e.key} « ${e.title} »`)
      .join(', ');
  }
}
