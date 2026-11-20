import { spawn, type ChildProcess } from 'node:child_process';
import { createWriteStream, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { stateDir } from './env';
import { waitFor } from './app';

/** Seconde instance de l'outil, sur une base neuve (018, research.md R7). */
export interface FreshInstance {
  baseURL: string;
  /** URL de lancement, à usage unique. */
  launchUrl: string;
  dataDir: string;
  /** Code de sortie du processus, à sa fin (019). */
  exited: Promise<number | null>;
  stop(): Promise<void>;
}

export const freshPort = Number(process.env.SPECWRITER_E2E_FRESH_PORT ?? 47697);

function java(): string {
  return process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
}

/** Options de la seconde instance ; `extraArgs` vient en dernier et l'emporte (019). */
function freshArgs(dataDir: string, extraArgs: string[]): string[] {
  return [
    '-Dquarkus.profile=e2e',
    `-Dspecwriter.port=${freshPort}`,
    `-Dspecwriter.data-dir=${dataDir}`,
    '-Dspecwriter.github.fake=true',
    '-Dspecwriter.print-launch-url=true',
    '-Dspecwriter.onboarding.auto-open=true',
    `-Dspecwriter.llm.replay-dir=${join(process.cwd(), 'fixtures', 'llm')}`,
    `-Dspecwriter.embedding.folder=${join(process.cwd(), '../backend/target/test-model')}`,
    ...extraArgs,
    '-jar',
    join(process.cwd(), '../backend/target/quarkus-app/quarkus-run.jar'),
  ];
}

/**
 * Démarre le jar sur un dossier de données neuf, assistant de premier lancement ouvert de lui-même
 * (coupé sur l'instance partagée). Le même dossier peut être repris pour tester la reprise après
 * un arrêt de l'outil.
 */
export async function startFreshInstance(
  dataDir = mkdtempSync(join(tmpdir(), 'spec-writer-e2e-fresh-')),
  extraArgs: string[] = [],
): Promise<FreshInstance> {
  const baseURL = `http://127.0.0.1:${freshPort}`;
  const app: ChildProcess = spawn(java(), freshArgs(dataDir, extraArgs), { stdio: ['ignore', 'pipe', 'pipe'] });
  const exited = new Promise<number | null>((resolve) => app.once('exit', (code) => resolve(code)));
  const log = createWriteStream(join(stateDir, 'fresh-app.log'), { flags: 'a' });
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
    app.on('exit', (code) => reject(new Error(`La seconde instance s'est arrêtée (code ${code}), voir fresh-app.log`)));
  });
  await waitFor(`${baseURL}/q/health/ready`);
  return {
    baseURL,
    launchUrl,
    dataDir,
    exited,
    stop: () =>
      new Promise<void>((resolve) => {
        if (app.exitCode !== null) {
          resolve();
          return;
        }
        app.once('exit', () => resolve());
        app.kill('SIGTERM');
      }),
  };
}
