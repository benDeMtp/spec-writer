import { TOUR_STOPS } from './tour-stops';

/** Les cibles posées dans les gabarits sont vérifiées par l'E2E us18-visite (V1). */
describe('TOUR_STOPS', () => {
  it('une cible et des textes pour chaque arrêt, cibles toutes différentes', () => {
    const targets = TOUR_STOPS.map((s) => s.target);
    expect(new Set(targets).size).toBe(targets.length);
    for (const stop of TOUR_STOPS) {
      expect(stop.title && stop.text && stop.fallback, stop.id).toBeTruthy();
    }
  });

  it('deux phrases au plus par bulle', () => {
    for (const stop of TOUR_STOPS) {
      for (const text of [stop.text, stop.fallback]) {
        expect((text.match(/[.!?](\s|$)/g) ?? []).length, stop.id).toBeLessThanOrEqual(2);
      }
    }
  });
});
