import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { expectNoA11yViolations } from './support/a11y';
import { baseURL, stateDir, storageStatePath } from './support/env';

const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';

function html(title: string, body: string): string {
  return `<html><head><title>${title}</title></head><body><h1>${title}</h1><p>${body}</p></body></html>`;
}

/**
 * 017 : synchronisation automatique par source (US2). En E2E, une minute de délai vaut une seconde
 * (`%e2e.specwriter.knowledge.auto-sync.minute=1s`) et la tâche planifiée se réveille toutes les 2 s.
 */
test.describe.serial('017 Synchronisation automatique des sources', () => {
  let context: BrowserContext;
  let page: Page;
  let projectId = '';
  let folder = '';
  let docId = '';
  const headers = { Origin: baseURL };

  async function indexed(): Promise<void> {
    await expect
      .poll(
        async () => {
          const r = await page.request.get(`/api/projects/${projectId}/indexations/current`);
          return r.status() === 404 ? 'NONE' : (await r.json()).status;
        },
        { timeout: 120_000 },
      )
      .not.toBe('RUNNING');
  }

  async function state(): Promise<{ interval: string; last: { at: string; outcome: string; errorCode: string | null } | null }> {
    const list = await (await page.request.get(`/api/projects/${projectId}/source-sync`)).json();
    return list.find((s: { sourceKey: string }) => s.sourceKey === docId);
  }

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    const created = await page.request.post('/api/projects', { headers, data: { name: 'Synchro E2E' } });
    expect(created.status()).toBe(201);
    projectId = (await created.json()).id;
    await page.request.put('/api/projects/current', { headers, data: { projectId } });
    folder = mkdtempSync(join(tmpdir(), 'spec-writer-synchro-'));
    writeFileSync(join(folder, 'Demande.html'), html('Demande de congés', 'Un salarié pose ses congés en ligne.'));
    const doc = await page.request.post(`/api/projects/${projectId}/doc-sources`, {
      headers,
      data: { kind: 'DOCUMENTS', location: folder },
    });
    expect(doc.status()).toBe(201);
    docId = (await doc.json()).id;
    await indexed();
  });

  test.afterAll(async () => {
    await page.request.put('/api/projects/current', { headers, data: { projectId: DEFAULT_PROJECT } });
    await page.request.delete(`/api/projects/${projectId}?sessions=DELETE`, { headers });
    rmSync(folder, { recursive: true, force: true });
    await context.close();
  });

  test('S1 : un délai réglé, la source seule est relue à l’échéance et affiche son bilan', async () => {
    test.setTimeout(120_000);
    await page.goto('/projet/reglages');
    const region = page.getByRole('region', { name: 'Synchronisation des sources' });
    const row = region.getByTestId(`source-sync-${docId}`);
    await expect(row).toContainText('Désactivée');
    await expectNoA11yViolations(page);

    // le bilan de l'indexation à l'ajout de la source ; la synchronisation automatique en donne un plus récent
    const before = (await state()).last?.at;
    writeFileSync(join(folder, 'Plafond.html'), html('Plafond des congés', 'Au plus 25 jours par an.'));
    await row.getByRole('combobox', { name: 'Synchronisation automatique' }).click();
    await page.getByRole('option', { name: 'Toutes les 15 minutes' }).click();
    await expect(region.getByRole('status')).toContainText('toutes les 15 minutes');
    await expect(row).toContainText('Prochaine synchronisation');

    // 15 « minutes » de E2E : la source repart seule, en incrémental, avec le nouveau document
    await expect.poll(async () => (await state()).last?.at, { timeout: 60_000 }).not.toBe(before);
    expect((await state()).last?.outcome).toBe('CHANGED');
    await page.reload();
    await expect(row).toContainText('1 nouveau');
    await expect(row).toContainText('Dernière synchronisation');
    await expectNoA11yViolations(page);
  });

  test('S2 : une synchronisation manuelle met à jour la date et repart le délai', async () => {
    test.setTimeout(120_000);
    await indexed();
    const before = (await state()).last!.at;
    const started = await page.request.post(`/api/projects/${projectId}/indexations`, {
      headers,
      data: { mode: 'INCREMENTAL', sources: [docId] },
    });
    expect([202, 409]).toContain(started.status());
    await expect.poll(async () => (await state()).last!.at, { timeout: 60_000 }).not.toBe(before);
    expect((await state()).last!.outcome).toBe('UNCHANGED');
  });

  test('S3 : une source illisible est en échec, avec sa dernière réussite', async () => {
    test.setTimeout(120_000);
    rmSync(folder, { recursive: true, force: true });
    await expect.poll(async () => (await state()).last?.outcome, { timeout: 60_000 }).toBe('ERROR');
    await page.goto('/projet/reglages');
    const row = page.getByTestId(`source-sync-${docId}`);
    await expect(row).toContainText('Échec');
    await expect(row).toContainText('dernière réussite');
    // la tentative suivante attend le délai : pas de relance immédiate
    const failedAt = (await state()).last!.at;
    await page.waitForTimeout(5_000);
    expect((await state()).last!.at).toBe(failedAt);
  });

  // ---------- US3 : notifications du navigateur ----------

  /** Notification remplacée par un espion : titre, corps et tag de chaque notification affichée. */
  const SPY = () => {
    const shown: { title: string; body?: string; tag?: string }[] = [];
    (window as unknown as { __notes: typeof shown }).__notes = shown;
    class Spy {
      static permission: NotificationPermission = 'granted';
      static requestPermission(): Promise<NotificationPermission> {
        return Promise.resolve('granted');
      }
      onclick: (() => void) | null = null;
      constructor(title: string, options?: NotificationOptions) {
        shown.push({ title, body: options?.body, tag: options?.tag });
      }
      close(): void {
        // rien
      }
    }
    (window as unknown as { Notification: unknown }).Notification = Spy;
  };

  let tabs: BrowserContext;

  async function notes(pages: Page[]): Promise<{ title: string; body?: string; tag?: string }[]> {
    const all = await Promise.all(pages.map((p) => p.evaluate(() => (window as unknown as { __notes: [] }).__notes ?? [])));
    return all.flat();
  }

  async function resync(): Promise<void> {
    await indexed();
    const r = await page.request.post(`/api/projects/${projectId}/indexations`, {
      headers,
      data: { mode: 'INCREMENTAL', sources: [docId] },
    });
    expect(r.status()).toBe(202);
    await indexed();
  }

  test('N1 : activée, une source changée donne une notification qui la nomme', async () => {
    test.setTimeout(120_000);
    // la synchronisation automatique est coupée : seules les synchronisations du test comptent
    await page.request.put(`/api/projects/${projectId}/source-sync/${docId}`, { headers, data: { intervalMinutes: null } });
    mkdirSync(folder, { recursive: true });
    tabs = await page.context().browser()!.newContext({ baseURL, storageState: storageStatePath, permissions: ['notifications'] });
    await tabs.addInitScript(SPY);
    const first = await tabs.newPage();
    await first.goto('/configuration');
    const card = first.getByTestId('notifications-card');
    const box = card.getByRole('checkbox', { name: 'Prévenir quand une source est mise à jour' });
    await expect(box).not.toBeChecked();
    await box.check();
    await expect(card.getByRole('status')).toContainText('Notifications activées.');
    await expectNoA11yViolations(first);

    writeFileSync(join(folder, 'Nouveau.html'), html('Télétravail', 'Deux jours par semaine.'));
    await resync();
    await expect.poll(async () => (await notes([first])).length, { timeout: 10_000 }).toBe(1);
    const [note] = await notes([first]);
    expect(note.title).toContain('mise à jour');
    expect(note.body).toContain('Synchro E2E');
    expect(note.body).toContain('1 nouveau');
    expect(note.body).not.toContain('Télétravail');
  });

  test('N2 : deux onglets, une seule notification ; rien sans changement', async () => {
    test.setTimeout(120_000);
    const [first] = tabs.pages();
    const second = await tabs.newPage();
    await second.goto('/');
    await second.waitForTimeout(1_000);
    writeFileSync(join(folder, 'Autre.html'), html('Astreintes', 'Une semaine par mois.'));
    await resync();
    await expect.poll(async () => (await notes([first, second])).length, { timeout: 10_000 }).toBe(2);
    await second.waitForTimeout(2_000);
    expect((await notes([first, second])).length).toBe(2);

    await resync();
    await second.waitForTimeout(3_000);
    expect((await notes([first, second])).length).toBe(2);
  });

  test('N3 : désactivées, plus aucune notification ; ni contenu ni jeton au journal', async () => {
    test.setTimeout(120_000);
    const [first] = tabs.pages();
    await first.goto('/configuration');
    const before = (await notes(tabs.pages())).length;
    await first.getByRole('checkbox', { name: 'Prévenir quand une source est mise à jour' }).uncheck();
    await expect(first.getByTestId('notifications-card').getByRole('status')).toContainText('Notifications désactivées.');
    writeFileSync(join(folder, 'Dernier.html'), html('Formation', 'Trois jours par an.'));
    await resync();
    await first.waitForTimeout(3_000);
    expect((await notes(tabs.pages())).length).toBe(before);

    const journal = JSON.stringify(await (await page.request.get('/api/journal')).json());
    const log = readFileSync(join(stateDir, 'app.log'), 'utf8');
    for (const text of [journal, log]) {
      expect(text).not.toContain('Deux jours par semaine');
      expect(text).not.toContain('Une semaine par mois');
    }
    await tabs.close();
  });
});
