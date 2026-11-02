import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { SessionsApi } from '../../core/sessions/sessions.api';
import { SettingsStore } from '../../core/settings/settings.store';
import { ConsentDialogComponent } from '../../shared/ui/consent-dialog.component';

export const MAX_NEED = 10_000;
export const MIN_WORDS = 10;

/** Saisie du besoin initial (FR-001) ; le consentement Copilot est demandé avant le premier envoi (FR-024). */
@Component({
  selector: 'sw-need-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h1>Nouveau besoin</h1>
    <div class="sheet">
      <p class="lead">
        Décrivez ce que vous voulez obtenir, avec vos mots. spec-writer vous posera ensuite quelques questions
        pour lever les ambiguïtés, puis rédigera la spécification.
      </p>
      <form (ngSubmit)="submit()" novalidate>
        <mat-form-field appearance="outline" class="need">
          <mat-label>Votre besoin</mat-label>
          <textarea
            matInput
            name="need"
            [(ngModel)]="text"
            [maxlength]="max"
            rows="9"
            [attr.aria-invalid]="showError()"
            aria-describedby="need-help"
          ></textarea>
          <mat-hint id="need-help" align="start">{{ wordsHint() }}</mat-hint>
          <mat-hint align="end">{{ length() }} / {{ max }}</mat-hint>
        </mat-form-field>
        @if (showError()) {
          <p class="status warn" role="alert">
            Décrivez votre besoin en au moins 10 mots, pour que les questions portent sur votre cas.
          </p>
        }
        <details class="example">
          <summary>Voir un exemple de besoin bien formulé</summary>
          <blockquote>{{ example }}</blockquote>
        </details>
        @if (refused()) {
          <p class="status warn" role="status">
            Sans votre accord, le besoin n'est pas envoyé à GitHub Copilot et le questionnaire ne peut pas démarrer.
          </p>
        }
        <div class="actions">
          <button mat-flat-button type="submit" [disabled]="sending()">
            {{ sending() ? 'Préparation des questions…' : 'Commencer le questionnaire' }}
          </button>
        </div>
        @if (sending()) {
          <p class="hint" role="status" aria-live="polite">GitHub Copilot lit votre besoin et prépare les premières questions.</p>
        }
      </form>
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
    .lead {
      margin: 0 0 1.25rem;
    }
    .need {
      width: 100%;
    }
    textarea {
      font-family: var(--sw-font-doc);
      line-height: 1.65;
    }
    .example {
      margin: 0.75rem 0;
    }
    .example summary {
      cursor: pointer;
      color: var(--sw-pencil);
    }
    blockquote {
      margin: 0.75rem 0 0;
      padding-left: 1rem;
      border-left: 3px solid var(--sw-rule);
      font-family: var(--sw-font-doc);
    }
    .status {
      margin: 0.75rem 0;
      padding: 0.5rem 0.75rem;
      border-left: 4px solid var(--sw-outcome);
      border-radius: var(--sw-radius-field);
    }
    .actions {
      margin-top: 1rem;
    }
    .hint {
      color: var(--sw-ink-soft);
    }
  `,
})
export class NeedPage {
  private readonly api = inject(SessionsApi);
  private readonly settings = inject(SettingsStore);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  protected readonly max = MAX_NEED;
  protected readonly example =
    "Les employés doivent pouvoir réserver une salle de réunion pour une durée donnée depuis leur poste, " +
    'et être prévenus si la salle est déjà prise.';

  protected readonly text = signal('');
  protected readonly submitted = signal(false);
  protected readonly sending = signal(false);
  protected readonly refused = signal(false);

  protected readonly length = computed(() => this.text().length);
  protected readonly words = computed(() => {
    const trimmed = this.text().trim();
    return trimmed ? trimmed.split(/\s+/).length : 0;
  });
  protected readonly valid = computed(() => this.words() >= MIN_WORDS && this.length() <= MAX_NEED);
  protected readonly showError = computed(() => (this.submitted() || this.length() > 0) && this.words() < MIN_WORDS);
  protected readonly wordsHint = computed(() => {
    const missing = MIN_WORDS - this.words();
    return missing > 0 ? `Encore ${missing} mot${missing > 1 ? 's' : ''} au minimum` : `${this.words()} mots`;
  });

  protected async submit(): Promise<void> {
    this.submitted.set(true);
    if (!this.valid() || this.sending()) {
      return;
    }
    this.refused.set(false);
    const settings = await this.settings.ensureLoaded();
    if (!settings.copilot.consent) {
      const granted = await firstValueFrom(
        this.dialog.open(ConsentDialogComponent, { data: { service: 'COPILOT' } }).afterClosed(),
      );
      if (granted !== true) {
        this.refused.set(true);
        return;
      }
      await this.settings.setConsent('COPILOT', true);
    }
    this.sending.set(true);
    try {
      const session = await this.api.create(this.text().trim());
      await this.router.navigate(['/sessions', session.id]);
    } catch {
      // Le bandeau affiche la cause ; le texte saisi est conservé (FR-023a).
    } finally {
      this.sending.set(false);
    }
  }
}
