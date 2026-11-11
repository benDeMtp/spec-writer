import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, jiraURL, storageStatePath } from './support/env';

const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';
const NEED =
  'Les assistantes doivent pouvoir réserver une salle de réunion pour un comité, et voir les salles libres du jour.';

/**
 * 012 : navigation par projet (quickstart 1 à 15). Deux projets créés pour le test : Aurora E2E,
 * avec la clé Jira FACT, et Recette E2E, sans clé.
 */
test.describe.serial('012 navigation par projet', () => {
  let context: BrowserContext;
  let page: Page;
  let aurora = '';
  let recette = '';
  let sessionId = '';
  const headers = { Origin: baseURL };

  const menu = () => page.getByRole('navigation', { name: 'Navigation principale' });
  const switcher = () => menu().getByRole('combobox');

  async function create(name: string, jiraProjectKey: string | null): Promise<string> {
    const created = await page.request.post('/api/projects', { headers, data: { name, jiraProjectKey } });
    expect(created.status()).toBe(201);
    return (await created.json()).id;
  }

  async function makeCurrent(projectId: string): Promise<void> {
    await page.request.put('/api/projects/current', { headers, data: { projectId } });
  }

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    aurora = await create('Aurora E2E', 'FACT');
    recette = await create('Recette E2E', null);
    await makeCurrent(aurora);
    await ensureCopilotConnected(page.request);
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: true } });
    const session = await page.request.post('/api/sessions', { headers, data: { need: NEED } });
    expect(session.status()).toBe(201);
    let detail = await session.json();
    sessionId = detail.id;
    // une spécification générée (LLM rejoué), pour la bascule et la proposition en attente (US3)
    while (detail.currentBatch.length) {
      const q = detail.currentBatch[0];
      detail = await (
        await page.request.put(`/api/sessions/${sessionId}/questions/${q.id}/answer`, {
          headers,
          data: { value: q.defaultValue ?? q.suggestions[0] ?? 'Oui' },
        })
      ).json();
    }
    expect((await page.request.post(`/api/sessions/${sessionId}/versions`, { headers })).status()).toBe(201);
  });

  test.afterAll(async () => {
    await makeCurrent(DEFAULT_PROJECT);
    for (const id of [aurora, recette]) {
      await page.request.delete(`/api/projects/${id}?sessions=DELETE`, { headers });
    }
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: false } });
    await context.close();
  });

  test('N1 : le menu sépare le projet courant de l’application', async () => {
    await page.goto('/');
    const [project, application] = [menu().getByRole('group').first(), menu().getByRole('group').last()];
    await expect(project).toHaveAccessibleName('Projet Aurora E2E');
    await expect(switcher()).toContainText('Aurora E2E');
    await expect(project.getByRole('link')).toHaveText([
      'Conversation',
      'Spécifications',
      'Tickets Jira, projet FACT',
      'Connaissance du projet',
      'Réglages du projet',
    ]);
    await expect(application).toHaveAccessibleName('Application');
    await expect(application.getByRole('link')).toHaveText(['Projets', 'Configuration', 'Journal']);
    await expectNoA11yViolations(page);
  });

  test('N2 : les pages du projet le nomment, et suivent le changement de projet', async () => {
    await menu().getByRole('link', { name: 'Spécifications' }).click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(/^Spécifications\s*· Aurora E2E$/);
    await expect(page).toHaveTitle('Spécifications · Aurora E2E · spec-writer');

    await switcher().click();
    await page.getByRole('option', { name: 'Recette E2E' }).click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(/^Spécifications\s*· Recette E2E$/);
    await expect(page).toHaveTitle('Spécifications · Recette E2E · spec-writer');
    await expect(menu().getByRole('link', { name: /^Tickets Jira/ })).toHaveText('Tickets Jira');

    await menu().getByRole('link', { name: 'Journal' }).click();
    await expect(page).toHaveTitle('Journal · spec-writer');
    await makeCurrent(aurora);
  });

  test('N3 : l’entrée du menu reste marquée sur ses pages filles', async () => {
    await page.goto('/jira/FACT-1');
    await expect(menu().locator('[aria-current="page"]')).toHaveText(/^Tickets Jira/);
    await page.goto(`/sessions/${sessionId}/versions/1`);
    await expect(menu().locator('[aria-current="page"]')).toHaveText('Spécifications');
  });

  test('N4 : sur petit écran, le menu se replie derrière un bouton', async () => {
    await page.setViewportSize({ width: 600, height: 900 });
    await page.goto('/');
    const toggle = page.getByRole('button', { name: 'Menu' });
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await expect(menu()).toBeHidden();
    await expectNoA11yViolations(page);

    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await expect(menu()).toBeVisible();
    await expectNoA11yViolations(page);

    await menu().getByRole('link', { name: 'Journal' }).click();
    await expect(page).toHaveURL(/\/journal$/);
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await page.setViewportSize({ width: 1280, height: 900 });
  });

  test('N5 : un seul écran règle le projet, et le menu suit sa clé Jira', async () => {
    await menu().getByRole('link', { name: 'Réglages du projet' }).click();
    await expect(page).toHaveURL(/\/projet\/reglages$/);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Réglages du projet · Aurora E2E');
    await expect(page.getByRole('heading', { level: 2 })).toHaveText([
      'Identité',
      'Fiche générale',
      'Sources de documentation',
      'Sources du code',
      "Modèle d'architecture",
    ]);
    await expectNoA11yViolations(page);
    const identity = page.getByRole('form', { name: 'Identité du projet Aurora E2E' });
    await identity.getByLabel('Clé Jira (facultative)').fill('AUR');
    await identity.getByRole('button', { name: 'Enregistrer' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Projet « Aurora E2E » enregistré.' })).toBeVisible();
    await expect(menu().getByRole('link', { name: /^Tickets Jira/ })).toHaveText('Tickets Jira, projet AUR');
  });

  test('N6 : une saisie non enregistrée retient le changement de projet', async () => {
    await page.getByLabel('Adresse du site').fill('https://docs.example.com/aurora/');
    await switcher().click();
    await page.getByRole('option', { name: 'Recette E2E' }).click();
    const leave = page.getByRole('dialog', { name: 'Quitter sans enregistrer ?' });
    await expect(leave).toBeVisible();
    await leave.getByRole('button', { name: 'Annuler' }).click();
    await expect(switcher()).toContainText('Aurora E2E');
    await expect(page.getByLabel('Adresse du site')).toHaveValue('https://docs.example.com/aurora/');
    await page.getByLabel('Adresse du site').fill('');
  });

  test('N7 : la connaissance du projet a sa page, avec les ADR et les réglages', async () => {
    await menu().getByRole('link', { name: 'Connaissance du projet' }).click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Connaissance du projet · Aurora E2E');
    const main = page.getByRole('main');
    await expect(main.getByRole('link', { name: 'ADR du projet' })).toHaveAttribute('href', '/projet/connaissance/adr');
    await expect(main.getByRole('link', { name: 'Réglages du projet' })).toHaveAttribute('href', '/projet/reglages');
    await expect(main.getByRole('form', { name: "Recherche d'essai" })).toBeVisible();
    await expectNoA11yViolations(page);
  });

  test('N8 : l’ancienne adresse du contexte rend son projet courant', async () => {
    await page.goto(`/projets/${recette}/contexte`);
    await expect(page).toHaveURL(/\/projet\/connaissance$/);
    await expect(switcher()).toContainText('Recette E2E');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Connaissance du projet · Recette E2E');
    await expect(page.locator('sw-project-notice').getByText('Projet courant : Recette E2E')).toBeVisible();
  });

  test('N9 : Tickets Jira sans Jira mène à la configuration', async () => {
    await menu().getByRole('link', { name: 'Tickets Jira' }).click();
    await expect(page.getByText("Jira n'est pas configuré.")).toBeVisible();
    await expect(page.getByRole('main').getByRole('link', { name: 'Ouvrir la configuration' })).toHaveAttribute(
      'href',
      '/configuration',
    );
    await makeCurrent(aurora);
  });

  test('N10 : un ticket Jira annonce le projet où sa spécification sera ajoutée', async () => {
    // le ticket se lit dans le projet Jira du projet courant : N5 a changé la clé d'Aurora
    await page.request.put(`/api/projects/${aurora}`, { headers, data: { name: 'Aurora E2E', jiraProjectKey: 'FACT' } });
    await page.request.put('/api/consents/JIRA', { headers, data: { granted: true } });
    const saved = await page.request.put('/api/settings/jira', {
      headers,
      data: { serverUrl: jiraURL, defaultProjectKey: 'PROJ', acceptInsecureHttp: true, token: 'e2e-good-token' },
    });
    expect(saved.status()).toBe(200);
    try {
      await page.goto('/jira/FACT-1');
      await expect(page.getByText('La spécification sera ajoutée au projet Aurora E2E.')).toBeVisible();
      await expectNoA11yViolations(page);
    } finally {
      await page.request.delete('/api/settings/jira', { headers });
      await page.request.put('/api/consents/JIRA', { headers, data: { granted: false } });
    }
  });

  test('N11 : une spécification d’un autre projet rend ce projet courant, avec une annonce', async () => {
    await makeCurrent(recette);
    await page.goto(`/sessions/${sessionId}/versions/1`);
    await expect(page.locator('sw-project-notice').getByText('Projet courant : Aurora E2E')).toBeVisible();
    await expect(switcher()).toContainText('Aurora E2E');
    await expectNoA11yViolations(page);

    await menu().getByRole('link', { name: 'Spécifications' }).click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Spécifications · Aurora E2E');
    await expect(page.locator('sw-project-notice .notice')).toHaveCount(0);

    await page.goto(`/sessions/${sessionId}/versions/1`);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.locator('sw-project-notice .notice')).toHaveCount(0);
  });

  test('N12 : une proposition en attente retient le changement de projet', async () => {
    await page.goto(`/sessions/${sessionId}/versions/1?discussion=1`);
    const panel = page.locator('sw-discussion-panel');
    const composer = panel.getByLabel('Votre question');
    // même déroulé que us14 (D2, D3) : la proposition rejouée cite le message 3 de la discussion
    for (const [text, answered] of [
      ['E2E-DISCUSSION Que dit la spécification de la réservation ?', 1],
      ["E2E-DISCUSSION E2E-ANNULATION Ajoutons l'annulation jusqu'à la veille.", 2],
    ] as const) {
      await composer.fill(text);
      await composer.press('Enter');
      await expect(panel.locator('li.answer')).toHaveCount(answered);
      await expect(panel.locator('li.answer').last().locator('.note')).not.toBeEmpty();
    }
    await panel.getByRole('button', { name: 'Proposer une nouvelle version' }).click();
    await panel.getByRole('button', { name: 'Rédiger la version' }).click();
    const review = page.locator('sw-proposal-review');
    await expect(review).toBeVisible();

    await switcher().click();
    await page.getByRole('option', { name: 'Recette E2E' }).click();
    const leave = page.getByRole('dialog', { name: 'Quitter sans appliquer la proposition ?' });
    await expect(leave).toBeVisible();
    await leave.getByRole('button', { name: 'Annuler' }).click();
    await expect(review).toBeVisible();
    await expect(switcher()).toContainText('Aurora E2E');
    await review.getByRole('button', { name: 'Abandonner' }).click();
  });

  test('N13 : la configuration sépare les connexions des réglages avancés, et le test vise le projet courant', async () => {
    await makeCurrent(aurora);
    await page.request.put('/api/consents/JIRA', { headers, data: { granted: true } });
    // 012 : la clé par défaut est facultative
    const saved = await page.request.put('/api/settings/jira', {
      headers,
      data: { serverUrl: jiraURL, acceptInsecureHttp: true, token: 'e2e-good-token' },
    });
    expect(saved.status()).toBe(200);
    await page.goto('/configuration');
    await expect(page.getByRole('heading', { level: 2 })).toHaveText(['Connexions', 'Avancé']);
    await expect(page.getByText("S'applique à tous les projets. Changer de modèle demande de réindexer chaque projet.")).toBeVisible();
    await expect(page.getByText('Les agents de code accèdent à tous les projets.')).toBeVisible();
    const states = menu().getByRole('list', { name: 'État des connexions' });
    await expect(states.getByRole('listitem')).toHaveText(['Copilot : connecté', 'Jira : configuré']);
    await expect(page.getByLabel('Clé Jira par défaut (facultatif)')).toHaveValue('');

    await page.getByRole('button', { name: 'Tester la connexion' }).click();
    await expect(page.getByText('Projet Jira vérifié : FACT, celui du projet courant.')).toBeVisible();
    await expectNoA11yViolations(page);
  });

  test('N14 : un projet sans clé mène à ses réglages ; la clé par défaut sert de repli', async () => {
    await makeCurrent(recette);
    await page.goto('/jira');
    await expect(page.getByText("Aucun projet Jira n'est associé à Recette E2E.")).toBeVisible();
    await page.getByRole('link', { name: 'Renseigner la clé Jira du projet' }).click();
    await expect(page).toHaveURL(/\/projet\/reglages#jira$/);
    await expect(page.getByLabel('Clé Jira (facultative)')).toBeFocused();

    await page.request.put('/api/settings/jira', {
      headers,
      data: { serverUrl: jiraURL, defaultProjectKey: 'PROJ', acceptInsecureHttp: true },
    });
    await page.goto('/jira');
    await expect(page.locator('.lead')).toContainText("Projet Jira PROJ (clé par défaut de l'application)");
    await expect(menu().getByRole('link', { name: /^Tickets Jira/ })).toHaveText('Tickets Jira, projet PROJ');

    await page.request.delete('/api/settings/jira', { headers });
    await page.request.put('/api/consents/JIRA', { headers, data: { granted: false } });
    await makeCurrent(aurora);
  });
});

test('N15 : une fenêtre sans session affiche seulement l’écran de relance', async ({ browser }) => {
  // sans cookie : la configuration fournit sinon l'état de session des autres tests
  const bare = await browser.newContext({ baseURL, storageState: { cookies: [], origins: [] } });
  const page = await bare.newPage();
  try {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Relancez spec-writer depuis son raccourci');
    await expect(page.getByRole('navigation', { name: 'Navigation principale' })).toHaveCount(0);
    await expect(page.getByText('Aucune conversation')).toHaveCount(0);
    await expectNoA11yViolations(page);
  } finally {
    await bare.close();
  }
});
