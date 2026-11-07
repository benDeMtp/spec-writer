import { ChangeDetectionStrategy, Component, ElementRef, computed, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Question } from '../../core/sessions/sessions.model';
import { MentionStatusComponent } from '../../shared/ui/mention-status.component';
import { MentionDirective } from '../../shared/ui/mention.directive';

const THEMES: Record<string, string> = {
  ACTORS: 'Acteurs',
  GOAL: 'Objectif',
  NOMINAL_FLOW: 'Parcours nominal',
  BUSINESS_RULES: 'Règles métier',
  ERRORS: "Cas d'erreur",
  EDGE_CASES: 'Cas limites',
  NON_FUNCTIONAL: 'Contraintes',
  CONTRADICTION: 'Contradiction à trancher',
};

/** Une question : justification, suggestions, valeur par défaut, réponse ou question passée (FR-005, FR-006). */
@Component({
  selector: 'sw-question-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatChipsModule, MatFormFieldModule, MatInputModule, MentionDirective, MentionStatusComponent],
  template: `
    @let q = question();
    <article class="question" [class.contradiction]="q.theme === 'CONTRADICTION'" [attr.aria-labelledby]="'q-' + q.id">
      <p class="theme">{{ themeLabel() }}</p>
      <h2 [id]="'q-' + q.id" tabindex="-1" #heading>{{ q.label }}</h2>
      <p class="rationale">{{ q.rationale }}</p>
      @if (q.suggestions.length) {
        <mat-chip-listbox [attr.aria-label]="'Réponses suggérées pour : ' + q.label">
          @for (s of q.suggestions; track s) {
            <mat-chip-option [selected]="value() === s" (click)="value.set(s)">{{ s }}</mat-chip-option>
          }
        </mat-chip-listbox>
      }
      <mat-form-field appearance="outline" class="answer">
        <mat-label>Votre réponse</mat-label>
        <textarea
          matInput
          rows="3"
          maxlength="5000"
          [swMention]="projectId()"
          [ngModel]="value()"
          (ngModelChange)="value.set($event)"
        ></textarea>
      </mat-form-field>
      <sw-mention-status
        [projectId]="projectId()"
        [sessionId]="sessionId()"
        field="ANSWER"
        [text]="value()"
        (replaced)="value.set($event)"
      />
      @if (q.theme !== 'CONTRADICTION') {
        <p class="default">{{ defaultText() }}</p>
      }
      <div class="actions">
        <button mat-flat-button type="button" [disabled]="busy() || !value().trim()" (click)="answered.emit(value().trim())">
          Enregistrer la réponse
        </button>
        @if (q.theme !== 'CONTRADICTION') {
          <button mat-stroked-button type="button" [disabled]="busy()" (click)="skipped.emit()">Passer cette question</button>
        }
      </div>
    </article>
  `,
  styles: `
    .question {
      padding: 1.25rem 0;
      border-top: 1px solid var(--sw-rule);
    }
    .question.contradiction {
      padding-left: 1rem;
      border-left: 4px solid var(--sw-outcome);
    }
    .theme {
      margin: 0;
      color: var(--sw-ink-soft);
      font-size: var(--sw-step--1);
    }
    h2 {
      margin: 0.25rem 0 0.5rem;
      font-size: var(--sw-step-1);
      font-weight: 650;
      outline: none;
    }
    .rationale {
      margin: 0 0 0.75rem;
      color: var(--sw-ink-soft);
    }
    mat-chip-listbox {
      display: block;
      margin-bottom: 0.75rem;
    }
    .answer {
      width: 100%;
    }
    .default {
      margin: 0 0 0.5rem;
      color: var(--sw-ink-soft);
      font-size: var(--sw-step--1);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
  `,
})
export class QuestionCardComponent {
  readonly question = input.required<Question>();
  readonly busy = input(false);
  /** Projet et session : les documents du projet sont mentionnables avec @ dans la réponse (007). */
  readonly projectId = input<string | null>(null);
  readonly sessionId = input<string | null>(null);
  readonly answered = output<string>();
  readonly skipped = output<void>();

  protected readonly value = signal('');
  protected readonly defaultText = computed(
    () => `Si vous passez cette question, spec-writer retiendra « ${this.question().defaultValue ?? 'Non précisé'} » comme hypothèse.`,
  );
  protected readonly themeLabel = computed(() => THEMES[this.question().theme] ?? this.question().theme);
  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');

  focus(): void {
    this.heading().nativeElement.focus();
  }
}
