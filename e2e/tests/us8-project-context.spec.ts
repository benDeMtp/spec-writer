import { expect, request, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, jiraURL, storageStatePath } from './support/env';

const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';
const NEED = 'Il faut pouvoir émettre un avoir sur une facture déjà émise, pour corriger une erreur de montant.';
const EDITED = '## Acteurs\n- Le gestionnaire de facturation émet les avoirs.\n\n## Règles métier\n- Un avoir ne dépasse jamais le restant dû.';

/**
 * US2 de la feature 002 : la fiche générale du projet (quickstart P6 à P8), rédigée à la main.
 * Copilot est rejoué depuis fixtures/llm. Réécrit en T117 avec la connaissance indexée.
 */
test.describe.serial('US8 contexte de projet', () => {
  let context: BrowserContext;
  let page: Page;
  let jira: APIRequestContext;
  let projectId = '';
  let specId = '';
  const headers = { Origin: baseURL };

  async function project(): Promise<{ context: string; contextUpdatedAt: string | null }> {
    const all = (await (await page.request.get('/api/projects')).json()) as {
      id: string;
      context: string;
      contextUpdatedAt: string | null;
    }[];
    return all.find((p) => p.id === projectId)!;
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
    const created = await page.request.post('/api/projects', { headers, data: { name: 'Facturation contexte', jiraProjectKey: 'FACT' } });
    expect(created.status()).toBe(201);
    projectId = (await created.json()).id;
    await page.request.put('/api/projects/current', { headers, data: { projectId } });
  });

  test.afterAll(async () => {
    await page.request.put('/api/projects/current', { headers, data: { projectId: DEFAULT_PROJECT } });
    await page.request.delete(`/api/projects/${projectId}?sessions=DELETE`, { headers });
    await jira.dispose();
    await context.close();
  });

  test('P6 : fiche générale rédigée à la main, avec un aperçu', async () => {
    await page.goto(`/projets/${projectId}/contexte`);
    await expect(page.getByRole('button', { name: 'Proposer une fiche' })).toHaveCount(0);
    await page.getByLabel('Fiche générale (Markdown)').fill(EDITED);
    await page.getByRole('button', { name: 'Enregistrer la fiche' }).click();
    await expect(page.getByRole('status')).toContainText('Fiche générale enregistrée.');
    await expect(page.getByText('Modifiée le')).toBeVisible();

    await page.getByRole('button', { name: 'Aperçu' }).click();
    await expect(page.getByLabel('Aperçu de la fiche générale')).toContainText('Le gestionnaire de facturation émet les avoirs.');
    await expectNoA11yViolations(page);
    await page.getByRole('button', { name: 'Aperçu' }).click();
    const p = await project();
    expect(p.context).toBe(EDITED);
    expect(p.contextUpdatedAt).not.toBeNull();
  });

  test('P7 : aucune question sur les acteurs, use case sourcé par le contexte du projet', async () => {
    let session = await (await page.request.post('/api/sessions', { headers, data: { need: NEED } })).json();
    specId = session.id as string;
    const themes: string[] = [];
    while (session.currentBatch.length) {
      const q = session.currentBatch[0];
      themes.push(q.theme);
      session = await (
        await page.request.put(`/api/sessions/${specId}/questions/${q.id}/answer`, { headers, data: { value: q.defaultValue } })
      ).json();
    }
    expect(themes.length).toBeGreaterThan(0);
    expect(themes).not.toContain('ACTORS');
    expect((await page.request.post(`/api/sessions/${specId}/versions`, { headers })).status()).toBe(201);

    await page.goto(`/sessions/${specId}/versions/1`);
    await expect(page.getByText('Contexte du projet : « Le gestionnaire de facturation émet les avoirs. »'))
      .toBeVisible();
    await expect(page.locator('.meta').filter({ hasText: 'Version 1' })).toContainText('contexte du');
    await expectNoA11yViolations(page);
  });

  test('P8 : nouvelle version après une modification du contexte, réutilisation sinon', async () => {
    const edited = await page.request.put(`/api/projects/${projectId}/context`, {
      headers,
      data: { markdown: `${EDITED}\n- Un avoir porte la date du jour.` },
    });
    expect(edited.status()).toBe(200);
    const changed = await page.request.post(`/api/sessions/${specId}/versions`, { headers });
    expect(changed.status()).toBe(201);
    expect((await changed.json()).number).toBe(2);
    const same = await page.request.post(`/api/sessions/${specId}/versions`, { headers });
    expect(same.status()).toBe(200);
    expect((await same.json()).number).toBe(2);
  });
});
