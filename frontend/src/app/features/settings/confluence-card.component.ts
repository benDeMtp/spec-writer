import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ConfluenceApi, ConfluenceTestOutcome } from '../../core/settings/confluence.api';

/**
 * Serveur Confluence Server / Data Center de l'outil (016, R4) : adresse, jeton personnel masqué,
 * certificat de l'autorité interne, test de connexion. Lecture seule : aucun accord n'est demandé,
 * rien n'est envoyé à Confluence.
 */
@Component({
  selector: 'sw-confluence-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule],
  template: `
    <section class="sheet" aria-labelledby="confluence-title" data-testid="confluence-card">
      <h3 id="confluence-title">Confluence</h3>
      @let c = api.settings();
      <p class="hint">
        Pages Confluence indexées comme documentation des projets. Les racines se choisissent dans les réglages de
        chaque projet.
      </p>
      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        <mat-form-field appearance="outline">
          <mat-label>Adresse du serveur</mat-label>
          <input
            matInput
            formControlName="serverUrl"
            type="url"
            autocomplete="url"
            placeholder="https://wiki.mon-entreprise.fr/confluence"
          />
          <mat-hint>Chemin compris, tel qu'il apparaît avant /pages/… dans votre navigateur.</mat-hint>
          @if (form.controls.serverUrl.invalid && form.controls.serverUrl.touched) {
            <mat-error>Saisissez l'adresse du serveur Confluence.</mat-error>
          }
        </mat-form-field>

        @if (insecure()) {
          <div class="status warn" role="alert">
            <p>Cette adresse n'est pas chiffrée (http). Votre jeton circulerait en clair sur le réseau.</p>
            <mat-checkbox formControlName="acceptInsecureHttp">Je comprends et je veux continuer en http</mat-checkbox>
          </div>
        }

        @if (c?.tokenSet && !replacingToken()) {
          <div class="token-set">
            <p>Jeton d'accès personnel enregistré dans le trousseau.</p>
            <div class="actions">
              <button mat-stroked-button type="button" (click)="replacingToken.set(true)">Remplacer</button>
              @if (confirmingDelete()) {
                <button mat-flat-button type="button" class="danger" (click)="remove()">Confirmer le retrait</button>
                <button mat-button type="button" (click)="confirmingDelete.set(false)">Annuler</button>
              } @else {
                <button mat-button type="button" (click)="confirmingDelete.set(true)">Retirer le serveur</button>
              }
            </div>
            @if (confirmingDelete()) {
              <p class="hint">
                Le serveur et son jeton seront retirés. Les pages déjà indexées restent ; leur relecture sera en erreur
                jusqu'à un nouveau serveur.
              </p>
            }
          </div>
        } @else {
          <mat-form-field appearance="outline">
            <mat-label>Jeton d'accès personnel</mat-label>
            <input matInput formControlName="token" type="password" autocomplete="off" />
            <mat-hint>Créé dans Confluence : Profil, puis Jetons d'accès personnels.</mat-hint>
          </mat-form-field>
        }

        <mat-form-field appearance="outline">
          <mat-label>Certificat de l'autorité interne (facultatif)</mat-label>
          <input matInput formControlName="caCertificatePath" autocomplete="off" placeholder="/chemin/vers/autorite.pem" />
          <mat-hint>Fichier PEM, si votre serveur Confluence utilise un certificat d'entreprise.</mat-hint>
        </mat-form-field>

        <div class="actions">
          <button mat-flat-button type="submit" [disabled]="saving()">Enregistrer</button>
          @if (c?.enabled) {
            <button mat-stroked-button type="button" [disabled]="testing()" (click)="test()">Tester la connexion</button>
          }
        </div>
      </form>

      @if (outcome(); as o) {
        <div
          class="status"
          [class.ok]="o.result === 'OK'"
          [class.warn]="o.result !== 'OK'"
          role="status"
          aria-live="polite"
          data-testid="confluence-test"
        >
          <p>
            {{ o.message }}
            @if (o.account) {
              Compte : {{ o.account }}.
            }
            @if (o.serverVersion) {
              (Confluence {{ o.serverVersion }})
            }
          </p>
        </div>
      }

      @if (c?.secretPersistence === 'MEMORY_ONLY') {
        <p class="status warn">
          Le trousseau du système est indisponible : le jeton sera oublié à la fermeture de spec-writer.
        </p>
      }
    </section>
  `,
  styleUrl: './settings-card.scss',
})
export class ConfluenceCardComponent implements OnInit {
  protected readonly api = inject(ConfluenceApi);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({
    serverUrl: ['', [Validators.required]],
    token: [''],
    caCertificatePath: [''],
    acceptInsecureHttp: [false],
  });

  protected readonly replacingToken = signal(false);
  protected readonly confirmingDelete = signal(false);
  protected readonly saving = signal(false);
  protected readonly testing = signal(false);
  protected readonly outcome = signal<ConfluenceTestOutcome | null>(null);

  private readonly serverUrl = toSignal(this.form.controls.serverUrl.valueChanges, { initialValue: '' });
  protected readonly insecure = computed(() => /^http:\/\//i.test(this.serverUrl().trim()));

  async ngOnInit(): Promise<void> {
    try {
      const c = await this.api.load();
      if (c.enabled) {
        this.form.patchValue({
          serverUrl: c.serverUrl ?? '',
          caCertificatePath: c.caCertificatePath ?? '',
          acceptInsecureHttp: c.insecureHttp,
        });
      }
    } catch {
      // Le bandeau d'erreur affiche la cause.
    }
  }

  protected async save(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid || (this.insecure() && !this.form.controls.acceptInsecureHttp.value)) {
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    try {
      await this.api.save({
        serverUrl: v.serverUrl.trim(),
        caCertificatePath: v.caCertificatePath.trim() || null,
        acceptInsecureHttp: this.insecure() && v.acceptInsecureHttp,
        token: v.token ? v.token : undefined,
      });
      this.form.controls.token.reset('');
      this.replacingToken.set(false);
      this.outcome.set(null);
    } catch {
      // Le bandeau d'erreur affiche la cause.
    } finally {
      this.saving.set(false);
    }
  }

  protected async test(): Promise<void> {
    this.testing.set(true);
    try {
      this.outcome.set(await this.api.test());
    } catch {
      this.outcome.set(null);
    } finally {
      this.testing.set(false);
    }
  }

  protected async remove(): Promise<void> {
    this.confirmingDelete.set(false);
    await this.api.remove();
    this.form.reset();
    this.outcome.set(null);
  }
}
