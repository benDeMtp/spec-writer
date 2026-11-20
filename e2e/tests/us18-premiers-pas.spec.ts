import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { jiraURL } from './support/env';
import { startFreshInstance, type FreshInstance } from './support/fresh-instance';

/**
 * 018, US1 : assistant de premier lancement. Les parcours d'une installation vierge tournent sur une
 * seconde instance, base neuve et ouverture automatique (research.md R7) ; l'instance partagée,
 * elle, ne l'ouvre pas d'elle-même.
 */

async function openFresh(browser: Browser, fresh: FreshInstance): Promise<{ context: BrowserContext; page: Page }> {
  const context = await browser.newContext({ baseURL: fresh.baseURL, storageState: undefined });
  const page = await context.newPage();
  await page.goto(fresh.launchUrl);
  // le jeton de lancement est échangé par le front : l'assistant ouvert, le cookie est posé
  await expect(page).toHaveURL(/\/premiers-pas\//);
  return { context, page };
}

/** Copilot simulé connecté et accord donné, par l'API, pour les parcours qui ne testent pas la connexion. */
async function copilotByApi(page: Page, fresh: FreshInstance): Promise<void> {
  const headers = { Origin: fresh.baseURL };
  const start = await (await page.request.post('/api/settings/copilot/device-flow', { headers, data: {} })).json();
  await expect
    .poll(async () => (await (await page.request.get(`/api/settings/copilot/device-flow/${start.flowId}`)).json()).state)
    .toBe('CONNECTED');
  expect((await page.request.put('/api/consents/COPILOT', { headers, data: { granted: true } })).ok()).toBe(true);
}

function stepItem(page: Page, name: string) {
  return page.getByRole('navigation', { name: "Étapes de l'assistant" }).getByRole('listitem').filter({ hasText: name });
}

test.describe.serial('018 Assistant de premier lancement, installation vierge', () => {
  test.setTimeout(180_000);

  test('P1 : de Copilot au projet indexé, sans ouvrir les écrans de réglage', async ({ browser }) => {
    const fresh = await startFreshInstance();
    const { context, page } = await openFresh(browser, fresh);
    try {
      await expect(page).toHaveURL(/\/premiers-pas\/accueil$/);
      await expect(page.getByRole('heading', { level: 1, name: 'Premiers pas' })).toBeVisible();
      await expect(page.getByRole('heading', { level: 2, name: 'Bienvenue' })).toBeVisible();
      await expect(page.getByText('obligatoire, c\'est lui qui répond et rédige')).toBeVisible();
      await expectNoA11yViolations(page);
      await page.getByRole('button', { name: 'Commencer' }).click();

      // Copilot : seule étape obligatoire
      await expect(page).toHaveURL(/\/premiers-pas\/copilot$/);
      await expect(page.getByRole('button', { name: 'Suivant' })).toBeDisabled();
      await expect(page.getByRole('button', { name: 'Passer' })).toHaveCount(0);
      await page.getByRole('button', { name: 'Se connecter à GitHub' }).click();
      const popup = page.waitForEvent('popup');
      await page.getByRole('link', { name: 'Ouvrir GitHub' }).click();
      await (await popup).close();
      await expect(page.getByText('Connecté en tant que e2e-user')).toBeVisible();
      await page.getByRole('switch', { name: 'Envoyer mes besoins et mes réponses à GitHub Copilot' }).click();
      await expect(page.getByRole('button', { name: 'Suivant' })).toBeEnabled();
      await expectNoA11yViolations(page);
      await page.getByRole('button', { name: 'Suivant' }).click();

      // Jira : la carte de Configuration, telle quelle
      await expect(page).toHaveURL(/\/premiers-pas\/jira$/);
      await page.getByLabel('Adresse du serveur').fill(jiraURL);
      await page.getByLabel("Jeton d'accès personnel").fill('e2e-good-token');
      await page.getByRole('checkbox', { name: 'Je comprends et je veux continuer en http' }).check();
      await page.getByRole('button', { name: 'Enregistrer' }).click();
      await page.getByRole('dialog', { name: 'Envoyer des données à Jira ?' }).getByRole('button', { name: 'Accepter' }).click();
      await page.getByRole('button', { name: 'Tester la connexion' }).click();
      await expect(page.getByRole('button', { name: 'Suivant' })).toBeEnabled();
      await page.getByRole('button', { name: 'Suivant' }).click();

      // Confluence : passée
      await expect(page).toHaveURL(/\/premiers-pas\/confluence$/);
      await page.getByRole('button', { name: 'Passer' }).click();

      // Projet : le projet par défaut prend son nom et sa clé
      await expect(page).toHaveURL(/\/premiers-pas\/projet$/);
      await page.getByLabel('Nom du projet').fill('Aurora');
      await page.getByLabel('Clé Jira (facultative)').fill('FACT');
      await page.getByRole('button', { name: 'Enregistrer' }).click();
      await expect(page.getByText('Projet « Aurora » enregistré.')).toBeVisible();
      await expectNoA11yViolations(page);
      await page.getByRole('button', { name: 'Suivant' }).click();

      // Sources : un site Antora
      await expect(page).toHaveURL(/\/premiers-pas\/sources$/);
      await page.getByLabel('Adresse du site').fill(`${jiraURL}/antora-knowledge/`);
      await page.getByRole('button', { name: 'Ajouter' }).click();
      await expect(page.getByRole('button', { name: `Retirer la source ${jiraURL}/antora-knowledge/` })).toBeVisible();
      await page.getByRole('button', { name: 'Suivant' }).click();

      // Indexation : suivie, la conversation s'ouvre sans attendre
      await expect(page).toHaveURL(/\/premiers-pas\/indexation$/);
      await expect(page.getByRole('group').or(page.locator('.outcome')).first()).toBeVisible();
      await expectNoA11yViolations(page);
      await page.getByRole('button', { name: 'Ouvrir la conversation' }).click();
      await expect(page).toHaveURL(/\/$/);

      const projects = await (await page.request.get('/api/projects')).json();
      expect(projects.map((p: { name: string }) => p.name)).toEqual(['Aurora']);
      const onboarding = await (await page.request.get('/api/onboarding')).json();
      expect(onboarding.completed).toBe(true);
      expect(onboarding.steps.map((s: { state: string }) => s.state)).toEqual(['DONE', 'DONE', 'SKIPPED', 'DONE', 'DONE', 'TODO']);

      // T049 : le journal de l'outil ne contient ni le jeton ni le nom saisi
      const journal = await (await page.request.get('/api/journal')).text();
      expect(journal).not.toContain('e2e-good-token');
    } finally {
      await context.close();
      await fresh.stop();
    }
  });

  test("P2 : tout passer après Copilot mène à la conversation, avec l'explication de l'indexation vide", async ({ browser }) => {
    const fresh = await startFreshInstance();
    const { context, page } = await openFresh(browser, fresh);
    try {
      await copilotByApi(page, fresh);
      await page.goto('/premiers-pas/copilot');
      await page.getByRole('button', { name: 'Suivant' }).click();
      for (const step of ['jira', 'confluence', 'projet', 'sources']) {
        await expect(page).toHaveURL(new RegExp(`/premiers-pas/${step}$`));
        await page.getByRole('button', { name: 'Passer' }).click();
      }
      await expect(page).toHaveURL(/\/premiers-pas\/indexation$/);
      await expect(page.getByTestId('nothing-to-index')).toContainText("l'agent ne connaîtra pas le projet");
      await page.getByRole('button', { name: 'Suivant' }).click();
      await expect(page).toHaveURL(/\/premiers-pas\/fin$/);
      // V5 : la visite guidée est proposée en fin d'assistant
      await page.getByRole('button', { name: 'Faire la visite guidée' }).click();
      const bubble = page.getByRole('dialog', { name: 'Converser avec l’agent' });
      await expect(bubble).toContainText('Arrêt 1 sur 12');
      await expect(page).toHaveURL(/\/$/);
      expect((await (await page.request.get('/api/onboarding')).json()).completed).toBe(true);
      await page.keyboard.press('Escape');
      await expect(bubble).toHaveCount(0);
    } finally {
      await context.close();
      await fresh.stop();
    }
  });

  test("P3 : l'assistant reprend à l'étape où il s'était arrêté, outil redémarré", async ({ browser }) => {
    let fresh = await startFreshInstance();
    let opened = await openFresh(browser, fresh);
    try {
      await copilotByApi(opened.page, fresh);
      const headers = { Origin: fresh.baseURL };
      expect((await opened.page.request.put('/api/onboarding/steps/COPILOT', { headers, data: { state: 'DONE' } })).ok()).toBe(true);
      expect((await opened.page.request.put('/api/onboarding/steps/JIRA', { headers, data: { state: 'SKIPPED' } })).ok()).toBe(true);
      await opened.context.close();
      await fresh.stop();

      fresh = await startFreshInstance(fresh.dataDir);
      opened = await openFresh(browser, fresh);
      await expect(opened.page).toHaveURL(/\/premiers-pas\/confluence$/);
      await expect(stepItem(opened.page, 'Connecter GitHub Copilot')).toContainText('faite');
      await expect(stepItem(opened.page, 'Connecter Jira')).toContainText('passée');
      await expect(stepItem(opened.page, 'Connecter Confluence')).toContainText('en cours');
    } finally {
      await opened.context.close();
      await fresh.stop();
    }
  });
});

test.describe('018 Assistant de premier lancement, installation déjà utilisée', () => {
  test("P4 : pas d'ouverture d'elle-même ; relance depuis Configuration à l'accueil", async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1, name: 'Premiers pas' })).toHaveCount(0);
    expect((await (await page.request.get('/api/onboarding')).json()).autoOpen).toBe(false);
    await page.goto('/configuration');
    await page.getByRole('link', { name: "Relancer l'assistant de premier lancement" }).click();
    await expect(page).toHaveURL(/\/premiers-pas\/accueil$/);
    await expect(page.getByRole('heading', { level: 2, name: 'Bienvenue' })).toBeVisible();
    await expectNoA11yViolations(page);
  });
});
