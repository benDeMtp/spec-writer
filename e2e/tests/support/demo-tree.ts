import { cpSync, mkdirSync, mkdtempSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

/** Arbre de démonstration de la feature 013 (fixtures/sources/demo-rv, voir son README). */
export const DEMO_SOURCE = join(process.cwd(), 'fixtures', 'sources', 'demo-rv');

/**
 * Copie l'arbre de démonstration dans un dossier temporaire, avec les pièges que le dépôt ne peut
 * pas versionner : fichiers sensibles, dossiers générés, binaire, et lien symbolique vers
 * l'extérieur. Tous contiennent ContractStatus ; aucun ne doit apparaître dans une analyse.
 */
export function copyDemoTree(): { root: string; dispose: () => void } {
  const base = mkdtempSync(join(tmpdir(), 'sw-demo-'));
  const root = join(base, 'demo-rv');
  cpSync(DEMO_SOURCE, root, { recursive: true });
  const trap = 'ContractStatus piège\n';
  const write = (path: string, content: string | Buffer) => {
    mkdirSync(join(path, '..'), { recursive: true });
    writeFileSync(path, content);
  };
  write(join(root, '.env'), `SECRET=${trap}`);
  write(join(root, 'modules', 'facturation', 'settings.xml'), `<settings>${trap}</settings>`);
  write(join(root, 'front', 'node_modules', 'x', 'index.js'), trap);
  write(join(root, 'modules', 'facturation', 'target', 'gen', 'A.java'), trap);
  write(join(root, 'libs', 'common-contract', 'data.bin'), Buffer.concat([Buffer.from([0]), Buffer.from(trap)]));
  const outside = join(base, 'hors-arbre');
  write(join(outside, 'Externe.java'), trap);
  try {
    symlinkSync(outside, join(root, 'modules', 'lien-externe'), 'dir');
  } catch {
    // système sans liens symboliques : le piège est simplement absent
  }
  return { root, dispose: () => rmSync(base, { recursive: true, force: true }) };
}
