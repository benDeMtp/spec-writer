import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MarkdownComponent } from 'ngx-markdown';
import { ContextSheetState, SheetKind } from '../../core/projects/projects.model';

export const SHEET_LABELS: Record<SheetKind, { name: string; adjective: string; hint: string }> = {
  FUNCTIONAL: {
    name: 'Fiche fonctionnelle',
    adjective: 'fonctionnelle',
    hint: 'Vision et objectifs, acteurs, vocabulaire, règles métier, contraintes fonctionnelles.',
  },
  TECHNICAL: {
    name: 'Fiche technique',
    adjective: 'technique',
    hint: 'Architecture, contraintes techniques, conventions de code, intégrations. Transmise aussi aux agents de code.',
  },
};

/** Une fiche du contexte, modifiable à la main avec un aperçu Markdown (FR-008a, FR-013c). */
@Component({
  selector: 'sw-sheet-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MarkdownComponent, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <p class="meta">{{ originLabel() }}</p>
    <p class="hint">{{ labels().hint }}</p>
    @if (previewing()) {
      <div class="rendered" role="region" [attr.aria-label]="'Aperçu de la ' + labels().name.toLowerCase()">
        @if (draft().trim()) {
          <markdown class="rich" [data]="draft()" />
        } @else {
          <p class="hint">La fiche est vide.</p>
        }
      </div>
    } @else {
      <mat-form-field appearance="outline" class="editor">
        <mat-label>{{ labels().name }} (Markdown)</mat-label>
        <textarea
          matInput
          rows="14"
          maxlength="20000"
          [name]="'context-' + sheet()"
          [ngModel]="draft()"
          (ngModelChange)="draft.set($event)"
          [attr.aria-describedby]="'count-' + sheet()"
        ></textarea>
        <mat-hint [id]="'count-' + sheet()" align="end">{{ draft().length }} / 20 000</mat-hint>
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
  readonly sheet = input.required<SheetKind>();
  readonly state = input.required<ContextSheetState>();
  readonly busy = input(false);
  readonly saved = output<string>();

  protected readonly draft = signal('');
  protected readonly previewing = signal(false);
  protected readonly labels = computed(() => SHEET_LABELS[this.sheet()]);
  protected readonly dirty = computed(() => this.draft().trim() !== this.state().markdown);

  constructor() {
    // une fiche enregistrée ou acceptée remplace la saisie
    effect(() => this.draft.set(this.state().markdown));
  }

  protected originLabel(): string {
    const s = this.state();
    const at = s.updatedAt ? new Date(s.updatedAt).toLocaleString('fr-FR', { dateStyle: 'long', timeStyle: 'short' }) : '';
    switch (s.origin) {
      case 'GENERATED':
        return `Proposée par l'IA et acceptée le ${at}.`;
      case 'EDITED':
        return `Modifiée à la main le ${at}.`;
      default:
        return `Aucune ${this.labels().name.toLowerCase()} pour ce projet.`;
    }
  }
}
