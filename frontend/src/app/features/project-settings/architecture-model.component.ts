import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { KnowledgeApi } from '../../core/projects/knowledge.api';
import { Project } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';

/**
 * Modèle d'architecture d'un projet (002) : chemin du workspace.dsl Structurizr et ownership.
 * Partagé par les réglages du projet et l'assistant de premier lancement (018, R3).
 */
@Component({
  selector: 'sw-architecture-model',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <p role="status" aria-live="polite" class="announce">{{ announce() }}</p>
    @if (project(); as p) {
      <p class="hint">
        Le fichier workspace.dsl Structurizr du projet, lu avec le modèle qu'il étend et les fichiers qu'il inclut. Les
        blocs !script sont ignorés, avec un avertissement.
      </p>
      <form class="add" (ngSubmit)="saveWorkspace()" aria-label="Modèle d'architecture">
        <mat-form-field appearance="outline" class="grow">
          <mat-label>Chemin du fichier workspace.dsl</mat-label>
          <input matInput name="workspace" maxlength="2000" autocomplete="off" spellcheck="false" [ngModel]="workspace()" (ngModelChange)="workspace.set($event)" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Ownership du projet (facultatif)</mat-label>
          <input
            matInput
            name="ownership"
            maxlength="80"
            aria-describedby="ownership-hint"
            [ngModel]="ownership()"
            (ngModelChange)="ownership.set($event)"
          />
        </mat-form-field>
        <button mat-stroked-button type="submit" [disabled]="!workspace().trim() || busy()">Enregistrer</button>
        @if (p.architectureWorkspace) {
          <button
            mat-button
            type="button"
            (click)="clearWorkspace()"
            [disabled]="busy()"
            aria-label="Retirer le modèle d'architecture"
          >
            Retirer
          </button>
        }
      </form>
      <p class="hint" id="ownership-hint">
        Ownership, facultatif : les éléments dont la propriété ownership vaut cette valeur sont propres au projet. Sans
        valeur, les éléments du modèle étendu sont communs et les autres sont propres au projet.
      </p>
    }
  `,
  styles: `
    .announce:empty {
      display: none;
    }
    .hint {
      margin: 0.25rem 0 0.75rem;
      color: var(--sw-ink-soft);
    }
    .add {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.5rem;
    }
    .grow {
      flex: 1 1 18rem;
    }
  `,
})
export class ArchitectureModelComponent {
  private readonly store = inject(ProjectsStore);
  private readonly knowledge = inject(KnowledgeApi);

  readonly project = input.required<Project>();

  protected readonly workspace = signal('');
  protected readonly ownership = signal('');
  protected readonly busy = signal(false);
  protected readonly announce = signal('');

  constructor() {
    effect(() => {
      const p = this.project();
      untracked(() => {
        this.workspace.set(p.architectureWorkspace ?? '');
        this.ownership.set(p.architectureOwnership ?? '');
      });
    });
  }

  /** Un chemin ou un ownership différent de celui enregistré serait perdu en quittant. */
  dirty(): boolean {
    const p = this.project();
    return this.workspace().trim() !== (p.architectureWorkspace ?? '') || this.ownership().trim() !== (p.architectureOwnership ?? '');
  }

  protected async saveWorkspace(): Promise<void> {
    const workspace = this.workspace().trim();
    if (!workspace) {
      return;
    }
    await this.run(async () => {
      await this.knowledge.setArchitecture(this.project().id, workspace, this.ownership().trim() || null);
      await this.store.load();
      this.announce.set("Modèle d'architecture enregistré. Il est lu en arrière-plan.");
    });
  }

  protected async clearWorkspace(): Promise<void> {
    await this.run(async () => {
      await this.knowledge.clearArchitecture(this.project().id);
      this.announce.set("Modèle d'architecture retiré.");
      await this.store.load();
    });
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
    } catch {
      // Le bandeau affiche la cause (chemin invalide, projet introuvable).
      this.announce.set('');
    } finally {
      this.busy.set(false);
    }
  }
}
