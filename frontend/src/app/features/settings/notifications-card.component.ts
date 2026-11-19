import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AppEventsService } from '../../core/events/app-events.service';
import { NotificationsApi } from '../../core/settings/notifications.api';

/** Où accorder la permission quand le navigateur l'a refusée (FR-016). */
export const DENIED_HELP =
  "Le navigateur bloque les notifications de spec-writer. Autorisez-les dans les réglages du site (icône à gauche de l'adresse), puis réactivez-les ici.";

/**
 * Notifications du navigateur des sources mises à jour (017, US3, FR-016) : désactivées par défaut ;
 * l'activation demande la permission du navigateur, et n'est enregistrée que si elle est accordée.
 */
@Component({
  selector: 'sw-notifications-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="sheet" aria-labelledby="notifications-title" data-testid="notifications-card">
      <h3 id="notifications-title">Notifications</h3>
      <p class="scope">
        Une notification du navigateur dit quelle source a été mise à jour et combien d'éléments ont changé, jamais leur
        contenu. Elle part de l'onglet de spec-writer : rien si aucun onglet n'est ouvert.
      </p>
      @if (loaded()) {
        <label class="toggle">
          <input type="checkbox" [checked]="enabled()" [disabled]="busy() || unsupported" (change)="toggle($event)" />
          Prévenir quand une source est mise à jour
        </label>
        @if (unsupported) {
          <p class="hint">Ce navigateur n'affiche pas de notifications.</p>
        }
      }
      <p class="hint" role="status" aria-live="polite">{{ message() }}</p>
    </section>
  `,
  styleUrl: './settings-card.scss',
  styles: `
    .toggle {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      min-height: 2rem;
      cursor: pointer;
    }
    input[type='checkbox'] {
      width: 1.1rem;
      height: 1.1rem;
      margin: 0;
      accent-color: var(--sw-pencil);
    }
  `,
})
export class NotificationsCardComponent {
  private readonly api = inject(NotificationsApi);
  private readonly events = inject(AppEventsService);

  protected readonly unsupported = typeof Notification === 'undefined';
  protected readonly enabled = signal(false);
  protected readonly loaded = signal(false);
  protected readonly busy = signal(false);
  protected readonly message = signal('');

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      this.enabled.set((await this.api.get()).sourceUpdates);
    } finally {
      this.loaded.set(true);
    }
  }

  protected async toggle(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const wanted = input.checked;
    this.busy.set(true);
    try {
      if (wanted) {
        const permission =
          Notification.permission === 'default' ? await Notification.requestPermission() : Notification.permission;
        if (permission !== 'granted') {
          input.checked = false;
          this.message.set(DENIED_HELP);
          return;
        }
      }
      const saved = (await this.api.save(wanted)).sourceUpdates;
      this.enabled.set(saved);
      this.events.enabled.set(saved);
      this.message.set(saved ? 'Notifications activées.' : 'Notifications désactivées.');
    } catch {
      input.checked = this.enabled();
    } finally {
      this.busy.set(false);
    }
  }
}
