import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MarkdownComponent } from 'ngx-markdown';
import { ContextProposal, ProposalQuestion, ProposedSheet, SheetKind } from '../../core/projects/projects.model';
import { UnifiedDiffComponent } from '../../shared/ui/unified-diff.component';
import { SHEET_LABELS } from './sheet-editor.component';

export interface QuestionReply {
  question: ProposalQuestion;
  answer: string | null;
  skip: boolean;
}

/**
 * Proposition de l'IA (FR-013a, FR-013h) : chaque fiche avec son diff, acceptée ou gardée
 * séparément, puis les questions de clarification et la révision avec les réponses.
 */
@Component({
  selector: 'sw-context-proposal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MarkdownComponent, MatButtonModule, MatFormFieldModule, MatInputModule, UnifiedDiffComponent],
  template: `
    @for (kind of kinds; track kind) {
      @let s = sheetOf(kind);
      <section class="sheet-proposal" [attr.aria-labelledby]="'proposed-' + kind">
        <h3 [id]="'proposed-' + kind">{{ labels[kind].name }} proposée</h3>
        @if (s.accepted) {
          <p class="outcome">{{ labels[kind].name }} acceptée et enregistrée.</p>
        } @else if (dismissed().includes(kind)) {
          <p class="outcome">{{ labels[kind].name }} actuelle conservée.</p>
        } @else if (!s.changed) {
          <p class="hint">Identique à la fiche actuelle.</p>
        } @else {
          <sw-unified-diff [diff]="s.unifiedDiff" [label]="'Différences avec la ' + labels[kind].name.toLowerCase() + ' actuelle'" />
          <div class="rendered" role="region" [attr.aria-label]="'Contenu de la ' + labels[kind].name.toLowerCase() + ' proposée'">
            <markdown class="rich" [data]="s.markdown" />
          </div>
          <div class="actions">
            <button mat-flat-button type="button" (click)="accept.emit(kind)" [disabled]="busy()">
              Accepter la {{ labels[kind].name.toLowerCase() }}
            </button>
            <button mat-button type="button" (click)="keep.emit(kind)" [disabled]="busy()"
              [attr.aria-label]="'Garder la mienne : ' + labels[kind].name.toLowerCase() + ' actuelle'">
              Garder la mienne
            </button>
          </div>
        }
      </section>
    }
    @if (proposal().sources.length) {
      <p class="meta">Sources citées : {{ proposal().sources.length }}</p>
    }

    @if (proposal().questions.length) {
      <section aria-labelledby="questions-title">
        <h3 id="questions-title">Questions de l'assistant</h3>
        <p class="hint">
          Répondez à ce qui n'est pas clair, ou passez la question. Vos réponses sont gardées avec le projet et citées comme
          sources (Q-1, Q-2…).
        </p>
        <ol class="questions">
          @for (q of proposal().questions; track q.id) {
            <li>
              <p class="label">
                <span class="badge">{{ labels[q.sheet].adjective }}</span>
                @if (q.number) {
                  <span class="ref">Q-{{ q.number }}</span>
                }
                {{ q.label }}
              </p>
              @if (q.rationale) {
                <p class="meta">{{ q.rationale }}</p>
              }
              @if (q.state === 'OPEN' || editing() === q.id) {
                @if (q.suggestions.length) {
                  <div class="suggestions" role="group" [attr.aria-label]="'Suggestions pour « ' + q.label + ' »'">
                    @for (sug of q.suggestions; track sug) {
                      <button mat-stroked-button type="button" (click)="setText(q, sug)">{{ sug }}</button>
                    }
                  </div>
                }
                <mat-form-field appearance="outline" class="answer">
                  <mat-label>Votre réponse</mat-label>
                  <textarea matInput rows="2" maxlength="2000" [name]="'answer-' + q.id" [ngModel]="textOf(q)"
                    (ngModelChange)="setText(q, $event)"></textarea>
                </mat-form-field>
                <div class="actions">
                  <button mat-flat-button type="button" [disabled]="busy() || !textOf(q).trim()" (click)="reply(q, false)"
                    [attr.aria-label]="'Répondre à « ' + q.label + ' »'">
                    Répondre
                  </button>
                  <button mat-button type="button" [disabled]="busy()" (click)="reply(q, true)"
                    [attr.aria-label]="'Passer la question « ' + q.label + ' »'">Passer</button>
                </div>
              } @else {
                <p class="given">
                  @if (q.state === 'ANSWERED') {
                    Réponse : {{ q.answer }}
                  } @else {
                    Question passée : elle ne sera plus posée.
                  }
                  <button mat-button type="button" (click)="editing.set(q.id)" [attr.aria-label]="'Modifier la réponse à « ' + q.label + ' »'">
                    Modifier
                  </button>
                </p>
              }
            </li>
          }
        </ol>
        <div class="actions">
          <button mat-stroked-button type="button" [disabled]="busy() || !replied()" (click)="revise.emit()">
            Réviser la proposition
          </button>
        </div>
        <p class="hint">L'IA révise les deux fiches avec vos réponses, sans relire les sources.</p>
      </section>
    }
  `,
  styles: `
    h3 {
      margin: 1rem 0 0.5rem;
      font-size: var(--sw-step-0);
      font-weight: 650;
    }
    .meta,
    .hint {
      margin: 0.25rem 0 0.75rem;
      color: var(--sw-ink-soft);
    }
    .outcome {
      font-weight: 600;
    }
    .rendered {
      margin: 0.5rem 0 1rem;
      padding: 0.75rem 1rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
    }
    .actions,
    .suggestions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin-bottom: 0.5rem;
    }
    .questions {
      margin: 0;
      padding-left: 1.25rem;
    }
    .questions li {
      margin-bottom: 1rem;
    }
    .label {
      margin: 0;
      font-weight: 600;
    }
    .badge,
    .ref {
      margin-right: 0.5rem;
      padding: 0 0.4rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
      font-weight: 500;
      font-size: 0.9em;
    }
    .answer {
      width: 100%;
    }
    .given {
      margin: 0.25rem 0;
    }
  `,
})
export class ContextProposalComponent {
  readonly proposal = input.required<ContextProposal>();
  readonly busy = input(false);
  readonly dismissed = input<SheetKind[]>([]);
  readonly accept = output<SheetKind>();
  readonly keep = output<SheetKind>();
  readonly answer = output<QuestionReply>();
  readonly revise = output<void>();

  protected readonly kinds: SheetKind[] = ['FUNCTIONAL', 'TECHNICAL'];
  protected readonly labels = SHEET_LABELS;
  protected readonly editing = signal<string | null>(null);
  private readonly texts = signal<Record<string, string>>({});
  protected readonly replied = computed(() => this.proposal().questions.some((q) => q.state !== 'OPEN'));

  protected sheetOf(kind: SheetKind): ProposedSheet {
    return kind === 'FUNCTIONAL' ? this.proposal().functional : this.proposal().technical;
  }

  protected textOf(q: ProposalQuestion): string {
    return this.texts()[q.id] ?? (q.state === 'ANSWERED' ? (q.answer ?? '') : '');
  }

  protected setText(q: ProposalQuestion, text: string): void {
    this.texts.update((t) => ({ ...t, [q.id]: text }));
  }

  protected reply(q: ProposalQuestion, skip: boolean): void {
    this.editing.set(null);
    this.answer.emit({ question: q, answer: skip ? null : this.textOf(q).trim(), skip });
  }
}
