import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { copyDemoTree } from './support/demo-tree';
import { baseURL, storageStatePath } from './support/env';

const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';

/**
 * 013 : périmètre des modules touchés (quickstart). L'arbre de démonstration est copié dans un
 * dossier temporaire avec ses pièges ; le projet Périmètre E2E est créé pour le test.
 */
test.describe.serial('013 périmètre des modules touchés', () => {
  let context: BrowserContext;
  let page: Page;
  let projectId = '';
  let demo: ReturnType<typeof copyDemoTree>;
  const headers = { Origin: baseURL };

  test.beforeAll(async ({ browser }) => {
    demo = copyDemoTree();
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    const created = await page.request.post('/api/projects', { headers, data: { name: 'Périmètre E2E' } });
    expect(created.status()).toBe(201);
    projectId = (await created.json()).id;
    await page.request.put('/api/projects/current', { headers, data: { projectId } });
  });

  test.afterAll(async () => {
    await page.request.put('/api/projects/current', { headers, data: { projectId: DEFAULT_PROJECT } });
    await page.request.delete(`/api/projects/${projectId}?sessions=DELETE`, { headers });
    await context.close();
    demo.dispose();
  });

  test('Q1 : la racine se déclare après un aperçu de ses quatorze modules', async () => {
    await page.goto('/projet/reglages#sources-code');
    const section = page.getByRole('region', { name: 'Sources du code' });
    await expect(page.getByRole('heading', { name: 'Sources du code' })).toBeFocused();
    await expect(section).toContainText('envoie à GitHub Copilot les noms des modules et de courts extraits');
    await section.getByLabel('Chemin du dossier').fill(demo.root);
    await section.getByRole('button', { name: 'Détecter les modules' }).click();
    const status = section.getByRole('status');
    await expect(status).toContainText('14 modules détectés');
    await expect(status).toContainText('2 fichiers sensibles écartés');
    await expect(status).toContainText('demo-front');
    await expect(status).toContainText('facturation-chart');
    await expect(status).not.toContainText('lien-externe');
    await expectNoA11yViolations(page);
  });

  test('Q2 : un motif retire le module legacy de l’aperçu, puis la racine est ajoutée', async () => {
    const section = page.getByRole('region', { name: 'Sources du code' });
    await section.getByLabel("Motifs d'exclusion (facultatif)").fill('legacy/**');
    await section.getByRole('button', { name: 'Détecter les modules' }).click();
    await expect(section.getByRole('status')).toContainText('13 modules détectés');
    await expect(section.getByRole('status')).not.toContainText('ancien');
    await section.getByRole('button', { name: 'Ajouter la racine' }).click();
    await expect(section.getByRole('listitem').filter({ hasText: 'Exclus : legacy/**' })).toContainText('demo-rv');
  });

  test('Q3 : un chemin absent ou imbriqué est refusé avec sa raison', async () => {
    const section = page.getByRole('region', { name: 'Sources du code' });
    await section.getByLabel('Chemin du dossier').fill(`${demo.root}-absent`);
    await section.getByRole('button', { name: 'Ajouter la racine' }).click();
    await expect(page.getByRole('alert')).toContainText("Ce dossier n'existe pas");
    await section.getByLabel('Chemin du dossier').fill(`${demo.root}/modules`);
    await section.getByRole('button', { name: 'Ajouter la racine' }).click();
    await expect(page.getByRole('alert')).toContainText('se contiennent');
    await section.getByLabel('Chemin du dossier').fill('');
  });
});
