import { Anchor, Stop } from '../../core/interviews/interviews.model';

/**
 * Navigation dans le parcours d'une séance (021, FR-003). L'ordre des arrêts vient du serveur ;
 * ici, seulement le bloc affiché autour de l'arrêt courant et les déplacements au clavier.
 */

/** Un use case et ses étapes et alternatives forment un bloc ; tout autre arrêt est seul. */
export function block(stops: Stop[], index: number): number[] {
  const current = stops[index];
  if (!current || !isUseCasePart(current)) {
    return [index];
  }
  const indices: number[] = [];
  stops.forEach((s, i) => {
    if (s.sessionId === current.sessionId && isUseCasePart(s) && s.anchor.element === current.anchor.element) {
      indices.push(i);
    }
  });
  return indices;
}

function isUseCasePart(s: Stop): boolean {
  return s.kind === 'USE_CASE' || s.kind === 'STEP' || s.kind === 'ALTERNATIVE';
}

/** Premier arrêt du bloc suivant ; reste en place à la fin. */
export function nextGroup(stops: Stop[], index: number): number {
  const current = block(stops, index);
  const after = current[current.length - 1] + 1;
  return after < stops.length ? after : index;
}

/** Premier arrêt du bloc précédent (ou du bloc courant si l'on n'en est pas au début). */
export function prevGroup(stops: Stop[], index: number): number {
  const current = block(stops, index);
  const first = current[0];
  if (first > 0) {
    return block(stops, first - 1)[0];
  }
  return 0;
}

/** Prochain arrêt qui porte un point non traité, après l'arrêt courant. */
export function nextPending(stops: Stop[], index: number): number | null {
  for (let i = index + 1; i < stops.length; i++) {
    if (stops[i].items.some((item) => item.pending)) {
      return i;
    }
  }
  return null;
}

/** Arrêt du curseur enregistré, pour reprendre la séance ; le premier sinon. */
export function findCursor(stops: Stop[], cursor: { sessionId: string; anchor: Anchor | null } | null): number {
  if (!cursor?.anchor) {
    return 0;
  }
  const a = cursor.anchor;
  const found = stops.findIndex(
    (s) =>
      s.sessionId === cursor.sessionId &&
      s.anchor.element === a.element &&
      (s.anchor.step ?? null) === (a.step ?? null) &&
      s.anchor.alternative === a.alternative,
  );
  return found < 0 ? 0 : found;
}

/** Ce qu'annonce l'arrivée sur un arrêt (région polite). */
export function announcement(stop: Stop): string {
  const pending = stop.items.filter((i) => i.pending).length;
  const notes = stop.items.filter((i) => i.origin === 'REMARK' || i.origin === 'QUESTION').length;
  const head = `${stop.label}${stop.text ? ' : ' + stop.text : ''}`;
  const parts = [/[.!?…]$/.test(head) ? head : `${head}.`];
  if (pending) {
    parts.push(`${pending} point${pending > 1 ? 's' : ''} à traiter.`);
  }
  if (notes) {
    parts.push(`${notes} note${notes > 1 ? 's' : ''}.`);
  }
  return parts.join(' ');
}
