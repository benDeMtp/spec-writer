import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  InjectionToken,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { KnowledgeApi } from '../../core/projects/knowledge.api';
import {
  Indexation,
  IndexationError,
  IndexationMode,
  IndexationStep,
  IndexationTickets,
  IndexableSource,
  KnowledgeHit,
  KnowledgeKind,
  KnowledgeStatus,
  ModelState,
} from '../../core/projects/projects.model';
import { count, formatDuration } from '../../shared/text';

/** Intervalle d'interrogation de l'indexation (research.md R14) ; réduit par les tests. */
export const KNOWLEDGE_POLL_MS = new InjectionToken<number>('KNOWLEDGE_POLL_MS', { factory: () => 1000 });
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

const MODEL_STATES: Record<ModelState, string> = {
  MISSING: 'pas encore téléchargé',
  DOWNLOADING: 'téléchargement en cours',
  READY: 'prêt',
  ERROR: 'en erreur',
  TERMS_ONLY: 'indisponible : la recherche se fait par les termes',
};

const KINDS: Record<KnowledgeKind, string> = {
  TICKET: 'Ticket',
  DOC_SECTION: 'Documentation',
  ADR: 'ADR',
  ARCH_ELEMENT: "Élément d'architecture",
};

/**
 * Connaissance indexée d'un projet (US2 de 002) : état et compteurs, indexation en arrière-plan
 * suivie (étape, élément en cours, minuteur, interruption, erreurs) et recherche d'essai. Rien de ce
 * qui est indexé ne quitte le poste.
 */
@Component({
  selector: 'sw-knowledge-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, RouterLink],
  template: `
    @if (status(); as s) {
      <p class="counts">
        {{ countsBefore(s) }}
        @if (s.adrs > 0) {
          <a routerLink="/projet/connaissance/adr">{{ adrCount(s) }}</a>
        } @else {
          {{ adrCount(s) }}
        }
        · {{ architectureCount(s) }}
        @if (s.partial) {
          <span class="partial">Résultats partiels</span>
        }
      </p>
      <p class="meta">{{ synced(s) }} · Modèle de recherche : {{ modelLabel(s.model) }}</p>
    }

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
          <button mat-stroked-button type="button" (click)="cancel()" [disabled]="busy()">Interrompre</button>
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
    @if (indexation()?.status !== 'RUNNING') {
      <div class="actions">
        <button mat-stroked-button type="button" (click)="start('INCREMENTAL')" [disabled]="busy()">Resynchroniser</button>
        <button mat-button type="button" (click)="start('FULL')" [disabled]="busy()">Tout réindexer</button>
        @if (status()?.sources?.length) {
          <button #chooseButton mat-button type="button" (click)="openChoice()" [disabled]="busy()" [attr.aria-expanded]="choosing()">Réindexer des sources…</button>
        }
      </div>
      @if (choosing()) {
        <fieldset class="choice">
          <legend #choiceLegend tabindex="-1">Sources à réindexer</legend>
          @for (s of status()?.sources ?? []; track s.id) {
            <label><input type="checkbox" [checked]="chosen().has(s.id)" (change)="toggle(s.id)" /> {{ sourceLabel(s) }}</label>
          }
          <div class="actions">
            <button mat-stroked-button type="button" (click)="startChosen()" [disabled]="busy() || chosen().size === 0">Réindexer la sélection</button>
            <button mat-button type="button" (click)="closeChoice()">Annuler</button>
          </div>
        </fieldset>
      }
    }

    <form class="search" (ngSubmit)="search()" aria-label="Recherche d'essai">
      <h3>Recherche d'essai</h3>
      <p class="hint">Pour vérifier ce que spec-writer retrouve. La recherche se fait sur le poste, sans appel à Jira ni à l'IA.</p>
      <div class="fields">
        <mat-form-field appearance="outline" class="grow">
          <mat-label>Rechercher</mat-label>
          <input matInput name="q" maxlength="2000" [ngModel]="query()" (ngModelChange)="query.set($event)" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Dans</mat-label>
          <mat-select name="kind" [value]="kind()" (selectionChange)="kind.set($event.value)">
            <mat-option value="">Tout</mat-option>
            <mat-option value="TICKET">Tickets</mat-option>
            <mat-option value="DOC_SECTION">Documentation</mat-option>
            <mat-option value="ADR">ADR</mat-option>
            <mat-option value="ARCH_ELEMENT">Architecture</mat-option>
          </mat-select>
        </mat-form-field>
        <button mat-stroked-button type="submit" [disabled]="!query().trim() || busy()">Rechercher</button>
      </div>
      @if (hits(); as hs) {
        @if (hs.length) {
          <ol class="hits" aria-label="Résultats">
            @for (h of hs; track h.kind + h.ref) {
              <li>
                <p class="hit-title">
                  <span class="kind">{{ kindLabel(h.kind) }}</span>
                  @if (h.url) {
                    <a [href]="h.url" target="_blank" rel="noopener">{{ h.ref }}</a>
                  } @else {
                    <span class="ref">{{ h.ref }}</span>
                  }
                  {{ h.title }}
                  @if (h.status) {
                    <span class="meta">· {{ h.status }}</span>
                  }
                  @if (h.scope) {
                    <span class="meta">· {{ h.scope === 'PROJECT' ? 'propre au projet' : 'commun' }}</span>
                  }
                </p>
                <p class="excerpt">{{ h.excerpt }}</p>
              </li>
            }
          </ol>
        } @else {
          <p class="hint">Aucun résultat.</p>
        }
      }
    </form>
  `,
  styles: `
    .counts {
      margin: 0 0 0.25rem;
      font-weight: 600;
    }
    .partial {
      margin-left: 0.5rem;
      padding: 0 0.4rem;
      border: 1px solid var(--sw-outcome);
      border-radius: var(--sw-radius-field);
      font-weight: 500;
    }
    .meta,
    .hint {
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
    .errors li,
    .excerpt {
      overflow-wrap: anywhere;
    }
    .errors {
      margin: 0.5rem 0;
      padding-left: 1.25rem;
    }
    .choice {
      margin: 0.75rem 0 0;
      border: 1px solid var(--sw-line, #ccc);
      border-radius: 4px;
      padding: 0.5rem 0.75rem;
    }
    .choice label {
      display: block;
      margin: 0.25rem 0;
    }
    .actions,
    .fields {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.5rem;
    }
    .grow {
      flex: 1 1 18rem;
    }
    h3 {
      margin: 1.5rem 0 0.25rem;
      font-size: var(--sw-step-0);
      font-weight: 650;
    }
    .hits {
      margin: 0;
      padding-left: 1.25rem;
    }
    .hits li {
      margin-bottom: 0.75rem;
    }
    .hit-title {
      margin: 0;
    }
    .kind {
      margin-right: 0.5rem;
      padding: 0 0.4rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
    }
    .ref,
    a {
      margin-right: 0.25rem;
      font-weight: 600;
    }
    .excerpt {
      margin: 0.25rem 0 0;
      color: var(--sw-ink-soft);
    }
  `,
})
export class KnowledgePanelComponent {
  readonly projectId = input.required<string>();
  /** Change quand une source change : l'état et l'indexation sont relus. */
  readonly revision = input(0);

  private readonly api = inject(KnowledgeApi);
  private readonly pollMs = inject(KNOWLEDGE_POLL_MS);
  private readonly clock = inject(KNOWLEDGE_CLOCK);

  protected readonly status = signal<KnowledgeStatus | null>(null);
  protected readonly indexation = signal<Indexation | null>(null);
  protected readonly hits = signal<KnowledgeHit[] | null>(null);
  protected readonly query = signal('');
  protected readonly kind = signal<KnowledgeKind | ''>('');
  protected readonly busy = signal(false);
  /** Choix des sources à réindexer ouvert, et sources cochées (005, US2). */
  protected readonly choosing = signal(false);
  protected readonly chosen = signal<ReadonlySet<string>>(new Set());
  private readonly chooseButton = viewChild('chooseButton', { read: ElementRef<HTMLButtonElement> });
  private readonly choiceLegend = viewChild<ElementRef<HTMLElement>>('choiceLegend');
  private readonly now = signal(this.clock());
  private ticker: ReturnType<typeof setInterval> | null = null;
  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private pollToken = 0;

  constructor() {
    effect(() => {
      const id = this.projectId();
      this.revision();
      untracked(() => void this.refresh(id));
    });
    effect(() => {
      const running = this.indexation()?.status === 'RUNNING';
      untracked(() => (running ? this.startTicker() : this.stopTicker()));
    });
    inject(DestroyRef).onDestroy(() => {
      this.stopPolling();
      this.stopTicker();
    });
  }

  private async refresh(id: string): Promise<void> {
    const [status, current] = await Promise.all([this.api.status(id), this.api.current(id)]);
    this.status.set(status);
    this.indexation.set(current);
    if (current?.status === 'RUNNING') {
      this.poll(id);
    }
  }

  /** Tickets et sections, suivis de « · » : le nombre d'ADR est un lien vers leur page (004). */
  protected countsBefore(s: KnowledgeStatus): string {
    return `${count(s.tickets, 'ticket', 'tickets')} · ${count(s.docSections, 'section de documentation', 'sections de documentation')} ·`;
  }

  protected adrCount(s: KnowledgeStatus): string {
    return count(s.adrs, 'ADR', 'ADR');
  }

  protected architectureCount(s: KnowledgeStatus): string {
    return count(s.architectureElements, "élément d'architecture", "éléments d'architecture");
  }

  protected synced(s: KnowledgeStatus): string {
    return s.syncedAt
      ? `Synchronisée le ${new Date(s.syncedAt).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' })}`
      : 'Jamais synchronisée';
  }

  protected modelLabel(state: ModelState): string {
    return MODEL_STATES[state] ?? state;
  }

  protected kindLabel(kind: KnowledgeKind): string {
    return KINDS[kind];
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

  protected async start(mode: IndexationMode): Promise<void> {
    const id = this.projectId();
    await this.run(async () => {
      this.indexation.set(await this.api.start(id, mode));
      this.poll(id);
    });
  }

  protected openChoice(): void {
    this.chosen.set(new Set());
    this.choosing.set(true);
    setTimeout(() => this.choiceLegend()?.nativeElement.focus());
  }

  protected closeChoice(): void {
    this.choosing.set(false);
    setTimeout(() => this.chooseButton()?.nativeElement.focus());
  }

  protected toggle(id: string): void {
    const next = new Set(this.chosen());
    if (!next.delete(id)) {
      next.add(id);
    }
    this.chosen.set(next);
  }

  protected async startChosen(): Promise<void> {
    const id = this.projectId();
    const ids = (this.status()?.sources ?? []).filter((s) => this.chosen().has(s.id)).map((s) => s.id);
    this.choosing.set(false);
    await this.run(async () => {
      this.indexation.set(await this.api.start(id, 'FULL', ids));
      this.poll(id);
    });
  }

  protected sourceLabel(s: IndexableSource): string {
    switch (s.kind) {
      case 'DOCUMENTATION':
        return `Documentation : ${s.label}`;
      case 'ARCHITECTURE':
        return `Modèle d'architecture : ${s.label}`;
      default:
        return s.label;
    }
  }

  protected chosenLabel(ids: string[]): string {
    const known = this.status()?.sources ?? [];
    return ids.map((id) => known.find((s) => s.id === id)).filter((s): s is IndexableSource => !!s)
      .map((s) => this.sourceLabel(s)).join(' ; ');
  }

  protected async cancel(): Promise<void> {
    const id = this.projectId();
    await this.run(async () => {
      await this.api.cancel(id);
      this.poll(id, 0);
    });
  }

  protected async search(): Promise<void> {
    const q = this.query().trim();
    if (!q) {
      return;
    }
    const kind = this.kind();
    await this.run(async () => this.hits.set(await this.api.search(this.projectId(), q, kind ? [kind] : [])));
  }

  private poll(id: string, delayMs = this.pollMs): void {
    this.stopPolling();
    const token = ++this.pollToken;
    this.pollTimer = setTimeout(async () => {
      if (token !== this.pollToken) {
        return;
      }
      try {
        const g = await this.api.current(id);
        if (token !== this.pollToken) {
          return;
        }
        this.indexation.set(g);
        if (g?.status === 'RUNNING') {
          this.poll(id);
        } else {
          this.status.set(await this.api.status(id));
        }
      } catch {
        // le bandeau affiche la cause ; l'utilisateur peut relancer
      }
    }, delayMs);
  }

  private stopPolling(): void {
    this.pollToken++;
    if (this.pollTimer) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
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

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
    } catch {
      // le bandeau affiche la cause (indexation déjà en cours, recherche invalide)
    } finally {
      this.busy.set(false);
    }
  }
}
