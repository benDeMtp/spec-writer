import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, HostListener, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { LeaveGuard } from '../../core/navigation/leave-guard';
import { ContextApi } from '../../core/projects/context.api';
import { KnowledgeApi } from '../../core/projects/knowledge.api';
import { ProjectsApi } from '../../core/projects/projects.api';
import { DocSource, DocSourceKind, DocumentsPreview, Project, ProjectInput } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { SettingsStore } from '../../core/settings/settings.store';
import { count } from '../../shared/text';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { ProjectHeadingComponent } from '../../shared/ui/project-heading.component';
import { ProjectIdentityFormComponent } from '../projects/project-identity-form.component';
import { SheetEditorComponent } from './sheet-editor.component';
import { SourceRootsComponent } from './source-roots.component';

/**
 * Réglages du projet courant (012, US2) : identité et clé Jira, fiche générale rédigée à la main,
 * sources de documentation et modèle d'architecture que spec-writer indexe (FR-008, FR-013 de 002).
 * Ce que l'outil en a tiré est dans la page Connaissance du projet.
 */
@Component({
  selector: 'sw-project-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    RouterLink,
    ProjectHeadingComponent,
    ProjectIdentityFormComponent,
    SheetEditorComponent,
    SourceRootsComponent,
  ],
  template: `
    <sw-project-heading label="Réglages du projet" />
    <p class="lead"><a routerLink="/projet/connaissance">Voir la connaissance du projet</a></p>
    @if (project(); as p) {
      <p role="status" aria-live="polite" class="announce">{{ announce() }}</p>
      @if (warning(); as w) {
        <p class="warning" role="alert">{{ w }}</p>
      }

      <section class="sheet" aria-labelledby="identity-title">
        <h2 id="identity-title">Identité</h2>
        <sw-project-identity-form
          keyId="jira"
          [project]="p"
          [defaultKey]="defaultKey()"
          [busy]="busy()"
          (saved)="saveIdentity($event)"
          (dirtyChange)="identityDirty.set($event)"
        />
      </section>

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
                <span class="meta">{{ kindLabel(s) }} · {{ lastRead(s) }}</span>
                @if (s.kind === 'DOCUMENTS') {
                  <span class="meta">{{ s.exclusions.length ? 'Exclus : ' + s.exclusions.join(', ') : "Aucun motif d'exclusion" }}</span>
                  <button mat-button type="button" (click)="editExclusions(s)" [attr.aria-label]="'Modifier les motifs de ' + s.location">
                    Motifs
                  </button>
                }
                <button mat-button type="button" (click)="removeSource(s)" [attr.aria-label]="'Retirer la source ' + s.location">
                  Retirer
                </button>
                @if (editing() === s.id) {
                  <form class="add motifs" (ngSubmit)="saveExclusions(s)" [attr.aria-label]="'Motifs d’exclusion de ' + s.location">
                    <mat-form-field appearance="outline" class="grow">
                      <mat-label>Motifs d'exclusion, un par ligne</mat-label>
                      <textarea
                        matInput
                        rows="4"
                        [name]="'exclusions-' + s.id"
                        [ngModelOptions]="{ standalone: true }"
                        [ngModel]="editedExclusions()"
                        (ngModelChange)="editedExclusions.set($event)"
                      ></textarea>
                    </mat-form-field>
                    <button mat-stroked-button type="submit" [disabled]="busy()">Enregistrer les motifs</button>
                    <button mat-button type="button" (click)="editing.set(null)">Annuler</button>
                  </form>
                }
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
              <mat-select name="kind" [value]="kind()" (selectionChange)="chooseKind($event.value)">
                <mat-option value="URL">Lien</mat-option>
                <mat-option value="FOLDER">Dossier</mat-option>
                <mat-option value="DOCUMENTS">Dossier de documents (SharePoint synchronisé)</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline" class="grow">
              <mat-label>{{ locationLabel() }}</mat-label>
              <input matInput name="location" maxlength="2000" autocomplete="off" spellcheck="false" [ngModel]="location()" (ngModelChange)="location.set($event)" />
            </mat-form-field>
            @if (kind() === 'DOCUMENTS') {
              <mat-form-field appearance="outline" class="full">
                <mat-label>Motifs d'exclusion (facultatif)</mat-label>
                <textarea
                  matInput
                  name="exclusions"
                  rows="3"
                  aria-describedby="exclusions-hint"
                  [ngModel]="exclusions()"
                  (ngModelChange)="exclusions.set($event)"
                ></textarea>
              </mat-form-field>
              <p id="exclusions-hint" class="hint full">
                Un motif par ligne, relatif au dossier : *.xlsx exclut tous les classeurs, Archives/** un dossier et son
                contenu, ? remplace un caractère. Word, Excel, PDF et HTML sont lus ; les autres fichiers sont ignorés.
              </p>
              <button mat-button type="button" (click)="previewSource()" [disabled]="!location().trim() || busy()">Aperçu du dossier</button>
            }
            <button mat-stroked-button type="submit" [disabled]="!location().trim()">Ajouter</button>
          </form>
          @if (kind() === 'DOCUMENTS') {
            @if (preview(); as pv) {
              <div class="preview" role="status">
                <p>{{ previewLabel(pv) }}</p>
                @if (pv.excludedExamples.length) {
                  <p class="hint">Exclus : {{ pv.excludedExamples.join(', ') }}</p>
                }
                @if (pv.ignoredExamples.length) {
                  <p class="hint">Ignorés (format) : {{ pv.ignoredExamples.join(', ') }}</p>
                }
                @if (pv.retainedExamples.length) {
                  <p class="hint">Retenus : {{ pv.retainedExamples.join(', ') }}</p>
                }
              </div>
            }
            <details class="sync-help">
              <summary>Synchroniser un dossier SharePoint</summary>
              <ol>
                <li>Ouvrez le dossier partagé dans SharePoint, dans le navigateur.</li>
                <li>
                  Sur un dossier partagé avec vous, choisissez « Ajouter un raccourci à Mes fichiers » ; pour une bibliothèque
                  entière, « Synchroniser ». OneDrive doit être connecté à votre compte professionnel.
                </li>
                <li>
                  Le dossier apparaît dans le Finder sous le nom de votre organisation, par exemple
                  ~/Library/CloudStorage/OneDrive-Organisation/… ; copiez ce chemin ici.
                </li>
                <li>
                  Pour une lecture hors ligne, clic droit sur le dossier, « Toujours conserver sur cet appareil ». Sinon,
                  OneDrive télécharge chaque fichier lu.
                </li>
              </ol>
              <p class="hint">Si « Synchroniser » n'apparaît pas, la synchronisation est désactivée par votre service informatique.</p>
            </details>
          }
        } @else {
          <p class="hint">Un projet a au plus 10 sources.</p>
        }
      </section>

      <section class="sheet" aria-labelledby="sources-code">
        <h2 id="sources-code" tabindex="-1">Sources du code</h2>
        <sw-source-roots [projectId]="p.id" [roots]="p.sourceRoots ?? []" (changed)="load()" />
      </section>

      <section class="sheet" aria-labelledby="architecture-title">
        <h2 id="architecture-title">Modèle d'architecture</h2>
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
      </section>
    }
  `,
  styles: `
    sw-project-heading {
      margin: 0 0 0.5rem;
    }
    .lead {
      margin: 0 0 1.5rem;
    }
    .warning {
      max-width: var(--sw-measure);
      padding: 0.75rem 1rem;
      border-left: 4px solid var(--sw-outcome);
      background: color-mix(in srgb, var(--sw-outcome) 8%, transparent);
      border-radius: var(--sw-radius-field);
    }
    h2 {
      margin: 0 0 0.5rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-1);
      font-weight: 600;
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
    .full {
      flex: 1 1 100%;
    }
    .motifs {
      flex: 1 1 100%;
    }
    .sync-help {
      margin-top: 0.75rem;
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
export class ProjectSettingsPage {
  private readonly store = inject(ProjectsStore);
  private readonly settings = inject(SettingsStore);
  private readonly dialog = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly sheetEditor = viewChild(SheetEditorComponent);
  private readonly sourceRoots = viewChild(SourceRootsComponent);
  private readonly projects = inject(ProjectsApi);
  private readonly api = inject(ContextApi);
  private readonly knowledge = inject(KnowledgeApi);

  protected readonly project = signal<Project | null>(null);
  protected readonly kind = signal<DocSourceKind>('URL');
  protected readonly location = signal('');
  /** Dossier de documents (006) : motifs saisis, aperçu, et motifs d'une source en cours de modification. */
  protected readonly exclusions = signal('');
  protected readonly preview = signal<DocumentsPreview | null>(null);
  protected readonly editing = signal<string | null>(null);
  protected readonly editedExclusions = signal('');
  protected readonly busy = signal(false);
  protected readonly announce = signal('');
  protected readonly workspace = signal('');
  protected readonly ownership = signal('');
  protected readonly warning = signal<string | null>(null);
  protected readonly identityDirty = signal(false);
  /** `#jira` dans l'adresse : le champ de la clé reçoit le focus dès qu'il est affiché. */
  private focusKey = false;
  /** `#sources-code` (013) : le titre « Sources du code » reçoit le focus. */
  private focusSources = false;
  protected readonly defaultKey = computed(() => this.settings.jira()?.defaultProjectKey ?? null);

  constructor() {
    effect(() => {
      const p = this.store.current();
      untracked(() => this.show(p));
    });
    if (!this.store.current()) {
      void this.store.load().catch(() => undefined);
    }
    void this.settings.ensureLoaded().catch(() => undefined);
    inject(DestroyRef).onDestroy(inject(LeaveGuard).register(() => this.canLeave()));
    // « Renseigner la clé Jira du projet » : #jira donne le focus au champ de la clé
    this.route.fragment.subscribe((fragment) => {
      this.focusKey = fragment === 'jira';
      this.focusSources = fragment === 'sources-code';
      this.focusKeyField();
    });
  }

  /** Rechargement ou fermeture de l'onglet : le navigateur prévient aussi (FR-013). */
  @HostListener('window:beforeunload', ['$event'])
  protected warnBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.unsaved()) {
      event.preventDefault();
    }
  }

  /** Une saisie non enregistrée serait perdue en quittant la page ou en changeant de projet (FR-013). */
  async canLeave(): Promise<boolean> {
    if (!this.unsaved()) {
      return true;
    }
    const result = await firstValueFrom(
      this.dialog
        .open(ConfirmDialogComponent, {
          data: {
            title: 'Quitter sans enregistrer ?',
            message: 'Les modifications des réglages du projet ne sont pas enregistrées : elles seront perdues.',
            confirm: 'Quitter sans enregistrer',
          },
        })
        .afterClosed(),
    );
    return result === true;
  }

  private unsaved(): boolean {
    const p = this.project();
    return (
      this.identityDirty() ||
      this.location().trim() !== '' ||
      this.editing() !== null ||
      (this.sheetEditor()?.dirty() ?? false) ||
      (this.sourceRoots()?.dirty() ?? false) ||
      (p !== null && this.workspace().trim() !== (p.architectureWorkspace ?? '')) ||
      (p !== null && this.ownership().trim() !== (p.architectureOwnership ?? ''))
    );
  }

  /** Relit le projet courant après une modification : le menu et la page suivent. */
  protected async load(): Promise<void> {
    await this.store.load();
  }

  protected async saveIdentity(input: ProjectInput): Promise<void> {
    const p = this.project();
    if (!p) {
      return;
    }
    await this.run(async () => {
      const saved = await this.projects.update(p.id, input);
      this.warning.set(saved.jiraKeyWarning);
      this.announce.set(`Projet « ${saved.name} » enregistré.`);
      await this.load();
    });
  }

  private show(p: Project | null): void {
    this.project.set(p);
    this.workspace.set(p?.architectureWorkspace ?? '');
    this.ownership.set(p?.architectureOwnership ?? '');
    this.focusKeyField();
  }

  /** Le champ n'existe qu'une fois le projet affiché : le focus attend le rendu. */
  private focusKeyField(): void {
    if (!this.focusKey && !this.focusSources) {
      return;
    }
    setTimeout(() => {
      // plusieurs rendus peuvent être en attente : seul le premier qui trouve la cible la sert
      if (!this.focusKey && !this.focusSources) {
        return;
      }
      const target = this.host.nativeElement.querySelector<HTMLElement>(this.focusKey ? '#jira' : '#sources-code');
      if (target) {
        this.focusKey = false;
        this.focusSources = false;
        target.focus();
      }
    });
  }

  protected kindLabel(s: DocSource): string {
    return s.kind === 'URL' ? 'Lien' : s.kind === 'DOCUMENTS' ? 'Documents' : 'Dossier';
  }

  protected locationLabel(): string {
    const k = this.kind();
    return k === 'URL' ? 'Adresse du site' : k === 'DOCUMENTS' ? 'Chemin du dossier synchronisé' : 'Chemin du dossier';
  }

  protected chooseKind(kind: DocSourceKind): void {
    this.kind.set(kind);
    this.preview.set(null);
  }

  protected previewLabel(p: DocumentsPreview): string {
    const label = `${count(p.retained, 'document retenu', 'documents retenus')}, ${count(p.excluded, 'exclu', 'exclus')}, ${count(p.ignored, 'ignoré', 'ignorés')}`;
    return p.limitReached ? `${label} (limite atteinte : aperçu partiel)` : label;
  }

  protected async previewSource(): Promise<void> {
    const p = this.project();
    const location = this.location().trim();
    if (!p || !location) {
      return;
    }
    await this.run(async () => {
      this.preview.set(await this.api.preview(p.id, location, lines(this.exclusions())));
    });
  }

  protected editExclusions(s: DocSource): void {
    this.editing.set(s.id);
    this.editedExclusions.set(s.exclusions.join('\n'));
  }

  protected async saveExclusions(s: DocSource): Promise<void> {
    const p = this.project();
    if (!p) {
      return;
    }
    await this.run(async () => {
      await this.api.setExclusions(p.id, s.id, lines(this.editedExclusions()));
      this.editing.set(null);
      this.announce.set("Motifs enregistrés. Ils s'appliquent à la prochaine indexation du dossier.");
      await this.load();
    });
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
      await this.api.save(p.id, markdown);
      await this.load();
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
      const kind = this.kind();
      await this.api.addSource(p.id, kind, location, kind === 'DOCUMENTS' ? lines(this.exclusions()) : undefined);
      this.location.set('');
      this.exclusions.set('');
      this.preview.set(null);
      this.announce.set('Source ajoutée. Elle est lue en arrière-plan.');
      await this.load();
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
      await this.load();
    });
  }

  protected async saveWorkspace(): Promise<void> {
    const p = this.project();
    const workspace = this.workspace().trim();
    if (!p || !workspace) {
      return;
    }
    await this.run(async () => {
      await this.knowledge.setArchitecture(p.id, workspace, this.ownership().trim() || null);
      await this.load();
      this.announce.set("Modèle d'architecture enregistré. Il est lu en arrière-plan.");
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
      await this.load();
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

/** Motifs saisis, un par ligne, sans les lignes vides. */
function lines(text: string): string[] {
  return text
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l.length > 0);
}
