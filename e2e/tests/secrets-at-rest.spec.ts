import { expect, request, test } from '@playwright/test';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { baseURL, stateDir, storageStatePath } from './support/env';

/** Jetons GitHub (préfixes officiels) et jetons Jira saisis pendant les parcours E2E. */
const SECRETS = [/gho_[A-Za-z0-9]/, /ghu_[A-Za-z0-9]/, /github_pat_/, /e2e-good-token/, /jeton-perime/];

function files(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? files(path) : [path];
  });
}

function leaks(name: string, content: string): string[] {
  return SECRETS.filter((p) => p.test(content)).map((p) => `${name} : ${p.source}`);
}

/**
 * SC-009 et V16 : après tous les parcours (projet « secrets », lancé en dernier), aucun jeton dans le
 * dossier de données, le journal de l'application ni les exports. Le journal de Jira simulé n'est
 * pas examiné : il trace l'en-tête Authorization que Jira reçoit légitimement.
 */
test('V16 : aucun jeton au repos dans les données, le journal et les exports', async () => {
  const found: string[] = [];
  const dataDir = readFileSync(join(stateDir, 'data-dir'), 'utf8').trim();
  const data = files(dataDir);
  expect(data.some((f) => f.endsWith('.db'))).toBe(true);
  for (const file of data) {
    // la base SQLite (et son WAL) est binaire : lecture octet par octet
    found.push(...leaks(file, readFileSync(file).toString('latin1')));
  }
  found.push(...leaks('app.log', readFileSync(join(stateDir, 'app.log'), 'utf8')));

  const api = await request.newContext({ baseURL, storageState: storageStatePath });
  let exported = 0;
  for (const s of (await (await api.get('/api/sessions')).json()) as { id: string }[]) {
    const response = await api.get(`/api/sessions/${s.id}/versions/1/export`);
    if (response.ok()) {
      exported++;
      found.push(...leaks(`export ${s.id}`, await response.text()));
    }
  }
  await api.dispose();

  expect(exported).toBeGreaterThan(0);
  expect(found).toEqual([]);
});
