import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { DeletionMode, Project } from '../../core/projects/projects.model';

export interface DeleteProjectData {
  project: Project;
  others: Project[];
}

export interface DeleteProjectChoice {
  mode: DeletionMode;
  targetProjectId?: string;
}

/** Suppression d'un projet : ses spécifications sont déplacées ou supprimées (FR-002). */
@Component({
  selector: 'sw-delete-project-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatRadioModule, MatSelectModule],
  template: `
    <h2 mat-dialog-title>Supprimer le projet « {{ data.project.name }} » ?</h2>
    <mat-dialog-content>
      @if (count() === 0) {
        <p>Ce projet ne contient aucune spécification.</p>
      } @else {
        <p id="sessions-choice">
          Il contient {{ count() }} {{ count() > 1 ? 'spécifications' : 'spécification' }}. Que faut-il en faire ?
        </p>
        <mat-radio-group aria-labelledby="sessions-choice" [(ngModel)]="mode" class="choices">
          <mat-radio-button value="MOVE">Les déplacer vers un autre projet</mat-radio-button>
          <mat-radio-button value="DELETE">Les supprimer définitivement, avec leurs versions</mat-radio-button>
        </mat-radio-group>
        @if (mode() === 'MOVE') {
          <mat-form-field appearance="outline">
            <mat-label>Projet de destination</mat-label>
            <mat-select [(ngModel)]="target" required>
              @for (p of data.others; track p.id) {
                <mat-option [value]="p.id">{{ p.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        }
      }
      <p class="note">Jira n'est pas modifié.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="null" cdkFocusInitial>Annuler</button>
      <button mat-flat-button type="button" class="danger" [disabled]="!valid()" (click)="confirm()">
        Supprimer le projet
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .choices {
      display: grid;
      gap: 0.5rem;
      margin: 0 0 1rem;
    }
    mat-form-field {
      width: 100%;
    }
    .note {
      color: var(--sw-ink-soft);
    }
    .danger {
      --mat-button-filled-container-color: var(--sw-outcome);
    }
  `,
})
export class DeleteProjectDialogComponent {
  protected readonly data = inject<DeleteProjectData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<DeleteProjectDialogComponent, DeleteProjectChoice | null>>(MatDialogRef);

  protected readonly count = computed(() => this.data.project.sessionCount);
  protected readonly mode = signal<DeletionMode>('MOVE');
  protected readonly target = signal<string | null>(this.data.others[0]?.id ?? null);
  protected readonly valid = computed(() => this.count() === 0 || this.mode() === 'DELETE' || !!this.target());

  protected confirm(): void {
    if (this.count() === 0 || this.mode() === 'DELETE') {
      this.ref.close({ mode: 'DELETE' });
    } else {
      this.ref.close({ mode: 'MOVE', targetProjectId: this.target() ?? undefined });
    }
  }
}
