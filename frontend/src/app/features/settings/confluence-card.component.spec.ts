import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ConfluenceSettings } from '../../core/settings/confluence.api';
import { ConfluenceCardComponent } from './confluence-card.component';

const NONE: ConfluenceSettings = {
  enabled: false,
  serverUrl: null,
  caCertificatePath: null,
  insecureHttp: false,
  serverVersion: null,
  lastTest: null,
  tokenSet: false,
  secretPersistence: null,
};

const SAVED: ConfluenceSettings = {
  ...NONE,
  enabled: true,
  serverUrl: 'https://wiki.example.com/confluence',
  tokenSet: true,
  secretPersistence: 'KEYCHAIN',
};

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

function type(el: HTMLElement, name: string, value: string): void {
  const input = el.querySelector<HTMLInputElement>(`input[formcontrolname="${name}"]`)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function button(el: HTMLElement, label: string): HTMLButtonElement | undefined {
  return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label);
}

describe('ConfluenceCardComponent (016)', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<ConfluenceCardComponent>;
  let el: HTMLElement;

  async function open(settings: ConfluenceSettings): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ConfluenceCardComponent);
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/settings/confluence').flush(settings);
    await settle(fixture);
  }

  afterEach(() => ctrl.verify());

  it('enregistre le serveur et le jeton, sans jamais le relire', async () => {
    await open(NONE);
    expect(button(el, 'Tester la connexion')).toBeUndefined();
    type(el, 'serverUrl', 'https://wiki.example.com/confluence/');
    type(el, 'token', 'PAT-123');
    await settle(fixture);
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    const req = ctrl.expectOne({ method: 'PUT', url: '/api/settings/confluence' });
    expect(req.request.body).toEqual({
      serverUrl: 'https://wiki.example.com/confluence/',
      caCertificatePath: null,
      acceptInsecureHttp: false,
      token: 'PAT-123',
    });
    req.flush(SAVED);
    await settle(fixture);
    expect(el.textContent).toContain("Jeton d'accès personnel enregistré dans le trousseau.");
    expect(el.querySelector('input[formcontrolname="token"]')).toBeNull();
  });

  it('avertit avant de continuer en http', async () => {
    await open(NONE);
    type(el, 'serverUrl', 'http://wiki.local');
    await settle(fixture);
    expect(el.querySelector('[role="alert"]')!.textContent).toContain("n'est pas chiffrée (http)");
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    // sans la case cochée, rien n'est envoyé
    ctrl.expectNone({ method: 'PUT', url: '/api/settings/confluence' });
  });

  it('teste la connexion et montre le compte', async () => {
    await open(SAVED);
    button(el, 'Tester la connexion')!.click();
    ctrl
      .expectOne({ method: 'POST', url: '/api/settings/confluence/test' })
      .flush({ result: 'OK', account: 'Ada', serverVersion: '8.5.4', message: 'Connexion à Confluence réussie.' });
    await settle(fixture);
    ctrl.expectOne('/api/settings/confluence').flush(SAVED);
    await settle(fixture);
    expect(el.querySelector('[data-testid="confluence-test"]')!.textContent).toContain('Compte : Ada.');
    expect(el.textContent).toContain('(Confluence 8.5.4)');
  });

  it('retire le serveur après confirmation', async () => {
    await open(SAVED);
    button(el, 'Retirer le serveur')!.click();
    await settle(fixture);
    expect(el.textContent).toContain('Les pages déjà indexées restent');
    button(el, 'Confirmer le retrait')!.click();
    ctrl.expectOne({ method: 'DELETE', url: '/api/settings/confluence' }).flush(null);
    await settle(fixture);
    ctrl.expectOne('/api/settings/confluence').flush(NONE);
    await settle(fixture);
    expect(el.querySelector<HTMLInputElement>('input[formcontrolname="token"]')).not.toBeNull();
  });
});
