import { request } from '@playwright/test';
import { spawn } from 'node:child_process';
import { createWriteStream, existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { responds, startApp, stopApp, waitFor } from './app';
import { baseURL, dataDir, jiraPort, jiraURL, stateDir, storageStatePath, wiremockJar } from './env';

/**
 * Démarre Jira simulé (WireMock) et le jar réel en profil e2e, puis échange le jeton de lancement
 * contre le cookie de session, enregistré pour tous les tests (research.md R14).
 */
export default async function globalSetup(): Promise<() => Promise<void>> {
  const jar = join(process.cwd(), '../backend/target/quarkus-app/quarkus-run.jar');
  if (!existsSync(jar)) {
    throw new Error(`Jar absent (${jar}) : lancez d'abord ./mvnw -B package -DskipTests`);
  }
  if (!existsSync(wiremockJar)) {
    throw new Error(`WireMock absent (${wiremockJar}) : définissez WIREMOCK_JAR`);
  }
  mkdirSync(stateDir, { recursive: true });
  writeFileSync(join(stateDir, 'app.log'), '');
  // Un processus resté d'un lancement précédent servirait d'anciens stubs ou une ancienne version
  for (const url of [`${baseURL}/q/health/ready`, `${jiraURL}/__admin/health`]) {
    if (await responds(url)) {
      throw new Error(`Port déjà occupé (${new URL(url).port}) : arrêtez le processus restant`);
    }
  }

  const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
  const wiremock = spawn(java, ['-jar', wiremockJar, '--bind-address', '127.0.0.1', '--port', String(jiraPort),
    '--root-dir', join(process.cwd(), 'fixtures', 'jira'), '--disable-banner', '--verbose'], { stdio: ['ignore', 'pipe', 'pipe'] });
  const wiremockLog = createWriteStream(join(stateDir, 'wiremock.log'));
  wiremock.stdout!.pipe(wiremockLog);
  wiremock.stderr!.pipe(wiremockLog);

  const teardown = async () => {
    await stopApp();
    wiremock.kill('SIGTERM');
  };

  try {
    const started = Date.now();
    const launchUrl = await startApp();
    // durée du lancement jusqu'à /q/health/ready, vérifiée par startup.spec.ts (SC-007)
    writeFileSync(join(stateDir, 'startup-ms'), String(Date.now() - started));
    await waitFor(`${jiraURL}/__admin/health`);
    await exchange(launchUrl);
    writeFileSync(join(stateDir, 'ready'), new Date().toISOString());
    // lu par secrets-at-rest.spec.ts (SC-009)
    writeFileSync(join(stateDir, 'data-dir'), dataDir());
  } catch (error) {
    await teardown();
    throw error;
  }
  return teardown;
}

/** Échange le jeton de lancement contre le cookie, enregistré pour tous les tests. */
export async function exchange(launchUrl: string): Promise<void> {
  const token = new URL(launchUrl).searchParams.get('launch');
  const api = await request.newContext({ baseURL });
  const response = await api.post('/api/session-auth', { headers: { Origin: baseURL }, data: { launchToken: token } });
  if (response.status() !== 204) {
    throw new Error(`Échange du jeton de lancement refusé (${response.status()})`);
  }
  await api.storageState({ path: storageStatePath });
  await api.dispose();
}
