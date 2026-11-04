import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, storageStatePath } from './support/env';

const NEED =
  'Les assistantes doivent pouvoir réserver une salle de réunion pour un comité de direction, et voir les salles libres.';

/** US5 de la feature 002 : statut d'implémentation, verrou et évolution (quickstart P11). */
test.describe.serial('US11 statut et agents', () => {
  let context: BrowserContext;
  let page: Page;
  let specId = '';
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
      "Faire évoluer avec l'IA",
      'Créer un ticket Jira',
      'Supprimer cette version',
    ]) {
      await expect(page.getByRole('button', { name })).toBeDisabled();
    }
    await expectNoA11yViolations(page);

    // l'API refuse aussi : génération, modification assistée, suppression de version
    for (const [method, url] of [
      ['POST', `/api/sessions/${specId}/versions`],
      ['POST', `/api/sessions/${specId}/ai-edits`],
      ['DELETE', `/api/sessions/${specId}/versions/1`],
    ] as const) {
      const r = await page.request.fetch(url, {
        method,
        headers,
        data: method === 'POST' ? { instruction: 'Précise' } : undefined,
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
    await expect(page.getByRole('button', { name: "Faire évoluer avec l'IA" })).toBeEnabled();
  });
});
