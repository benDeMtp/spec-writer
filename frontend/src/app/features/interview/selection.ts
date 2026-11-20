/** Passage sélectionné dans le document de séance, ramené au premier élément touché. */
export interface PassageSelection {
  /** Clé d'ancre de l'élément (`data-anchor`). */
  key: string;
  /** Texte cité, 1 000 caractères au plus. */
  quote: string;
}

export const MAX_QUOTE = 1000;

/**
 * Sélection d'un passage du document (021, conception du 20/11/2026) : le premier élément ancré
 * qu'elle touche, et le texte sélectionné dans cet élément seulement. Nulle pour une sélection vide,
 * hors du document, ou hors de tout élément ancré.
 */
export function passageOf(root: HTMLElement, selection: Selection | null): PassageSelection | null {
  if (!selection || selection.isCollapsed || selection.rangeCount === 0) {
    return null;
  }
  const range = selection.getRangeAt(0);
  if (!root.contains(range.commonAncestorContainer)) {
    return null;
  }
  if (inPoint(range.startContainer) || inPoint(range.endContainer)) {
    return null;
  }
  const element = anchored(range.startContainer, root) ?? firstAnchoredIn(range, root);
  if (!element) {
    return null;
  }
  const clipped = range.cloneRange();
  if (!element.contains(range.startContainer)) {
    clipped.setStart(element, 0);
  }
  if (!element.contains(range.endContainer)) {
    clipped.setEnd(element, element.childNodes.length);
  }
  // le texte des points insérés sous l'élément ne fait pas partie du passage
  const fragment = clipped.cloneContents();
  fragment.querySelectorAll('[data-card], [data-note]').forEach((n) => n.remove());
  const quote = (fragment.textContent ?? '').replace(/\s+/g, ' ').trim().slice(0, MAX_QUOTE);
  return quote ? { key: element.getAttribute('data-anchor')!, quote } : null;
}

/** Le texte des cartes et des saisies n'est pas un passage de la spécification. */
function inPoint(node: Node): boolean {
  const e = node instanceof HTMLElement ? node : node.parentElement;
  return !!e?.closest('[data-card], [data-note]');
}

function anchored(node: Node, root: HTMLElement): HTMLElement | null {
  const start = node instanceof HTMLElement ? node : node.parentElement;
  const found = start?.closest<HTMLElement>('[data-anchor]') ?? null;
  return found && root.contains(found) ? found : null;
}

function firstAnchoredIn(range: Range, root: HTMLElement): HTMLElement | null {
  return Array.from(root.querySelectorAll<HTMLElement>('[data-anchor]')).find((e) => range.intersectsNode(e)) ?? null;
}
