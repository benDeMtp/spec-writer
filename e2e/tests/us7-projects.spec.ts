import { expect, request, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, jiraURL, storageStatePath } from './support/env';

const NEED =
  'Les employés doivent pouvoir réserver une place de parking pour une journée, et être prévenus quand le parking ' +
  'est complet.';
const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';

/**
 * US1 de la feature 002 : ranger ses spécifications par projet (quickstart P1 à P4). La conservation
 * du projet courant après un redémarrage est vérifiée par ProjectPersistenceIT.
 */
test.describe.serial('US7 projets', () => {
  let context: BrowserContext;
  let page: Page;
  let jira: APIRequestContext;
  let specId = '';
  const headers = { Origin: baseURL };

  async function stub(mapping: object): Promise<void> {
    expect((await jira.post('/__admin/mappings', { data: { priority: 1, ...mapping } })).status()).toBe(201);
  }

  async function dialogOpen(): Promise<void> {
    await expect(page.locator('.mat-mdc-dialog-container').last()).toHaveClass(/mdc-dialog--open/);
    await expect(page.locator('.mat-mdc-dialog-container').last()).not.toHaveClass(/mdc-dialog--opening/);
  }

  /** Une spécification générée dans le projet courant, par le rejeu du LLM. */
  async function generatedSpec(): Promise<string> {
    let session = await (await page.request.post('/api/sessions', { headers, data: { need: NEED } })).json();
    const id = session.id as string;
    let first = true;
    while (session.currentBatch.length) {
      const q = session.currentBatch[0];
      const data = first ? { skip: true } : { value: q.defaultValue ?? q.suggestions[0] ?? 'Oui' };
      first = false;
      session = await (await page.request.put(`/api/sessions/${id}/questions/${q.id}/answer`, { headers, data })).json();
    }
    expect((await page.request.post(`/api/sessions/${id}/versions`, { headers })).status()).toBe(201);
    return id;
  }

  async function chooseCurrent(name: string): Promise<void> {
    await page.getByRole('combobox', { name: 'Projet courant' }).click();
    await page.getByRole('option', { name }).click();
    await expect(page.getByRole('combobox', { name: 'Projet courant' })).toContainText(name);
  }

  async function previewProject(): Promise<string> {
    await page.goto(`/sessions/${specId}/versions/1`);
    await page.getByRole('button', { name: 'Créer un ticket Jira' }).click();
    const dialog = page.getByRole('dialog', { name: /Créer un ticket Jira/ });
    const key = (await dialog.locator('dt:text-is("Projet") + dd').textContent())?.trim() ?? '';
    await dialog.getByRole('button', { name: 'Annuler' }).click();
    await expect(dialog).toHaveCount(0);
    return key;
  }

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    jira = await request.newContext({ baseURL: jiraURL });
    await jira.post('/__admin/mappings/reset');
    await ensureCopilotConnected(page.request);
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: true } });
    await page.request.put('/api/consents/JIRA', { headers, data: { granted: true } });
    const saved = await page.request.put('/api/settings/jira', {
      headers,
      data: { serverUrl: jiraURL, defaultProjectKey: 'PROJ', acceptInsecureHttp: true, token: 'e2e-good-token' },
    });
    expect(saved.status()).toBe(200);
    for (const key of ['PROJ', 'FACT']) {
      await stub({
        request: { method: 'GET', urlPath: `/rest/api/2/issue/createmeta/${key}/issuetypes` },
        response: {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
          jsonBody: { total: 1, values: [{ id: '10001', name: 'Story', subtask: false }] },
        },
      });
      await stub({
        request: { method: 'GET', urlPath: `/rest/api/2/issue/createmeta/${key}/issuetypes/10001` },
        response: { status: 200, headers: { 'Content-Type': 'application/json' }, jsonBody: { total: 0, values: [] } },
      });
    }
  });

  test.afterAll(async () => {
    await page.request.put('/api/projects/current', { headers, data: { projectId: DEFAULT_PROJECT } });
    const projects = (await (await page.request.get('/api/projects')).json()) as { id: string }[];
    for (const p of projects.filter((p) => p.id !== DEFAULT_PROJECT)) {
      await page.request.delete(`/api/projects/${p.id}?sessions=MOVE&targetProjectId=${DEFAULT_PROJECT}`, { headers });
    }
    await jira.post('/__admin/mappings/reset');
    await jira.dispose();
    await context.close();
  });

  test('P1 : les sessions existantes sont dans le projet par défaut, qui est courant', async () => {
    const before = (await (await page.request.get('/api/sessions')).json()) as unknown[];
    await page.goto('/projets');
    await expect(page.getByRole('heading', { name: 'Projets', level: 1 })).toBeVisible();
    const item = page.locator('ul.projects > li').filter({ hasText: 'Projet par défaut' });
    await expect(item).toContainText('Projet courant');
    await expect(item).toContainText(`${before.length} spécification`);
    await expect(item).toContainText('Jira : PROJ (projet par défaut)');
    await expect(page.getByRole('combobox', { name: 'Projet courant' })).toContainText('Projet par défaut');
    await expectNoA11yViolations(page);
  });

  test('P2 : une spécification du projet « Facturation » vise FACT, celle du projet par défaut PROJ', async () => {
    await page.getByRole('button', { name: 'Nouveau projet' }).click();
    await page.getByLabel('Nom du projet').fill('Facturation');
    await page.getByLabel('Clé du projet Jira (facultative)').fill('FACT');
    await expectNoA11yViolations(page);
    await page.getByRole('button', { name: 'Enregistrer' }).click();
    await expect(page.getByRole('status')).toContainText('Projet « Facturation » créé.');
    await expect(page.getByRole('alert')).toHaveCount(0);
    await expect(page.locator('ul.projects > li').filter({ hasText: 'Facturation' })).toContainText('Jira : FACT');

    await chooseCurrent('Facturation');
    specId = await generatedSpec();
    expect(await previewProject()).toBe('FACT');

    // « Déplacer vers… » : la spécification rejoint le projet par défaut et vise PROJ
    await page.getByRole('button', { name: 'Déplacer vers…' }).click();
    await dialogOpen();
    await expectNoA11yViolations(page);
    await page.getByRole('dialog').getByRole('button', { name: 'Déplacer' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'déplacée' })).toContainText('Projet par défaut');
    expect(await previewProject()).toBe('PROJ');
  });

  test('P3 : la liste et la recherche ne montrent que le projet courant', async () => {
    await page.goto(`/sessions/${specId}/versions/1`);
    await page.getByRole('button', { name: 'Déplacer vers…' }).click();
    await page.getByRole('dialog').getByRole('combobox', { name: 'Projet de destination' }).click();
    await page.getByRole('option', { name: 'Facturation' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Déplacer' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'déplacée' })).toContainText('Facturation');

    async function found(): Promise<string[]> {
      const r = await (await page.request.get('/api/search?q=parking')).json();
      return (r.items as { id: string }[]).map((i) => i.id);
    }

    await page.goto('/sessions');
    await expect(page.getByRole('combobox', { name: 'Projet courant' })).toContainText('Facturation');
    await expect(page.locator('ul.sessions > li')).toHaveCount(1);
    expect(await found()).toContain(specId);
    await expectNoA11yViolations(page);

    await chooseCurrent('Projet par défaut');
    await expect(page.locator('ul.sessions > li').first()).toBeVisible();
    const sessions = (await (await page.request.get('/api/sessions')).json()) as { id: string }[];
    expect(sessions.map((s) => s.id)).not.toContain(specId);
    expect(await found()).not.toContain(specId);
  });

  test('P4 : supprimer un projet en déplaçant ses spécifications, le dernier projet reste', async () => {
    await page.goto('/projets');
    const item = page.locator('ul.projects > li').filter({ hasText: 'Facturation' });
    await expect(item).toContainText('1 spécification');
    await item.getByRole('button', { name: 'Supprimer le projet Facturation' }).click();
    const dialog = page.getByRole('dialog', { name: 'Supprimer le projet « Facturation » ?' });
    await dialogOpen();
    await expect(dialog).toContainText("Jira n'est pas modifié.");
    await expect(dialog.getByRole('radio', { name: 'Les déplacer vers un autre projet' })).toBeChecked();
    await expectNoA11yViolations(page);
    await dialog.getByRole('button', { name: 'Supprimer le projet' }).click();
    await expect(page.getByRole('status')).toContainText('Projet « Facturation » supprimé.');

    const sessions = (await (await page.request.get('/api/sessions')).json()) as { id: string }[];
    expect(sessions.map((s) => s.id)).toContain(specId);
    await expect(page.getByRole('button', { name: 'Supprimer le projet Projet par défaut' })).toBeDisabled();
    await expect(page.getByText('Le dernier projet restant ne peut pas être supprimé.')).toBeVisible();
    const refused = await page.request.delete(`/api/projects/${DEFAULT_PROJECT}?sessions=DELETE`, { headers });
    expect(refused.status()).toBe(409);
    expect((await refused.json()).code).toBe('LAST_PROJECT');
  });
});
