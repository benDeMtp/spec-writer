import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Onboarding } from './onboarding.api';
import { OnboardingService } from './onboarding.service';

function onboarding(patch: Partial<Onboarding>): Onboarding {
  return { currentStep: 'JIRA', steps: [], completed: false, completedAt: null, autoOpen: true, ...patch };
}

describe('OnboardingService', () => {
  let ctrl: HttpTestingController;
  let router: Router;
  let path: string;

  function setUp(pathname: string): OnboardingService {
    path = pathname;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: DOCUMENT,
          useFactory: () => {
            const real = document;
            return new Proxy(real, {
              get: (target, prop) => {
                if (prop === 'location') {
                  return { pathname: path };
                }
                const value = Reflect.get(target, prop, target);
                return typeof value === 'function' ? value.bind(target) : value;
              },
            });
          },
        },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    return TestBed.inject(OnboardingService);
  }

  async function flush(o: Onboarding): Promise<void> {
    ctrl.expectOne('/api/onboarding').flush(o);
    await new Promise((r) => setTimeout(r, 0));
  }

  afterEach(() => ctrl.verify());

  it("mène une installation vierge à l'étape courante de l'assistant", async () => {
    setUp('/').start();
    await flush(onboarding({ currentStep: 'JIRA' }));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/premiers-pas/jira');
  });

  it("ne fait rien quand l'assistant est terminé ou que l'ouverture automatique est coupée", async () => {
    setUp('/').start();
    await flush(onboarding({ autoOpen: false, completed: true, currentStep: 'DONE' }));
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it("ne détourne pas une adresse déjà dans l'assistant", async () => {
    setUp('/premiers-pas/sources').start();
    await flush(onboarding({ currentStep: 'COPILOT' }));
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
