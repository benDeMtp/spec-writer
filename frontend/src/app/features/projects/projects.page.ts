import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ProjectFollower } from '../../core/projects/project-follower';
import { ProjectsApi } from '../../core/projects/projects.api';
import { Project, ProjectInput } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { SettingsStore } from '../../core/settings/settings.store';
import { DeleteProjectChoice, DeleteProjectDialogComponent } from './delete-project-dialog.component';
import { ProjectIdentityFormComponent } from './project-identity-form.component';

/** Projets : créer, modifier, supprimer, choisir le projet courant (US1, FR-001 à FR-007). */
@Component({
  selector: 'sw-projects-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, ProjectIdentityFormComponent],
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
                <button mat-button type="button" (click)="open(p, '/projet/connaissance')" [attr.aria-label]="'Connaissance du projet ' + p.name">
                  Connaissance
                </button>
                <button mat-button type="button" (click)="open(p, '/projet/reglages')" [attr.aria-label]="'Modifier le projet ' + p.name">
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
          </li>
        }
      </ul>
      @if (store.projects().length < 2) {
        <p class="hint">Le dernier projet restant ne peut pas être supprimé.</p>
      }

      @if (creating()) {
        <sw-project-identity-form
          keyId="new-jira-key"
          [cancellable]="true"
          [defaultKey]="defaultKey()"
          (saved)="create($event)"
          (cancelled)="creating.set(false)"
        />
      } @else {
        <button mat-flat-button type="button" (click)="creating.set(true)">Nouveau projet</button>
      }
    </div>

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

  private readonly follower = inject(ProjectFollower);
  private readonly router = inject(Router);
  private readonly settings = inject(SettingsStore);

  protected readonly creating = signal(false);
  protected readonly announce = signal('');
  protected readonly warning = signal<string | null>(null);
  protected readonly defaultKey = computed(() => this.settings.jira()?.defaultProjectKey ?? null);

  async ngOnInit(): Promise<void> {
    void this.settings.ensureLoaded().catch(() => undefined);
    await this.store.load();
  }

  protected meta(p: Project): string {
    const specs = `${p.sessionCount} ${p.sessionCount > 1 ? 'spécifications' : 'spécification'}`;
    const jira = p.jiraProjectKey
      ? p.jiraProjectKey
      : p.effectiveJiraProjectKey
        ? `${p.effectiveJiraProjectKey} (clé par défaut de l'application)`
        : 'aucun projet';
    return `${specs} · Jira : ${jira}`;
  }

  protected async create(input: ProjectInput): Promise<void> {
    try {
      const saved = await this.api.create(input);
      this.creating.set(false);
      this.warning.set(saved.jiraKeyWarning);
      this.announce.set(`Projet « ${saved.name} » créé.`);
      await this.store.load();
    } catch {
      // Le bandeau affiche la cause (nom déjà utilisé, saisie invalide).
    }
  }

  /** Réglages et connaissance portent sur le projet courant (012) : le projet le devient d'abord. */
  protected async open(p: Project, path: string): Promise<void> {
    try {
      await this.follower.follow(p.id);
      await this.router.navigateByUrl(path);
    } catch {
      // Le bandeau affiche la cause.
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
