import { ChangeDetectionStrategy, Component, DestroyRef, InjectionToken, effect, inject, input, output, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { Indexation, IndexationError, IndexationMode, IndexationStep, IndexationTickets, IndexableSource } from '../../core/projects/projects.model';
import { count, formatDuration } from '../../shared/text';

/** Horloge du minuteur ; remplacée par les tests. */
export const KNOWLEDGE_CLOCK = new InjectionToken<() => number>('KNOWLEDGE_CLOCK', { factory: () => () => Date.now() });

const STEPS: Record<IndexationStep, string> = {
  MODEL: 'Préparation du modèle de recherche',
  JIRA: 'Lecture des tickets Jira',
  DOCUMENTATION: 'Lecture de la documentation',
  ARCHITECTURE: "Lecture du modèle d'architecture",
  EMBEDDING: 'Calcul des vecteurs de recherche',
};

const ERROR_KINDS: Record<IndexationError['kind'], string> = {
  PAGE: 'Page non lue',
  SOURCE: 'Source non lue',
  JIRA: 'Jira',
  ARCHITECTURE: "Modèle d'architecture",
  MODEL: 'Modèle de recherche',
  FAILURE: 'Échec',
  WARNING: 'Avertissement',
};

/** Raisons d'un dossier de documents (006), dites en clair ; les autres restent leur code. */
const REASONS: Record<string, string> = {
  PROTECTED: 'protégé par mot de passe',
  NO_TEXT: 'sans texte, document scanné ?',
  UNAVAILABLE: 'non disponible, fichier OneDrive non téléchargé hors ligne ?',
  IGNORED_FORMAT: 'formats non pris en charge',
};

/**
 * Progression ou issue d'une indexation (002, 005) : étape, minuteur, barre, élément en cours,
 * interruption, puis bilan et erreurs. Affichage seul : l'interrogation reste à l'écran qui
 * l'utilise (connaissance du projet, assistant de premier lancement, 018 R4).
 */
@Component({
  selector: 'sw-indexation-progress',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, RouterLink],
  template: `
    @if (indexation(); as g) {
      @if (g.status === 'RUNNING') {
        <div class="progress" role="group" aria-labelledby="indexation-step">
          <p id="indexation-step" class="step">{{ stepLabel(g) }}</p>
          <p class="meta" role="timer">{{ elapsed(g) }}</p>
          @if (g.total > 0) {
            <progress [value]="g.done" [max]="g.total" [attr.aria-label]="stepLabel(g)"></progress>
            <p class="meta">{{ g.done }} sur {{ g.total }}</p>
          } @else {
            <progress [attr.aria-label]="stepLabel(g)"></progress>
          }
          @if (g.current) {
            <p class="current">En cours : <span>{{ g.current }}</span></p>
          }
          <button mat-stroked-button type="button" (click)="interrupt.emit()" [disabled]="busy()">Interrompre</button>
        </div>
      } @else {
        <p class="outcome">{{ outcome(g) }}</p>
        @if (g.tickets; as t) {
          <p class="meta tickets">{{ ticketsLabel(g.mode, t) }}</p>
        }
        @if (g.sources; as ids) {
          <p class="meta reindexed">Sources réindexées : {{ chosenLabel(ids) }}</p>
        }
      }
      @if (g.errors.length) {
        <details class="errors-detail">
          <summary>{{ detailLabel(g) }}</summary>
          <ul class="errors">
            @for (e of g.errors; track $index) {
              <li>{{ errorLabel(e) }}</li>
            }
          </ul>
          <p><a routerLink="/journal">Voir le journal</a></p>
        </details>
      }
    }
  `,
  styles: `
    .meta {
      margin: 0.25rem 0 0.75rem;
      color: var(--sw-ink-soft);
    }
    .progress {
      display: grid;
      gap: 0.25rem;
      margin-bottom: 1rem;
    }
    .progress progress {
      width: 100%;
      height: 0.75rem;
    }
    .step,
    .outcome {
      margin: 0 0 0.5rem;
      font-weight: 650;
    }
    .current span,
    .errors li {
      overflow-wrap: anywhere;
    }
    .errors {
      margin: 0.5rem 0;
      padding-left: 1.25rem;
    }
  `,
})
export class IndexationProgressComponent {
  readonly indexation = input.required<Indexation>();
  /** Sources connues du projet, pour nommer celles d'une réindexation choisie (005). */
  readonly sources = input<IndexableSource[]>([]);
  readonly busy = input(false);
  /** « Interrompre » : l'écran qui suit l'indexation l'arrête. */
  readonly interrupt = output<void>();

  private readonly clock = inject(KNOWLEDGE_CLOCK);
  private readonly now = signal(this.clock());
  private ticker: ReturnType<typeof setInterval> | null = null;

  constructor() {
    effect(() => {
      const running = this.indexation().status === 'RUNNING';
      untracked(() => (running ? this.startTicker() : this.stopTicker()));
    });
    inject(DestroyRef).onDestroy(() => this.stopTicker());
  }

  protected stepLabel(g: Indexation): string {
    return g.step ? STEPS[g.step] : 'Démarrage';
  }

  protected elapsed(g: Indexation): string {
    const total = formatDuration(this.now() - Date.parse(g.startedAt));
    return g.stepStartedAt
      ? `Temps écoulé : ${total} · étape en cours depuis ${formatDuration(this.now() - Date.parse(g.stepStartedAt))}`
      : `Temps écoulé : ${total}`;
  }

  protected outcome(g: Indexation): string {
    const duration = g.finishedAt ? ` en ${formatDuration(Date.parse(g.finishedAt) - Date.parse(g.startedAt))}` : '';
    switch (g.status) {
      case 'SUCCEEDED':
        if (g.errors.some((e) => e.kind !== 'WARNING')) {
          return `Indexation terminée${duration}, avec des erreurs.`;
        }
        return g.errors.length ? `Indexation terminée${duration}, avec des avertissements.` : `Indexation terminée${duration}.`;
      case 'CANCELLED':
        return 'Indexation interrompue : ce qui était déjà indexé est gardé.';
      default:
        return 'Indexation en échec : ce qui était déjà indexé est gardé.';
    }
  }

  protected ticketsLabel(mode: IndexationMode, t: IndexationTickets): string {
    const parts = [count(t.read, 'ticket relu', 'tickets relus')];
    if (mode === 'FULL' || t.keptClosed > 0) {
      parts.push(`${count(t.keptClosed, 'ticket fermé gardé', 'tickets fermés gardés')} sans relecture`);
    }
    parts.push(count(t.removed, 'retiré', 'retirés'));
    return parts.join(', ');
  }

  protected detailLabel(g: Indexation): string {
    const warnings = g.errors.filter((e) => e.kind === 'WARNING').length;
    const errors = g.errors.length - warnings;
    const parts = [
      errors ? `${errors} erreur${errors > 1 ? 's' : ''}` : '',
      warnings ? `${warnings} avertissement${warnings > 1 ? 's' : ''}` : '',
    ].filter((p) => p);
    return `Détail (${parts.join(', ')})`;
  }

  protected errorLabel(e: IndexationError): string {
    const where = e.location ? ` : ${e.location}` : '';
    if (e.reason === 'UNEXPECTED') {
      // erreur inattendue d'une étape (005) : les étapes suivantes ont continué
      return `${ERROR_KINDS[e.kind]}${where} (${e.message ?? 'Erreur inattendue'} ; les autres sources ont été indexées)`;
    }
    const http = e.httpStatus ? ` ${e.httpStatus}` : '';
    const message = e.message ? `, ${e.message}` : '';
    return `${ERROR_KINDS[e.kind]}${where} (${REASONS[e.reason] ?? e.reason}${http}${message})`;
  }

  protected chosenLabel(ids: string[]): string {
    return ids
      .map((id) => this.sources().find((s) => s.id === id))
      .filter((s): s is IndexableSource => !!s)
      .map((s) => sourceLabel(s))
      .join(' ; ');
  }

  private startTicker(): void {
    this.now.set(this.clock());
    this.ticker ??= setInterval(() => this.now.set(this.clock()), 1000);
  }

  private stopTicker(): void {
    if (this.ticker) {
      clearInterval(this.ticker);
      this.ticker = null;
    }
  }
}

/** Libellé d'une source indexable (005) : « Documentation : … », « Modèle d'architecture : … ». */
export function sourceLabel(s: IndexableSource): string {
  switch (s.kind) {
    case 'DOCUMENTATION':
      return `Documentation : ${s.label}`;
    case 'ARCHITECTURE':
      return `Modèle d'architecture : ${s.label}`;
    default:
      return s.label;
  }
}
