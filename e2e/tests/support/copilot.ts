import { expect, type APIRequestContext } from '@playwright/test';
import { baseURL } from './env';

/** Connecte GitHub simulé par l'API, pour les parcours qui ne testent pas la connexion elle-même. */
export async function ensureCopilotConnected(request: APIRequestContext): Promise<void> {
  const headers = { Origin: baseURL };
  const settings = await (await request.get('/api/settings')).json();
  if (settings.copilot.state === 'CONNECTED') {
    return;
  }
  const start = await (await request.post('/api/settings/copilot/device-flow', { headers, data: {} })).json();
  await expect
    .poll(async () => (await (await request.get(`/api/settings/copilot/device-flow/${start.flowId}`)).json()).state)
    .toBe('CONNECTED');
}
