import {
  expect,
  request,
  test,
  type APIRequestContext,
  type BrowserContext,
  type Page,
} from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, jiraURL, storageStatePath } from './support/env';

const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';
const TOTAL_ISSUES = 120;
const FOUND = 60;
const LOCAL = 10;
const JSON_HEADERS = { 'Content-Type': 'application/json' };

const CATEGORIES = [
  { name: 'À faire', key: 'new' },
  { name: 'En cours', key: 'indeterminate' },
  { name: 'Terminé', key: 'done' },
];

/** Ticket Jira au format de /rest/api/2/search ; le statut tourne sur les trois catégories. */
function issue(key: string, summary: string, updated: string, n = 0) {
  const category = CATEGORIES[n % CATEGORIES.length];
  return {
    key,
    fields: {
      summary,
      description: `Description de ${summary}`,
      status: { name: category.name, statusCategory: { key: category.key } },
      issuetype: { name: 'Story' },
      updated,
    },
  };
}

/** Date Jira, `n` heures avant (négatif) ou après la référence. */
function at(reference: number, hours: number): string {
  return new Date(reference + hours * 3_600_000).toISOString().replace('Z', '+0000');
}

/**
 * US4 de la feature 002 : tickets Jira et recherche par pages de 25 (quickstart P10). Le projet
 * Jira `PAGE` est simulé ici : 120 tickets, et 60 résultats pour « lettrage », dont la moitié plus
 * récents que les 10 spécifications locales.
 */
test.describe.serial('US10 Jira paginé', () => {
  let context: BrowserContext;
  let page: Page;
  let jira: APIRequestContext;
  let projectId = '';
  const headers = { Origin: baseURL };

  /** Ajoute un mapping WireMock et rend son identifiant, pour le retirer ensuite. */
  async function stub(mapping: object): Promise<string> {
    const created = await jira.post('/__admin/mappings', {
      data: { priority: 1, ...mapping },
    });
    expect(created.status()).toBe(201);
    return (await created.json()).id;
  }

  /** Jira coupé pour une JQL : 503 jusqu'au retrait du mapping. */
  function cut(jql: string): Promise<string> {
    return stub({
      priority: 0,
      request: {
        method: 'GET',
        urlPath: '/rest/api/2/search',
        queryParameters: { jql: { equalTo: jql } },
      },
      response: { status: 503 },
    });
  }

  function search(jql: string, startAt: number, issues: object[], total: number): object {
    return {
      request: {
        method: 'GET',
        urlPath: '/rest/api/2/search',
        queryParameters: {
          jql: { equalTo: jql },
          startAt: { equalTo: String(startAt) },
        },
      },
      response: {
        status: 200,
        headers: JSON_HEADERS,
        jsonBody: { startAt, maxResults: 25, total, issues },
      },
    };
  }

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({
      baseURL,
      storageState: storageStatePath,
    });
    page = await context.newPage();
    jira = await request.newContext({ baseURL: jiraURL });
    await jira.post('/__admin/mappings/reset');
    await ensureCopilotConnected(page.request);
    await page.request.put('/api/consents/COPILOT', {
      headers,
      data: { granted: true },
    });
    await page.request.put('/api/consents/JIRA', {
      headers,
      data: { granted: true },
    });
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

    await stub({
      request: { method: 'GET', urlPath: '/rest/api/2/project/PAGE' },
      response: {
        status: 200,
        headers: JSON_HEADERS,
        jsonBody: { key: 'PAGE' },
      },
    });
    // 017 : la vue Jira lit l'index local ; la synchronisation de Jira y range les 120 tickets
    const now = Date.now();
    const all = Array.from({ length: TOTAL_ISSUES }, (_, i) => {
      const n = TOTAL_ISSUES - i;
      return issue(`PAGE-${n}`, `Ticket ${n}`, at(now, -i), n);
    });
    await stub({
      request: {
        method: 'GET',
        urlPath: '/rest/api/2/search',
        queryParameters: {
          jql: {
            matches: 'project = PAGE( AND .*)? ORDER BY (updated|key) ASC',
          },
        },
      },
      response: {
        status: 200,
        headers: JSON_HEADERS,
        jsonBody: { startAt: 0, maxResults: 200, total: TOTAL_ISSUES, issues: all },
      },
    });
    // 30 tickets plus récents que les spécifications locales, puis 30 plus anciens
    const found = Array.from({ length: FOUND }, (_, i) =>
      issue(
        `PAGE-${500 + i}`,
        `Lettrage ${i}`,
        i < 30 ? at(now, 24 * 365 - i) : at(now, -24 * 365 - i),
      ),
    );
    // le curseur de la recherche reprend Jira à n'importe quel décalage
    for (let startAt = 0; startAt < FOUND; startAt++) {
      await stub(
        search(
          'project = PAGE AND text ~ "lettrage" ORDER BY updated DESC',
          startAt,
          found.slice(startAt, startAt + 25),
          FOUND,
        ),
      );
    }

    const created = await page.request.post('/api/projects', {
      headers,
      data: { name: 'Pagination', jiraProjectKey: 'PAGE' },
    });
    expect(created.status()).toBe(201);
    projectId = (await created.json()).id;
    await page.request.put('/api/projects/current', {
      headers,
      data: { projectId },
    });
    for (let i = 0; i < LOCAL; i++) {
      const need = `Les assistantes doivent pouvoir réserver une salle de réunion, avec le lettrage numéro ${i}.`;
      expect((await page.request.post('/api/sessions', { headers, data: { need } })).status()).toBe(
        201,
      );
    }
  });

  test.afterAll(async () => {
    await page.request.put('/api/projects/current', {
      headers,
      data: { projectId: DEFAULT_PROJECT },
    });
    await page.request.delete(`/api/projects/${projectId}?sessions=DELETE`, {
      headers,
    });
    await page.request.delete('/api/settings/jira', { headers });
    // les parcours suivants (US2) attendent les consentements non encore donnés
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: false } });
    await page.request.put('/api/consents/JIRA', { headers, data: { granted: false } });
    await jira.post('/__admin/mappings/reset');
    await jira.dispose();
    await context.close();
  });

  /** Attend la fin de l'indexation en cours, s'il y en a une. */
  async function indexed(): Promise<void> {
    await expect
      .poll(
        async () => {
          const r = await page.request.get(`/api/projects/${projectId}/indexations/current`);
          return r.status() === 404 ? 'NONE' : (await r.json()).status;
        },
        { timeout: 120_000 },
      )
      .not.toBe('RUNNING');
  }

  test('P10, 017 J1 : la vue Jira lit l’index, triée sur toute la liste, tri gardé', async () => {
    test.setTimeout(180_000);
    await indexed();
    await page.goto('/');
    await page.getByRole('link', { name: 'Tickets Jira' }).click();
    await expect(page.getByRole('heading', { name: 'Tickets Jira' })).toBeVisible();
    // la liste vient de l'index, rempli par la synchronisation de Jira seul ; sa date est vérifiée par us17
    await page.getByRole('button', { name: 'Resynchroniser Jira' }).click();
    await expect(page.getByRole('button', { name: 'Resynchroniser Jira' })).toBeEnabled({ timeout: 120_000 });
    const pager = page.getByRole('navigation', { name: 'Pages de tickets' });
    // 024 : les tickets terminés sont cachés par défaut ; ce parcours trie toute la liste, terminés compris
    await page.getByRole('group', { name: 'Filtres' }).getByRole('checkbox', { name: 'Afficher les tickets terminés' })
      .check();
    await expect(pager).toContainText('1 à 50 sur 120');
    await expect(page.locator('tbody tr')).toHaveCount(50);
    await expect(page.locator('tbody tr').first()).toContainText('PAGE-120');
    await expectNoA11yViolations(page);

    // tri par statut : les « À faire » d'abord, sur toute la liste
    await page.getByRole('button', { name: /^Statut/ }).click();
    await expect(page).toHaveURL(/tri=status&ordre=asc/);
    await expect(page.getByRole('columnheader', { name: /Statut/ })).toHaveAttribute('aria-sort', 'ascending');
    await expect(page.locator('tbody tr').first()).toContainText('À faire');
    await pager.getByRole('button', { name: 'Page suivante' }).click();
    await expect(pager).toContainText('51 à 100 sur 120');
    await expect(page.locator('tbody tr').first()).toContainText('En cours');
    await expect(page.locator('tbody tr').last()).toContainText('Terminé');
    // retour à la vue : le tri et la page sont gardés
    await page.goto('/');
    await page.goBack();
    await expect(pager).toContainText('51 à 100 sur 120');
    await expect(page.getByRole('columnheader', { name: /Statut/ })).toHaveAttribute('aria-sort', 'ascending');
    await expectNoA11yViolations(page);
  });

  test('017 J2 : trier et paginer n’appelle pas Jira, même coupé', async () => {
    await jira.delete('/__admin/requests');
    const down = await cut('project = PAGE ORDER BY updated ASC');
    await page.goto('/');
    await page.getByRole('link', { name: 'Tickets Jira' }).click();
    const pager = page.getByRole('navigation', { name: 'Pages de tickets' });
    await expect(pager).toContainText('sur 120');
    await page.getByRole('button', { name: /^Synchro/ }).click();
    await expect(pager).toContainText('1 à 50 sur 120');
    await pager.getByRole('button', { name: 'Page suivante' }).click();
    await expect(pager).toContainText('51 à 100 sur 120');
    const calls = await jira.post('/__admin/requests/count', { data: { urlPath: '/rest/api/2/search' } });
    expect((await calls.json()).count).toBe(0);
    await jira.delete(`/__admin/mappings/${down}`);
  });

  test('P10 : recherche de 70 résultats, triée par date sur toutes ses pages, sans doublon', async () => {
    // par l'API : toutes les pages, en suivant les curseurs
    const ids: string[] = [];
    const dates: number[] = [];
    let cursor: string | null = null;
    do {
      const query: string = cursor ? `&cursor=${encodeURIComponent(cursor)}` : '';
      const body = await (await page.request.get(`/api/search?q=lettrage${query}`)).json();
      expect(body.items.length).toBeLessThanOrEqual(25);
      for (const item of body.items as { id: string; updatedAt: string }[]) {
        ids.push(item.id);
        dates.push(Date.parse(item.updatedAt));
      }
      cursor = body.nextCursor;
    } while (cursor);
    expect(ids).toHaveLength(FOUND + LOCAL);
    expect(new Set(ids).size).toBe(FOUND + LOCAL);
    expect(dates).toEqual([...dates].sort((a, b) => b - a));
    const forged = await page.request.get('/api/search?q=lettrage&cursor=abc.def');
    expect(forged.status()).toBe(422);

    // à l'écran : positions, puis Jira coupé sur la page suivante
    await page.goto('/sessions?q=lettrage');
    const pager = page.getByRole('navigation', { name: 'Pages de résultats' });
    await expect(pager).toContainText('1 à 25 sur 70');
    await pager.getByRole('button', { name: 'Page suivante' }).click();
    await expect(pager).toContainText('26 à 50 sur 70');
    await expect(page.getByRole('cell', { name: 'spec-writer' }).first()).toBeVisible();
    await expectNoA11yViolations(page);
    await pager.getByRole('button', { name: 'Page précédente' }).click();
    await expect(pager).toContainText('1 à 25 sur 70');

    const down = await cut('project = PAGE AND text ~ "lettrage" ORDER BY updated DESC');
    await pager.getByRole('button', { name: 'Page suivante' }).click();
    await expect(
      page.getByText("Jira n'a pas répondu : seuls les résultats spec-writer sont affichés."),
    ).toBeVisible();
    await expect(pager).toContainText('sur environ');
    await jira.delete(`/__admin/mappings/${down}`);
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(pager).toContainText('26 à 50 sur 70');
  });
});
