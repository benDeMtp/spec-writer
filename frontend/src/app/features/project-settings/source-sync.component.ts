import { SwDatePipe } from '../../shared/ui/sw-date.pipe';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { KnowledgeApi } from '../../core/projects/knowledge.api';
import { SourceSyncState, SyncInterval } from '../../core/projects/projects.model';
import { changeLabel } from '../../core/projects/source-changes';

/** Délais proposés, en minutes (FR-007) ; « Désactivée » par défaut. */
export const INTERVALS: { value: SyncInterval; minutes: number | null; label: string }[] = [
  { value: 'OFF', minutes: null, label: 'Désactivée' },
  { value: 'M15', minutes: 15, label: 'Toutes les 15 minutes' },
  { value: 'M30', minutes: 30, label: 'Toutes les 30 minutes' },
  { value: 'H1', minutes: 60, label: 'Toutes les heures' },
  { value: 'H4', minutes: 240, label: 'Toutes les 4 heures' },
  { value: 'D1', minutes: 1440, label: 'Une fois par jour' },
];

const KINDS: Record<SourceSyncState['kind'], string> = {
  JIRA: 'Jira',
  DOCUMENTATION: 'Documentation',
  ARCHITECTURE: "Modèle d'architecture",
};

export { changeLabel };

/**
 * Synchronisation automatique de chaque source du projet (017, US2) : délai réglable, dernière
 * synchronisation et son bilan, prochaine échéance (SC-006).
 */
@Component({
  selector: 'sw-source-sync',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SwDatePipe, MatFormFieldModule, MatSelectModule],
  template: `
    <p class="hint" id="source-sync-hint">
      Tant que spec-writer tourne, une source dont le délai est écoulé est relue seule, sans relire les autres. Une
      synchronisation manuelle repart le délai.
    </p>
    @if (states(); as list) {
      @if (list.length) {
        <ul class="sync-sources">
          @for (s of list; track s.sourceKey) {
            <li [attr.data-testid]="'source-sync-' + s.sourceKey">
              <p class="name"><span class="kind">{{ kind(s) }}</span> {{ s.label }}</p>
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>Synchronisation automatique</mat-label>
                <mat-select
                  [value]="s.interval"
                  (selectionChange)="change(s, $event.value)"
                  aria-describedby="source-sync-hint"
                >
                  @for (i of intervals; track i.value) {
                    <mat-option [value]="i.value">{{ i.label }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <p class="last">
                @if (s.last; as last) {
                  Dernière synchronisation : {{ last.at | swDate: 'd MMM y, HH:mm' }} ·
                  @if (last.outcome === 'ERROR') {
                    <strong>Échec</strong> ({{ last.errorCode }})
                    @if (s.lastSuccessAt) {
                      , dernière réussite le {{ s.lastSuccessAt | swDate: 'd MMM y, HH:mm' }}
                    }
                  } @else {
                    {{ changes(last.change) }}
                  }
                } @else {
                  Jamais synchronisée automatiquement.
                }
                @if (s.nextRunAt) {
                  <br />Prochaine synchronisation vers {{ s.nextRunAt | swDate: 'd MMM y, HH:mm' }}
                }
              </p>
            </li>
          }
        </ul>
      } @else {
        <p>Aucune source à synchroniser : déclarez une documentation, Jira ou un modèle d'architecture.</p>
      }
    }
    <p class="sw-visually-hidden" role="status" aria-live="polite">{{ announce() }}</p>
  `,
  styles: `
    .sync-sources {
      list-style: none;
      margin: 0.5rem 0 0;
      padding: 0;
      display: grid;
      gap: 1rem;
    }
    .sync-sources li {
      display: grid;
      grid-template-columns: minmax(12rem, 1fr) minmax(14rem, 18rem);
      gap: 0.25rem 1rem;
      align-items: center;
    }
    .name {
      margin: 0;
      overflow-wrap: anywhere;
    }
    .kind {
      font-weight: 600;
    }
    .last {
      grid-column: 1 / -1;
      margin: 0;
      color: var(--sw-muted, inherit);
    }
    mat-form-field {
      width: 100%;
    }
  `,
})
export class SourceSyncComponent {
  private readonly api = inject(KnowledgeApi);

  readonly projectId = input.required<string>();
  /** Change quand les sources du projet changent : la liste est relue. */
  readonly revision = input<unknown>(null);

  protected readonly intervals = INTERVALS;
  protected readonly states = signal<SourceSyncState[] | null>(null);
  protected readonly announce = signal('');

  constructor() {
    effect(() => {
      const id = this.projectId();
      this.revision();
      untracked(() => void this.load(id));
    });
  }

  protected kind(s: SourceSyncState): string {
    return KINDS[s.kind];
  }

  protected changes(change: { added: number; modified: number; removed: number }): string {
    const label = changeLabel(change);
    return label.charAt(0).toUpperCase() + label.slice(1);
  }

  protected async change(s: SourceSyncState, value: SyncInterval): Promise<void> {
    const interval = INTERVALS.find((i) => i.value === value)!;
    try {
      const updated = await this.api.setSyncInterval(this.projectId(), s.sourceKey, interval.minutes);
      this.states.update((list) => list?.map((x) => (x.sourceKey === s.sourceKey ? updated : x)) ?? null);
      this.announce.set(`Synchronisation automatique de ${s.label} : ${interval.label.toLowerCase()}.`);
    } catch {
      // le bandeau d'erreur global explique le refus ; la liste est relue
      await this.load(this.projectId());
    }
  }

  private async load(id: string): Promise<void> {
    try {
      this.states.set(await this.api.sourceSync(id));
    } catch {
      this.states.set([]);
    }
  }
}
