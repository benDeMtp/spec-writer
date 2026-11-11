import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Router } from '@angular/router';
import { ProblemStore } from '../../core/http/problem.store';

/** Bandeau d'erreur commun : message métier et action proposée, annoncé aux lecteurs d'écran. */
@Component({
  selector: 'sw-problem-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule],
  template: `
    <div class="banner" role="alert" aria-live="assertive" [hidden]="!problem()">
      @if (problem(); as p) {
        <p class="message">{{ p.userMessage }}</p>
        <div class="actions">
          @if (actionLabel(); as label) {
            <button mat-flat-button type="button" (click)="act()">{{ label }}</button>
          }
          <button mat-button type="button" (click)="store.dismiss()">Fermer</button>
        </div>
      }
    </div>
  `,
  styles: `
    .banner {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem 1.5rem;
      margin: 0 0 1.5rem;
      padding: 0.75rem 1rem;
      border-left: 4px solid var(--sw-outcome);
      border-radius: var(--sw-radius-field);
      background: var(--sw-sheet);
    }
    .banner[hidden] {
      display: none;
    }
    .message {
      margin: 0;
      flex: 1 1 24ch;
    }
    .actions {
      display: flex;
      gap: 0.5rem;
    }
  `,
})
export class ProblemBannerComponent {
  protected readonly store = inject(ProblemStore);
  private readonly router = inject(Router);
  protected readonly problem = this.store.current;

  protected readonly actionLabel = computed(() => {
    switch (this.problem()?.action) {
      case 'RETRY':
        return 'Réessayer';
      case 'OPEN_SETTINGS':
        return 'Ouvrir la configuration';
      case 'OPEN_PROJECT_SETTINGS':
        return 'Ouvrir les réglages du projet';
      case 'RECONNECT':
        return 'Se reconnecter';
      case 'EDIT_INPUT':
        return 'Corriger la saisie';
      default:
        return null;
    }
  });

  protected act(): void {
    const action = this.problem()?.action;
    this.store.dismiss();
    if (action === 'OPEN_SETTINGS' || action === 'RECONNECT') {
      void this.router.navigateByUrl('/configuration');
    } else if (action === 'OPEN_PROJECT_SETTINGS') {
      // 012 : la clé Jira se règle dans le projet
      void this.router.navigateByUrl('/projet/reglages#jira');
    } else if (action === 'RETRY') {
      void this.router.navigateByUrl(this.router.url, { onSameUrlNavigation: 'reload' });
    } else if (action === 'EDIT_INPUT') {
      document.querySelector<HTMLElement>('main [aria-invalid="true"], main textarea, main input')?.focus();
    }
  }
}
