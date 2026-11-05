import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { Project } from '../../core/projects/projects.model';

export interface MoveSessionData {
  sessionName: string;
  targets: Project[];
}

/** Choix du projet où déplacer une spécification (FR-004). Renvoie l'identifiant du projet. */
@Component({
  selector: 'sw-move-session-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatSelectModule],
  template: `
    <h2 mat-dialog-title>Déplacer « {{ data.sessionName }} »</h2>
    <mat-dialog-content>
      <p>La spécification et toutes ses versions changent de projet.</p>
      <mat-form-field appearance="outline">
        <mat-label>Projet de destination</mat-label>
        <mat-select [(ngModel)]="target" required>
          @for (p of data.targets; track p.id) {
            <mat-option [value]="p.id">{{ p.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="null" cdkFocusInitial>Annuler</button>
      <button mat-flat-button type="button" [disabled]="!target()" [mat-dialog-close]="target()">Déplacer</button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-form-field {
      width: 100%;
    }
  `,
})
export class MoveSessionDialogComponent {
  protected readonly data = inject<MoveSessionData>(MAT_DIALOG_DATA);
  protected readonly target = signal<string | null>(this.data.targets[0]?.id ?? null);
}
