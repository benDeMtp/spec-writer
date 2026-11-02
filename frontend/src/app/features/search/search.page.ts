import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { SearchApi } from '../../core/search/search.api';
import { JiraStatus, PAGE_SIZE, SearchResult } from '../../core/search/search.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
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
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, ResultsTableComponent, RouterLink],
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
        @if (pages() > 1) {
          <nav class="pages" aria-label="Pages de résultats Jira">
            <button mat-button type="button" [disabled]="pageIndex() === 0" (click)="goTo(pageIndex() - 1)">
              Résultats précédents
            </button>
            <span>Page {{ pageIndex() + 1 }} sur {{ pages() }}</span>
            <button mat-button type="button" [disabled]="pageIndex() + 1 >= pages()" (click)="goTo(pageIndex() + 1)">
              Résultats suivants
            </button>
          </nav>
        }
      } @else {
        <div class="empty">
          <p>Aucun résultat pour « {{ q() }} ». Essayez d'autres mots, ou décrivez un nouveau besoin.</p>
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
    .pages {
      display: flex;
      gap: 1rem;
      align-items: center;
      margin-top: 0.75rem;
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

  /** Paramètres d'URL : la recherche se partage et survit au retour arrière. */
  readonly q = input<string>('');
  readonly page = input<string>('0');

  protected readonly max = MAX_QUERY;
  protected readonly draft = signal('');
  protected readonly tooShort = signal(false);
  protected readonly result = signal<SearchResult | null>(null);
  protected readonly announce = signal('');
  protected readonly pageIndex = computed(() => Math.max(0, Number(this.page()) || 0));
  protected readonly pages = computed(() => Math.ceil((this.result()?.jiraTotal ?? 0) / PAGE_SIZE));
  protected readonly notice = computed(() => {
    const status = this.result()?.jiraStatus;
    return status ? (JIRA_NOTICE[status] ?? null) : null;
  });

  constructor() {
    effect(() => {
      const q = this.q() ?? '';
      const page = this.pageIndex();
      this.draft.set(q);
      void this.run(q, page);
    });
  }

  protected submit(): void {
    const q = this.draft().trim();
    this.tooShort.set(q.length < MIN_QUERY);
    if (q.length >= MIN_QUERY) {
      void this.router.navigate([], { queryParams: { q, page: null } });
    }
  }

  protected goTo(page: number): void {
    void this.router.navigate([], { queryParams: { q: this.q(), page: page || null } });
  }

  protected retry(): void {
    void this.run(this.q(), this.pageIndex());
  }

  protected async allowJira(): Promise<void> {
    if (await this.consent.ensure('JIRA')) {
      await this.run(this.q(), this.pageIndex());
    }
  }

  private async run(q: string, page: number): Promise<void> {
    if (q.trim().length < MIN_QUERY) {
      this.result.set(null);
      return;
    }
    this.announce.set('Recherche en cours…');
    try {
      const r = await this.api.search(q.trim(), page);
      this.result.set(r);
      this.announce.set(r.items.length ? `${count(r.items.length, 'résultat', 'résultats')}.` : 'Aucun résultat.');
    } catch {
      this.announce.set('');
    }
  }
}
