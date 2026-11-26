import { SwDatePipe } from '../../shared/ui/sw-date.pipe';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { isProblem } from '../../core/http/problem';
import { JiraApi } from '../../core/jira/jira.api';
import { IssuePage, IssueSortField, IssueSync, ProjectIssue, SortDirection } from '../../core/jira/jira.model';
import { KnowledgeApi } from '../../core/projects/knowledge.api';
import { ProjectsStore } from '../../core/projects/projects.store';
import { SettingsStore } from '../../core/settings/settings.store';
import { PagerComponent } from '../../shared/ui/pager.component';
import { ProjectHeadingComponent } from '../../shared/ui/project-heading.component';

export const ISSUES_PAGE_SIZE = 50;

const FIELDS: readonly IssueSortField[] = ['updated', 'status', 'sync'];
const LABELS: Record<IssueSortField, string> = { updated: 'Modifié le', status: 'Statut', sync: 'Synchro' };
/** Sens d'un premier clic : les plus récents, les catégories dans l'ordre, les conflits d'abord. */
const FIRST: Record<IssueSortField, SortDirection> = { updated: 'desc', status: 'asc', sync: 'asc' };
export const SYNC_LABELS: Record<IssueSync, string> = {
  CONFLICT: 'Conflit',
  LOCAL_AHEAD: 'Spécification plus récente',
  IN_SYNC: 'À jour',
  UNLINKED: 'Non liée',
};

/** Message affiché au-dessus de la page conservée quand Jira ne la rend pas (FR-022 de 002). */
function failure(error: unknown): string {
  if (error instanceof HttpErrorResponse && isProblem(error.error)) {
    return error.error.userMessage;
  }
  return "La liste des tickets n'a pas pu être lue.";
}

/** Tickets du projet Jira courant, les plus récemment modifiés d'abord, par 25 (FR-020 à FR-022 de 002). */
@Component({
  selector: 'sw-jira-issues-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SwDatePipe, MatButtonModule, PagerComponent, ProjectHeadingComponent, RouterLink],
  template: `
    <sw-project-heading label="Tickets Jira" />
    @if (projectKey(); as key) {
      <p class="lead">
        Projet Jira {{ key }}{{ usesDefaultKey() ? " (clé par défaut de l'application)" : '' }}, lu dans l'index local du projet.
      </p>
      <div class="freshness">
        @if (page(); as p) {
          @if (p.lastSync; as last) {
            <p>Dernière synchronisation de Jira : {{ last.at | swDate: 'd MMM y, HH:mm' }}</p>
          } @else {
            <p>Jira n'a pas encore été synchronisé : synchronisez-le pour remplir la liste.</p>
          }
        }
        <button mat-stroked-button type="button" (click)="resync()" [disabled]="syncing()">
          {{ syncing() ? 'Synchronisation de Jira…' : 'Resynchroniser Jira' }}
        </button>
      </div>
      <p class="sw-visually-hidden" aria-live="polite">{{ announce() }}</p>
      @if (error(); as message) {
        <div class="notice" role="alert">
          <p>{{ message }} La page affichée est conservée.</p>
          <button mat-stroked-button type="button" (click)="load()">Réessayer</button>
        </div>
      }
      @if (page(); as p) {
        @if (p.issues.length) {
          <div class="sheet" data-tour="jira-table">
            <table>
              <caption class="sw-visually-hidden">Tickets du projet {{ key }}, {{ position() }}, {{ sortLabel() }}</caption>
              <thead>
                <tr>
                  <th scope="col">Ticket</th>
                  <th scope="col">Titre</th>
                  @for (f of fields; track f) {
                    <th scope="col" [attr.aria-sort]="ariaSort(f)">
                      <button type="button" class="sort" (click)="sortBy(f)">
                        {{ labels[f] }}
                        <span aria-hidden="true">{{ arrow(f) }}</span>
                      </button>
                    </th>
                  }
                  <th scope="col">Spécification</th>
                </tr>
              </thead>
              <tbody>
                @for (i of p.issues; track i.key) {
                  <tr>
                    <td>
                      <span class="ticket">
                        @if (i.subtasks.length) {
                          <button
                            mat-button
                            type="button"
                            class="toggle"
                            [attr.aria-expanded]="isOpen(i.key)"
                            [attr.aria-controls]="isOpen(i.key) ? childIds(i) : null"
                            [attr.aria-label]="toggleLabel(i)"
                            (click)="toggle(i.key)"
                          >
                            <svg aria-hidden="true" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" [class.open]="isOpen(i.key)"><path d="m9 6 6 6-6 6" /></svg>
                            {{ i.subtasks.length }}
                          </button>
                        }
                        <a [routerLink]="['/jira', i.key]">{{ i.key }}</a>
                      </span>
                    </td>
                    <td>
                      {{ i.title }}
                      @if (i.parentKey) {
                        <span class="parent">Sous-tâche de {{ i.parentKey }}</span>
                      }
                    </td>
                    <td>{{ i.updatedAt | swDate: 'd MMM y, HH:mm' }}</td>
                    <td>{{ i.status ?? '—' }}</td>
                    <td><span class="sync" [attr.data-sync]="i.sync">{{ syncLabels[i.sync] }}</span></td>
                    <td>
                      @if (i.sessionId) {
                        <a [routerLink]="['/sessions', i.sessionId]">Ouvrir la session liée</a>
                      } @else {
                        <a [routerLink]="['/jira', i.key]">Voir et importer</a>
                      }
                    </td>
                  </tr>
                  @if (isOpen(i.key)) {
                    @for (c of i.subtasks; track c.key) {
                      <tr class="subtask" [id]="'sous-tache-' + c.key">
                        <td>
                          <span class="sw-visually-hidden">Sous-tâche de {{ i.key }} : </span>
                          <a [routerLink]="['/jira', c.key]">{{ c.key }}</a>
                        </td>
                        <td>{{ c.title }}</td>
                        <td>{{ c.updatedAt | swDate: 'd MMM y, HH:mm' }}</td>
                        <td>{{ c.status ?? '—' }}</td>
                        <td><span class="sync" [attr.data-sync]="c.sync">{{ syncLabels[c.sync] }}</span></td>
                        <td>
                          @if (c.sessionId) {
                            <a [routerLink]="['/sessions', c.sessionId]">Ouvrir la session liée</a>
                          } @else {
                            <a [routerLink]="['/jira', c.key]">Voir et importer</a>
                          }
                        </td>
                      </tr>
                    }
                  }
                }
              </tbody>
            </table>
          </div>
          <sw-pager
            label="Pages de tickets"
            [position]="position()"
            [hasPrevious]="p.startAt > 0"
            [hasNext]="p.startAt + p.issues.length < p.total"
            [busy]="busy()"
            (previous)="goTo(pageNumber() - 1)"
            (next)="goTo(pageNumber() + 1)"
          />
        } @else {
          <p>Aucun ticket indexé pour ce projet.</p>
        }
      } @else if (busy()) {
        <p role="status">Chargement des tickets…</p>
      }
    } @else if (settings.settings()) {
      <!-- 012 : la clé se règle dans le projet ; le serveur et le jeton, dans la configuration -->
      @if (jiraConfigured()) {
        <p>Aucun projet Jira n'est associé à {{ projectName() }}.</p>
        <a mat-stroked-button routerLink="/projet/reglages" fragment="jira">Renseigner la clé Jira du projet</a>
      } @else {
        <p>Jira n'est pas configuré.</p>
        <a mat-stroked-button routerLink="/configuration">Ouvrir la configuration</a>
      }
    }
  `,
  styles: `
    .sheet {
      margin-top: 1rem;
      padding: 0.5rem clamp(0.5rem, 2vw, 1.5rem);
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-sheet);
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th,
    td {
      text-align: left;
      padding: 0.5rem;
      border-bottom: 1px solid var(--sw-rule);
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
    .freshness {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem 1rem;
      align-items: center;
    }
    .freshness p {
      margin: 0;
    }
    th .sort {
      font: inherit;
      font-weight: 600;
      background: none;
      border: 0;
      padding: 0;
      cursor: pointer;
      color: inherit;
      text-align: left;
    }
    th .sort:focus-visible {
      outline: 2px solid var(--sw-focus, currentColor);
      outline-offset: 2px;
    }
    .sync[data-sync='CONFLICT'] {
      font-weight: 600;
    }
    .notice p {
      margin: 0;
      flex: 1 1 20rem;
    }
    .ticket {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      white-space: nowrap;
    }
    .toggle {
      min-width: 0;
      padding: 0 0.4rem;
    }
    .toggle svg {
      vertical-align: middle;
      transition: transform 120ms ease;
    }
    .toggle svg.open {
      transform: rotate(90deg);
    }
    @media (prefers-reduced-motion: reduce) {
      .toggle svg {
        transition: none;
      }
    }
    tr.subtask td:first-child {
      padding-left: 2.25rem;
      border-left: 3px solid var(--sw-rule);
    }
    tr.subtask td {
      color: var(--sw-ink-soft);
    }
    tr.subtask a {
      color: var(--sw-ink);
    }
    .parent {
      display: block;
      color: var(--sw-ink-soft);
      font-size: var(--sw-step--1);
    }
  `,
})
export class JiraIssuesPage {
  private readonly api = inject(JiraApi);
  private readonly knowledge = inject(KnowledgeApi);
  private readonly projects = inject(ProjectsStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly settings = inject(SettingsStore);

  protected readonly fields = FIELDS;
  protected readonly labels = LABELS;
  protected readonly syncLabels = SYNC_LABELS;
  protected readonly size = ISSUES_PAGE_SIZE;
  protected readonly projectId = computed(() => this.projects.current()?.id ?? null);
  protected readonly projectKey = computed(() => this.projects.current()?.effectiveJiraProjectKey ?? null);
  protected readonly projectName = computed(() => this.projects.current()?.name ?? 'ce projet');
  /** Le projet n'a pas de clé : la clé par défaut de l'application sert de repli (FR-016 de 012). */
  protected readonly usesDefaultKey = computed(() => !!this.projectKey() && !this.projects.current()?.jiraProjectKey);
  protected readonly jiraConfigured = computed(() => !!this.settings.jira()?.enabled);

  /** Tri et page gardés dans l'URL : retour à la vue et rechargement les retrouvent (FR-004). */
  private readonly query = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  protected readonly sort = computed<IssueSortField>(() => {
    const t = this.query().get('tri');
    return FIELDS.includes(t as IssueSortField) ? (t as IssueSortField) : 'updated';
  });
  protected readonly dir = computed<SortDirection>(() => {
    const o = this.query().get('ordre');
    return o === 'asc' || o === 'desc' ? o : FIRST[this.sort()];
  });
  protected readonly pageNumber = computed(() => Math.max(1, Number.parseInt(this.query().get('page') ?? '1', 10) || 1));

  protected readonly page = signal<IssuePage | null>(null);
  /** 022 : tickets dépliés ; tout se replie au changement de page ou de tri. */
  protected readonly open = signal<ReadonlySet<string>>(new Set());
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly syncing = signal(false);
  protected readonly announce = signal('');
  protected readonly position = computed(() => {
    const p = this.page();
    if (!p || !p.issues.length) {
      return '';
    }
    return `${p.startAt + 1} à ${p.startAt + p.issues.length} sur ${p.total}`;
  });
  protected readonly sortLabel = computed(
    () => `triés par ${LABELS[this.sort()].toLowerCase()}, ordre ${this.dir() === 'asc' ? 'croissant' : 'décroissant'}`,
  );

  constructor() {
    void this.settings.ensureLoaded().catch(() => undefined);
    effect(() => {
      // projet, tri ou page : la liste est relue
      if (this.projectKey() && this.projectId()) {
        this.sort();
        this.dir();
        this.pageNumber();
        untracked(() => void this.load());
      }
    });
  }

  protected isOpen(key: string): boolean {
    return this.open().has(key);
  }

  protected toggle(key: string): void {
    this.open.update((keys) => {
      const next = new Set(keys);
      if (!next.delete(key)) {
        next.add(key);
      }
      return next;
    });
  }

  protected toggleLabel(issue: ProjectIssue): string {
    const n = issue.subtasks.length;
    const what = n === 1 ? 'la sous-tâche' : `les ${n} sous-tâches`;
    return `${this.isOpen(issue.key) ? 'Masquer' : 'Afficher'} ${what} de ${issue.key}`;
  }

  protected childIds(issue: ProjectIssue): string {
    return issue.subtasks.map((c) => 'sous-tache-' + c.key).join(' ');
  }

  protected ariaSort(field: IssueSortField): string | null {
    if (field !== this.sort()) {
      return null;
    }
    return this.dir() === 'asc' ? 'ascending' : 'descending';
  }

  protected arrow(field: IssueSortField): string {
    if (field !== this.sort()) {
      return '';
    }
    return this.dir() === 'asc' ? '▲' : '▼';
  }

  /** Une autre colonne part de son sens naturel ; la même colonne inverse le sens. Retour à la page 1. */
  protected sortBy(field: IssueSortField): void {
    const dir: SortDirection = field === this.sort() ? (this.dir() === 'asc' ? 'desc' : 'asc') : FIRST[field];
    this.announce.set(`Tickets triés par ${LABELS[field].toLowerCase()}, ordre ${dir === 'asc' ? 'croissant' : 'décroissant'}.`);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { tri: field, ordre: dir, page: null } });
  }

  protected goTo(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page > 1 ? page : null },
      queryParamsHandling: 'merge',
    });
  }

  /** En cas d'erreur, la page affichée reste en place, avec le message (FR-022 de 002). */
  protected async load(): Promise<void> {
    const id = this.projectId();
    if (!id) {
      return;
    }
    this.busy.set(true);
    this.open.set(new Set());
    try {
      this.page.set(await this.api.issues(id, this.sort(), this.dir(), (this.pageNumber() - 1) * this.size, this.size));
      this.error.set(null);
    } catch (e) {
      this.error.set(failure(e));
    } finally {
      this.busy.set(false);
    }
  }

  /** Relit Jira seul, en incrémental (FR-005), puis recharge la liste. */
  protected async resync(): Promise<void> {
    const id = this.projectId();
    if (!id) {
      return;
    }
    this.syncing.set(true);
    try {
      await this.knowledge.start(id, 'INCREMENTAL', ['JIRA']);
      for (;;) {
        await new Promise((resolve) => setTimeout(resolve, 1000));
        const current = await this.knowledge.current(id);
        if (!current || current.status !== 'RUNNING') {
          break;
        }
      }
      await this.load();
    } catch {
      // le bandeau d'erreur global explique le refus (indexation déjà en cours…)
    } finally {
      this.syncing.set(false);
    }
  }
}
