import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { count } from '../text';

export type DiffLineKind = 'context' | 'added' | 'removed' | 'hunk';

export interface DiffLine {
  kind: DiffLineKind;
  text: string;
}

/** Lignes d'un diff unifié, sans les en-têtes de fichiers. */
export function parseUnifiedDiff(diff: string): DiffLine[] {
  const lines: DiffLine[] = [];
  for (const raw of diff.split('\n')) {
    if (raw.startsWith('--- ') || raw.startsWith('+++ ') || raw === '') {
      continue;
    }
    if (raw.startsWith('@@')) {
      const m = /^@@ -(\d+)(?:,\d+)? \+(\d+)/.exec(raw);
      lines.push({ kind: 'hunk', text: m ? `À partir de la ligne ${m[2]}` : raw });
    } else if (raw.startsWith('+')) {
      lines.push({ kind: 'added', text: raw.slice(1) });
    } else if (raw.startsWith('-')) {
      lines.push({ kind: 'removed', text: raw.slice(1) });
    } else {
      lines.push({ kind: 'context', text: raw.slice(1) });
    }
  }
  return lines;
}

/**
 * Différences entre la version actuelle et la proposition. Ajouts et suppressions sont dits en
 * toutes lettres aux lecteurs d'écran et marqués par un signe, pas seulement par la couleur ; la
 * zone défile au clavier.
 */
@Component({
  selector: 'sw-unified-diff',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="totals">{{ totals() }}</p>
    @if (lines().length) {
      <div class="diff" role="region" tabindex="0" [attr.aria-label]="label()">
        <ol>
          @for (line of lines(); track $index) {
            <li [class]="line.kind">
              @switch (line.kind) {
                @case ('added') {
                  <span class="sign" aria-hidden="true">+</span><span class="sw-visually-hidden">Ajouté : </span>
                }
                @case ('removed') {
                  <span class="sign" aria-hidden="true">−</span><span class="sw-visually-hidden">Supprimé : </span>
                }
                @default {
                  <span class="sign" aria-hidden="true"></span>
                }
              }
              <span class="text">{{ line.text || ' ' }}</span>
            </li>
          }
        </ol>
      </div>
    }
  `,
  styles: `
    .totals {
      margin: 0 0 0.5rem;
      color: var(--sw-ink-soft);
    }
    .diff {
      max-height: 28rem;
      overflow: auto;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
      background: var(--sw-sheet);
    }
    .diff:focus-visible {
      outline: 2px solid var(--sw-pencil);
      outline-offset: 2px;
    }
    ol {
      margin: 0;
      padding: 0.25rem 0;
      list-style: none;
      font-family: var(--sw-font-doc);
      line-height: 1.5;
    }
    li {
      display: grid;
      grid-template-columns: 1.5rem minmax(0, 1fr);
      padding: 0 0.75rem 0 0.25rem;
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }
    .sign {
      text-align: center;
      font-weight: 700;
    }
    .added {
      background: color-mix(in srgb, var(--sw-pencil) 14%, transparent);
    }
    .removed {
      background: color-mix(in srgb, var(--sw-outcome) 14%, transparent);
    }
    .removed .text {
      text-decoration: line-through;
      text-decoration-thickness: 1px;
    }
    .hunk {
      margin-top: 0.5rem;
      color: var(--sw-ink-soft);
      font-family: var(--sw-font-ui);
      font-size: var(--sw-step--1);
    }
  `,
})
export class UnifiedDiffComponent {
  readonly diff = input.required<string>();
  readonly label = input('Différences avec la version actuelle');

  protected readonly lines = computed(() => parseUnifiedDiff(this.diff()));
  protected readonly totals = computed(() => {
    const added = this.lines().filter((l) => l.kind === 'added').length;
    const removed = this.lines().filter((l) => l.kind === 'removed').length;
    if (!added && !removed) {
      return 'Aucune différence avec la version actuelle.';
    }
    return `${count(added, 'ligne ajoutée', 'lignes ajoutées')}, ${count(removed, 'ligne supprimée', 'lignes supprimées')}.`;
  });
}
