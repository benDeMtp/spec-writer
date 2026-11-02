import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { JiraApi, PushOutcome } from '../../core/jira/jira.api';
import { PushPreview } from '../../core/jira/jira.model';

export interface PushDialogData {
  sessionId: string;
  versionNumber: number;
}

/**
 * Aperçu de la description qui remplacera celle du ticket, puis envoi sur confirmation (FR-047).
 * Se ferme avec l'issue de l'envoi, ou sans valeur si l'utilisateur annule.
 */
@Component({
  selector: 'sw-push-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Envoyer la version {{ data.versionNumber }} vers Jira ?</h2>
    <mat-dialog-content>
      @if (preview(); as p) {
        <p>
          La description du ticket <strong>{{ p.issueKey }}</strong> sera remplacée par ce texte, au format de Jira.
          Les autres champs du ticket ne changent pas.
        </p>
        <pre class="wiki" tabindex="0" aria-label="Description envoyée à Jira">{{ p.descriptionWiki }}</pre>
      } @else {
        <p role="status" aria-live="polite">Préparation de l'aperçu…</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close [disabled]="sending()">Annuler</button>
      <button mat-flat-button type="button" [disabled]="!preview() || sending()" (click)="send()">
        {{ sending() ? 'Envoi…' : 'Envoyer vers Jira' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .wiki {
      max-height: 22rem;
      overflow: auto;
      margin: 0;
      padding: 0.75rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
      white-space: pre-wrap;
      overflow-wrap: anywhere;
      font-size: var(--sw-step--1);
    }
    .wiki:focus-visible {
      outline: 2px solid var(--sw-pencil);
      outline-offset: 2px;
    }
  `,
})
export class PushDialogComponent {
  protected readonly data = inject<PushDialogData>(MAT_DIALOG_DATA);
  private readonly api = inject(JiraApi);
  private readonly ref = inject<MatDialogRef<PushDialogComponent, PushOutcome>>(MatDialogRef);

  protected readonly preview = signal<PushPreview | null>(null);
  protected readonly sending = signal(false);

  constructor() {
    this.api.preview(this.data.sessionId, this.data.versionNumber).then(
      (p) => this.preview.set(p),
      () => this.ref.close(),
    );
  }

  protected async send(): Promise<void> {
    this.sending.set(true);
    try {
      this.ref.close(await this.api.push(this.data.sessionId, this.data.versionNumber));
    } catch {
      this.sending.set(false);
    }
  }
}
