import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { ConsentService } from '../../core/settings/settings.model';

const TEXTS: Record<ConsentService, { title: string; body: string[] }> = {
  JIRA: {
    title: 'Envoyer des données à Jira ?',
    body: [
      'spec-writer enverra à votre serveur Jira les spécifications que vous choisissez de publier, ' +
        'et les recherches que vous y lancez.',
      'Votre jeton Jira reste dans le trousseau de votre système. Vous pourrez retirer cet accord à tout moment ' +
        'depuis la configuration.',
    ],
  },
  COPILOT: {
    title: 'Envoyer vos besoins à GitHub Copilot ?',
    body: [
      'spec-writer enverra à GitHub Copilot le texte de vos besoins et vos réponses, pour poser les questions ' +
        'et rédiger la spécification.',
      'Vous pourrez retirer cet accord à tout moment depuis la configuration.',
    ],
  },
};

/** Accord explicite avant tout envoi à un service tiers (FR-024). Renvoie true si accepté. */
@Component({
  selector: 'sw-consent-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ text.title }}</h2>
    <mat-dialog-content>
      @for (p of text.body; track $index) {
        <p>{{ p }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false">Refuser</button>
      <button mat-flat-button type="button" [mat-dialog-close]="true" cdkFocusInitial>Accepter</button>
    </mat-dialog-actions>
  `,
})
export class ConsentDialogComponent {
  protected readonly text = TEXTS[inject<{ service: ConsentService }>(MAT_DIALOG_DATA).service];
}
