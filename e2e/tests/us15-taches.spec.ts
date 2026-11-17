import { expect, request, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, jiraURL, storageStatePath } from './support/env';

const NEED =
  'Les employés doivent pouvoir réserver une place de parking pour une journée, et être prévenus quand le parking ' +
  'est complet.';
const META = '/rest/api/2/issue/createmeta/PROJ/issuetypes';
const STORY = 'PROJ-300';
const json = { 'Content-Type': 'application/json' };

/** 015 : tâches d'une spécification, créées en sous-tâches Jira et suivies par les agents. */
test.describe.serial('US15 tâches et avancement', () => {
  let context: BrowserContext;
  let page: Page;
  let jira: APIRequestContext;
  let sessionId = '';
  let versionPath = '';
  const headers = { Origin: baseURL };

  async function stub(mapping: object): Promise<void> {
    expect((await jira.post('/__admin/mappings', { data: { priority: 5, ...mapping } })).status()).toBe(201);
  }

  async function sent(method: string, urlPath: string): Promise<{ body: string; url: string }[]> {
    const r = await jira.post('/__admin/requests/find', { data: { method, urlPath } });
    return (await r.json()).requests;
  }

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    jira = await request.newContext({ baseURL: jiraURL });
    await jira.post('/__admin/mappings/reset');
    await jira.delete('/__admin/requests');
    await ensureCopilotConnected(page.request);
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: true } });
    await page.request.put('/api/consents/JIRA', { headers, data: { granted: true } });
    const saved = await page.request.put('/api/settings/jira', {
      headers,
      data: { serverUrl: jiraURL, defaultProjectKey: 'PROJ', acceptInsecureHttp: true, token: 'e2e-good-token' },
    });
    expect(saved.status()).toBe(200);

    let session = await (await page.request.post('/api/sessions', { headers, data: { need: NEED } })).json();
    sessionId = session.id as string;
    let first = true;
    while (session.currentBatch.length) {
      const q = session.currentBatch[0];
      const data = first ? { skip: true } : { value: q.defaultValue ?? q.suggestions[0] ?? 'Oui' };
      first = false;
      session = await (await page.request.put(`/api/sessions/${sessionId}/questions/${q.id}/answer`, { headers, data })).json();
    }
    expect((await page.request.post(`/api/sessions/${sessionId}/versions`, { headers })).status()).toBe(201);
    versionPath = `/sessions/${sessionId}/versions/1`;

    await stub({
      request: { method: 'GET', urlPath: META },
      response: {
        status: 200,
        headers: json,
        jsonBody: {
          total: 2,
          values: [
            { id: '10001', name: 'Story', subtask: false },
            { id: '10003', name: 'Sous-tâche', subtask: true },
          ],
        },
      },
    });
    await stub({
      request: { method: 'GET', urlPath: `${META}/10001` },
      response: { status: 200, headers: json, jsonBody: { total: 0, values: [] } },
    });
    // la story, lue à la création du ticket comme à la proposition des tâches
    await stub({
      request: { method: 'GET', urlPath: `/rest/api/2/issue/${STORY}` },
      response: {
        status: 200,
        headers: json,
        jsonBody: {
          key: STORY,
          fields: {
            summary: 'Réservation des places de parking',
            description: 'h1. x',
            updated: '2026-11-17T09:00:00.000+0000',
            status: { name: 'Ouvert', statusCategory: { key: 'new' } },
            issuetype: { name: 'Story', subtask: false },
            subtasks: [],
          },
        },
      },
    });
    await stub({
      request: { method: 'POST', url: '/rest/api/2/issue' },
      response: { status: 201, headers: json, jsonBody: { id: '20300', key: STORY } },
    });
    // sous-tâches : une clé nouvelle à chaque création, dans l'ordre
    for (let i = 1; i <= 4; i++) {
      await stub({
        priority: 1,
        scenarioName: 'sous-taches',
        requiredScenarioState: i === 1 ? 'Started' : `cree-${i - 1}`,
        newScenarioState: `cree-${i}`,
        request: {
          method: 'POST',
          url: '/rest/api/2/issue',
          bodyPatterns: [{ matchesJsonPath: `$.fields[?(@.parent.key == '${STORY}')]` }],
        },
        response: { status: 201, headers: json, jsonBody: { id: `${20300 + i}`, key: `PROJ-${300 + i}` } },
      });
    }
  });

  test.afterAll(async () => {
    // les autres parcours cherchent la session « parking » par son besoin : elle ne doit pas rester
    if (sessionId) {
      await page.request.delete(`/api/sessions/${sessionId}`, { headers });
    }
    await page.request.delete('/api/settings/jira', { headers });
    await jira.post('/__admin/mappings/reset');
    await jira.dispose();
    await context.close();
  });

  test('US1-5 : sans ticket lié, la proposition est indisponible et l’écran dit pourquoi', async () => {
    await page.goto(versionPath);
    const panel = page.getByRole('region', { name: 'Tâches' });
    const propose = panel.getByRole('button', { name: 'Proposer des tâches' });
    await expect(propose).toBeDisabled();
    await expect(propose).toHaveAccessibleDescription(/Envoyez d'abord la spécification vers Jira/);
  });

  test('US1-1 à US1-3 : proposer puis corriger, rien n’est envoyé à Jira', async () => {
    const created = await page.request.post(`/api/sessions/${sessionId}/jira`, {
      headers,
      data: { confirm: true, versionNumber: 1, issueTypeId: '10001', extraFields: {} },
    });
    expect(created.status()).toBe(201);
    await jira.delete('/__admin/requests');
    await page.reload();
    const panel = page.getByRole('region', { name: 'Tâches' });
    await panel.getByRole('button', { name: 'Proposer des tâches' }).click();
    await expect(panel.getByRole('heading', { name: 'Tâches proposées' })).toBeVisible();
    await expect(panel.getByLabel('Titre de la tâche 1')).toHaveValue('Enregistrer une réservation de place');
    await expect(panel.getByText('Cas d’usage UC-01').first()).toBeVisible();

    await panel.getByRole('button', { name: 'Supprimer la tâche 3' }).click();
    await expect(panel.getByLabel('Titre de la tâche 3')).toHaveCount(0);
    await panel.getByLabel('Nouvelle tâche').fill('Documenter la réservation');
    await panel.getByLabel('Son résultat vérifiable').fill("La page d'aide décrit la réservation");
    await panel.getByRole('button', { name: 'Ajouter', exact: true }).click();
    await expect(panel.getByLabel('Titre de la tâche 3')).toHaveValue('Documenter la réservation');
    await panel.getByRole('button', { name: 'Monter la tâche 3' }).click();
    await expect(panel.getByLabel('Titre de la tâche 2')).toHaveValue('Documenter la réservation');

    await page.reload();
    await expect(panel.getByLabel('Titre de la tâche 2')).toHaveValue('Documenter la réservation');
    await expect(panel.getByLabel('Titre de la tâche 3')).toHaveValue('Refuser la réservation quand le parking est complet');
    await expectNoA11yViolations(page);
    expect(await sent('POST', '/rest/api/2/issue')).toHaveLength(0);
  });

  test('US1-4 : valider crée une sous-tâche par tâche, dans l’ordre, sous la story', async () => {
    const panel = page.getByRole('region', { name: 'Tâches' });
    await panel.getByRole('button', { name: 'Valider et créer dans Jira' }).click();
    await expect(panel.getByText('0 tâche sur 3 terminée')).toBeVisible();
    await expect(panel.getByRole('link', { name: /PROJ-301/ })).toHaveAttribute('href', /\/browse\/PROJ-301$/);
    const posts = (await sent('POST', '/rest/api/2/issue')).map((r) => JSON.parse(r.body).fields);
    expect(posts.map((f) => [f.parent.key, f.summary])).toEqual([
      [STORY, 'Enregistrer une réservation de place'],
      [STORY, 'Documenter la réservation'],
      [STORY, 'Refuser la réservation quand le parking est complet'],
    ]);
    await expect(panel.getByRole('heading', { name: 'Tâches proposées' })).toHaveCount(0);
    await expectNoA11yViolations(page);
  });
});
