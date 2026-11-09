import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, storageStatePath } from './support/env';

const NEED =
  'Les comptables doivent pouvoir lettrer les virements reçus chaque jour avec les factures émises, ' +
  'sans saisie manuelle.';
const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';
const EXCERPT = 'Le délai de lettrage est de 3 jours ouvrés';

/**
 * 009 : joindre un document à une spécification (quickstart 1, 2, 5, 6, 10). Les fichiers sont
 * écrits dans un dossier temporaire, hors de tout dossier synchronisé ; seul leur texte est gardé.
 */
test.describe.serial('009 joindre un document', () => {
  let context: BrowserContext;
  let page: Page;
  let files: string;
  const headers = { Origin: baseURL };

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    // export par téléchargement, sans le sélecteur d'enregistrement du navigateur (comme us4)
    await context.addInitScript(() => {
      delete (window as unknown as Record<string, unknown>)['showSaveFilePicker'];
    });
    page = await context.newPage();
    await ensureCopilotConnected(page.request);
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: true } });
    await page.request.put('/api/projects/current', { headers, data: { projectId: DEFAULT_PROJECT } });
    files = mkdtempSync(join(tmpdir(), 'spec-writer-joint-'));
    writeFileSync(
      join(files, 'Regles lettrage.html'),
      `<html><head><title>Règles de lettrage</title></head><body><h1>Règles de lettrage</h1><p>${EXCERPT}.</p>` +
        '<p>Un virement sans facture correspondante reste à lettrer.</p></body></html>',
    );
    writeFileSync(
      join(files, 'Annexe.html'),
      '<html><head><title>Annexe</title></head><body><h1>Annexe</h1><p>Les avoirs se lettrent comme les factures.</p></body></html>',
    );
    writeFileSync(join(files, 'ancien.doc'), 'ancien format Word');
  });

  test.afterAll(async () => {
    // us2 vérifie la demande de consentement d'un premier envoi : l'accord donné ici est retiré
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: false } });
    await context.close();
  });

  const attachments = () => page.locator('sw-attachments');
  const picker = () => attachments().locator('input[type="file"]');

  test('J1 : joindre au besoin, refus expliqué, état, démarrage', async () => {
    await page.goto('/nouveau');
    await page.getByLabel('Votre besoin').fill(NEED);

    await picker().setInputFiles(join(files, 'ancien.doc'));
    await expect(attachments().getByRole('status')).toContainText('« ancien.doc » n\'est pas joint');

    await picker().setInputFiles(join(files, 'Regles lettrage.html'));
    const item = attachments().getByRole('listitem');
    await expect(item).toContainText('Règles de lettrage');
    await expect(item).toContainText('Regles lettrage.html');
    await expect(item).toContainText('transmis à l’IA');
    await expectNoA11yViolations(page);

    await page.getByRole('button', { name: 'Commencer le questionnaire' }).click();
    await expect(page).toHaveURL(/\/sessions\/[0-9a-f-]{36}$/);
    await expect(page.getByRole('heading', { level: 2, name: 'Quand le lettrage doit-il être lancé ?' })).toBeVisible();
    await expect(attachments().getByRole('listitem')).toContainText('Regles lettrage.html');
  });

  test('J2 : joindre puis retirer depuis le questionnaire', async () => {
    await picker().setInputFiles(join(files, 'Annexe.html'));
    await expect(attachments().getByRole('listitem')).toHaveCount(2);
    await attachments().getByRole('button', { name: 'Retirer Annexe' }).click();
    await expect(attachments().getByRole('listitem')).toHaveCount(1);
    await expect(attachments().getByRole('status')).toContainText('« Annexe » est retiré.');
    await expectNoA11yViolations(page);
  });

  test('J3 : générer ; le document est cité par son nom, sans extrait, ni à l’écran ni à l’export', async () => {
    const card = page.locator('sw-question-card').filter({ hasText: 'Quand le lettrage' });
    await card.getByRole('option', { name: 'Chaque matin' }).click();
    await card.getByRole('button', { name: 'Enregistrer la réponse' }).click();
    await expect(page.getByRole('heading', { name: 'Questionnaire terminé' })).toBeVisible();
    await page.getByRole('button', { name: 'Générer la spécification' }).click();

    await expect(page).toHaveURL(/\/versions\/1$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Lettrage des virements reçus' })).toBeVisible();
    const source = page.locator('dd.source').first();
    await expect(source).toContainText('Document joint');
    await expect(source).toContainText('Regles lettrage.html');
    await expect(source.locator('a')).toHaveCount(0);
    await expect(attachments().getByRole('listitem')).toContainText('Regles lettrage.html');
    await expectNoA11yViolations(page);

    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Exporter en Markdown' }).click();
    const markdown = readFileSync((await (await download).path())!, 'utf8');
    expect(markdown).toContain('document joint Regles lettrage.html');
    expect(markdown).not.toContain(EXCERPT);
  });
});
