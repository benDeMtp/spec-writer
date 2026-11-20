import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, output, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { KnowledgeApi } from '../../core/projects/knowledge.api';
import { Indexation, Project } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { IndexationProgressComponent } from '../project-knowledge/indexation-progress.component';
import { KNOWLEDGE_POLL_MS } from '../project-knowledge/knowledge-panel.component';

/**
 * Étape « Indexation du projet » de l'assistant (018, R4) : suit l'indexation déjà lancée par
 * l'ajout d'une source, sinon en lance une incrémentale, sinon explique qu'il n'y a rien à indexer.
 * La conversation s'ouvre sans attendre la fin (FR-007).
 */
@Component({
  selector: 'sw-indexation-step',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IndexationProgressComponent, MatButtonModule, RouterLink],
  template: `
    @if (nothing()) {
      <p data-testid="nothing-to-index">
        Le projet n'a ni clé Jira ni source de documentation : l'agent ne connaîtra pas le projet. Vous pourrez en
        ajouter plus tard dans les <a routerLink="/projet/reglages">réglages du projet</a>.
      </p>
    } @else if (indexation(); as g) {
      <p>L'agent s'appuie sur ce qui est indexé. Vous pouvez ouvrir la conversation sans attendre la fin.</p>
      <sw-indexation-progress [indexation]="g" [busy]="busy()" (interrupt)="cancel()" />
    } @else {
      <p role="status" aria-live="polite">Préparation de l'indexation…</p>
    }
    <div class="actions">
      <button mat-stroked-button type="button" (click)="openConversation.emit()">Ouvrir la conversation</button>
    </div>
  `,
  styles: `
    .actions {
      margin-top: 1rem;
    }
  `,
})
export class IndexationStepComponent {
  readonly openConversation = output<void>();

  private readonly store = inject(ProjectsStore);
  private readonly api = inject(KnowledgeApi);
  private readonly pollMs = inject(KNOWLEDGE_POLL_MS);
  protected readonly indexation = signal<Indexation | null>(null);
  protected readonly nothing = signal(false);
  protected readonly busy = signal(false);
  private timer: ReturnType<typeof setTimeout> | null = null;
  private started = false;

  constructor() {
    effect(() => {
      const p = this.store.current();
      if (p) {
        untracked(() => void this.begin(p));
      }
    });
    inject(DestroyRef).onDestroy(() => this.stop());
  }

  private async begin(p: Project): Promise<void> {
    if (this.started) {
      return;
    }
    this.started = true;
    try {
      const current = await this.api.current(p.id);
      if (current?.status === 'RUNNING') {
        this.follow(p.id, current);
      } else if (p.effectiveJiraProjectKey || p.docSources.length > 0 || p.architectureWorkspace) {
        this.follow(p.id, await this.api.start(p.id, 'INCREMENTAL'));
      } else {
        this.nothing.set(true);
      }
    } catch {
      // le bandeau d'erreur affiche la cause ; la conversation reste accessible
    }
  }

  protected async cancel(): Promise<void> {
    const p = this.store.current();
    if (!p) {
      return;
    }
    this.busy.set(true);
    try {
      await this.api.cancel(p.id);
      this.follow(p.id, null, 0);
    } catch {
      // le bandeau d'erreur affiche la cause
    } finally {
      this.busy.set(false);
    }
  }

  private follow(projectId: string, g: Indexation | null, delay = this.pollMs): void {
    if (g) {
      this.indexation.set(g);
      if (g.status !== 'RUNNING') {
        return;
      }
    }
    this.stop();
    this.timer = setTimeout(async () => {
      try {
        const next = await this.api.current(projectId);
        if (next) {
          this.follow(projectId, next);
        }
      } catch {
        // le bandeau d'erreur affiche la cause
      }
    }, delay);
  }

  private stop(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
