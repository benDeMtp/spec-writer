import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, HostListener, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { LeaveGuard } from '../../core/navigation/leave-guard';
import { ContextApi } from '../../core/projects/context.api';
import { ProjectsApi } from '../../core/projects/projects.api';
import { SourceOrigin } from '../../core/knowledge/provenance';
import { Project, ProjectInput } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { SettingsStore } from '../../core/settings/settings.store';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { ProjectHeadingComponent } from '../../shared/ui/project-heading.component';
import { ProjectIdentityFormComponent } from '../projects/project-identity-form.component';
import { ArchitectureModelComponent } from './architecture-model.component';
import { DocSourcesComponent } from './doc-sources.component';
import { SheetEditorComponent } from './sheet-editor.component';
import { ReliabilityComponent } from './reliability.component';
import { SourceRootsComponent } from './source-roots.component';
import { SourceSyncComponent } from './source-sync.component';

/**
 * Réglages du projet courant (012, US2) : identité et clé Jira, fiche générale rédigée à la main,
 * sources de documentation et modèle d'architecture que spec-writer indexe (FR-008, FR-013 de 002).
 * Ce que l'outil en a tiré est dans la page Connaissance du projet.
 */
@Component({
  selector: 'sw-project-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ArchitectureModelComponent,
    DocSourcesComponent,
    ProjectHeadingComponent,
    ProjectIdentityFormComponent,
    ReliabilityComponent,
    SheetEditorComponent,
    SourceRootsComponent,
    SourceSyncComponent,
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

      <section class="sheet" aria-labelledby="sources-title" data-tour="doc-sources">
        <h2 id="sources-title">Sources de documentation</h2>
        <sw-doc-sources [project]="p" />
      </section>

      <section class="sheet" aria-labelledby="reliability-title">
        <h2 id="reliability-title">Fiabilité des sources</h2>
        <sw-reliability [projectId]="p.id" [origins]="origins()" />
      </section>

      <section class="sheet" aria-labelledby="source-sync-title">
        <h2 id="source-sync-title">Synchronisation des sources</h2>
        <sw-source-sync [projectId]="p.id" [revision]="syncRevision()" />
      </section>

      <section class="sheet" aria-labelledby="sources-code">
        <h2 id="sources-code" tabindex="-1">Sources du code</h2>
        <sw-source-roots [projectId]="p.id" [roots]="p.sourceRoots ?? []" (changed)="load()" />
      </section>

      <section class="sheet" aria-labelledby="architecture-title">
        <h2 id="architecture-title">Modèle d'architecture</h2>
        <sw-architecture-model [project]="p" />
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
  private readonly docSources = viewChild(DocSourcesComponent);
  private readonly architecture = viewChild(ArchitectureModelComponent);
  private readonly projects = inject(ProjectsApi);
  private readonly api = inject(ContextApi);

  protected readonly project = signal<Project | null>(null);
  /** Sources de documentation et workspace : la synchronisation des sources est relue s'ils changent. */
  protected readonly syncRevision = computed(() => {
    const p = this.project();
    return p ? `${p.docSources.map((d) => d.id).join(',')}|${p.architectureWorkspace ?? ''}` : '';
  });

  /** Sortes de source déclarées du projet, pour leurs niveaux de fiabilité (016). */
  protected readonly origins = computed<SourceOrigin[]>(() => {
    const p = this.project();
    if (!p) {
      return [];
    }
    const kinds = new Set(p.docSources.map((d) => d.kind));
    const out: SourceOrigin[] = [];
    if (p.jiraProjectKey || this.defaultKey()) {
      out.push('JIRA');
    }
    if (kinds.has('URL') || kinds.has('FOLDER')) {
      out.push('ANTORA');
    }
    if (kinds.has('DOCUMENTS')) {
      out.push('DOCUMENTS');
    }
    if (kinds.has('CONFLUENCE')) {
      out.push('CONFLUENCE');
    }
    if (p.architectureWorkspace) {
      out.push('ARCHITECTURE');
    }
    out.push('SPECIFICATIONS');
    return out;
  });
  protected readonly busy = signal(false);
  protected readonly announce = signal('');
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
    return (
      this.identityDirty() ||
      (this.docSources()?.dirty() ?? false) ||
      (this.sheetEditor()?.dirty() ?? false) ||
      (this.sourceRoots()?.dirty() ?? false) ||
      (this.architecture()?.dirty() ?? false)
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
