import { expect, request, test, type APIRequestContext } from '@playwright/test';
import http from 'node:http';
import { appPort, baseURL, storageStatePath } from './support/env';

const NEED =
  'Les employés doivent pouvoir réserver une place de parking pour une journée, et être prévenus quand le parking ' +
  'est complet.';

/** Requête brute : le client HTTP de Playwright ne laisse pas remplacer l'en-tête Host. */
function rawGet(path: string, host: string, cookie: string): Promise<number> {
  return new Promise((resolve, reject) => {
    const req = http.request({ host: '127.0.0.1', port: appPort, path, headers: { Host: host, Cookie: cookie } }, (res) => {
      res.resume();
      resolve(res.statusCode ?? 0);
    });
    req.on('error', reject);
    req.end();
  });
}

/** V15 et research.md R14 : l'API locale refuse les autres sites et le DNS rebinding. */
test.describe('V15 sécurité de l’API locale', () => {
  let api: APIRequestContext;
  let cookie = '';

  async function sessionCount(): Promise<number> {
    return ((await (await api.get('/api/sessions')).json()) as unknown[]).length;
  }

  test.beforeAll(async () => {
    api = await request.newContext({ baseURL, storageState: storageStatePath });
    const state = await api.storageState();
    cookie = state.cookies.map((c) => `${c.name}=${c.value}`).join('; ');
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('une page d’un autre site ne peut pas créer de session, même avec le cookie', async ({ browser }) => {
    const context = await browser.newContext({ storageState: storageStatePath });
    const page = await context.newPage();
    await page.route('http://evil.example/**', (route) =>
      route.fulfill({ contentType: 'text/html', body: '<!doctype html><title>Autre site</title><p>Autre site</p>' }),
    );
    await page.goto('http://evil.example/');
    const before = await sessionCount();

    const statuses: number[] = [];
    page.on('response', (r) => {
      if (r.url().startsWith(`${baseURL}/api/sessions`)) {
        statuses.push(r.status());
      }
    });
    // requête « simple » (sans pré-vol CORS) : c'est elle qu'un site malveillant enverrait
    const outcome = await page.evaluate(async (url) => {
      try {
        await fetch(url, {
          method: 'POST',
          mode: 'no-cors',
          credentials: 'include',
          headers: { 'Content-Type': 'text/plain' },
          body: JSON.stringify({ need: 'x'.repeat(80) }),
        });
        return 'sent';
      } catch {
        return 'blocked';
      }
    }, `${baseURL}/api/sessions`);

    // le navigateur peut bloquer la requête lui-même (Private Network Access) ; sinon le serveur la refuse
    expect(['sent', 'blocked']).toContain(outcome);
    await expect.poll(() => statuses.length > 0 || outcome === 'blocked').toBe(true);
    expect(statuses.every((s) => s === 403)).toBe(true);
    expect(await sessionCount()).toBe(before);
    await context.close();
  });

  test('Origin étranger refusé en 403, même avec le cookie', async () => {
    const response = await api.post('/api/sessions', {
      headers: { Origin: 'http://evil.example' },
      data: { need: NEED },
    });
    expect(response.status()).toBe(403);
  });

  test('écriture sans Origin refusée', async () => {
    expect((await api.post('/api/sessions', { data: { need: NEED } })).status()).toBe(403);
  });

  test('Host étranger (DNS rebinding) refusé, même en lecture avec le cookie', async () => {
    // Quarkus refuse déjà ce Host (400) avant LocalSecurityFilter (403) : seul compte le refus
    expect([400, 403]).toContain(await rawGet('/api/sessions', 'evil.example', cookie));
    expect([400, 403]).toContain(await rawGet('/api/sessions', `evil.example:${appPort}`, cookie));
    expect(await rawGet('/api/sessions', `127.0.0.1:${appPort}`, cookie)).toBe(200);
  });
});
