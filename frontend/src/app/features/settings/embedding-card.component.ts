import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { EmbeddingApi, EmbeddingSettings, Pooling } from '../../core/settings/embedding.api';
import { ModelState } from '../../core/projects/projects.model';

const STATES: Record<ModelState, string> = {
  MISSING: "Pas encore téléchargé : il le sera à la prochaine indexation, par le proxy d'entreprise.",
  DOWNLOADING: 'Téléchargement en cours.',
  READY: 'Prêt.',
  ERROR: 'En erreur.',
  TERMS_ONLY: 'Indisponible : la recherche se fait par les termes seulement.',
};

/**
 * Modèle de recherche par le sens (research.md R19 de 002) : état, et remplacement par un dossier
 * local avec son pooling et ses préfixes. Un autre modèle impose de réindexer.
 */
@Component({
  selector: 'sw-embedding-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  template: `
    <section class="sheet" aria-labelledby="embedding-title">
      <h2 id="embedding-title">Modèle de recherche</h2>
      @if (current(); as c) {
        <div class="status" [class.ok]="c.state === 'READY'" [class.warn]="c.state === 'ERROR' || c.state === 'TERMS_ONLY'" role="status">
          <p>{{ c.defaultModel ? 'Modèle par défaut (multilingual-e5-small).' : 'Modèle du dossier ' + c.folder + '.' }}</p>
          <p>{{ stateLabel(c.state) }}</p>
          @if (c.problem) {
            <p>Motif : {{ c.problem }}</p>
          }
        </div>
        <form (ngSubmit)="save()" aria-label="Remplacer le modèle de recherche">
          <mat-form-field appearance="outline">
            <mat-label>Dossier du modèle de remplacement</mat-label>
            <input matInput name="folder" maxlength="2000" [ngModel]="folder()" (ngModelChange)="folder.set($event)" />
            <mat-hint>Chemin absolu d'un dossier avec model.onnx et tokenizer.json. Vide : modèle par défaut.</mat-hint>
          </mat-form-field>
          @if (folder().trim()) {
            <mat-form-field appearance="outline">
              <mat-label>Pooling</mat-label>
              <mat-select name="pooling" [value]="pooling()" (selectionChange)="pooling.set($event.value)">
                <mat-option value="MEAN">MEAN (moyenne)</mat-option>
                <mat-option value="CLS">CLS</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Préfixe des recherches</mat-label>
              <input matInput name="queryPrefix" maxlength="50" [ngModel]="queryPrefix()" (ngModelChange)="queryPrefix.set($event)" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Préfixe des éléments indexés</mat-label>
              <input matInput name="passagePrefix" maxlength="50" [ngModel]="passagePrefix()" (ngModelChange)="passagePrefix.set($event)" />
            </mat-form-field>
          }
          <div class="actions">
            <button mat-flat-button type="submit" [disabled]="busy()">Utiliser ce modèle</button>
          </div>
          <p class="hint">Après un changement de modèle, réindexez les projets pour revectoriser leur connaissance.</p>
        </form>
      } @else {
        <p role="status" aria-live="polite">Chargement du modèle de recherche…</p>
      }
    </section>
  `,
  styleUrl: './settings-card.scss',
})
export class EmbeddingCardComponent {
  private readonly api = inject(EmbeddingApi);

  protected readonly current = signal<EmbeddingSettings | null>(null);
  protected readonly folder = signal('');
  protected readonly pooling = signal<Pooling>('MEAN');
  protected readonly queryPrefix = signal('');
  protected readonly passagePrefix = signal('');
  protected readonly busy = signal(false);

  constructor() {
    void this.api.get().then((s) => this.show(s)).catch(() => undefined);
  }

  protected stateLabel(state: ModelState): string {
    return STATES[state] ?? state;
  }

  private show(s: EmbeddingSettings): void {
    this.current.set(s);
    this.folder.set(s.folder ?? '');
    this.pooling.set(s.pooling);
    this.queryPrefix.set(s.queryPrefix ?? '');
    this.passagePrefix.set(s.passagePrefix ?? '');
  }

  protected async save(): Promise<void> {
    this.busy.set(true);
    try {
      this.show(
        await this.api.save({
          folder: this.folder().trim(),
          pooling: this.pooling(),
          queryPrefix: this.queryPrefix(),
          passagePrefix: this.passagePrefix(),
        }),
      );
    } catch {
      // le bandeau affiche le motif (chemin relatif, fichier manquant)
    } finally {
      this.busy.set(false);
    }
  }
}
