import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

export interface ConfirmData {
  title: string;
  message: string;
  confirm: string;
  /** Case facultative, par exemple « Supprimer aussi ses discussions » (011). */
  option?: { label: string; checked: boolean };
}

/** Réponse d'une confirmation avec case : la case n'est lue que si l'action est confirmée. */
export interface ConfirmResult {
  confirmed: true;
  option: boolean;
}

/**
 * Confirmation d'une action irréversible, sans window.confirm. Renvoie true si confirmée, ou
 * {@link ConfirmResult} quand la confirmation porte une case.
 */
@Component({
  selector: 'sw-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule, MatCheckboxModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p>{{ data.message }}</p>
      @if (data.option; as option) {
        <mat-checkbox [checked]="checked()" (change)="checked.set($event.checked)">{{
          option.label
        }}</mat-checkbox>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false" cdkFocusInitial>Annuler</button>
      <button mat-flat-button type="button" class="danger" [mat-dialog-close]="result()">
        {{ data.confirm }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .danger {
      --mat-button-filled-container-color: var(--sw-outcome);
    }
  `,
})
export class ConfirmDialogComponent {
  protected readonly data = inject<ConfirmData>(MAT_DIALOG_DATA);
  protected readonly checked = signal(this.data.option?.checked ?? false);

  protected result(): true | ConfirmResult {
    return this.data.option ? { confirmed: true, option: this.checked() } : true;
  }
}
