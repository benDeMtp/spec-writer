import { ChangeDetectionStrategy, Component, ElementRef, afterNextRender, computed, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { Outcome } from '../../core/interviews/interviews.model';

/** Ce qu'on note : la réponse à un point de la spécification, une remarque, ou une question. */
export type EditorKind = 'SPEC' | 'REMARK' | 'QUESTION';

export interface EditorValue {
  text: string;
  outcome: Outcome | null;
  owner: string | null;
  due: string | null;
}

const LABELS: Record<Outcome, string> = {
  CONFIRMED: 'Confirmé',
  CHANGE: 'À modifier',
  REMOVE: 'À retirer',
  DEFERRED: 'Reporté',
};

/** Saisie d'un point en séance (021, contracts/ui.md) : texte, issue, responsable et échéance si reporté. */
@Component({
  selector: 'sw-point-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatRadioModule],
  template: `
    <form class="editor" (ngSubmit)="submit()" (keydown.escape)="cancelled.emit()" [attr.aria-label]="heading()">
      <p class="heading">{{ heading() }}</p>
      <mat-form-field appearance="outline" class="text">
        <mat-label>{{ kind() === 'QUESTION' ? 'Question' : 'Ce qui a été dit' }}</mat-label>
        <textarea
          #textarea
          matInput
          name="text"
          rows="3"
          maxlength="5000"
          [(ngModel)]="text"
          (keydown.control.enter)="submit(); $event.preventDefault()"
          (keydown.meta.enter)="submit(); $event.preventDefault()"
          [attr.aria-describedby]="error() ? 'point-error' : null"
        ></textarea>
      </mat-form-field>
      @if (kind() !== 'QUESTION') {
        <mat-radio-group name="outcome" [(ngModel)]="outcome" aria-label="Issue du point" class="outcomes">
          @for (o of outcomes(); track o) {
            <mat-radio-button [value]="o">{{ label(o) }}</mat-radio-button>
          }
        </mat-radio-group>
      }
      @if (deferred()) {
        <div class="deferred">
          <mat-form-field appearance="outline">
            <mat-label>Responsable</mat-label>
            <input matInput name="owner" maxlength="120" [(ngModel)]="owner" (keydown.enter)="submit(); $event.preventDefault()" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Échéance</mat-label>
            <input matInput type="date" name="due" [(ngModel)]="due" />
          </mat-form-field>
        </div>
      }
      @if (error()) {
        <p id="point-error" class="error" role="alert">{{ error() }}</p>
      }
      <div class="actions">
        <button mat-flat-button type="submit">Enregistrer</button>
        <button mat-button type="button" (click)="cancelled.emit()">Annuler</button>
      </div>
    </form>
  `,
  styles: `
    .editor {
      display: grid;
      gap: 0.5rem;
      padding: 1rem;
      border: 2px solid var(--sw-accent, currentColor);
      border-radius: var(--sw-radius-field);
      background: var(--sw-sheet);
    }
    .heading {
      margin: 0;
      font-weight: 600;
    }
    .text {
      width: 100%;
    }
    .outcomes {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem 1.5rem;
    }
    .deferred {
      display: flex;
      flex-wrap: wrap;
      gap: 0 1rem;
    }
    .error {
      margin: 0;
      color: var(--sw-danger, #b3261e);
    }
    .actions {
      display: flex;
      gap: 0.5rem;
    }
  `,
})
export class PointEditorComponent {
  readonly kind = input.required<EditorKind>();
  readonly heading = input.required<string>();
  readonly initial = input<EditorValue | null>(null);
  readonly saved = output<EditorValue>();
  readonly cancelled = output<void>();

  protected readonly text = signal('');
  protected readonly outcome = signal<Outcome | null>(null);
  protected readonly owner = signal('');
  protected readonly due = signal('');
  protected readonly error = signal<string | null>(null);
  private readonly textarea = viewChild<ElementRef<HTMLTextAreaElement>>('textarea');

  protected readonly outcomes = computed<Outcome[]>(() =>
    this.kind() === 'SPEC' ? ['CONFIRMED', 'CHANGE', 'REMOVE', 'DEFERRED'] : ['CHANGE', 'REMOVE', 'DEFERRED'],
  );
  protected readonly deferred = computed(() => this.kind() === 'QUESTION' || this.outcome() === 'DEFERRED');

  constructor() {
    afterNextRender(() => {
      const v = this.initial();
      this.text.set(v?.text ?? '');
      this.outcome.set(v?.outcome ?? (this.kind() === 'REMARK' ? 'CHANGE' : null));
      this.owner.set(v?.owner ?? '');
      this.due.set(v?.due ?? '');
      this.textarea()?.nativeElement.focus();
    });
  }

  protected label(o: Outcome): string {
    return LABELS[o];
  }

  protected submit(): void {
    const text = this.text().trim();
    const outcome = this.kind() === 'QUESTION' ? 'DEFERRED' : this.outcome();
    if (this.kind() !== 'QUESTION' && !outcome) {
      this.error.set("Choisissez l'issue du point.");
      return;
    }
    const textRequired = this.kind() !== 'SPEC' || outcome === 'CHANGE' || outcome === 'DEFERRED';
    if (textRequired && !text) {
      this.error.set('Écrivez ce qui a été dit avant d’enregistrer.');
      return;
    }
    this.error.set(null);
    const deferred = outcome === 'DEFERRED';
    this.saved.emit({
      text,
      outcome: this.kind() === 'QUESTION' ? null : outcome,
      owner: deferred && this.owner().trim() ? this.owner().trim() : null,
      due: deferred && this.due() ? this.due() : null,
    });
  }
}
