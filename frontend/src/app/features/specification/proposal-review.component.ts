import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ConversationsApi } from '../../core/conversations/conversations.api';
import { JiraApi } from '../../core/jira/jira.api';
import { Proposal } from '../../core/jira/jira.model';
import { Specification } from '../../core/sessions/sessions.model';
import { ElapsedComponent } from '../../shared/ui/elapsed.component';
import { UnifiedDiffComponent } from '../../shared/ui/unified-diff.component';

/**
 * Relecture d'une proposition de l'IA (FR-044, 011 FR-010) : les différences avec la version de
 * départ, puis « Appliquer », qui crée la version suivante, ou « Abandonner », qui ne crée rien.
 * Sert à la proposition d'une discussion comme à la fusion d'un conflit Jira.
 */
@Component({
  selector: 'sw-proposal-review',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ElapsedComponent, MatButtonModule, UnifiedDiffComponent],
  template: `
    <section class="review" aria-labelledby="proposal-title">
      <h2 id="proposal-title" tabindex="-1">{{ title() }}</h2>
      <p>
        Proposition à partir de la version {{ proposal().baseVersion }} : « {{
          proposal().proposed.title
        }} ». Relisez les différences avant de l'appliquer ; elle deviendra la version
        {{ proposal().baseVersion + 1 }}.
      </p>
      <sw-unified-diff [diff]="proposal().unifiedDiff" />
      <div class="actions">
        <button mat-flat-button type="button" [disabled]="busy()" (click)="apply()">
          Appliquer
        </button>
        <button mat-button type="button" [disabled]="busy()" (click)="discard()">
          Abandonner
        </button>
        <sw-elapsed class="hint" [running]="busy()" />
      </div>
    </section>
  `,
  styles: `
    .review {
      margin-top: 1rem;
      padding: 1rem 1.25rem;
      border: 1px solid var(--sw-rule);
      border-left: 4px solid var(--sw-pencil);
      border-radius: var(--sw-radius-sheet);
      background: var(--sw-sheet);
    }
    h2 {
      margin: 0 0 0.5rem;
      font-size: var(--sw-step-1);
      font-weight: 650;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
      margin-top: 0.75rem;
    }
  `,
})
export class ProposalReviewComponent {
  private readonly jira = inject(JiraApi);
  private readonly conversations = inject(ConversationsApi);

  readonly sessionId = input.required<string>();
  readonly proposal = input.required<Proposal>();
  readonly title = input('Proposition de l’IA');
  readonly applied = output<Specification>();
  readonly discarded = output<void>();

  protected readonly busy = signal(false);

  protected async apply(): Promise<void> {
    this.busy.set(true);
    try {
      this.applied.emit(await this.jira.acceptEdit(this.sessionId(), this.proposal().proposalId));
    } catch {
      // le refus (verrou, proposition expirée) est annoncé par le bandeau d'erreur
    } finally {
      this.busy.set(false);
    }
  }

  protected async discard(): Promise<void> {
    this.busy.set(true);
    try {
      await this.conversations.discardProposal(this.sessionId(), this.proposal().proposalId);
    } catch {
      // déjà expirée ou prise : rien à abandonner
    } finally {
      this.busy.set(false);
      this.discarded.emit();
    }
  }
}
