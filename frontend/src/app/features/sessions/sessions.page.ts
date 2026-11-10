import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ProjectsStore } from '../../core/projects/projects.store';
import { SessionsApi } from '../../core/sessions/sessions.api';
import { IMPLEMENTATION_LABELS, SessionState, SessionSummary } from '../../core/sessions/sessions.model';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { SearchPage } from '../search/search.page';

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
  imports: [DatePipe, FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, RouterLink, SearchPage],
  template: `
    <header class="head">
      <h1>Spécifications</h1>
      <a mat-flat-button routerLink="/nouveau">Nouvelle spécification</a>
    </header>
    <section class="search" aria-labelledby="search-title">
      <sw-search-page [q]="q()" [embedded]="true" />
    </section>
    <h2 id="list-title">Vos spécifications</h2>
    <div class="sheet" role="region" aria-labelledby="list-title">
      <p role="status" aria-live="polite" class="announce">{{ announce() }}</p>
      @if (sessions(); as list) {
        @if (list.length) {
          <ul class="sessions">
            @for (s of list; track s.id) {
              <li>
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
                  <a class="name" [routerLink]="['/sessions', s.id]">{{ s.name }}</a>
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
                @if (editing() !== s.id) {
                  <div class="actions">
                    @if (s.readable) {
                      <a mat-stroked-button [routerLink]="['/sessions', s.id]">Reprendre</a>
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
        } @else {
          <p>Aucune spécification pour l'instant. Rédigez-en une, ou partez d'une conversation avec l'agent.</p>
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
    h1 {
      margin: 0 0 1.5rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-3);
      font-weight: 600;
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
  `,
})
export class SessionsPage {
  /** Mots-clés de la recherche, dans l'adresse (`/sessions?q=…`). */
  readonly q = input<string>('');

  private readonly api = inject(SessionsApi);
  private readonly dialog = inject(MatDialog);

  protected readonly sessions = signal<SessionSummary[] | null>(null);
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

  protected meta(s: SessionSummary): string {
    const state = STATES[s.state] ?? '';
    const base = s.latestVersion ? `${state}, version ${s.latestVersion}` : state;
    // le statut d'implémentation n'est dit qu'au-delà du brouillon (FR-026 de 002)
    return s.implementationStatus && s.implementationStatus !== 'DRAFT'
      ? `${base}, ${IMPLEMENTATION_LABELS[s.implementationStatus].toLowerCase()}`
      : base;
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
    const confirmed = await firstValueFrom(
      this.dialog
        .open(ConfirmDialogComponent, {
          data: {
            title: 'Supprimer cette spécification ?',
            message:
              `« ${s.name} » et toutes ses versions seront supprimées définitivement.` +
              (s.jiraKey ? ` Elle est liée au ticket ${s.jiraKey} : Jira n'est pas modifié.` : ''),
            confirm: 'Supprimer définitivement',
          },
        })
        .afterClosed(),
    );
    if (confirmed !== true) {
      return;
    }
    await this.api.delete(s.id);
    this.announce.set(`Spécification « ${s.name} » supprimée.`);
    await this.reload();
  }

  private async reload(): Promise<void> {
    this.sessions.set(await this.api.list());
  }
}
