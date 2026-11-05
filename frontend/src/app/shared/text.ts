/** « 1 question », « 2 questions » : accord du nom avec le nombre. */
export function count(n: number, singular: string, plural: string): string {
  return `${n} ${n > 1 ? plural : singular}`;
}

/** « 12 s », « 1 min 05 s » ou « 1 h 02 min » : une durée lisible, arrondie à la seconde. */
export function formatDuration(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const sec = total % 60;
  if (h > 0) {
    return `${h} h ${String(m).padStart(2, '0')} min`;
  }
  return m > 0 ? `${m} min ${String(sec).padStart(2, '0')} s` : `${sec} s`;
}
