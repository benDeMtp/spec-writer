import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { Settings } from '../../core/settings/settings.model';
import { COPILOT_POLL_INTERVAL } from '../../core/settings/settings.store';
import { SettingsPage } from './settings.page';

const disconnected: Settings = {
  copilot: {
    state: 'NOT_CONFIGURED',
    login: null,
    secretPersistence: 'KEYCHAIN',
    consent: false,
    model: null,
    availableModels: [],
    models: [],
    knowledgeChars: 35000,
  },
  jira: null,
};

const connected: Settings = {
  copilot: {
    state: 'CONNECTED',
    login: 'ada',
    secretPersistence: 'KEYCHAIN',
    consent: true,
    model: 'claude-sonnet-5.5',
    availableModels: ['claude-sonnet-5.5', 'gpt-6-luna'],
    models: [
      { id: 'claude-sonnet-5.5', name: 'Claude Sonnet 5.5', promptTokens: 128000, contextTokens: 200000, multiplier: 1, knowledgeChars: 162000 },
      { id: 'gpt-6-luna', name: 'GPT 6 Luna', promptTokens: null, contextTokens: null, multiplier: null, knowledgeChars: 35000 },
    ],
    knowledgeChars: 162000,
  },
  jira: {
    enabled: true,
    serverUrl: 'https://jira.example.fr',
    defaultProjectKey: 'PROJ',
    caCertificatePath: null,
    tokenSet: true,
    secretPersistence: 'KEYCHAIN',
    insecureHttp: false,
    lastTest: null,
    consent: true,
  },
};

/** Laisse s'achever les promesses du store (hors du suivi d'Angular), puis rafraîchit la vue. */
async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

function button(el: HTMLElement, label: string): HTMLElement | undefined {
  return Array.from(el.querySelectorAll<HTMLElement>('button, a')).find((b) => b.textContent?.trim() === label);
}

describe('SettingsPage', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<SettingsPage>;
  let el: HTMLElement;

  async function open(settings: Settings): Promise<void> {
    fixture = TestBed.createComponent(SettingsPage);
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/settings').flush(settings);
    await settle(fixture);
    // la carte du modèle de recherche lit son état
    ctrl.match('/api/settings/embedding').forEach((r) =>
      r.flush({ state: 'READY', modelId: 'multilingual-e5-small', problem: null, defaultModel: true, folder: null, pooling: 'MEAN', queryPrefix: null, passagePrefix: null }),
    );
    // la carte du navigateur liste les navigateurs installés ; elle est testée à part
    ctrl.match('/api/settings/browser').forEach((r) => r.flush({ selected: null, missing: false, installed: [] }));
    // la carte des agents charge son état ; elle est testée à part
    ctrl.match('/api/settings/agent-access').forEach((r) =>
      r.flush({ enabled: false, command: 'spec-writer-mcp', mcpJson: '{}' }),
    );
    await settle(fixture);
    ctrl.match((r) => r.url === '/api/settings/agent-access/log').forEach((r) =>
      r.flush({ startAt: 0, total: 0, entries: [] }),
    );
    await settle(fixture);
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: COPILOT_POLL_INTERVAL, useValue: 1000 },
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
  });

  afterEach(() => vi.useRealTimers());

  it("affiche les permissions avant d'ouvrir GitHub, puis relit l'état jusqu'à CONNECTED", async () => {
    await open(disconnected);
    button(el, 'Se connecter à GitHub')!.click();
    ctrl.expectOne({ method: 'POST', url: '/api/settings/copilot/device-flow' }).flush({
      flowId: 'f1',
      authorizationUrl: 'https://github.com/login/oauth/authorize?client_id=x',
      requestedScopes: ['read:user', 'repo'],
      expiresAt: '2026-10-30T10:00:00Z',
    });
    await settle(fixture);

    const scopes = Array.from(el.querySelectorAll('.scopes li')).map((li) => li.textContent);
    expect(scopes[0]).toContain('read:user');
    expect(scopes[0]).toContain('lire votre profil GitHub');
    const link = button(el, 'Ouvrir GitHub') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toContain('https://github.com/login/oauth/authorize');
    expect(link.target).toBe('_blank');
    expect(link.rel).toContain('noopener');
    // Les permissions précèdent le bouton dans l'ordre de lecture
    expect(el.querySelector('.scopes')!.compareDocumentPosition(link) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

    vi.useFakeTimers();
    link.addEventListener('click', (e) => e.preventDefault());
    link.click();
    vi.advanceTimersByTime(0);
    ctrl.expectOne('/api/settings/copilot/device-flow/f1').flush({ state: 'PENDING', login: null, secretPersistence: 'KEYCHAIN' });
    fixture.detectChanges();
    expect(el.textContent).toContain("En attente de votre autorisation");

    vi.advanceTimersByTime(1000);
    ctrl.expectOne('/api/settings/copilot/device-flow/f1').flush({ state: 'CONNECTED', login: 'ada', secretPersistence: 'KEYCHAIN' });
    ctrl.expectOne('/api/settings').flush(connected);
    vi.useRealTimers();
    await settle(fixture);
    expect(el.textContent).toContain('Connecté en tant que ada');
    ctrl.verify();
  });

  it('signale un compte sans Copilot', async () => {
    await open({ ...disconnected, copilot: { ...disconnected.copilot, state: 'NO_COPILOT', login: 'bob' } });
    expect(el.querySelector('[role="alert"]')?.textContent).toContain("n'a pas d'abonnement Copilot actif");
    expect(button(el, 'Recommencer')).toBeTruthy();
  });

  const flat = (text: string | null | undefined) => (text ?? '').replace(/\s+/g, ' ').trim();

  it('affiche la liste des modèles autorisés, avec leur capacité et leur coût (008)', async () => {
    await open(connected);
    expect(flat(el.querySelector('mat-select')?.textContent)).toBe('Claude Sonnet 5.5');
    expect(flat(el.querySelector('.knowledge')?.textContent)).toBe('Connaissance transmise : jusqu’à 162 000 caractères');
    (el.querySelector('mat-select .mat-mdc-select-trigger') as HTMLElement).click();
    await settle(fixture);
    const options = Array.from(document.querySelectorAll('mat-option'));
    // le texte entier de l'option est lu au lecteur d'écran
    expect(options.map((o) => flat(o.textContent))).toEqual([
      'Claude Sonnet 5.5 environ 128 000 tokens (≈ 384 000 caractères) · ×1',
      'GPT 6 Luna capacité inconnue · coût inconnu',
    ]);
    expect(options.map((o) => o.getAttribute('aria-label'))).toEqual([null, null]);
  });

  it('coefficient décimal et modèle absent des capacités', async () => {
    await open({
      ...connected,
      copilot: {
        ...connected.copilot,
        model: 'gpt-6-luna',
        knowledgeChars: 35000,
        models: [{ id: 'gpt-6-luna', name: 'gpt-6-luna', promptTokens: 32000, contextTokens: null, multiplier: 0.33, knowledgeChars: 35000 }],
      },
    });
    expect(flat(el.querySelector('.knowledge')?.textContent)).toBe('Connaissance transmise : jusqu’à 35 000 caractères');
    (el.querySelector('mat-select .mat-mdc-select-trigger') as HTMLElement).click();
    await settle(fixture);
    expect(Array.from(document.querySelectorAll('mat-option')).map((o) => flat(o.textContent))).toEqual([
      'gpt-6-luna environ 32 000 tokens (≈ 96 000 caractères) · ×0,33',
    ]);
  });

  it('jeton masqué, vide après enregistrement, avec Remplacer et Supprimer mais sans Afficher', async () => {
    await open({ ...connected, jira: null });
    const token = el.querySelector<HTMLInputElement>('input[formcontrolname="token"]')!;
    expect(token.type).toBe('password');

    const fill = (name: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(`input[formcontrolname="${name}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    fill('serverUrl', 'https://jira.example.fr');
    fill('defaultProjectKey', 'PROJ');
    fill('token', 'PAT-SECRET');
    button(el, 'Enregistrer')!.click();
    const put = ctrl.expectOne({ method: 'PUT', url: '/api/settings/jira' });
    expect(put.request.body.token).toBe('PAT-SECRET');
    put.flush(connected.jira);
    await settle(fixture);
    ctrl.expectOne('/api/settings').flush(connected);
    await settle(fixture);

    expect(el.querySelector('input[formcontrolname="token"]')).toBeNull();
    expect(el.textContent).not.toContain('PAT-SECRET');
    expect(button(el, 'Remplacer')).toBeTruthy();
    expect(button(el, 'Supprimer')).toBeTruthy();
    expect(button(el, 'Afficher')).toBeUndefined();

    button(el, 'Remplacer')!.click();
    fixture.detectChanges();
    expect(el.querySelector<HTMLInputElement>('input[formcontrolname="token"]')!.value).toBe('');
  });

  it("demande le consentement Jira au premier enregistrement s'il manque", async () => {
    await open({ ...connected, jira: null });
    const input = (name: string, value: string) => {
      const i = el.querySelector<HTMLInputElement>(`input[formcontrolname="${name}"]`)!;
      i.value = value;
      i.dispatchEvent(new Event('input'));
    };
    input('serverUrl', 'https://jira.example.fr');
    input('defaultProjectKey', 'PROJ');
    button(el, 'Enregistrer')!.click();
    ctrl.expectOne({ method: 'PUT', url: '/api/settings/jira' }).flush(connected.jira);
    await settle(fixture);
    ctrl.expectOne('/api/settings').flush({ ...connected, jira: { ...connected.jira!, consent: false } });
    await settle(fixture);

    const dialog = document.querySelector('sw-consent-dialog')!;
    expect(dialog.textContent).toContain('Envoyer des données à Jira ?');
    (Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Accepter') as HTMLElement).click();
    await settle(fixture);
    const consent = ctrl.expectOne({ method: 'PUT', url: '/api/consents/JIRA' });
    expect(consent.request.body).toEqual({ granted: true });
  });

  it("affiche la cause et l'action du test de connexion", async () => {
    await open(connected);
    button(el, 'Tester la connexion')!.click();
    ctrl.expectOne({ method: 'POST', url: '/api/settings/jira/test' }).flush({
      result: 'TOKEN_REJECTED',
      serverVersion: '9.12.3',
      userMessage: "Le jeton a été refusé. Créez un nouveau jeton d'accès personnel dans Jira, puis remplacez-le ici.",
    });
    await settle(fixture);
    ctrl.expectOne('/api/settings').flush(connected);
    await settle(fixture);
    const status = el.querySelector('.status.warn[role="status"]')!;
    expect(status.textContent).toContain('Le jeton a été refusé.');
    expect(status.textContent).toContain('Créez un nouveau jeton');
    expect(status.textContent).toContain('Jira 9.12.3');
  });

  it('présente les connexions, puis les réglages avancés avec leur portée (012)', async () => {
    await open(connected);
    const sections = Array.from(el.querySelectorAll('section[aria-labelledby]')).filter((n) => n.querySelector(':scope > h2'));
    expect(sections.map((n) => n.querySelector(':scope > h2')?.textContent)).toEqual(['Connexions', 'Avancé']);
    expect(Array.from(sections[0].querySelectorAll('h3')).map((h) => h.textContent)).toEqual(['GitHub Copilot', 'Jira']);
    expect(Array.from(sections[1].querySelectorAll('h3')).map((h) => h.textContent)).toEqual([
      'Modèle de recherche',
      'Accès des agents',
      'Navigateur',
    ]);
    expect(sections[1].textContent).toContain(
      "S'applique à tous les projets. Changer de modèle demande de réindexer chaque projet.",
    );
    expect(sections[1].textContent).toContain('Les agents de code accèdent à tous les projets.');
  });

  it('enregistre Jira sans clé par défaut, puis nomme le projet vérifié par le test (012)', async () => {
    await open({ ...connected, jira: null });
    expect(el.textContent).toContain('Clé Jira par défaut (facultatif)');
    const input = (name: string, value: string) => {
      const i = el.querySelector<HTMLInputElement>(`input[formcontrolname="${name}"]`)!;
      i.value = value;
      i.dispatchEvent(new Event('input'));
    };
    input('serverUrl', 'https://jira.example.fr');
    input('token', 'PAT');
    button(el, 'Enregistrer')!.click();
    const put = ctrl.expectOne({ method: 'PUT', url: '/api/settings/jira' });
    expect(put.request.body.defaultProjectKey).toBeNull();
    put.flush({ ...connected.jira!, defaultProjectKey: null });
    await settle(fixture);
    ctrl.expectOne('/api/settings').flush({ ...connected, jira: { ...connected.jira!, defaultProjectKey: null } });
    await settle(fixture);

    button(el, 'Tester la connexion')!.click();
    ctrl.expectOne({ method: 'POST', url: '/api/settings/jira/test' }).flush({
      result: 'OK',
      serverVersion: '9.12.3',
      userMessage: 'Connexion à Jira réussie.',
      projectKey: 'FACT',
    });
    await settle(fixture);
    ctrl.expectOne('/api/settings').flush(connected);
    await settle(fixture);
    expect(el.querySelector('.status.ok[role="status"]')?.textContent).toContain('Projet Jira vérifié : FACT');
  });

  it("exige de confirmer l'avertissement http avant d'enregistrer", async () => {
    await open({ ...connected, jira: null });
    const input = (name: string, value: string) => {
      const i = el.querySelector<HTMLInputElement>(`input[formcontrolname="${name}"]`)!;
      i.value = value;
      i.dispatchEvent(new Event('input'));
    };
    input('serverUrl', 'http://jira.example.fr');
    input('defaultProjectKey', 'PROJ');
    fixture.detectChanges();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain("n'est pas chiffrée");

    button(el, 'Enregistrer')!.click();
    ctrl.expectNone({ method: 'PUT', url: '/api/settings/jira' });

    (el.querySelector('mat-checkbox input') as HTMLInputElement).click();
    button(el, 'Enregistrer')!.click();
    const put = ctrl.expectOne({ method: 'PUT', url: '/api/settings/jira' });
    expect(put.request.body.acceptInsecureHttp).toBe(true);
  });
});
