import {
  expect,
  request,
  test,
  type APIRequestContext,
  type BrowserContext,
  type Page,
} from '@playwright/test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { McpClient } from '../mcp-client';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, jiraURL, stateDir, storageStatePath } from './support/env';

const NEED =
  'Les assistantes doivent pouvoir réserver une salle de réunion pour un comité de direction, et voir les salles libres.';

/**
 * US5 de la feature 002 : statut d'implémentation, verrou et évolution (quickstart P11), puis accès
 * des agents par la passerelle MCP (quickstart P12).
 */
test.describe.serial('US11 statut et agents', () => {
  let context: BrowserContext;
  let page: Page;
  let specId = '';
  let factId = '';
  let jira: APIRequestContext;
  const headers = { Origin: baseURL };

  async function dialogOpen(): Promise<void> {
    await expect(page.locator('.mat-mdc-dialog-container').last()).toHaveClass(/mdc-dialog--open/);
    await expect(page.locator('.mat-mdc-dialog-container').last()).not.toHaveClass(
      /mdc-dialog--opening/,
    );
  }

  async function chooseStatus(label: string): Promise<void> {
    await page.getByRole('combobox', { name: "Statut d'implémentation" }).click();
    await page.getByRole('listbox').getByRole('option', { name: label }).click();
    // le panneau se ferme avant qu'un autre choix ne le rouvre
    await expect(page.getByRole('listbox')).toHaveCount(0);
  }

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    await ensureCopilotConnected(page.request);
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: true } });
    let session = await (
      await page.request.post('/api/sessions', { headers, data: { need: NEED } })
    ).json();
    specId = session.id;
    while (session.currentBatch.length) {
      const q = session.currentBatch[0];
      session = await (
        await page.request.put(`/api/sessions/${specId}/questions/${q.id}/answer`, {
          headers,
          data: { value: q.defaultValue ?? q.suggestions[0] ?? 'Oui' },
        })
      ).json();
    }
    expect(
      (await page.request.post(`/api/sessions/${specId}/versions`, { headers })).status(),
    ).toBe(201);
  });

  test.afterAll(async () => {
    const all = (await (await page.request.get('/api/sessions')).json()) as {
      id: string;
      name: string;
    }[];
    for (const s of all.filter((s) => s.id === specId || s.name.startsWith('Évolution de '))) {
      await page.request.delete(`/api/sessions/${s.id}`, { headers });
    }
    await page.request.put('/api/settings/agent-access', { headers, data: { enabled: false } });
    if (factId) {
      await page.request.put('/api/projects/current', {
        headers,
        data: { projectId: '00000000-0000-0000-0000-000000000001' },
      });
      await page.request.delete(`/api/projects/${factId}?sessions=DELETE`, { headers });
    }
    await page.request.put('/api/consents/JIRA', { headers, data: { granted: false } });
    await page.request.delete('/api/settings/jira', { headers });
    await jira?.dispose();
    // les parcours suivants (US2) attendent un consentement non encore donné
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: false } });
    await context.close();
  });

  test('P11 : implémentée, la spécification est verrouillée, avec la raison', async () => {
    await page.goto(`/sessions/${specId}/versions/1`);
    await chooseStatus('Prête à implémenter');
    await expect(
      page.getByRole('status').filter({ hasText: 'Statut : Prête à implémenter.' }),
    ).toBeVisible();
    await chooseStatus('En cours');
    await chooseStatus('Implémentée');
    await expect(page.locator('#locked-reason')).toContainText(
      'cette spécification est verrouillée',
    );
    for (const name of [
      "Discuter avec l'IA",
      'Créer un ticket Jira',
      'Supprimer cette version',
    ]) {
      await expect(page.getByRole('button', { name })).toBeDisabled();
    }
    await expectNoA11yViolations(page);

    // l'API refuse aussi : génération, discussion (011), suppression de version
    for (const [method, url] of [
      ['POST', `/api/sessions/${specId}/versions`],
      ['POST', `/api/sessions/${specId}/discussion`],
      ['DELETE', `/api/sessions/${specId}/versions/1`],
    ] as const) {
      const r = await page.request.fetch(url, {
        method,
        headers,
        data: method === 'POST' ? {} : undefined,
      });
      expect(r.status(), `${method} ${url}`).toBe(409);
      expect((await r.json()).code).toBe('SPECIFICATION_LOCKED');
    }
    await page.goto(`/sessions/${specId}`);
    await expect(page.getByText('Cette spécification est implémentée')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Générer à nouveau' })).toBeDisabled();
  });

  test('P11 : « Rétablir » demande une confirmation ; « Créer une évolution » ouvre un brouillon qui cite l’origine', async () => {
    await page.goto(`/sessions/${specId}/versions/1`);
    await chooseStatus('En cours');
    const dialog = page.getByRole('dialog', { name: 'Rétablir un statut antérieur ?' });
    await expect(dialog).toContainText('a été implémentée le');
    await dialogOpen();
    await expectNoA11yViolations(page);
    await dialog.getByRole('button', { name: 'Annuler' }).click();
    await expect(page.locator('#locked-reason')).toBeVisible();

    await page.getByRole('button', { name: 'Créer une évolution' }).click();
    await expect(page).toHaveURL(/\/sessions\/[0-9a-f-]+$/);
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Évolution de');
    const evolvedId = page.url().split('/').at(-1)!;
    const evolved = await (await page.request.get(`/api/sessions/${evolvedId}`)).json();
    expect(evolved.implementation).toEqual({
      status: 'DRAFT',
      implementedAt: null,
      evolvedFromSessionId: specId,
    });

    await page.goto(`/sessions/${specId}/versions/1`);
    await chooseStatus('En cours');
    await page.getByRole('dialog').getByRole('button', { name: 'Rétablir' }).click();
    await expect(page.locator('#locked-reason')).toHaveCount(0);
    await expect(page.getByRole('button', { name: "Discuter avec l'IA" })).toBeEnabled();
  });

  /** Projet FACT indexé depuis le Jira simulé, pour search_knowledge. */
  async function factProject(): Promise<string> {
    jira = await request.newContext({ baseURL: jiraURL });
    await jira.post('/__admin/mappings/reset');
    await page.request.put('/api/consents/JIRA', { headers, data: { granted: true } });
    const saved = await page.request.put('/api/settings/jira', {
      headers,
      data: {
        serverUrl: jiraURL,
        defaultProjectKey: 'PROJ',
        acceptInsecureHttp: true,
        token: 'e2e-good-token',
      },
    });
    expect(saved.status()).toBe(200);
    const created = await page.request.post('/api/projects', {
      headers,
      data: { name: 'Agents FACT', jiraProjectKey: 'FACT' },
    });
    expect(created.status()).toBe(201);
    const id = (await created.json()).id as string;
    await page.request.post(`/api/projects/${id}/indexations`, { headers, data: { mode: 'FULL' } });
    await expect
      .poll(
        async () =>
          (await (await page.request.get(`/api/projects/${id}/knowledge`)).json()).tickets,
        { timeout: 90_000 },
      )
      .toBeGreaterThan(0);
    await expect
      .poll(
        async () =>
          (await (await page.request.get(`/api/projects/${id}/indexations/current`)).json()).status,
        {
          timeout: 90_000,
        },
      )
      .toBe('SUCCEEDED');
    return id;
  }

  test('P12 : activation, appels MCP par la passerelle, journal, régénération et refus', async () => {
    test.setTimeout(180_000);
    factId = await factProject();
    await page.request.put(`/api/sessions/${specId}/status`, {
      headers,
      data: { status: 'READY', confirmUnlock: true },
    });

    // activation depuis la configuration : l'extrait à copier ne contient pas le secret
    await page.goto('/configuration');
    const card = page.locator('sw-agent-access-card');
    await card.getByRole('button', { name: "Activer l'accès des agents" }).click();
    await expect(card.getByRole('status').first()).toContainText('Activé');
    await expect(card.locator('pre')).toContainText('dev.specwriter.mcp.McpBridge');
    await expectNoA11yViolations(page);
    const dataDir = readFileSync(join(stateDir, 'data-dir'), 'utf8').trim();
    const secret = JSON.parse(readFileSync(join(dataDir, 'agent-endpoint.json'), 'utf8'))
      .secret as string;
    expect(await card.locator('pre').textContent()).not.toContain(secret);

    // la commande déclarée dans l'agent, lancée telle quelle
    const state = await (await page.request.get('/api/settings/agent-access')).json();
    const server = JSON.parse(state.mcpJson).mcpServers['spec-writer'];
    const mcp = new McpClient(server.command, server.args ?? []);
    try {
      const init = await mcp.request('initialize', {
        protocolVersion: '2025-06-18',
        capabilities: {},
        clientInfo: { name: 'Claude Code', version: 'e2e' },
      });
      expect(init.result?.protocolVersion).toBe('2025-06-18');
      mcp.notify('notifications/initialized');

      const projects = JSON.parse((await mcp.call('list_projects')).text) as {
        id: string;
        name: string;
      }[];
      expect(projects.map((p) => p.name)).toContain('Agents FACT');
      const features = JSON.parse(
        (await mcp.call('list_features', { projectId: '00000000-0000-0000-0000-000000000001' }))
          .text,
      ) as { id: string }[];
      expect(features.map((f) => f.id)).toContain(specId);

      const feature = JSON.parse((await mcp.call('get_feature', { featureId: specId })).text);
      const exported = await (
        await page.request.get(`/api/sessions/${specId}/versions/1/export`)
      ).text();
      expect(feature.markdown).toBe(exported);
      expect(feature.knowledge).toHaveProperty('generalContext');

      // recherche locale : ni Jira ni le LLM
      await jira.delete('/__admin/requests');
      const hits = JSON.parse(
        (await mcp.call('search_knowledge', { projectId: factId, query: 'avoir' })).text,
      ) as {
        ref: string;
      }[];
      expect(hits.length).toBeLessThanOrEqual(10);
      expect(hits.map((h) => h.ref)).toContain('FACT-7');
      const sent = await (
        await jira.post('/__admin/requests/count', { data: { urlPattern: '/rest/.*' } })
      ).json();
      expect(sent.count).toBe(0);

      // statut : READY → IMPLEMENTED refusé, puis les deux transitions permises
      const jump = await mcp.call('set_feature_status', {
        featureId: specId,
        status: 'IMPLEMENTED',
      });
      expect(jump.isError).toBe(true);
      expect(jump.text).toMatch(/^TRANSITION_NOT_ALLOWED/);
      expect(
        (await mcp.call('set_feature_status', { featureId: specId, status: 'IN_PROGRESS' }))
          .isError,
      ).toBe(false);
      expect(
        (await mcp.call('set_feature_status', { featureId: specId, status: 'IMPLEMENTED' }))
          .isError,
      ).toBe(false);

      // régénération : l'ancien secret est refusé, la passerelle relit le fichier et continue
      await page.reload();
      await card.getByRole('button', { name: 'Régénérer le secret' }).click();
      await page.getByRole('dialog').getByRole('button', { name: 'Régénérer' }).click();
      await expect(card.getByText('Secret régénéré.')).toBeVisible();
      expect((await mcp.call('list_projects')).isError).toBe(false);
      expect(mcp.invalidLines).toEqual([]);
      expect(mcp.stderr.join('\n')).not.toContain(secret);
    } finally {
      mcp.close();
    }

    const direct = await request.newContext({ baseURL });
    expect(
      (
        await direct.get('/api/agent/projects', { headers: { Authorization: `Bearer ${secret}` } })
      ).status(),
    ).toBe(401);
    const renewed = JSON.parse(readFileSync(join(dataDir, 'agent-endpoint.json'), 'utf8'))
      .secret as string;
    expect(
      (
        await direct.get('/api/agent/projects', {
          headers: { Authorization: `Bearer ${renewed}`, Origin: baseURL },
        })
      ).status(),
    ).toBe(403);
    await direct.dispose();

    // journal : deux changements de statut, avec l'agent annoncé
    await page.reload();
    const log = card.getByRole('table');
    await expect(log).toContainText('Claude Code');
    await expect(log).toContainText('Prête à implémenter → En cours');
    await expect(log).toContainText('En cours → Implémentée');
    await expect(log).not.toContainText('avoir');
  });
});
