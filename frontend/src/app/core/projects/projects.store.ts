import { Injectable, computed, inject, signal } from '@angular/core';
import { ProjectsApi } from './projects.api';
import { Project } from './projects.model';

/**
 * Projets et projet courant, partagés par les écrans (FR-005). Les listes qui dépendent du projet
 * courant (sessions, recherche) se rechargent quand {@link current} change.
 */
@Injectable({ providedIn: 'root' })
export class ProjectsStore {
  private readonly api = inject(ProjectsApi);

  readonly projects = signal<Project[]>([]);
  readonly current = signal<Project | null>(null);
  readonly currentId = computed(() => this.current()?.id ?? null);

  async load(): Promise<void> {
    const [projects, current] = await Promise.all([this.api.list(), this.api.current()]);
    this.projects.set(projects);
    this.current.set(current);
  }

  async select(projectId: string): Promise<void> {
    if (projectId === this.currentId()) {
      return;
    }
    await this.api.select(projectId);
    await this.load();
  }
}
