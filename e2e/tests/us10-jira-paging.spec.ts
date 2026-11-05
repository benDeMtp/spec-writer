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

/** Ticket Jira au format de /rest/api/2/search. */
function issue(key: string, summary: string, updated: string) {
  return {
    key,
    fields: {
      summary,
      status: { name: 'À faire' },
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
    // l'index de connaissance du projet reste vide : la synchronisation ne trouve rien
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
        jsonBody: { startAt: 0, maxResults: 50, total: 0, issues: [] },
      },
    });
    const now = Date.now();
    for (let startAt = 0; startAt < TOTAL_ISSUES; startAt += 25) {
      const issues = Array.from({ length: 25 }, (_, i) => {
        const n = TOTAL_ISSUES - startAt - i;
        return issue(`PAGE-${n}`, `Ticket ${n}`, at(now, -(startAt + i)));
      });
      await stub(search('project = PAGE ORDER BY updated DESC', startAt, issues, TOTAL_ISSUES));
    }
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

  test('P10 : tickets du projet, page suivante puis précédente ; Jira coupé, la page reste', async () => {
    await page.goto('/');
    await page.getByRole('link', { name: 'Tickets Jira' }).click();
    await expect(page.getByRole('heading', { name: 'Tickets Jira' })).toBeVisible();
    const pager = page.getByRole('navigation', { name: 'Pages de tickets' });
    await expect(pager).toContainText('1 à 25 sur 120');
    await expect(page.locator('tbody tr')).toHaveCount(25);
    await expect(page.locator('tbody tr').first()).toContainText('PAGE-120');
    await expectNoA11yViolations(page);

    await pager.getByRole('button', { name: 'Page suivante' }).click();
    await expect(pager).toContainText('26 à 50 sur 120');
    await expect(page.locator('tbody tr').first()).toContainText('PAGE-95');
    await pager.getByRole('button', { name: 'Page précédente' }).click();
    await expect(pager).toContainText('1 à 25 sur 120');

    const down = await cut('project = PAGE ORDER BY updated DESC');
    await pager.getByRole('button', { name: 'Page suivante' }).click();
    await expect(page.getByRole('alert')).toContainText('La page affichée est conservée.');
    await expect(pager).toContainText('1 à 25 sur 120');
    await expect(page.locator('tbody tr').first()).toContainText('PAGE-120');
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
    await page.goto('/?q=lettrage');
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
