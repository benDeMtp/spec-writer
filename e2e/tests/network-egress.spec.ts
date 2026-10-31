import { expect, request, test, type APIRequestContext } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { ensureCopilotConnected } from './support/copilot';
import { baseURL, jiraURL, stateDir, storageStatePath } from './support/env';

const headers = { Origin: baseURL };
const NEED =
  'Les employés doivent pouvoir réserver une place de parking pour une journée, et être prévenus quand le parking ' +
  'est complet.';

/**
 * SC-008 : rien ne part vers Jira ni vers le LLM avant le consentement correspondant. Lancé en premier
 * (projet « egress »), sur un dossier de données neuf, et remet l'état initial à la fin.
 */
test.describe.serial('SC-008 aucun envoi avant consentement', () => {
  let api: APIRequestContext;
  let jira: APIRequestContext;

  /** Requêtes reçues par Jira simulé (le journal de WireMock ignore son API d'administration). */
  async function jiraCalls(): Promise<number> {
    return (await (await jira.get('/__admin/requests')).json()).meta.total;
  }

  /** Appels au LLM : LangChainLlmClient journalise chaque opération, sans son contenu. */
  function llmCalls(): number {
    return (readFileSync(join(stateDir, 'app.log'), 'utf8').match(/LLM [a-z-]+ : \d+ ms/g) ?? []).length;
  }

  test.beforeAll(async () => {
    api = await request.newContext({ baseURL, storageState: storageStatePath });
    jira = await request.newContext({ baseURL: jiraURL });
    await jira.delete('/__admin/requests');
  });

  test.afterAll(async () => {
    // état initial attendu par US1 : ni Jira, ni Copilot, ni consentement
    await api.put('/api/consents/JIRA', { headers, data: { granted: false } });
    await api.put('/api/consents/COPILOT', { headers, data: { granted: false } });
    await api.delete('/api/settings/jira', { headers });
    await api.delete('/api/settings/copilot', { headers });
    await jira.dispose();
    await api.dispose();
  });

  test('Jira configuré sans consentement : aucune requête vers Jira', async () => {
    const saved = await api.put('/api/settings/jira', {
      headers,
      data: { serverUrl: jiraURL, defaultProjectKey: 'PROJ', acceptInsecureHttp: true, token: 'e2e-good-token' },
    });
    expect(saved.status()).toBe(200);

    const search = await (await api.get('/api/search?q=parking')).json();
    expect(search.jiraStatus).toBe('CONSENT_REQUIRED');
    expect((await api.get('/api/jira/issues/PROJ-102')).status()).toBe(428);
    // le test de connexion renvoie son résultat dans le corps, sans appeler Jira
    expect((await (await api.post('/api/settings/jira/test', { headers, data: {} })).json()).result).toBe('CONSENT_REQUIRED');
    expect((await api.post('/api/jira/issues/PROJ-102/import', { headers, data: {} })).status()).toBe(428);

    expect(await jiraCalls()).toBe(0);
  });

  test('Copilot connecté sans consentement : aucun appel au LLM', async () => {
    await ensureCopilotConnected(api);
    const before = llmCalls();
    const started = await api.post('/api/sessions', { headers, data: { need: NEED } });
    expect(started.status()).toBe(428);
    expect((await started.json()).code).toBe('CONSENT_REQUIRED');
    expect(llmCalls()).toBe(before);
  });

  test('témoin : une fois le consentement accordé, les requêtes partent', async () => {
    await api.put('/api/consents/JIRA', { headers, data: { granted: true } });
    const search = await (await api.get('/api/search?q=parking')).json();
    expect(search.jiraStatus).toBe('OK');
    expect(await jiraCalls()).toBeGreaterThan(0);
  });
});
