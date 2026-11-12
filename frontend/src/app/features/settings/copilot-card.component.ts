import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Subscription } from 'rxjs';
import { ConnectionStart, FlowState, ModelInfo } from '../../core/settings/settings.model';
import { SettingsStore } from '../../core/settings/settings.store';

type Step = 'idle' | 'starting' | 'ready' | 'waiting' | FlowState;

const NUMBER = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 });
/** Conversion prudente pour le français, celle du budget de la connaissance (research.md R2). */
const CHARS_PER_TOKEN = 3;

/** Signification des permissions GitHub demandées par le runtime Copilot (FR-030a). */
const SCOPE_LABELS: Record<string, string> = {
  'read:user': 'lire votre profil GitHub',
  'read:org': 'lire les organisations dont vous êtes membre',
  repo: 'accéder à vos dépôts',
  gist: 'créer et lire vos gists',
  codespace: 'gérer vos codespaces',
  'write:plugin_gateway_connections': 'gérer les connexions des extensions Copilot',
};

/** Connexion GitHub Copilot : autorisation dans le navigateur, puis choix du modèle (US1), avec sa capacité et son coût (008). */
@Component({
  selector: 'sw-copilot-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatFormFieldModule, MatSelectModule, MatSlideToggleModule],
  template: `
    <section class="sheet" aria-labelledby="copilot-title">
      <h3 id="copilot-title">GitHub Copilot</h3>
      @let c = store.copilot();

      @if (c?.state === 'CONNECTED') {
        <p class="status ok">Connecté en tant que <strong>{{ c!.login }}</strong>.</p>
        <mat-form-field appearance="outline" class="model">
          <mat-label>Modèle utilisé</mat-label>
          <mat-select [value]="c!.model" (selectionChange)="selectModel($event.value)">
            <mat-select-trigger>{{ chosenName() }}</mat-select-trigger>
            @for (m of c!.models; track m.id) {
              <mat-option [value]="m.id">{{ m.name }} <span class="model-capacity">{{ capacity(m) }}</span></mat-option>
            }
          </mat-select>
          <mat-hint>Seuls les modèles autorisés pour votre compte sont proposés.</mat-hint>
        </mat-form-field>
        <p class="knowledge">Connaissance transmise : jusqu’à {{ chars(c!.knowledgeChars) }} caractères</p>
        <mat-slide-toggle [checked]="c!.consent" (change)="store.setConsent('COPILOT', $event.checked)">
          Envoyer mes besoins et mes réponses à GitHub Copilot
        </mat-slide-toggle>
        <div class="actions">
          <button mat-stroked-button type="button" (click)="disconnect()">Se déconnecter de GitHub</button>
        </div>
      } @else {
        @switch (step()) {
          @case ('ready') {
            <p>GitHub va vous demander d'autoriser spec-writer à :</p>
            <ul class="scopes">
              @for (s of start()!.requestedScopes; track s) {
                <li><code>{{ s }}</code> {{ scopeLabel(s) }}</li>
              }
            </ul>
            <p class="hint">Ces permissions sont celles du client GitHub Copilot. spec-writer n'utilise que l'accès aux modèles.</p>
            <div class="actions">
              <a mat-flat-button [href]="start()!.authorizationUrl" target="_blank" rel="noopener noreferrer" (click)="waitForAuthorization()">
                Ouvrir GitHub
              </a>
              <button mat-button type="button" (click)="cancel()">Annuler</button>
            </div>
          }
          @case ('waiting') {
            <p role="status" aria-live="polite">
              En attente de votre autorisation dans l'onglet GitHub. Revenez ici une fois l'autorisation donnée.
            </p>
            <div class="actions">
              <button mat-button type="button" (click)="cancel()">Annuler</button>
            </div>
          }
          @default {
            @if (outcomeMessage(); as message) {
              <p class="status warn" role="alert">{{ message }}</p>
            } @else if (c?.state === 'NO_COPILOT') {
              <p class="status warn" role="alert">{{ noCopilotMessage }}</p>
            } @else {
              <p>spec-writer utilise votre compte GitHub Copilot pour poser les questions et rédiger les spécifications.</p>
            }
            <div class="actions">
              <button mat-flat-button type="button" [disabled]="step() === 'starting'" (click)="connect()">
                {{ step() === 'idle' && c?.state !== 'NO_COPILOT' ? 'Se connecter à GitHub' : 'Recommencer' }}
              </button>
            </div>
          }
        }
      }
      @if (c?.secretPersistence === 'MEMORY_ONLY') {
        <p class="status warn">
          Le trousseau du système est indisponible : la connexion sera perdue à la fermeture de spec-writer.
        </p>
      }
    </section>
  `,
  styleUrl: './settings-card.scss',
  styles: `
    .model-capacity {
      margin-left: 0.25rem;
      font-size: 0.8125rem;
      opacity: 0.8;
    }
    .knowledge {
      margin: 0 0 0.75rem;
      font-size: 0.875rem;
    }
  `,
})
export class CopilotCardComponent {
  protected readonly store = inject(SettingsStore);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly step = signal<Step>('idle');
  protected readonly start = signal<ConnectionStart | null>(null);
  private watch?: Subscription;

  protected readonly noCopilotMessage =
    "Ce compte GitHub n'a pas d'abonnement Copilot actif. Connectez-vous avec un compte qui en dispose, " +
    'ou demandez un accès à votre administrateur GitHub.';

  /** Nom du modèle choisi, sans sa capacité, dans le champ fermé. */
  protected readonly chosenName = computed(() => {
    const c = this.store.copilot();
    return c?.models.find((m) => m.id === c.model)?.name ?? c?.model ?? '';
  });

  protected readonly outcomeMessage = computed(() => {
    switch (this.step()) {
      case 'EXPIRED':
        return "Le délai d'autorisation est dépassé. Recommencez la connexion.";
      case 'DENIED':
        return "L'autorisation a été refusée sur GitHub. Recommencez si c'était une erreur.";
      case 'NO_COPILOT':
        return this.noCopilotMessage;
      case 'ERROR':
        return "La connexion n'a pas abouti. Recommencez dans un instant.";
      default:
        return null;
    }
  });

  constructor() {
    this.destroyRef.onDestroy(() => this.watch?.unsubscribe());
  }

  /** « environ N tokens (≈ M caractères) · ×coefficient », ou ce qui est inconnu (008, FR-002). */
  protected capacity(m: ModelInfo): string {
    const size =
      m.promptTokens === null
        ? 'capacité inconnue'
        : `environ ${NUMBER.format(m.promptTokens)} tokens (≈ ${NUMBER.format(m.promptTokens * CHARS_PER_TOKEN)} caractères)`;
    const cost = m.multiplier === null ? 'coût inconnu' : `×${NUMBER.format(m.multiplier)}`;
    return `${size} · ${cost}`;
  }

  protected chars(n: number): string {
    return NUMBER.format(n);
  }

  protected scopeLabel(scope: string): string {
    return SCOPE_LABELS[scope] ?? '';
  }

  protected async connect(): Promise<void> {
    this.cancel();
    this.step.set('starting');
    try {
      this.start.set(await this.store.startCopilotConnection());
      this.step.set('ready');
    } catch {
      this.step.set('ERROR');
    }
  }

  protected waitForAuthorization(): void {
    const flow = this.start();
    if (!flow) {
      return;
    }
    this.step.set('waiting');
    this.watch = this.store.watchCopilotConnection(flow.flowId).subscribe({
      next: (status) => {
        if (status.state !== 'PENDING') {
          this.step.set(status.state === 'CONNECTED' ? 'idle' : status.state);
        }
      },
      error: () => this.step.set('ERROR'),
    });
  }

  protected cancel(): void {
    this.watch?.unsubscribe();
    this.watch = undefined;
    this.start.set(null);
    this.step.set('idle');
  }

  protected selectModel(model: string): void {
    void this.store.selectModel(model);
  }

  protected disconnect(): void {
    this.cancel();
    void this.store.disconnectCopilot();
  }
}
