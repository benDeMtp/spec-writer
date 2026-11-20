import { Injectable, inject, signal } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

/**
 * Mode présentation (021, research.md R6) : actif tant que la route affichée porte
 * `data.presentation`. Le shell masque alors le rail et l'avis de projet, et les notifications du
 * navigateur sont suspendues : l'écran partagé ne montre que la séance.
 */
@Injectable({ providedIn: 'root' })
export class PresentationService {
  readonly active = signal(false);

  constructor() {
    const router = inject(Router);
    router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.active.set(presentation(router.routerState.snapshot.root));
    });
  }
}

/** Vrai si la route la plus profonde (ou l'une de ses parentes) demande la présentation. */
export function presentation(route: ActivatedRouteSnapshot): boolean {
  let current: ActivatedRouteSnapshot | null = route;
  let found = false;
  while (current) {
    found = found || current.data?.['presentation'] === true;
    current = current.firstChild;
  }
  return found;
}
