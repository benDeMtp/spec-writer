import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { JournalApi, JournalEntry, JournalLevel } from '../../core/journal/journal.api';

/**
 * Journal des erreurs (FR-031) : les 500 dernières entrées d'avertissement et d'erreur du backend,
 * expurgées, les plus récentes d'abord, filtrables par niveau et copiables pour un signalement.
 */
@Component({
  selector: 'sw-journal-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, MatButtonModule, MatFormFieldModule, MatSelectModule],
  template: `
    <h1>Journal</h1>
    <div class="sheet">
      <p class="hint">
        Avertissements et erreurs de spec-writer, les plus récents d'abord. Les jetons, les besoins, les réponses et les
        contenus de pages ou de tickets n'y figurent jamais.
      </p>
      <p role="status" aria-live="polite" class="announce">{{ announce() }}</p>
      <div class="toolbar">
        <mat-form-field appearance="outline">
          <mat-label>Niveau</mat-label>
          <mat-select [value]="level()" (selectionChange)="filter($event.value)">
            <mat-option value="">Tous</mat-option>
            <mat-option value="WARN">Avertissements</mat-option>
            <mat-option value="ERROR">Erreurs</mat-option>
          </mat-select>
        </mat-form-field>
        <button mat-stroked-button type="button" (click)="load()">Actualiser</button>
      </div>
      @if (loaded() && !entries().length) {
        <p class="hint">Aucune entrée.</p>
      }
      <ol class="entries">
        @for (e of entries(); track $index) {
          <li [class]="e.level === 'ERROR' ? 'error' : 'warn'">
            <div class="head">
              <span class="level">{{ e.level === 'ERROR' ? 'Erreur' : 'Avertissement' }}</span>
              <time [attr.datetime]="e.at">{{ e.at | date: 'd MMM y, HH:mm:ss' }}</time>
              <span class="logger">{{ shortLogger(e.logger) }}</span>
            </div>
            <p class="message">{{ e.message }}</p>
            @if (e.cause || e.stack.length) {
              <details>
                <summary>Cause</summary>
                @if (e.cause) {
                  <p class="cause">{{ e.cause }}</p>
                }
                @if (e.stack.length) {
                  <pre tabindex="0" aria-label="Pile d'appels">{{ e.stack.join('\\n') }}</pre>
                }
              </details>
            }
            <button mat-button type="button" (click)="copy(e)" [attr.aria-label]="copyLabel(e)">
              Copier
            </button>
          </li>
        }
      </ol>
    </div>
  `,
  styles: `
    h1 {
      margin: 0 0 1.5rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-3);
      font-weight: 600;
    }
    .sheet {
      max-width: var(--sw-measure);
      padding: 1.5rem clamp(1rem, 3vw, 2rem);
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-sheet);
    }
    .hint {
      color: var(--sw-ink-soft);
    }
    .announce:empty {
      display: none;
    }
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.75rem;
    }
    .entries {
      margin: 0;
      padding: 0;
      list-style: none;
    }
    li {
      padding: 0.75rem 0 0.75rem 0.75rem;
      border-top: 1px solid var(--sw-rule);
      border-left: 4px solid var(--sw-rule);
    }
    li.error {
      border-left-color: var(--sw-outcome);
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem 1rem;
      color: var(--sw-ink-soft);
    }
    .level {
      color: var(--sw-ink);
      font-weight: 650;
    }
    .message,
    .cause {
      margin: 0.25rem 0;
      overflow-wrap: anywhere;
    }
    pre {
      overflow-x: auto;
      font-size: 0.85em;
    }
  `,
})
export class JournalPage implements OnInit {
  private readonly api = inject(JournalApi);

  protected readonly entries = signal<JournalEntry[]>([]);
  protected readonly level = signal<JournalLevel | ''>('');
  protected readonly loaded = signal(false);
  protected readonly announce = signal('');

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  protected async load(): Promise<void> {
    const level = this.level();
    const entries = await this.api.latest(level || undefined);
    this.entries.set(entries);
    this.loaded.set(true);
    this.announce.set(`${entries.length} ${entries.length > 1 ? 'entrées' : 'entrée'}.`);
  }

  protected async filter(level: JournalLevel | ''): Promise<void> {
    this.level.set(level);
    await this.load();
  }

  protected shortLogger(logger: string): string {
    return logger?.split('.').pop() ?? '';
  }

  protected copyLabel(e: JournalEntry): string {
    return `Copier l'entrée de ${new Date(e.at).toLocaleTimeString('fr-FR')}`;
  }

  /** Texte brut de l'entrée, pour un signalement. */
  protected async copy(e: JournalEntry): Promise<void> {
    const text = [`${e.at} ${e.level} ${e.logger}`, e.message, e.cause ?? '', ...e.stack].filter((l) => l).join('\n');
    try {
      await navigator.clipboard.writeText(text);
      this.announce.set('Entrée copiée.');
    } catch {
      this.announce.set("La copie n'est pas disponible dans ce navigateur.");
    }
  }
}
