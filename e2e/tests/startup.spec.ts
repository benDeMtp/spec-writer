import { expect, test } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { stateDir } from './support/env';

/** SC-007 (T136) : le jar répond sur /q/health/ready moins de 5 s après son lancement. */
test('SC-007 : démarrage en moins de 5 s', () => {
  const ms = Number(readFileSync(join(stateDir, 'startup-ms'), 'utf8'));
  expect(ms).toBeGreaterThan(0);
  expect(ms).toBeLessThan(5_000);
});
