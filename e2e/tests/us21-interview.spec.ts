import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, storageStatePath } from './support/env';

const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';
const NEED =
  'Les assistantes doivent pouvoir réserver une salle de réunion pour un comité, et voir les salles libres du jour.';

/**
 * 021 : séance de relecture avec les sachants (quickstart V1). La première question est passée :
 * son hypothèse, sans rattachement avant US4, est relue dans « Non rattachés ».
 */
test.describe.serial('021 interview d’une spécification', () => {
  let context: BrowserContext;
  let page: Page;
  let sessionId = '';
  const headers = { Origin: baseURL };

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    await ensureCopilotConnected(page.request);
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: true } });
    await page.request.put('/api/projects/current', { headers, data: { projectId: DEFAULT_PROJECT } });
    let session = await (await page.request.post('/api/sessions', { headers, data: { need: NEED } })).json();
    sessionId = session.id;
    let first = true;
    while (session.currentBatch.length) {
      const q = session.currentBatch[0];
      const data = first ? { skip: true } : { value: q.defaultValue ?? q.suggestions[0] ?? 'Oui' };
      first = false;
      session = await (
        await page.request.put(`/api/sessions/${sessionId}/questions/${q.id}/answer`, { headers, data })
      ).json();
    }
    expect((await page.request.post(`/api/sessions/${sessionId}/versions`, { headers })).status()).toBe(201);
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('V1 : faire le point, répondre, noter une remarque et une question, reprendre', async () => {
    await page.goto(`/sessions/${sessionId}/versions/1`);
    const start = page.getByRole('button', { name: /^Faire le point/ });
    await expect(start).toBeVisible();
    await start.click();
    await expect(page).toHaveURL(/\/seances\/[0-9a-f-]+$/);

    // présentation : sans menu, la séance seule
    await expect(page.getByRole('navigation', { name: 'Navigation principale' })).toHaveCount(0);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expectNoA11yViolations(page);

    // N : prochain point à traiter, l'hypothèse de la question passée
    await page.keyboard.press('n');
    const current = page.locator('[aria-current="step"]');
    await expect(current).toContainText('Hypothèse H-01');
    await page.keyboard.press('Enter');
    await page.getByRole('radio', { name: 'Confirmé' }).check();
    await page.getByRole('button', { name: 'Enregistrer' }).click();
    await expect(page.locator('.progress')).toContainText('1 traité');

    // R : une remarque sur l'élément courant ; Q : une question avec son responsable
    await page.keyboard.press('ArrowUp');
    await page.keyboard.press('r');
    await page.getByRole('textbox', { name: 'Ce qui a été dit' }).fill('Validation du manager au-delà de 500 €');
    await page.getByRole('button', { name: 'Enregistrer' }).click();
    await expect(page.locator('.progress')).toContainText('1 note');
    await page.keyboard.press('q');
    await page.getByRole('textbox', { name: 'Question' }).fill('Qui valide en l’absence du manager ?');
    await page.getByLabel('Responsable').fill('Paul D.');
    await page.getByRole('button', { name: 'Enregistrer' }).click();
    await expect(page.locator('.progress')).toContainText('2 notes');
    const label = (await current.locator('.label').textContent())?.trim() ?? '';

    // reprise après rechargement : même élément, rien de perdu
    await page.reload();
    await expect(page.locator('[aria-current="step"] .label')).toHaveText(label);
    await expect(page.locator('.progress')).toContainText('1 traité');
    await expect(page.locator('.progress')).toContainText('2 notes');
    await expect(page.getByText('Qui valide en l’absence du manager ?')).toBeVisible();

    // depuis la page de la spécification, la séance se reprend
    await page.goto(`/sessions/${sessionId}/versions/1`);
    await expect(page.getByRole('button', { name: 'Reprendre la séance' })).toBeVisible();
  });
});
