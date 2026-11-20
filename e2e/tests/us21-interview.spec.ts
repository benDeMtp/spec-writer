import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, storageStatePath } from './support/env';

const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';
const NEED =
  'Les assistantes doivent pouvoir réserver une salle de réunion pour un comité, et voir les salles libres du jour.';

/**
 * 021 : séance de relecture avec les sachants (quickstart V1), en lecture du document. La première
 * question est passée : son hypothèse se confirme d'un clic.
 */
test.describe.serial('021 interview d’une spécification', () => {
  let context: BrowserContext;
  let page: Page;
  let sessionId = '';
  const headers = { Origin: baseURL };

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    await ensureCopilotConnected(page.request);
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: true } });
    await page.request.put('/api/projects/current', { headers, data: { projectId: DEFAULT_PROJECT } });
    let session = await (await page.request.post('/api/sessions', { headers, data: { need: NEED } })).json();
    sessionId = session.id;
    let first = true;
    while (session.currentBatch.length) {
      const q = session.currentBatch[0];
      const data = first ? { skip: true } : { value: q.defaultValue ?? q.suggestions[0] ?? 'Oui' };
      first = false;
      session = await (
        await page.request.put(`/api/sessions/${sessionId}/questions/${q.id}/answer`, { headers, data })
      ).json();
    }
    expect((await page.request.post(`/api/sessions/${sessionId}/versions`, { headers })).status()).toBe(201);
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('V1 : faire le point, répondre, noter une remarque et une question, reprendre', async () => {
    await page.goto(`/sessions/${sessionId}/versions/1`);
    const start = page.getByRole('button', { name: /^Faire le point/ });
    await expect(start).toBeVisible();
    await start.click();
    await expect(page).toHaveURL(/\/seances\/[0-9a-f-]+$/);

    // présentation : sans menu, la séance seule
    await expect(page.getByRole('navigation', { name: 'Navigation principale' })).toHaveCount(0);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expectNoA11yViolations(page);

    // la spécification se déroule en document ; le prochain point à traiter est l'hypothèse de la
    // question passée, que « Confirmer » valide d'un clic
    await page.getByRole('button', { name: 'Prochain point à traiter' }).click();
    const card = page.locator('sw-point-card:focus');
    await expect(card).toContainText('Hypothèse H-01');
    await card.getByRole('button', { name: 'Confirmer' }).click();
    await expect(page.locator('.progress')).toContainText('1 traité');
    await expect(page.getByRole('region', { name: 'Hypothèse H-01' })).toContainText('Confirmé');

    // « Noter » sur le résumé : une remarque, puis une question avec son responsable
    const summary = page.locator('[data-anchor="SUMMARY"]');
    await summary.getByRole('button', { name: /^Noter/ }).click();
    await summary.getByRole('toolbar').getByRole('button', { name: 'Commenter' }).click();
    await page.getByRole('textbox', { name: 'Ce qui a été dit' }).fill('Validation du manager au-delà de 500 €');
    await page.getByRole('button', { name: 'Enregistrer' }).click();
    await expect(page.locator('.progress')).toContainText('1 note');
    await summary.getByRole('button', { name: /^Noter/ }).click();
    await summary.getByRole('toolbar').getByRole('button', { name: 'Question…' }).click();
    await page.getByRole('textbox', { name: 'Question' }).fill('Qui valide en l’absence du manager ?');
    await page.getByLabel('Responsable').fill('Paul D.');
    await page.getByRole('button', { name: 'Enregistrer' }).click();
    await expect(page.locator('.progress')).toContainText('2 notes');

    // un passage sélectionné se supprime d'un clic, et la remarque le cite
    const step = page.locator('[data-anchor="UC-01:1"]');
    const line = step.locator('.line');
    const passage = ((await line.textContent()) ?? '').trim();
    await line.selectText();
    await line.dispatchEvent('mouseup');
    await step.getByRole('toolbar').getByRole('button', { name: 'Supprimer' }).click();
    await expect(page.locator('.progress')).toContainText('3 notes');
    await expect(step.getByRole('region', { name: 'Remarque' })).toContainText(`« ${passage} »`);
    await expectNoA11yViolations(page);

    // reprise après rechargement : rien de perdu, le focus revient au dernier point
    await page.reload();
    await expect(page.locator('.progress')).toContainText('1 traité');
    await expect(page.locator('.progress')).toContainText('3 notes');
    await expect(page.getByText('Qui valide en l’absence du manager ?')).toBeVisible();
    await expect(page.locator('sw-point-card:focus')).toHaveCount(1);

    // depuis la page de la spécification, la séance se reprend
    await page.goto(`/sessions/${sessionId}/versions/1`);
    await expect(page.getByRole('button', { name: 'Reprendre la séance' })).toBeVisible();
  });

  test('V2 : terminer, copier le compte rendu, le retrouver sur la spécification', async () => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: baseURL });
    await page.getByRole('button', { name: 'Reprendre la séance' }).click();
    await page.getByRole('button', { name: 'Terminer' }).click();
    await expect(page).toHaveURL(/\/seances\/[0-9a-f-]+\/recapitulatif$/);
    await expect(page.getByRole('heading', { name: 'Récapitulatif de la séance' })).toBeVisible();
    await expectNoA11yViolations(page);

    await page.getByRole('button', { name: 'Copier le compte rendu' }).click();
    await expect(page.getByText('Compte rendu copié.')).toBeVisible();
    const copied = await page.evaluate(() => navigator.clipboard.readText());
    expect(copied).toContain('Point sur « ');
    expect(copied).toContain('H-01');
    expect(copied).toContain('à modifier : Validation du manager au-delà de 500 €');
    expect(copied).toContain('Qui valide en l’absence du manager ? (Paul D.)');
    expect(copied).toMatch(/étape 1, « .+ » — à retirer/);

    // le compte rendu reste consultable depuis la spécification (FR-012)
    await page.goto(`/sessions/${sessionId}/versions/1`);
    const seances = page.getByRole('region', { name: 'Séances' }).or(page.locator('section[aria-labelledby="interviews-title"]'));
    await expect(seances.getByRole('link', { name: 'Compte rendu' })).toBeVisible();
    await expect(page.getByRole('button', { name: /^Faire le point/ })).toBeVisible();
  });

  test('V3 : proposer la nouvelle version, relire le diff, appliquer', async () => {
    await page.goto(`/sessions/${sessionId}/versions/1`);
    await page.locator('section[aria-labelledby="interviews-title"]').getByRole('link', { name: 'Compte rendu' }).click();
    await expect(page).toHaveURL(/\/seances\/[0-9a-f-]+\/recapitulatif$/);
    await page.getByRole('button', { name: /^Proposer la nouvelle version/ }).click();

    // la page de la spécification suit la rédaction, puis la proposition se relit à côté du document
    await expect(page).toHaveURL(new RegExp(`/sessions/${sessionId}/versions/1\\?discussion=1`));
    const review = page.locator('sw-proposal-review');
    await expect(page.getByRole('heading', { name: 'Nouvelle version proposée' })).toBeVisible();
    await expect(page.locator('sw-discussion-panel [role="log"]')).toContainText('Décisions à intégrer');
    await expectNoA11yViolations(page);
    await review.getByRole('button', { name: 'Appliquer' }).click();
    await expect(page).toHaveURL(new RegExp(`/sessions/${sessionId}/versions/2`));

    // l'hypothèse confirmée a disparu, la question reportée est un point ouvert, la séance est la source
    await expect(page.locator('section[aria-labelledby="assumptions"]')).not.toContainText('H-01');
    const open = page.locator('section[aria-labelledby="open-questions"]');
    await expect(open).toContainText('Q-01');
    await expect(open).toContainText('Qui valide en l’absence du manager ? (responsable : Paul D.)');
    await expect(page.locator('#UC-01')).toContainText('Séance du ');

    // la séance le sait : sa révision ne se propose plus
    await page.goto(`/sessions/${sessionId}/versions/2`);
    await page.locator('section[aria-labelledby="interviews-title"]').getByRole('link', { name: 'Compte rendu' }).click();
    await expect(page.getByText('Version 2 appliquée.')).toBeVisible();
  });

  test('V4 : une hypothèse rattachée se relit sur son élément', async () => {
    let session = await (
      await page.request.post('/api/sessions', {
        headers,
        data: { need: 'E2E-ANCRES Les employés doivent pouvoir réserver une salle de réunion depuis leur poste.' },
      })
    ).json();
    const id = session.id;
    while (session.currentBatch.length) {
      const q = session.currentBatch[0];
      session = await (
        await page.request.put(`/api/sessions/${id}/questions/${q.id}/answer`, {
          headers,
          data: { value: q.defaultValue ?? q.suggestions[0] ?? 'Oui' },
        })
      ).json();
    }
    expect((await page.request.post(`/api/sessions/${id}/versions`, { headers })).status()).toBe(201);

    // la spécification affiche l'élément de l'hypothèse, avec un lien qui y mène
    await page.goto(`/sessions/${id}/versions/1`);
    const element = page.locator('section[aria-labelledby="assumptions"] a.element');
    await expect(element).toHaveText('UC-01, étape 1');
    await element.click();
    await expect(page.locator('#UC-01-etape-1')).toBeFocused();

    // la séance la présente sur l'étape, pas dans « Non rattachés »
    await page.getByRole('button', { name: /^Faire le point/ }).click();
    await expect(page).toHaveURL(/\/seances\/[0-9a-f-]+$/);
    await page.getByRole('button', { name: 'Prochain point à traiter' }).click();
    const card = page.locator('[data-anchor="UC-01:1"] sw-point-card:focus');
    await expect(card).toContainText('Hypothèse H-01');
    await expect(page.getByText('Non rattachés')).toHaveCount(0);
  });

  test('V5 : préparer une séance sur deux spécifications, en écartant un point', async () => {
    let created = await (
      await page.request.post('/api/sessions', {
        headers,
        data: { need: 'E2E-ANCRES Les employés doivent pouvoir réserver une salle de réunion depuis leur poste.' },
      })
    ).json();
    const other = created.id;
    while (created.currentBatch.length) {
      const q = created.currentBatch[0];
      created = await (
        await page.request.put(`/api/sessions/${other}/questions/${q.id}/answer`, {
          headers,
          data: { value: q.defaultValue ?? q.suggestions[0] ?? 'Oui' },
        })
      ).json();
    }
    expect((await page.request.post(`/api/sessions/${other}/versions`, { headers })).status()).toBe(201);
    // des noms uniques : plusieurs spécifications de la suite naissent du même besoin
    const names: Record<string, string> = { [sessionId]: 'Salles, séance V5', [other]: 'Ancres, séance V5' };
    for (const [id, name] of Object.entries(names)) {
      expect((await page.request.patch(`/api/sessions/${id}`, { headers, data: { name } })).ok()).toBe(true);
    }
    const nameOf = (id: string) => names[id];

    // la page « Spécifications » : points à traiter, sélection, préparation
    await page.goto('/sessions');
    await page.getByRole('checkbox', { name: `Sélectionner « ${nameOf(sessionId)} » pour une séance` }).check();
    await page.getByRole('checkbox', { name: `Sélectionner « ${nameOf(other)} » pour une séance` }).check();
    await page.getByRole('button', { name: 'Préparer une séance (2)' }).click();
    await expect(page).toHaveURL(/\/seances\/[0-9a-f-]+\/preparation$/);
    const interviewId = page.url().split('/').at(-2)!;
    await expect(page.getByRole('heading', { name: 'Préparer la séance' })).toBeVisible();
    await expectNoA11yViolations(page);
    const second = page.locator('.specs > li').filter({ hasText: nameOf(other) });
    await second.getByRole('button', { name: 'Écarter de cette séance' }).first().click();
    await page.getByRole('button', { name: 'Enregistrer', exact: true }).click();
    await expect(page).toHaveURL(/\/sessions$/);

    // la séance préparée attend en tête de la page, même après un rechargement
    await page.reload();
    const prepared = page.locator('section[aria-labelledby="prepared-title"]');
    await expect(prepared).toContainText('Séance préparée : 2 spécifications');
    await prepared.getByRole('button', { name: 'Démarrer' }).click();
    await expect(page).toHaveURL(new RegExp(`/seances/${interviewId}$`));
    await expect(page.locator('.spec-pos')).toHaveText('Spéc. 1 sur 2');

    // le point écarté n'est pas dans le parcours ; une seconde séance sur une spécification occupée est refusée
    const detail = await (await page.request.get(`/api/interviews/${interviewId}`)).json();
    const items = detail.stops
      .filter((s: { sessionId: string }) => s.sessionId === other)
      .flatMap((s: { items: { code: string }[] }) => s.items.map((i) => i.code));
    expect(items).not.toContain('H-01');
    const busy = await page.request.post(`/api/sessions/${other}/interviews`, { headers, data: {} });
    expect(busy.status()).toBe(409);
    expect((await busy.json()).code).toBe('INTERVIEW_BUSY');

    // le compte rendu est en deux parties, une par spécification
    // des points restent à traiter : la fin se confirme
    await page.getByRole('button', { name: 'Terminer' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Terminer la séance' }).click();
    await expect(page).toHaveURL(/\/recapitulatif$/);
    await expect(page.locator('.preview h3')).toHaveCount(2);
  });
});

