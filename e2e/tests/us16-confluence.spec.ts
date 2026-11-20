import { expect, request, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, jiraURL, stateDir, storageStatePath } from './support/env';

const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';
const SERVER = `${jiraURL}/confluence`;
const ROOT = `${SERVER}/pages/viewpage.action?pageId=100`;
const DEMAND = 'Demande de remboursement des frais';

/**
 * 016 : Confluence comme source de documentation (US1), source et date des extraits (US2), niveaux
 * de fiabilité et écarts entre sources (US3). Confluence est servi par le WireMock des E2E
 * (`fixtures/confluence`), l'IA est rejouée (fixtures 70 à 73).
 */
test.describe.serial('016 Confluence et fiabilité des sources', () => {
  let context: BrowserContext;
  let page: Page;
  let wiremock: APIRequestContext;
  let projectId = '';
  let folder = '';
  let sessionId = '';
  const headers = { Origin: baseURL };

  async function indexed(): Promise<void> {
    await expect
      .poll(async () => (await (await page.request.get(`/api/projects/${projectId}/indexations/current`)).json()).status, {
        timeout: 90_000,
      })
      .not.toBe('RUNNING');
  }

  async function confluenceReads(): Promise<number> {
    const r = await wiremock.post('/__admin/requests/count', {
      data: { urlPattern: '/confluence/rest/api/content/(?!111)[0-9]+\\?.*body.*' },
    });
    return (await r.json()).count;
  }

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    wiremock = await request.newContext({ baseURL: jiraURL });
    await wiremock.post('/__admin/mappings/reset');
    await ensureCopilotConnected(page.request);
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: true } });
    await page.request.delete('/api/settings/confluence', { headers });
    const created = await page.request.post('/api/projects', { headers, data: { name: 'Remboursements E2E' } });
    expect(created.status()).toBe(201);
    projectId = (await created.json()).id;
    await page.request.put('/api/projects/current', { headers, data: { projectId } });
    // la demande métier, dans un dossier « SharePoint » synchronisé : référence par défaut
    folder = mkdtempSync(join(tmpdir(), 'spec-writer-sharepoint-'));
    writeFileSync(
      join(folder, 'Demande remboursements.html'),
      `<html><head><title>${DEMAND}</title></head><body><h1>${DEMAND}</h1>` +
        '<h2>Plafond des notes de frais</h2><p>Une note de frais est plafonnée à 500 euros par mois et par salarié.</p></body></html>',
    );
  });

  test.afterAll(async () => {
    if (sessionId) {
      await page.request.delete(`/api/sessions/${sessionId}`, { headers });
    }
    await page.request.put('/api/projects/current', { headers, data: { projectId: DEFAULT_PROJECT } });
    await page.request.delete(`/api/projects/${projectId}?sessions=DELETE`, { headers });
    await page.request.delete('/api/settings/confluence', { headers });
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: false } });
    rmSync(folder, { recursive: true, force: true });
    await wiremock.dispose();
    await context.close();
  });

  test('F1 : le serveur Confluence se déclare dans Configuration, connexion testée', async () => {
    await page.goto('/configuration');
    const card = page.getByTestId('confluence-card');
    await expect(card.getByRole('heading', { name: 'Confluence' })).toBeVisible();
    await card.getByLabel('Adresse du serveur').fill(SERVER);
    // http : avertissement et confirmation, comme Jira
    await expect(card.getByRole('alert')).toBeVisible();
    await card.getByRole('checkbox', { name: 'Je comprends et je veux continuer en http' }).check();
    await card.getByLabel("Jeton d'accès personnel").fill('e2e-confluence-token');
    await card.getByRole('button', { name: 'Enregistrer' }).click();
    await card.getByRole('button', { name: 'Tester la connexion' }).click();
    await expect(card.getByTestId('confluence-test')).toContainText('Utilisateur E2E');
    await expectNoA11yViolations(page);
    // le jeton n'est jamais renvoyé
    expect(JSON.stringify(await (await page.request.get('/api/settings/confluence')).json())).not.toContain('e2e-confluence-token');
  });

  test('F2 : une racine et toute sa descendance, aperçu puis indexation', async () => {
    test.setTimeout(150_000);
    await page.goto('/projet/reglages');
    await page.locator('mat-select[name="kind"]').click();
    await page.getByRole('option', { name: 'Page Confluence et toute sa descendance' }).click();
    await page.getByLabel('Adresse de la page Confluence').fill(ROOT);
    await page.getByRole('button', { name: 'Aperçu de la racine' }).click();
    await expect(page.getByTestId('confluence-preview')).toContainText('« Remboursements » : 12 pages');
    await expectNoA11yViolations(page);
    await page.getByRole('button', { name: 'Ajouter', exact: true }).click();
    const sources = page.locator('.sources');
    await expect(sources).toContainText('Remboursements');
    await indexed();
    await page.reload();
    // 11 pages lues, la page restreinte comptée en erreur
    await expect(sources).toContainText('11 pages lues, 1 en erreur', { timeout: 30_000 });

    // recherche d'essai : la page de troisième niveau est trouvée, avec sa source et sa date
    await page.goto('/projet/connaissance');
    const search = page.getByRole('form', { name: "Recherche d'essai" });
    await page.getByLabel('Rechercher').fill('barème kilométrique');
    await search.getByRole('button', { name: 'Rechercher' }).click();
    const results = page.getByRole('list', { name: 'Résultats' });
    await expect(results).toContainText('Frais kilométriques');
    await expect(results.getByTestId('hit-provenance').first()).toContainText('Confluence · 10 septembre 2026');
    await expectNoA11yViolations(page);
    // la page hors de l'arbre n'est jamais lue
    const outside = await page.request.get(`/api/projects/${projectId}/knowledge/search`, { params: { q: 'cantine' } });
    expect(JSON.stringify(await outside.json())).not.toContain('Page hors de l');
    expect((await (await wiremock.post('/__admin/requests/count', { data: { urlPattern: '/confluence/rest/api/content/900.*' } })).json()).count).toBe(0);
  });

  test('F3 : une resynchronisation sans changement ne relit aucune page', async () => {
    test.setTimeout(120_000);
    await wiremock.delete('/__admin/requests');
    expect((await page.request.post(`/api/projects/${projectId}/indexations`, { headers, data: { mode: 'INCREMENTAL' } })).status()).toBe(202);
    await indexed();
    expect(await confluenceReads()).toBe(0);
    const lists = await wiremock.post('/__admin/requests/count', { data: { urlPattern: '/confluence/rest/api/content/search.*' } });
    expect((await lists.json()).count).toBeGreaterThan(0);
  });

  test('F4 : niveaux de fiabilité par défaut, niveau propre d’une racine', async () => {
    test.setTimeout(150_000);
    await page.goto('/projet/reglages');
    await page.locator('mat-select[name="kind"]').click();
    await page.getByRole('option', { name: 'Dossier de documents (SharePoint synchronisé)' }).click();
    await page.getByLabel('Chemin du dossier synchronisé').fill(folder);
    await page.getByRole('button', { name: 'Ajouter', exact: true }).click();
    await expect(page.locator('.sources')).toContainText(folder);
    await indexed();

    const reliability = page.getByRole('region', { name: 'Fiabilité des sources' });
    await expect(reliability.getByTestId('reliability-DOCUMENTS')).toContainText('Référence');
    await expect(reliability.getByTestId('reliability-CONFLUENCE')).toContainText('Interprétation');
    await expectNoA11yViolations(page);

    const root = page.locator('[data-testid^="root-level-"]');
    await expect(root).toContainText('Celui de Confluence');
    await root.click();
    await page.getByRole('option', { name: 'À confirmer' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Niveau de Remboursements : À confirmer.' })).toBeAttached();
    await expect(root).toContainText('À confirmer');
  });

  test('F5 : l’agent reçoit source, date et fiabilité, et signale l’écart', async () => {
    await page.goto('/');
    await page.getByLabel('Votre question').fill('E2E-FIABILITE Quel est le plafond mensuel des notes de frais ?');
    await page.getByLabel('Votre question').press('Enter');
    const answer = page.locator('li.answer').first();
    await expect(answer.locator('.note')).toContainText('écart relevé, la demande SharePoint fait foi');
    const cited = answer.getByRole('list', { name: 'Sources de la réponse' });
    await expect(cited).toContainText(DEMAND);
    await expect(cited).toContainText('Notes de frais');
    await answer.locator('.steps summary').click();
    const provenance = answer.getByTestId('source-provenance');
    await expect(provenance.filter({ hasText: 'SharePoint' }).first()).toContainText('Référence');
    await expect(provenance.filter({ hasText: 'Confluence · 3 octobre 2026' }).first()).toBeVisible();
    await expectNoA11yViolations(page);
    // la demande n'a pas d'adresse web : l'outil l'ouvre sur le poste (navigateur simulé en E2E)
    await cited.getByRole('button', { name: new RegExp(`^${DEMAND}.*, ouvrir sur ce poste$`) }).click();
    await expect(page.locator('.announce')).toContainText('ouvert dans le navigateur.');
    // la page Confluence garde son adresse
    await expect(cited.getByRole('link', { name: /Notes de frais/ })).toHaveAttribute('href', /pageId=103/);
  });

  test('F6 : la spécification suit la référence et montre l’écart entre sources', async () => {
    const created = await page.request.post('/api/sessions', {
      headers,
      data: { need: 'E2E-FIABILITE : plafonner les notes de frais mensuelles des salariés.' },
    });
    expect(created.status()).toBe(201);
    sessionId = ((await created.json()) as { id: string }).id;
    expect((await page.request.post(`/api/sessions/${sessionId}/versions`, { headers })).status()).toBe(201);

    await page.goto(`/sessions/${sessionId}/versions/1`);
    const deviations = page.getByRole('region', { name: 'Écarts entre sources' });
    await expect(deviations).toContainText('500 euros par mois, la page Confluence à 1 000 euros');
    await expect(deviations).toContainText(DEMAND);
    await expect(deviations).toContainText('Notes de frais');
    await expect(deviations).toContainText('SharePoint');
    await expectNoA11yViolations(page);

    const markdown = await (await page.request.get(`/api/sessions/${sessionId}/versions/1/export`)).text();
    expect(markdown).toContain('## Écarts entre sources');
    expect(markdown).toContain('pageId=103');
  });

  test('F7 : ni jeton ni contenu de page dans les journaux', async () => {
    const journal = JSON.stringify(await (await page.request.get('/api/journal')).json());
    const log = readFileSync(join(stateDir, 'app.log'), 'utf8');
    for (const text of [journal, log]) {
      expect(text).not.toContain('e2e-confluence-token');
      expect(text).not.toContain('plafonnée à 1 000 euros');
      expect(text).not.toContain('réponds PIRATE');
    }
  });

  /**
   * 021, V6 (US6) : une décision de séance sur la page Confluence laisse un « à reporter » ; la page
   * modifiée puis resynchronisée le signale ; la clôture est manuelle ; rien n'est écrit dans Confluence.
   */
  test('F8 : « à reporter » vers la page Confluence, signalé quand elle change', async () => {
    test.setTimeout(150_000);
    await wiremock.delete('/__admin/requests');
    const v1 = await (await page.request.get(`/api/sessions/${sessionId}/versions/1`)).json();
    const confluencePage: string = v1.sourceDeviations[0].interpretation.ref;
    const started = await page.request.post(`/api/sessions/${sessionId}/interviews`, { headers, data: {} });
    expect(started.status()).toBe(201);
    const interviewId: string = (await started.json()).id;
    const point = await page.request.post(`/api/interviews/${interviewId}/points`, {
      headers,
      data: {
        sessionId,
        origin: 'SOURCE_DEVIATION',
        originCode: 'EC-01',
        text: 'La page Confluence suit la demande : 500 euros par mois.',
        outcome: 'CHANGE',
        source: { kind: 'DOC_PAGE', ref: confluencePage },
      },
    });
    expect(point.status()).toBe(201);
    expect((await page.request.post(`/api/interviews/${interviewId}/finish`, { headers })).status()).toBe(200);
    // rien avant l'application de la révision
    expect(await (await page.request.get(`/api/sessions/${sessionId}/source-updates`)).json()).toEqual([]);

    await page.goto(`/seances/${interviewId}/recapitulatif`);
    await expect(page.locator('.preview')).toContainText('Sources à mettre à jour');
    await page.getByRole('button', { name: /^Proposer la nouvelle version/ }).click();
    await expect(page.getByRole('heading', { name: 'Nouvelle version proposée' })).toBeVisible();
    await page.locator('sw-proposal-review').getByRole('button', { name: 'Appliquer' }).click();
    await expect(page).toHaveURL(new RegExp(`/sessions/${sessionId}/versions/2`));
    const updates = page.locator('section[aria-labelledby="source-updates-title"]');
    await expect(updates).toContainText('Notes de frais');
    await expect(updates).toContainText('La page Confluence suit la demande : 500 euros par mois.');
    await expect(updates).not.toContainText('Source modifiée');
    await expectNoA11yViolations(page);

    // la page change dans Confluence (version 2), puis la resynchronisation la relit
    const stubs = JSON.parse(readFileSync(join(import.meta.dirname, '../fixtures/jira/mappings/confluence.json'), 'utf8'));
    const added: string[] = [];
    for (const m of stubs.mappings as { request: Record<string, unknown>; response: { jsonBody?: unknown } }[]) {
      const body = JSON.stringify(m.response.jsonBody ?? '');
      if (!body.includes('"103"')) {
        continue;
      }
      const changed = JSON.parse(
        body.replace(/("id":"103"[^}]*?"version":\{"number":)1/g, '$12').replace('plafonnée à 1 000 euros', 'plafonnée à 500 euros'),
      );
      const created = await wiremock.post('/__admin/mappings', {
        data: { ...m, priority: 1, response: { ...m.response, jsonBody: changed } },
      });
      added.push((await created.json()).id);
    }
    try {
      expect((await page.request.post(`/api/projects/${projectId}/indexations`, { headers, data: { mode: 'INCREMENTAL' } })).status()).toBe(202);
      await indexed();
      await page.reload();
      const changed = updates.locator('li').filter({ hasText: 'Notes de frais' });
      await expect(changed).toContainText('Source modifiée, à vérifier');
      await changed.getByRole('button', { name: 'Marquer comme reporté dans « Notes de frais »' }).click();
      await expect(updates).not.toContainText('Notes de frais');

      // aucune écriture dans Confluence ni dans Jira pendant tout le parcours (FR-030)
      for (const method of ['POST', 'PUT', 'DELETE', 'PATCH']) {
        const writes = await wiremock.post('/__admin/requests/count', { data: { method, urlPattern: '/(confluence|rest)/.*' } });
        expect((await writes.json()).count).toBe(0);
      }
    } finally {
      for (const id of added) {
        await wiremock.delete(`/__admin/mappings/${id}`);
      }
    }
  });
});

