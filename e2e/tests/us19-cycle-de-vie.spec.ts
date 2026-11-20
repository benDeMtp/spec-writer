import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { launchAgain, spawnInstance, startFreshInstance, type FreshInstance } from './support/fresh-instance';

/**
 * 019 : cycle de vie de l'outil. Chaque parcours tourne sur une seconde instance, sur son propre
 * dossier de données, pour ne jamais arrêter l'instance partagée.
 */

const NO_WIZARD = ['-Dspecwriter.onboarding.auto-open=false'];

async function open(browser: Browser, fresh: FreshInstance): Promise<{ context: BrowserContext; page: Page }> {
  const context = await browser.newContext({ baseURL: fresh.baseURL, storageState: undefined });
  const page = await context.newPage();
  await page.goto(fresh.launchUrl);
  await expect(page.getByRole('button', { name: 'Quitter spec-writer' })).toBeVisible();
  return { context, page };
}

test.describe.serial('019 Cycle de vie de l’outil', () => {
  test.setTimeout(120_000);

  test('Q1 : Quitter arrête l’outil, chaque onglet affiche l’écran d’arrêt', async ({ browser }) => {
    const fresh = await startFreshInstance(undefined, NO_WIZARD);
    const { context, page } = await open(browser, fresh);
    try {
      const other = await context.newPage();
      await other.goto('/');
      await expect(other.getByRole('button', { name: 'Quitter spec-writer' })).toBeVisible();

      await page.getByRole('button', { name: 'Quitter spec-writer' }).click();
      const dialog = page.getByRole('dialog', { name: 'Quitter spec-writer ?' });
      await expect(dialog).toContainText('Vos données sont gardées.');
      await expect(dialog.getByRole('button', { name: 'Annuler' })).toBeFocused();
      await expectNoA11yViolations(page);

      // Annuler laisse l'outil tourner
      await dialog.getByRole('button', { name: 'Annuler' }).click();
      await expect(dialog).toBeHidden();
      expect((await page.request.get('/api/application')).ok()).toBe(true);

      const begin = Date.now();
      await page.getByRole('button', { name: 'Quitter spec-writer' }).click();
      await page.getByRole('dialog').getByRole('button', { name: 'Quitter', exact: true }).click();

      const stopped = page.getByRole('heading', { level: 1, name: 'spec-writer est arrêté' });
      await expect(stopped).toBeVisible();
      await expect(stopped).toBeFocused();
      await expect(page.getByRole('main')).toContainText('Vous pouvez fermer cet onglet.');
      await expectNoA11yViolations(page);

      expect(await fresh.exited).toBe(0);
      expect(Date.now() - begin).toBeLessThan(10_000);

      // l'autre onglet découvre l'arrêt de lui-même, avec la consigne générale
      await expect(other.getByRole('heading', { level: 1, name: 'spec-writer est arrêté' })).toBeVisible({
        timeout: 15_000,
      });
      await expect(other.getByRole('main')).toContainText('ouvrez-le depuis son raccourci');
    } finally {
      await context.close();
      await fresh.stop();
    }
  });

  test('Q1 : relancé sur le même dossier, l’outil retrouve ses données', async ({ browser }) => {
    let fresh = await startFreshInstance(undefined, NO_WIZARD);
    let opened = await open(browser, fresh);
    const headers = { Origin: fresh.baseURL };
    try {
      const created = await opened.page.request.post('/api/projects', {
        headers,
        data: { name: 'Avant Quitter', description: null, jiraProjectKey: null },
      });
      expect(created.ok()).toBe(true);
      expect((await opened.page.request.post('/api/application/quit', { headers, data: {} })).status()).toBe(202);
      expect(await fresh.exited).toBe(0);
      await opened.context.close();

      fresh = await startFreshInstance(fresh.dataDir, NO_WIZARD);
      opened = await open(browser, fresh);
      const projects = await (await opened.page.request.get('/api/projects')).json();
      expect(projects.map((p: { name: string }) => p.name)).toContain('Avant Quitter');
    } finally {
      await opened.context.close();
      await fresh.stop();
    }
  });
  test('Q3 : un second lancement rouvre l’instance en marche, sans en démarrer une autre', async ({ browser }) => {
    const fresh = await startFreshInstance(undefined, NO_WIZARD);
    const { context } = await open(browser, fresh);
    try {
      const again = await launchAgain(fresh.dataDir, NO_WIZARD);
      expect(again.code).toBe(0);
      expect(again.ms).toBeLessThan(5_000 + 3_000); // démarrage de la JVM compris
      expect(again.stdout).toContain('spec-writer est déjà lancé');
      const url = /(http:\/\/127\.0\.0\.1:\d+\/\?launch=[\w-]+)/.exec(again.stdout)![1];
      expect(url.startsWith(fresh.baseURL)).toBe(true);

      // l'adresse ouvre une session dans un navigateur neuf
      const other = await browser.newContext({ baseURL: fresh.baseURL, storageState: undefined });
      const page = await other.newPage();
      await page.goto(url);
      await expect(page.getByRole('button', { name: 'Quitter spec-writer' })).toBeVisible();
      await other.close();

      // l'instance d'origine tourne toujours, seule
      expect((await context.request.get(`${fresh.baseURL}/q/health/ready`)).ok()).toBe(true);
    } finally {
      await context.close();
      await fresh.stop();
    }
  });

  test('Q3 : deux lancements simultanés, une seule instance', async () => {
    const dataDir = mkdtempSync(join(tmpdir(), 'spec-writer-e2e-fresh-'));
    const a = spawnInstance(dataDir, NO_WIZARD);
    const b = spawnInstance(dataDir, NO_WIZARD);
    try {
      // le premier à finir est le second lancement : il a rouvert l'instance, puis rendu la main
      const [first, code] = await Promise.race([a.exited.then((c) => [a, c] as const), b.exited.then((c) => [b, c] as const)]);
      expect(code).toBe(0);
      expect(first.stdout()).toContain('spec-writer est déjà lancé');
      const instance = first === a ? b : a;
      expect(await Promise.race([instance.exited.then(() => 'arrêtée'), new Promise((r) => setTimeout(() => r('tourne'), 2_000))])).toBe(
        'tourne',
      );
    } finally {
      await Promise.all([a.terminate(), b.terminate()]);
    }
  });

  test('Q4 : après un arrêt brutal, la relance démarre normalement', async () => {
    const fresh = await startFreshInstance(undefined, NO_WIZARD);
    await fresh.kill();
    const again = await startFreshInstance(fresh.dataDir, NO_WIZARD);
    try {
      expect((await fetch(`${again.baseURL}/q/health/ready`)).ok).toBe(true);
    } finally {
      await again.stop();
    }
  });
});
