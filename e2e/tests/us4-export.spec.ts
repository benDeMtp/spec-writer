import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, storageStatePath } from './support/env';

const NEED =
  'Les employés doivent pouvoir réserver une place de parking pour une journée, et être prévenus quand le parking ' +
  'est complet.';

/** US4 : export Markdown d'une spécification générée (V10). */
test.describe.serial('US4 export Markdown', () => {
  let context: BrowserContext;
  let page: Page;
  let versionPath = '';

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath, acceptDownloads: true });
    // Le sélecteur natif de Chromium ouvrirait une fenêtre système : on teste le repli par téléchargement.
    // Le chemin du sélecteur (et sa confirmation d'écrasement) est couvert par export.service.spec.ts.
    await context.addInitScript(() => {
      delete (window as unknown as Record<string, unknown>)['showSaveFilePicker'];
    });
    page = await context.newPage();
    const headers = { Origin: baseURL };
    await ensureCopilotConnected(page.request);
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: true } });

    // Spécification générée par l'API : une question répondue, une passée (elle devient une hypothèse)
    let session = await (await page.request.post('/api/sessions', { headers, data: { need: NEED } })).json();
    const id = session.id as string;
    let first = true;
    while (session.currentBatch.length) {
      const q = session.currentBatch[0];
      const data = first ? { skip: true } : { value: q.defaultValue ?? q.suggestions[0] ?? 'Oui' };
      first = false;
      session = await (await page.request.put(`/api/sessions/${id}/questions/${q.id}/answer`, { headers, data })).json();
    }
    const generated = await page.request.post(`/api/sessions/${id}/versions`, { headers });
    expect(generated.status()).toBe(201);
    await page.request.patch(`/api/sessions/${id}`, { headers, data: { name: 'Parking / été' } });
    versionPath = `/sessions/${id}/versions/1`;
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('V10 : le fichier exporté contient le résumé, les use cases, les scénarios et les hypothèses', async () => {
    await page.goto(versionPath);
    await expect(page.getByRole('heading', { level: 1, name: 'Réservation des places de parking' })).toBeVisible();
    await expectNoA11yViolations(page);

    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Exporter en Markdown' }).click();
    const file = await download;
    expect(file.suggestedFilename()).toBe('Parking-été-v1.md');
    const markdown = await readFile((await file.path())!, 'utf8');
    for (const part of ['# Réservation des places de parking', '## Résumé', '## Use cases', 'UC-01', "## Scénarios d'acceptation",
      'SC-01-01', '## Hypothèses', 'H-01']) {
      expect(markdown).toContain(part);
    }
    expect(markdown).toMatch(/En tant que/);
    await expect(page.getByText('Version 1 exportée en Markdown dans vos téléchargements.')).toBeVisible();
  });
});
