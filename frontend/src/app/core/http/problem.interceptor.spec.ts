import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { ProblemBannerComponent } from '../../shared/ui/problem-banner.component';
import { problemInterceptor } from './problem.interceptor';
import { ProblemStore } from './problem.store';

describe('problemInterceptor et bandeau', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let store: ProblemStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([problemInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
    store = TestBed.inject(ProblemStore);
  });

  it("affiche le message métier et l'action du Problem", async () => {
    http.get('/api/x').subscribe({ error: () => undefined });
    ctrl.expectOne('/api/x').flush(
      { code: 'COPILOT_NOT_CONNECTED', userMessage: "GitHub Copilot n'est pas connecté.", action: 'OPEN_SETTINGS' },
      { status: 503, statusText: 'x' },
    );
    expect(store.current()?.code).toBe('COPILOT_NOT_CONNECTED');

    const fixture = TestBed.createComponent(ProblemBannerComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const alert = el.querySelector('[role="alert"]')!;
    expect(alert.getAttribute('aria-live')).toBe('assertive');
    expect(alert.textContent).toContain("GitHub Copilot n'est pas connecté.");
    const button = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Ouvrir la configuration'));
    expect(button).toBeTruthy();

    const router = TestBed.inject(Router);
    const nav = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    button!.click();
    expect(nav).toHaveBeenCalledWith('/configuration');
    expect(store.current()).toBeNull();
  });

  it('libellés des actions', () => {
    const fixture = TestBed.createComponent(ProblemBannerComponent);
    const labels: Record<string, string> = {
      RETRY: 'Réessayer',
      RECONNECT: 'Se reconnecter',
      EDIT_INPUT: 'Corriger la saisie',
    };
    for (const [action, label] of Object.entries(labels)) {
      store.show({ code: 'X', userMessage: 'm', action: action as never });
      fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).textContent).toContain(label);
    }
  });

  it('signale une coupure réseau', () => {
    http.get('/api/y').subscribe({ error: () => undefined });
    ctrl.expectOne('/api/y').error(new ProgressEvent('error'), { status: 0 });
    expect(store.current()?.action).toBe('RETRY');
  });

  it('respecte les requêtes silencieuses', () => {
    http.get('/api/z', { headers: { 'X-Sw-Silent': '1' } }).subscribe({ error: () => undefined });
    const req = ctrl.expectOne('/api/z');
    expect(req.request.headers.has('X-Sw-Silent')).toBe(false);
    req.flush({ code: 'X', userMessage: 'm', action: 'NONE' }, { status: 500, statusText: 'x' });
    expect(store.current()).toBeNull();
  });
});
