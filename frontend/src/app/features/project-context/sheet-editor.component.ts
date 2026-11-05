import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MarkdownComponent } from 'ngx-markdown';

/** Fiche générale, rédigée à la main avec un aperçu Markdown (FR-008). */
@Component({
  selector: 'sw-sheet-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MarkdownComponent, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <p class="meta">{{ updatedLabel() }}</p>
    <p class="hint">
      Vocabulaire, acteurs, règles et décisions propres au projet. Elle l'emporte sur la documentation, les tickets et
      le modèle d'architecture, et accompagne chaque rédaction.
    </p>
    @if (previewing()) {
      <div class="rendered" role="region" aria-label="Aperçu de la fiche générale">
        @if (draft().trim()) {
          <markdown class="rich" [data]="draft()" />
        } @else {
          <p class="hint">La fiche est vide.</p>
        }
      </div>
    } @else {
      <mat-form-field appearance="outline" class="editor">
        <mat-label>Fiche générale (Markdown)</mat-label>
        <textarea
          matInput
          rows="14"
          maxlength="20000"
          name="context"
          [ngModel]="draft()"
          (ngModelChange)="draft.set($event)"
          aria-describedby="context-count"
        ></textarea>
        <mat-hint id="context-count" align="end">{{ draft().length }} / 20 000</mat-hint>
      </mat-form-field>
    }
    <div class="actions">
      <button mat-stroked-button type="button" (click)="previewing.set(!previewing())" [attr.aria-pressed]="previewing()">
        Aperçu
      </button>
      <button mat-flat-button type="button" (click)="saved.emit(draft())" [disabled]="!dirty() || busy()">
        Enregistrer la fiche
      </button>
    </div>
  `,
  styles: `
    .meta,
    .hint {
      margin: 0.25rem 0 0.75rem;
      color: var(--sw-ink-soft);
    }
    .editor {
      width: 100%;
    }
    .rendered {
      margin: 0.5rem 0 1rem;
      padding: 0.75rem 1rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
  `,
})
export class SheetEditorComponent {
  readonly markdown = input.required<string>();
  readonly updatedAt = input<string | null>(null);
  readonly busy = input(false);
  readonly saved = output<string>();

  protected readonly draft = signal('');
  protected readonly previewing = signal(false);
  protected readonly dirty = computed(() => this.draft().trim() !== this.markdown());

  constructor() {
    // une fiche enregistrée remplace la saisie
    effect(() => this.draft.set(this.markdown()));
  }

  protected updatedLabel(): string {
    const at = this.updatedAt();
    if (!this.markdown() || !at) {
      return 'Aucune fiche générale pour ce projet.';
    }
    return `Modifiée le ${new Date(at).toLocaleString('fr-FR', { dateStyle: 'long', timeStyle: 'short' })}.`;
  }
}
