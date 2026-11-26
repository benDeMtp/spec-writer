import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { isProblem } from '../../core/http/problem';
import { JiraLabelApi } from '../../core/projects/jira-label.api';

/**
 * Label de reconnaissance (024, US1) : posé sur les stories et sous-tâches que spec-writer crée ou
 * reprend ; il filtre la vue Jira et les tickets servis aux agents de code.
 */
@Component({
  selector: 'sw-jira-label',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule],
  template: `
    <div class="field">
      <label for="sw-jira-label">Label des tickets de spec-writer</label>
      <input
        id="sw-jira-label"
        type="text"
        maxlength="255"
        autocomplete="off"
        spellcheck="false"
        aria-describedby="sw-jira-label-hint"
        [value]="draft()"
        (input)="draft.set($any($event.target).value)"
      />
      <p id="sw-jira-label-hint" class="hint">
        Un label Jira, sans espace. Il est posé sur ce que spec-writer crée ou reprend dans Jira, et sert de filtre à la
        vue Jira et aux agents de code. Sans label, rien n’est étiqueté ni filtré.
      </p>
    </div>
    @if (renaming()) {
      <p class="hint">Les tickets qui portent l’ancien label ne seront plus reconnus.</p>
    }
    <div class="actions">
      <button mat-flat-button type="button" [disabled]="!changed() || busy()" (click)="save()">Enregistrer le label</button>
      @if (saved()) {
        <button mat-button type="button" [disabled]="busy()" (click)="remove()">Retirer le label</button>
      }
    </div>
    @if (problem()) {
      <p class="problem" role="alert">{{ problem() }}</p>
    }
    <p class="sw-visually-hidden" role="status" aria-live="polite">{{ announce() }}</p>
  `,
  styles: `
    .field {
      display: grid;
      gap: 0.25rem;
      max-width: 32rem;
    }
    label {
      font-weight: 600;
    }
    input {
      font: inherit;
      padding: 0.4rem 0.5rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
    }
    .hint {
      color: var(--sw-ink-soft);
      margin: 0.25rem 0;
    }
    .actions {
      display: flex;
      gap: 0.5rem;
      margin: 0.5rem 0 1rem;
    }
    .problem {
      padding: 0.5rem 0.75rem;
      border-left: 4px solid var(--mat-sys-error, #b3261e);
    }
  `,
})
export class JiraLabelComponent {
  private readonly api = inject(JiraLabelApi);
  readonly projectId = input.required<string>();

  protected readonly saved = signal<string | null>(null);
  protected readonly draft = signal('');
  protected readonly busy = signal(false);
  protected readonly problem = signal('');
  protected readonly announce = signal('');

  protected readonly changed = computed(() => {
    const d = this.draft().trim();
    return !!d && d !== this.saved();
  });
  protected readonly renaming = computed(() => !!this.saved() && this.changed());

  constructor() {
    effect(() => {
      const id = this.projectId();
      void this.api.get(id).then((v) => {
        this.saved.set(v.label);
        this.draft.set(v.label ?? '');
      });
    });
  }

  protected async save(): Promise<void> {
    this.busy.set(true);
    this.problem.set('');
    try {
      const v = await this.api.save(this.projectId(), this.draft().trim());
      this.saved.set(v.label);
      this.draft.set(v.label ?? '');
      this.announce.set(`Label ${v.label} enregistré.`);
    } catch (e) {
      this.problem.set(
        e instanceof HttpErrorResponse && isProblem(e.error) ? e.error.userMessage : 'Le label n’a pas pu être enregistré.',
      );
    } finally {
      this.busy.set(false);
    }
  }

  protected async remove(): Promise<void> {
    this.busy.set(true);
    try {
      await this.api.remove(this.projectId());
      this.saved.set(null);
      this.draft.set('');
      this.announce.set('Label retiré : plus rien n’est étiqueté ni filtré.');
    } finally {
      this.busy.set(false);
    }
  }
}
