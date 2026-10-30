import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Provenance, SearchItem } from '../../core/search/search.model';

const PROVENANCE: Record<Provenance, string> = {
  SPEC_WRITER: 'spec-writer',
  JIRA: 'Jira',
  BOTH: 'les deux',
};

/** Lien d'un résultat : la dernière version d'une session, le questionnaire, ou le détail du ticket Jira. */
export function itemLink(item: SearchItem): string[] {
  if (item.sessionId) {
    return item.latestVersion
      ? ['/sessions', item.sessionId, 'versions', String(item.latestVersion)]
      : ['/sessions', item.sessionId];
  }
  return ['/jira', item.id];
}

/** Liste unique des résultats (FR-041, FR-042) : identifiant, titre, provenance et date. */
@Component({
  selector: 'sw-results-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, MatTableModule, RouterLink],
  template: `
    <table mat-table [dataSource]="items()" class="results">
      <caption class="sw-visually-hidden">{{ caption() }}</caption>
      <ng-container matColumnDef="id">
        <th mat-header-cell *matHeaderCellDef scope="col">Identifiant</th>
        <td mat-cell *matCellDef="let item" class="id">{{ identifier(item) }}</td>
      </ng-container>
      <ng-container matColumnDef="title">
        <th mat-header-cell *matHeaderCellDef scope="col">Titre</th>
        <td mat-cell *matCellDef="let item">
          <a [routerLink]="link(item)" class="title">{{ item.title }}</a>
          @if (item.excerpt) {
            <span class="excerpt">{{ item.excerpt }}</span>
          }
        </td>
      </ng-container>
      <ng-container matColumnDef="provenance">
        <th mat-header-cell *matHeaderCellDef scope="col">Provenance</th>
        <td mat-cell *matCellDef="let item">
          <span [class]="'provenance ' + item.provenance">{{ provenance(item) }}</span>
        </td>
      </ng-container>
      <ng-container matColumnDef="updatedAt">
        <th mat-header-cell *matHeaderCellDef scope="col">Modifié le</th>
        <td mat-cell *matCellDef="let item" class="date">
          <time [attr.datetime]="item.updatedAt">{{ item.updatedAt | date: 'd MMM y, HH:mm' }}</time>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="columns"></tr>
      <tr mat-row *matRowDef="let item; columns: columns"></tr>
    </table>
  `,
  styles: `
    .results {
      width: 100%;
      background: transparent;
    }
    .id {
      white-space: nowrap;
      color: var(--sw-ink-soft);
      font-variant-numeric: tabular-nums;
    }
    .title {
      color: var(--sw-ink);
      font-weight: 600;
      text-underline-offset: 0.2em;
    }
    .excerpt {
      display: block;
      margin-top: 0.15rem;
      color: var(--sw-ink-soft);
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step--1);
    }
    .provenance {
      display: inline-block;
      padding: 0.1rem 0.55rem;
      border: 1px solid currentColor;
      border-radius: 999px;
      font-size: var(--sw-step--1);
      white-space: nowrap;
    }
    .SPEC_WRITER {
      color: var(--sw-pencil);
    }
    .JIRA {
      color: var(--sw-role);
    }
    .BOTH {
      color: var(--sw-ink);
      border-style: double;
      border-width: 3px;
    }
    .date {
      white-space: nowrap;
    }
    @media (max-width: 40rem) {
      .id,
      .mat-column-id,
      .date,
      .mat-column-updatedAt {
        display: none;
      }
    }
  `,
})
export class ResultsTableComponent {
  readonly items = input.required<SearchItem[]>();
  readonly caption = input('Résultats de la recherche');

  protected readonly columns = ['id', 'title', 'provenance', 'updatedAt'];

  protected provenance(item: SearchItem): string {
    return PROVENANCE[item.provenance];
  }

  /** Clé Jira quand l'élément en a une, sinon les premiers caractères de l'identifiant de session. */
  protected identifier(item: SearchItem): string {
    return item.jiraKey ?? item.id.slice(0, 8);
  }

  protected link(item: SearchItem): string[] {
    return itemLink(item);
  }
}
