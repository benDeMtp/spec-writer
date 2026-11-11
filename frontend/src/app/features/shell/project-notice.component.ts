import { LiveAnnouncer } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { ProjectFollower } from '../../core/projects/project-follower';

/**
 * Annonce d'une bascule du projet courant (012, US3, FR-012) : visible, effacée quand on quitte la
 * page, et transmise aux lecteurs d'écran par LiveAnnouncer (la page garde son propre statut).
 */
@Component({
  selector: 'sw-project-notice',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (follower.notice(); as notice) {
      <p class="notice">{{ notice }}</p>
    }
  `,
  styles: `
    .notice {
      max-width: var(--sw-measure);
      margin: 0 0 1.5rem;
      padding: 0.75rem 1rem;
      border-left: 4px solid var(--sw-pencil);
      background: var(--sw-sheet);
      border-radius: var(--sw-radius-field);
    }
  `,
})
export class ProjectNoticeComponent {
  protected readonly follower = inject(ProjectFollower);

  constructor() {
    const announcer = inject(LiveAnnouncer);
    effect(() => {
      const notice = this.follower.notice();
      if (notice) {
        void announcer.announce(notice, 'polite');
      }
    });
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe((e) => this.follower.leftFor((e as NavigationEnd).urlAfterRedirects));
  }
}
