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
  // Ordre imposé : « egress » sur un état neuf (SC-008), les parcours, puis « secrets » sur l'état
  // final (SC-009). Un projet ne démarre que si le précédent a réussi.
  projects: [
    { name: 'egress', testMatch: 'network-egress.spec.ts', use: { ...devices['Desktop Chrome'] } },
    {
      name: 'chromium',
      testIgnore: ['network-egress.spec.ts', 'secrets-at-rest.spec.ts'],
      dependencies: ['egress'],
      use: { ...devices['Desktop Chrome'] },
    },
    { name: 'secrets', testMatch: 'secrets-at-rest.spec.ts', dependencies: ['chromium'], use: { ...devices['Desktop Chrome'] } },
  ],
});
