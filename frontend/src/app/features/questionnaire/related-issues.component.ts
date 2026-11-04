import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { DecisionOutcome, SessionsApi } from '../../core/sessions/sessions.api';
import { IssueComparison, RelatedDecision, RelatedIssue, RelatedIssues } from '../../core/sessions/sessions.model';

/** Choix fait parmi les tickets proches, et sa suite renvoyée par le serveur. */
export interface RelatedChoice {
  decision: RelatedDecision;
  key: string | null;
  outcome: DecisionOutcome;
}

/**
 * Tickets proches du besoin, montrés avant les questions (FR-034, FR-035 de 002) : un ticket fermé
 * se prolonge par une nouvelle story liée ; un ticket ouvert se compare au besoin, puis l'utilisateur
 * dit si c'est la même story.
 */
@Component({
  selector: 'sw-related-issues',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, MatButtonModule],
  template: `
    <section class="related" aria-labelledby="related-title">
      <h2 id="related-title" tabindex="-1">Tickets proches</h2>
      <p class="hint">
        Ces tickets du projet semblent traiter du même problème. Choisissez-en un, ou continuez sans.
      </p>
      @if (related().status === 'PARTIAL') {
        <p class="hint">L'indexation du projet est en cours : résultats partiels.</p>
      }
      <ul>
        @for (issue of related().issues; track issue.key) {
          <li>
            <p class="title">
              @if (issue.url) {
                <a [href]="issue.url" target="_blank" rel="noopener noreferrer"
                  >{{ issue.key }}<span class="sw-visually-hidden"> (nouvel onglet)</span></a
                >
              } @else {
                <span class="key">{{ issue.key }}</span>
              }
              {{ issue.title }}
            </p>
            <p class="status">
              <span class="badge" [class.closed]="issue.closed">{{ issue.status }}</span>
              @if (!issue.statusFresh) {
                statut indexé
                @if (issue.updatedAt) {
                  le {{ issue.updatedAt | date: 'd MMM y, HH:mm' }}
                }
                (Jira n'a pas pu être relu)
              }
            </p>
            @if (issue.reason) {
              <p class="reason">{{ issue.reason }}</p>
            }
            <div class="actions">
              @if (issue.closed) {
                <button mat-stroked-button type="button" [disabled]="busy()" (click)="decide('LINK_NEW', issue)"
                  [attr.aria-label]="'Lier une nouvelle story à ' + issue.key">
                  Lier une nouvelle story
                </button>
              } @else {
                <button mat-stroked-button type="button" [disabled]="busy()" (click)="compare(issue)"
                  [attr.aria-label]="'Comparer le besoin à ' + issue.key">
                  Comparer
                </button>
              }
            </div>
            @if (comparison()?.key === issue.key) {
              @let c = comparison()!;
              <div class="comparison" role="group" [attr.aria-label]="'Comparaison avec ' + issue.key">
                <div>
                  <h3>En commun</h3>
                  <ul>@for (x of c.common; track $index) {<li>{{ x }}</li>} @empty {<li>Rien.</li>}</ul>
                </div>
                <div>
                  <h3>Différent</h3>
                  <ul>@for (x of c.different; track $index) {<li>{{ x }}</li>} @empty {<li>Rien.</li>}</ul>
                </div>
              </div>
              <p class="hint">
                Suggestion : {{ c.suggestion === 'SAME' ? 'la même story' : 'une story différente' }}.
              </p>
              <div class="actions">
                <button mat-flat-button type="button" [disabled]="busy()" (click)="decide('SAME', issue)">
                  C'est la même
                </button>
                <button mat-stroked-button type="button" [disabled]="busy()" (click)="decide('DIFFERENT', issue)">
                  C'est différent
                </button>
              </div>
            }
          </li>
        }
      </ul>
      <button mat-button type="button" [disabled]="busy()" (click)="decide('NONE', null)">Aucun ne correspond</button>
      @if (busy()) {
        <p class="hint" role="status" aria-live="polite">{{ busyMessage() }}</p>
      }
    </section>
  `,
  styles: `
    .related {
      margin-top: 1.25rem;
    }
    h2 {
      margin: 0 0 0.5rem;
      font-size: var(--sw-step-1);
      outline: none;
    }
    h3 {
      margin: 0 0 0.25rem;
      font-size: var(--sw-step-0);
      font-weight: 600;
    }
    ul {
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .related > ul > li {
      margin-bottom: 1rem;
      padding: 0.75rem 1rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
    }
    .title {
      margin: 0;
      font-weight: 650;
    }
    .status,
    .reason {
      margin: 0.25rem 0 0;
    }
    .reason {
      font-family: var(--sw-font-doc);
    }
    .hint,
    .status {
      color: var(--sw-ink-soft);
    }
    .badge {
      padding: 0 0.5rem;
      border: 1px solid var(--sw-pencil);
      border-radius: 999px;
      color: var(--sw-pencil);
      font-size: var(--sw-step--1);
    }
    .badge.closed {
      border-color: var(--sw-outcome);
      color: var(--sw-outcome);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin-top: 0.5rem;
    }
    .comparison {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
      gap: 1rem;
      margin-top: 0.75rem;
    }
    .comparison ul {
      padding-left: 1.25rem;
      list-style: disc;
    }
  `,
})
export class RelatedIssuesComponent {
  private readonly api = inject(SessionsApi);

  readonly sessionId = input.required<string>();
  readonly related = input.required<RelatedIssues>();
  readonly decided = output<RelatedChoice>();

  protected readonly comparison = signal<IssueComparison | null>(null);
  protected readonly busy = signal(false);
  protected readonly busyMessage = signal('');

  protected async compare(issue: RelatedIssue): Promise<void> {
    this.busy.set(true);
    this.busyMessage.set(`GitHub Copilot compare le besoin et ${issue.key}…`);
    try {
      this.comparison.set(await this.api.compare(this.sessionId(), issue.key));
    } catch {
      // le bandeau d'erreur affiche la cause ; le choix reste possible
    } finally {
      this.busy.set(false);
    }
  }

  protected async decide(decision: RelatedDecision, issue: RelatedIssue | null): Promise<void> {
    this.busy.set(true);
    this.busyMessage.set(
      decision === 'SAME'
        ? `Import de ${issue?.key} et proposition de modification…`
        : 'GitHub Copilot prépare les premières questions…',
    );
    try {
      const outcome = await this.api.decide(this.sessionId(), decision, issue?.key);
      this.decided.emit({ decision, key: issue?.key ?? null, outcome });
    } catch {
      // le bandeau d'erreur affiche la cause ; les tickets restent proposés
    } finally {
      this.busy.set(false);
    }
  }
}
