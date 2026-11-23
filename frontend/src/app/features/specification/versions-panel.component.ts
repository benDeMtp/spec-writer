import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink } from '@angular/router';
import { VersionSummary } from '../../core/sessions/sessions.model';
import { SwDatePipe } from '../../shared/ui/sw-date.pipe';

const ORIGINS: Record<VersionSummary['origin'], string> = {
  GENERATED: 'Générée',
  AI_EDIT: "Modifiée avec l'IA",
  JIRA_IMPORT: 'Importée de Jira',
  CONFLICT_MERGE: 'Fusion avec Jira',
  DISCUSSION: 'Issue de la discussion',
};

/**
 * Sélecteur de version (FR-018) : « Version 4 ▾ », chaque version reste consultable depuis le
 * menu. La version affichée, issue d'une discussion, mène à sa conversation tant qu'elle existe
 * (011, FR-011).
 */
@Component({
  selector: 'sw-versions-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatMenuModule, RouterLink, SwDatePipe],
  template: `
    <button
      mat-button
      type="button"
      class="version-menu"
      [matMenuTriggerFor]="menu"
      [attr.aria-label]="'Version ' + current() + ', changer de version'"
    >
      Version {{ current() }} <span aria-hidden="true">▾</span>
    </button>
    <mat-menu #menu="matMenu" class="version-items">
      @for (v of versions(); track v.number) {
        <a
          mat-menu-item
          [routerLink]="['/sessions', sessionId(), 'versions', v.number]"
          queryParamsHandling="preserve"
          [attr.aria-current]="v.number === current() ? 'page' : null"
        >
          <span class="number">Version {{ v.number }}</span>
          <span class="meta">{{ origin(v) }}, {{ v.createdAt | swDate: 'd MMM y, HH:mm' }}</span>
        </a>
      }
    </mat-menu>
    @if (conversationId(); as c) {
      <a class="origin" [routerLink]="['/conversations', c]">Discussion d’origine</a>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: baseline;
      gap: 0.25rem;
      flex-wrap: wrap;
    }
    .version-menu {
      font-weight: 600;
    }
    .origin {
      color: var(--sw-pencil);
      font-size: var(--sw-step--1);
    }
    .number {
      font-weight: 600;
      margin-right: 0.5rem;
    }
    .meta {
      color: var(--sw-ink-soft);
      font-size: var(--sw-step--1);
    }
  `,
})
export class VersionsPanelComponent {
  readonly sessionId = input.required<string>();
  readonly versions = input.required<VersionSummary[]>();
  readonly current = input.required<number>();

  protected readonly conversationId = computed(
    () => this.versions().find((v) => v.number === this.current())?.conversationId ?? null,
  );

  protected origin(v: VersionSummary): string {
    return ORIGINS[v.origin] ?? v.origin;
  }
}
