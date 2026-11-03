import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { firstValueFrom } from 'rxjs';
import { ProjectsApi } from '../../core/projects/projects.api';
import { Project, ProjectInput } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { DeleteProjectChoice, DeleteProjectDialogComponent } from './delete-project-dialog.component';

interface Draft {
  name: string;
  description: string;
  jiraProjectKey: string;
}

const EMPTY: Draft = { name: '', description: '', jiraProjectKey: '' };

/** Projets : créer, modifier, supprimer, choisir le projet courant (US1, FR-001 à FR-007). */
@Component({
  selector: 'sw-projects-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, NgTemplateOutlet],
  template: `
    <h1>Projets</h1>
    <div class="sheet">
      <p role="status" aria-live="polite" class="announce">{{ announce() }}</p>
      @if (warning(); as w) {
        <p class="warning" role="alert">{{ w }}</p>
      }
      <ul class="projects">
        @for (p of store.projects(); track p.id) {
          <li>
            @if (editing() === p.id) {
              <ng-container *ngTemplateOutlet="form; context: { $implicit: p }" />
            } @else {
              <div class="head">
                <span class="name">{{ p.name }}</span>
                @if (p.id === store.currentId()) {
                  <span class="current">Projet courant</span>
                }
              </div>
              @if (p.description) {
                <p class="description">{{ p.description }}</p>
              }
              <p class="meta">{{ meta(p) }}</p>
              <div class="actions">
                @if (p.id !== store.currentId()) {
                  <button mat-stroked-button type="button" (click)="select(p)">Rendre courant</button>
                }
                <button mat-button type="button" (click)="startEdit(p)" [attr.aria-label]="'Modifier le projet ' + p.name">
                  Modifier
                </button>
                <button
                  mat-button
                  type="button"
                  (click)="remove(p)"
                  [disabled]="store.projects().length < 2"
                  [attr.aria-label]="'Supprimer le projet ' + p.name"
                >
                  Supprimer
                </button>
              </div>
            }
          </li>
        }
      </ul>
      @if (store.projects().length < 2) {
        <p class="hint">Le dernier projet restant ne peut pas être supprimé.</p>
      }

      @if (editing() === 'new') {
        <ng-container *ngTemplateOutlet="form; context: { $implicit: null }" />
      } @else {
        <button mat-flat-button type="button" (click)="startCreate()">Nouveau projet</button>
      }
    </div>

    <ng-template #form let-project>
      <form class="edit" (ngSubmit)="save(project)" [attr.aria-label]="project ? 'Modifier ' + project.name : 'Nouveau projet'">
        <mat-form-field appearance="outline">
          <mat-label>Nom du projet</mat-label>
          <input matInput name="name" maxlength="80" required [ngModel]="draft().name" (ngModelChange)="patch({ name: $event })" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Description (facultative)</mat-label>
          <textarea matInput name="description" maxlength="1000" rows="2" [ngModel]="draft().description" (ngModelChange)="patch({ description: $event })"></textarea>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Clé du projet Jira (facultative)</mat-label>
          <input
            matInput
            name="jiraProjectKey"
            maxlength="10"
            pattern="[A-Z][A-Z0-9_]{1,9}"
            [ngModel]="draft().jiraProjectKey"
            (ngModelChange)="patch({ jiraProjectKey: $event })"
            aria-describedby="jira-key-hint"
          />
          <mat-hint id="jira-key-hint">Par exemple FACT. Sans clé, le projet Jira par défaut de la configuration est utilisé.</mat-hint>
        </mat-form-field>
        <div class="actions">
          <button mat-flat-button type="submit" [disabled]="!draft().name.trim()">Enregistrer</button>
          <button mat-button type="button" (click)="editing.set(null)">Annuler</button>
        </div>
      </form>
    </ng-template>
  `,
  styles: `
    h1 {
      margin: 0 0 1.5rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-3);
      font-weight: 600;
    }
    .sheet {
      max-width: var(--sw-measure);
      padding: 1.5rem clamp(1rem, 3vw, 2rem);
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-sheet);
    }
    .announce:empty {
      display: none;
    }
    .warning {
      padding: 0.75rem 1rem;
      border-left: 4px solid var(--sw-outcome);
      background: color-mix(in srgb, var(--sw-outcome) 8%, transparent);
      border-radius: var(--sw-radius-field);
    }
    .projects {
      list-style: none;
      margin: 0 0 1rem;
      padding: 0;
    }
    li {
      padding: 1rem 0;
      border-top: 1px solid var(--sw-rule);
    }
    li:first-child {
      border-top: none;
      padding-top: 0;
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.75rem;
    }
    .name {
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-1);
      font-weight: 600;
    }
    .current {
      color: var(--sw-pencil);
      font-weight: 650;
    }
    .description,
    .meta,
    .hint {
      margin: 0.25rem 0 0.5rem;
      color: var(--sw-ink-soft);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .edit {
      display: grid;
      gap: 0.5rem;
      margin: 0.5rem 0;
    }
  `,
})
export class ProjectsPage implements OnInit {
  protected readonly store = inject(ProjectsStore);
  private readonly api = inject(ProjectsApi);
  private readonly dialog = inject(MatDialog);

  protected readonly editing = signal<string | null>(null);
  protected readonly draft = signal<Draft>({ ...EMPTY });
  protected readonly announce = signal('');
  protected readonly warning = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    await this.store.load();
  }

  protected meta(p: Project): string {
    const specs = `${p.sessionCount} ${p.sessionCount > 1 ? 'spécifications' : 'spécification'}`;
    const jira = p.jiraProjectKey
      ? p.jiraProjectKey
      : p.effectiveJiraProjectKey
        ? `${p.effectiveJiraProjectKey} (projet par défaut)`
        : 'non configuré';
    return `${specs} · Jira : ${jira}`;
  }

  protected patch(change: Partial<Draft>): void {
    this.draft.update((d) => ({ ...d, ...change }));
  }

  protected startCreate(): void {
    this.draft.set({ ...EMPTY });
    this.editing.set('new');
  }

  protected startEdit(p: Project): void {
    this.draft.set({ name: p.name, description: p.description ?? '', jiraProjectKey: p.jiraProjectKey ?? '' });
    this.editing.set(p.id);
  }

  protected async save(project: Project | null): Promise<void> {
    const d = this.draft();
    if (!d.name.trim()) {
      return;
    }
    const input: ProjectInput = {
      name: d.name.trim(),
      description: d.description.trim() || null,
      jiraProjectKey: d.jiraProjectKey.trim() || null,
    };
    try {
      const saved = project ? await this.api.update(project.id, input) : await this.api.create(input);
      this.editing.set(null);
      this.warning.set(saved.jiraKeyWarning);
      this.announce.set(project ? `Projet « ${saved.name} » enregistré.` : `Projet « ${saved.name} » créé.`);
      await this.store.load();
    } catch {
      // Le bandeau affiche la cause (nom déjà utilisé, saisie invalide).
    }
  }

  protected async select(p: Project): Promise<void> {
    await this.store.select(p.id);
    this.announce.set(`« ${p.name} » est maintenant le projet courant.`);
  }

  protected async remove(p: Project): Promise<void> {
    const choice = await firstValueFrom(
      this.dialog
        .open<DeleteProjectDialogComponent, unknown, DeleteProjectChoice | null>(DeleteProjectDialogComponent, {
          data: { project: p, others: this.store.projects().filter((o) => o.id !== p.id) },
        })
        .afterClosed(),
    );
    if (!choice) {
      return;
    }
    try {
      await this.api.delete(p.id, choice.mode, choice.targetProjectId);
      this.announce.set(`Projet « ${p.name} » supprimé.`);
      await this.store.load();
    } catch {
      // Le bandeau affiche la cause (dernier projet, opération en cours).
    }
  }
}
