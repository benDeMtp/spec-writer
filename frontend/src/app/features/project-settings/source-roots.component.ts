import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { firstValueFrom } from 'rxjs';
import { SourceRoot, SourceRootPreview } from '../../core/projects/projects.model';
import { SourceRootsApi } from '../../core/projects/source-roots.api';
import { count } from '../../shared/text';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';

const KINDS: Record<string, string> = { MAVEN: 'Maven', NPM: 'npm', HELM: 'Helm', ROOT: 'racine' };

/**
 * Sources du code du projet (013, US1, contracts/ui.md) : racines déclarées, motifs d'exclusion et
 * aperçu des modules. Les erreurs (chemin absent, racine imbriquée, motif refusé) passent par le
 * bandeau commun, comme les autres réglages du projet.
 */
@Component({
  selector: 'sw-source-roots',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, NgTemplateOutlet],
  template: `
    <p class="hint" id="sources-code-info">
      Lors d'une analyse de périmètre, spec-writer envoie à GitHub Copilot les noms des modules et de courts extraits des
      lignes trouvées. Le code n'est ni copié ni indexé. Les fichiers sensibles (.env, clés, settings.xml…) ne sont jamais
      lus.
    </p>
    @if (roots().length) {
      <ul class="roots">
        @for (r of roots(); track r.id) {
          <li>
            <span class="location" translate="no">{{ r.label }}</span>
            <span class="meta path" translate="no">{{ r.location }}</span>
            @if (r.state !== 'OK') {
              <span class="state">{{ r.state === 'MISSING' ? 'Introuvable' : 'Illisible' }}</span>
            }
            <span class="meta">{{ r.exclusions.length ? 'Exclus : ' + r.exclusions.join(', ') : "Aucun motif d'exclusion" }}</span>
            <button
              mat-button
              type="button"
              (click)="toggleRootPreview(r)"
              [disabled]="busy()"
              [attr.aria-expanded]="rootPreview()?.id === r.id"
              [attr.aria-label]="'Détecter les modules de la racine ' + r.label"
            >
              Détecter les modules
            </button>
            <button mat-button type="button" (click)="edit(r)" [attr.aria-label]="'Modifier les motifs de la racine ' + r.label">Motifs</button>
            <button mat-button type="button" (click)="remove(r)" [attr.aria-label]="'Supprimer la racine ' + r.label">Supprimer</button>
            @if (editing() === r.id) {
              <form class="add motifs" (ngSubmit)="saveExclusions(r)" [attr.aria-label]="'Motifs d’exclusion de la racine ' + r.label">
                <mat-form-field appearance="outline" class="grow">
                  <mat-label>Motifs d'exclusion, un par ligne</mat-label>
                  <textarea
                    matInput
                    rows="4"
                    [name]="'root-exclusions-' + r.id"
                    [ngModel]="editedExclusions()"
                    (ngModelChange)="editedExclusions.set($event)"
                  ></textarea>
                </mat-form-field>
                <button mat-stroked-button type="submit" [disabled]="busy()">Enregistrer les exclusions</button>
                <button mat-button type="button" (click)="editing.set(null)">Annuler</button>
              </form>
            }
            @if (rootPreview(); as rp) {
              @if (rp.id === r.id) {
                <ng-container *ngTemplateOutlet="previewTpl; context: { $implicit: rp.preview, label: r.label }" />
              }
            }
          </li>
        }
      </ul>
    } @else {
      <p class="hint">Aucune source du code. Ajoutez le dossier racine de vos modules, par exemple celui du dépôt.</p>
    }
    @if (roots().length < 10) {
      <form class="add" (ngSubmit)="add()" aria-label="Ajouter une racine de sources">
        <mat-form-field appearance="outline" class="grow">
          <mat-label>Chemin du dossier</mat-label>
          <input
            matInput
            name="root-location"
            maxlength="2000"
            autocomplete="off"
            spellcheck="false"
            [ngModel]="location()"
            (ngModelChange)="changeLocation($event)"
          />
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>Motifs d'exclusion (facultatif)</mat-label>
          <textarea
            matInput
            name="root-exclusions"
            rows="3"
            aria-describedby="root-exclusions-hint"
            [ngModel]="exclusions()"
            (ngModelChange)="changeExclusions($event)"
          ></textarea>
        </mat-form-field>
        <p id="root-exclusions-hint" class="hint full">
          Un motif par ligne, relatif au dossier : legacy/** exclut un dossier et son contenu, **/*.generated.java des fichiers
          à toute profondeur. Les dossiers générés (target, node_modules…) et les fichiers ignorés par .gitignore sont
          toujours écartés.
        </p>
        <button mat-button type="button" (click)="showPreview()" [disabled]="!location().trim() || busy()">Détecter les modules</button>
        <button mat-stroked-button type="submit" [disabled]="!location().trim() || busy()">Ajouter la racine</button>
      </form>
      @if (preview(); as pv) {
        <ng-container *ngTemplateOutlet="previewTpl; context: { $implicit: pv, label: null }" />
      }
    } @else {
      <p class="hint">Un projet a au plus 10 racines de sources.</p>
    }
    <ng-template #previewTpl let-pv let-label="label">
      <div class="preview">
        <p role="status">{{ previewLabel(pv) }}</p>
        @if (pv.modules.length) {
          <ul class="modules" tabindex="0" [attr.aria-label]="label ? 'Modules détectés de la racine ' + label : 'Modules détectés'">
            @for (m of pv.modules; track m.path) {
              <li>
                <span class="name">{{ m.name }}</span><span class="meta"> {{ m.path || '(racine)' }} · {{ kindLabel(m.kind) }}</span>
              </li>
            }
          </ul>
        }
      </div>
    </ng-template>
  `,
  styles: `
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
    .full,
    .motifs {
      flex: 1 1 100%;
    }
    .roots .preview {
      flex: 1 1 100%;
    }
    .roots,
    .modules {
      margin: 0 0 1rem;
      padding: 0;
      list-style: none;
    }
    .roots li {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.5rem 1rem;
      padding: 0.5rem 0;
      border-top: 1px solid var(--sw-rule);
    }
    .roots li:first-child {
      border-top: none;
    }
    .location,
    .name {
      overflow-wrap: anywhere;
      font-weight: 600;
    }
    .path {
      overflow-wrap: anywhere;
    }
    .state {
      font-weight: 600;
      color: var(--sw-outcome);
    }
    .modules {
      max-height: 18rem;
      overflow: auto;
    }
    .modules li {
      padding: 0.125rem 0;
    }
  `,
})
export class SourceRootsComponent {
  private readonly api = inject(SourceRootsApi);
  private readonly dialog = inject(MatDialog);
  private readonly announcer = inject(LiveAnnouncer);

  readonly projectId = input.required<string>();
  readonly roots = input<SourceRoot[]>([]);
  /** Une racine a été ajoutée, modifiée ou supprimée : le projet est à relire. */
  readonly changed = output<void>();

  protected readonly location = signal('');
  protected readonly exclusions = signal('');
  protected readonly preview = signal<SourceRootPreview | null>(null);
  protected readonly editing = signal<string | null>(null);
  protected readonly editedExclusions = signal('');
  protected readonly busy = signal(false);
  /** Aperçu d'une racine déjà déclarée, lu avec son chemin et ses motifs enregistrés. */
  protected readonly rootPreview = signal<{ id: string; preview: SourceRootPreview } | null>(null);

  /** Saisie non enregistrée, perdue en quittant la page. */
  dirty(): boolean {
    return this.location().trim() !== '' || this.editing() !== null;
  }

  protected kindLabel(kind: string): string {
    return KINDS[kind] ?? kind;
  }

  protected changeLocation(value: string): void {
    this.location.set(value);
    this.preview.set(null);
  }

  protected changeExclusions(value: string): void {
    this.exclusions.set(value);
    this.preview.set(null);
  }

  protected previewLabel(p: SourceRootPreview): string {
    const parts = [
      count(p.moduleCount, 'module détecté', 'modules détectés'),
      count(p.retainedFiles, 'fichier retenu', 'fichiers retenus'),
      `${count(p.excludedFiles, 'exclu', 'exclus')} par vos motifs`,
      `${count(p.ignoredByGit, 'ignoré', 'ignorés')} par .gitignore`,
      count(p.sensitiveFiles, 'fichier sensible écarté', 'fichiers sensibles écartés'),
    ];
    let label = parts.join(' · ');
    if (p.moduleCount > p.modules.length) {
      label += ` (${p.modules.length} premiers affichés)`;
    }
    return p.limitReached ? `${label}. Limite atteinte : aperçu partiel.` : label;
  }

  protected async showPreview(): Promise<void> {
    const location = this.location().trim();
    if (!location) {
      return;
    }
    await this.run(async () => {
      this.preview.set(await this.api.preview(this.projectId(), location, lines(this.exclusions())));
    });
  }

  protected async toggleRootPreview(r: SourceRoot): Promise<void> {
    if (this.rootPreview()?.id === r.id) {
      this.rootPreview.set(null);
      return;
    }
    await this.run(async () => {
      this.rootPreview.set({ id: r.id, preview: await this.api.preview(this.projectId(), r.location, r.exclusions) });
    });
  }

  protected async add(): Promise<void> {
    const location = this.location().trim();
    if (!location) {
      return;
    }
    await this.run(async () => {
      const root = await this.api.add(this.projectId(), location, lines(this.exclusions()));
      this.location.set('');
      this.exclusions.set('');
      this.preview.set(null);
      void this.announcer.announce(`Racine ${root.label} ajoutée.`);
      this.changed.emit();
    });
  }

  protected edit(r: SourceRoot): void {
    this.editing.set(r.id);
    this.editedExclusions.set(r.exclusions.join('\n'));
  }

  protected async saveExclusions(r: SourceRoot): Promise<void> {
    await this.run(async () => {
      await this.api.setExclusions(this.projectId(), r.id, lines(this.editedExclusions()));
      this.editing.set(null);
      if (this.rootPreview()?.id === r.id) {
        this.rootPreview.set(null);
      }
      void this.announcer.announce(`Exclusions de la racine ${r.label} enregistrées.`);
      this.changed.emit();
    });
  }

  protected async remove(r: SourceRoot): Promise<void> {
    const confirmed = await firstValueFrom(
      this.dialog
        .open(ConfirmDialogComponent, {
          data: {
            title: `Supprimer la racine ${r.label} ?`,
            message: 'Les analyses passées restent lisibles. Les prochaines analyses ne liront plus ce dossier.',
            confirm: 'Supprimer la racine',
          },
        })
        .afterClosed(),
    );
    if (confirmed !== true) {
      return;
    }
    await this.run(async () => {
      await this.api.remove(this.projectId(), r.id);
      void this.announcer.announce(`Racine ${r.label} supprimée.`);
      this.changed.emit();
    });
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
    } catch {
      // Le bandeau commun affiche la cause (chemin absent, racine imbriquée, motif refusé).
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
