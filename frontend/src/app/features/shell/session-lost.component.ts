import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Fenêtre qui n'est plus reliée à spec-writer (012, US5, FR-018) : seul cet écran s'affiche, au
 * lieu d'un cadre aux pages vides trompeuses.
 */
@Component({
  selector: 'sw-session-lost',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main id="contenu" class="lost">
      <h1>Relancez spec-writer depuis son raccourci</h1>
      <p>
        Cette fenêtre n'est plus reliée à spec-writer, par exemple après un redémarrage de l'application ou de
        l'ordinateur. Vos données sont conservées.
      </p>
      <p>Fermez cet onglet, puis ouvrez spec-writer depuis son raccourci : une nouvelle fenêtre s'ouvre.</p>
    </main>
  `,
  styles: `
    .lost {
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
    p {
      margin: 0 0 0.75rem;
      color: var(--sw-ink-soft);
    }
  `,
})
export class SessionLostComponent {}
