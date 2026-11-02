import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MarkdownComponent } from 'ngx-markdown';
import { ConflictResolution, JiraConflict } from '../../core/jira/jira.model';

/**
 * Le ticket a changé dans Jira depuis la dernière lecture (FR-048) : les deux versions côte à côte,
 * et trois choix explicites. Rien n'est écrit tant que l'utilisateur n'a pas choisi.
 */
@Component({
  selector: 'sw-conflict-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, MarkdownComponent, MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Le ticket a été modifié dans Jira</h2>
    <mat-dialog-content>
      <p>
        Quelqu'un a modifié le ticket le {{ data.jiraUpdated | date: 'd MMMM y à HH:mm' }}, après votre dernière
        lecture. Rien n'a été envoyé : choisissez quelle version garder.
      </p>
      <div class="versions">
        <section aria-labelledby="jira-version" class="version">
          <h3 id="jira-version">Version Jira</h3>
          <div class="body" tabindex="0" aria-labelledby="jira-version"><markdown [data]="data.jiraDescriptionMarkdown" /></div>
        </section>
        <section aria-labelledby="local-version" class="version">
          <h3 id="local-version">Votre version</h3>
          <div class="body" tabindex="0" aria-labelledby="local-version"><markdown [data]="data.localMarkdown" /></div>
        </section>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions class="choices">
      <button mat-button type="button" [mat-dialog-close]="undefined">Annuler</button>
      <span class="spacer"></span>
      <button mat-stroked-button type="button" [mat-dialog-close]="choice('TAKE_JIRA')">Reprendre la version Jira</button>
      <button mat-stroked-button type="button" [mat-dialog-close]="choice('AI_MERGE')">Fusionner avec l'IA</button>
      <button mat-flat-button type="button" [mat-dialog-close]="choice('OVERWRITE')">Écraser avec ma version</button>
    </mat-dialog-actions>
  `,
  styles: `
    .versions {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 1rem;
    }
    @media (max-width: 48rem) {
      .versions {
        grid-template-columns: 1fr;
      }
    }
    h3 {
      margin: 0 0 0.35rem;
      font-size: var(--sw-step-0);
      font-weight: 650;
    }
    .body {
      max-height: 20rem;
      overflow: auto;
      padding: 0.5rem 0.75rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
      font-family: var(--sw-font-doc);
      overflow-wrap: anywhere;
    }
    .body:focus-visible {
      outline: 2px solid var(--sw-pencil);
      outline-offset: 2px;
    }
    .choices {
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .spacer {
      flex: 1;
    }
  `,
})
export class ConflictDialogComponent {
  protected readonly data = inject<JiraConflict>(MAT_DIALOG_DATA);

  protected choice(resolution: ConflictResolution): ConflictResolution {
    return resolution;
  }
}
