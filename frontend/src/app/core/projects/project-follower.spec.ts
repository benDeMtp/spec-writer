import { TestBed } from '@angular/core/testing';
import { Project } from './projects.model';
import { ProjectsStore } from './projects.store';
import { ProjectFollower } from './project-follower';

const project = (id: string, name: string): Project => ({
  id,
  name,
  description: null,
  jiraProjectKey: null,
  effectiveJiraProjectKey: null,
  jiraKeyWarning: null,
  sessionCount: 0,
  context: '',
  contextUpdatedAt: null,
  docSources: [],
});

describe('ProjectFollower', () => {
  let follower: ProjectFollower;
  let store: {
    currentId: () => string | null;
    projects: () => Project[];
    select: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    vi.useFakeTimers();
    let current = 'a';
    store = {
      currentId: () => current,
      projects: () => [project('a', 'Aurora'), project('b', 'Recette')],
      select: vi.fn(async (id: string) => {
        current = id;
      }),
    };
    TestBed.configureTestingModule({ providers: [{ provide: ProjectsStore, useValue: store }] });
    follower = TestBed.inject(ProjectFollower);
  });

  afterEach(() => vi.useRealTimers());

  it('ne fait rien pour le projet courant', async () => {
    await follower.follow('a');
    expect(store.select).not.toHaveBeenCalled();
    expect(follower.notice()).toBeNull();
  });

  it('rend courant le projet de la page et l’annonce, puis efface l’annonce', async () => {
    await follower.follow('b');
    expect(store.select).toHaveBeenCalledWith('b');
    expect(follower.notice()).toBe('Projet courant : Recette');
    vi.advanceTimersByTime(6000);
    expect(follower.notice()).toBeNull();
  });

  it('n’annonce rien si le changement échoue', async () => {
    store.select.mockRejectedValueOnce(new Error('refus'));
    await expect(follower.follow('b')).rejects.toThrow('refus');
    expect(follower.notice()).toBeNull();
  });

  it('efface l’annonce sur demande', async () => {
    await follower.follow('b');
    follower.clear();
    expect(follower.notice()).toBeNull();
  });
});
