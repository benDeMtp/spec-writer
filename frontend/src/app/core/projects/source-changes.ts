/** Décomptes d'une synchronisation de source (017). */
export interface SourceChangeCounts {
  added: number;
  modified: number;
  removed: number;
}

function plural(n: number, one: string, many: string): string {
  return `${n} ${n > 1 ? many : one}`;
}

/** Bilan lisible : décomptes non nuls, ou « aucun changement » (FR-008, FR-014). */
export function changeLabel(change: SourceChangeCounts): string {
  const parts = [
    change.added ? plural(change.added, 'nouveau', 'nouveaux') : '',
    change.modified ? plural(change.modified, 'modifié', 'modifiés') : '',
    change.removed ? plural(change.removed, 'retiré', 'retirés') : '',
  ].filter(Boolean);
  return parts.length ? parts.join(', ') : 'aucun changement';
}
