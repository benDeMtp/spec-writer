import { spawn } from 'node:child_process';
import { createWriteStream, existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { appPort, baseURL, dataDir, stateDir } from './env';

const pidFile = join(stateDir, 'app.pid');

function java(): string {
  return process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
}

/**
 * Démarre le jar réel en profil e2e, détaché, et renvoie son URL de lancement. Le pid est noté pour
 * qu'un test puisse arrêter brutalement l'application (V8) et que le teardown arrête la dernière.
 */
export async function startApp(): Promise<string> {
  const jar = join(process.cwd(), '../backend/target/quarkus-app/quarkus-run.jar');
  const app = spawn(java(), [
    '-Dquarkus.profile=e2e',
    `-Dspecwriter.port=${appPort}`,
    `-Dspecwriter.data-dir=${dataDir()}`,
    '-Dspecwriter.github.fake=true',
    '-Dspecwriter.print-launch-url=true',
    `-Dspecwriter.llm.replay-dir=${join(process.cwd(), 'fixtures', 'llm')}`,
    '-jar', jar,
  ], { stdio: ['ignore', 'pipe', 'pipe'], detached: true });
  writeFileSync(pidFile, String(app.pid));
  // Journal pour le diagnostic ; l'URL de lancement y est masquée
  const log = createWriteStream(join(stateDir, 'app.log'), { flags: 'a' });
  const mask = (chunk: Buffer) => chunk.toString().replace(/launch=[\w-]+/g, 'launch=***');
  app.stderr!.on('data', (chunk: Buffer) => log.write(mask(chunk)));

  const launchUrl = await new Promise<string>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('URL de lancement non reçue en 60 s')), 60_000);
    let buffer = '';
    app.stdout!.on('data', (chunk: Buffer) => {
      log.write(mask(chunk));
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
  app.unref();
  await waitFor(`${baseURL}/q/health/ready`);
  return launchUrl;
}

/** Arrête l'application en cours ; {@code SIGKILL} simule une fermeture brutale (V8). */
export async function stopApp(signal: NodeJS.Signals = 'SIGTERM'): Promise<void> {
  if (!existsSync(pidFile)) {
    return;
  }
  const pid = Number(readFileSync(pidFile, 'utf8'));
  try {
    process.kill(pid, signal);
  } catch {
    return;
  }
  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline && (await responds(`${baseURL}/q/health/ready`))) {
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
}

export async function responds(url: string): Promise<boolean> {
  try {
    return (await fetch(url)).ok;
  } catch {
    return false;
  }
}

export async function waitFor(url: string): Promise<void> {
  const deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    if (await responds(url)) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`${url} ne répond pas`);
}
