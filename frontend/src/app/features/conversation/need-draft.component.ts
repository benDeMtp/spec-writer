import { HttpErrorResponse } from '@angular/common/http';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ConversationsApi } from '../../core/conversations/conversations.api';
import { EstablishedPoint, NeedDraft } from '../../core/conversations/conversations.model';
import { isProblem } from '../../core/http/problem';
import { THEMES } from '../questionnaire/question-card.component';

/**
 * Brouillon proposé par l'agent (010, US2) : l'utilisateur corrige le besoin, corrige ou retire les
 * points établis, puis démarre la spécification. Les points deviennent des réponses déjà données
 * du questionnaire.
 */
@Component({
  selector: 'sw-need-draft',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule],
  template: `
    <section class="draft" aria-labelledby="draft-title">
      <h2 id="draft-title" tabindex="-1" #title>Rédiger une spécification</h2>
      <p class="hint">
        Relisez ce que l’agent a retenu de la conversation. Les points gardés seront des réponses déjà données : le
        questionnaire ne les reposera pas.
      </p>
      <form (ngSubmit)="start()" novalidate>
        <label for="draft-need">Besoin à spécifier</label>
        <textarea
          #needField
          id="draft-need"
          name="need"
          rows="4"
          [(ngModel)]="need"
          maxlength="5000"
          autocomplete="off"
          [attr.aria-invalid]="needMissing() ? true : null"
          [attr.aria-describedby]="needMissing() ? 'draft-need-error' : null"
        ></textarea>
        @if (needMissing()) {
          <p id="draft-need-error" class="field-error">Décrivez le besoin à spécifier : qui doit pouvoir faire quoi.</p>
        }

        @if (points().length) {
          <ol class="points" aria-label="Points établis">
            @for (p of points(); track $index; let i = $index) {
              <li>
                <fieldset>
                  <legend>{{ theme(p) }}</legend>
                  <label [for]="'q-' + i">Question</label>
                  <input
                    [id]="'q-' + i"
                    [name]="'q-' + i"
                    [ngModel]="p.question"
                    (ngModelChange)="edit(i, { question: $event })"
                    maxlength="500"
                    autocomplete="off"
                  />
                  <label [for]="'a-' + i">Réponse</label>
                  <textarea
                    [id]="'a-' + i"
                    [name]="'a-' + i"
                    rows="2"
                    [ngModel]="p.answer"
                    (ngModelChange)="edit(i, { answer: $event })"
                    maxlength="5000"
                    autocomplete="off"
                  ></textarea>
                  <div class="bar">
                    <span class="hint">{{ p.basis === 'SOURCE' ? 'Établi par la source ' + p.sourceRef : 'Dit dans la conversation' }}</span>
                    <button mat-button type="button" (click)="remove(i)">
                      Retirer ce point<span class="sw-visually-hidden"> : {{ p.question }}</span>
                    </button>
                  </div>
                </fieldset>
              </li>
            }
          </ol>
        } @else {
          <p class="hint">Aucun point établi : le questionnaire posera toutes ses questions.</p>
        }

        @if (problem()) {
          <p class="problem" role="alert">{{ problem() }}</p>
        }
        <div class="actions">
          <button mat-flat-button type="submit" [disabled]="busy()">
            {{ busy() ? 'Démarrage…' : 'Démarrer la spécification' }}
          </button>
          <button mat-button type="button" [disabled]="busy()" (click)="cancelled.emit()">Annuler</button>
        </div>
      </form>
    </section>
  `,
  styles: `
    .draft {
      margin: 1.5rem 0 0;
      padding: 1.25rem 1.5rem;
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-left: 3px solid var(--sw-pencil);
      border-radius: var(--sw-radius-sheet);
    }
    h2 {
      margin: 0 0 0.5rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-1);
    }
    label {
      display: block;
      margin: 0.75rem 0 0.25rem;
      font-weight: 600;
    }
    textarea,
    input {
      box-sizing: border-box;
      width: 100%;
      padding: 0.5rem 0.6rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
      background: var(--sw-paper);
      color: var(--sw-ink);
      font: inherit;
    }
    .points {
      list-style: none;
      margin: 1rem 0 0;
      padding: 0;
    }
    fieldset {
      margin: 0 0 0.75rem;
      padding: 0.25rem 0.9rem 0.5rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
    }
    legend {
      padding: 0 0.3rem;
      color: var(--sw-role);
      font-weight: 600;
    }
    .bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      margin-top: 0.25rem;
    }
    .hint {
      color: var(--sw-ink-soft);
      font-size: var(--sw-step--1);
    }
    .actions {
      display: flex;
      gap: 0.75rem;
      margin-top: 1rem;
    }
    .field-error {
      margin: 0.25rem 0 0;
      color: var(--mat-sys-error, #b3261e);
    }
    .problem {
      padding: 0.5rem 0.75rem;
      border-left: 4px solid var(--mat-sys-error, #b3261e);
    }
  `,
})
export class NeedDraftComponent implements AfterViewInit {
  readonly conversationId = input.required<string>();
  readonly draft = input.required<NeedDraft>();
  readonly started = output<string>();
  readonly cancelled = output<void>();

  private readonly api = inject(ConversationsApi);
  private readonly title = viewChild<ElementRef<HTMLElement>>('title');
  private readonly needField = viewChild<ElementRef<HTMLTextAreaElement>>('needField');

  protected readonly need = signal('');
  protected readonly points = signal<EstablishedPoint[]>([]);
  protected readonly busy = signal(false);
  protected readonly problem = signal('');
  protected readonly needMissing = signal(false);

  ngAfterViewInit(): void {
    this.need.set(this.draft().need);
    this.points.set(this.draft().points);
    this.title()?.nativeElement.focus();
  }

  protected theme(p: EstablishedPoint): string {
    return THEMES[p.theme] ?? p.theme;
  }

  protected edit(index: number, change: Partial<EstablishedPoint>): void {
    this.points.update((list) => list.map((p, i) => (i === index ? { ...p, ...change } : p)));
  }

  protected remove(index: number): void {
    this.points.update((list) => list.filter((_, i) => i !== index));
    this.title()?.nativeElement.focus();
  }

  protected async start(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.needMissing.set(!this.need().trim());
    if (this.needMissing()) {
      this.needField()?.nativeElement.focus();
      return;
    }
    this.busy.set(true);
    this.problem.set('');
    try {
      const points = this.points().filter((p) => p.question.trim() && p.answer.trim());
      const session = await this.api.specify(this.conversationId(), { need: this.need().trim(), points });
      this.started.emit(session.id);
    } catch (e) {
      this.problem.set(
        e instanceof HttpErrorResponse && isProblem(e.error)
          ? e.error.userMessage
          : 'La spécification n’a pas pu être démarrée. Réessayez.',
      );
    } finally {
      this.busy.set(false);
    }
  }
}
