import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { ProjectsStore } from '../../core/projects/projects.store';
import { SearchApi } from '../../core/search/search.api';
import { JiraStatus, SearchResult } from '../../core/search/search.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { PagerComponent } from '../../shared/ui/pager.component';
import { count } from '../../shared/text';
import { ResultsTableComponent } from './results-table.component';

export const MIN_QUERY = 2;
export const MAX_QUERY = 200;

/** Ce que l'utilisateur doit savoir de Jira pour lire les résultats (FR-045). */
const JIRA_NOTICE: Partial<Record<JiraStatus, string>> = {
  NOT_CONFIGURED: "Jira n'est pas configuré : la recherche porte sur vos spécifications uniquement.",
  UNAVAILABLE: "Jira n'a pas répondu : seuls les résultats spec-writer sont affichés.",
  TOKEN_REJECTED: 'Jira a refusé le jeton : seuls les résultats spec-writer sont affichés.',
  CONSENT_REQUIRED: 'La recherche dans Jira demande votre accord : seuls les résultats spec-writer sont affichés.',
};

/** Recherche dans spec-writer et dans Jira, en une liste unique (FR-039 à FR-045). */
@Component({
  selector: 'sw-search-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, PagerComponent, ResultsTableComponent, RouterLink],
  template: `
    <h1>Rechercher une spécification</h1>
    <form class="search" role="search" (ngSubmit)="submit()">
      <mat-form-field appearance="outline" class="field">
        <mat-label>Mots-clés</mat-label>
        <input matInput type="search" name="q" [(ngModel)]="draft" [maxlength]="max" autocomplete="off" />
        <mat-hint>Dans vos spécifications et, s'il est configuré, dans Jira.</mat-hint>
      </mat-form-field>
      <button mat-flat-button type="submit">Rechercher</button>
    </form>
    @if (tooShort()) {
      <p class="error" role="alert">Saisissez au moins 2 caractères.</p>
    }

    <p role="status" aria-live="polite" class="announce">{{ announce() }}</p>

    @if (result(); as r) {
      @if (notice(); as text) {
        <div class="notice">
          <p>{{ text }}</p>
          @switch (r.jiraStatus) {
            @case ('CONSENT_REQUIRED') {
              <button mat-stroked-button type="button" (click)="allowJira()">Autoriser la recherche dans Jira</button>
            }
            @case ('UNAVAILABLE') {
              <button mat-stroked-button type="button" (click)="retry()">Réessayer</button>
            }
            @default {
              <a mat-stroked-button routerLink="/configuration">Ouvrir la configuration</a>
            }
          }
        </div>
      }
      @if (r.items.length) {
        <div class="sheet">
          <sw-results-table [items]="r.items" [caption]="'Résultats pour « ' + q() + ' »'" />
        </div>
        @if (index() > 0 || r.nextCursor) {
          <sw-pager
            label="Pages de résultats"
            [position]="r.position"
            [hasPrevious]="index() > 0"
            [hasNext]="!!r.nextCursor"
            [busy]="busy()"
            (previous)="previous()"
            (next)="next()"
          />
        }
      } @else {
        <div class="empty">
          @if (index() > 0) {
            <button mat-stroked-button type="button" (click)="previous()">Page précédente</button>
            <p>Aucun autre résultat pour « {{ q() }} ».</p>
          } @else {
            <p>Aucun résultat pour « {{ q() }} ». Essayez d'autres mots, ou décrivez un nouveau besoin.</p>
          }
          <a mat-flat-button routerLink="/nouveau">Décrire un nouveau besoin</a>
        </div>
      }
    } @else if (!q()) {
      <div class="empty">
        <p>Cherchez par mots-clés dans le titre, le besoin et le contenu de vos spécifications.</p>
        <a mat-stroked-button routerLink="/nouveau">Décrire un nouveau besoin</a>
      </div>
    }
  `,
  styles: `
    .search {
      display: flex;
      gap: 0.75rem;
      align-items: flex-start;
      max-width: var(--sw-measure);
    }
    .field {
      flex: 1;
    }
    .search button {
      margin-top: 0.5rem;
    }
    .error {
      margin: 0;
      color: var(--mat-sys-error);
    }
    .announce:empty {
      display: none;
    }
    .notice {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem 1rem;
      align-items: center;
      margin: 1rem 0;
      padding: 0.75rem 1rem;
      border-left: 4px solid var(--sw-outcome);
      background: var(--sw-sheet);
    }
    .notice p {
      margin: 0;
      flex: 1 1 20rem;
    }
    .sheet {
      margin-top: 1rem;
      padding: 0.5rem clamp(0.5rem, 2vw, 1.5rem);
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-sheet);
    }
    .empty {
      margin-top: 1.5rem;
      max-width: var(--sw-measure);
    }
    @media (max-width: 36rem) {
      .search {
        flex-direction: column;
        align-items: stretch;
      }
    }
  `,
})
export class SearchPage {
  private readonly api = inject(SearchApi);
  private readonly router = inject(Router);
  private readonly consent = inject(ConsentGate);
  private readonly projects = inject(ProjectsStore);

  /** Paramètre d'URL : la recherche se partage et survit au retour arrière. */
  readonly q = input<string>('');

  protected readonly max = MAX_QUERY;
  protected readonly draft = signal('');
  protected readonly tooShort = signal(false);
  protected readonly result = signal<SearchResult | null>(null);
  protected readonly announce = signal('');
  protected readonly busy = signal(false);
  /** Curseur de chaque page déjà vue, pour revenir en arrière (le premier est nul). */
  private readonly cursors = signal<(string | null)[]>([null]);
  protected readonly index = signal(0);
  protected readonly notice = computed(() => {
    const r = this.result();
    if (!r) {
      return null;
    }
    return r.jiraError ?? JIRA_NOTICE[r.jiraStatus] ?? null;
  });

  constructor() {
    // relancée aussi quand le projet courant change : la recherche porte sur lui (FR-005 de 002)
    effect(() => {
      this.projects.currentId();
      const q = this.q() ?? '';
      this.draft.set(q);
      this.cursors.set([null]);
      this.index.set(0);
      void this.run(q, null);
    });
  }

  protected submit(): void {
    const q = this.draft().trim();
    this.tooShort.set(q.length < MIN_QUERY);
    if (q.length >= MIN_QUERY) {
      void this.router.navigate([], { queryParams: { q } });
    }
  }

  protected async next(): Promise<void> {
    const cursor = this.result()?.nextCursor;
    if (!cursor) {
      return;
    }
    if (await this.run(this.q(), cursor)) {
      this.cursors.update((c) => [...c.slice(0, this.index() + 1), cursor]);
      this.index.update((i) => i + 1);
    }
  }

  protected async previous(): Promise<void> {
    const target = this.index() - 1;
    if (target >= 0 && (await this.run(this.q(), this.cursors()[target]))) {
      this.index.set(target);
    }
  }

  protected retry(): void {
    void this.run(this.q(), this.cursors()[this.index()]);
  }

  protected async allowJira(): Promise<void> {
    if (await this.consent.ensure('JIRA')) {
      await this.run(this.q(), this.cursors()[this.index()]);
    }
  }

  /** Charge une page ; en cas d'échec, la page affichée reste en place (FR-022 de 002). */
  private async run(q: string, cursor: string | null): Promise<boolean> {
    if (q.trim().length < MIN_QUERY) {
      this.result.set(null);
      return false;
    }
    this.announce.set('Recherche en cours…');
    this.busy.set(true);
    try {
      const r = await this.api.search(q.trim(), cursor);
      this.result.set(r);
      this.announce.set(
        cursor ? '' : r.items.length ? `${count(r.items.length, 'résultat', 'résultats')}.` : 'Aucun résultat.',
      );
      return true;
    } catch {
      this.announce.set("Cette page n'a pas pu être chargée : relancez la recherche.");
      return false;
    } finally {
      this.busy.set(false);
    }
  }
}
