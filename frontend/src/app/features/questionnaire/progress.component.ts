import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Progression du questionnaire (FR-004), lisible par les lecteurs d'écran. */
@Component({
  selector: 'sw-progress',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="bar"
      role="progressbar"
      aria-label="Progression du questionnaire"
      aria-valuemin="0"
      [attr.aria-valuemax]="total()"
      [attr.aria-valuenow]="done()"
      [attr.aria-valuetext]="text()"
    >
      <span class="fill" [style.width.%]="percent()"></span>
    </div>
    <p class="label">{{ text() }}</p>
  `,
  styles: `
    .bar {
      height: 6px;
      border-radius: 999px;
      background: var(--sw-rule);
      overflow: hidden;
    }
    .fill {
      display: block;
      height: 100%;
      background: var(--sw-pencil);
      transition: width 200ms ease-out;
    }
    @media (prefers-reduced-motion: reduce) {
      .fill {
        transition: none;
      }
    }
    .label {
      margin: 0.5rem 0 0;
      color: var(--sw-ink-soft);
      font-size: var(--sw-step--1);
    }
  `,
})
export class ProgressComponent {
  readonly answered = input.required<number>();
  readonly skipped = input.required<number>();
  readonly total = input.required<number>();

  protected readonly done = computed(() => this.answered() + this.skipped());
  protected readonly percent = computed(() => (this.total() ? (100 * this.done()) / this.total() : 0));
  protected readonly text = computed(() => {
    const skipped = this.skipped() ? `, dont ${this.skipped()} passée${this.skipped() > 1 ? 's' : ''}` : '';
    return `${this.done()} question${this.done() > 1 ? 's' : ''} traitée${this.done() > 1 ? 's' : ''} sur ${this.total()}${skipped}`;
  });
}
