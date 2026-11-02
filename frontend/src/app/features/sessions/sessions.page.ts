import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { SessionsApi } from '../../core/sessions/sessions.api';
import { SessionState, SessionSummary } from '../../core/sessions/sessions.model';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';

const STATES: Record<SessionState, string> = {
  QUESTIONING: 'Questionnaire en cours',
  READY_TO_GENERATE: 'Prête à générer',
  GENERATED: 'Spécification générée',
};

/** Sessions : reprendre, renommer, supprimer (FR-017). */
@Component({
  selector: 'sw-sessions-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, RouterLink],
  template: `
    <h1>Sessions</h1>
    <div class="sheet">
      <p role="status" aria-live="polite" class="announce">{{ announce() }}</p>
      @if (sessions(); as list) {
        @if (list.length) {
          <ul class="sessions">
            @for (s of list; track s.id) {
              <li>
                @if (editing() === s.id) {
                  <form class="rename" (ngSubmit)="saveName(s)">
                    <mat-form-field appearance="outline">
                      <mat-label>Nom de la session</mat-label>
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
                    <button mat-button type="button" (click)="remove(s)" [attr.aria-label]="'Supprimer la session ' + s.name">
                      Supprimer
                    </button>
                  </div>
                }
              </li>
            }
          </ul>
        } @else {
          <p>Aucune session pour l'instant.</p>
          <a mat-flat-button routerLink="/nouveau">Décrire un nouveau besoin</a>
        }
      } @else {
        <p role="status">Chargement des sessions…</p>
      }
    </div>
  `,
  styles: `
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
export class SessionsPage implements OnInit {
  private readonly api = inject(SessionsApi);
  private readonly dialog = inject(MatDialog);

  protected readonly sessions = signal<SessionSummary[] | null>(null);
  protected readonly editing = signal<string | null>(null);
  protected readonly draftName = signal('');
  protected readonly announce = signal('');

  async ngOnInit(): Promise<void> {
    await this.reload();
  }

  protected meta(s: SessionSummary): string {
    const state = STATES[s.state] ?? '';
    return s.latestVersion ? `${state}, version ${s.latestVersion}` : state;
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
      this.announce.set(`Session renommée en « ${name} ».`);
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
            title: 'Supprimer cette session ?',
            message: `« ${s.name} » et toutes ses versions seront supprimées définitivement.`,
            confirm: 'Supprimer définitivement',
          },
        })
        .afterClosed(),
    );
    if (confirmed !== true) {
      return;
    }
    await this.api.delete(s.id);
    this.announce.set(`Session « ${s.name} » supprimée.`);
    await this.reload();
  }

  private async reload(): Promise<void> {
    this.sessions.set(await this.api.list());
  }
}
