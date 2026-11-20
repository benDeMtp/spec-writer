import { LiveAnnouncer } from '@angular/cdk/a11y';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonModule } from '@angular/material/button';
import { Router, provideRouter } from '@angular/router';
import { Onboarding, OnboardingStep } from '../../core/onboarding/onboarding.api';
import { OnboardingPage } from './onboarding.page';

const STEPS: OnboardingStep[] = ['COPILOT', 'JIRA', 'CONFLUENCE', 'PROJECT', 'SOURCES', 'INDEXATION'];

function onboarding(current: OnboardingStep, done: OnboardingStep[] = [], skipped: OnboardingStep[] = []): Onboarding {
  return {
    currentStep: current,
    steps: STEPS.map((step) => ({
      step,
      required: step === 'COPILOT',
      state: done.includes(step) ? 'DONE' : skipped.includes(step) ? 'SKIPPED' : 'TODO',
    })),
    completed: false,
    completedAt: null,
    autoOpen: true,
  };
}

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

function button(root: HTMLElement, label: string): HTMLButtonElement | undefined {
  return Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.trim() === label);
}

describe('OnboardingPage', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<OnboardingPage>;
  let el: HTMLElement;
  let router: Router;
  let announced: string[];

  async function open(slug: string, state: Onboarding, copilot: Record<string, unknown>): Promise<void> {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    // les écrans existants (cartes, sources) ont leurs propres tests : seul le cadre est testé ici
    TestBed.overrideComponent(OnboardingPage, { set: { imports: [MatButtonModule], schemas: [CUSTOM_ELEMENTS_SCHEMA] } });
    ctrl = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    announced = [];
    vi.spyOn(TestBed.inject(LiveAnnouncer), 'announce').mockImplementation(async (m) => {
      announced.push(String(m));
    });
    fixture = TestBed.createComponent(OnboardingPage);
    fixture.componentRef.setInput('etape', slug);
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/settings').flush({ copilot, jira: null });
    ctrl.expectOne('/api/settings/confluence').flush({ enabled: false, lastTest: null });
    ctrl.expectOne('/api/projects').flush([]);
    ctrl.expectOne('/api/projects/current').flush(null);
    await settle(fixture);
    ctrl.expectOne('/api/onboarding').flush(state);
    await settle(fixture);
  }

  afterEach(() => ctrl.verify());

  it("à l'étape Copilot, Suivant attend la connexion et l'étape ne se passe pas", async () => {
    await open('copilot', onboarding('COPILOT'), { state: 'NOT_CONFIGURED', consent: false, model: null });
    expect(el.querySelector('h2')?.textContent).toBe('Connecter GitHub Copilot');
    expect(button(el, 'Suivant')!.disabled).toBe(true);
    expect(button(el, 'Passer')).toBeUndefined();
    expect(el.querySelector('#copilot-missing')?.textContent).toContain('seule étape obligatoire');
    expect(announced).toContain('Étape 1 sur 6 : Connecter GitHub Copilot');
    expect(el.querySelector('[aria-current="step"]')?.textContent).toContain('Connecter GitHub Copilot');
  });

  it("Copilot connecté, modèle choisi et accord donné : Suivant marque l'étape faite et mène à Jira", async () => {
    await open('copilot', onboarding('COPILOT'), { state: 'CONNECTED', consent: true, model: 'claude-sonnet-5.5' });
    button(el, 'Suivant')!.click();
    const put = ctrl.expectOne('/api/onboarding/steps/COPILOT');
    expect(put.request.body).toEqual({ state: 'DONE' });
    put.flush(onboarding('JIRA', ['COPILOT']));
    await settle(fixture);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/premiers-pas/jira');
  });

  it("une étape facultative se passe d'un clic, et Précédent revient en arrière", async () => {
    await open('jira', onboarding('JIRA', ['COPILOT']), { state: 'CONNECTED', consent: true, model: 'm' });
    expect(button(el, 'Suivant')!.disabled).toBe(true);
    button(el, 'Passer')!.click();
    const put = ctrl.expectOne('/api/onboarding/steps/JIRA');
    expect(put.request.body).toEqual({ state: 'SKIPPED' });
    put.flush(onboarding('CONFLUENCE', ['COPILOT'], ['JIRA']));
    await settle(fixture);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/premiers-pas/confluence');
    button(el, 'Précédent')!.click();
    const back = ctrl.expectOne('/api/onboarding/current');
    expect(back.request.body).toEqual({ step: 'COPILOT' });
    back.flush(onboarding('COPILOT', ['COPILOT'], ['JIRA']));
    await settle(fixture);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/premiers-pas/copilot');
  });

  it("une adresse d'étape différente de l'étape courante la rend courante (reprise, lien direct)", async () => {
    await open('projet', onboarding('JIRA', ['COPILOT']), { state: 'CONNECTED', consent: true, model: 'm' });
    const go = ctrl.expectOne('/api/onboarding/current');
    expect(go.request.body).toEqual({ step: 'PROJECT' });
    go.flush(onboarding('PROJECT', ['COPILOT']));
    await settle(fixture);
    expect(el.querySelector('h2')?.textContent).toBe('Votre projet');
    expect(el.textContent).toContain('faite');
  });
});
