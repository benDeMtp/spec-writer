import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, inject, viewChild } from '@angular/core';
import { LifecycleStore } from '../../core/lifecycle/lifecycle.store';

/**
 * L'outil est arrêté (019, contracts/ui.md) : seul cet écran s'affiche, avec la consigne de relance.
 */
@Component({
  selector: 'sw-app-stopped',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main id="contenu" class="stopped">
      <h1 #title tabindex="-1">spec-writer est arrêté</h1>
      <p>Vos données sont conservées. {{ relaunch() }}</p>
      <p>Vous pouvez fermer cet onglet.</p>
    </main>
  `,
  styles: `
    .stopped {
      max-width: var(--sw-measure);
      margin: 0 auto;
      padding: clamp(2rem, 10vh, 6rem) clamp(1.5rem, 4vw, 3rem);
    }
    h1 {
      margin: 0 0 1rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-3);
      font-weight: 600;
    }
    h1:focus {
      outline: none;
    }
    p {
      margin: 0 0 0.75rem;
      color: var(--sw-ink-soft);
    }
  `,
})
export class AppStoppedComponent implements AfterViewInit {
  private readonly lifecycle = inject(LifecycleStore);
  private readonly title = viewChild.required<ElementRef<HTMLElement>>('title');

  protected readonly relaunch = this.lifecycle.relaunch;

  ngAfterViewInit(): void {
    // le titre reçoit le focus : le lecteur d'écran le lit, sans région d'annonce de plus
    this.title().nativeElement.focus();
  }
}
