import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, storageStatePath } from './support/env';

const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';

/**
 * 010 : l'agent conversationnel comme point d'entrée (quickstart 1 à 3, 6 à 9, 12). L'IA est
 * rejouée (fixtures 30 à 36) : recherche visible, réponse en flux, citation d'un document joint,
 * incohérence signalée, rédaction d'une spécification avec des points corrigés.
 */
test.describe.serial('010 agent conversationnel', () => {
  let context: BrowserContext;
  let page: Page;
  let files: string;
  let conversationUrl = '';
  let sessionId = '';
  const headers = { Origin: baseURL };

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    await ensureCopilotConnected(page.request);
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: true } });
    await page.request.put('/api/projects/current', { headers, data: { projectId: DEFAULT_PROJECT } });
    files = mkdtempSync(join(tmpdir(), 'spec-writer-conversation-'));
    writeFileSync(
      join(files, 'parking.html'),
      '<html><head><title>Réservation du parking</title></head><body><h1>Réservation du parking</h1>' +
        '<p>Une place se réserve pour une journée, au plus 7 jours à l’avance.</p></body></html>',
    );
  });

  test.afterAll(async () => {
    if (sessionId) {
      await page.request.delete(`/api/sessions/${sessionId}`, { headers });
    }
    // us2 vérifie la demande de consentement d'un premier envoi : l'accord donné ici est retiré
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: false } });
    await context.close();
  });

  const composer = () => page.getByLabel('Votre question');
  const answers = () => page.locator('li.answer');

  test('C1 : accueil sur la conversation, nouveau menu', async () => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1, name: 'Conversation' })).toBeVisible();
    const menu = page.getByRole('navigation', { name: 'Navigation principale' });
    await expect(menu.getByRole('link')).toHaveText([
      'Conversation',
      'Spécifications',
      'Tickets Jira',
      'Projets',
      'Journal',
      'Configuration',
    ]);
    await expect(menu.getByRole('link', { name: 'Conversation' })).toHaveAttribute('aria-current', 'page');
    await expect(page.getByText('Posez une question sur le projet')).toBeVisible();
    await expectNoA11yViolations(page);
  });

  test('C2 : question, étape de recherche, réponse, conversation nommée', async () => {
    await composer().fill('E2E-AGENT Que sait-on de la réservation des places de parking ?');
    await composer().press('Enter');
    await expect(page).toHaveURL(/\/conversations\/[0-9a-f-]{36}$/);
    conversationUrl = new URL(page.url()).pathname;
    await expect(answers().first().locator('.note')).toContainText('Je ne trouve pas de règle écrite');
    await expect(answers().first().locator('.steps summary')).toContainText('1 recherche');
    await expect(page.getByRole('button', { name: 'Envoyer' })).toBeVisible();
    await expect(page.getByRole('heading', { level: 1 })).toContainText('E2E-AGENT Que sait-on');
    await expectNoA11yViolations(page);
  });

  test('C3 : document joint, incohérence signalée avec sa source', async () => {
    await page.locator('sw-attachments input[type="file"]').setInputFiles(join(files, 'parking.html'));
    await expect(page.locator('sw-attachments').getByRole('listitem')).toContainText('Réservation du parking');

    await composer().fill('E2E-INCOHERENCE Une place se réserve-t-elle 14 jours à l’avance ?');
    await page.getByRole('button', { name: 'Envoyer' }).click();
    const answer = answers().nth(1);
    await expect(answer.locator('.note')).toContainText('Écart relevé');
    // un document joint n'a pas d'adresse : l'appel mène à sa note sous la réponse
    await answer.locator('.note a', { hasText: '[1]' }).click();
    await expect(answer.getByRole('list', { name: 'Sources de la réponse' }).getByRole('listitem').first()).toBeFocused();
    await expect(answer.getByRole('list', { name: 'Sources de la réponse' })).toContainText('Document joint');
    await expect(answer.getByRole('list', { name: 'Sources de la réponse' })).toContainText('Réservation du parking');
    await expectNoA11yViolations(page);
  });

  test('C4 : la conversation se retrouve après rechargement', async () => {
    await page.reload();
    await expect(page.locator('li.question')).toHaveCount(2);
    await expect(answers().nth(1).locator('.note')).toContainText('Écart relevé');
  });

  test('C5 : rédiger une spécification avec des points corrigés', async () => {
    await page.getByRole('button', { name: 'Rédiger une spécification' }).click();
    const draft = page.getByRole('region', { name: 'Rédiger une spécification' });
    await expect(draft.getByLabel('Besoin à spécifier')).toHaveValue(/place de parking/);
    // le point qui cite une source absente de la conversation est écarté
    await expect(draft.locator('fieldset')).toHaveCount(2);
    await expect(draft).toContainText('Établi par la source joint:parking.html');
    await expectNoA11yViolations(page);

    await draft.locator('fieldset').filter({ hasText: 'Acteurs' }).getByLabel('Réponse')
      .fill('Les employés du siège et les visiteurs.');
    await draft.getByRole('button', { name: /Retirer ce point : Combien de temps/ }).click();
    await expect(draft.locator('fieldset')).toHaveCount(1);
    await draft.getByRole('button', { name: 'Démarrer la spécification' }).click();

    await expect(page).toHaveURL(/\/sessions\/[0-9a-f-]{36}$/);
    sessionId = new URL(page.url()).pathname.split('/').pop()!;
    await expect(page.getByText('Issue de la conversation')).toBeVisible();
    await expect(page.getByRole('region', { name: 'Réponses données' })).toContainText(
      'Les employés du siège et les visiteurs.',
    );
    await expect(page.locator('sw-attachments').getByRole('listitem')).toContainText('parking.html');
    await expectNoA11yViolations(page);
  });

  test('C6 : la conversation montre la spécification, se renomme et se supprime', async () => {
    await page.getByRole('link', { name: /E2E-AGENT/ }).first().click();
    await expect(page).toHaveURL(new RegExp(`${conversationUrl}$`));
    await expect(page.getByText('Spécifications issues de cette conversation')).toBeVisible();

    const list = page.locator('sw-conversation-list');
    await list.getByRole('button', { name: /^Renommer E2E-AGENT/ }).click();
    await list.getByLabel('Nom de la conversation').fill('Parking');
    await list.getByRole('button', { name: 'Enregistrer' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Parking' })).toBeVisible();

    await list.getByRole('button', { name: 'Supprimer Parking' }).click();
    await expectNoA11yViolations(page);
    await list.getByRole('button', { name: 'Supprimer', exact: true }).click();
    await expect(page).toHaveURL(/\/$/);
    await expect(list.getByRole('link', { name: 'Parking' })).toHaveCount(0);
    // la spécification reste
    expect((await page.request.get(`/api/sessions/${sessionId}`)).status()).toBe(200);
  });

  test('C7 : les spécifications portent la recherche et la nouvelle spécification', async () => {
    await page.getByRole('link', { name: 'Spécifications' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Spécifications' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Nouvelle spécification' })).toBeVisible();
    await expect(page.getByRole('search')).toBeVisible();
    await page.goto('/recherche');
    await expect(page.getByRole('heading', { level: 1, name: 'Conversation' })).toBeVisible();
    await expectNoA11yViolations(page);
  });
});
