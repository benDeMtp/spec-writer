import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router, UrlTree } from '@angular/router';
import { ProblemStore } from '../http/problem.store';
import { ProjectFollower } from '../projects/project-follower';
import { ProjectsStore } from '../projects/projects.store';

/**
 * Anciennes adresses `projets/:id/contexte` et `projets/:id/contexte/adr` (012, research.md R1) :
 * le projet devient courant, puis la page équivalente s'ouvre. Un projet inconnu mène à la liste.
 */
export function followProjectGuard(target: string): CanActivateFn {
  return async (route: ActivatedRouteSnapshot): Promise<UrlTree> => {
    const router = inject(Router);
    const store = inject(ProjectsStore);
    const follower = inject(ProjectFollower);
    const problems = inject(ProblemStore);
    const id = route.paramMap.get('id') ?? '';
    try {
      if (!store.projects().length) {
        await store.load();
      }
      if (!store.projects().some((p) => p.id === id)) {
        problems.show({ code: 'NOT_FOUND', userMessage: "Ce projet n'existe plus.", action: 'NONE' });
        return router.parseUrl('/projets');
      }
      await follower.follow(id, target);
      return router.parseUrl(target);
    } catch {
      // Le bandeau affiche la cause.
      return router.parseUrl('/projets');
    }
  };
}
