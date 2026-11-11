import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { ProjectsStore } from '../../core/projects/projects.store';

/**
 * Titre d'une page du bloc « Projet » (012, FR-003) : « <page> · <projet courant> ». La taille se
 * règle par la propriété `--sw-heading-size` posée sur l'élément.
 */
@Component({
  selector: 'sw-project-heading',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 [attr.id]="headingId() ?? null">{{ label() }}@if (store.current(); as p) {<span class="project"> · {{ p.name }}</span>}</h1>
  `,
  styles: `
    :host {
      display: block;
    }
    h1 {
      margin: 0;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-heading-size, var(--sw-step-3));
      font-weight: 600;
    }
    .project {
      color: var(--sw-ink-soft);
      font-weight: 500;
    }
  `,
})
export class ProjectHeadingComponent {
  protected readonly store = inject(ProjectsStore);
  readonly label = input.required<string>();
  /** Identifiant du titre, pour un `aria-labelledby`. */
  readonly headingId = input<string>();
}
