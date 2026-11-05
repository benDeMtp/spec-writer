import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { SessionsApi } from '../../core/sessions/sessions.api';
import { Subtask, SubtaskReport } from '../../core/sessions/sessions.model';

/**
 * Rubrique « Sous-tâches de <clé> » d'une version (FR-046, FR-047 de 002) : les sous-tâches que la
 * version rend incohérentes d'abord, rattachées par le sens à un cas d'usage (« probable ») ; les
 * cohérentes et les non rattachées repliées. Chargée à part, sans retarder la spécification.
 */
@Component({
  selector: 'sw-subtasks-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, NgTemplateOutlet],
  template: `
    @if (report(); as r) {
      <section aria-labelledby="subtasks-title" class="subtasks">
        <h2 id="subtasks-title">Sous-tâches de {{ r.storyKey }}</h2>
        @if (r.parentKey) {
          <p class="hint">Story parente : {{ r.parentKey }}. Elle fait partie de la même story, ce n'est pas un travail concurrent.</p>
        }
        @if (r.matching === 'UNAVAILABLE') {
          <p class="hint" role="note">
            Rattachement aux cas d'usage indisponible{{ r.matchingProblem ? ' : ' + r.matchingProblem : '.' }}
          </p>
        }
        @if (!r.subtasks.length) {
          <p class="hint">Aucune sous-tâche.</p>
        }
        @if (toReview().length) {
          <h3>À revoir</h3>
          <ul>
            @for (t of toReview(); track t.key) {
              <ng-container *ngTemplateOutlet="row; context: { $implicit: t }" />
            }
          </ul>
        }
        @if (rework().length) {
          <h3>Livrées, à reprendre</h3>
          <ul>
            @for (t of rework(); track t.key) {
              <ng-container *ngTemplateOutlet="row; context: { $implicit: t }" />
            }
          </ul>
        }
        @if (consistent().length) {
          <details>
            <summary>Cohérentes ({{ consistent().length }})</summary>
            <ul>
              @for (t of consistent(); track t.key) {
                <ng-container *ngTemplateOutlet="row; context: { $implicit: t }" />
              }
            </ul>
          </details>
        }
        @if (unmatched().length) {
          <details>
            <summary>Non rattachées ({{ unmatched().length }})</summary>
            <ul>
              @for (t of unmatched(); track t.key) {
                <ng-container *ngTemplateOutlet="row; context: { $implicit: t }" />
              }
            </ul>
          </details>
        }
      </section>
    }

    <ng-template #row let-t>
      <li class="subtask">
        <p class="title">
          @if (t.url) {
            <a [href]="t.url" target="_blank" rel="noopener noreferrer"
              >{{ t.key }}<span class="sw-visually-hidden"> (nouvel onglet)</span></a
            >
          } @else {
            <span class="key">{{ t.key }}</span>
          }
          {{ t.title }}
          @if (t.status) {
            <span class="badge">{{ t.status }}</span>
          }
          @if (!t.statusFresh && t.status) {
            <span class="stale">
              statut indexé{{ t.statusDate ? ' le ' + (t.statusDate | date: 'd MMM y') : '' }} (Jira n'a pas pu être relu)
            </span>
          }
        </p>
        @if (t.useCase) {
          <p class="match">
            {{ changeLabel(t) }}
            <span class="probable">Probable :</span> {{ closeness(t) }} de {{ t.useCase.code }} « {{ t.useCase.title }} »{{
              t.alternative ? ', ou de ' + t.alternative.code + ' « ' + t.alternative.title + ' »' : ''
            }}.
          </p>
        }
      </li>
    </ng-template>
  `,
  styles: `
    .subtasks ul {
      list-style: none;
      padding: 0;
    }
    .subtask {
      margin-block: 0.5rem;
    }
    .subtask p {
      margin: 0.125rem 0;
    }
    .key {
      font-weight: 600;
    }
    .badge {
      margin-inline-start: 0.5rem;
      font-size: 0.875rem;
    }
    .stale,
    .hint {
      color: var(--sw-muted, #555);
    }
    .probable {
      font-style: italic;
    }
  `,
})
export class SubtasksPanelComponent {
  private readonly api = inject(SessionsApi);

  readonly sessionId = input.required<string>();
  readonly versionNumber = input.required<number>();

  protected readonly report = signal<SubtaskReport | null>(null);
  protected readonly toReview = computed(() => this.of('TO_REVIEW'));
  protected readonly rework = computed(() => this.of('DELIVERED_TO_REWORK'));
  protected readonly consistent = computed(() => this.of('CONSISTENT'));
  protected readonly unmatched = computed(() => this.of('UNMATCHED'));

  constructor() {
    effect(() => {
      const id = this.sessionId();
      const n = this.versionNumber();
      untracked(() => void this.load(id, n));
    });
  }

  private async load(id: string, n: number): Promise<void> {
    try {
      this.report.set(await this.api.subtasks(id, n));
    } catch {
      // la rubrique est un complément : son absence ne gêne pas la lecture de la version
      this.report.set(null);
    }
  }

  private of(state: Subtask['state']): Subtask[] {
    return this.report()?.subtasks.filter((t) => t.state === state) ?? [];
  }

  protected changeLabel(t: Subtask): string {
    if (!t.change || !t.useCase) {
      return '';
    }
    return `${t.useCase.code} ${t.change === 'REMOVED' ? 'a été supprimé' : 'a changé'}.`;
  }

  protected closeness(t: Subtask): string {
    return t.closeness === 'VERY_CLOSE' ? 'très proche' : 'proche';
  }
}
