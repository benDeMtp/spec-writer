import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { isProblem } from '../../core/http/problem';
import { JiraApi } from '../../core/jira/jira.api';
import { IssuePage } from '../../core/jira/jira.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { PagerComponent } from '../../shared/ui/pager.component';

export const ISSUES_PAGE_SIZE = 25;

/** Message affiché au-dessus de la page conservée quand Jira ne la rend pas (FR-022 de 002). */
function failure(error: unknown): string {
  if (error instanceof HttpErrorResponse && isProblem(error.error)) {
    return error.error.userMessage;
  }
  return "Jira n'a pas répondu.";
}

/** Tickets du projet Jira courant, les plus récemment modifiés d'abord, par 25 (FR-020 à FR-022 de 002). */
@Component({
  selector: 'sw-jira-issues-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, MatButtonModule, PagerComponent, RouterLink],
  template: `
    <h1>Tickets Jira</h1>
    @if (projectKey(); as key) {
      <p class="lead">Projet Jira {{ key }}, du plus récemment modifié au plus ancien.</p>
      @if (error(); as message) {
        <div class="notice" role="alert">
          <p>{{ message }} La page affichée est conservée.</p>
          <button mat-stroked-button type="button" (click)="load(requested())">Réessayer</button>
        </div>
      }
      @if (page(); as p) {
        @if (p.issues.length) {
          <div class="sheet">
            <table>
              <caption class="sw-visually-hidden">Tickets du projet {{ key }}, {{ position() }}</caption>
              <thead>
                <tr><th scope="col">Ticket</th><th scope="col">Titre</th><th scope="col">Modifié le</th><th scope="col">Spécification</th></tr>
              </thead>
              <tbody>
                @for (i of p.issues; track i.key) {
                  <tr>
                    <td><a [routerLink]="['/jira', i.key]">{{ i.key }}</a></td>
                    <td>{{ i.summary }}</td>
                    <td>{{ i.updated | date: 'd MMM y, HH:mm' }}</td>
                    <td>
                      @if (i.linkedSessionId) {
                        <a [routerLink]="['/sessions', i.linkedSessionId]">Ouvrir la session liée</a>
                      } @else {
                        <a [routerLink]="['/jira', i.key]">Voir et importer</a>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <sw-pager
            label="Pages de tickets"
            [position]="position()"
            [hasPrevious]="p.startAt > 0"
            [hasNext]="p.startAt + p.issues.length < p.total"
            [busy]="busy()"
            (previous)="load(p.startAt - size)"
            (next)="load(p.startAt + size)"
          />
        } @else {
          <p>Aucun ticket dans ce projet.</p>
        }
      } @else if (busy()) {
        <p role="status">Chargement des tickets…</p>
      }
    } @else {
      <p>Aucun projet Jira n'est associé au projet courant.</p>
      <a mat-stroked-button routerLink="/configuration">Ouvrir la configuration</a>
    }
  `,
  styles: `
    .sheet {
      margin-top: 1rem;
      padding: 0.5rem clamp(0.5rem, 2vw, 1.5rem);
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-sheet);
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th,
    td {
      text-align: left;
      padding: 0.5rem;
      border-bottom: 1px solid var(--sw-rule);
    }
    .notice {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem 1rem;
      align-items: center;
      margin: 1rem 0;
      padding: 0.75rem 1rem;
      border-left: 4px solid var(--sw-outcome);
      background: var(--sw-sheet);
    }
    .notice p {
      margin: 0;
      flex: 1 1 20rem;
    }
  `,
})
export class JiraIssuesPage {
  private readonly api = inject(JiraApi);
  private readonly projects = inject(ProjectsStore);

  protected readonly size = ISSUES_PAGE_SIZE;
  protected readonly projectKey = computed(() => this.projects.current()?.effectiveJiraProjectKey ?? null);
  protected readonly page = signal<IssuePage | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  /** Dernière position demandée, pour « Réessayer ». */
  protected readonly requested = signal(0);
  protected readonly position = computed(() => {
    const p = this.page();
    if (!p || !p.issues.length) {
      return '';
    }
    return `${p.startAt + 1} à ${p.startAt + p.issues.length} sur ${p.total}`;
  });

  constructor() {
    effect(() => {
      if (this.projectKey()) {
        // seul le projet relance la liste : la page demandée ne doit pas la ramener au début
        untracked(() => {
          this.page.set(null);
          void this.load(0);
        });
      }
    });
  }

  /** En cas d'erreur, la page affichée reste en place, avec le message (FR-022 de 002). */
  protected async load(startAt: number): Promise<void> {
    const key = this.projectKey();
    if (!key) {
      return;
    }
    this.requested.set(Math.max(0, startAt));
    this.busy.set(true);
    try {
      this.page.set(await this.api.projectIssues(key, this.requested()));
      this.error.set(null);
    } catch (e) {
      this.error.set(failure(e));
    } finally {
      this.busy.set(false);
    }
  }
}
