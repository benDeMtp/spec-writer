import { mkdtempSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { join } from 'node:path';

/** Paramètres partagés par la configuration, le globalSetup et les tests. */
export const appPort = Number(process.env.SPECWRITER_E2E_PORT ?? 47699);
export const jiraPort = Number(process.env.SPECWRITER_E2E_JIRA_PORT ?? 47698);
export const baseURL = `http://127.0.0.1:${appPort}`;
export const jiraURL = `http://127.0.0.1:${jiraPort}`;
export const stateDir = join(process.cwd(), 'test-results', '.e2e-state');
export const storageStatePath = join(stateDir, 'storage-state.json');

export const wiremockJar =
  process.env.WIREMOCK_JAR ??
  join(homedir(), '.m2/repository/org/wiremock/wiremock-standalone/4.0.0-beta.41/wiremock-standalone-4.0.0-beta.41.jar');

export function dataDir(): string {
  process.env.SPECWRITER_E2E_DATA_DIR ??= mkdtempSync(join(tmpdir(), 'spec-writer-e2e-'));
  return process.env.SPECWRITER_E2E_DATA_DIR;
}
