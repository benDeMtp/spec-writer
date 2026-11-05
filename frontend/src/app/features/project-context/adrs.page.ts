import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { KnowledgeApi } from '../../core/projects/knowledge.api';
import { AdrList, AdrStatus, AdrSummary } from '../../core/projects/projects.model';
import { ProjectsApi } from '../../core/projects/projects.api';
import { count } from '../../shared/text';

const STATUSES: AdrStatus[] = ['ACCEPTED', 'PROPOSED', 'SUPERSEDED', 'DEPRECATED', 'UNKNOWN'];

const LABELS: Record<AdrStatus, [string, string, string]> = {
  ACCEPTED: ['Accepté', 'accepté', 'acceptés'],
  PROPOSED: ['Proposé', 'proposé', 'proposés'],
  SUPERSEDED: ['Remplacé', 'remplacé', 'remplacés'],
  DEPRECATED: ['Obsolète', 'obsolète', 'obsolètes'],
  UNKNOWN: ['Inconnu', 'inconnu', 'inconnus'],
};

/**
 * ADR du projet (004) : ce que l'outil a reconnu dans la documentation, ce qu'il transmet à l'IA et
 * ce qui pose problème. Les titres ouvrent la page de l'ADR dans un nouvel onglet ; le texte des
 * ADR n'est pas montré ici.
 */
@Component({
  selector: 'sw-adrs-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <p class="back"><a [routerLink]="['/projets', id(), 'contexte']">Contexte du projet</a></p>
    <h1>ADR du projet {{ projectName() }}</h1>
    @if (list(); as l) {
      @if (l.indexing) {
        <p class="meta" role="status">Indexation en cours : la liste peut encore changer.</p>
      }
      @if (l.adrs.length) {
        <p class="summary">{{ summary(l) }}</p>
        <p class="filter">
          <label for="adr-status">Statut</label>
          <select id="adr-status" [value]="filter()" (change)="filter.set($any($event.target).value)">
            <option value="">Tous</option>
            @for (s of statuses; track s) {
              <option [value]="s">{{ label(s) }}</option>
            }
          </select>
        </p>
        <table aria-describedby="adr-legend">
          <caption id="adr-legend">
            Les ADR remplacés ou obsolètes ne sont pas transmis à l'IA.
          </caption>
          <thead>
            <tr>
              <th scope="col">N°</th>
              <th scope="col">Titre</th>
              <th scope="col">Statut</th>
              <th scope="col">Remplacé par</th>
            </tr>
          </thead>
          <tbody>
            @for (a of shown(); track $index) {
              <tr [class.retired]="!a.sentToAi">
                <td>{{ number(a) }}</td>
                <td>
                  @if (a.url) {
                    <a class="title" [href]="a.url" target="_blank" rel="noopener noreferrer" [attr.aria-label]="a.title + ' (nouvel onglet)'">{{ a.title }}</a>
                  } @else {
                    <span class="title">{{ a.title }}</span>
                  }
                  @if (a.duplicate) {
                    <span class="flag">numéro en double</span>
                  }
                </td>
                <td>
                  <span class="badge">{{ label(a.status) }}</span>
                  @if (!a.sentToAi) {
                    <span class="flag">non transmis à l'IA</span>
                  }
                </td>
                <td>{{ successor(a) }}</td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <p>Aucun ADR n'a été trouvé dans la documentation de ce projet.</p>
        <p><a [routerLink]="['/projets', id(), 'contexte']">Revenir au contexte du projet</a></p>
      }
    }
  `,
  styles: `
    .summary { font-weight: 500; }
    .filter { display: flex; gap: 8px; align-items: center; }
    table { border-collapse: collapse; width: 100%; }
    caption { text-align: left; color: var(--sw-muted, #555); padding-bottom: 8px; }
    th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid #ddd; vertical-align: top; }
    tr.retired td { color: #595959; background: #f4f4f4; }
    .badge { display: inline-block; padding: 0 6px; border: 1px solid currentColor; border-radius: 4px; }
    .flag { display: inline-block; margin-left: 8px; font-size: 0.9em; font-style: italic; }
  `,
})
export class AdrsPage {
  readonly id = input.required<string>();

  private readonly knowledge = inject(KnowledgeApi);
  private readonly projects = inject(ProjectsApi);

  protected readonly statuses = STATUSES;
  protected readonly list = signal<AdrList | null>(null);
  protected readonly projectName = signal('');
  protected readonly filter = signal<AdrStatus | ''>('');
  protected readonly shown = computed(() => {
    const l = this.list();
    const f = this.filter();
    return l ? l.adrs.filter((a) => !f || a.status === f) : [];
  });

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => void this.load(id));
    });
  }

  private async load(id: string): Promise<void> {
    const [all, adrs] = await Promise.all([this.projects.list(), this.knowledge.adrs(id)]);
    this.projectName.set(all.find((p) => p.id === id)?.name ?? '');
    this.list.set(adrs);
  }

  protected label(s: AdrStatus): string {
    return LABELS[s][0];
  }

  protected number(a: AdrSummary): string {
    return a.number === null ? '—' : String(a.number).padStart(4, '0');
  }

  protected successor(a: AdrSummary): string {
    if (!a.supersededBy) {
      return '';
    }
    return a.successorFound ? a.supersededBy : `${a.supersededBy} (introuvable)`;
  }

  protected summary(l: AdrList): string {
    const parts = STATUSES.filter((s) => (l.byStatus[s] ?? 0) > 0).map((s) =>
      count(l.byStatus[s] ?? 0, LABELS[s][1], LABELS[s][2]),
    );
    return `${l.adrs.length} ADR : ${parts.join(', ')}`;
  }
}
