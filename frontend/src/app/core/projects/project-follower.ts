import { Injectable, inject, signal } from '@angular/core';
import { ProjectsStore } from './projects.store';

const NOTICE_MS = 6000;

/**
 * Le menu et la page montrent toujours le même projet (012, research.md R3) : ouvrir une page d'un
 * autre projet rend ce projet courant, et une annonce le dit.
 */
@Injectable({ providedIn: 'root' })
export class ProjectFollower {
  private readonly store = inject(ProjectsStore);
  private timer: ReturnType<typeof setTimeout> | undefined;

  /** « Projet courant : <nom> » après une bascule, effacée après 6 s ou à la navigation suivante. */
  readonly notice = signal<string | null>(null);

  async follow(projectId: string): Promise<void> {
    if (projectId === this.store.currentId()) {
      return;
    }
    await this.store.select(projectId);
    const name = this.store.projects().find((p) => p.id === projectId)?.name;
    if (name) {
      this.notice.set(`Projet courant : ${name}`);
      clearTimeout(this.timer);
      this.timer = setTimeout(() => this.notice.set(null), NOTICE_MS);
    }
  }

  clear(): void {
    clearTimeout(this.timer);
    this.notice.set(null);
  }
}
