import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { InterviewsApi } from '../../core/interviews/interviews.api';
import { InterviewSummary } from '../../core/interviews/interviews.model';
import { ConfirmData, ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { reason } from '../conversation/conversation-thread.component';
import { ProjectsStore } from '../../core/projects/projects.store';
import { SessionsApi } from '../../core/sessions/sessions.api';
import { IMPLEMENTATION_LABELS, PendingPoints, SessionState, SessionSummary } from '../../core/sessions/sessions.model';
import { confirmDeleteSpecification } from '../../shared/ui/confirm-delete-specification';
import { SearchPage } from '../search/search.page';
import { ProjectHeadingComponent } from '../../shared/ui/project-heading.component';

const STATES: Record<SessionState, string> = {
  QUESTIONING: 'Questionnaire en cours',
  READY_TO_GENERATE: 'Prête à générer',
  GENERATED: 'Spécification générée',
};

/**
 * Spécifications du projet courant (010, US5) : nouvelle spécification, recherche (déplacée depuis le
 * menu), puis la liste à reprendre, renommer, supprimer (FR-017, FR-005 de 002).
 */
@Component({
  selector: 'sw-sessions-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, RouterLink, SearchPage, ProjectHeadingComponent],
  template: `
    <header class="head">
      <sw-project-heading label="Spécifications" />
      <a mat-flat-button routerLink="/nouveau">Nouvelle spécification</a>
    </header>
    @if (prepared().length) {
      <section class="prepared" aria-labelledby="prepared-title">
        <h2 id="prepared-title">Séances préparées</h2>
        <ul>
          @for (i of prepared(); track i.id) {
            <li>
              <span class="what">{{ preparedLabel(i) }}</span>
              <span class="names">{{ specNames(i) }}</span>
              <div class="actions">
                <button mat-flat-button type="button" (click)="startPrepared(i)">Démarrer</button>
                <a mat-button [routerLink]="['/seances', i.id, 'preparation']">Modifier</a>
                <button mat-button type="button" (click)="deletePrepared(i)">Supprimer</button>
              </div>
            </li>
          }
        </ul>
      </section>
    }
    <section class="search" aria-labelledby="search-title">
      <sw-search-page [q]="q()" [embedded]="true" />
    </section>
    <h2 id="list-title">Vos spécifications</h2>
    <div class="sheet" role="region" aria-labelledby="list-title" data-tour="sessions-list">
      <p role="status" aria-live="polite" class="announce">{{ announce() }}</p>
      @if (problem()) {
        <p role="alert">{{ problem() }}</p>
      }
      @if (sessions(); as all) {
        @if (all.length) {
          <div class="tools">
            <label class="filter">
              <input type="checkbox" [checked]="onlyPending()" (change)="toggleFilter($event)" />
              Avec des points à traiter
            </label>
            @if (selected().size) {
              <button mat-flat-button type="button" [disabled]="preparing()" (click)="prepareSelected()">
                Préparer une séance ({{ selected().size }})
              </button>
            }
          </div>
        }
        @if (shown(); as list) {
          @if (list.length) {
          <ul class="sessions">
            @for (s of list; track s.id) {
              <li>
                @if (s.readable && s.pending) {
                  <input
                    type="checkbox"
                    class="pick"
                    [checked]="selected().has(s.id)"
                    [attr.aria-label]="'Sélectionner « ' + s.name + ' » pour une séance'"
                    (change)="toggle(s.id)"
                  />
                }
                @if (editing() === s.id) {
                  <form class="rename" (ngSubmit)="saveName(s)">
                    <mat-form-field appearance="outline">
                      <mat-label>Nom de la spécification</mat-label>
                      <input matInput name="name" maxlength="120" [(ngModel)]="draftName" required />
                    </mat-form-field>
                    <button mat-flat-button type="submit" [disabled]="!draftName().trim()">Enregistrer</button>
                    <button mat-button type="button" (click)="editing.set(null)">Annuler</button>
                  </form>
                } @else if (s.readable) {
                  <a
                    class="name"
                    [routerLink]="openLink(s)"
                    [attr.aria-describedby]="s.pending ? 'pending-' + s.id : null"
                    >{{ s.name }}</a
                  >
                } @else {
                  <span class="name">{{ s.name }}</span>
                }
                <p class="meta">
                  @if (s.readable) {
                    {{ meta(s) }}, modifiée le {{ s.updatedAt | date: 'd MMMM y, HH:mm' }}
                  } @else {
                    <span class="unreadable">Illisible : sa sauvegarde est endommagée. Vous pouvez la supprimer.</span>
                  }
                </p>
                @if (s.readable && s.pending; as p) {
                  <p class="pending" [id]="'pending-' + s.id">{{ pendingLabel(p) }}</p>
                }
                @if (editing() !== s.id) {
                  <div class="actions">
                    @if (s.readable) {
                      <a mat-stroked-button [routerLink]="openLink(s)">Reprendre</a>
                      <button mat-button type="button" (click)="startRename(s)">Renommer</button>
                    }
                    <button mat-button type="button" (click)="remove(s)" [attr.aria-label]="'Supprimer la spécification ' + s.name">
                      Supprimer
                    </button>
                  </div>
                }
              </li>
            }
          </ul>
          } @else if (all.length) {
            <p>Aucune spécification n'a de point à traiter.</p>
          } @else {
            <p>Aucune spécification pour l'instant. Rédigez-en une, ou partez d'une conversation avec l'agent.</p>
          }
        }
      } @else {
        <p role="status">Chargement des spécifications…</p>
      }
    </div>
  `,
  styles: `
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      justify-content: space-between;
      gap: 0.5rem 1rem;
    }
    .search {
      margin: 0 0 2rem;
    }
    h2 {
      margin: 0 0 0.75rem;
      font-size: var(--sw-step-1);
    }
    sw-project-heading {
      margin: 0 0 1.5rem;
    }
    .sheet {
      max-width: var(--sw-measure);
      padding: 1.5rem clamp(1rem, 3vw, 2rem);
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-sheet);
    }
    .announce:empty {
      display: none;
    }
    .sessions {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    li {
      padding: 1rem 0;
      border-top: 1px solid var(--sw-rule);
    }
    li:first-child {
      border-top: none;
      padding-top: 0;
    }
    .name {
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-1);
      font-weight: 600;
      color: var(--sw-ink);
    }
    .meta {
      margin: 0.25rem 0 0.5rem;
      color: var(--sw-ink-soft);
    }
    .unreadable {
      color: var(--sw-outcome);
    }
    .actions,
    .rename {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.5rem;
    }
    .rename mat-form-field {
      flex: 1 1 20rem;
    }
    .tools {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem 1rem;
      margin-bottom: 1rem;
    }
    .pick {
      margin-right: 0.5rem;
    }
    .pending {
      margin: 0 0 0.5rem;
      color: var(--sw-outcome);
    }
    .prepared {
      max-width: var(--sw-measure);
      margin: 0 0 2rem;
    }
    .prepared ul {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .prepared li {
      display: grid;
      gap: 0.25rem;
      padding: 0.75rem 0;
      border-top: 1px solid var(--sw-rule);
    }
    .what {
      font-weight: 600;
    }
    .names {
      color: var(--sw-ink-soft);
    }
  `,
})
export class SessionsPage {
  /** Mots-clés de la recherche, dans l'adresse (`/sessions?q=…`). */
  readonly q = input<string>('');

  private readonly api = inject(SessionsApi);
  private readonly interviews = inject(InterviewsApi);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  protected readonly sessions = signal<SessionSummary[] | null>(null);
  /** Séances préparées du projet (021, US5), en tête de page. */
  protected readonly prepared = signal<InterviewSummary[]>([]);
  protected readonly onlyPending = signal(false);
  protected readonly selected = signal<ReadonlySet<string>>(new Set());
  protected readonly preparing = signal(false);
  protected readonly problem = signal('');
  protected readonly shown = computed(() => {
    const all = this.sessions() ?? [];
    return this.onlyPending() ? all.filter((s) => s.readable && total(s.pending) > 0) : all;
  });
  protected readonly editing = signal<string | null>(null);
  protected readonly draftName = signal('');
  protected readonly announce = signal('');

  constructor() {
    // la liste ne montre que le projet courant : elle est relue quand il change
    const projects = inject(ProjectsStore);
    effect(() => {
      projects.currentId();
      void this.reload();
    });
  }

  /** Une spécification générée s'ouvre sur sa dernière version ; sans version, sur son questionnaire. */
  protected openLink(s: SessionSummary): (string | number)[] {
    return s.latestVersion ? ['/sessions', s.id, 'versions', s.latestVersion] : ['/sessions', s.id];
  }

  protected meta(s: SessionSummary): string {
    const state = STATES[s.state] ?? '';
    const base = s.latestVersion ? `${state}, version ${s.latestVersion}` : state;
    // le statut d'implémentation n'est dit qu'au-delà du brouillon (FR-026 de 002)
    return s.implementationStatus && s.implementationStatus !== 'DRAFT'
      ? `${base}, ${IMPLEMENTATION_LABELS[s.implementationStatus].toLowerCase()}`
      : base;
  }

  /** « 3 hypothèses · 2 points ouverts · 1 écart entre sources », ou « Aucun point à traiter ». */
  protected pendingLabel(p: PendingPoints): string {
    const parts = [
      count(p.assumptions, 'hypothèse', 'hypothèses'),
      count(p.openQuestions, 'point ouvert', 'points ouverts'),
      count(p.sourceDeviations, 'écart entre sources', 'écarts entre sources'),
    ].filter((x) => x);
    return parts.length ? parts.join(' · ') : 'Aucun point à traiter';
  }

  protected toggleFilter(event: Event): void {
    this.onlyPending.set((event.target as HTMLInputElement).checked);
    const n = this.shown().length;
    this.announce.set(`${n} spécification${n > 1 ? 's' : ''} affichée${n > 1 ? 's' : ''}.`);
  }

  protected toggle(id: string): void {
    this.selected.update((current) => {
      const next = new Set(current);
      if (!next.delete(id)) {
        next.add(id);
      }
      return next;
    });
  }

  /** La séance préparée reprend les spécifications cochées, dans l'ordre de la liste. */
  protected async prepareSelected(): Promise<void> {
    const ids = (this.sessions() ?? []).filter((s) => this.selected().has(s.id)).map((s) => s.id);
    if (!ids.length || this.preparing()) {
      return;
    }
    this.preparing.set(true);
    this.problem.set('');
    try {
      const d = await this.interviews.prepare({ specs: ids.map((sessionId) => ({ sessionId, excluded: [] })), participants: [] });
      await this.router.navigate(['/seances', d.id, 'preparation']);
    } catch (e) {
      this.problem.set(reason(e, 'La séance n’a pas pu être préparée. Réessayez.'));
    } finally {
      this.preparing.set(false);
    }
  }

  protected preparedLabel(i: InterviewSummary): string {
    const n = i.specifications.length;
    return `Séance préparée : ${n} spécification${n > 1 ? 's' : ''}, ${i.points} point${i.points > 1 ? 's' : ''}`;
  }

  protected specNames(i: InterviewSummary): string {
    return i.specifications.map((s) => `« ${s.name} »`).join(', ');
  }

  protected async startPrepared(i: InterviewSummary): Promise<void> {
    this.problem.set('');
    try {
      const d = await this.interviews.start(i.id);
      await this.router.navigate(['/seances', d.id]);
    } catch (e) {
      this.problem.set(reason(e, 'La séance n’a pas pu démarrer. Réessayez.'));
    }
  }

  protected async deletePrepared(i: InterviewSummary): Promise<void> {
    const data: ConfirmData = {
      title: 'Supprimer cette séance préparée ?',
      message: `La séance préparée sur ${this.specNames(i)} sera supprimée. Les spécifications ne changent pas.`,
      confirm: 'Supprimer la séance',
    };
    if ((await firstValueFrom(this.dialog.open(ConfirmDialogComponent, { data }).afterClosed())) !== true) {
      return;
    }
    await this.interviews.delete(i.id);
    this.announce.set('Séance préparée supprimée.');
    await this.reload();
  }

  protected startRename(s: SessionSummary): void {
    this.draftName.set(s.name);
    this.editing.set(s.id);
  }

  protected async saveName(s: SessionSummary): Promise<void> {
    const name = this.draftName().trim();
    if (!name) {
      return;
    }
    try {
      await this.api.rename(s.id, name);
      this.editing.set(null);
      this.announce.set(`Spécification renommée en « ${name} ».`);
      await this.reload();
    } catch {
      // Le bandeau affiche la cause.
    }
  }

  protected async remove(s: SessionSummary): Promise<void> {
    const confirmed = await confirmDeleteSpecification(this.dialog, this.api, s.id, {
      title: 'Supprimer cette spécification ?',
      message:
        `« ${s.name} » et toutes ses versions seront supprimées définitivement.` +
        (s.jiraKey ? ` Elle est liée au ticket ${s.jiraKey} : Jira n'est pas modifié.` : ''),
      confirm: 'Supprimer définitivement',
    });
    if (!confirmed) {
      return;
    }
    await this.api.delete(s.id, confirmed.keepDiscussions);
    this.announce.set(`Spécification « ${s.name} » supprimée.`);
    await this.reload();
  }

  private async reload(): Promise<void> {
    const [list, prepared] = await Promise.all([this.api.list(), this.interviews.prepared().catch(() => [])]);
    this.sessions.set(list);
    this.prepared.set(prepared);
    const ids = new Set(list.map((s) => s.id));
    this.selected.update((current) => new Set([...current].filter((id) => ids.has(id))));
  }
}

function total(p: PendingPoints | null | undefined): number {
  return p ? p.assumptions + p.openQuestions + p.sourceDeviations : 0;
}

function count(n: number, one: string, many: string): string {
  return n ? `${n} ${n > 1 ? many : one}` : '';
}
