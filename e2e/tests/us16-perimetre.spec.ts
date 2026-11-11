import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { expectNoA11yViolations } from './support/a11y';
import { ensureCopilotConnected } from './support/copilot';
import { copyDemoTree } from './support/demo-tree';
import { baseURL, stateDir, storageStatePath } from './support/env';

/** Modèle d'architecture du projet : facturation reliée à notification et aux archives (US4). */
const WORKSPACE = join(process.cwd(), 'fixtures', 'c4', 'perimetre', 'workspace.dsl');

const DEFAULT_PROJECT = '00000000-0000-0000-0000-000000000001';

/** Modules de l'arbre qui contiennent un terme, `legacy/**` étant exclu (Q2). */
const FOUND = ['common-contract', 'demo-front', 'facturation', 'facturation-chart', 'annuaire', 'quarkus-contract'];
/** Modules ajoutés par les poms (un dépendant, l'agrégateur, US3) et par l'architecture (US4). */
const PROPAGATED = ['relance', 'aggregates', 'notification'];
const SUMMARY = '9 modules : 3 impactés, 4 à vérifier, 1 non impacté, 1 non qualifié';

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
    const architecture = await page.request.put(`/api/projects/${projectId}/architecture-source`, {
      headers,
      data: { workspace: WORKSPACE },
    });
    expect(architecture.status()).toBe(200);
    await expect
      .poll(async () => (await (await page.request.get(`/api/projects/${projectId}/indexations/current`)).json()).status, {
        timeout: 60_000,
      })
      .not.toBe('RUNNING');
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
  /** Ligne d'un module, par son nom exact : une chaîne de dépendance en cite d'autres. */
  const row = (step: ReturnType<Page['locator']>, name: string) =>
    step.locator('tbody tr').filter({ has: page.locator('.name', { hasText: new RegExp(`^${name}$`) }) });

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
    await expect(step.locator('.summary')).toHaveText(SUMMARY);
    await expect(step.getByRole('heading', { level: 3 })).toHaveText([
      'Impactés (3)',
      'À vérifier (4)',
      'Non impactés (1)',
      'Non qualifiés (1)',
      'Éléments d’architecture sans module (1)',
    ]);

    const common = row(step, 'common-contract');
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

  test('Q5 : un module qui dépend d’un module trouvé est à vérifier, avec sa chaîne ; l’agrégateur est signalé', async () => {
    const step = answers().first().locator('sw-perimeter-step');
    const toCheck = step.getByRole('region', { name: 'À vérifier (4)' });
    const relance = row(toCheck, 'relance');
    await expect(relance).toContainText('dépend de');
    await expect(relance.getByRole('list', { name: 'Chaîne de dépendance' }).getByRole('listitem')).toHaveText([
      'facturation',
      'relance',
    ]);
    await expect(relance).toContainText('Dépend de la facturation');
    await expect(row(toCheck, 'aggregates')).toContainText('agrège common-contract');
  });

  test('Q6 : un module relié par le modèle d’architecture est à vérifier, avec la relation', async () => {
    const step = answers().first().locator('sw-perimeter-step');
    const notification = row(step.getByRole('region', { name: 'À vérifier (4)' }), 'notification');
    await expect(notification).toContainText('relié à Facturation API (REST, « envoie les relances »)');
    await expect(row(step, 'demo-front')).toContainText('absent du modèle d’architecture');
    await expect(row(step, 'facturation')).not.toContainText('absent du modèle');
    await expect(step.getByRole('region', { name: 'Éléments d’architecture sans module (1)' })).toContainText(
      'Archives : relié à Facturation API (SFTP, « archive les factures »)',
    );
  });

  test('Q7 et Q8 : aucun module trouvé ne manque, aucun piège n’apparaît', async () => {
    const step = answers().first().locator('sw-perimeter-step');
    expect((await names(step)).sort()).toEqual([...FOUND, ...PROPAGATED].sort());
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
    await expect(row(step, 'batch-suspension')).toContainText('Nouveau');
    await expect(row(step, 'common-contract')).not.toContainText('Nouveau');
    expect((await names(step)).sort()).toEqual([...FOUND, ...PROPAGATED, 'batch-suspension'].sort());
  });

  test('Q11 : les analyses sont relues à l’identique après rechargement', async () => {
    await page.reload();
    await expect(page.locator('sw-perimeter-step')).toHaveCount(2);
    const first = answers().first().locator('sw-perimeter-step');
    await expect(first.locator('.summary')).toHaveText(SUMMARY);
    await expect(row(first, 'common-contract')).toContainText('Définit l\'énumération');
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
