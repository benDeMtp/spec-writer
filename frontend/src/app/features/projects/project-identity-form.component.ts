import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Project, ProjectInput } from '../../core/projects/projects.model';

interface Draft {
  name: string;
  description: string;
  jiraProjectKey: string;
}

function draftOf(p: Project | null): Draft {
  return { name: p?.name ?? '', description: p?.description ?? '', jiraProjectKey: p?.jiraProjectKey ?? '' };
}

/**
 * Nom, description et clé Jira d'un projet (FR-006 de 012) : création dans la page Projets,
 * modification dans les réglages du projet.
 */
@Component({
  selector: 'sw-project-identity-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <form class="edit" (ngSubmit)="submit()" [attr.aria-label]="label()">
      <mat-form-field appearance="outline">
        <mat-label>Nom du projet</mat-label>
        <input matInput name="name" maxlength="80" required [ngModel]="draft().name" (ngModelChange)="patch({ name: $event })" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Description (facultative)</mat-label>
        <textarea matInput name="description" maxlength="1000" rows="2" [ngModel]="draft().description" (ngModelChange)="patch({ description: $event })"></textarea>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>Clé Jira (facultative)</mat-label>
        <input
          matInput
          [id]="keyId()"
          name="jiraProjectKey"
          maxlength="10"
          pattern="[A-Z][A-Z0-9_]{1,9}"
          [ngModel]="draft().jiraProjectKey"
          (ngModelChange)="patch({ jiraProjectKey: $event })"
          [attr.aria-describedby]="keyId() + '-hint'"
        />
        <mat-hint [id]="keyId() + '-hint'">{{ keyHint() }}</mat-hint>
      </mat-form-field>
      <div class="actions">
        <button mat-flat-button type="submit" [disabled]="!draft().name.trim() || !dirty() || busy()">Enregistrer</button>
        @if (cancellable()) {
          <button mat-button type="button" (click)="cancelled.emit()">Annuler</button>
        } @else if (dirty()) {
          <button mat-button type="button" (click)="reset()">Annuler les modifications</button>
        }
      </div>
    </form>
  `,
  styles: `
    .edit {
      display: grid;
      gap: 0.5rem;
      margin: 0.5rem 0;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
  `,
})
export class ProjectIdentityFormComponent {
  /** Projet modifié ; null pour une création. */
  readonly project = input<Project | null>(null);
  /** Clé Jira par défaut de l'application, rappelée dans l'aide. */
  readonly defaultKey = input<string | null>(null);
  readonly keyId = input('jira-key');
  readonly busy = input(false);
  /** Création : « Annuler » ferme le formulaire ; modification : il rétablit les valeurs. */
  readonly cancellable = input(false);
  readonly saved = output<ProjectInput>();
  readonly cancelled = output<void>();
  readonly dirtyChange = output<boolean>();

  protected readonly draft = signal<Draft>(draftOf(null));
  protected readonly label = computed(() => {
    const p = this.project();
    return p ? 'Identité du projet ' + p.name : 'Nouveau projet';
  });
  readonly dirty = computed(() => {
    const d = this.draft();
    const o = draftOf(this.project());
    return d.name.trim() !== o.name || d.description.trim() !== o.description || d.jiraProjectKey.trim() !== o.jiraProjectKey;
  });
  protected readonly keyHint = computed(() => {
    const fallback = this.defaultKey();
    return fallback
      ? `Par exemple FACT. Sans clé, la clé par défaut de l'application est utilisée : ${fallback}.`
      : 'Par exemple FACT. Sans clé, aucun projet Jira n’est associé.';
  });

  constructor() {
    effect(() => {
      const p = this.project();
      untracked(() => this.draft.set(draftOf(p)));
    });
    effect(() => this.dirtyChange.emit(this.dirty()));
  }

  protected patch(change: Partial<Draft>): void {
    this.draft.update((d) => ({ ...d, ...change }));
  }

  protected reset(): void {
    this.draft.set(draftOf(this.project()));
  }

  protected submit(): void {
    const d = this.draft();
    if (!d.name.trim()) {
      return;
    }
    this.saved.emit({
      name: d.name.trim(),
      description: d.description.trim() || null,
      jiraProjectKey: d.jiraProjectKey.trim() || null,
    });
  }
}
