import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { copilotRequiredGuard } from './copilot-required.guard';

describe('copilotRequiredGuard', () => {
  let ctrl: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    ctrl = TestBed.inject(HttpTestingController);
  });

  function run(): Promise<unknown> {
    return TestBed.runInInjectionContext(
      () => copilotRequiredGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot) as Promise<unknown>,
    );
  }

  it('laisse passer quand Copilot est connecté', async () => {
    const result = run();
    ctrl.expectOne('/api/settings').flush({ copilot: { state: 'CONNECTED' }, jira: null });
    expect(await result).toBe(true);
  });

  it('redirige vers la configuration sinon (US1-1)', async () => {
    const result = run();
    ctrl.expectOne('/api/settings').flush({ copilot: { state: 'NOT_CONFIGURED' }, jira: null });
    const tree = (await result) as UrlTree;
    expect(tree.toString()).toBe('/configuration?raison=copilot');
  });
});
