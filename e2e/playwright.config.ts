import { defineConfig, devices } from '@playwright/test';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/**
 * Les tests démarrent le jar réel (profil e2e) dans un dossier de données temporaire.
 * Le LLM est rejoué (ReplayChatModel), GitHub est simulé (FakeGitHubAuthenticator),
 * Jira est servi par WireMock (globalSetup).
 */
const port = Number(process.env.SPECWRITER_E2E_PORT ?? 47699);
const dataDir = process.env.SPECWRITER_E2E_DATA_DIR ?? mkdtempSync(join(tmpdir(), 'spec-writer-e2e-'));
process.env.SPECWRITER_E2E_DATA_DIR = dataDir;

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: [
      'java',
      '-Dquarkus.profile=e2e',
      `-Dspecwriter.port=${port}`,
      `-Dspecwriter.data-dir=${dataDir}`,
      '-Dspecwriter.github.fake=true',
      `-Dspecwriter.llm.replay-dir=${join(process.cwd(), 'fixtures', 'llm')}`,
      '-jar',
      '../backend/target/quarkus-app/quarkus-run.jar',
    ].join(' '),
    url: `http://127.0.0.1:${port}/q/health/ready`,
    reuseExistingServer: false,
    timeout: 60_000,
  },
});
