import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, convertToParamMap, provideRouter } from '@angular/router';
import { ProblemStore } from '../http/problem.store';
import { ProjectFollower } from '../projects/project-follower';
import { Project } from '../projects/projects.model';
import { ProjectsStore } from '../projects/projects.store';
import { followProjectGuard } from './follow-project.guard';

describe('followProjectGuard', () => {
  let follow: ReturnType<typeof vi.fn>;
  let store: ProjectsStore;

  beforeEach(() => {
    follow = vi.fn(async () => undefined);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ProjectFollower, useValue: { follow } }],
    });
    store = TestBed.inject(ProjectsStore);
    store.projects.set([{ id: 'p1', name: 'Aurora' } as Project]);
  });

  async function run(id: string): Promise<string> {
    const route = { paramMap: convertToParamMap({ id }) } as ActivatedRouteSnapshot;
    const result = (await TestBed.runInInjectionContext(() =>
      followProjectGuard('/projet/connaissance')(route, {} as RouterStateSnapshot),
    )) as UrlTree;
    return TestBed.inject(Router).serializeUrl(result);
  }

  it('rend le projet courant, puis ouvre la page équivalente', async () => {
    expect(await run('p1')).toBe('/projet/connaissance');
    expect(follow).toHaveBeenCalledWith('p1', '/projet/connaissance');
  });

  it('un projet inconnu mène à la liste des projets, avec un message', async () => {
    const show = vi.spyOn(TestBed.inject(ProblemStore), 'show');
    expect(await run('perdu')).toBe('/projets');
    expect(follow).not.toHaveBeenCalled();
    expect(show).toHaveBeenCalledWith(expect.objectContaining({ userMessage: "Ce projet n'existe plus." }));
  });
});
