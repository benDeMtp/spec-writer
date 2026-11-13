import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { BrowserApi, BrowserSettings } from '../../core/settings/browser.api';

/** Valeur du bouton radio « navigateur du système ». */
const SYSTEM = '';

/**
 * Navigateur où s'ouvre spec-writer (013, T057) : celui du système par défaut, ou un navigateur
 * détecté sur le poste. Le choix vaut pour les prochains lancements ; « Ouvrir » le met en œuvre
 * tout de suite, sans relancer l'application.
 */
@Component({
  selector: 'sw-browser-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule],
  template: `
    <section class="sheet" aria-labelledby="browser-title">
      <h3 id="browser-title">Navigateur</h3>
      <p class="scope">Navigateur où s'ouvre spec-writer à chaque lancement.</p>
      @if (current(); as c) {
        @if (c.missing) {
          <div class="status warn" role="status">
            <p>Le navigateur choisi n'est plus installé : spec-writer s'ouvre dans celui du système.</p>
          </div>
        }
        <form (submit)="$event.preventDefault(); save()" aria-label="Choisir le navigateur">
          <fieldset class="choices">
            <legend>Ouvrir spec-writer dans</legend>
            <label>
              <input type="radio" name="browser" [value]="system" [checked]="choice() === system" (change)="choice.set(system)" />
              Navigateur par défaut du système
            </label>
            @for (b of c.installed; track b.id) {
              <label>
                <input type="radio" name="browser" [value]="b.id" [checked]="choice() === b.id" (change)="choice.set(b.id)" />
                {{ b.name }}
              </label>
            }
          </fieldset>
          @if (!c.installed.length) {
            <p class="hint">Aucun autre navigateur n'a été détecté sur ce poste.</p>
          }
          <div class="actions">
            <button mat-flat-button type="submit" [disabled]="busy() || !dirty()">Utiliser ce navigateur</button>
            <button mat-stroked-button type="button" [disabled]="busy() || dirty()" (click)="open()">
              Ouvrir spec-writer dans ce navigateur
            </button>
          </div>
        </form>
        <p class="hint" role="status" aria-live="polite">{{ message() }}</p>
      } @else {
        <p role="status" aria-live="polite">Recherche des navigateurs installés…</p>
      }
    </section>
  `,
  styleUrl: './settings-card.scss',
  styles: `
    .choices {
      display: grid;
      gap: 0.35rem;
      margin: 0;
      padding: 0;
      border: 0;
    }
    legend {
      margin-bottom: 0.35rem;
      padding: 0;
      font-weight: 600;
    }
    .choices label {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      min-height: 2rem;
      cursor: pointer;
    }
    input[type='radio'] {
      width: 1.1rem;
      height: 1.1rem;
      margin: 0;
      accent-color: var(--sw-pencil);
    }
  `,
})
export class BrowserCardComponent {
  private readonly api = inject(BrowserApi);

  protected readonly system = SYSTEM;
  protected readonly current = signal<BrowserSettings | null>(null);
  protected readonly choice = signal(SYSTEM);
  protected readonly busy = signal(false);
  protected readonly message = signal('');

  /** Le choix affiché diffère de celui qui est enregistré. */
  protected readonly dirty = computed(() => this.choice() !== (this.current()?.selected ?? SYSTEM));

  constructor() {
    void this.api.get().then((s) => this.show(s)).catch(() => undefined);
  }

  private show(s: BrowserSettings): void {
    this.current.set(s);
    this.choice.set(s.selected ?? SYSTEM);
  }

  protected async save(): Promise<void> {
    this.busy.set(true);
    try {
      const saved = await this.api.save(this.choice() || null);
      this.show(saved);
      this.message.set(`Enregistré : spec-writer s'ouvrira dans ${this.label(saved.selected)} au prochain lancement.`);
    } catch {
      this.message.set('');
      // le bandeau d'erreur donne le motif
    } finally {
      this.busy.set(false);
    }
  }

  protected async open(): Promise<void> {
    this.busy.set(true);
    try {
      const r = await this.api.open();
      const name = r.browser?.name ?? 'le navigateur du système';
      this.message.set(
        r.opened
          ? `spec-writer s'ouvre dans ${name}. Cette fenêtre reste utilisable.`
          : `Impossible d'ouvrir ${name}. Relancez spec-writer depuis son raccourci.`,
      );
    } catch {
      this.message.set('');
    } finally {
      this.busy.set(false);
    }
  }

  private label(id: string | null): string {
    return this.current()?.installed.find((b) => b.id === id)?.name ?? 'le navigateur du système';
  }
}
