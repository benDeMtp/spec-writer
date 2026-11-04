import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { ContextApi } from '../../core/projects/context.api';
import { DocSource, DocSourceKind, Project } from '../../core/projects/projects.model';
import { ProjectsApi } from '../../core/projects/projects.api';
import { KnowledgeApi } from '../../core/projects/knowledge.api';
import { KnowledgePanelComponent } from './knowledge-panel.component';
import { SheetEditorComponent } from './sheet-editor.component';

/**
 * Contexte d'un projet (US2 de 002) : la fiche générale, rédigée à la main, et les sources de
 * documentation que spec-writer indexe (FR-008, FR-013, research.md R25).
 */
@Component({
  selector: 'sw-project-context-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, RouterLink, SheetEditorComponent, KnowledgePanelComponent],
  template: `
    <p class="back"><a routerLink="/projets">Projets</a></p>
    @if (project(); as p) {
      <h1>Contexte du projet {{ p.name }}</h1>
      <p role="status" aria-live="polite" class="announce">{{ announce() }}</p>

      <section class="sheet" aria-labelledby="fiche-title">
        <h2 id="fiche-title">Fiche générale</h2>
        <sw-sheet-editor [markdown]="p.context" [updatedAt]="p.contextUpdatedAt" [busy]="busy()" (saved)="save($event)" />
      </section>

      <section class="sheet" aria-labelledby="sources-title">
        <h2 id="sources-title">Sources de documentation</h2>
        @if (p.docSources.length) {
          <ul class="sources">
            @for (s of p.docSources; track s.id) {
              <li>
                <span class="location">{{ s.location }}</span>
                <span class="meta">{{ s.kind === 'URL' ? 'Lien' : 'Dossier' }} · {{ lastRead(s) }}</span>
                <button mat-button type="button" (click)="removeSource(s)" [attr.aria-label]="'Retirer la source ' + s.location">
                  Retirer
                </button>
              </li>
            }
          </ul>
        } @else {
          <p class="hint">Aucune source. Ajoutez l'adresse d'un site Antora ou le dossier d'un site généré.</p>
        }
        @if (p.docSources.length < 10) {
          <form class="add" (ngSubmit)="addSource()" aria-label="Ajouter une source">
            <mat-form-field appearance="outline">
              <mat-label>Type</mat-label>
              <mat-select name="kind" [value]="kind()" (selectionChange)="kind.set($event.value)">
                <mat-option value="URL">Lien</mat-option>
                <mat-option value="FOLDER">Dossier</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline" class="grow">
              <mat-label>{{ kind() === 'URL' ? 'Adresse du site' : 'Chemin du dossier' }}</mat-label>
              <input matInput name="location" maxlength="2000" [ngModel]="location()" (ngModelChange)="location.set($event)" />
            </mat-form-field>
            <button mat-stroked-button type="submit" [disabled]="!location().trim()">Ajouter</button>
          </form>
        } @else {
          <p class="hint">Un projet a au plus 10 sources.</p>
        }
      </section>

      <section class="sheet" aria-labelledby="architecture-title">
        <h2 id="architecture-title">Modèle d'architecture</h2>
        <p class="hint">
          Le fichier workspace.dsl Structurizr du projet, lu avec le modèle qu'il étend et les fichiers qu'il inclut. Les
          éléments dont la propriété ownership vaut la valeur indiquée sont propres au projet.
        </p>
        <form class="add" (ngSubmit)="saveWorkspace()" aria-label="Modèle d'architecture">
          <mat-form-field appearance="outline" class="grow">
            <mat-label>Chemin du fichier workspace.dsl</mat-label>
            <input matInput name="workspace" maxlength="2000" [ngModel]="workspace()" (ngModelChange)="workspace.set($event)" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Ownership du projet</mat-label>
            <input matInput name="ownership" maxlength="80" [ngModel]="ownership()" (ngModelChange)="ownership.set($event)" />
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
        <p class="hint">Jira : {{ p.effectiveJiraProjectKey ? 'projet ' + p.effectiveJiraProjectKey : 'aucun projet Jira' }}.</p>
      </section>

      <section class="sheet" aria-labelledby="knowledge-title">
        <h2 id="knowledge-title">Connaissance du projet</h2>
        <sw-knowledge-panel [projectId]="p.id" [revision]="revision()" />
      </section>
    }
  `,
  styles: `
    h1 {
      margin: 0 0 1.5rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-3);
      font-weight: 600;
    }
    h2 {
      margin: 0 0 0.5rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-1);
      font-weight: 600;
    }
    .back {
      margin: 0 0 0.5rem;
    }
    .announce:empty {
      display: none;
    }
    .sheet {
      max-width: var(--sw-measure);
      margin-bottom: 1.5rem;
      padding: 1.5rem clamp(1rem, 3vw, 2rem);
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-sheet);
    }
    .meta,
    .hint {
      margin: 0.25rem 0 0.75rem;
      color: var(--sw-ink-soft);
    }
    .actions,
    .add {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.5rem;
    }
    .grow {
      flex: 1 1 18rem;
    }
    .sources {
      margin: 0 0 1rem;
      padding: 0;
      list-style: none;
    }
    .sources li {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.5rem 1rem;
      padding: 0.5rem 0;
      border-top: 1px solid var(--sw-rule);
    }
    .sources li:first-child {
      border-top: none;
    }
    .location {
      overflow-wrap: anywhere;
      font-weight: 600;
    }
  `,
})
export class ProjectContextPage {
  readonly id = input.required<string>();

  private readonly projects = inject(ProjectsApi);
  private readonly api = inject(ContextApi);
  private readonly knowledge = inject(KnowledgeApi);

  protected readonly project = signal<Project | null>(null);
  protected readonly kind = signal<DocSourceKind>('URL');
  protected readonly location = signal('');
  protected readonly busy = signal(false);
  protected readonly announce = signal('');
  protected readonly workspace = signal('');
  protected readonly ownership = signal('');
  /** Une source ou le workspace changent : la connaissance est relue (indexation lancée par le serveur). */
  protected readonly revision = signal(0);

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => void this.load(id));
    });
  }

  private async load(id: string): Promise<void> {
    const all = await this.projects.list();
    this.show(all.find((p) => p.id === id) ?? null);
  }

  private show(p: Project | null): void {
    this.project.set(p);
    this.workspace.set(p?.architectureWorkspace ?? '');
    this.ownership.set(p?.architectureOwnership ?? '');
  }

  protected lastRead(s: DocSource): string {
    if (!s.lastReadAt) {
      return 'jamais lue';
    }
    const at = new Date(s.lastReadAt).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });
    const errors = s.pagesInError ? `, ${s.pagesInError} en erreur` : '';
    return `${s.pagesRead ?? 0} ${(s.pagesRead ?? 0) > 1 ? 'pages lues' : 'page lue'}${errors}, le ${at}`;
  }

  protected async save(markdown: string): Promise<void> {
    const p = this.project();
    if (!p) {
      return;
    }
    await this.run(async () => {
      this.show(await this.api.save(p.id, markdown));
      this.announce.set('Fiche générale enregistrée.');
    });
  }

  protected async addSource(): Promise<void> {
    const p = this.project();
    const location = this.location().trim();
    if (!p || !location) {
      return;
    }
    await this.run(async () => {
      await this.api.addSource(p.id, this.kind(), location);
      this.location.set('');
      this.announce.set('Source ajoutée. Elle est lue en arrière-plan.');
      await this.load(p.id);
      this.revision.update((n) => n + 1);
    });
  }

  protected async removeSource(s: DocSource): Promise<void> {
    const p = this.project();
    if (!p) {
      return;
    }
    await this.run(async () => {
      await this.api.deleteSource(p.id, s.id);
      this.announce.set(`Source ${s.location} retirée.`);
      await this.load(p.id);
      this.revision.update((n) => n + 1);
    });
  }

  protected async saveWorkspace(): Promise<void> {
    const p = this.project();
    const workspace = this.workspace().trim();
    if (!p || !workspace) {
      return;
    }
    await this.run(async () => {
      this.show(await this.knowledge.setArchitecture(p.id, workspace, this.ownership().trim() || null));
      this.announce.set("Modèle d'architecture enregistré. Il est lu en arrière-plan.");
      this.revision.update((n) => n + 1);
    });
  }

  protected async clearWorkspace(): Promise<void> {
    const p = this.project();
    if (!p) {
      return;
    }
    await this.run(async () => {
      await this.knowledge.clearArchitecture(p.id);
      this.announce.set("Modèle d'architecture retiré.");
      await this.load(p.id);
      this.revision.update((n) => n + 1);
    });
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
    } catch {
      // Le bandeau affiche la cause (saisie invalide, projet introuvable).
      this.announce.set('');
    } finally {
      this.busy.set(false);
    }
  }
}
