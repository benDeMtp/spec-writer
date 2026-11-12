import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProjectsStore } from '../../core/projects/projects.store';
import { ProjectHeadingComponent } from '../../shared/ui/project-heading.component';
import { KnowledgePanelComponent } from './knowledge-panel.component';

/**
 * Connaissance du projet courant (012, US2, FR-007) : ce que spec-writer a tiré de Jira, de la
 * documentation et du modèle d'architecture, l'indexation, la recherche d'essai et les ADR. Les
 * sources se règlent dans les réglages du projet.
 */
@Component({
  selector: 'sw-project-knowledge-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KnowledgePanelComponent, ProjectHeadingComponent, RouterLink],
  template: `
    <sw-project-heading label="Connaissance du projet" />
    <p class="lead">
      <a routerLink="/projet/connaissance/adr">ADR du projet</a>
      <a routerLink="/projet/reglages">Réglages du projet</a>
    </p>
    @if (store.currentId(); as id) {
      <section class="sheet" aria-label="Connaissance du projet">
        <sw-knowledge-panel [projectId]="id" />
      </section>
    }
  `,
  styles: `
    sw-project-heading {
      margin: 0 0 0.5rem;
    }
    .lead {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem 1.5rem;
      margin: 0 0 1.5rem;
    }
    .sheet {
      max-width: var(--sw-measure);
      padding: 1.5rem clamp(1rem, 3vw, 2rem);
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-sheet);
    }
  `,
})
export class ProjectKnowledgePage {
  protected readonly store = inject(ProjectsStore);

  constructor() {
    if (!this.store.current()) {
      void this.store.load().catch(() => undefined);
    }
  }
}
