import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { baseURL, jiraURL, storageStatePath } from './support/env';

/** US1 : connexion à GitHub Copilot et à Jira, sans jeton lisible hors du trousseau. */
test.describe.serial('US1 configuration', () => {
  let context: BrowserContext;
  let page: Page;

  test.beforeAll(async ({ browser }) => {
    // Une seule page pour tout le parcours ; axe exige un contexte explicite
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('US1-1 : sans Copilot, le questionnaire renvoie vers la configuration', async () => {
    await page.goto('/nouveau');
    await expect(page).toHaveURL(/\/configuration\?raison=copilot$/);
    await expect(page.getByText('Connectez GitHub Copilot pour commencer un nouveau besoin.')).toBeVisible();
    await expect(page.getByRole('heading', { level: 1, name: 'Configuration' })).toBeVisible();
    await expectNoA11yViolations(page);
  });

  test("US1-2 : autorisation dans le navigateur, puis login et modèles affichés", async () => {
    await page.getByRole('button', { name: 'Se connecter à GitHub' }).click();
    await expect(page.getByText("GitHub va vous demander d'autoriser spec-writer à :")).toBeVisible();
    await expect(page.locator('.scopes li').first()).toContainText('read:user');
    await expectNoA11yViolations(page);

    const popup = page.waitForEvent('popup');
    await page.getByRole('link', { name: 'Ouvrir GitHub' }).click();
    const github = await popup;
    await expect(github.getByRole('heading', { name: 'Autorisation GitHub simulée' })).toBeVisible();
    await github.close();

    await expect(page.getByText('Connecté en tant que e2e-user')).toBeVisible();
    await expect(page.getByRole('combobox', { name: 'Modèle utilisé' })).toBeVisible();
    await expectNoA11yViolations(page);

    await page.goto('/nouveau');
    await expect(page).toHaveURL(/\/nouveau$/);
    await page.goto('/configuration');
  });

  test('US1-3 : capacité et coût de chaque modèle, place de la connaissance (008)', async () => {
    const select = page.getByRole('combobox', { name: 'Modèle utilisé' });
    const knowledge = page.locator('.knowledge');
    // le modèle par défaut n'annonce pas sa limite : le budget d'aujourd'hui
    await expect(knowledge).toHaveText(/Connaissance transmise : jusqu’à 35\s000 caractères/);
    await select.click();
    const options = page.getByRole('option');
    await expect(options).toHaveCount(3);
    await expect(options.nth(0)).toHaveText(/Model A\s+capacité inconnue · coût inconnu/);
    await expect(options.nth(1)).toHaveText(/Model B\s+environ 32\s000 tokens \(≈ 96\s000 caractères\) · ×0,33/);
    await expect(options.nth(2)).toHaveText(/Grand modèle\s+environ 128\s000 tokens \(≈ 384\s000 caractères\) · ×1/);
    await expectNoA11yViolations(page);

    await page.getByRole('option', { name: /Grand modèle/ }).click();
    await expect(select).toHaveText('Grand modèle');
    await expect(knowledge).toHaveText(/jusqu’à 162\s000 caractères/);
    await expectNoA11yViolations(page);

    // retour au modèle par défaut pour les parcours suivants
    await select.click();
    await page.getByRole('option', { name: /Model A/ }).click();
    await expect(knowledge).toHaveText(/jusqu’à 35\s000 caractères/);
  });

  test("US1-4 : configuration Jira en http avec avertissement, consentement puis test réussi", async () => {
    await page.getByLabel('Adresse du serveur').fill(jiraURL);
    await expect(page.getByText("Cette adresse n'est pas chiffrée (http).")).toBeVisible();
    await page.getByLabel('Clé Jira par défaut (facultatif)').fill('PROJ');
    await page.getByLabel("Jeton d'accès personnel").fill('e2e-good-token');
    await page.getByRole('button', { name: 'Enregistrer' }).click();
    // Sans confirmation de l'avertissement, rien n'est enregistré
    await expect(page.getByRole('button', { name: 'Tester la connexion' })).toHaveCount(0);

    await page.getByRole('checkbox', { name: 'Je comprends et je veux continuer en http' }).check();
    await page.getByRole('button', { name: 'Enregistrer' }).click();

    const dialog = page.getByRole('dialog', { name: 'Envoyer des données à Jira ?' });
    await expect(dialog).toBeVisible();
    // Analyse une fois l'animation d'ouverture terminée, sinon axe mesure des couleurs en transition
    await expect(page.locator('.mat-mdc-dialog-container')).toHaveClass(/mdc-dialog--open/);
    await expect(page.locator('.mat-mdc-dialog-container')).not.toHaveClass(/mdc-dialog--opening/);
    await expectNoA11yViolations(page);
    await dialog.getByRole('button', { name: 'Accepter' }).click();
    await expect(dialog).toBeHidden();

    await page.getByRole('button', { name: 'Tester la connexion' }).click();
    await expect(page.getByText('Connexion à Jira réussie.')).toBeVisible();
    await expect(page.getByText('(Jira 9.12.3)')).toBeVisible();
    await expectNoA11yViolations(page);
  });

  test('US1-5 : le jeton est masqué et jamais renvoyé par l’API', async () => {
    await expect(page.getByLabel("Jeton d'accès personnel")).toHaveCount(0);
    await expect(page.getByText("Jeton d'accès personnel enregistré dans le trousseau.")).toBeVisible();
    await expect(page.getByRole('button', { name: 'Remplacer' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Supprimer' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Afficher' })).toHaveCount(0);

    const body = await (await page.request.get('/api/settings')).text();
    expect(body).not.toContain('e2e-good-token');
    expect(JSON.parse(body).jira.tokenSet).toBe(true);

    await page.reload();
    await expect(page.locator('body')).not.toContainText('e2e-good-token');
  });

  test('US1-6 : un jeton refusé donne la cause et l’action', async () => {
    await page.getByRole('button', { name: 'Remplacer' }).click();
    const token = page.getByLabel("Jeton d'accès personnel");
    await expect(token).toHaveAttribute('type', 'password');
    await expect(token).toHaveValue('');
    await token.fill('jeton-perime');
    await page.getByRole('button', { name: 'Enregistrer' }).click();
    await expect(page.getByText("Jeton d'accès personnel enregistré dans le trousseau.")).toBeVisible();

    await page.getByRole('button', { name: 'Tester la connexion' }).click();
    const status = page.getByRole('status').filter({ hasText: 'Le jeton a été refusé.' });
    await expect(status).toContainText("Créez un nouveau jeton d'accès personnel dans Jira");
    await expectNoA11yViolations(page);
  });

  test('T057 (013) : choisir le navigateur où s’ouvre spec-writer, et l’y ouvrir aussitôt', async () => {
    const card = page.getByRole('region', { name: 'Navigateur' });
    await card.scrollIntoViewIfNeeded();
    const system = card.getByRole('radio', { name: 'Navigateur par défaut du système' });
    await expect(system).toBeChecked();
    await expect(card.getByRole('radio')).toHaveCount(3);
    const open = card.getByRole('button', { name: 'Ouvrir spec-writer dans ce navigateur' });

    await card.getByRole('radio', { name: 'Navigateur simulé B' }).check();
    await expect(open).toBeDisabled();
    await card.getByRole('button', { name: 'Utiliser ce navigateur' }).click();
    await expect(card.getByRole('status')).toContainText(
      "spec-writer s'ouvrira dans Navigateur simulé B au prochain lancement.",
    );

    await page.reload();
    await expect(page.getByRole('region', { name: 'Navigateur' }).getByRole('radio', { name: 'Navigateur simulé B' })).toBeChecked();
    await page.getByRole('region', { name: 'Navigateur' }).getByRole('button', { name: 'Ouvrir spec-writer dans ce navigateur' }).click();
    await expect(page.getByRole('region', { name: 'Navigateur' }).getByRole('status')).toContainText(
      "spec-writer s'ouvre dans Navigateur simulé B.",
    );
    await expectNoA11yViolations(page);

    // retour au navigateur du système, pour les parcours suivants
    await page.getByRole('region', { name: 'Navigateur' }).getByRole('radio', { name: 'Navigateur par défaut du système' }).check();
    await page.getByRole('region', { name: 'Navigateur' }).getByRole('button', { name: 'Utiliser ce navigateur' }).click();
    await expect(page.getByRole('region', { name: 'Navigateur' }).getByRole('status')).toContainText('le navigateur du système');
  });
});
