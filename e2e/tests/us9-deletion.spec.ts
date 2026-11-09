import { expect, request, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, jiraURL, storageStatePath } from './support/env';

const NEED =
  'Les assistantes doivent pouvoir réserver une salle de réunion pour un comité, et voir les salles libres du jour.';

/** US3 de la feature 002 : suppression locale d'une version et d'une spécification (quickstart P9). */
test.describe.serial('US9 suppression locale', () => {
  let context: BrowserContext;
  let page: Page;
  let jira: APIRequestContext;
  let threeVersions = '';
  let linked = '';
  let linkedName = '';
  const headers = { Origin: baseURL };

  /** Requêtes reçues par Jira, hors recherches plein texte de l'écran de recherche. */
  async function jiraCalls(): Promise<string[]> {
    const all = await (await jira.get('/__admin/requests')).json();
    return (all.requests as { request: { method: string; url: string } }[])
      .map((r) => `${r.request.method} ${r.request.url}`)
      .filter((r) => !r.includes('text+~') && !r.includes('text%20~'));
  }

  async function dialogOpen(): Promise<void> {
    await expect(page.locator('.mat-mdc-dialog-container').last()).toHaveClass(/mdc-dialog--open/);
    await expect(page.locator('.mat-mdc-dialog-container').last()).not.toHaveClass(/mdc-dialog--opening/);
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

    // une spécification de trois versions : rédaction, puis deux modifications assistées acceptées
    let session = await (await page.request.post('/api/sessions', { headers, data: { need: NEED } })).json();
    threeVersions = session.id;
    while (session.currentBatch.length) {
      const q = session.currentBatch[0];
      session = await (
        await page.request.put(`/api/sessions/${threeVersions}/questions/${q.id}/answer`, {
          headers,
          data: { value: q.defaultValue ?? q.suggestions[0] ?? 'Oui' },
        })
      ).json();
    }
    expect((await page.request.post(`/api/sessions/${threeVersions}/versions`, { headers })).status()).toBe(201);
    for (const n of [2, 3]) {
      const proposal = await (
        await page.request.post(`/api/sessions/${threeVersions}/ai-edits`, {
          headers,
          data: { instruction: `Version ${n} E2E-EDIT` },
        })
      ).json();
      const accepted = await page.request.post(`/api/sessions/${threeVersions}/ai-edits/${proposal.proposalId}/accept`, {
        headers,
      });
      expect((await accepted.json()).number).toBe(n);
    }

    // une spécification liée au ticket FACT-1, par l'import
    const imported = await page.request.post('/api/jira/issues/FACT-1/import', { headers, data: {} });
    expect(imported.status()).toBe(201);
    const body = await imported.json();
    linked = body.id;
    linkedName = body.name;
    await jira.delete('/__admin/requests');
  });

  test.afterAll(async () => {
    await page.request.delete(`/api/sessions/${threeVersions}`, { headers });
    await page.request.delete('/api/settings/jira', { headers });
    await jira.dispose();
    await context.close();
  });

  test('P9 : supprimer la version 2 sur trois, après une confirmation nommée ; annuler ne change rien', async () => {
    await page.goto(`/sessions/${threeVersions}/versions/2`);
    await page.getByRole('button', { name: 'Supprimer cette version' }).click();
    const dialog = page.getByRole('dialog', { name: 'Supprimer la version 2 ?' });
    await expect(dialog).toContainText('La version 2 de');
    await expect(dialog).toContainText('Les autres versions gardent leur numéro.');
    await dialogOpen();
    await expectNoA11yViolations(page);
    await dialog.getByRole('button', { name: 'Annuler' }).click();
    await expect(dialog).toHaveCount(0);
    let versions = await (await page.request.get(`/api/sessions/${threeVersions}/versions`)).json();
    expect(versions.map((v: { number: number }) => v.number)).toEqual([1, 2, 3]);

    await page.getByRole('button', { name: 'Supprimer cette version' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Supprimer la version' }).click();
    await expect(page).toHaveURL(new RegExp(`/sessions/${threeVersions}/versions/3$`));
    await expect(page.getByRole('status').filter({ hasText: 'Version 2 supprimée.' })).toBeVisible();
    versions = await (await page.request.get(`/api/sessions/${threeVersions}/versions`)).json();
    expect(versions.map((v: { number: number }) => v.number)).toEqual([1, 3]);
    await expect(page.getByRole('navigation', { name: 'Versions de la spécification' })).not.toContainText('Version 2');
    expect(await jiraCalls()).toEqual([]);
  });

  test('P9 : supprimer une spécification liée à FACT-1 prévient que Jira n’est pas modifié, et n’y touche pas', async () => {
    await page.goto('/sessions');
    await page.getByRole('button', { name: `Supprimer la spécification ${linkedName}` }).click();
    const dialog = page.getByRole('dialog', { name: 'Supprimer cette spécification ?' });
    await expect(dialog).toContainText("Elle est liée au ticket FACT-1 : Jira n'est pas modifié.");
    await dialogOpen();
    await expectNoA11yViolations(page);
    await dialog.getByRole('button', { name: 'Supprimer définitivement' }).click();
    await expect(page.getByText(`Spécification « ${linkedName} » supprimée.`)).toBeVisible();
    await expect(page.getByRole('link', { name: linkedName })).toHaveCount(0);
    expect((await page.request.get(`/api/sessions/${linked}`)).status()).toBe(404);
    const found = await (await page.request.get(`/api/search?q=${encodeURIComponent('contrôle des lignes')}`)).json();
    expect(found.items.map((i: { id: string }) => i.id)).not.toContain(linked);
    expect(await jiraCalls()).toEqual([]);
  });
});
