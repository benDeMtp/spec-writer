import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Outcome, PointOrigin, StopItem } from '../../core/interviews/interviews.model';
import { EditorKind, EditorValue, PointEditorComponent } from './point-editor.component';

const ORIGINS: Record<PointOrigin, string> = {
  ASSUMPTION: 'Hypothèse',
  SOURCE_DEVIATION: 'Écart entre sources',
  OPEN_QUESTION: 'Point ouvert',
  REMARK: 'Remarque',
  QUESTION: 'Question',
};

const DONE: Record<Outcome, string> = {
  CONFIRMED: '✓ Confirmé',
  CHANGE: '✎ À modifier',
  REMOVE: '✗ À retirer',
  DEFERRED: '⏳ Reporté',
};

/**
 * Un point dans le document de séance (021, conception du 20/11/2026) : pour une hypothèse ou un
 * écart, la réponse proposée est le texte de la spécification, confirmé d'un clic ; un point ouvert
 * appelle une réponse ; un point traité se replie en une ligne, que « Modifier » rouvre.
 */
@Component({
  selector: 'sw-point-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, PointEditorComponent],
  host: {
    '[class.pending]': 'item().pending',
    'data-card': '',
    tabindex: '-1',
  },
  template: `
    @let it = item();
    <section class="card" role="region" [attr.aria-label]="title()">
      <p class="head">
        <span class="kind">{{ title() }}</span>
        @if (it.point?.quote; as q) {
          <q class="quote">« {{ q }} »</q>
        }
        @if (it.statement) {
          <span class="statement">{{ it.statement }}</span>
        }
      </p>
      @if (editing(); as initial) {
        <sw-point-editor
          [kind]="kind()"
          [heading]="title() + (it.statement ? ' : ' + it.statement : '')"
          [initial]="initial"
          (saved)="emit($event)"
          (cancelled)="editing.set(null)"
        />
      } @else if (it.point?.outcome || (it.point && !it.pending)) {
        <p class="done">
          <strong>{{ doneLabel() }}</strong>
          @if (it.point?.text) {
            : {{ it.point?.text }}
          }
          @if (it.point?.owner) {
            ({{ it.point?.owner }})
          }
          <button mat-button type="button" (click)="reopen()">Modifier<span class="sr-only"> {{ title() }}</span></button>
          @if (removable()) {
            <button mat-button type="button" (click)="deleted.emit()">
              Supprimer la {{ it.origin === 'QUESTION' ? 'question' : 'remarque' }}
            </button>
          }
        </p>
      } @else if (proposed()) {
        <div class="actions">
          <button mat-flat-button type="button" (click)="decide('CONFIRMED')">Confirmer</button>
          <button mat-stroked-button type="button" (click)="open('CHANGE', it.statement ?? '')">Réécrire</button>
          <button mat-stroked-button type="button" (click)="decide('REMOVE')">Retirer</button>
          <button mat-button type="button" (click)="open('DEFERRED', it.statement ?? '')">Reporter…</button>
        </div>
      } @else {
        <div class="actions">
          <button mat-flat-button type="button" (click)="open('CHANGE', '')">Répondre</button>
          <button mat-button type="button" (click)="open('DEFERRED', '')">Reporter…</button>
        </div>
      }
    </section>
  `,
  styles: `
    :host {
      display: block;
      margin: 0.5rem 0;
      outline: none;
    }
    :host(:focus-visible) .card,
    :host(:focus) .card {
      box-shadow: 0 0 0 3px var(--sw-pencil);
    }
    .card {
      display: grid;
      gap: 0.5rem;
      padding: 0.75rem 1rem;
      border-left: 4px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
      background: var(--sw-sheet);
    }
    :host(.pending) .card {
      border-left-color: var(--sw-pencil);
    }
    .head {
      margin: 0;
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem 0.75rem;
      align-items: baseline;
    }
    .kind {
      font-weight: 600;
    }
    .quote {
      quotes: none;
      font-style: italic;
    }
    .done {
      margin: 0;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
    }
  `,
})
export class PointCardComponent {
  readonly item = input.required<StopItem>();
  readonly saved = output<EditorValue>();
  /** « Supprimer » : seulement une remarque ou une question notée en séance. */
  readonly deleted = output<void>();

  /** Saisie ouverte, avec sa valeur initiale ; nulle quand la carte est fermée. */
  protected readonly editing = signal<EditorValue | null>(null);

  protected readonly title = computed(() => {
    const it = this.item();
    return `${ORIGINS[it.origin]}${it.code ? ' ' + it.code : ''}`;
  });

  protected readonly kind = computed<EditorKind>(() => {
    const o = this.item().origin;
    return o === 'REMARK' ? 'REMARK' : o === 'QUESTION' ? 'QUESTION' : 'SPEC';
  });

  protected readonly removable = computed(() => {
    const it = this.item();
    return !!it.point && (it.origin === 'REMARK' || it.origin === 'QUESTION');
  });

  /** Une hypothèse ou un écart porte sa réponse proposée : le texte de la spécification. */
  protected readonly proposed = computed(() => this.item().origin === 'ASSUMPTION' || this.item().origin === 'SOURCE_DEVIATION');

  protected readonly doneLabel = computed(() => {
    const p = this.item().point;
    if (!p?.outcome) {
      return this.item().origin === 'QUESTION' ? '⏳ Reporté' : '✎ Noté';
    }
    return p.outcome === 'CHANGE' && this.proposed() ? '✎ Réécrit' : DONE[p.outcome];
  });

  protected decide(outcome: Outcome): void {
    this.emit({ text: '', outcome, owner: null, due: null, source: null });
  }

  protected open(outcome: Outcome, text: string): void {
    this.editing.set({ text, outcome, owner: null, due: null, source: null });
  }

  protected reopen(): void {
    const p = this.item().point!;
    this.editing.set({
      text: p.text,
      outcome: p.outcome,
      owner: p.owner,
      due: p.due,
      source: p.source ? { kind: p.source.kind as 'DOC_PAGE', ref: p.source.ref } : null,
    });
  }

  protected emit(value: EditorValue): void {
    this.editing.set(null);
    this.saved.emit(value);
  }
}
