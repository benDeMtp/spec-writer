import { Injectable, effect, inject, untracked } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { ProjectsStore } from '../projects/projects.store';

/**
 * Titre de l'onglet (012, research.md R5) : `<page> · <projet> · spec-writer` pour les pages du
 * bloc « Projet » (`data.scope = 'project'`), `<page> · spec-writer` pour les autres. Recalculé
 * quand le projet courant change.
 */
@Injectable({ providedIn: 'root' })
export class ProjectTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly projects = inject(ProjectsStore);
  private last: RouterStateSnapshot | null = null;

  constructor() {
    super();
    effect(() => {
      this.projects.current();
      untracked(() => this.last && this.updateTitle(this.last));
    });
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.last = snapshot;
    const page = this.buildTitle(snapshot);
    if (!page) {
      return;
    }
    let route = snapshot.root;
    while (route.firstChild) {
      route = route.firstChild;
    }
    const project = route.data['scope'] === 'project' ? this.projects.current()?.name : undefined;
    this.title.setTitle([page, project, 'spec-writer'].filter(Boolean).join(' · '));
  }
}
