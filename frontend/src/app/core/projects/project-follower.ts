import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ProjectsStore } from './projects.store';

const NOTICE_MS = 6000;

/**
 * Le menu et la page montrent toujours le même projet (012, research.md R3) : ouvrir une page d'un
 * autre projet rend ce projet courant, et une annonce le dit.
 */
@Injectable({ providedIn: 'root' })
export class ProjectFollower {
  private readonly store = inject(ProjectsStore);
  private readonly router = inject(Router);
  private timer: ReturnType<typeof setTimeout> | undefined;
  /** Page où l'annonce s'affiche ; elle s'efface quand on en sort. */
  private shownOn: string | null = null;

  /** « Projet courant : <nom> » après une bascule, effacée après 6 s ou quand on quitte sa page. */
  readonly notice = signal<string | null>(null);

  /**
   * Rend `projectId` courant. `shownOn` : page où l'annonce doit s'afficher, la page affichée par
   * défaut ; une garde qui redirige donne la page de destination.
   */
  async follow(projectId: string, shownOn: string = this.router.url): Promise<void> {
    if (!this.store.current()) {
      // page ouverte directement : le projet courant n'est pas encore lu, il l'est avant de comparer
      await this.store.load();
    }
    if (projectId === this.store.currentId()) {
      return;
    }
    await this.store.select(projectId);
    const name = this.store.projects().find((p) => p.id === projectId)?.name;
    if (name) {
      this.notice.set(`Projet courant : ${name}`);
      this.shownOn = path(shownOn);
      clearTimeout(this.timer);
      this.timer = setTimeout(() => this.notice.set(null), NOTICE_MS);
    }
  }

  clear(): void {
    clearTimeout(this.timer);
    this.notice.set(null);
    this.shownOn = null;
  }

  /** Après une navigation : l'annonce reste sur sa page, et s'efface ailleurs. */
  leftFor(url: string): void {
    if (this.shownOn !== null && path(url) !== this.shownOn) {
      this.clear();
    }
  }
}

function path(url: string): string {
  return url.split(/[?#]/)[0];
}
