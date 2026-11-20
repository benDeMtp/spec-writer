import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

/**
 * Bulle d'un arrêt de la visite guidée (018, FR-014, FR-018) : titre, explication, position dans
 * la visite, et Précédent, Suivant, Quitter. Le focus reste dans la bulle tant qu'elle est ouverte.
 */
@Component({
  selector: 'sw-tour-bubble',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [A11yModule, MatButtonModule],
  template: `
    <div
      class="bubble"
      role="dialog"
      aria-modal="true"
      aria-labelledby="tour-title"
      aria-describedby="tour-text"
      cdkTrapFocus
      [cdkTrapFocusAutoCapture]="true"
    >
      <p class="position">Arrêt {{ index() + 1 }} sur {{ total() }}</p>
      <h2 id="tour-title">{{ title() }}</h2>
      <p id="tour-text">{{ text() }}</p>
      <div class="actions">
        @if (index() > 0) {
          <button mat-button type="button" (click)="previous.emit()">Précédent</button>
        }
        <button mat-flat-button type="button" cdkFocusInitial (click)="next.emit()">
          {{ index() + 1 === total() ? 'Terminer' : 'Suivant' }}
        </button>
        <button mat-button type="button" (click)="quit.emit()">Quitter la visite</button>
      </div>
    </div>
  `,
  styles: `
    .bubble {
      max-width: 22rem;
      padding: 1rem 1.25rem;
      background: var(--sw-sheet);
      color: var(--sw-ink);
      border: 2px solid var(--sw-ink);
      border-radius: var(--sw-radius-sheet);
      box-shadow: 0 0.5rem 1.5rem rgb(0 0 0 / 20%);
    }
    .position {
      margin: 0;
      font-size: var(--sw-step--1);
      color: var(--sw-ink-soft);
    }
    h2 {
      margin: 0.25rem 0 0.5rem;
      font-size: var(--sw-step-1);
      font-weight: 600;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
  `,
})
export class TourBubbleComponent {
  readonly index = input.required<number>();
  readonly total = input.required<number>();
  readonly title = input.required<string>();
  readonly text = input.required<string>();
  readonly previous = output<void>();
  readonly next = output<void>();
  readonly quit = output<void>();
}
