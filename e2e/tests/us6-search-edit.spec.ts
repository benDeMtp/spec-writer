import { expect, request, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, jiraURL, storageStatePath } from './support/env';

const ISSUE = '/rest/api/2/issue/PROJ-102';

/** US6 : recherche unifiée, évolution assistée d'un ticket et envoi vers Jira (V12 à V14). */
test.describe.serial('US6 recherche et modification assistée', () => {
  let context: BrowserContext;
  let page: Page;
  let jira: APIRequestContext;
  let sessionUrl: string;

  async function puts(): Promise<number> {
    const r = await jira.post('/__admin/requests/count', { data: { method: 'PUT', url: ISSUE } });
    return (await r.json()).count;
  }

  async function stub(mapping: object): Promise<void> {
    expect((await jira.post('/__admin/mappings', { data: { priority: 1, ...mapping } })).status()).toBe(201);
  }

  async function dialogOpen(): Promise<void> {
    // axe mesure des couleurs en transition tant que l'animation d'ouverture n'est pas finie
    await expect(page.locator('.mat-mdc-dialog-container').last()).toHaveClass(/mdc-dialog--open/);
    await expect(page.locator('.mat-mdc-dialog-container').last()).not.toHaveClass(/mdc-dialog--opening/);
  }

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    jira = await request.newContext({ baseURL: jiraURL });
    await jira.post('/__admin/mappings/reset');
    await jira.delete('/__admin/requests');
    const headers = { Origin: baseURL };
    await ensureCopilotConnected(page.request);
    const saved = await page.request.put('/api/settings/jira', {
      headers,
      data: { serverUrl: jiraURL, defaultProjectKey: 'PROJ', acceptInsecureHttp: true, token: 'e2e-good-token' },
    });
    expect(saved.status()).toBe(200);
    for (const service of ['JIRA', 'COPILOT']) {
      await page.request.put(`/api/consents/${service}`, { headers, data: { granted: true } });
    }
  });

  test.afterAll(async () => {
    await jira.post('/__admin/mappings/reset');
    await jira.dispose();
    await context.close();
  });

  test('V12 : la recherche liste les tickets Jira avec leur provenance', async () => {
    // la recherche est dans l'écran des spécifications (010, US5)
    await page.goto('/sessions');
    await page.getByLabel('Mots-clés').fill('parking');
    await page.getByRole('button', { name: 'Rechercher' }).click();
    await expect(page).toHaveURL(/\?q=parking$/);
    const rows = page.locator('tr.mat-mdc-row');
    await expect(rows.filter({ hasText: 'PROJ-102' })).toContainText('Jira');
    await expect(rows.filter({ hasText: 'PROJ-103' })).toContainText('Parking des vélos');
    await expectNoA11yViolations(page);
  });

  test('US6-3 : un ticket affiche sa description complète', async () => {
    await page.getByRole('link', { name: 'Parking visiteurs' }).click();
    await expect(page).toHaveURL(/\/jira\/PROJ-102$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Parking visiteurs' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Besoin' })).toBeVisible();
    await expect(page.getByText('réserver une place du parking visiteurs')).toBeVisible();
    await expectNoA11yViolations(page);
  });

  test("V12a : la syntaxe Jira est interprétée (tableau, lien, image)", async () => {
    const description = page.getByRole('article', { name: 'Description du ticket' });
    const table = description.getByRole('table');
    await expect(table.getByRole('columnheader', { name: 'Créneau' })).toBeVisible();
    await expect(table.getByRole('row', { name: /Après-midi\s*8/ })).toBeVisible();
    const link = description.getByRole('link', { name: 'règlement du parking' });
    await expect(link).toHaveAttribute('href', 'https://intranet.example/parking');
    await expect(link).toHaveAttribute('target', '_blank');
    await expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    const image = description.getByRole('link', { name: 'image : plan-parking.png' });
    await expect(image).toHaveAttribute('href', `${jiraURL}/browse/PROJ-102`);
    await expect(description.locator('img')).toHaveCount(0);
    await expectNoA11yViolations(page);
  });

  test("US6-4 et US6-5 : faire évoluer avec l'IA, sans écrire dans Jira", async () => {
    await page.getByRole('button', { name: "Faire évoluer avec l'IA" }).click();
    await expect(page).toHaveURL(/\/sessions\/[^/]+\/versions\/1\?edit=1$/);
    sessionUrl = new URL(page.url()).pathname.replace(/\/versions\/1$/, '');
    await expect(page.getByRole('heading', { level: 1, name: 'Réservation du parking visiteurs' })).toBeVisible();
    await expect(page.getByText('Liée au ticket')).toContainText('PROJ-102');
    await expect(page.getByText('Liée au ticket')).toContainText('à jour avec Jira');

    // FR-012b et FR-012c : le tableau écrit en wiki par le modèle est un vrai tableau, et le saut
    // de ligne d'un champ simple est conservé
    const details = page.getByRole('region', { name: 'Détails' });
    await expect(details.getByRole('columnheader', { name: 'Créneau' })).toBeVisible();
    await expect(details.getByRole('row', { name: /Après-midi\s+3/ })).toBeVisible();
    await expect(details).not.toContainText('||');
    expect(await page.locator('dd', { hasText: 'le jour venu' }).first().innerText()).toBe("Disposer d'une place\nle jour venu");

    await page.getByLabel("Ce que l'IA doit changer").fill("Ajoute l'annulation jusqu'à la veille E2E-EDIT");
    await page.getByRole('button', { name: 'Proposer une modification' }).click();
    const diff = page.getByRole('region', { name: 'Différences avec la version actuelle' });
    await expect(diff).toBeVisible();
    await expect(page.getByText(/lignes? ajoutées?/)).toBeVisible();
    await expect(diff.locator('li.added').filter({ hasText: "Le salarié annule sa réservation jusqu'à la veille" }))
      .toContainText('Ajouté :');
    await expectNoA11yViolations(page);

    await page.getByRole('button', { name: 'Appliquer' }).click();
    await expect(page).toHaveURL(new RegExp(`${sessionUrl}/versions/2$`));
    await expect(page.getByRole('heading', { level: 1, name: 'Réservation du parking visiteurs avec annulation' }))
      .toBeVisible();
    await expect(page.getByText('Liée au ticket')).toContainText('modifications à envoyer');
    expect(await puts()).toBe(0);
  });

  test("V12 : l'élément lié n'apparaît qu'une fois, avec la provenance « les deux »", async () => {
    await page.goto('/sessions?q=parking');
    const rows = page.locator('tr.mat-mdc-row');
    await expect(rows.filter({ hasText: 'PROJ-103' })).toBeVisible();
    await expect(rows.filter({ hasText: 'PROJ-102' })).toHaveCount(1);
    await expect(rows.filter({ hasText: 'PROJ-102' })).toContainText('les deux');
    await rows.filter({ hasText: 'PROJ-102' }).getByRole('link').click();
    await expect(page).toHaveURL(new RegExp(`${sessionUrl}/versions/2$`));
  });

  test('V13 : un ticket modifié dans Jira est signalé, et la fusion par l’IA est envoyée', async () => {
    await stub({
      request: { method: 'GET', urlPath: ISSUE },
      response: {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        jsonBody: {
          key: 'PROJ-102',
          fields: {
            summary: 'Parking visiteurs',
            description: 'Les visiteurs reçoivent un badge à l’accueil. E2E-MERGE',
            updated: '2026-10-30T10:00:00.000+0000',
          },
        },
      },
    });
    await page.getByRole('button', { name: 'Envoyer vers Jira' }).click();
    const preview = page.getByRole('dialog', { name: 'Envoyer la version 2 vers Jira ?' });
    await expect(preview.getByLabel('Description envoyée à Jira')).toContainText(
      'h1. Réservation du parking visiteurs avec annulation',
    );
    await expect(preview.getByLabel('Description envoyée à Jira')).toContainText('||Créneau||Places||');
    await dialogOpen();
    await expectNoA11yViolations(page);
    await preview.getByRole('button', { name: 'Envoyer vers Jira' }).click();

    const conflict = page.getByRole('dialog', { name: 'Le ticket a été modifié dans Jira' });
    await expect(conflict.getByRole('region', { name: 'Version Jira' })).toContainText('badge à l’accueil');
    await expect(conflict.getByRole('region', { name: 'Votre version' })).toContainText('annulation');
    for (const choice of ['Écraser avec ma version', 'Reprendre la version Jira', "Fusionner avec l'IA"]) {
      await expect(conflict.getByRole('button', { name: choice })).toBeVisible();
    }
    await dialogOpen();
    await expectNoA11yViolations(page);
    expect(await puts()).toBe(0);
    await expect(page.getByText('Liée au ticket')).toContainText('en conflit avec Jira');

    await conflict.getByRole('button', { name: "Fusionner avec l'IA" }).click();
    await expect(page.getByRole('heading', { name: 'Proposition de l’IA' })).toBeVisible();
    await page.getByRole('button', { name: 'Appliquer' }).click();
    await expect(page).toHaveURL(new RegExp(`${sessionUrl}/versions/3$`));
    await expect(page.getByRole('heading', { level: 1, name: 'Réservation du parking visiteurs fusionnée' })).toBeVisible();

    await stub({ request: { method: 'PUT', urlPath: ISSUE }, response: { status: 204 } });
    await page.getByRole('button', { name: 'Envoyer vers Jira' }).click();
    await page.getByRole('dialog', { name: 'Envoyer la version 3 vers Jira ?' })
      .getByRole('button', { name: 'Envoyer vers Jira' }).click();
    await expect(page.getByText('Version 3 envoyée vers PROJ-102.')).toBeVisible();
    await expect(page.getByText('Liée au ticket')).toContainText('à jour avec Jira');
    expect(await puts()).toBe(1);
  });

  test("V14 : Jira coupé, les résultats spec-writer s'affichent avec un message", async () => {
    await stub({ request: { method: 'GET', urlPath: '/rest/api/2/search' }, response: { fault: 'CONNECTION_RESET_BY_PEER' } });
    await page.goto('/sessions?q=parking');
    await expect(page.getByText("Jira n'a pas répondu : seuls les résultats spec-writer sont affichés.")).toBeVisible();
    await expect(page.locator('tr.mat-mdc-row').filter({ hasText: 'PROJ-102' })).toBeVisible();
    await expect(page.locator('tr.mat-mdc-row').filter({ hasText: 'PROJ-103' })).toHaveCount(0);
    await expectNoA11yViolations(page);
  });
});
