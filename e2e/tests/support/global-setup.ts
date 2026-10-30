import { request } from '@playwright/test';
import { type ChildProcess, spawn } from 'node:child_process';
import { createWriteStream, existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { appPort, baseURL, dataDir, jiraPort, jiraURL, stateDir, storageStatePath, wiremockJar } from './env';

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
  // Un processus resté d'un lancement précédent servirait d'anciens stubs ou une ancienne version
  for (const url of [`${baseURL}/q/health/ready`, `${jiraURL}/__admin/health`]) {
    if (await responds(url)) {
      throw new Error(`Port déjà occupé (${new URL(url).port}) : arrêtez le processus restant`);
    }
  }

  const children: ChildProcess[] = [];
  const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java';

  const wiremock = spawn(java, ['-jar', wiremockJar, '--bind-address', '127.0.0.1', '--port', String(jiraPort),
    '--root-dir', join(process.cwd(), 'fixtures', 'jira'), '--disable-banner', '--verbose'], { stdio: ['ignore', 'pipe', 'pipe'] });
  children.push(wiremock);
  const wiremockLog = createWriteStream(join(stateDir, 'wiremock.log'));
  wiremock.stdout!.pipe(wiremockLog);
  wiremock.stderr!.pipe(wiremockLog);

  const app = spawn(java, [
    '-Dquarkus.profile=e2e',
    `-Dspecwriter.port=${appPort}`,
    `-Dspecwriter.data-dir=${dataDir()}`,
    '-Dspecwriter.github.fake=true',
    '-Dspecwriter.print-launch-url=true',
    `-Dspecwriter.llm.replay-dir=${join(process.cwd(), 'fixtures', 'llm')}`,
    '-jar', jar,
  ], { stdio: ['ignore', 'pipe', 'pipe'] });
  children.push(app);
  // Journal de l'application pour le diagnostic ; l'URL de lancement y est masquée
  const log = createWriteStream(join(stateDir, 'app.log'));
  const mask = (chunk: Buffer) => chunk.toString().replace(/launch=[\w-]+/g, 'launch=***');
  app.stdout!.on('data', (chunk: Buffer) => log.write(mask(chunk)));
  app.stderr!.on('data', (chunk: Buffer) => log.write(mask(chunk)));

  const teardown = async () => {
    for (const child of children) {
      child.kill('SIGTERM');
    }
  };

  try {
    const launchUrl = await new Promise<string>((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("URL de lancement non reçue en 60 s")), 60_000);
      let buffer = '';
      app.stdout!.on('data', (chunk: Buffer) => {
        buffer += chunk.toString();
        const match = /(http:\/\/127\.0\.0\.1:\d+\/\?launch=[\w-]+)/.exec(buffer);
        if (match) {
          clearTimeout(timer);
          resolve(match[1]);
        }
      });
      app.on('exit', (code) =>
        reject(new Error(`Le jar s'est arrêté (code ${code}) : Java 25 est requis (JAVA_HOME), voir ${stateDir}/app.log`)),
      );
    });
    await waitFor(`${baseURL}/q/health/ready`);
    await waitFor(`${jiraURL}/__admin/health`);

    const token = new URL(launchUrl).searchParams.get('launch');
    const api = await request.newContext({ baseURL });
    const response = await api.post('/api/session-auth', {
      headers: { Origin: baseURL },
      data: { launchToken: token },
    });
    if (response.status() !== 204) {
      throw new Error(`Échange du jeton de lancement refusé (${response.status()})`);
    }
    await api.storageState({ path: storageStatePath });
    await api.dispose();
    writeFileSync(join(stateDir, 'ready'), new Date().toISOString());
  } catch (error) {
    await teardown();
    throw error;
  }
  return teardown;
}

async function responds(url: string): Promise<boolean> {
  try {
    return (await fetch(url)).ok;
  } catch {
    return false;
  }
}

async function waitFor(url: string): Promise<void> {
  const deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    if (await responds(url)) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`${url} ne répond pas`);
}
