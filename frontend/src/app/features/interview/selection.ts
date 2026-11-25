/** Passage sélectionné dans le document de séance, et l'élément auquel la remarque se rattache. */
export interface PassageSelection {
  /** Clé d'ancre de l'élément (`data-anchor`). */
  key: string;
  /** Texte cité, 1 000 caractères au plus. */
  quote: string;
}

export const MAX_QUOTE = 1000;

/**
 * Sélection d'un passage du document (021) : tout texte s'annote, titres, détails et tableaux
 * compris. La remarque se rattache à l'élément ancré qui contient le début de la sélection, sinon au
 * dernier élément ancré qui le précède, sinon au premier qu'elle touche. Le passage cité est le texte
 * sélectionné, sans celui des cartes et des saisies. Nulle pour une sélection vide, hors du document,
 * ou commencée dans une carte.
 */
export function passageOf(root: HTMLElement, selection: Selection | null): PassageSelection | null {
  if (!selection || selection.isCollapsed || selection.rangeCount === 0) {
    return null;
  }
  const range = selection.getRangeAt(0);
  if (!root.contains(range.commonAncestorContainer) || pointOf(range.startContainer)) {
    return null;
  }
  const element = containing(range.startContainer, root) ?? preceding(range.startContainer, root) ?? touched(range, root);
  if (!element) {
    return null;
  }
  const fragment = range.cloneContents();
  fragment.querySelectorAll('[data-card], [data-note]').forEach((n) => n.remove());
  const quote = (fragment.textContent ?? '').replace(/\s+/g, ' ').trim().slice(0, MAX_QUOTE);
  return quote ? { key: element.getAttribute('data-anchor')!, quote } : null;
}

/** Carte ou saisie qui contient le nœud : leur texte n'est pas un passage de la spécification. */
function pointOf(node: Node): HTMLElement | null {
  const e = node instanceof HTMLElement ? node : node.parentElement;
  return e?.closest<HTMLElement>('[data-card], [data-note]') ?? null;
}

function containing(node: Node, root: HTMLElement): HTMLElement | null {
  const start = node instanceof HTMLElement ? node : node.parentElement;
  const found = start?.closest<HTMLElement>('[data-anchor]') ?? null;
  return found && root.contains(found) ? found : null;
}

/** Dernier élément ancré qui précède le nœud dans le document (un titre, un tableau des détails…). */
function preceding(node: Node, root: HTMLElement): HTMLElement | null {
  let found: HTMLElement | null = null;
  for (const e of Array.from(root.querySelectorAll<HTMLElement>('[data-anchor]'))) {
    if (e.compareDocumentPosition(node) & Node.DOCUMENT_POSITION_FOLLOWING) {
      found = e;
    }
  }
  return found;
}

function touched(range: Range, root: HTMLElement): HTMLElement | null {
  return Array.from(root.querySelectorAll<HTMLElement>('[data-anchor]')).find((e) => range.intersectsNode(e)) ?? null;
}
