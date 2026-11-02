import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, storageStatePath } from './support/env';

const NEED =
  'Les employés du siège doivent pouvoir réserver une salle de réunion depuis leur poste, pour un créneau donné, ' +
  'et être prévenus si la salle est déjà prise.';

/** US2 : du besoin brut à une spécification avec scénarios (quickstart V4 à V7, US2-1 à US2-5). */
test.describe.serial('US2 du besoin à la spécification', () => {
  let context: BrowserContext;
  let page: Page;
  let sessionUrl = '';

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    await ensureCopilotConnected(page.request);
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('V4 : un besoin de 5 mots est refusé avec un exemple', async () => {
    await page.goto('/nouveau');
    await page.getByLabel('Votre besoin').fill('Réserver une salle de réunion');
    await page.getByRole('button', { name: 'Commencer le questionnaire' }).click();
    await expect(page.getByRole('alert').filter({ hasText: 'au moins 10 mots' })).toBeVisible();
    await expect(page).toHaveURL(/\/nouveau$/);
    await page.getByText('Voir un exemple de besoin bien formulé').click();
    await expect(page.getByText('Les employés doivent pouvoir réserver une salle de réunion pour une durée donnée')).toBeVisible();
    await expectNoA11yViolations(page);
  });

  test('V5 : consentement, puis première question avec sa justification et la progression', async () => {
    await page.getByLabel('Votre besoin').fill(NEED);
    await page.getByRole('button', { name: 'Commencer le questionnaire' }).click();
    const dialog = page.getByRole('dialog', { name: 'Envoyer vos besoins à GitHub Copilot ?' });
    // chaque lancement part d'un dossier de données vide : l'accord Copilot n'est jamais encore donné
    await expect(dialog).toBeVisible();
    await expect(page.locator('.mat-mdc-dialog-container')).toHaveClass(/mdc-dialog--open/);
    await expect(page.locator('.mat-mdc-dialog-container')).not.toHaveClass(/mdc-dialog--opening/);
    await expectNoA11yViolations(page);
    await dialog.getByRole('button', { name: 'Accepter' }).click();
    await expect(page).toHaveURL(/\/sessions\/[0-9a-f-]{36}$/);
    sessionUrl = page.url();

    const first = page.getByRole('heading', { level: 2, name: 'Quel problème la réservation doit-elle résoudre ?' });
    await expect(first).toBeVisible();
    await expect(first).toBeFocused();
    await expect(page.getByText("L'objectif guide les règles de gestion")).toBeVisible();
    const progress = page.getByRole('progressbar', { name: 'Progression du questionnaire' });
    await expect(progress).toHaveAttribute('aria-valuenow', '0');
    await expect(progress).toHaveAttribute('aria-valuemax', '3');
    // l'acteur est cité dans le besoin : aucune question sur les acteurs (US2-2)
    await expect(page.getByText('Acteurs', { exact: true })).toHaveCount(0);
    await expectNoA11yViolations(page);
  });

  test('V6 : passer une question, répondre aux autres, générer', async () => {
    const goal = page.locator('sw-question-card').filter({ hasText: 'Quel problème la réservation' });
    await goal.getByRole('option', { name: 'Éviter les doubles réservations' }).click();
    await goal.getByRole('button', { name: 'Enregistrer la réponse' }).click();

    const duration = page.locator('sw-question-card').filter({ hasText: 'durée maximale' });
    await expect(duration.getByText('spec-writer retiendra « 4 heures » comme hypothèse')).toBeVisible();
    await duration.getByRole('button', { name: 'Passer cette question' }).click();

    const taken = page.locator('sw-question-card').filter({ hasText: 'Que proposer quand la salle' });
    await taken.getByLabel('Votre réponse').fill('Proposer les autres salles libres du même étage');
    await taken.getByRole('button', { name: 'Enregistrer la réponse' }).click();

    await expect(page.getByRole('heading', { name: 'Questionnaire terminé' })).toBeVisible();
    await expect(page.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '3');
    await expectNoA11yViolations(page);

    await page.getByRole('button', { name: 'Générer la spécification' }).click();
    await expect(page).toHaveURL(/\/versions\/1$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Réservation des salles de réunion' })).toBeVisible();
    await expect(page.getByRole('heading', { level: 3, name: 'UC-01 Réserver une salle' }).first()).toBeVisible();

    const scenario = page.locator('sw-scenario').filter({ hasText: 'Salle déjà prise' });
    await expect(scenario.locator('dt')).toHaveText(['En tant que', 'Étant donné', 'Quand', 'Alors', 'Et']);
    await expect(page.locator('sw-scenario').filter({ hasText: 'durée maximale' }).getByText('Hypothèse')).toBeVisible();

    // la question passée est tracée comme hypothèse (US2-5)
    const assumptions = page.getByRole('region', { name: 'Hypothèses' });
    await expect(assumptions).toContainText('Quelle est la durée maximale d\'une réservation ?');
    await expect(assumptions).toContainText('4 heures');
    await expect(page.getByText('Extrait du besoin : « réserver une salle de réunion depuis leur poste »')).toBeVisible();
    await expectNoA11yViolations(page);
  });

  test('V7 : générer à nouveau sans rien changer renvoie la même version', async () => {
    await page.goto(sessionUrl);
    const response = page.waitForResponse((r) => r.url().endsWith('/versions') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Générer à nouveau' }).click();
    expect((await response).status()).toBe(200);
    await expect(page).toHaveURL(/\/versions\/1$/);
    const versions = await (await page.request.get(sessionUrl.replace(baseURL, '').replace('/sessions/', '/api/sessions/') + '/versions')).json();
    expect(versions).toHaveLength(1);
  });
});
