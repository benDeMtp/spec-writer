import { defineConfig, devices } from '@playwright/test';
import { baseURL, storageStatePath } from './tests/support/env';

/**
 * Les tests démarrent le jar réel (profil e2e) dans un dossier de données temporaire.
 * Le LLM est rejoué (ReplayChatModel), GitHub est simulé (FakeGitHubAuthenticator),
 * Jira est servi par WireMock. Tout est lancé par le globalSetup.
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  globalSetup: './tests/support/global-setup.ts',
  use: {
    baseURL,
    storageState: storageStatePath,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
