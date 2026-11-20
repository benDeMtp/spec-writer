import { LiveAnnouncer } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Router } from '@angular/router';
import {
  OnboardingApi,
  OnboardingStep,
  STEP_ORDER,
  STEP_SLUGS,
  StepState,
  stepOfSlug,
} from '../../core/onboarding/onboarding.api';
import { ProjectsStore } from '../../core/projects/projects.store';
import { ConfluenceApi } from '../../core/settings/confluence.api';
import { SettingsStore } from '../../core/settings/settings.store';
import { TourService } from '../../core/tour/tour.service';
import { ArchitectureModelComponent } from '../project-settings/architecture-model.component';
import { DocSourcesComponent } from '../project-settings/doc-sources.component';
import { ConfluenceCardComponent } from '../settings/confluence-card.component';
import { CopilotCardComponent } from '../settings/copilot-card.component';
import { JiraCardComponent } from '../settings/jira-card.component';
import { IndexationStepComponent } from './indexation-step.component';
import { ProjectStepComponent } from './project-step.component';

const TITLES: Record<OnboardingStep, string> = {
  WELCOME: 'Bienvenue',
  COPILOT: 'Connecter GitHub Copilot',
  JIRA: 'Connecter Jira',
  CONFLUENCE: 'Connecter Confluence',
  PROJECT: 'Votre projet',
  SOURCES: 'Documentation et architecture',
  INDEXATION: 'Indexation du projet',
  DONE: 'C’est prêt',
};

const STATE_LABELS: Record<StepState, string> = { TODO: 'à faire', DONE: 'faite', SKIPPED: 'passée' };

/** Étapes suivies, dans l'ordre (sans l'accueil ni la fin). */
const TRACKED: OnboardingStep[] = STEP_ORDER.filter((s) => s !== 'WELCOME' && s !== 'DONE');

/**
 * Assistant de premier lancement (018, US1) : de la connexion à Copilot jusqu'à l'indexation du
 * projet. Chaque étape place les écrans existants (cartes de connexion, identité du projet,
 * sources) ; seule la connexion à Copilot est obligatoire (FR-003).
 */
@Component({
  selector: 'sw-onboarding-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ArchitectureModelComponent,
    ConfluenceCardComponent,
    CopilotCardComponent,
    DocSourcesComponent,
    IndexationStepComponent,
    JiraCardComponent,
    MatButtonModule,
    ProjectStepComponent,
  ],
  template: `
    <h1>Premiers pas</h1>
    @if (state(); as o) {
      <nav aria-label="Étapes de l'assistant">
        <ol class="steps">
          @for (s of tracked; track s; let i = $index) {
            <li [class.current]="s === step()" [attr.aria-current]="s === step() ? 'step' : null">
              <span class="name">{{ i + 1 }}. {{ title(s) }}</span>
              <span class="state" [attr.data-state]="stateOf(s)">
                {{ stateLabel(s) }}{{ s === 'COPILOT' ? ' · obligatoire' : '' }}
              </span>
            </li>
          }
        </ol>
      </nav>

      <section class="sheet" aria-labelledby="step-title">
        <h2 id="step-title" tabindex="-1" #stepTitle>{{ title(step()) }}</h2>
        @switch (step()) {
          @case ('WELCOME') {
            <p>
              spec-writer vous aide à rédiger des spécifications avec un agent qui connaît votre projet. Cet assistant le
              prépare en six étapes, une dizaine de minutes hors durée d'indexation.
            </p>
            <ul>
              <li>Connecter GitHub Copilot : <strong>obligatoire</strong>, c'est lui qui répond et rédige.</li>
              <li>Connecter Jira et Confluence : facultatif.</li>
              <li>Nommer votre projet, déclarer sa documentation et son modèle d'architecture : facultatif.</li>
              <li>Indexer le projet, pour que l'agent le connaisse.</li>
            </ul>
            <p class="hint">Chaque étape facultative se passe d'un clic et se reprend plus tard depuis les réglages.</p>
          }
          @case ('COPILOT') {
            <p>Connectez votre compte GitHub, choisissez le modèle, puis donnez votre accord pour l'envoi des données à Copilot.</p>
            <sw-copilot-card />
            @if (!copilotReady()) {
              <p class="hint" id="copilot-missing">{{ copilotMissing() }}</p>
            }
          }
          @case ('JIRA') {
            <p>L'agent lit les tickets du projet Jira et peut créer des tickets depuis une spécification.</p>
            <sw-jira-card />
          }
          @case ('CONFLUENCE') {
            <p>Déclarez le serveur Confluence si la documentation du projet s'y trouve.</p>
            <sw-confluence-card />
          }
          @case ('PROJECT') {
            <sw-project-step [jiraConnected]="jiraConnected()" (saved)="projectSaved.set(true)" />
          }
          @case ('SOURCES') {
            @if (projects.current(); as p) {
              <h3>Sources de documentation</h3>
              <sw-doc-sources [project]="p" />
              <h3>Modèle d'architecture</h3>
              <sw-architecture-model [project]="p" />
            }
          }
          @case ('INDEXATION') {
            <sw-indexation-step (openConversation)="openConversation()" />
          }
          @case ('DONE') {
            <p>Votre projet est configuré. Tout se modifie ensuite dans Configuration et dans les réglages du projet.</p>
            <div class="actions">
              <button mat-flat-button type="button" (click)="openConversation()">Ouvrir la conversation</button>
              <button mat-stroked-button type="button" (click)="tour()">Faire la visite guidée</button>
            </div>
          }
        }
      </section>

      @if (step() !== 'DONE') {
        <div class="actions nav">
          @if (step() !== 'WELCOME') {
            <button mat-button type="button" (click)="previous()" [disabled]="busy()">Précédent</button>
          }
          @if (step() === 'WELCOME') {
            <button mat-flat-button type="button" (click)="begin()" [disabled]="busy()">Commencer</button>
          } @else {
            <button
              mat-flat-button
              type="button"
              (click)="next()"
              [disabled]="busy() || !canContinue()"
              [attr.aria-describedby]="step() === 'COPILOT' && !copilotReady() ? 'copilot-missing' : null"
            >
              Suivant
            </button>
          }
          @if (optional()) {
            <button mat-button type="button" (click)="skip()" [disabled]="busy()">Passer</button>
          }
        </div>
      }
    } @else {
      <p role="status" aria-live="polite">Chargement de l'assistant…</p>
    }
  `,
  styles: `
    h1 {
      margin: 0 0 1rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-3);
      font-weight: 600;
    }
    h2 {
      margin: 0 0 0.75rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-2);
      font-weight: 600;
      outline: none;
    }
    h3 {
      margin: 1.25rem 0 0.5rem;
      font-size: var(--sw-step-0);
      font-weight: 650;
    }
    .steps {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin: 0 0 1.5rem;
      padding: 0;
      list-style: none;
    }
    .steps li {
      display: grid;
      padding: 0.4rem 0.75rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
    }
    .steps li.current {
      border-color: var(--sw-ink);
      font-weight: 600;
    }
    .state {
      font-size: 0.875rem;
      font-weight: 400;
      color: var(--sw-ink-soft);
    }
    .sheet {
      max-width: var(--sw-measure);
      margin-bottom: 1.5rem;
      padding: 1.5rem clamp(1rem, 3vw, 2rem);
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-sheet);
    }
    .hint {
      color: var(--sw-ink-soft);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
  `,
})
export class OnboardingPage {
  /** Segment d'adresse de l'étape (`/premiers-pas/:etape`). */
  readonly etape = input<string>();

  private readonly api = inject(OnboardingApi);
  private readonly router = inject(Router);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly settings = inject(SettingsStore);
  private readonly confluence = inject(ConfluenceApi);
  protected readonly projects = inject(ProjectsStore);
  private readonly tours = inject(TourService);
  private readonly stepTitle = viewChild<ElementRef<HTMLElement>>('stepTitle');

  protected readonly tracked = TRACKED;
  protected readonly state = this.api.state;
  protected readonly busy = signal(false);
  protected readonly projectSaved = signal(false);
  protected readonly step = computed<OnboardingStep>(() => stepOfSlug(this.etape()) ?? 'WELCOME');
  protected readonly optional = computed(() => TRACKED.includes(this.step()) && this.step() !== 'COPILOT');

  protected readonly jiraConnected = computed(() => this.settings.jira()?.lastTest?.result?.startsWith('OK') ?? false);
  protected readonly copilotReady = computed(() => {
    const c = this.settings.copilot();
    return c?.state === 'CONNECTED' && !!c.model && c.consent;
  });
  protected readonly copilotMissing = computed(() => {
    const c = this.settings.copilot();
    if (c?.state !== 'CONNECTED') {
      return 'Connectez votre compte GitHub pour continuer : c’est la seule étape obligatoire.';
    }
    if (!c.model) {
      return 'Choisissez un modèle pour continuer.';
    }
    return 'Activez l’accord d’envoi des données à Copilot pour continuer.';
  });

  /** « Suivant » marque l'étape faite : seulement quand ce qu'elle demande est fait. */
  protected readonly canContinue = computed(() => {
    switch (this.step()) {
      case 'COPILOT':
        return this.copilotReady();
      case 'JIRA':
        return this.jiraConnected();
      case 'CONFLUENCE':
        return this.confluence.settings()?.lastTest?.result === 'OK';
      case 'PROJECT':
        return this.projectSaved();
      case 'SOURCES': {
        const p = this.projects.current();
        return !!p && (p.docSources.length > 0 || !!p.architectureWorkspace);
      }
      default:
        return true;
    }
  });

  constructor() {
    void this.settings.ensureLoaded().catch(() => undefined);
    void this.confluence.load().catch(() => undefined);
    if (!this.projects.current()) {
      void this.projects.load().catch(() => undefined);
    }
    // L'étape de l'adresse devient l'étape courante (reprise, retour en arrière, lien direct).
    effect(() => {
      const wanted = this.step();
      untracked(() => void this.follow(wanted));
    });
  }

  protected title(s: OnboardingStep): string {
    return TITLES[s];
  }

  protected stateOf(s: OnboardingStep): StepState {
    return this.state()?.steps.find((v) => v.step === s)?.state ?? 'TODO';
  }

  protected stateLabel(s: OnboardingStep): string {
    return s === this.step() ? 'en cours' : STATE_LABELS[this.stateOf(s)];
  }

  protected async begin(): Promise<void> {
    await this.run(async () => this.show((await this.api.goTo('COPILOT')).currentStep));
  }

  protected async next(): Promise<void> {
    const step = this.step();
    await this.run(async () => this.show((await this.api.mark(step, 'DONE')).currentStep));
  }

  protected async skip(): Promise<void> {
    const step = this.step();
    await this.run(async () => this.show((await this.api.mark(step, 'SKIPPED')).currentStep));
  }

  protected async previous(): Promise<void> {
    const index = STEP_ORDER.indexOf(this.step());
    const target = STEP_ORDER[Math.max(0, index - 1)];
    await this.run(async () => this.show((await this.api.goTo(target)).currentStep));
  }

  protected async openConversation(): Promise<void> {
    await this.finish();
    await this.router.navigateByUrl('/');
  }

  protected async tour(): Promise<void> {
    await this.finish();
    await this.router.navigateByUrl('/');
    await this.tours.start();
  }

  private async finish(): Promise<void> {
    if (!this.state()?.completed && this.copilotReady()) {
      await this.api.complete().catch(() => undefined);
    }
  }

  /** Aligne l'étape enregistrée sur l'adresse ; une étape interdite ramène à l'étape courante. */
  private async follow(wanted: OnboardingStep): Promise<void> {
    try {
      const current = this.state() ?? (await this.api.load());
      if (current.currentStep !== wanted && wanted !== 'WELCOME') {
        await this.api.goTo(wanted);
      }
      this.announce(wanted);
    } catch {
      const current = this.state() ?? (await this.api.load().catch(() => null));
      if (current && current.currentStep !== wanted) {
        await this.router.navigateByUrl(`/premiers-pas/${STEP_SLUGS[current.currentStep]}`, { replaceUrl: true });
      }
    }
  }

  private async show(step: OnboardingStep): Promise<void> {
    await this.router.navigateByUrl(`/premiers-pas/${STEP_SLUGS[step]}`);
  }

  private announce(step: OnboardingStep): void {
    const index = TRACKED.indexOf(step);
    const position = index >= 0 ? `Étape ${index + 1} sur ${TRACKED.length} : ` : '';
    void this.announcer.announce(`${position}${TITLES[step]}`, 'polite');
    setTimeout(() => this.stepTitle()?.nativeElement.focus());
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
    } catch {
      // le bandeau d'erreur affiche la cause (étape obligatoire, connexion perdue)
    } finally {
      this.busy.set(false);
    }
  }
}
