import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, storageStatePath } from './support/env';

const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';
const NEED =
  'Les assistantes doivent pouvoir réserver une salle de réunion pour un comité, et voir les salles libres du jour.';

/**
 * 011 : discuter d'une spécification avec l'IA (quickstart 1, 2, 5 à 8, 10, 12, 15). L'IA est
 * rejouée (fixtures 40 à 49) : réponse qui cite une section, proposition qui cite le message de
 * la discussion, aucun changement établi, abandon, nouvelle discussion, suppression qui garde
 * les discussions.
 */
test.describe.serial('011 discussion d’une spécification', () => {
  let context: BrowserContext;
  let page: Page;
  let sessionId = '';
  let firstConversation = '';
  const headers = { Origin: baseURL };

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    await ensureCopilotConnected(page.request);
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: true } });
    await page.request.put('/api/projects/current', { headers, data: { projectId: DEFAULT_PROJECT } });
    let session = await (await page.request.post('/api/sessions', { headers, data: { need: NEED } })).json();
    sessionId = session.id;
    while (session.currentBatch.length) {
      const q = session.currentBatch[0];
      session = await (
        await page.request.put(`/api/sessions/${sessionId}/questions/${q.id}/answer`, {
          headers,
          data: { value: q.defaultValue ?? q.suggestions[0] ?? 'Oui' },
        })
      ).json();
    }
    expect((await page.request.post(`/api/sessions/${sessionId}/versions`, { headers })).status()).toBe(201);
  });

  test.afterAll(async () => {
    await page.request.delete(`/api/sessions/${sessionId}`, { headers });
    for (const c of await (await page.request.get(`/api/projects/${DEFAULT_PROJECT}/conversations`)).json()) {
      if ((c.name as string).startsWith('Discussion : ')) {
        await page.request.delete(`/api/conversations/${c.id}`, { headers });
      }
    }
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: false } });
    await context.close();
  });

  const panel = () => page.locator('sw-discussion-panel');
  const composer = () => panel().getByLabel('Votre question');
  const answers = () => panel().locator('li.answer');

  async function say(text: string, answered: number): Promise<void> {
    await composer().fill(text);
    await composer().press('Enter');
    await expect(answers()).toHaveCount(answered);
    await expect(answers().last().locator('.note')).not.toBeEmpty();
  }

  test('D1 : « Discuter avec l’IA » ouvre la discussion à côté du document', async () => {
    await page.goto(`/sessions/${sessionId}/versions/1`);
    const discuss = page.getByRole('button', { name: "Discuter avec l'IA" });
    await expect(discuss).toHaveAttribute('aria-expanded', 'false');
    await discuss.click();
    await expect(discuss).toHaveAttribute('aria-expanded', 'true');
    await expect(panel().getByRole('heading', { name: 'Discussion avec l’IA' })).toBeVisible();
    await expect(panel()).toContainText(/Contexte : .+, version 1/);
    await expect(composer()).toBeFocused();
    await expect(panel().getByRole('button', { name: 'Proposer une nouvelle version' })).toBeDisabled();
    await expectNoA11yViolations(page);
  });

  test('D2 : la réponse cite une section, qui s’affiche dans le document', async () => {
    await say('E2E-DISCUSSION Que dit la spécification de la réservation ?', 1);
    await expect(answers().first().locator('.note')).toContainText('premier use case');
    await expect(panel().locator('.context-mark').first()).toHaveText('Contexte : version 1 de la spécification');
    await answers().first().locator('.note a').first().click();
    await expect(page).toHaveURL(new RegExp(`/sessions/${sessionId}/versions/1\\?discussion=1$`));
    await expect(page.locator('#UC-01')).toBeFocused();
  });

  test('D3 : proposer une nouvelle version, la relire, l’appliquer', async () => {
    await say("E2E-DISCUSSION E2E-ANNULATION Ajoutons l'annulation jusqu'à la veille.", 2);
    await panel().getByRole('button', { name: 'Proposer une nouvelle version' }).click();
    const picker = panel().getByRole('group', { name: 'Changements à intégrer' });
    const said = picker.getByRole('checkbox', { name: /Ajouter l'annulation d'une réservation/ });
    await expect(said).toBeFocused();
    await expect(said).toBeChecked();
    await expect(picker).toContainText('dit par vous');
    const proposedByAgent = picker.getByRole('checkbox', { name: /Prévenir le demandeur/ });
    await expect(proposedByAgent).toBeChecked();
    await expect(picker).toContainText('proposé par l’agent');
    await expectNoA11yViolations(page);
    await proposedByAgent.uncheck();
    await picker.getByRole('button', { name: 'Rédiger la version' }).click();
    // les changements retenus entrent dans la discussion, au nom de la personne
    await expect(panel().locator('[role="log"]')).toContainText(
      "Changements retenus pour la nouvelle version : - Ajouter l'annulation d'une réservation jusqu'à la veille",
    );
    await expect(panel().locator('[role="log"]')).not.toContainText('Prévenir le demandeur');
    const review = page.locator('sw-proposal-review');
    await expect(page.getByRole('heading', { name: 'Nouvelle version proposée' })).toBeFocused();
    await expect(review).toContainText('elle deviendra la version 2');
    await expect(review.locator('li.added').filter({ hasText: 'Annuler une réservation' }).first()).toBeVisible();
    await expect(panel()).toContainText('Une proposition attend votre relecture');
    await expectNoA11yViolations(page);

    await review.getByRole('button', { name: 'Appliquer' }).click();
    await expect(page).toHaveURL(new RegExp(`/sessions/${sessionId}/versions/2\\?discussion=1$`));
    await expect(panel()).toContainText(/Contexte : .+, version 2/);
    const versions = page.getByRole('navigation', { name: 'Versions de la spécification' });
    const origin = versions.getByRole('link', { name: 'Issue de la discussion' });
    await expect(origin).toHaveAttribute('href', /^\/conversations\/[0-9a-f-]{36}$/);
    firstConversation = (await origin.getAttribute('href'))!.split('/').pop()!;
    await expect(page.locator('#UC-02')).toContainText('Établi dans la discussion :');
    await expect(page.locator('#UC-02').getByRole('link', { name: /Discussion du .+, message 3/ }))
      .toHaveAttribute('href', `/conversations/${firstConversation}`);
  });

  test('D4 : une discussion qui n’établit rien ne crée pas de version', async () => {
    await say('E2E-DISCUSSION E2E-SANS-CHANGEMENT Qui réserve les salles ?', 3);
    await panel().getByRole('button', { name: 'Proposer une nouvelle version' }).click();
    const picker = panel().getByRole('group', { name: 'Changements à intégrer' });
    await expect(picker).toContainText('L’agent n’a relevé aucun changement');
    await expect(picker.getByRole('checkbox')).toHaveCount(0);
    const other = picker.getByLabel('Autre changement');
    await expect(other).toBeFocused();
    await expect(picker.getByRole('button', { name: 'Rédiger la version' })).toBeDisabled();
    // Échap referme le choix, le focus revient au bouton
    await other.press('Escape');
    await expect(picker).toHaveCount(0);
    await expect(panel().getByRole('button', { name: 'Proposer une nouvelle version' })).toBeFocused();
    await panel().getByRole('button', { name: 'Proposer une nouvelle version' }).click();
    await picker.getByLabel('Autre changement').fill('Préciser qui réserve les salles');
    await picker.getByRole('button', { name: 'Rédiger la version' }).click();
    await expect(panel().locator('.unchanged')).toContainText(
      "Aucun changement établi par la discussion : La discussion n'a posé que des questions.",
    );
    const list = await (await page.request.get(`/api/sessions/${sessionId}/versions`)).json();
    expect(list).toHaveLength(2);
  });

  test('D5 : nouvelle discussion, proposition abandonnée', async () => {
    await panel().getByRole('button', { name: 'Nouvelle discussion' }).click();
    await expect(answers()).toHaveCount(0);
    await say("E2E-DISCUSSION E2E-ANNULATION Ajoutons l'annulation jusqu'à la veille.", 1);
    await panel().getByRole('button', { name: 'Proposer une nouvelle version' }).click();
    await panel().getByRole('button', { name: 'Rédiger la version' }).click();
    const review = page.locator('sw-proposal-review');
    await expect(review).toContainText('elle deviendra la version 3');
    await expect(page).toHaveURL(/\?discussion=1$/);
    // quitter l'écran perdrait la proposition : confirmation d'abord
    await page.getByRole('navigation', { name: 'Navigation principale' }).getByRole('link', { name: 'Conversation' }).click();
    const leave = page.getByRole('dialog', { name: 'Quitter sans appliquer la proposition ?' });
    await expect(leave).toBeVisible();
    await leave.getByRole('button', { name: 'Annuler' }).click();
    await expect(review).toBeVisible();
    await review.getByRole('button', { name: 'Abandonner' }).click();
    await expect(review).toHaveCount(0);
    await expect(page.getByText('Proposition abandonnée : la spécification est inchangée.')).toBeVisible();
    const list = await (await page.request.get(`/api/sessions/${sessionId}/versions`)).json();
    expect(list).toHaveLength(2);
  });

  test('D6 : une version plus ancienne affichée, la discussion porte sur la dernière', async () => {
    await page.goto(`/sessions/${sessionId}/versions/1?discussion=1`);
    await expect(panel().locator('.older')).toHaveText(
      'Vous consultez la version 1. La discussion porte sur la version 2, la plus récente.',
    );
    await page.getByRole('button', { name: 'Fermer' }).click();
    await expect(panel()).toHaveCount(0);
    await expect(page.getByRole('button', { name: "Discuter avec l'IA" })).toBeFocused();
  });

  test('D7 : les deux discussions dans la liste, gardées quand la spécification est supprimée', async () => {
    await page.goto(`/sessions/${sessionId}/versions/2`);
    await page.getByRole('button', { name: 'Supprimer la spécification' }).click();
    const dialog = page.getByRole('dialog', { name: 'Supprimer la spécification ?' });
    await expect(dialog).toContainText('Elle a 2 discussions avec l’IA.');
    const option = dialog.getByRole('checkbox', { name: 'Supprimer aussi ses discussions' });
    await expect(option).toBeChecked();
    // axe après l'animation d'ouverture du dialogue
    await expect(page.locator('.mat-mdc-dialog-container').last()).toHaveClass(/mdc-dialog--open/);
    await expect(page.locator('.mat-mdc-dialog-container').last()).not.toHaveClass(/mdc-dialog--opening/);
    await expectNoA11yViolations(page);
    await option.uncheck();
    await dialog.getByRole('button', { name: 'Supprimer la spécification' }).click();
    await expect(page).toHaveURL(/\/sessions$/);

    await page.goto(`/conversations/${firstConversation}`);
    const threads = page.getByRole('list', { name: 'Conversations' });
    await expect(threads.locator('.about').filter({ hasText: '(spécification supprimée)' })).toHaveCount(2);
    await expect(page.locator('.discussed')).toContainText('spécification supprimée');
    await expectNoA11yViolations(page);
  });
});
