/** Entrées du menu (012, data-model.md). */
export type NavEntry =
  | 'conversation'
  | 'specifications'
  | 'jira'
  | 'knowledge'
  | 'project-settings'
  | 'projects'
  | 'configuration'
  | 'journal';

/** Préfixes de chaque entrée : la page elle-même et ses pages filles (research.md R2). */
const PREFIXES: readonly (readonly [string, NavEntry])[] = [
  ['/conversations/', 'conversation'],
  ['/sessions', 'specifications'],
  ['/nouveau', 'specifications'],
  ['/jira', 'jira'],
  ['/projet/connaissance', 'knowledge'],
  ['/projet/reglages', 'project-settings'],
  ['/projets', 'projects'],
  ['/configuration', 'configuration'],
  ['/journal', 'journal'],
];

/** Entrée du menu marquée comme page courante pour une adresse, ou null. */
export function activeEntry(url: string): NavEntry | null {
  const path = url.split(/[?#]/)[0];
  if (path === '/') {
    return 'conversation';
  }
  for (const [prefix, entry] of PREFIXES) {
    if (
      prefix.endsWith('/')
        ? path.startsWith(prefix)
        : path === prefix || path.startsWith(prefix + '/')
    ) {
      return entry;
    }
  }
  return null;
}
