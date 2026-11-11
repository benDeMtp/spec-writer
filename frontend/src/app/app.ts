import { ChangeDetectionStrategy, Component, ElementRef, inject, viewChild } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { LaunchTokenService } from './core/launch/launch-token.service';
import { MainNavComponent } from './features/shell/main-nav.component';
import { ProblemBannerComponent } from './shared/ui/problem-banner.component';

/** Shell : lien d'évitement, navigation, zone principale et bandeau d'erreur (T037, FR-029). */
@Component({
  selector: 'sw-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, ProblemBannerComponent, MainNavComponent],
  template: `
    <a class="skip" href="#contenu" (click)="focusMain($event)">Aller au contenu</a>
    <div class="layout">
      <header class="rail">
        <a class="brand" routerLink="/">spec-writer</a>
        <sw-main-nav />
      </header>
      <main id="contenu" #main tabindex="-1">
        @if (launch.state() === 'rejected') {
          <p class="session-lost" role="alert">
            Cette fenêtre n'est plus reliée à spec-writer. Relancez l'application depuis son raccourci pour reprendre.
          </p>
        }
        <sw-problem-banner />
        <router-outlet />
      </main>
    </div>
  `,
  styles: `
    :host {
      display: block;
      min-height: 100vh;
    }
    .skip {
      position: absolute;
      left: 1rem;
      top: -4rem;
      padding: 0.5rem 1rem;
      background: var(--sw-sheet);
      color: var(--sw-ink);
      border-radius: var(--sw-radius-field);
      z-index: 10;
    }
    .skip:focus {
      top: 1rem;
    }
    .layout {
      display: grid;
      grid-template-columns: 14rem minmax(0, 1fr);
      min-height: 100vh;
    }
    .rail {
      padding: 1.5rem 1rem;
      border-right: 1px solid var(--sw-rule);
    }
    .brand {
      display: block;
      margin: 0 0 2rem 0.75rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-1);
      font-weight: 600;
      color: var(--sw-ink);
      text-decoration: none;
    }
    main {
      padding: clamp(1.5rem, 4vw, 3rem);
      outline: none;
    }
    .session-lost {
      margin: 0 0 1.5rem;
      padding: 0.75rem 1rem;
      border-left: 4px solid var(--sw-outcome);
      background: var(--sw-sheet);
      border-radius: var(--sw-radius-field);
    }
    @media (max-width: 48rem) {
      .layout {
        grid-template-columns: 1fr;
      }
      .rail {
        border-right: none;
        border-bottom: 1px solid var(--sw-rule);
        padding: 1rem;
      }
      .brand {
        margin-bottom: 1rem;
      }
    }
  `,
})
export class App {
  protected readonly launch = inject(LaunchTokenService);
  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');

  constructor() {
    // Gestion du focus : après chaque navigation, le focus va au contenu principal.
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe((e) => {
        if ((e as NavigationEnd).id > 1) {
          queueMicrotask(() => this.main().nativeElement.focus());
        }
      });
  }

  protected focusMain(event: Event): void {
    event.preventDefault();
    this.main().nativeElement.focus();
  }
}
