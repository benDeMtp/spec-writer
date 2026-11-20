import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { RELIABILITY_LABELS, Reliability } from '../../core/knowledge/provenance';
import { ContextApi } from '../../core/projects/context.api';
import { ConfluencePreview, DocSource, DocSourceKind, DocumentsPreview, Project } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { count } from '../../shared/text';

/**
 * Sources de documentation d'un projet (002, 006, 016) : liste, ajout, retrait, motifs d'exclusion
 * et niveau d'une racine Confluence. Partagé par les réglages du projet et l'assistant de premier
 * lancement (018, R3). Après chaque modification, le projet courant est relu.
 */
@Component({
  selector: 'sw-doc-sources',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, RouterLink],
  template: `
    <p role="status" aria-live="polite" class="announce">{{ announce() }}</p>
    @if (project(); as p) {
      @if (p.docSources.length) {
        <ul class="sources">
          @for (s of p.docSources; track s.id) {
            <li>
              <span class="location">{{ s.kind === 'CONFLUENCE' && s.title ? s.title : s.location }}</span>
              <span class="meta">{{ kindLabel(s) }} · {{ lastRead(s) }}</span>
              @if (s.kind === 'CONFLUENCE') {
                <span class="break" aria-hidden="true"></span>
                <mat-form-field appearance="outline" class="level" subscriptSizing="dynamic">
                  <mat-label>Niveau de cette racine</mat-label>
                  <mat-select
                    [value]="s.reliability ?? 'INHERIT'"
                    (selectionChange)="setRootLevel(s, $event.value === 'INHERIT' ? null : $event.value)"
                    [attr.data-testid]="'root-level-' + s.id"
                  >
                    <mat-option value="INHERIT">Celui de Confluence</mat-option>
                    @for (r of levels; track r) {
                      <mat-option [value]="r">{{ levelLabel(r) }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              }
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
              <mat-option value="CONFLUENCE">Page Confluence et toute sa descendance</mat-option>
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
          @if (kind() === 'CONFLUENCE') {
            <button mat-button type="button" (click)="previewConfluence()" [disabled]="!location().trim() || busy()">
              Aperçu de la racine
            </button>
          }
          <button mat-stroked-button type="submit" [disabled]="!location().trim()">Ajouter</button>
        </form>
        @if (kind() === 'CONFLUENCE') {
          @if (confluencePreview(); as cp) {
            <div class="preview" role="status" data-testid="confluence-preview">
              @if (cp.problem) {
                <p>{{ confluenceRefusal(cp) }}</p>
                @if (cp.problem === 'CONFLUENCE_NOT_CONFIGURED') {
                  <p><a routerLink="/configuration">Déclarer le serveur Confluence dans Configuration</a></p>
                }
              } @else {
                <p>« {{ cp.title }} » : {{ pagesLabel(cp.pages) }}, la page et toute sa descendance.</p>
              }
            </div>
          } @else {
            <p class="hint">
              Copiez l’adresse de la page depuis votre navigateur : toutes les pages enfants, à toute profondeur, seront
              indexées. Le serveur se déclare une fois dans Configuration.
            </p>
          }
        }
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
    }
  `,
  styles: `
    .announce:empty {
      display: none;
    }
    .meta,
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
    /* niveau propre d'une racine Confluence, dans la ligne de la source (016) */
    .sources li:has(.level) {
      align-items: center;
    }
    .level {
      width: 15rem;
    }
    .break {
      flex-basis: 100%;
      height: 0;
    }
  `,
})
export class DocSourcesComponent {
  private readonly store = inject(ProjectsStore);
  private readonly api = inject(ContextApi);

  readonly project = input.required<Project>();

  protected readonly kind = signal<DocSourceKind>('URL');
  protected readonly location = signal('');
  /** Dossier de documents (006) : motifs saisis, aperçu, et motifs d'une source en cours de modification. */
  protected readonly exclusions = signal('');
  protected readonly preview = signal<DocumentsPreview | null>(null);
  /** Racine Confluence (016) : aperçu avant l'ajout. */
  protected readonly confluencePreview = signal<ConfluencePreview | null>(null);
  protected readonly levels: Reliability[] = ['REFERENCE', 'INTERPRETATION', 'TO_CONFIRM'];
  protected readonly editing = signal<string | null>(null);
  protected readonly editedExclusions = signal('');
  protected readonly busy = signal(false);
  protected readonly announce = signal('');

  /** Une adresse saisie ou des motifs en cours de modification seraient perdus en quittant. */
  dirty(): boolean {
    return this.location().trim() !== '' || this.editing() !== null;
  }

  protected kindLabel(s: DocSource): string {
    return s.kind === 'URL' ? 'Lien' : s.kind === 'DOCUMENTS' ? 'Documents' : s.kind === 'CONFLUENCE' ? 'Confluence' : 'Dossier';
  }

  protected locationLabel(): string {
    const k = this.kind();
    return k === 'URL'
      ? 'Adresse du site'
      : k === 'DOCUMENTS'
        ? 'Chemin du dossier synchronisé'
        : k === 'CONFLUENCE'
          ? 'Adresse de la page Confluence'
          : 'Chemin du dossier';
  }

  protected chooseKind(kind: DocSourceKind): void {
    this.kind.set(kind);
    this.preview.set(null);
    this.confluencePreview.set(null);
  }

  protected levelLabel(r: Reliability): string {
    return RELIABILITY_LABELS[r];
  }

  protected pagesLabel(n: number): string {
    return count(n, 'page', 'pages');
  }

  /** Refus d'une racine, avec ce qu'il faut faire (016, FR-004). */
  protected confluenceRefusal(p: ConfluencePreview): string {
    switch (p.problem) {
      case 'CONFLUENCE_NOT_CONFIGURED':
        return 'Aucun serveur Confluence n’est déclaré.';
      case 'OTHER_HOST':
        return 'Cette page n’est pas sur le serveur Confluence déclaré dans Configuration.';
      case 'ACCESS_DENIED':
        return 'Votre jeton ne donne pas accès à cette page.';
      case 'TOO_LARGE':
        return `Cette racine compte ${count(p.pages, 'page', 'pages')} : un projet en indexe 2 000 au plus. Choisissez une page plus basse dans l’arbre.`;
      case 'UNREACHABLE':
        return 'Confluence ne répond pas. Vérifiez votre connexion, puis réessayez.';
      default:
        return 'Cette adresse ne désigne pas une page Confluence lisible. Copiez l’adresse de la page depuis votre navigateur.';
    }
  }

  protected async previewConfluence(): Promise<void> {
    const location = this.location().trim();
    if (!location) {
      return;
    }
    await this.run(async () => {
      this.confluencePreview.set(await this.api.previewConfluence(this.project().id, location));
    });
  }

  /** Niveau propre d'une racine (016, FR-016a) ; `null` : celui de Confluence. */
  protected async setRootLevel(s: DocSource, level: Reliability | null): Promise<void> {
    await this.run(async () => {
      await this.api.setSourceReliability(this.project().id, s.id, level);
      this.announce.set(`Niveau de ${s.title ?? s.location} : ${level ? RELIABILITY_LABELS[level] : 'celui de Confluence'}.`);
      await this.store.load();
    });
  }

  protected previewLabel(p: DocumentsPreview): string {
    const label = `${count(p.retained, 'document retenu', 'documents retenus')}, ${count(p.excluded, 'exclu', 'exclus')}, ${count(p.ignored, 'ignoré', 'ignorés')}`;
    return p.limitReached ? `${label} (limite atteinte : aperçu partiel)` : label;
  }

  protected async previewSource(): Promise<void> {
    const location = this.location().trim();
    if (!location) {
      return;
    }
    await this.run(async () => {
      this.preview.set(await this.api.preview(this.project().id, location, lines(this.exclusions())));
    });
  }

  protected editExclusions(s: DocSource): void {
    this.editing.set(s.id);
    this.editedExclusions.set(s.exclusions.join('\n'));
  }

  protected async saveExclusions(s: DocSource): Promise<void> {
    await this.run(async () => {
      await this.api.setExclusions(this.project().id, s.id, lines(this.editedExclusions()));
      this.editing.set(null);
      this.announce.set("Motifs enregistrés. Ils s'appliquent à la prochaine indexation du dossier.");
      await this.store.load();
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

  protected async addSource(): Promise<void> {
    const location = this.location().trim();
    if (!location) {
      return;
    }
    await this.run(async () => {
      const kind = this.kind();
      await this.api.addSource(this.project().id, kind, location, kind === 'DOCUMENTS' ? lines(this.exclusions()) : undefined);
      this.location.set('');
      this.exclusions.set('');
      this.preview.set(null);
      this.confluencePreview.set(null);
      this.announce.set('Source ajoutée. Elle est lue en arrière-plan.');
      await this.store.load();
    });
  }

  protected async removeSource(s: DocSource): Promise<void> {
    await this.run(async () => {
      await this.api.deleteSource(this.project().id, s.id);
      this.announce.set(`Source ${s.location} retirée.`);
      await this.store.load();
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
