import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink } from '@angular/router';
import { MarkdownComponent } from 'ngx-markdown';
import { JiraApi } from '../../core/jira/jira.api';
import { JiraIssueView } from '../../core/jira/jira.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';

/**
 * Ticket Jira non lié (FR-043) : sa description complète, et une seule action. « Faire évoluer
 * avec l'IA » importe le ticket en session liée, sans écrire dans Jira, puis ouvre la modification
 * assistée sur la spécification créée (US6-5).
 */
@Component({
  selector: 'sw-item-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, MarkdownComponent, MatButtonModule, RouterLink],
  template: `
    @if (issue(); as i) {
      <header class="head">
        <p class="key">{{ i.key }}</p>
        <h1>{{ i.summary }}</h1>
        <p class="meta">
          Modifié dans Jira le <time [attr.datetime]="i.updated">{{ i.updated | date: 'd MMMM y, HH:mm' }}</time>.
          <a [href]="i.url" target="_blank" rel="noopener noreferrer">Ouvrir dans Jira<span class="sw-visually-hidden"> (nouvel onglet)</span></a>
        </p>
        <div class="actions">
          <button mat-flat-button type="button" [disabled]="importing()" (click)="evolve()">Faire évoluer avec l'IA</button>
          <a mat-button routerLink="/sessions">Retour aux spécifications</a>
        </div>
        <p role="status" aria-live="polite" class="announce">{{ status() }}</p>
      </header>
      <article class="sheet document" aria-label="Description du ticket">
        @if (i.descriptionMarkdown.trim()) {
          <markdown [data]="i.descriptionMarkdown" />
        } @else {
          <p>Ce ticket n'a pas de description.</p>
        }
      </article>
    } @else if (failed()) {
      <p>Ce ticket n'a pas pu être lu. <a routerLink="/sessions">Retour aux spécifications</a></p>
    } @else {
      <p role="status" aria-live="polite">Chargement du ticket…</p>
    }
  `,
  styles: `
    .key {
      margin: 0;
      color: var(--sw-role);
      font-weight: 600;
    }
    .head h1 {
      margin: 0.15rem 0 0;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-3);
      font-weight: 600;
    }
    .meta {
      margin: 0.25rem 0 0.75rem;
      color: var(--sw-ink-soft);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .announce:empty {
      display: none;
    }
    .sheet {
      margin-top: 1rem;
      max-width: calc(var(--sw-measure) + 5rem);
      padding: 1.5rem clamp(1rem, 3vw, 2.5rem);
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-sheet);
      font-family: var(--sw-font-doc);
      line-height: 1.65;
      overflow-wrap: anywhere;
    }
    .sheet :is(pre, code) {
      white-space: pre-wrap;
    }
  `,
})
export class ItemDetailPage {
  private readonly api = inject(JiraApi);
  private readonly router = inject(Router);
  private readonly consent = inject(ConsentGate);

  readonly key = input.required<string>();

  protected readonly issue = signal<JiraIssueView | null>(null);
  protected readonly failed = signal(false);
  protected readonly importing = signal(false);
  protected readonly status = signal('');

  constructor() {
    effect(() => void this.load(this.key()));
  }

  private async load(key: string): Promise<void> {
    this.issue.set(null);
    this.failed.set(false);
    try {
      this.issue.set(await this.api.issue(key));
    } catch {
      this.failed.set(true);
    }
  }

  protected async evolve(): Promise<void> {
    if (!(await this.consent.ensure('JIRA', 'COPILOT'))) {
      return;
    }
    this.importing.set(true);
    this.status.set("Import du ticket et structuration par l'IA…");
    try {
      const session = await this.api.importIssue(this.key());
      await this.router.navigate(['/sessions', session.id, 'versions', String(session.latestVersion ?? 1)], {
        queryParams: { edit: 1 },
        state: { warnings: session.warnings },
      });
    } catch {
      this.status.set('');
    } finally {
      this.importing.set(false);
    }
  }
}
