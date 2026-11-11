import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, storageStatePath } from './support/env';

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
    sessionId = (await session.json()).id;
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
    await page.goto(`/sessions/${sessionId}`);
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
});
