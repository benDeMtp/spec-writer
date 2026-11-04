import { expect, request, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, jiraURL, storageStatePath } from './support/env';

const NEED =
  'Les employés doivent pouvoir réserver une place de parking pour une journée, et être prévenus quand le parking ' +
  'est complet.';
const META = '/rest/api/2/issue/createmeta/PROJ/issuetypes';

/** US5 : création d'un ticket Jira depuis une spécification (V11). */
test.describe.serial('US5 création du ticket Jira', () => {
  let context: BrowserContext;
  let page: Page;
  let jira: APIRequestContext;
  let versionPath = '';
  const headers = { Origin: baseURL };

  async function stub(mapping: object): Promise<void> {
    expect((await jira.post('/__admin/mappings', { data: { priority: 1, ...mapping } })).status()).toBe(201);
  }

  async function creations(): Promise<number> {
    const r = await jira.post('/__admin/requests/count', { data: { method: 'POST', url: '/rest/api/2/issue' } });
    return (await r.json()).count;
  }

  async function dialogOpen(): Promise<void> {
    // axe mesure des couleurs en transition tant que l'animation d'ouverture n'est pas finie
    await expect(page.locator('.mat-mdc-dialog-container').last()).toHaveClass(/mdc-dialog--open/);
    await expect(page.locator('.mat-mdc-dialog-container').last()).not.toHaveClass(/mdc-dialog--opening/);
  }

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    jira = await request.newContext({ baseURL: jiraURL });
    await jira.post('/__admin/mappings/reset');
    await jira.delete('/__admin/requests');
    await ensureCopilotConnected(page.request);
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: true } });
    await page.request.delete('/api/settings/jira', { headers });
    await page.request.put('/api/consents/JIRA', { headers, data: { granted: false } });

    let session = await (await page.request.post('/api/sessions', { headers, data: { need: NEED } })).json();
    const id = session.id as string;
    let first = true;
    while (session.currentBatch.length) {
      const q = session.currentBatch[0];
      const data = first ? { skip: true } : { value: q.defaultValue ?? q.suggestions[0] ?? 'Oui' };
      first = false;
      session = await (await page.request.put(`/api/sessions/${id}/questions/${q.id}/answer`, { headers, data })).json();
    }
    expect((await page.request.post(`/api/sessions/${id}/versions`, { headers })).status()).toBe(201);
    versionPath = `/sessions/${id}/versions/1`;

    await stub({
      request: { method: 'GET', urlPath: META },
      response: {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        jsonBody: {
          total: 3,
          values: [
            { id: '10002', name: 'Bug', subtask: false },
            { id: '10001', name: 'Story', subtask: false },
            { id: '10003', name: 'Sous-tâche', subtask: true },
          ],
        },
      },
    });
    await stub({
      request: { method: 'GET', urlPath: `${META}/10001` },
      response: {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        jsonBody: {
          total: 2,
          values: [
            { fieldId: 'summary', name: 'Résumé', required: true, hasDefaultValue: false, schema: { type: 'string' } },
            {
              fieldId: 'customfield_10200',
              name: 'Équipe',
              required: true,
              hasDefaultValue: false,
              schema: { type: 'option' },
              allowedValues: [
                { id: '1', value: 'Alpha' },
                { id: '2', value: 'Bêta' },
              ],
            },
          ],
        },
      },
    });
    await stub({
      request: { method: 'GET', urlPath: `${META}/10002` },
      response: { status: 200, headers: { 'Content-Type': 'application/json' }, jsonBody: { total: 0, values: [] } },
    });
    await stub({
      request: { method: 'POST', url: '/rest/api/2/issue' },
      response: {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
        jsonBody: { id: '20001', key: 'PROJ-200', self: `${jiraURL}/rest/api/2/issue/20001` },
      },
    });
    await stub({
      request: { method: 'GET', urlPath: '/rest/api/2/issue/PROJ-200' },
      response: {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        jsonBody: {
          key: 'PROJ-200',
          fields: { summary: 'Réservation des places de parking', description: 'h1. x', updated: '2026-10-30T12:00:00.000+0000' },
        },
      },
    });
  });

  test.afterAll(async () => {
    await jira.post('/__admin/mappings/reset');
    await jira.dispose();
    await context.close();
  });

  test("Jira non configuré : le bouton est désactivé et l'écran explique pourquoi", async () => {
    await page.goto(versionPath);
    const create = page.getByRole('button', { name: 'Créer un ticket Jira' });
    await expect(create).toBeDisabled();
    await expect(create).toHaveAccessibleDescription(/renseignez le serveur, le projet et le jeton Jira/);
    await expectNoA11yViolations(page);
  });

  test('US5-1 et US5-2 : aperçu avec Story par défaut, rien de créé avant confirmation', async () => {
    const saved = await page.request.put('/api/settings/jira', {
      headers,
      data: { serverUrl: jiraURL, defaultProjectKey: 'PROJ', acceptInsecureHttp: true, token: 'e2e-good-token' },
    });
    expect(saved.status()).toBe(200);
    await page.reload();
    await page.getByRole('button', { name: 'Créer un ticket Jira' }).click();
    // consentement Jira demandé avant le premier appel
    await page.getByRole('button', { name: 'Accepter' }).click();
    const dialog = page.getByRole('dialog', { name: /Créer un ticket Jira/ });
    await expect(dialog.getByText('PROJ', { exact: true })).toBeVisible();
    await expect(dialog.getByText('Réservation des places de parking', { exact: true })).toBeVisible();
    await expect(dialog.getByRole('combobox', { name: 'Type de ticket' })).toContainText('Story');
    await expect(dialog.getByLabel('Description envoyée à Jira')).toContainText('h1. Réservation des places de parking');
    await expect(dialog.getByRole('button', { name: 'Créer le ticket' })).toBeDisabled();
    await dialogOpen();
    await expectNoA11yViolations(page);
    expect(await creations()).toBe(0);
  });

  test('US5-3 : changer de type recharge les champs obligatoires', async () => {
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('combobox', { name: 'Type de ticket' }).click();
    await page.getByRole('option', { name: 'Bug' }).click();
    // la liste précédente doit être refermée, sinon le second clic la vise pendant sa fermeture
    await expect(page.getByRole('listbox')).toHaveCount(0);
    await expect(dialog.getByRole('combobox', { name: 'Équipe' })).toHaveCount(0);
    await expect(dialog.getByRole('button', { name: 'Créer le ticket' })).toBeEnabled();
    await dialog.getByRole('combobox', { name: 'Type de ticket' }).click();
    await page.getByRole('option', { name: 'Story' }).click();
    await expect(dialog.getByRole('combobox', { name: 'Équipe' })).toBeVisible();
    await expect(dialog.getByRole('button', { name: 'Créer le ticket' })).toBeDisabled();
  });

  test('US5-4 : le ticket est créé, sa clé et son lien sont affichés, la session lui est liée', async () => {
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('combobox', { name: 'Équipe' }).click();
    await page.getByRole('option', { name: 'Bêta' }).click();
    await dialog.getByRole('button', { name: 'Créer le ticket' }).click();
    const link = dialog.getByRole('link', { name: /PROJ-200/ });
    await expect(link).toHaveAttribute('target', '_blank');
    await expect(link).toHaveAttribute('rel', /noopener/);
    expect(await creations()).toBe(1);
    const sent = await (await jira.post('/__admin/requests/find', { data: { method: 'POST', url: '/rest/api/2/issue' } })).json();
    const fields = JSON.parse(sent.requests[0].body).fields;
    expect(fields.issuetype).toEqual({ id: '10001' });
    expect(fields.customfield_10200).toEqual({ id: '2' });
    expect(fields.description).toContain('h1. Réservation des places de parking');
    await dialog.getByRole('button', { name: 'Fermer' }).click();
    await expect(page.getByText('Ticket PROJ-200 créé depuis la version 1.')).toBeVisible();
    await expect(page.getByText('Liée au ticket')).toContainText('PROJ-200');
    await expect(page.getByRole('button', { name: 'Envoyer vers Jira' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Créer un ticket Jira' })).toHaveCount(0);
  });
});
