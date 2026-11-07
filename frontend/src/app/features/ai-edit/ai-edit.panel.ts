import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { JiraApi } from '../../core/jira/jira.api';
import { Proposal } from '../../core/jira/jira.model';
import { Specification } from '../../core/sessions/sessions.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { MentionStatusComponent } from '../../shared/ui/mention-status.component';
import { MentionDirective } from '../../shared/ui/mention.directive';
import { UnifiedDiffComponent } from '../../shared/ui/unified-diff.component';

export const MAX_INSTRUCTION = 2000;

/**
 * Modification demandée à l'IA en langage naturel (FR-044) : la proposition et ses différences
 * s'affichent, et rien n'est appliqué sans « Appliquer » (US6-4). Une fusion de conflit arrive
 * ici déjà proposée.
 */
@Component({
  selector: 'sw-ai-edit-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MentionDirective, MentionStatusComponent, UnifiedDiffComponent],
  template: `
    <section class="panel" aria-labelledby="ai-edit-title">
      <h2 id="ai-edit-title">{{ proposal() ? 'Proposition de l’IA' : 'Faire évoluer avec l’IA' }}</h2>
      @if (proposal(); as p) {
        <p>
          Proposition à partir de la version {{ p.baseVersion }} : « {{ p.proposed.title }} ». Relisez les
          différences avant de l'appliquer ; elle deviendra la version {{ p.baseVersion + 1 }}.
        </p>
        <sw-unified-diff [diff]="p.unifiedDiff" />
        <div class="actions">
          <button mat-flat-button type="button" [disabled]="busy()" (click)="apply(p)">Appliquer</button>
          <button mat-button type="button" [disabled]="busy()" (click)="discard()">Abandonner</button>
        </div>
      } @else {
        <form (ngSubmit)="propose()">
          <mat-form-field appearance="outline" class="field">
            <mat-label>Ce que l'IA doit changer</mat-label>
            <textarea
              matInput
              name="instruction"
              rows="4"
              [maxlength]="max"
              [swMention]="projectId()"
              [(ngModel)]="instruction"
              placeholder="Par exemple : ajoute un cas d'erreur quand la salle est déjà réservée."
            ></textarea>
            <mat-hint align="end">{{ instruction().length }} / {{ max }}</mat-hint>
          </mat-form-field>
          <sw-mention-status
            [projectId]="projectId()"
            [sessionId]="sessionId()"
            field="INSTRUCTION"
            [text]="instruction()"
            (replaced)="instruction.set($event)"
          />
          @if (empty()) {
            <p class="error" role="alert">Décrivez la modification souhaitée.</p>
          }
          <div class="actions">
            <button mat-flat-button type="submit" [disabled]="busy()">Proposer une modification</button>
            <button mat-button type="button" [disabled]="busy()" (click)="closed.emit()">Fermer</button>
          </div>
        </form>
      }
      <p role="status" aria-live="polite" class="announce">{{ status() }}</p>
    </section>
  `,
  styles: `
    .panel {
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
    .field {
      width: 100%;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin-top: 0.75rem;
    }
    .error {
      margin: 0;
      color: var(--mat-sys-error);
    }
    .announce:empty {
      display: none;
    }
  `,
})
export class AiEditPanelComponent {
  private readonly api = inject(JiraApi);
  private readonly consent = inject(ConsentGate);

  readonly sessionId = input.required<string>();
  /** Projet de la session : ses documents sont mentionnables avec @ dans l'instruction (007). */
  readonly projectId = input<string | null>(null);
  /** Proposition déjà calculée (fusion d'un conflit Jira). */
  readonly initialProposal = input<Proposal | null>(null);
  readonly applied = output<Specification>();
  readonly closed = output<void>();

  protected readonly max = MAX_INSTRUCTION;
  protected readonly instruction = signal('');
  protected readonly proposal = signal<Proposal | null>(null);
  protected readonly busy = signal(false);
  protected readonly empty = signal(false);
  protected readonly status = signal('');

  constructor() {
    effect(() => {
      const initial = this.initialProposal();
      if (initial) {
        this.proposal.set(initial);
      }
    });
  }

  protected async propose(): Promise<void> {
    const text = this.instruction().trim();
    this.empty.set(!text);
    if (!text || this.busy() || !(await this.consent.ensure('COPILOT'))) {
      return;
    }
    this.busy.set(true);
    this.status.set("L'IA prépare une proposition…");
    try {
      this.proposal.set(await this.api.proposeEdit(this.sessionId(), text));
      this.status.set('Proposition prête : relisez les différences.');
    } catch {
      this.status.set('');
    } finally {
      this.busy.set(false);
    }
  }

  protected async apply(p: Proposal): Promise<void> {
    this.busy.set(true);
    try {
      const version = await this.api.acceptEdit(this.sessionId(), p.proposalId);
      this.proposal.set(null);
      this.instruction.set('');
      this.status.set(`Version ${version.number} créée.`);
      this.applied.emit(version);
    } catch {
      this.status.set('');
    } finally {
      this.busy.set(false);
    }
  }

  protected discard(): void {
    this.proposal.set(null);
    this.status.set('Proposition abandonnée : la spécification est inchangée.');
  }
}
