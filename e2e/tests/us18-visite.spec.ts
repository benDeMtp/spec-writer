import { expect, test, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { baseURL } from './support/env';

/** 018, US2 : visite guidée de l'outil, dans l'ordre d'usage (contracts/tour.md). */

const TITLES = [
  'Converser avec l’agent',
  'Rédiger depuis la discussion',
  'Vos spécifications',
  'Une spécification',
  'Discuter avec l’IA',
  'Tâches et suivi par les agents',
  'Tickets Jira',
  'Connaissance du projet',
  'Réglages du projet',
  'Réglages avancés',
  'Agents de code (MCP)',
];

/** Cibles présentes quel que soit l'état du projet : la visite doit les désigner. */
const ALWAYS = new Map([
  [0, 'composer'],
  [2, 'sessions-list'],
  [7, 'knowledge-state'],
  [8, 'doc-sources'],
  [9, 'advanced'],
  [10, 'agent-access'],
]);

/** Lectures envoyées en POST pour leur corps : l'état des mentions d'une spécification (007). */
const READS_BY_POST = [/\/api\/projects\/[^/]+\/mentions\/resolve$/];

function bubble(page: Page) {
  return page.locator('sw-tour-bubble [role="dialog"]');
}

test.describe.serial('018 Visite guidée', () => {
  test("V1, V4 : visite complète au clavier depuis le menu, cibles désignées, aucune écriture", async ({ page }) => {
    const writes: string[] = [];
    page.on('request', (r) => {
      if (r.method() !== 'GET' && r.url().startsWith(baseURL) && !READS_BY_POST.some((re) => re.test(new URL(r.url()).pathname))) {
        writes.push(`${r.method()} ${r.url()}`);
      }
    });
    await page.goto('/journal');
    await page.getByRole('button', { name: 'Visite guidée' }).click();
    for (let i = 0; i < TITLES.length; i++) {
      await expect(bubble(page)).toContainText(`Arrêt ${i + 1} sur 11`);
      await expect(page.getByRole('dialog', { name: TITLES[i] })).toBeVisible();
      const target = ALWAYS.get(i);
      if (target) {
        await expect(page.locator(`[data-tour="${target}"]`)).toHaveClass(/sw-tour-target/);
      }
      await expectNoA11yViolations(page);
      // le focus est dans la bulle : Entrée active « Suivant », puis « Terminer »
      await page.keyboard.press('Enter');
    }
    await expect(bubble(page)).toHaveCount(0);
    await expect(page).toHaveURL(/\/journal$/);
    expect(writes).toEqual([]);
  });

  test("V2 : projet sans spécification, les arrêts de la spécification restent sur la liste", async ({ page }) => {
    const headers = { Origin: baseURL };
    const previous = (await (await page.request.get('/api/projects/current')).json()).id;
    const created = await page.request.post('/api/projects', { headers, data: { name: `Visite ${Date.now()}` } });
    const id = (await created.json()).id;
    await page.request.put('/api/projects/current', { headers, data: { projectId: id } });
    try {
      await page.goto('/');
      await page.getByRole('button', { name: 'Visite guidée' }).click();
      for (let i = 0; i < 3; i++) {
        await expect(bubble(page)).toContainText(`Arrêt ${i + 1} sur 11`);
        await bubble(page).getByRole('button', { name: 'Suivant' }).click();
      }
      for (const [i, text] of [
        [4, 'Une spécification s’ouvre depuis cette liste'],
        [5, '« Discuter avec l’IA » propose une nouvelle version'],
        [6, 'Une spécification se découpe en tâches'],
      ] as const) {
        await expect(bubble(page)).toContainText(`Arrêt ${i} sur 11`);
        await expect(page).toHaveURL(/\/sessions$/);
        await expect(bubble(page)).toContainText(text);
        await bubble(page).getByRole('button', { name: 'Suivant' }).click();
      }
      await page.keyboard.press('Escape');
    } finally {
      await page.request.put('/api/projects/current', { headers, data: { projectId: previous } });
      await page.request.delete(`/api/projects/${id}?sessions=DELETE`, { headers });
    }
  });

  test("V3 : Échap à l'arrêt 7 ramène à l'écran de départ", async ({ page }) => {
    await page.goto('/projets');
    await page.getByRole('button', { name: 'Visite guidée' }).click();
    for (let i = 0; i < 6; i++) {
      await expect(bubble(page)).toContainText(`Arrêt ${i + 1} sur 11`);
      await bubble(page).getByRole('button', { name: 'Suivant' }).click();
    }
    await expect(bubble(page)).toContainText('Arrêt 7 sur 11');
    await expect(page).toHaveURL(/\/jira$/);
    await page.keyboard.press('Escape');
    await expect(bubble(page)).toHaveCount(0);
    await expect(page).toHaveURL(/\/projets$/);
  });
});
