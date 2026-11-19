import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Étapes de l'assistant de premier lancement, dans l'ordre (018, contracts/rest-api.md). */
export type OnboardingStep = 'WELCOME' | 'COPILOT' | 'JIRA' | 'CONFLUENCE' | 'PROJECT' | 'SOURCES' | 'INDEXATION' | 'DONE';
export type StepState = 'TODO' | 'DONE' | 'SKIPPED';

export interface OnboardingStepView {
  step: OnboardingStep;
  required: boolean;
  state: StepState;
}

export interface Onboarding {
  currentStep: OnboardingStep;
  steps: OnboardingStepView[];
  completed: boolean;
  completedAt: string | null;
  autoOpen: boolean;
}

export const STEP_ORDER: OnboardingStep[] = ['WELCOME', 'COPILOT', 'JIRA', 'CONFLUENCE', 'PROJECT', 'SOURCES', 'INDEXATION', 'DONE'];

/** Segment d'adresse de chaque étape : /premiers-pas/<segment>. */
export const STEP_SLUGS: Record<OnboardingStep, string> = {
  WELCOME: 'accueil',
  COPILOT: 'copilot',
  JIRA: 'jira',
  CONFLUENCE: 'confluence',
  PROJECT: 'projet',
  SOURCES: 'sources',
  INDEXATION: 'indexation',
  DONE: 'fin',
};

export function stepOfSlug(slug: string | null | undefined): OnboardingStep | null {
  const found = (Object.entries(STEP_SLUGS) as [OnboardingStep, string][]).find(([, s]) => s === slug);
  return found ? found[0] : null;
}

@Injectable({ providedIn: 'root' })
export class OnboardingApi {
  private readonly http = inject(HttpClient);
  /** Dernière progression lue ; `null` avant la première lecture. */
  readonly state = signal<Onboarding | null>(null);

  async load(): Promise<Onboarding> {
    return this.keep(await firstValueFrom(this.http.get<Onboarding>('/api/onboarding')));
  }

  async mark(step: OnboardingStep, state: 'DONE' | 'SKIPPED'): Promise<Onboarding> {
    return this.keep(await firstValueFrom(this.http.put<Onboarding>(`/api/onboarding/steps/${step}`, { state })));
  }

  async goTo(step: OnboardingStep): Promise<Onboarding> {
    return this.keep(await firstValueFrom(this.http.put<Onboarding>('/api/onboarding/current', { step })));
  }

  async complete(): Promise<Onboarding> {
    return this.keep(await firstValueFrom(this.http.post<Onboarding>('/api/onboarding/complete', {})));
  }

  private keep(o: Onboarding): Onboarding {
    this.state.set(o);
    return o;
  }
}
