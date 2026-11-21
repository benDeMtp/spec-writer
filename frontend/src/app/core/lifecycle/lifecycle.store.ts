import { Injectable, signal } from '@angular/core';

export type Lifecycle = 'running' | 'stopped';

/**
 * L'outil est-il encore lancé, vu de cet onglet (019, research.md R7) ? `stopped` est définitif
 * pour l'onglet : seule une relance de l'outil ouvre un nouvel onglet relié.
 */
@Injectable({ providedIn: 'root' })
export class LifecycleStore {
  readonly state = signal<Lifecycle>('running');
  /**
   * Consigne de relance : précise dans l'onglet qui a quitté (système et mode d'installation lus
   * avant l'arrêt), générale ailleurs.
   */
  readonly relaunch = signal('Pour le relancer, ouvrez-le depuis son raccourci.');
  private probing: Promise<void> | null = null;

  stopped(): void {
    this.state.set('stopped');
  }

  /**
   * Sonde après une coupure du flux d'événements ou une erreur réseau : seule une erreur réseau
   * fait passer l'onglet à l'écran d'arrêt ; toute réponse HTTP, même un refus, le laisse tel quel.
   */
  probe(): Promise<void> {
    if (this.state() === 'stopped') {
      return Promise.resolve();
    }
    this.probing ??= fetch('/api/application', { cache: 'no-store', credentials: 'same-origin' })
      .then(
        () => undefined,
        () => this.stopped(),
      )
      .finally(() => (this.probing = null));
    return this.probing;
  }
}
