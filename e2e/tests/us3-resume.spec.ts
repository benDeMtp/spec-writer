import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { startApp, stopApp } from './support/app';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, storageStatePath } from './support/env';
import { exchange } from './support/global-setup';

const NEED =
  'Les employés doivent pouvoir réserver une place de parking pour une journée, et être prévenus quand le parking ' +
  'est complet.';

async function waitForAnimation(page: Page): Promise<void> {
  await expect(page.locator('.mat-mdc-dialog-container')).toHaveClass(/mdc-dialog--open/);
  await expect(page.locator('.mat-mdc-dialog-container')).not.toHaveClass(/mdc-dialog--opening/);
}

/** US3 : reprendre après une fermeture brutale, modifier une réponse, régénérer (V8, V9, US3-3). */
test.describe.serial('US3 reprise et révision', () => {
  let context: BrowserContext;
  let page: Page;
  let sessionPath = '';

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    await ensureCopilotConnected(page.request);
    await page.request.put('/api/consents/COPILOT', { headers: { Origin: baseURL }, data: { granted: true } });
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('V8 : après un arrêt brutal, la session reprend à la même question', async () => {
    test.setTimeout(120_000);
    await page.goto('/nouveau');
    await page.getByLabel('Votre besoin').fill(NEED);
    await page.getByRole('button', { name: 'Commencer le questionnaire' }).click();
    await expect(page).toHaveURL(/\/sessions\/[0-9a-f-]{36}$/);
    sessionPath = new URL(page.url()).pathname;
    const visitors = page.locator('sw-question-card').filter({ hasText: 'Les visiteurs peuvent-ils réserver' });
    await visitors.getByRole('option', { name: 'Oui' }).click();
    await visitors.getByRole('button', { name: 'Enregistrer la réponse' }).click();
    await expect(page.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '1');

    await stopApp('SIGKILL');
    const launchUrl = await startApp();
    // la nouvelle instance a un nouveau jeton de lancement : on rouvre l'application avec lui
    await exchange(launchUrl);
    await context.close();
    context = await page.context().browser()!.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();

    await page.goto('/sessions');
    await expect(page.getByRole('heading', { level: 1, name: 'Sessions' })).toBeVisible();
    await expectNoA11yViolations(page);
    const item = page.getByRole('listitem').filter({ hasText: 'Les employés doivent pouvoir réserver une place' });
    await expect(item).toContainText('Questionnaire en cours');
    await item.getByRole('link', { name: 'Reprendre' }).click();

    await expect(page).toHaveURL(new RegExp(`${sessionPath}$`));
    await expect(page.getByRole('heading', { level: 2, name: 'Que faire pour un visiteur sans badge ?' })).toBeFocused();
    await expect(page.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '1');
    await expect(page.getByRole('region', { name: 'Réponses données' })).toContainText('Oui');
  });

  test('génération de la version 1', async () => {
    const badge = page.locator('sw-question-card').filter({ hasText: 'visiteur sans badge' });
    await badge.getByLabel('Votre réponse').fill("Les accueillir à l'entrée");
    await badge.getByRole('button', { name: 'Enregistrer la réponse' }).click();
    await page.getByRole('button', { name: 'Générer la spécification' }).click();
    await expect(page).toHaveURL(/\/versions\/1$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Réservation des places de parking' })).toBeVisible();
  });

  test('V9 : modifier une réponse retire les questions sans objet et garde les autres', async () => {
    await page.goto(sessionPath);
    await page.getByRole('button', { name: 'Modifier la réponse à : Les visiteurs peuvent-ils réserver une place ?' }).click();
    await page.getByLabel('Nouvelle réponse').fill('Non, réservé aux employés');
    await page.getByRole('button', { name: 'Enregistrer la modification' }).click();

    await expect(page.getByRole('status').filter({ hasText: 'Réponse modifiée' })).toContainText(
      '1 question retirée : Que faire pour un visiteur sans badge ?',
    );
    await expect(page.getByRole('status').filter({ hasText: 'Réponse modifiée' })).toContainText(
      "1 question ajoutée : Combien de jours à l'avance peut-on réserver ?",
    );
    const answered = page.getByRole('region', { name: 'Réponses données' });
    await expect(answered).toContainText('Non, réservé aux employés');
    await expect(answered).not.toContainText("Les accueillir à l'entrée");
    await expect(page.getByRole('heading', { level: 2, name: "Combien de jours à l'avance peut-on réserver ?" })).toBeFocused();
    await expectNoA11yViolations(page);
  });

  test('US3-3 : régénérer crée la version 2, la version 1 reste consultable', async () => {
    const days = page.locator('sw-question-card').filter({ hasText: "Combien de jours à l'avance" });
    await days.getByRole('option', { name: '7 jours' }).click();
    await days.getByRole('button', { name: 'Enregistrer la réponse' }).click();
    await page.getByRole('button', { name: 'Générer à nouveau' }).click();
    await expect(page).toHaveURL(/\/versions\/2$/);

    const versions = page.getByRole('navigation', { name: 'Versions de la spécification' });
    await expect(versions.getByRole('link')).toHaveText(['Version 1', 'Version 2']);
    await expect(versions.getByRole('link', { name: 'Version 2' })).toHaveAttribute('aria-current', 'page');
    await expectNoA11yViolations(page);
    await versions.getByRole('link', { name: 'Version 1' }).click();
    await expect(page).toHaveURL(/\/versions\/1$/);
    await expect(page.getByText('Version 1 ·')).toBeVisible();
  });

  test('renommer puis supprimer la session', async () => {
    await page.goto('/sessions');
    const item = page.getByRole('listitem').filter({ hasText: 'réserver une place' });
    await item.getByRole('button', { name: 'Renommer' }).click();
    await page.getByLabel('Nom de la session').fill('Parking du siège');
    await page.getByRole('button', { name: 'Enregistrer' }).click();
    await expect(page.getByRole('status')).toContainText('Session renommée en « Parking du siège »');

    const renamed = page.getByRole('listitem').filter({ hasText: 'Parking du siège' });
    await renamed.getByRole('button', { name: 'Supprimer la session Parking du siège' }).click();
    const dialog = page.getByRole('dialog', { name: 'Supprimer cette session ?' });
    await waitForAnimation(page);
    await expectNoA11yViolations(page);
    await dialog.getByRole('button', { name: 'Supprimer définitivement' }).click();
    await expect(page.getByRole('status')).toContainText('Session « Parking du siège » supprimée');
    await expect(page.getByRole('listitem').filter({ hasText: 'Parking du siège' })).toHaveCount(0);
  });
});
