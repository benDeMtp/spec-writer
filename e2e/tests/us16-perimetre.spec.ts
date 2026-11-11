import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { copyDemoTree } from './support/demo-tree';
import { baseURL, stateDir, storageStatePath } from './support/env';

const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';

/** Modules de l'arbre qui contiennent un terme, `legacy/**` étant exclu (Q2). */
const FOUND = ['common-contract', 'demo-front', 'facturation', 'facturation-chart', 'annuaire', 'quarkus-contract'];

/**
 * 013 : périmètre des modules touchés (quickstart). L'arbre de démonstration est copié dans un
 * dossier temporaire avec ses pièges ; le projet Périmètre E2E est créé pour le test. L'IA est
 * rejouée (fixtures 50 à 59) : termes proposés, qualification qui omet demo-front, réponses.
 */
test.describe.serial('013 périmètre des modules touchés', () => {
  let context: BrowserContext;
  let page: Page;
  let projectId = '';
  let demo: ReturnType<typeof copyDemoTree>;
  const headers = { Origin: baseURL };

  test.beforeAll(async ({ browser }) => {
    demo = copyDemoTree();
    context = await browser.newContext({ baseURL, storageState: storageStatePath });
    page = await context.newPage();
    await ensureCopilotConnected(page.request);
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: true } });
    const created = await page.request.post('/api/projects', { headers, data: { name: 'Périmètre E2E' } });
    expect(created.status()).toBe(201);
    projectId = (await created.json()).id;
    await page.request.put('/api/projects/current', { headers, data: { projectId } });
  });

  test.afterAll(async () => {
    // us2 vérifie la demande de consentement d'un premier envoi : l'accord donné ici est retiré
    await page.request.put('/api/consents/COPILOT', { headers, data: { granted: false } });
    await page.request.put('/api/projects/current', { headers, data: { projectId: DEFAULT_PROJECT } });
    await page.request.delete(`/api/projects/${projectId}?sessions=DELETE`, { headers });
    await context.close();
    demo.dispose();
  });

  test('Q1 : la racine se déclare après un aperçu de ses quatorze modules', async () => {
    await page.goto('/projet/reglages#sources-code');
    const section = page.getByRole('region', { name: 'Sources du code' });
    await expect(page.getByRole('heading', { name: 'Sources du code' })).toBeFocused();
    await expect(section).toContainText('envoie à GitHub Copilot les noms des modules et de courts extraits');
    await section.getByLabel('Chemin du dossier').fill(demo.root);
    await section.getByRole('button', { name: 'Détecter les modules' }).click();
    const status = section.getByRole('status');
    await expect(status).toContainText('14 modules détectés');
    await expect(status).toContainText('2 fichiers sensibles écartés');
    await expect(status).toContainText('demo-front');
    await expect(status).toContainText('facturation-chart');
    await expect(status).not.toContainText('lien-externe');
    await expectNoA11yViolations(page);
  });

  test('Q2 : un motif retire le module legacy de l’aperçu, puis la racine est ajoutée', async () => {
    const section = page.getByRole('region', { name: 'Sources du code' });
    await section.getByLabel("Motifs d'exclusion (facultatif)").fill('legacy/**');
    await section.getByRole('button', { name: 'Détecter les modules' }).click();
    await expect(section.getByRole('status')).toContainText('13 modules détectés');
    await expect(section.getByRole('status')).not.toContainText('ancien');
    await section.getByRole('button', { name: 'Ajouter la racine' }).click();
    await expect(section.getByRole('listitem').filter({ hasText: 'Exclus : legacy/**' })).toContainText('demo-rv');
  });

  test('Q3 : un chemin absent ou imbriqué est refusé avec sa raison', async () => {
    const section = page.getByRole('region', { name: 'Sources du code' });
    await section.getByLabel('Chemin du dossier').fill(`${demo.root}-absent`);
    await section.getByRole('button', { name: 'Ajouter la racine' }).click();
    await expect(page.getByRole('alert')).toContainText("Ce dossier n'existe pas");
    await section.getByLabel('Chemin du dossier').fill(`${demo.root}/modules`);
    await section.getByRole('button', { name: 'Ajouter la racine' }).click();
    await expect(page.getByRole('alert')).toContainText('se contiennent');
    await section.getByLabel('Chemin du dossier').fill('');
  });

  const composer = () => page.getByLabel('Votre question');
  const answers = () => page.locator('li.answer');
  const names = (step: ReturnType<Page['locator']>) => step.locator('tbody tr .name').allTextContents();

  test('Q4 : l’agent analyse le périmètre, montre chaque module et ses occurrences, et les cite', async () => {
    await page.goto('/');
    await composer().fill('E2E-PERIMETRE Quels modules touche l’ajout d’un statut SUSPENDU au contrat ?');
    await composer().press('Enter');
    const answer = answers().first();
    await expect(answer.locator('.note')).toContainText('Trois modules sont impactés');
    const step = answer.locator('sw-perimeter-step');
    await expect(step.locator('details.perimeter')).toHaveAttribute('open', '');
    await expect(step.locator('summary').first()).toContainText('Analyse du périmètre · 3 termes · 1 racine');
    await expect(step).toContainText('Termes : ContractStatus, CONTRACT_STATUS, contract.status');
    await expect(step.locator('.summary')).toHaveText('6 modules : 3 impactés, 1 à vérifier, 1 non impacté, 1 non qualifié');
    await expect(step.getByRole('heading', { level: 3 })).toHaveText([
      'Impactés (3)',
      'À vérifier (1)',
      'Non impactés (1)',
      'Non qualifiés (1)',
    ]);

    const common = step.getByRole('row').filter({ hasText: 'common-contract' }).first();
    await expect(common).toContainText('Définit l\'énumération ContractStatus');
    await common.getByText(/^Occurrences/).click();
    await expect(common).toContainText(/ContractStatus\.java:\d+/);
    await expect(common.locator('code').first()).toContainText('ContractStatus');

    // une citation de module mène à sa ligne dans l'étape
    const cite = answer.locator('.note a', { hasText: '[1]' });
    await expect(cite).toHaveAttribute('href', /^#perimeter-/);
    await cite.click();
    await expect(step.locator('tbody tr:focus')).toHaveCount(1);
    await expectNoA11yViolations(page);
  });

  test('Q7 et Q8 : aucun module trouvé ne manque, aucun piège n’apparaît', async () => {
    const step = answers().first().locator('sw-perimeter-step');
    expect((await names(step)).sort()).toEqual([...FOUND].sort());
    const unqualified = step.getByRole('region', { name: 'Non qualifiés (1)' });
    await expect(unqualified).toContainText('demo-front');
    await expect(unqualified).toContainText('L’agent ne s’est pas prononcé');
    // annuaire porte une instruction adressée à l'IA : la qualification rejouée reste la sienne
    await expect(step.getByRole('region', { name: 'Non impactés (1)' })).toContainText('annuaire');

    for (const details of await step.locator('details.occurrences summary').all()) {
      await details.click();
    }
    const text = (await step.textContent()) ?? '';
    for (const trap of ['.env', 'settings.xml', 'node_modules', 'target/', 'generated/', 'data.bin', 'lien-externe', 'Externe.java', 'piège']) {
      expect(text, trap).not.toContain(trap);
    }
  });

  test('Q9 : la relance avec un terme de plus signale le module nouveau', async () => {
    await composer().fill('E2E-RELANCE cherche aussi contract_suspended');
    await page.getByRole('button', { name: 'Envoyer' }).click();
    const answer = answers().nth(1);
    await expect(answer.locator('.note')).toContainText('ajoute le module batch-suspension');
    const step = answer.locator('sw-perimeter-step');
    await expect(step).toContainText('Termes : ContractStatus, CONTRACT_STATUS, contract.status, contract_suspended');
    await expect(step.getByRole('row').filter({ hasText: 'batch-suspension' })).toContainText('Nouveau');
    await expect(step.getByRole('row').filter({ hasText: 'common-contract' })).not.toContainText('Nouveau');
    expect((await names(step)).sort()).toEqual([...FOUND, 'batch-suspension'].sort());
  });

  test('Q11 : les analyses sont relues à l’identique après rechargement', async () => {
    await page.reload();
    await expect(page.locator('sw-perimeter-step')).toHaveCount(2);
    const first = answers().first().locator('sw-perimeter-step');
    await expect(first.locator('.summary')).toHaveText('6 modules : 3 impactés, 1 à vérifier, 1 non impacté, 1 non qualifié');
    await expect(first.getByRole('row').filter({ hasText: 'common-contract' })).toContainText('Définit l\'énumération');
  });

  test('Q10 et Q13 : sans racine, les analyses passées restent, la suivante renvoie aux réglages', async () => {
    const roots = (await (await page.request.get(`/api/projects/${projectId}/source-roots`)).json()) as { id: string }[];
    for (const root of roots) {
      await page.request.delete(`/api/projects/${projectId}/source-roots/${root.id}`, { headers });
    }
    await page.reload();
    await expect(page.locator('sw-perimeter-step')).toHaveCount(2);

    await composer().fill('E2E-SANS-RACINE Quels modules touche le statut SUSPENDU ?');
    await page.getByRole('button', { name: 'Envoyer' }).click();
    const answer = answers().nth(2);
    await expect(answer.locator('.note')).toContainText('déclarez d\'abord les sources du code');
    const step = answer.locator('sw-perimeter-step');
    await expect(step).toContainText('Aucune source du code n’est déclarée pour ce projet.');
    await step.getByRole('link', { name: 'Déclarer les sources du code' }).click();
    await expect(page).toHaveURL(/\/projet\/reglages#sources-code$/);
    await expect(page.getByRole('heading', { name: 'Sources du code' })).toBeFocused();
  });

  test('Q14 : le journal ne garde ni chemin, ni extrait, ni terme proposé', async () => {
    const journal = JSON.stringify(await (await page.request.get('/api/journal')).json());
    const log = readFileSync(join(stateDir, 'app.log'), 'utf8');
    for (const secret of ['CONTRACT_STATUS', 'enum ContractStatus', 'common-contract/src', demo.root]) {
      expect(journal, secret).not.toContain(secret);
      expect(log, secret).not.toContain(secret);
    }
  });
});
