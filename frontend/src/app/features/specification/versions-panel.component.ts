import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { VersionSummary } from '../../core/sessions/sessions.model';

const ORIGINS: Record<VersionSummary['origin'], string> = {
  GENERATED: 'Générée',
  AI_EDIT: "Modifiée avec l'IA",
  JIRA_IMPORT: 'Importée de Jira',
  CONFLICT_MERGE: 'Fusion avec Jira',
};

/** Historique des versions (FR-018) : chacune reste consultable. */
@Component({
  selector: 'sw-versions-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink],
  template: `
    <nav class="versions" aria-label="Versions de la spécification">
      <h2>Versions</h2>
      <ol>
        @for (v of versions(); track v.number) {
          <li>
            <a
              [routerLink]="['/sessions', sessionId(), 'versions', v.number]"
              [attr.aria-current]="v.number === current() ? 'page' : null"
              [class.current]="v.number === current()"
            >
              Version {{ v.number }}
            </a>
            <span class="meta">{{ origin(v) }}, {{ v.createdAt | date: 'd MMM y, HH:mm' }}</span>
          </li>
        }
      </ol>
    </nav>
  `,
  styles: `
    .versions h2 {
      margin: 0 0 0.5rem;
      font-size: var(--sw-step-0);
      font-weight: 650;
    }
    ol {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 0.25rem;
    }
    a {
      color: var(--sw-pencil);
      font-weight: 600;
    }
    a.current {
      color: var(--sw-ink);
      text-decoration: none;
    }
    .meta {
      display: block;
      color: var(--sw-ink-soft);
      font-size: var(--sw-step--1);
    }
  `,
})
export class VersionsPanelComponent {
  readonly sessionId = input.required<string>();
  readonly versions = input.required<VersionSummary[]>();
  readonly current = input.required<number>();

  protected origin(v: VersionSummary): string {
    return ORIGINS[v.origin] ?? v.origin;
  }
}
