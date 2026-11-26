import { expect, request, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test';
import { McpClient } from '../mcp-client';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, jiraURL, storageStatePath } from './support/env';

const NEED =
  'Les employés doivent pouvoir réserver une place de parking pour une journée, et être prévenus quand le parking ' +
  'est complet.';
const META = '/rest/api/2/issue/createmeta/PROJ/issuetypes';
const STORY = 'PROJ-300';
const json = { 'Content-Type': 'application/json' };
const SUBTASKS = ['PROJ-301', 'PROJ-302', 'PROJ-303'];
type Category = 'new' | 'indeterminate' | 'done';
const NAMES: Record<Category, string> = { new: 'À faire', indeterminate: 'En cours', done: 'Terminé' };
const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';
/** 023 : workflow à la AURORA, où « Réalisée » et « En Cours » sont tous deux « en cours » pour Jira. */
const AURORA_TRANSITIONS = [
  { id: '21', name: 'Démarrer', to: { name: 'En Cours', statusCategory: { key: 'indeterminate' } } },
  { id: '41', name: 'Réaliser', to: { name: 'Réalisée', statusCategory: { key: 'indeterminate' } } },
  { id: '31', name: 'Fermer', to: { name: 'Fermée', statusCategory: { key: 'done' } } },
  { id: '51', name: 'Annuler', to: { name: 'Annulée', statusCategory: { key: 'done' } } },
];

/** 015 : tâches d'une spécification, créées en sous-tâches Jira et suivies par les agents. */
test.describe.serial('US15 tâches et avancement', () => {
  let context: BrowserContext;
  let page: Page;
  let jira: APIRequestContext;
  let sessionId = '';
  let versionPath = '';
  const headers = { Origin: baseURL };
  /** 023 : nom de statut Jira imposé à une sous-tâche, et sous-tâches au workflow à la AURORA. */
  const named: Record<string, string> = {};
  const aurora = new Set<string>();

  async function stub(mapping: object): Promise<void> {
    expect((await jira.post('/__admin/mappings', { data: { priority: 5, ...mapping } })).status()).toBe(201);
  }

  /** Jira simulé dans l'état donné : recherche par clés, statut et transitions de chaque sous-tâche. */
  async function jiraState(state: Record<string, Category>, down = false): Promise<void> {
    const mappings = (await (await jira.get('/__admin/mappings')).json()).mappings as { id: string; metadata?: { etat?: boolean } }[];
    for (const m of mappings.filter((m) => m.metadata?.etat)) {
      await jira.delete(`/__admin/mappings/${m.id}`);
    }
    const status = (key: string, c: Category) => ({ name: named[key] ?? NAMES[c], statusCategory: { key: c } });
    const add = (mapping: object) => stub({ priority: 0, metadata: { etat: true }, ...mapping });
    if (down) {
      await add({ request: { urlPattern: '/rest/api/2/(search.*|issue/PROJ-30[1-3].*)' }, response: { status: 503 } });
      return;
    }
    await add({
      request: { method: 'GET', urlPath: '/rest/api/2/search' },
      response: {
        status: 200,
        headers: json,
        jsonBody: {
          startAt: 0,
          total: Object.keys(state).length,
          issues: Object.entries(state).map(([key, c]) => ({
            key,
            fields: { summary: key, status: status(key, c), issuetype: { name: 'Sous-tâche' }, updated: '2026-11-17T10:00:00.000+0000' },
          })),
        },
      },
    });
    for (const [key, c] of Object.entries(state)) {
      await add({
        request: { method: 'GET', urlPath: `/rest/api/2/issue/${key}` },
        response: { status: 200, headers: json, jsonBody: { key, fields: { status: status(key, c), updated: '2026-11-17T10:00:00.000+0000' } } },
      });
      await add({
        request: { method: 'GET', urlPath: `/rest/api/2/issue/${key}/transitions` },
        response: {
          status: 200,
          headers: json,
          jsonBody: {
            transitions: aurora.has(key)
              ? AURORA_TRANSITIONS.filter((t) => t.to.name !== named[key])
              : [
                  { id: '11', name: 'Rouvrir', to: { statusCategory: { key: 'new' } } },
                  { id: '21', name: 'Démarrer', to: { statusCategory: { key: 'indeterminate' } } },
                  { id: '31', name: 'Terminer', to: { statusCategory: { key: 'done' } } },
                ].filter((t) => t.to.statusCategory.key !== c),
          },
        },
      });
      await add({ request: { method: 'POST', urlPath: `/rest/api/2/issue/${key}/transitions` }, response: { status: 204 } });
      await add({ request: { method: 'POST', urlPath: `/rest/api/2/issue/${key}/comment` }, response: { status: 201, headers: json, jsonBody: { id: '1' } } });
    }
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

  test('US4 : la spécification tirée d’un document s’ouvre sur l’énoncé de story, repris à l’export et vers Jira', async () => {
    const created = await page.request.post('/api/sessions', {
      headers,
      data: { need: "E2E-STORY : les agents d'accueil prêtent des vélos de courtoisie aux visiteurs du siège." },
    });
    const story = (await created.json()) as { id: string };
    try {
      const html =
        '<html><head><title>Accueil vélos</title></head><body><h1>Prêt de vélos</h1>' +
        '<p>Le prêt dure au plus 4 heures.</p></body></html>';
      const joined = await page.request.post(`/api/sessions/${story.id}/attachments?name=${encodeURIComponent('Accueil velos.html')}`, {
        headers: { ...headers, 'Content-Type': 'application/octet-stream' },
        data: Buffer.from(html),
      });
      expect(joined.status()).toBe(201);
      expect((await page.request.post(`/api/sessions/${story.id}/versions`, { headers })).status()).toBe(201);

      await page.goto(`/sessions/${story.id}/versions/1`);
      await expect(page.getByTestId('story')).toHaveText(
        "En tant que agent d'accueil, je veux prêter un vélo de courtoisie à un visiteur, afin de faciliter ses " +
          'déplacements entre les sites (hypothèse).',
        { useInnerText: true },
      );
      const split = page.getByRole('region', { name: 'Découpage proposé' });
      await expect(split.getByRole('listitem')).toHaveText(['Prêter un vélo', 'Restituer un vélo et signaler une panne']);
      await expectNoA11yViolations(page);

      const markdown = await (await page.request.get(`/api/sessions/${story.id}/versions/1/export`)).text();
      expect(markdown).toMatch(
        /^# Prêt de vélos de courtoisie\n\n> En tant que \*\*agent d'accueil\*\*, je veux \*\*prêter un vélo de courtoisie à un visiteur\*\*, afin de \*\*faciliter ses déplacements entre les sites\*\* \(hypothèse\)\.\n/,
      );
      expect(markdown).toContain('## Découpage proposé');
      const preview = await page.request.post(`/api/sessions/${story.id}/jira/preview`, {
        headers,
        data: { versionNumber: 1, issueTypeId: '10001' },
      });
      expect(preview.status()).toBe(200);
      const wiki = ((await preview.json()) as { descriptionWiki: string }).descriptionWiki;
      expect(wiki).toContain('En tant que');
      expect(wiki.indexOf("agent d'accueil")).toBeLessThan(wiki.indexOf('Résumé'));
    } finally {
      await page.request.delete(`/api/sessions/${story.id}`, { headers });
    }
  });

  test.describe.serial('US2 et US3 : suivi par les agents et avancement', () => {
    let first: McpClient;
    let second: McpClient;
    let instructions = '';
    const state: Record<string, Category> = { 'PROJ-301': 'new', 'PROJ-302': 'new', 'PROJ-303': 'new' };

    async function bridge(): Promise<McpClient> {
      const access = await (await page.request.get('/api/settings/agent-access')).json();
      const server = JSON.parse(access.mcpJson).mcpServers['spec-writer'];
      const client = new McpClient(server.command, server.args ?? []);
      const init = await client.request('initialize', {
        protocolVersion: '2025-06-18',
        capabilities: {},
        clientInfo: { name: 'Claude Code', version: 'e2e' },
      });
      instructions = (init as { result?: { instructions?: string } }).result?.instructions ?? '';
      client.notify('notifications/initialized');
      return client;
    }

    async function call(client: McpClient, tool: string, args: object = {}) {
      const r = await client.call(tool, { featureId: sessionId, ...args });
      return { ...r, json: r.isError ? null : JSON.parse(r.text) };
    }

    test.beforeAll(async () => {
      await page.request.put('/api/settings/agent-access', { headers, data: { enabled: true } });
      expect((await page.request.put(`/api/sessions/${sessionId}/status`, { headers, data: { status: 'READY' } })).status()).toBe(200);
      await jiraState(state);
      first = await bridge();
      second = await bridge();
    });

    test.afterAll(async () => {
      first?.close();
      second?.close();
      await page.request.put('/api/settings/agent-access', { headers, data: { enabled: false } });
    });

    test('US2-1, 3, 5 et 6 : prochaine tâche, prise, refus à une autre conversation', async () => {
      const feature = await call(first, 'get_feature');
      expect(feature.json.tasks.map((t: { key: string }) => t.key)).toEqual(SUBTASKS);
      expect(feature.json.progress).toEqual({ done: 0, total: 3 });

      // T084 : next_task répond en moins de 3 s, relecture Jira comprise
      const asked = Date.now();
      const next = await call(first, 'next_task');
      expect(Date.now() - asked).toBeLessThan(3_000);
      expect(next.json.outcome).toBe('TASK');
      expect(next.json.task.key).toBe('PROJ-301');
      expect((await call(first, 'next_task')).json).toEqual(next.json);
      // 022 (US2, US3) : le ticket parent de la sous-tâche, et la branche sur laquelle travailler
      expect(next.json.task.parentKey).toBe(STORY);
      expect(next.json.branch).toContain(`sous-tâche de ${STORY}`);
      expect(next.json.branch).toContain(`travaillez sur celle de ${STORY}`);
      expect(feature.json.tasks.map((t: { parentKey: string }) => t.parentKey)).toEqual([STORY, STORY, STORY]);
      expect(feature.json.jiraTicket.key).toBe(STORY);
      expect(instructions).toContain('se fait sur la branche de son ticket parent');

      await jira.delete('/__admin/requests');
      const started = await call(first, 'start_task', { taskKey: 'PROJ-301' });
      expect(started.isError).toBe(false);
      expect(started.json.jiraSync).toBe('OK');
      expect(started.json.branch).toContain(`travaillez sur celle de ${STORY}`);
      const moves = await sent('POST', '/rest/api/2/issue/PROJ-301/transitions');
      expect(moves.map((r) => JSON.parse(r.body).transition.id)).toEqual(['21']);
      state['PROJ-301'] = 'indeterminate';
      await jiraState(state);

      const held = await call(second, 'start_task', { taskKey: 'PROJ-301' });
      expect(held.isError).toBe(true);
      expect(held.text).toMatch(/^TASK_HELD : .*Claude Code/);
      expect((await call(second, 'next_task')).json.task.key).toBe('PROJ-302');
    });

    test('US2-2 : la conversation suivante reprend la tâche d’une conversation fermée, puis la termine', async () => {
      first.close();
      await expect
        .poll(async () => (await call(second, 'next_task')).json.task.key, { timeout: 10_000 })
        .toBe('PROJ-301');
      expect((await call(second, 'next_task')).json.resume).toBe(true);
      expect((await call(second, 'start_task', { taskKey: 'PROJ-301' })).isError).toBe(false);
      await jira.delete('/__admin/requests');
      const done = await call(second, 'complete_task', { taskKey: 'PROJ-301', report: 'Réservation enregistrée, tests verts.' });
      expect(done.json.task.status).toBe('DONE');
      expect(done.json.branch).toBeNull();
      expect((await sent('POST', '/rest/api/2/issue/PROJ-301/transitions')).map((r) => JSON.parse(r.body).transition.id))
        .toEqual(['31']);
      const comments = await sent('POST', '/rest/api/2/issue/PROJ-301/comment');
      expect(JSON.parse(comments[0].body).body).toContain('Réservation enregistrée, tests verts.');
      state['PROJ-301'] = 'done';
      await jiraState(state);

      const log = await (await page.request.get('/api/settings/agent-access/log')).json();
      const ops = log.entries.map((e: { operation: string }) => e.operation);
      expect(ops).toContain('START_TASK');
      expect(ops).toContain('COMPLETE_TASK');
      expect(JSON.stringify(log)).not.toContain('tests verts');
    });

    test('US3-1 à 4 : avancement relu dans Jira et statut qui suit les tâches', async () => {
      const opened = Date.now();
      await page.goto(versionPath);
      const panel = page.getByRole('region', { name: 'Tâches' });
      await expect(panel.getByText('1 tâche sur 3 terminée')).toBeVisible();
      // T084 : panneau ouvert, sous-tâches relues dans Jira, en moins de 2 s
      expect(Date.now() - opened).toBeLessThan(2_000);
      await expect(page.getByRole('combobox', { name: "Statut d'implémentation" })).toContainText('En cours');
      await panel.getByText("Compte rendu de l'agent").click();
      await expect(panel.getByText('Réservation enregistrée, tests verts.')).toBeVisible();
      await expectNoA11yViolations(page);

      state['PROJ-302'] = 'done';
      state['PROJ-303'] = 'done';
      await jiraState(state);
      await page.reload();
      await expect(panel.getByText('3 tâches sur 3 terminées')).toBeVisible();
      await expect(page.getByRole('combobox', { name: "Statut d'implémentation" })).toContainText('Implémentée');

      state['PROJ-302'] = 'new';
      await jiraState(state);
      await page.reload();
      await expect(panel.getByText('2 tâches sur 3 terminées')).toBeVisible();
      await expect(page.getByRole('combobox', { name: "Statut d'implémentation" })).toContainText('En cours');
    });

    test('FR-020a : repasser en Prête suspend le suivi, « Reprendre le suivi » le rétablit', async () => {
      const status = page.getByRole('combobox', { name: "Statut d'implémentation" });
      await status.click();
      await expect(page.getByRole('option', { name: 'Implémentée' })).toBeDisabled();
      await page.getByRole('option', { name: 'Prête à implémenter' }).click();
      await expect(page.getByRole('listbox')).toHaveCount(0);
      const panel = page.getByRole('region', { name: 'Tâches' });
      await page.reload();
      await expect(panel.getByText('Suivi suspendu')).toBeVisible();
      const suspended = await call(second, 'next_task');
      expect(suspended.isError).toBe(true);
      expect(suspended.text).toMatch(/^TRACKING_SUSPENDED/);
      await panel.getByRole('button', { name: 'Reprendre le suivi' }).click();
      await expect(panel.getByText('Suivi suspendu')).toHaveCount(0);
      await expect(status).toContainText('En cours');
    });

    test('US2-7, FR-013a : Jira coupé, la fin est « à synchroniser » puis envoyée par « Synchroniser »', async () => {
      expect((await call(second, 'start_task', { taskKey: 'PROJ-302' })).isError).toBe(false);
      state['PROJ-302'] = 'indeterminate';
      await jiraState(state, true);
      const done = await call(second, 'complete_task', { taskKey: 'PROJ-302' });
      expect(done.json.jiraSync).toBe('PENDING');
      expect(done.json.message).toContain('Jira est injoignable');

      await page.reload();
      const panel = page.getByRole('region', { name: 'Tâches' });
      await expect(panel.getByText('1 tâche à synchroniser')).toBeVisible();
      await expect(panel.getByText("Jira n'a pas pu être relu")).toBeVisible();

      await jiraState(state);
      await jira.delete('/__admin/requests');
      await panel.getByRole('button', { name: 'Synchroniser' }).click();
      await expect(panel.getByText('à synchroniser')).toHaveCount(0);
      expect((await sent('POST', '/rest/api/2/issue/PROJ-302/transitions')).map((r) => JSON.parse(r.body).transition.id))
        .toEqual(['31']);
    });
    test('023 : la tâche implémentée passe à « Réalisée », pas à « Fermée », et la story suit', async () => {
      // réglage du projet : « Réalisée » à l'arrivée, « En cours de test » fini aussi
      const saved = await page.request.put(`/api/projects/${DEFAULT_PROJECT}/task-workflow`, {
        headers,
        data: { arrivalStatus: 'Réalisée', finishedStatuses: ['En cours de test'] },
      });
      expect(saved.status()).toBe(200);
      try {
        await page.goto('/projet/reglages');
        const section = page.getByRole('region', { name: 'Suivi des tâches dans Jira' });
        await expect(section.getByRole('radio', { name: /Réalisée/ })).toBeChecked();
        await expect(section.getByRole('checkbox', { name: /En cours de test/ })).toBeChecked();
        await expectNoA11yViolations(page);

        // la story et ses transitions, au workflow à la AURORA
        await stub({
          priority: 1,
          request: { method: 'GET', urlPath: `/rest/api/2/issue/${STORY}/transitions` },
          response: { status: 200, headers: json, jsonBody: { transitions: AURORA_TRANSITIONS } },
        });
        await stub({
          priority: 1,
          request: { method: 'POST', urlPath: `/rest/api/2/issue/${STORY}/transitions` },
          response: { status: 204 },
        });
        aurora.add('PROJ-303');
        named['PROJ-303'] = 'Créée';
        Object.assign(state, { 'PROJ-301': 'done', 'PROJ-302': 'done', 'PROJ-303': 'new' });
        await jiraState(state);
        await jira.delete('/__admin/requests');

        // prise : la sous-tâche démarre, la story « à faire » passe en cours
        const started = await call(second, 'start_task', { taskKey: 'PROJ-303' });
        expect(started.isError).toBe(false);
        expect(started.json.story).toEqual({ key: STORY, status: 'MOVED', to: 'En Cours', message: null });
        expect((await sent('POST', '/rest/api/2/issue/PROJ-303/transitions')).map((r) => JSON.parse(r.body).transition.id))
          .toEqual(['21']);
        expect((await sent('POST', `/rest/api/2/issue/${STORY}/transitions`)).map((r) => JSON.parse(r.body).transition.id))
          .toEqual(['21']);

        // fin de la dernière tâche : « Réaliser », jamais « Fermer » ; la story passe à « Réalisée »
        named['PROJ-303'] = 'En Cours';
        state['PROJ-303'] = 'indeterminate';
        await jiraState(state);
        await jira.delete('/__admin/requests');
        const done = await call(second, 'complete_task', { taskKey: 'PROJ-303' });
        expect(done.isError).toBe(false);
        expect(done.json.task.status).toBe('DONE');
        expect(done.json.story).toEqual({ key: STORY, status: 'MOVED', to: 'Réalisée', message: null });
        expect((await sent('POST', '/rest/api/2/issue/PROJ-303/transitions')).map((r) => JSON.parse(r.body).transition.id))
          .toEqual(['41']);
        expect((await sent('POST', `/rest/api/2/issue/${STORY}/transitions`)).map((r) => JSON.parse(r.body).transition.id))
          .toEqual(['41']);

        // relecture : « Réalisée » est « en cours » pour Jira, mais la tâche reste terminée
        named['PROJ-303'] = 'Réalisée';
        await jiraState(state);
        await page.goto(versionPath);
        const panel = page.getByRole('region', { name: 'Tâches' });
        await expect(panel.getByText('3 tâches sur 3 terminées')).toBeVisible();
        // toutes finies : la spécification est implémentée, donc plus servie aux agents
        await expect(page.getByRole('combobox', { name: "Statut d'implémentation" })).toContainText('Implémentée');
      } finally {
        await page.request.delete(`/api/projects/${DEFAULT_PROJECT}/task-workflow`, { headers });
      }
    });
  });
});
