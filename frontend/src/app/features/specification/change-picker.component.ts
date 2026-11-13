import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ChangeCandidate } from '../../core/conversations/conversations.model';

/** Longueur maximale d'un changement retenu, comme le serveur. */
export const MAX_CHANGE = 1_000;

/**
 * Changements à intégrer dans la nouvelle version (011) : ceux que l'agent a relevés dans la
 * discussion, tous cochés, avec qui les a avancés, plus ceux que la personne ajoute. Ce qu'elle
 * retient entre dans la discussion à son nom : la rédaction repose sur une décision explicite.
 */
@Component({
  selector: 'sw-change-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule],
  host: { '(keydown.escape)': 'cancelled.emit()' },
  template: `
    <fieldset #box class="picker">
      <legend>Changements à intégrer</legend>
      @if (candidates().length) {
        <p class="hint">Relevés par l’agent dans la discussion. Décochez ceux à écarter.</p>
        <ul>
          @for (c of candidates(); track $index) {
            <li>
              <label>
                <input
                  type="checkbox"
                  [checked]="checked()[$index]"
                  (change)="toggle($index)"
                />
                <span class="text">{{ c.text }}</span>
                <span class="origin" [class.agent]="c.origin === 'AGENT'">
                  {{ c.origin === 'USER' ? 'dit par vous' : 'proposé par l’agent' }}
                </span>
              </label>
            </li>
          }
        </ul>
      } @else {
        <p class="hint">
          L’agent n’a relevé aucun changement dans la discussion. Décrivez ce qui doit changer.
        </p>
      }
      <label class="other" for="other-change">Autre changement</label>
      <textarea
        id="other-change"
        rows="2"
        aria-describedby="other-change-help"
        [value]="other()"
        (input)="other.set($any($event.target).value)"
      ></textarea>
      <p id="other-change-help" class="hint small">Un changement par ligne.</p>
      @if (problem(); as p) {
        <p class="problem" role="alert">{{ p }}</p>
      }
      <div class="buttons">
        <button mat-button type="button" (click)="cancelled.emit()">Annuler</button>
        <button
          mat-flat-button
          type="button"
          [disabled]="!retained().length"
          [attr.aria-describedby]="retained().length ? null : 'picker-empty'"
          (click)="confirm()"
        >
          Rédiger la version
        </button>
      </div>
      @if (!retained().length) {
        <p id="picker-empty" class="hint small">Cochez ou décrivez au moins un changement.</p>
      }
    </fieldset>
  `,
  styles: `
    .picker {
      margin: 0 0 0.75rem;
      padding: 0.5rem 0.75rem 0.75rem;
      border: 1px solid var(--sw-rule);
      border-left: 3px solid var(--sw-pencil);
      border-radius: var(--sw-radius-field);
      background: var(--sw-sheet);
      color: var(--sw-ink);
    }
    legend {
      padding: 0 0.25rem;
      font-weight: 600;
    }
    ul {
      max-height: 14rem;
      overflow-y: auto;
      margin: 0.25rem 0 0.5rem;
      padding: 0;
      list-style: none;
    }
    li + li {
      border-top: 1px solid var(--sw-rule);
    }
    li label {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 0.1rem 0.5rem;
      padding: 0.35rem 0;
      cursor: pointer;
    }
    input[type='checkbox'] {
      grid-row: span 2;
      width: 1.1rem;
      height: 1.1rem;
      margin: 0.15rem 0 0;
      accent-color: var(--sw-pencil);
    }
    .text {
      overflow-wrap: anywhere;
    }
    .origin {
      color: var(--sw-ink-soft);
      font-size: var(--sw-step--1);
    }
    .origin.agent {
      font-style: italic;
    }
    .other {
      display: block;
      margin-top: 0.25rem;
      font-weight: 600;
    }
    textarea {
      box-sizing: border-box;
      width: 100%;
      margin-top: 0.25rem;
      padding: 0.4rem 0.5rem;
      font: inherit;
      color: var(--sw-ink);
      background: var(--sw-paper);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
      resize: vertical;
    }
    .hint {
      margin: 0.25rem 0;
      color: var(--sw-ink-soft);
    }
    .small {
      font-size: var(--sw-step--1);
    }
    .buttons {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 0.5rem;
      margin-top: 0.25rem;
    }
    .problem {
      padding: 0.25rem 0.5rem;
      border-left: 4px solid var(--mat-sys-error, #b3261e);
    }
  `,
})
export class ChangePickerComponent implements AfterViewInit {
  readonly candidates = input.required<ChangeCandidate[]>();
  /** Changements retenus, sans vide ni doublon, dans l'ordre affiché. */
  readonly confirmed = output<string[]>();
  readonly cancelled = output<void>();

  private readonly box = viewChild.required<ElementRef<HTMLElement>>('box');
  private readonly unchecked = signal<ReadonlySet<number>>(new Set());
  protected readonly other = signal('');

  protected readonly checked = computed(() =>
    this.candidates().map((_, i) => !this.unchecked().has(i)),
  );

  protected readonly retained = computed(() => {
    const picked = this.candidates()
      .filter((_, i) => !this.unchecked().has(i))
      .map((c) => c.text);
    const added = this.other()
      .split('\n')
      .map((l) => l.trim().replace(/^[-*•]\s*/, ''))
      .filter((l) => l.length > 0);
    return [...new Set([...picked, ...added])];
  });

  protected readonly problem = computed(() =>
    this.retained().some((c) => c.length > MAX_CHANGE)
      ? `Un changement dépasse ${MAX_CHANGE} caractères : raccourcissez-le.`
      : '',
  );

  /** Le focus va au premier changement relevé, ou au champ libre s'il n'y en a aucun. */
  ngAfterViewInit(): void {
    this.box().nativeElement.querySelector<HTMLElement>('input, textarea')?.focus();
  }

  protected toggle(index: number): void {
    this.unchecked.update((s) => {
      const next = new Set(s);
      if (!next.delete(index)) {
        next.add(index);
      }
      return next;
    });
  }

  protected confirm(): void {
    if (this.retained().length && !this.problem()) {
      this.confirmed.emit(this.retained());
    }
  }
}
