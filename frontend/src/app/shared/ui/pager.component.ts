import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

/**
 * Pages précédente et suivante d'une liste, avec la position annoncée aux lecteurs d'écran
 * (FR-021 de 002). Les boutons restent dans l'ordre du clavier ; une page indisponible les
 * désactive.
 */
@Component({
  selector: 'sw-pager',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule],
  template: `
    <nav class="pager" [attr.aria-label]="label()">
      <button mat-button type="button" [disabled]="!hasPrevious() || busy()" (click)="previous.emit()">
        Page précédente
      </button>
      <span class="position" aria-live="polite" aria-atomic="true">{{ position() }}</span>
      <button mat-button type="button" [disabled]="!hasNext() || busy()" (click)="next.emit()">Page suivante</button>
    </nav>
  `,
  styles: `
    .pager {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem 1rem;
      align-items: center;
      margin-top: 0.75rem;
    }
  `,
})
export class PagerComponent {
  readonly label = input.required<string>();
  /** « 26 à 50 sur 120 ». */
  readonly position = input.required<string>();
  readonly hasPrevious = input(false);
  readonly hasNext = input(false);
  /** Une page est en cours de chargement : pas de second clic. */
  readonly busy = input(false);
  readonly previous = output<void>();
  readonly next = output<void>();
}
