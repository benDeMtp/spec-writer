import { expect, request, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, jiraURL, storageStatePath } from './support/env';

const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';
const NEED = 'Il faut pouvoir émettre un avoir sur une facture déjà émise, pour corriger une erreur de montant.';
const EDITED = '## Acteurs\n- Le gestionnaire de facturation émet les avoirs.\n\n## Règles métier\n- Un avoir ne dépasse jamais le restant dû.';

/**
 * US2 de la feature 002 : le contexte de projet (quickstart P5 à P8). Le site Antora et les 120
 * tickets de FACT sont servis par WireMock ; Copilot est rejoué depuis fixtures/llm.
 */
test.describe.serial('US8 contexte de projet', () => {
  let context: BrowserContext;
  let page: Page;
  let jira: APIRequestContext;
  let projectId = '';
  let specId = '';
  const headers = { Origin: baseURL };

  async function project(): Promise<{ context: string; contextOrigin: string }> {
    const all = (await (await page.request.get('/api/projects')).json()) as { id: string; context: string; contextOrigin: string }[];
    return all.find((p) => p.id === projectId)!;
  }

  async function proposeFromUi(): Promise<void> {
    await page.getByRole('button', { name: 'Proposer une fiche' }).click();
    await expect(page.getByText('Seront envoyés à Copilot : 32 pages de documentation et 120 tickets Jira.')).toBeVisible();
    await page.getByRole('button', { name: 'Envoyer à Copilot' }).click();
    await expect(page.getByRole('heading', { name: 'Fiche proposée' })).toBeVisible({ timeout: 60_000 });
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

  test('P5 : aperçu de l’envoi, proposition sourcée, rien d’enregistré avant « Accepter »', async () => {
    await page.goto('/projets');
    await page.getByRole('link', { name: 'Contexte du projet Facturation contexte' }).click();
    await expect(page.getByRole('heading', { name: 'Contexte du projet Facturation contexte', level: 1 })).toBeVisible();
    await expect(page.getByText('Aucun contexte pour ce projet.')).toBeVisible();
    await expectNoA11yViolations(page);

    await page.getByLabel('Adresse du site').fill(`${jiraURL}/antora/`);
    await page.getByRole('button', { name: 'Ajouter' }).click();
    await expect(page.getByRole('status')).toContainText('Source ajoutée.');
    await expect(page.locator('ul.sources > li')).toContainText('jamais lue');

    await proposeFromUi();
    const proposal = page.getByLabel('Fiche proposée');
    await expect(proposal).toContainText('[source : FACT-120]');
    await expect(proposal).toContainText('[source : http://127.0.0.1:47698/antora/billing/regle-tva.html]');
    await expect(page.locator('ul.unread')).toContainText('tarifs-2025.html : erreur du serveur');
    await expect(page.locator('ul.unread')).toContainText('recherche.html : pas une page Antora');
    await expectNoA11yViolations(page);
    expect((await project()).contextOrigin).toBe('NONE');

    await page.getByRole('button', { name: 'Accepter' }).click();
    await expect(page.getByRole('status')).toContainText('Fiche proposée acceptée et enregistrée.');
    await expect(page.getByText("Proposée par l'IA et acceptée le")).toBeVisible();
    await expect(page.locator('ul.sources > li')).toContainText('30 pages lues, 1 en erreur');
    expect((await project()).contextOrigin).toBe('GENERATED');
  });

  test('P6 : fiche modifiée à la main, puis proposition comparée et « Garder la mienne »', async () => {
    await page.getByLabel('Fiche de contexte (Markdown)').fill(EDITED);
    await page.getByRole('button', { name: 'Enregistrer la fiche' }).click();
    await expect(page.getByRole('status')).toContainText('Fiche de contexte enregistrée.');
    await expect(page.getByText('Modifiée à la main le')).toBeVisible();

    await page.getByRole('button', { name: 'Aperçu' }).click();
    await expect(page.getByLabel('Aperçu de la fiche')).toContainText('Le gestionnaire de facturation émet les avoirs.');
    await page.getByRole('button', { name: 'Aperçu' }).click();

    await proposeFromUi();
    const diff = page.getByRole('region', { name: 'Différences avec la fiche actuelle' });
    await expect(diff).toContainText('Un avoir ne dépasse jamais le restant dû.');
    await expectNoA11yViolations(page);
    await page.getByRole('button', { name: 'Garder la mienne' }).click();
    await expect(page.getByRole('status')).toContainText('Fiche actuelle conservée.');
    await expect(page.getByLabel('Fiche de contexte (Markdown)')).toHaveValue(EDITED);
    const p = await project();
    expect(p.context).toBe(EDITED);
    expect(p.contextOrigin).toBe('EDITED');
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
    await expect(page.getByText('Contexte du projet : « Le gestionnaire de facturation émet les avoirs. »')).toBeVisible();
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
