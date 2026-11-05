import { expect, request, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, jiraURL, storageStatePath } from './support/env';

const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';
const WORKSPACE = join(process.cwd(), 'fixtures', 'c4', 'facturation', 'workspace.dsl');
const BROKEN = join(process.cwd(), 'fixtures', 'c4', 'broken', 'workspace.dsl');
const ADR_005 = `${jiraURL}/antora-knowledge/facturation/adr/adr-005.html`;
/** Besoins du jeu fictif ; les réponses de Copilot sont rejouées depuis fixtures/llm (11 à 25). */
const NEED_AVOIR = 'Saisir un avoir sur une facture émise et en informer la comptabilité.';
const NEED_PORTAIL = 'Le client veut voir ses avoirs partiels dans le portail client.';
const NEED_SYNC = "Saisir un avoir et appeler la comptabilité en synchrone pour l'enregistrer.";
const META = '/rest/api/2/issue/createmeta/FACT/issuetypes';

/**
 * US2 de la feature 002 : connaissance du projet (quickstart P5 à P8) sur le jeu fictif. Jira et
 * le site Antora sont servis par WireMock, le workspace Structurizr est lu dans fixtures/c4, le
 * modèle de recherche est celui des tests (backend/target/test-model).
 */
test.describe.serial('US8 connaissance du projet', () => {
  let context: BrowserContext;
  let page: Page;
  let jira: APIRequestContext;
  let projectId = '';
  let avoirId = '';
  const headers = { Origin: baseURL };

  async function stub(mapping: object): Promise<void> {
    expect((await jira.post('/__admin/mappings', { data: { priority: 1, ...mapping } })).status()).toBe(201);
  }

  async function knowledge(): Promise<{ tickets: number; docSections: number; adrs: number; architectureElements: number }> {
    return (await page.request.get(`/api/projects/${projectId}/knowledge`)).json();
  }

  /** Répond à chaque question par sa valeur par défaut, ou par `value`, jusqu'à la fin du questionnaire. */
  async function answerAll(id: string, value?: string): Promise<void> {
    let session = await (await page.request.get(`/api/sessions/${id}`)).json();
    while (session.currentBatch.length) {
      const q = session.currentBatch[0];
      session = await (
        await page.request.put(`/api/sessions/${id}/questions/${q.id}/answer`, {
          headers,
          data: { value: value ?? q.defaultValue ?? q.suggestions[0] },
        })
      ).json();
    }
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
    const created = await page.request.post('/api/projects', {
      headers,
      data: { name: 'Facturation connaissance', jiraProjectKey: 'FACT' },
    });
    expect(created.status()).toBe(201);
    projectId = (await created.json()).id;
    await page.request.put('/api/projects/current', { headers, data: { projectId } });
  });

  test.afterAll(async () => {
    await page.request.put('/api/projects/current', { headers, data: { projectId: DEFAULT_PROJECT } });
    await page.request.delete(`/api/projects/${projectId}?sessions=DELETE`, { headers });
    await jira.post('/__admin/mappings/reset');
    await jira.dispose();
    await context.close();
  });

  test("P5 : sources déclarées, indexation suivie, quittée et retrouvée", async () => {
    // première indexation : chargement du modèle de recherche et calcul des vecteurs
    test.setTimeout(120_000);
    await page.goto(`/projets/${projectId}/contexte`);
    await page.getByLabel('Adresse du site').fill(`${jiraURL}/antora-knowledge/`);
    await page.getByRole('button', { name: 'Ajouter', exact: true }).click();
    await expect(page.getByRole('button', { name: `Retirer la source ${jiraURL}/antora-knowledge/` })).toBeVisible();

    await page.getByLabel('Chemin du fichier workspace.dsl').fill(WORKSPACE);
    await page.getByLabel('Ownership du projet').fill('facturation');
    await page.getByRole('form', { name: "Modèle d'architecture" }).getByRole('button', { name: 'Enregistrer' }).click();
    await expect(page.getByRole('button', { name: "Retirer le modèle d'architecture" })).toBeVisible();

    // on quitte la page pendant l'indexation, puis on y revient : l'état est retrouvé
    await page.goto('/sessions');
    await page.goto(`/projets/${projectId}/contexte`);
    await expect(page.locator('.counts')).toContainText('6 tickets', { timeout: 60_000 });
    await expect(page.getByRole('button', { name: 'Resynchroniser' })).toBeVisible({ timeout: 60_000 });
    const k = await knowledge();
    expect(k).toMatchObject({ tickets: 6, adrs: 3 });
    expect(k.docSections).toBeGreaterThan(0);
    expect(k.architectureElements).toBeGreaterThan(0);
    await expect(page.locator('.counts')).toContainText('3 ADR');
    await expectNoA11yViolations(page);

    // recherche d'essai : rien ne part vers Jira ni vers Copilot
    await jira.delete('/__admin/requests');
    await page.getByLabel('Rechercher').fill('avoir');
    await page.getByRole('form', { name: "Recherche d'essai" }).getByRole('button', { name: 'Rechercher' }).click();
    await expect(page.getByRole('list', { name: 'Résultats' })).toContainText('FACT-');
    const sent = await (await jira.post('/__admin/requests/count', { data: { urlPattern: '/rest/.*' } })).json();
    expect(sent.count).toBe(0);
  });

  test("P5b : un workspace dont un !include manque donne le fichier et la ligne, et garde le modèle précédent", async () => {
    test.setTimeout(120_000);
    const before = (await knowledge()).architectureElements;
    const broken = await page.request.put(`/api/projects/${projectId}/architecture-source`, {
      headers,
      data: { workspace: BROKEN },
    });
    expect(broken.status()).toBe(200);
    await expect
      .poll(async () => (await (await page.request.get(`/api/projects/${projectId}/indexations/current`)).json()).status, {
        timeout: 60_000,
      })
      .not.toBe('RUNNING');
    const indexation = await (await page.request.get(`/api/projects/${projectId}/indexations/current`)).json();
    const error = indexation.errors.find((e: { kind: string }) => e.kind === 'ARCHITECTURE');
    expect(error.location).toMatch(/workspace\.dsl:4$/);
    expect((await knowledge()).architectureElements).toBe(before);

    // le journal cite le fichier et la ligne, jamais le contenu du workspace
    const journal = JSON.stringify(await (await page.request.get('/api/journal')).json());
    expect(journal).toContain('workspace.dsl:4');
    expect(journal).not.toContain('!include');

    // retour au workspace valide pour la suite
    await page.request.put(`/api/projects/${projectId}/architecture-source`, {
      headers,
      data: { workspace: WORKSPACE, ownership: 'facturation' },
    });
    await expect
      .poll(async () => (await (await page.request.get(`/api/projects/${projectId}/indexations/current`)).json()).status, {
        timeout: 60_000,
      })
      .toBe('SUCCEEDED');
  });

  test('P5c : tickets proches avant les questions ; un ticket fermé donne une story liée « relates to »', async () => {
    await page.goto('/nouveau');
    await page.getByLabel('Votre besoin').fill(NEED_AVOIR);
    await page.getByRole('button', { name: 'Commencer le questionnaire' }).click();
    await expect(page.getByRole('heading', { name: 'Tickets proches' })).toBeVisible();
    avoirId = page.url().split('/').pop()!;
    const fact7 = page.locator('sw-related-issues li').filter({ hasText: 'FACT-7' });
    await expect(fact7).toContainText('Annuler une facture');
    await expect(fact7).toContainText('Terminé');
    await expect(fact7).toContainText('Le ticket annule déjà une facture émise par un avoir total.');
    await expect(page.locator('sw-related-issues li').filter({ hasText: 'FACT-9' })).toContainText('À faire');
    await expect(page.locator('sw-question-card')).toHaveCount(0);
    await expectNoA11yViolations(page);

    await fact7.getByRole('button', { name: 'Lier une nouvelle story à FACT-7' }).click();
    await expect(page.getByText('liée à FACT-7 par « relates to »')).toBeVisible();
    await expect(page.locator('sw-question-card')).toHaveCount(1);
    await answerAll(avoirId);
    expect((await page.request.post(`/api/sessions/${avoirId}/versions`, { headers })).status()).toBe(201);
  });

  test("P6 : ADR en vigueur cité, rubriques Architecture et Efforts supplémentaires calculées par l'outil", async () => {
    await page.goto(`/sessions/${avoirId}/versions/1`);
    const sources = page.locator('dd.source');
    await expect(sources.nth(0)).toContainText('Ticket');
    await expect(sources.nth(0).getByRole('link', { name: /FACT-7/ })).toHaveAttribute('href', `${jiraURL}/browse/FACT-7`);
    await expect(sources.nth(1)).toContainText('ADR');
    await expect(sources.nth(1).getByRole('link', { name: /adr-005/ })).toHaveAttribute('href', ADR_005);
    await expect(sources.nth(2)).toContainText("Élément d'architecture Facturation/Service Avoirs");
    await expect(page.getByText(/adr-002/i)).toHaveCount(0);

    const architecture = page.locator('section[aria-labelledby="architecture"]');
    await expect(architecture).toContainText('Facturation/Service Avoirs (propre au projet)');
    await expect(architecture).toContainText('Comptabilite/Grand Livre (commun)');
    // ce que le workspace ajoute dans un système commun est propre au projet (FR-038)
    await expect(architecture).toContainText('Comptabilite/Ecritures (propre au projet)');
    await expect(architecture).toContainText('Facturation/API Facturation → Comptabilite/Ecritures : flux manquant, à créer');
    // l'échange qui passe par le topic avoirs-emis existe : il n'est pas signalé
    await expect(architecture).toContainText(/Facturation\/Service Avoirs → Comptabilite\/Ecritures :\s+flux existant, via Suez\/avoirs-emis/);
    const efforts = page.locator('section[aria-labelledby="efforts"]');
    await expect(efforts).toContainText('Facturation/Service Avoirs → Comptabilite/Ecritures : traverse des infrastructures');
    await expect(efforts).toContainText('Facturation/API Facturation : plusieurs instances en production');
    await expect(efforts).toContainText('Facturation/Portail Client : aucune instance en production, à déployer');
    await expectNoA11yViolations(page);

    // la création du ticket annonce le lien, puis WireMock reçoit la story et le lien vers FACT-7
    await stub({
      request: { method: 'GET', urlPath: META },
      response: {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        jsonBody: { total: 1, values: [{ id: '10001', name: 'Story', subtask: false }] },
      },
    });
    await stub({
      request: { method: 'GET', urlPath: `${META}/10001` },
      response: {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        jsonBody: {
          total: 1,
          values: [{ fieldId: 'summary', name: 'Résumé', required: true, hasDefaultValue: false, schema: { type: 'string' } }],
        },
      },
    });
    await stub({
      request: { method: 'POST', url: '/rest/api/2/issue' },
      response: {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
        jsonBody: { id: '20020', key: 'FACT-20', self: `${jiraURL}/rest/api/2/issue/20020` },
      },
    });
    await stub({
      request: { method: 'GET', urlPath: '/rest/api/2/issue/FACT-20' },
      response: {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        jsonBody: { key: 'FACT-20', fields: { summary: 'Saisie', description: 'h1. x', updated: '2026-11-04T12:00:00.000+0000' } },
      },
    });
    await jira.delete('/__admin/requests');
    await page.getByRole('button', { name: 'Créer un ticket Jira' }).click();
    // le titre de la boîte change une fois le ticket créé
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText('La story sera liée à FACT-7 par « relates to ».');
    await dialog.getByRole('button', { name: 'Créer le ticket' }).click();
    await expect(dialog).toContainText('La story est liée à FACT-7 par « relates to ».');
    const links = await (await jira.post('/__admin/requests/find', { data: { method: 'POST', url: '/rest/api/2/issueLink' } })).json();
    expect(links.requests).toHaveLength(1);
    expect(links.requests[0].body).toContain('FACT-7');
    expect(links.requests[0].body).toContain('FACT-20');
    await dialog.getByRole('button', { name: 'Fermer' }).click();
  });

  test("P5d : un ticket ouvert se compare, et « C'est la même » bascule sur son import", async () => {
    await page.goto('/nouveau');
    await page.getByLabel('Votre besoin').fill(NEED_PORTAIL);
    await page.getByRole('button', { name: 'Commencer le questionnaire' }).click();
    const fact9 = page.locator('sw-related-issues li').filter({ hasText: 'FACT-9' });
    await expect(fact9).toContainText('Le ticket traite déjà des avoirs partiels.');
    await fact9.getByRole('button', { name: 'Comparer le besoin à FACT-9' }).click();
    const comparison = page.getByRole('group', { name: 'Comparaison avec FACT-9' });
    await expect(comparison).toContainText('Des avoirs partiels sur une facture émise');
    await expect(comparison).toContainText('le ticket les fait émettre par le gestionnaire');
    await expect(page.getByText('Suggestion : la même story.')).toBeVisible();
    await expectNoA11yViolations(page);

    await page.getByRole('button', { name: "C'est la même" }).click();
    await expect(page).toHaveURL(/\/sessions\/[^/]+\/versions\/1$/);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Émettre un avoir partiel');
    await expect(page.getByText('Liée au ticket')).toContainText('FACT-9');
    await expect(page.getByRole('heading', { name: 'Proposition de l’IA' })).toBeVisible();
    await expect(page.locator('sw-ai-edit-panel')).toContainText('Voir ses avoirs');
  });

  test("P7 : la fiche générale l'emporte sur les ADR ; régénérer sans changement réutilise la version", async () => {
    await page.goto(`/projets/${projectId}/contexte`);
    await page.getByLabel('Fiche générale (Markdown)').fill("## Décisions\n- L'ADR-005 n'est plus appliqué.");
    await page.getByRole('button', { name: 'Enregistrer la fiche' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'Fiche générale enregistrée.' })).toBeVisible();
    await page.getByRole('button', { name: 'Aperçu' }).click();
    await expect(page.getByLabel('Aperçu de la fiche générale')).toContainText("L'ADR-005 n'est plus appliqué.");
    await expectNoA11yViolations(page);

    const changed = await page.request.post(`/api/sessions/${avoirId}/versions`, { headers });
    expect(changed.status()).toBe(201);
    const v2 = await changed.json();
    expect(v2.number).toBe(2);
    expect(JSON.stringify(v2.useCases)).not.toContain('adr-005');
    expect(v2.useCases[0].source).toMatchObject({ kind: 'PROJECT_CONTEXT' });
    const same = await page.request.post(`/api/sessions/${avoirId}/versions`, { headers });
    expect(same.status()).toBe(200);
    expect((await same.json()).number).toBe(2);
    await page.request.put(`/api/projects/${projectId}/context`, { headers, data: { markdown: '' } });
  });

  test("P8 : un besoin contraire à l'ADR-005 est signalé pendant les questions et dans la rubrique « Écarts aux ADR »", async () => {
    await page.goto('/nouveau');
    await page.getByLabel('Votre besoin').fill(NEED_SYNC);
    await page.getByRole('button', { name: 'Commencer le questionnaire' }).click();
    const card = page.locator('sw-question-card');
    await expect(card).toHaveCount(1);
    await expect(card).toContainText("l'ADR-005 impose une notification asynchrone");
    const id = page.url().split('/').pop()!;
    await answerAll(id, "J'assume l'écart");
    expect((await page.request.post(`/api/sessions/${id}/versions`, { headers })).status()).toBe(201);

    await page.goto(`/sessions/${id}/versions/1`);
    const deviations = page.locator('section[aria-labelledby="deviations"]');
    await expect(deviations.getByRole('heading', { name: 'Écarts aux ADR' })).toBeVisible();
    await expect(deviations.getByRole('link', { name: /adr-005/ })).toHaveAttribute('href', ADR_005);
    await expect(deviations).toContainText('La comptabilité est notifiée de façon asynchrone');
    await expect(deviations).toContainText('Le besoin appelle la comptabilité en synchrone.');
    await expectNoA11yViolations(page);
  });

  test('P8a : les sous-tâches de la story ne sont pas des travaux concurrents ; celles que la version touche sont signalées', async () => {
    // FACT-40 et ses sous-tâches s'ajoutent aux tickets du jeu, le temps de ce scénario
    const base = JSON.parse(readFileSync(join(process.cwd(), 'fixtures', 'jira', 'mappings', 'knowledge.json'), 'utf8'));
    const issue = (key: string, summary: string, description: string, status: string, category: string, extra = {}) => ({
      key,
      fields: {
        summary,
        description,
        status: { name: status, statusCategory: { key: category } },
        issuetype: { name: key === 'FACT-40' || key === 'FACT-50' ? 'Story' : 'Sous-tâche' },
        updated: '2026-10-20T09:00:00.000+0000',
        subtasks: [],
        issuelinks: [],
        ...extra,
      },
    });
    const added = [
      issue('FACT-40', 'Payer une facture en ligne',
        "En tant qu'acheteur, je veux régler une facture émise par carte bancaire et recevoir un reçu (E2E-SUB).",
        'En cours', 'indeterminate', {
          subtasks: [{ key: 'FACT-41' }, { key: 'FACT-42' }, { key: 'FACT-43' }],
          issuelinks: [{ type: { name: 'Blocks', outward: 'blocks' }, outwardIssue: { key: 'FACT-50' } }],
        }),
      issue('FACT-41', 'Débit de la carte bancaire',
        'Appeler le prestataire de paiement pour débiter la carte bancaire du montant dû.', 'En cours', 'indeterminate',
        { parent: { key: 'FACT-40' } }),
      issue('FACT-42', 'Reçu de paiement par courriel', 'Envoyer un reçu par courriel une fois le paiement accepté.',
        'Terminé', 'done', { parent: { key: 'FACT-40' } }),
      issue('FACT-43', 'Mise à jour des dépendances du build', 'Monter les versions des bibliothèques Maven et npm du projet.',
        'À faire', 'new', { parent: { key: 'FACT-40' } }),
      issue('FACT-50', 'Rapprochement bancaire des paiements',
        'La comptabilité rapproche chaque jour les paiements reçus avec les relevés bancaires.', 'À faire', 'new', {
          issuelinks: [{ type: { name: 'Blocks', inward: 'is blocked by' }, inwardIssue: { key: 'FACT-40' } }],
        }),
    ];
    const issues = [...base.mappings[1].response.jsonBody.issues, ...added];
    await stub({
      request: { method: 'GET', urlPath: '/rest/api/2/search', queryParameters: { jql: { matches: 'project = FACT( AND updated >= .*)? ORDER BY updated ASC' } } },
      response: { status: 200, headers: { 'Content-Type': 'application/json' }, jsonBody: { startAt: 0, maxResults: 50, total: issues.length, issues } },
    });
    // la liste des clés du fichier de correspondances est en priorité 0 : celle-ci la remplace
    await stub({
      priority: 0,
      request: { method: 'GET', urlPath: '/rest/api/2/search', queryParameters: { fields: { equalTo: 'key' } } },
      response: {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        jsonBody: { startAt: 0, maxResults: 1000, total: issues.length, issues: issues.map((i) => ({ key: i.key })) },
      },
    });
    for (const i of added) {
      await stub({
        request: { method: 'GET', urlPath: `/rest/api/2/issue/${i.key}`, queryParameters: { fields: { equalTo: 'status,updated' } } },
        response: { status: 200, headers: { 'Content-Type': 'application/json' }, jsonBody: { key: i.key, fields: { status: i.fields.status, updated: i.fields.updated } } },
      });
    }
    await stub({
      request: { method: 'GET', urlPath: '/rest/api/2/issue/FACT-40' },
      response: { status: 200, headers: { 'Content-Type': 'application/json' }, jsonBody: added[0] },
    });
    const indexation = async () =>
      (await page.request.get(`/api/projects/${projectId}/indexations/current`)).json().catch(() => ({ status: 'NONE' }));
    await expect.poll(async () => (await indexation()).status, { timeout: 60_000 }).not.toBe('RUNNING');
    expect((await page.request.post(`/api/projects/${projectId}/indexations`, { headers, data: { mode: 'FULL' } })).status()).toBe(202);
    // l'état courant reste celui de l'indexation précédente tant que la nouvelle n'a pas démarré
    await expect.poll(async () => (await knowledge()).tickets, { timeout: 60_000 }).toBe(issues.length);
    await expect.poll(async () => (await indexation()).status, { timeout: 60_000 }).toBe('SUCCEEDED');

    // la story importée, puis modifiée : UC-01 change, UC-02 disparaît
    const imported = await page.request.post('/api/jira/issues/FACT-40/import', { headers, data: {} });
    expect(imported.status(), await imported.text()).toBe(201);
    const id = (await imported.json()).id;
    const proposed = await page.request.post(`/api/sessions/${id}/ai-edits`, {
      headers,
      data: { instruction: 'Paiement en trois fois, sans reçu E2E-SUB-TROIS-FOIS' },
    });
    expect(proposed.ok(), await proposed.text()).toBe(true);
    const proposal = await proposed.json();
    const accepted = await page.request.post(`/api/sessions/${id}/ai-edits/${proposal.proposalId}/accept`, { headers });
    expect(accepted.ok(), await accepted.text()).toBe(true);
    expect((await accepted.json()).number).toBe(2);
    await jira.delete('/__admin/requests');

    await page.goto(`/sessions/${id}/versions/2`);
    const panel = page.locator('section[aria-labelledby="subtasks-title"]');
    await expect(panel.getByRole('heading', { name: 'Sous-tâches de FACT-40' })).toBeVisible();
    await expect(panel).toContainText('À revoir');
    await expect(panel.locator('li').filter({ hasText: 'FACT-41' })).toContainText('UC-01 a changé.');
    await expect(panel.locator('li').filter({ hasText: 'FACT-41' })).toContainText('Probable');
    await expect(panel).toContainText('Livrées, à reprendre');
    await expect(panel.locator('li').filter({ hasText: 'FACT-42' })).toContainText('UC-02 a été supprimé.');
    await expect(panel.getByText('Non rattachées (1)')).toBeVisible();
    await expect(panel.locator('details li').filter({ hasText: 'FACT-43' })).toBeHidden();
    await expect(page.locator('body')).not.toContainText('travaux en cours');
    await expectNoA11yViolations(page);

    // la rubrique relit les statuts, sans jamais écrire dans Jira (FR-047)
    const calls = (await (await jira.get('/__admin/requests')).json()).requests as { request: { method: string; url: string } }[];
    expect(calls.map((c) => c.request.method).filter((m) => m !== 'GET')).toEqual([]);
    expect(calls.map((c) => c.request.url).join('\n')).toContain('/rest/api/2/issue/FACT-41');
  });
});
