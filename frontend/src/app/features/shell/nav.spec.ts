import { activeEntry } from './nav';

describe('activeEntry', () => {
  it.each([
    ['/', 'conversation'],
    ['/conversations/c1', 'conversation'],
    ['/sessions', 'specifications'],
    ['/sessions/s1', 'specifications'],
    ['/sessions/s1/versions/2?discussion=1', 'specifications'],
    ['/nouveau', 'specifications'],
    ['/jira', 'jira'],
    ['/jira/FACT-1', 'jira'],
    ['/projet/connaissance', 'knowledge'],
    ['/projet/connaissance/adr', 'knowledge'],
    ['/projet/reglages#jira', 'project-settings'],
    ['/projets', 'projects'],
    ['/projets/p1/contexte', 'projects'],
    ['/configuration?raison=copilot', 'configuration'],
    ['/journal', 'journal'],
  ])('%s → %s', (path, entry) => {
    expect(activeEntry(path)).toBe(entry);
  });

  it.each(['/inconnu', '/sessionsx', '/projetx', '/conversations'])(
    '%s → aucune entrée',
    (path) => {
      expect(activeEntry(path)).toBeNull();
    },
  );
});
