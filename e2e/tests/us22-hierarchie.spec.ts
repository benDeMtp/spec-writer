import {
  expect,
  request,
  test,
  type APIRequestContext,
  type BrowserContext,
  type Page,
} from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { baseURL, jiraURL, storageStatePath } from './support/env';

const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

/** Ticket Jira au format de /rest/api/2/search, avec son parent s'il en a un. */
function issue(key: string, type: string, updated: string, parent?: string) {
  return {
    key,
    fields: {
      summary: `Titre ${key}`,
      description: `Description de ${key}`,
      status: { name: 'À faire', statusCategory: { key: 'new' } },
      issuetype: { name: type },
      updated,
      ...(parent ? { parent: { key: parent } } : {}),
    },
  };
}

/**
 * Feature 022, US1 (quickstart Q1, Q2) : dans la vue « Tickets Jira », les sous-tâches sont repliées
 * sous leur ticket parent ; une sous-tâche dont le parent n'est pas indexé reste au premier niveau.
 * Le projet Jira `HIER` est simulé ici : une story et trois sous-tâches, une story seule, une
 * sous-tâche orpheline.
 */
test.describe.serial('US22 hiérarchie des sous-tâches', () => {
  let context: BrowserContext;
  let page: Page;
  let jira: APIRequestContext;
  let projectId = '';
  const headers = { Origin: baseURL };

  async function stub(mapping: object): Promise<void> {
    const created = await jira.post('/__admin/mappings', { data: { priority: 1, ...mapping } });
    expect(created.status()).toBe(201);
  }

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    jira = await request.newContext({ baseURL: jiraURL });
    await jira.post('/__admin/mappings/reset');
    await page.request.put('/api/consents/JIRA', { headers, data: { granted: true } });
    const saved = await page.request.put('/api/settings/jira', {
      headers,
      data: { serverUrl: jiraURL, defaultProjectKey: 'PROJ', acceptInsecureHttp: true, token: 'e2e-good-token' },
    });
    expect(saved.status()).toBe(200);
    await stub({
      request: { method: 'GET', urlPath: '/rest/api/2/project/HIER' },
      response: { status: 200, headers: JSON_HEADERS, jsonBody: { key: 'HIER' } },
    });
    const now = Date.now();
    const at = (minutes: number) => new Date(now - minutes * 60_000).toISOString().replace('Z', '+0000');
    const all = [
      issue('HIER-1', 'Story', at(30)),
      issue('HIER-2', 'Sous-tâche', at(1), 'HIER-1'),
      issue('HIER-3', 'Sous-tâche', at(2), 'HIER-1'),
      issue('HIER-10', 'Sous-tâche', at(3), 'HIER-1'),
      issue('HIER-20', 'Story', at(10)),
      issue('HIER-30', 'Sous-tâche', at(40), 'AUTRE-9'),
    ];
    await stub({
      request: {
        method: 'GET',
        urlPath: '/rest/api/2/search',
        queryParameters: { jql: { matches: 'project = HIER( AND .*)? ORDER BY (updated|key) ASC' } },
      },
      response: {
        status: 200,
        headers: JSON_HEADERS,
        jsonBody: { startAt: 0, maxResults: 200, total: all.length, issues: all },
      },
    });
    const created = await page.request.post('/api/projects', {
      headers,
      data: { name: 'Hiérarchie', jiraProjectKey: 'HIER' },
    });
    expect(created.status()).toBe(201);
    projectId = (await created.json()).id;
    await page.request.put('/api/projects/current', { headers, data: { projectId } });
  });

  test.afterAll(async () => {
    await page.request.put('/api/projects/current', { headers, data: { projectId: DEFAULT_PROJECT } });
    await page.request.delete(`/api/projects/${projectId}?sessions=DELETE`, { headers });
    await page.request.delete('/api/settings/jira', { headers });
    await page.request.put('/api/consents/JIRA', { headers, data: { granted: false } });
    await jira.post('/__admin/mappings/reset');
    await jira.dispose();
    await context.close();
  });

  test('Q1, Q2 : sous-tâches repliées sous leur parent, dépliables, orpheline signalée', async () => {
    test.setTimeout(180_000);
    await page.goto('/');
    await page.getByRole('link', { name: 'Tickets Jira' }).click();
    await expect(page.getByRole('heading', { name: 'Tickets Jira' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Resynchroniser Jira' })).toBeEnabled({ timeout: 120_000 });
    await page.getByRole('button', { name: 'Resynchroniser Jira' }).click();
    await expect(page.getByRole('button', { name: 'Resynchroniser Jira' })).toBeEnabled({ timeout: 120_000 });

    // premier niveau : les deux stories et l'orpheline ; le total ne compte pas les sous-tâches
    const pager = page.getByRole('navigation', { name: 'Pages de tickets' });
    await expect(pager).toContainText('1 à 3 sur 3');
    const rows = page.locator('tbody tr');
    await expect(rows).toHaveCount(3);
    await expect(page.getByRole('link', { name: 'HIER-2', exact: true })).toHaveCount(0);

    const toggle = page.getByRole('button', { name: 'Afficher les 3 sous-tâches de HIER-1' });
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await expectNoA11yViolations(page);

    await toggle.click();
    const shown = page.getByRole('button', { name: 'Masquer les 3 sous-tâches de HIER-1' });
    await expect(shown).toHaveAttribute('aria-expanded', 'true');
    const children = page.locator('tbody tr.subtask');
    await expect(children).toHaveCount(3);
    await expect(children.getByRole('link', { name: /^HIER-\d+$/ })).toHaveText(['HIER-2', 'HIER-3', 'HIER-10']);
    await expectNoA11yViolations(page);

    // l'orpheline reste au premier niveau, avec la clé de son parent
    await expect(rows.filter({ hasText: 'HIER-30' })).toContainText('Sous-tâche de AUTRE-9');

    // au clavier : Entrée replie
    await shown.focus();
    await page.keyboard.press('Enter');
    await expect(children).toHaveCount(0);
  });

  test('024 : la vue s’ouvre sur le label et sans les terminés ; un ticket supprimé est retiré', async () => {
    test.setTimeout(180_000);
    // le label se règle dans les réglages du projet
    await page.goto('/projet/reglages');
    const section = page.getByRole('region', { name: 'Suivi des tâches dans Jira' });
    await section.getByLabel('Label des tickets de spec-writer').fill('spec-writer');
    await section.getByRole('button', { name: 'Enregistrer le label' }).click();
    await expect(section.getByRole('button', { name: 'Retirer le label' })).toBeVisible();
    await expectNoA11yViolations(page);

    // Jira : HIER-1 et HIER-40 (fermée) portent le label ; HIER-3 a été supprimée
    const now = Date.now();
    const at = (minutes: number) => new Date(now - minutes * 60_000).toISOString().replace('Z', '+0000');
    const labelled = (i: ReturnType<typeof issue>) => ({ ...i, fields: { ...i.fields, labels: ['spec-writer'] } });
    const closed = labelled(issue('HIER-40', 'Story', at(5)));
    closed.fields.status = { name: 'Fermée', statusCategory: { key: 'done' } };
    const all = [
      labelled(issue('HIER-1', 'Story', at(30))),
      issue('HIER-2', 'Sous-tâche', at(1), 'HIER-1'),
      issue('HIER-10', 'Sous-tâche', at(3), 'HIER-1'),
      issue('HIER-20', 'Story', at(10)),
      issue('HIER-30', 'Sous-tâche', at(40), 'AUTRE-9'),
      closed,
    ];
    const created = await jira.post('/__admin/mappings', {
      data: {
        priority: 0,
        request: {
          method: 'GET',
          urlPath: '/rest/api/2/search',
          queryParameters: { jql: { matches: 'project = HIER( AND .*)? ORDER BY (updated|key) ASC' } },
        },
        response: {
          status: 200,
          headers: JSON_HEADERS,
          jsonBody: { startAt: 0, maxResults: 200, total: all.length, issues: all },
        },
      },
    });
    expect(created.status()).toBe(201);

    await page.goto('/');
    await page.getByRole('link', { name: 'Tickets Jira' }).click();
    await expect(page.getByRole('button', { name: 'Resynchroniser Jira' })).toBeEnabled({ timeout: 120_000 });
    await page.getByRole('button', { name: 'Resynchroniser Jira' }).click();
    await expect(page.getByRole('button', { name: 'Resynchroniser Jira' })).toBeEnabled({ timeout: 120_000 });

    const filters = page.getByRole('group', { name: 'Filtres' });
    const byLabel = filters.getByRole('checkbox', { name: 'Avec le label spec-writer' });
    const done = filters.getByRole('checkbox', { name: 'Afficher les tickets terminés' });
    await expect(byLabel).toBeChecked();
    await expect(done).not.toBeChecked();
    const rows = page.locator('tbody tr:not(.subtask)');
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText('HIER-1');

    // HIER-3 a disparu de la story ; les sous-tâches ne sont pas filtrées
    await page.getByRole('button', { name: 'Afficher les 2 sous-tâches de HIER-1' }).click();
    await expect(page.locator('tbody tr.subtask').getByRole('link', { name: /^HIER-\d+$/ })).toHaveText([
      'HIER-2',
      'HIER-10',
    ]);
    await expectNoA11yViolations(page);

    // au clavier : les terminés, puis tous les tickets
    await done.focus();
    await page.keyboard.press('Space');
    await expect(rows).toHaveCount(2);
    await expect(page.getByRole('link', { name: 'HIER-40', exact: true })).toBeVisible();
    await byLabel.focus();
    await page.keyboard.press('Space');
    await expect(rows).toHaveCount(4);

    // le choix est gardé en revenant à la vue
    await page.goto('/projet/reglages');
    await page.goto('/');
    await page.getByRole('link', { name: 'Tickets Jira' }).click();
    await expect(page.getByRole('group', { name: 'Filtres' }).getByRole('checkbox', { name: 'Avec le label spec-writer' }))
      .not.toBeChecked();
    await expect(page.locator('tbody tr:not(.subtask)')).toHaveCount(4);
  });
});
