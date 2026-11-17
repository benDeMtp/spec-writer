import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { ORIGIN_LABELS, RELIABILITY_LABELS, Reliability, SourceOrigin } from '../../core/knowledge/provenance';
import { ContextApi } from '../../core/projects/context.api';
import { ReliabilityLevels } from '../../core/projects/projects.model';

/** Ce que veut dire chaque niveau, sous son sélecteur. */
const MEANINGS: Record<Reliability, string> = {
  REFERENCE: 'porte la demande d’origine : l’emporte en cas d’écart',
  INTERPRETATION: 'compréhension de l’équipe : suit la référence, l’écart est signalé',
  TO_CONFIRM: 'pas encore validée : l’IA dit quand elle s’y appuie',
};

/**
 * Niveau de fiabilité de chaque sorte de source déclarée du projet (016, FR-015, FR-016) : défaut
 * indiqué, changement pris en compte à la prochaine recherche, sans réindexation (FR-022).
 */
@Component({
  selector: 'sw-reliability',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatFormFieldModule, MatSelectModule],
  template: `
    <p class="hint" id="reliability-hint">
      En cas d’écart, une source de référence l’emporte et l’écart est signalé en citant les deux extraits. Par
      défaut, les documents SharePoint sont la référence (ils portent les demandes métier), le reste est une
      interprétation.
    </p>
    @if (levels(); as l) {
      <ul class="levels">
        @for (o of origins(); track o) {
          <li>
            <mat-form-field appearance="outline">
              <mat-label>{{ originLabel(o) }}</mat-label>
              <mat-select
                [value]="l.levels[o]"
                (selectionChange)="change(o, $event.value)"
                [attr.data-testid]="'reliability-' + o"
                aria-describedby="reliability-hint"
              >
                @for (r of choices; track r) {
                  <mat-option [value]="r">{{ label(r) }}@if (l.defaults[o] === r) { (par défaut)}</mat-option>
                }
              </mat-select>
              <mat-hint>{{ meaning(l.levels[o]) }}</mat-hint>
            </mat-form-field>
          </li>
        }
      </ul>
    }
    <p class="sw-visually-hidden" role="status" aria-live="polite">{{ announce() }}</p>
  `,
  styles: `
    .levels {
      list-style: none;
      margin: 0.5rem 0 0;
      padding: 0;
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr));
      gap: 0.25rem 1rem;
    }
    mat-form-field {
      width: 100%;
    }
  `,
})
export class ReliabilityComponent {
  private readonly api = inject(ContextApi);

  readonly projectId = input.required<string>();
  /** Sortes de source déclarées du projet, dans l'ordre d'affichage. */
  readonly origins = input.required<SourceOrigin[]>();

  protected readonly choices: Reliability[] = ['REFERENCE', 'INTERPRETATION', 'TO_CONFIRM'];
  protected readonly levels = signal<ReliabilityLevels | null>(null);
  protected readonly announce = signal('');

  constructor() {
    effect(() => {
      const id = this.projectId();
      void this.api.reliability(id).then((l) => this.levels.set(l)).catch(() => this.levels.set(null));
    });
  }

  protected originLabel(o: SourceOrigin): string {
    return ORIGIN_LABELS[o];
  }

  protected label(r: Reliability): string {
    return RELIABILITY_LABELS[r];
  }

  protected meaning(r: Reliability | undefined): string {
    return r ? MEANINGS[r] : '';
  }

  protected async change(origin: SourceOrigin, level: Reliability): Promise<void> {
    try {
      this.levels.set(await this.api.setReliability(this.projectId(), origin, level));
      this.announce.set(`${ORIGIN_LABELS[origin]} : ${RELIABILITY_LABELS[level]}. Pris en compte dès la prochaine recherche.`);
    } catch {
      // Le bandeau d'erreur affiche la cause.
    }
  }
}
