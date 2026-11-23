import { formatDate } from './sw-date.pipe';

describe('swDate (dates en français, Intl.DateTimeFormat)', () => {
  // midi UTC : le même jour dans tous les fuseaux d'Europe
  const at = '2026-11-21T12:05:09Z';

  function local(iso: string): Date {
    return new Date(iso);
  }

  it('mois long, mois court, heure', () => {
    const d = local(at);
    const hh = String(d.getHours()).padStart(2, '0');
    expect(formatDate(at, 'd MMMM y')).toBe('21 novembre 2026');
    expect(formatDate(at, 'd MMM y')).toBe('21 nov. 2026');
    expect(formatDate(at, 'd MMM y, HH:mm')).toBe(`21 nov. 2026, ${hh}:05`);
    expect(formatDate(at, 'd MMM y, HH:mm:ss')).toBe(`21 nov. 2026, ${hh}:05:09`);
    expect(formatDate(at, 'd MMMM y à HH:mm')).toBe(`21 novembre 2026 à ${hh}:05`);
  });

  it('valeur absente ou invalide : vide', () => {
    expect(formatDate(null, 'd MMM y')).toBe('');
    expect(formatDate(undefined, 'd MMM y')).toBe('');
    expect(formatDate('pas une date', 'd MMM y')).toBe('');
  });

  it('accepte une date, un instant ISO ou des millisecondes', () => {
    expect(formatDate(new Date(2026, 0, 3), 'd MMMM y')).toBe('3 janvier 2026');
    expect(formatDate(new Date(2026, 4, 1).getTime(), 'd MMM y')).toBe('1 mai 2026');
  });
});
