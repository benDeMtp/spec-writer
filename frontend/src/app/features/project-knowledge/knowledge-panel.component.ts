import { provenanceLabel, RELIABILITY_LABELS } from '../../core/knowledge/provenance';
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
  IndexationMode,
  IndexableSource,
  KnowledgeHit,
  KnowledgeKind,
  KnowledgeStatus,
  ModelState,
} from '../../core/projects/projects.model';
import { count } from '../../shared/text';
import { IndexationProgressComponent, sourceLabel } from './indexation-progress.component';

/** Intervalle d'interrogation de l'indexation (research.md R14) ; réduit par les tests. */
export const KNOWLEDGE_POLL_MS = new InjectionToken<number>('KNOWLEDGE_POLL_MS', { factory: () => 1000 });
export { KNOWLEDGE_CLOCK } from './indexation-progress.component';

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
  imports: [FormsModule, IndexationProgressComponent, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, RouterLink],
  template: `
    @if (status(); as s) {
      <p class="counts" data-tour="knowledge-state">
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
      <sw-indexation-progress [indexation]="g" [sources]="status()?.sources ?? []" [busy]="busy()" (interrupt)="cancel()" />
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
                @if (provenance(h); as p) {
                  <p class="meta provenance" data-testid="hit-provenance">{{ p }}</p>
                }
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

  protected readonly status = signal<KnowledgeStatus | null>(null);
  protected readonly indexation = signal<Indexation | null>(null);
  protected readonly hits = signal<KnowledgeHit[] | null>(null);

  /** Source, date et niveau d'un résultat (016) : « SharePoint · 12 septembre 2026 · Référence ». */
  protected provenance(h: KnowledgeHit): string {
    const label = provenanceLabel(h);
    return label && h.level ? `${label} · ${RELIABILITY_LABELS[h.level]}` : label;
  }
  protected readonly query = signal('');
  protected readonly kind = signal<KnowledgeKind | ''>('');
  protected readonly busy = signal(false);
  /** Choix des sources à réindexer ouvert, et sources cochées (005, US2). */
  protected readonly choosing = signal(false);
  protected readonly chosen = signal<ReadonlySet<string>>(new Set());
  private readonly chooseButton = viewChild('chooseButton', { read: ElementRef<HTMLButtonElement> });
  private readonly choiceLegend = viewChild<ElementRef<HTMLElement>>('choiceLegend');
  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private pollToken = 0;

  constructor() {
    effect(() => {
      const id = this.projectId();
      this.revision();
      untracked(() => void this.refresh(id));
    });
    inject(DestroyRef).onDestroy(() => this.stopPolling());
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
    return sourceLabel(s);
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
