import { LiveAnnouncer } from '@angular/cdk/a11y';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import {
  PerimeterModule,
  PerimeterReason,
  PerimeterView,
  Qualification,
} from '../../core/conversations/conversations.model';

const GROUPS: { qualification: Qualification; title: string; one: string }[] = [
  { qualification: 'IMPACTED', title: 'Impactés', one: 'impacté' },
  { qualification: 'TO_CHECK', title: 'À vérifier', one: 'à vérifier' },
  { qualification: 'NOT_IMPACTED', title: 'Non impactés', one: 'non impacté' },
  { qualification: 'UNQUALIFIED', title: 'Non qualifiés', one: 'non qualifié' },
];

const INCOMPLETE: Record<string, string> = {
  MAX_FILES: 'nombre maximal de fichiers atteint',
  MAX_DURATION: 'durée maximale atteinte',
  ROOT_MISSING: 'une racine est introuvable',
  MAX_MODULES: 'nombre maximal de modules atteint',
  CANCELLED: 'analyse arrêtée',
};

const REJECTED: Record<string, string> = {
  TOO_SHORT: 'trop court',
  TOO_LONG: 'trop long',
  DUPLICATE: 'en double',
  OVER_LIMIT: 'au-delà de 10 termes',
};

const KINDS: Record<PerimeterModule['kind'], string> = {
  MAVEN: 'Maven',
  NPM: 'npm',
  HELM: 'Helm',
  ROOT: 'racine',
};

/** En deçà de cette largeur (px), chaque module tient sur une ligne, sa fiche s'ouvre à la demande. */
export const COMPACT_WIDTH = 640;

/**
 * Étape « Analyse du périmètre » d'une réponse de l'agent (013, contracts/ui.md) : tous les modules
 * trouvés, groupés par qualification, avec leurs raisons et leurs occurrences. Aucun module n'est
 * masqué, même si la réponse de l'agent l'omet : les non impactés sont repliés, pas retirés.
 * Dans un panneau étroit, un module tient sur une ligne (nom et raison principale) et sa fiche
 * s'ouvre à la demande. Seule la dernière analyse du fil est ouverte.
 */
@Component({
  selector: 'sw-perimeter-step',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, RouterLink],
  template: `
    @let a = analysis();
    <details class="perimeter" [open]="latest() || !!a.unavailable" [attr.aria-busy]="busy()">
      <summary>
        {{ heading() }}
        @if (!latest() && !a.unavailable) {
          <span class="older"> · remplacée par une analyse plus récente</span>
        }
      </summary>
      @if (a.unavailable === 'NO_ROOTS') {
        <p>Aucune source du code n’est déclarée pour ce projet.</p>
        <p>
          <a routerLink="/projet/reglages" fragment="sources-code">Déclarer les sources du code</a>
        </p>
      } @else {
        <p>Termes : <span translate="no">{{ a.terms.join(', ') }}</span></p>
        @if (a.rejectedTerms.length) {
          <p class="hint">
            Termes retirés :
            {{ rejected() }}
          </p>
        }
        @for (r of a.roots; track r.rootId) {
          @if (r.state !== 'SCANNED') {
            <p class="warning">
              Racine {{ r.label }} {{ r.state === 'MISSING' ? 'introuvable' : 'illisible' }} : non
              analysée
            </p>
          }
        }
        @if (a.state === 'INCOMPLETE') {
          <p class="warning">Analyse incomplète : {{ incomplete() }}.</p>
        }
        @if (a.filesSkipped) {
          <p class="hint">
            {{ a.filesSkipped }} fichier{{ a.filesSkipped > 1 ? 's' : '' }} non lu{{
              a.filesSkipped > 1 ? 's' : ''
            }}
            (trop gros ou illisibles){{ a.skippedExamples.length ? ' : ' + a.skippedExamples.join(', ') : '' }}.
          </p>
        }
        @if (!a.modules.length) {
          <p>Aucun module ne contient ces termes.</p>
        } @else {
          <p class="summary">{{ summary() }}</p>
          @for (g of groups(); track g.qualification) {
            <section [attr.aria-labelledby]="groupId(g.qualification)">
            <details class="group" [open]="g.qualification !== 'NOT_IMPACTED'">
              <summary>
                <h3 [id]="groupId(g.qualification)">{{ g.title }} ({{ g.modules.length }})</h3>
              </summary>
              @if (g.qualification === 'UNQUALIFIED' && a.qualified) {
                <p class="hint">L’agent ne s’est pas prononcé sur ces modules.</p>
              }
              @if (compact()) {
                <ul class="rows" [attr.aria-label]="'Modules ' + g.title.toLowerCase()">
                  @for (row of g.modules; track row.module.id) {
                    <li [id]="anchor(row.position)" tabindex="-1">
                      <details class="card">
                        <summary>
                          <span class="name" translate="no">{{ row.module.name }}</span>&ngsp;
                          @if (row.module.isNew) {
                            <span class="badge">Nouveau</span>&ngsp;
                          }
                          <span class="lead">{{ lead(row.module) }}</span>
                        </summary>
                        <div class="sheet">
                          <span class="path" translate="no">
                            @for (part of where(row.module); track $index; let last = $last) {
                              {{ part }}@if (!last) {
                                /<wbr />
                              }
                            }
                            · {{ kind(row.module) }}
                          </span>
                          @if (row.module.justification) {
                            <p class="why">{{ row.module.justification }}</p>
                          }
                          <ng-container
                            *ngTemplateOutlet="evidence; context: { $implicit: row.module }"
                          />
                        </div>
                      </details>
                    </li>
                  }
                </ul>
              } @else {
              <!-- rôles explicites : la sémantique du tableau reste quand il s'empile (panneau étroit) -->
              <table role="table">
                <caption class="visually-hidden">
                  Modules {{ g.title.toLowerCase() }}
                </caption>
                <colgroup>
                  <col class="col-module" />
                  <col class="col-reasons" />
                  <col class="col-why" />
                </colgroup>
                <thead role="rowgroup">
                  <tr role="row">
                    <th scope="col" role="columnheader">Module</th>
                    <th scope="col" role="columnheader">Raisons</th>
                    <th scope="col" role="columnheader">Justification</th>
                  </tr>
                </thead>
                <tbody role="rowgroup">
                  @for (row of g.modules; track row.module.id) {
                    <tr role="row" [id]="anchor(row.position)" tabindex="-1">
                      <td role="cell">
                        <span class="name" translate="no">{{ row.module.name }}</span>
                        @if (row.module.isNew) {
                          <span class="badge">Nouveau</span>
                        }
                        <span class="path" translate="no">
                          @for (part of where(row.module); track $index; let last = $last) {
                            {{ part }}@if (!last) {
                              /<wbr />
                            }
                          }
                          · {{ kind(row.module) }}
                        </span>
                      </td>
                      <td role="cell">
                        <ng-container
                          *ngTemplateOutlet="evidence; context: { $implicit: row.module }"
                        />
                      </td>
                      <td role="cell">
                        {{ row.module.justification }}
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
              }
            </details>
            </section>
          }
        }
        @if (a.architectureOnly.length) {
          <section [attr.aria-labelledby]="groupId('ARCH')">
          <details class="group">
            <summary>
              <h3 [id]="groupId('ARCH')">
                Éléments d’architecture sans module ({{ a.architectureOnly.length }})
              </h3>
            </summary>
            <ul>
              @for (x of a.architectureOnly; track $index) {
                <li>
                  {{ x.element }} : relié à {{ x.related
                  }}{{ x.technology || x.description ? ' (' + details(x) + ')' : '' }}
                </li>
              }
            </ul>
          </details>
          </section>
        }
      }
    </details>

    <!-- raisons et occurrences d'un module : colonne du tableau, ou fiche d'une ligne -->
    <ng-template #evidence let-m>
      <ul class="reasons" aria-label="Raisons">
        @for (r of $any(m).reasons; track $index) {
          <li>
            @switch (r.type) {
              @case ('DEPENDENCY') {
                dépend de
                <ol class="chain" aria-label="Chaîne de dépendance">
                  @for (c of r.chain; track c.id) {
                    <li>{{ c.name }}</li>
                  }
                </ol>
              }
              @default {
                {{ reason(r) }}
              }
            }
          </li>
        }
        @if ($any(m).archStatus === 'ABSENT') {
          <li class="hint">absent du modèle d’architecture</li>
        }
      </ul>
      @if ($any(m).samples.length) {
        <details class="occurrences">
          <summary>Occurrences ({{ $any(m).occurrences }})</summary>
          <ul>
            @for (o of $any(m).samples; track $index) {
              <li>
                <span class="where" translate="no">{{ o.path }}:{{ o.line }}</span>
                <code translate="no">{{ o.excerpt }}</code>
              </li>
            }
          </ul>
          @for (t of truncated(m); track t.term) {
            <p class="hint">
              {{ t.shown }} premières sur {{ t.count }} pour {{ t.term }}. Affinez le terme pour un
              détail complet.
            </p>
          }
        </details>
      }
    </ng-template>
  `,
  styles: `
    :host {
      display: block;
    }
    .perimeter {
      margin: 0 0 1rem;
      padding: 0.5rem 0.75rem;
      border: 1px solid var(--sw-rule);
      border-left: 3px solid var(--sw-pencil);
      border-radius: var(--sw-radius-field);
      background: var(--sw-sheet);
    }
    .perimeter > summary {
      cursor: pointer;
      font-weight: 600;
    }
    h3 {
      font-size: 1rem;
      margin: 1rem 0 0.25rem;
    }
    table {
      border-collapse: collapse;
      width: 100%;
      /* mêmes colonnes d'un groupe à l'autre */
      table-layout: fixed;
    }
    .col-module {
      width: 28%;
    }
    .col-reasons {
      width: 40%;
    }
    th,
    td {
      text-align: left;
      vertical-align: top;
      padding: 0.25rem 0.5rem 0.25rem 0;
      border-bottom: 1px solid var(--sw-rule);
    }
    .older {
      font-weight: 400;
      color: var(--sw-ink-soft);
    }
    .group > summary {
      cursor: pointer;
    }
    .group > summary h3 {
      display: inline;
    }
    .group {
      margin: 1rem 0 0.25rem;
    }
    .rows {
      margin: 0.25rem 0 0;
      padding: 0;
      list-style: none;
    }
    .rows > li {
      border-bottom: 1px solid var(--sw-rule);
    }
    .rows > li:focus {
      outline: 2px solid var(--sw-pencil);
      outline-offset: 2px;
    }
    .card > summary {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0 0.5rem;
      padding: 0.3rem 0;
      cursor: pointer;
    }
    .lead {
      color: var(--sw-ink-soft);
      font-size: 0.875rem;
      overflow-wrap: anywhere;
    }
    .card .sheet {
      padding: 0 0 0.5rem 1rem;
    }
    .why {
      margin: 0.25rem 0;
    }
    tr:focus {
      outline: 2px solid var(--sw-pencil);
      outline-offset: 2px;
    }
    .name {
      font-weight: 600;
      overflow-wrap: anywhere;
    }
    .path,
    .hint,
    .where {
      display: block;
      color: var(--sw-ink-soft);
      font-size: 0.875rem;
    }
    .path,
    .where {
      overflow-wrap: anywhere;
    }
    .badge {
      margin-left: 0.5rem;
      padding: 0 0.375rem;
      border: 1px solid currentColor;
      border-radius: 0.25rem;
      font-size: 0.75rem;
    }
    .warning {
      font-weight: 600;
    }
    .reasons,
    .chain {
      margin: 0;
      padding-left: 1rem;
    }
    .chain {
      display: inline;
      padding: 0;
      list-style: none;
    }
    .chain li {
      display: inline;
    }
    .chain li + li::before {
      content: ' → ';
    }
    code {
      white-space: pre-wrap;
      word-break: break-word;
    }
    .visually-hidden {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
    }
  `,
})
export class PerimeterStepComponent {
  readonly analysis = input.required<PerimeterView>();
  /** Message de l'agent qui porte l'analyse, pour les ancres des citations. */
  readonly messageId = input.required<string>();
  /** Le tour est en cours : la qualification peut encore arriver. */
  readonly live = input(false);
  /** Dernière analyse du fil : ouverte ; une analyse plus ancienne est repliée. */
  readonly latest = input(true);

  /** Panneau étroit : un module par ligne, sa fiche à la demande. */
  protected readonly compact = signal(false);

  private readonly announcer = inject(LiveAnnouncer);
  private announced = '';

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    if (typeof ResizeObserver === 'function') {
      const watch = new ResizeObserver(([entry]) =>
        this.compact.set(entry.contentRect.width > 0 && entry.contentRect.width < COMPACT_WIDTH),
      );
      watch.observe(host);
      inject(DestroyRef).onDestroy(() => watch.disconnect());
    }
    // annonce une fois la fin du recensement, puis une fois la qualification, pendant le tour
    effect(() => {
      const a = this.analysis();
      if (!this.live() || a.unavailable) {
        return;
      }
      const key = `${a.ordinal}:${a.qualified}`;
      if (key === this.announced) {
        return;
      }
      this.announced = key;
      void this.announcer.announce(
        a.qualified
          ? 'Modules qualifiés.'
          : `Analyse du périmètre terminée : ${plural(a.modules.length, 'module')}.`,
      );
    });
  }

  protected busy(): boolean {
    const a = this.analysis();
    return this.live() && !a.qualified && !a.unavailable && a.modules.length > 0;
  }

  protected heading(): string {
    const a = this.analysis();
    if (a.unavailable) {
      return 'Analyse du périmètre';
    }
    return [
      'Analyse du périmètre',
      plural(a.terms.length, 'terme'),
      plural(a.roots.length, 'racine'),
      plural(a.filesScanned, 'fichier'),
      `${(a.durationMs / 1000).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} s`,
    ].join(' · ');
  }

  protected summary(): string {
    const a = this.analysis();
    const head = `${plural(a.modules.length, 'module')} : `;
    if (!a.qualified && this.live()) {
      return head + 'qualification en cours…';
    }
    const s = a.summary;
    const counts = [s.impacted, s.toCheck, s.notImpacted, s.unqualified];
    return (
      head +
      GROUPS.map((g, i) => ({ n: counts[i], g }))
        .filter((x) => x.n > 0)
        .map((x) => `${x.n} ${x.g.one}${x.n > 1 && x.g.qualification !== 'TO_CHECK' ? 's' : ''}`)
        .join(', ')
    );
  }

  protected groups(): {
    qualification: Qualification;
    title: string;
    modules: { module: PerimeterModule; position: number }[];
  }[] {
    const rows = this.analysis().modules.map((module, position) => ({ module, position }));
    return GROUPS.map((g) => ({
      qualification: g.qualification,
      title: g.title,
      modules: rows.filter((r) => r.module.qualification === g.qualification),
    })).filter((g) => g.modules.length);
  }

  protected groupId(group: string): string {
    return `perimeter-${this.messageId()}-${this.analysis().ordinal}-${group}`;
  }

  protected anchor(position: number): string {
    return perimeterAnchor(this.messageId(), this.analysis().ordinal, position);
  }

  /** Racine et segments du chemin : la ligne se coupe après un « / », pas au milieu d'un nom. */
  protected where(m: PerimeterModule): string[] {
    return [m.rootLabel, ...(m.path ? m.path.split('/') : [])];
  }

  /** Raison principale d'un module, et le nombre des autres : ce qu'affiche sa ligne. */
  protected lead(m: PerimeterModule): string {
    const first = m.reasons[0];
    if (!first) {
      return '';
    }
    const text =
      first.type === 'DEPENDENCY'
        ? `dépend de ${(first.chain ?? []).map((c) => c.name).join(' → ')}`
        : this.reason(first);
    const others = m.reasons.length - 1;
    return others > 0 ? `${text} (+${others})` : text;
  }

  protected kind(m: PerimeterModule): string {
    return KINDS[m.kind];
  }

  protected rejected(): string {
    return this.analysis()
      .rejectedTerms.map((r) => `${r.term} (${REJECTED[r.reason]})`)
      .join(', ');
  }

  protected incomplete(): string {
    const reason = this.analysis().incompleteReason;
    return reason ? INCOMPLETE[reason] : 'limite atteinte';
  }

  protected reason(r: PerimeterReason): string {
    switch (r.type) {
      case 'TERM':
        return `${r.term} ×${r.count}`;
      case 'AGGREGATOR':
        return `agrège ${r.of?.name ?? ''}`;
      case 'ARCHITECTURE':
        return `relié à ${r.related}${r.technology || r.description ? ` (${this.details(r)})` : ''}`;
      default:
        return '';
    }
  }

  protected details(x: { technology: string | null; description: string | null }): string {
    return [x.technology, x.description ? `« ${x.description} »` : null]
      .filter(Boolean)
      .join(', ');
  }

  /** Termes dont l'échantillon d'occurrences est plus petit que le total. */
  protected truncated(m: PerimeterModule): { term: string; shown: number; count: number }[] {
    return m.reasons
      .filter((r) => r.type === 'TERM' && r.term && r.count)
      .map((r) => ({
        term: r.term!,
        count: r.count!,
        shown: m.samples.filter((o) => o.term === r.term).length,
      }))
      .filter((t) => t.shown < t.count);
  }
}

/** Ancre de la ligne d'un module, cible des citations `[n]` d'une source MODULE. */
export function perimeterAnchor(messageId: string, ordinal: number, position: number): string {
  return `perimeter-${messageId}-${ordinal}-${position}`;
}

function plural(n: number, word: string): string {
  return `${n.toLocaleString('fr-FR')} ${word}${n > 1 ? 's' : ''}`;
}
