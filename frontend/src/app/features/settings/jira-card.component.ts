import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { firstValueFrom } from 'rxjs';
import { JiraTestOutcome } from '../../core/settings/settings.model';
import { SettingsStore } from '../../core/settings/settings.store';
import { ConsentDialogComponent } from './consent-dialog.component';

const PROJECT_KEY = /^[A-Z][A-Z0-9_]+$/;

/** Accès à Jira on premise : adresse, projet, jeton masqué, test de connexion (US1-4 à US1-6). */
@Component({
  selector: 'sw-jira-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
  ],
  template: `
    <section class="sheet" aria-labelledby="jira-title">
      <h2 id="jira-title">Jira</h2>
      @let j = store.jira();
      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        <mat-form-field appearance="outline">
          <mat-label>Adresse du serveur</mat-label>
          <input matInput formControlName="serverUrl" type="url" autocomplete="url" placeholder="https://jira.mon-entreprise.fr" />
          @if (form.controls.serverUrl.invalid && form.controls.serverUrl.touched) {
            <mat-error>Saisissez l'adresse du serveur Jira.</mat-error>
          }
        </mat-form-field>

        @if (insecure()) {
          <div class="status warn" role="alert">
            <p>Cette adresse n'est pas chiffrée (http). Votre jeton circulerait en clair sur le réseau.</p>
            <mat-checkbox formControlName="acceptInsecureHttp">Je comprends et je veux continuer en http</mat-checkbox>
          </div>
        }

        <mat-form-field appearance="outline">
          <mat-label>Clé du projet par défaut</mat-label>
          <input matInput formControlName="defaultProjectKey" autocomplete="off" />
          <mat-hint>En majuscules, par exemple PROJ.</mat-hint>
          @if (form.controls.defaultProjectKey.invalid && form.controls.defaultProjectKey.touched) {
            <mat-error>La clé doit être en majuscules, par exemple PROJ ou SPEC_2.</mat-error>
          }
        </mat-form-field>

        @if (j?.tokenSet && !replacingToken()) {
          <div class="token-set">
            <p>Jeton d'accès personnel enregistré dans le trousseau.</p>
            <div class="actions">
              <button mat-stroked-button type="button" (click)="replacingToken.set(true)">Remplacer</button>
              @if (confirmingDelete()) {
                <button mat-flat-button type="button" class="danger" (click)="remove()">Confirmer la suppression</button>
                <button mat-button type="button" (click)="confirmingDelete.set(false)">Annuler</button>
              } @else {
                <button mat-button type="button" (click)="confirmingDelete.set(true)">Supprimer</button>
              }
            </div>
            @if (confirmingDelete()) {
              <p class="hint">La configuration Jira et son jeton seront supprimés.</p>
            }
          </div>
        } @else {
          <mat-form-field appearance="outline">
            <mat-label>Jeton d'accès personnel</mat-label>
            <input matInput formControlName="token" type="password" autocomplete="off" />
            <mat-hint>Créé dans Jira : Profil, puis Jetons d'accès personnels.</mat-hint>
          </mat-form-field>
        }

        <mat-form-field appearance="outline">
          <mat-label>Certificat de l'autorité interne (facultatif)</mat-label>
          <input matInput formControlName="caCertificatePath" autocomplete="off" placeholder="/chemin/vers/autorite.pem" />
          <mat-hint>Fichier PEM, si votre serveur Jira utilise un certificat d'entreprise.</mat-hint>
        </mat-form-field>

        <div class="actions">
          <button mat-flat-button type="submit" [disabled]="saving()">Enregistrer</button>
          @if (j) {
            <button mat-stroked-button type="button" [disabled]="testing()" (click)="test()">Tester la connexion</button>
          }
        </div>
      </form>

      @if (outcome(); as o) {
        <div class="status" [class.ok]="o.result === 'OK'" [class.warn]="o.result !== 'OK'" role="status" aria-live="polite">
          <p>{{ o.userMessage }}@if (o.serverVersion) { (Jira {{ o.serverVersion }})}</p>
        </div>
      }

      @if (j) {
        <mat-slide-toggle [checked]="j.consent" (change)="store.setConsent('JIRA', $event.checked)">
          Envoyer des données à Jira
        </mat-slide-toggle>
        @if (j.secretPersistence === 'MEMORY_ONLY') {
          <p class="status warn">
            Le trousseau du système est indisponible : le jeton sera oublié à la fermeture de spec-writer.
          </p>
        }
      }
    </section>
  `,
  styleUrl: './settings-card.scss',
})
export class JiraCardComponent {
  protected readonly store = inject(SettingsStore);
  private readonly dialog = inject(MatDialog);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    serverUrl: ['', [Validators.required]],
    defaultProjectKey: ['', [Validators.required, Validators.pattern(PROJECT_KEY)]],
    token: [''],
    caCertificatePath: [''],
    acceptInsecureHttp: [false],
  });

  protected readonly replacingToken = signal(false);
  protected readonly confirmingDelete = signal(false);
  protected readonly saving = signal(false);
  protected readonly testing = signal(false);
  protected readonly outcome = signal<JiraTestOutcome | null>(null);

  private readonly serverUrl = toSignal(this.form.controls.serverUrl.valueChanges, { initialValue: '' });
  protected readonly insecure = computed(() => /^http:\/\//i.test(this.serverUrl().trim()));

  constructor() {
    // Préremplit le formulaire à chaque chargement de la configuration ; le jeton n'est jamais relu.
    effect(() => {
      const j = this.store.jira();
      if (j) {
        this.form.patchValue({
          serverUrl: j.serverUrl,
          defaultProjectKey: j.defaultProjectKey,
          caCertificatePath: j.caCertificatePath ?? '',
          acceptInsecureHttp: j.insecureHttp,
        });
      }
    });
  }

  protected async save(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid || (this.insecure() && !this.form.controls.acceptInsecureHttp.value)) {
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    try {
      await this.store.saveJira({
        serverUrl: v.serverUrl.trim(),
        defaultProjectKey: v.defaultProjectKey.trim(),
        token: v.token ? v.token : undefined,
        caCertificatePath: v.caCertificatePath.trim() || null,
        acceptInsecureHttp: this.insecure() && v.acceptInsecureHttp,
      });
      this.form.controls.token.reset('');
      this.replacingToken.set(false);
      this.outcome.set(null);
      if (!this.store.jira()?.consent) {
        const granted = await firstValueFrom(
          this.dialog.open(ConsentDialogComponent, { data: { service: 'JIRA' }, autoFocus: 'dialog' }).afterClosed(),
        );
        if (granted === true) {
          await this.store.setConsent('JIRA', true);
        }
      }
    } catch {
      // Le bandeau d'erreur affiche la cause (FR-028).
    } finally {
      this.saving.set(false);
    }
  }

  protected async test(): Promise<void> {
    this.testing.set(true);
    try {
      this.outcome.set(await this.store.testJira());
    } catch {
      this.outcome.set(null);
    } finally {
      this.testing.set(false);
    }
  }

  protected async remove(): Promise<void> {
    this.confirmingDelete.set(false);
    await this.store.deleteJira();
    this.form.reset();
    this.outcome.set(null);
  }
}
