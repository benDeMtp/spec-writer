import { ChangeDetectionStrategy, Component, OnDestroy, effect, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { KnowledgeApi } from '../../core/projects/knowledge.api';
import { BetterModel, MentionDocument, MentionField, MentionState } from '../../core/projects/projects.model';
import { MENTION_DELAYS } from './mention.directive';

const NUMBER = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 });

/**
 * État de chaque mention @ d'un champ, avant la génération (007, US3) : transmise, en partie, faute
 * de place, ambiguë (avec les documents possibles), introuvable, ADR remplacé ou obsolète. Aucune
 * mention n'échoue en silence. Estimation sur l'index, à la pause de frappe. Une mention coupée
 * indique le modèle qui la transmettrait en entier (008).
 */
@Component({
  selector: 'sw-mention-status',
  imports: [MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mention-status" aria-live="polite">
      @if (states().length) {
        <ul aria-label="Documents mentionnés">
          @for (m of states(); track $index) {
            <li [class]="m.state">
              <span class="mention">{{ m.text }}</span>
              @if (m.document) {
                <span class="doc">{{ m.document.title }}</span>
              }
              <span class="state">{{ label(m) }}</span>
              @if (m.betterModel; as better) {
                <span class="better">{{ betterLabel(better) }}</span>
              }
              @for (c of m.candidates; track c.path) {
                <button mat-button type="button" (click)="use(m, c)">Utiliser {{ c.path }}</button>
              }
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: `
    ul {
      list-style: none;
      margin: 0.25rem 0 0;
      padding: 0;
      font-size: 0.875rem;
    }
    li {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0 0.5rem;
    }
    .mention {
      font-weight: 500;
    }
    .doc {
      opacity: 0.85;
    }
    .better {
      font-style: italic;
    }
    li.NOT_FOUND .state,
    li.AMBIGUOUS .state,
    li.NO_ROOM .state,
    li.DEPRECATED .state {
      color: var(--mat-sys-error, #b3261e);
    }
  `,
})
export class MentionStatusComponent implements OnDestroy {
  readonly projectId = input<string | null>(null);
  readonly sessionId = input<string | null>(null);
  readonly field = input.required<MentionField>();
  readonly text = input('');
  /** Texte réécrit quand l'utilisateur lève une ambiguïté. */
  readonly replaced = output<string>();

  private readonly api = inject(KnowledgeApi);
  private readonly delays = inject(MENTION_DELAYS);
  protected readonly states = signal<MentionState[]>([]);
  private timer: ReturnType<typeof setTimeout> | undefined;
  private request = 0;

  constructor() {
    effect(() => {
      const projectId = this.projectId();
      const sessionId = this.sessionId();
      const field = this.field();
      const text = this.text() ?? '';
      clearTimeout(this.timer);
      const request = ++this.request;
      if (!projectId || !text.includes('@')) {
        this.states.set([]);
        return;
      }
      this.timer = setTimeout(async () => {
        try {
          const states = await this.api.resolveMentions(projectId, sessionId, [{ field, text }]);
          if (request === this.request) {
            this.states.set(states);
          }
        } catch {
          if (request === this.request) {
            this.states.set([]);
          }
        }
      }, this.delays.resolve);
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.timer);
  }

  protected label(m: MentionState): string {
    switch (m.state) {
      case 'TRANSMITTED':
        return 'transmis à l’IA';
      case 'PARTIAL':
        return `transmis en partie (environ ${m.transmittedShare} %)`;
      case 'NO_ROOM':
        return 'non transmis : plus de place';
      case 'AMBIGUOUS':
        return 'ambigu : précisez le document';
      case 'NOT_FOUND':
        return 'introuvable';
      case 'SUPERSEDED':
        return 'ADR remplacé, son successeur est transmis';
      case 'DEPRECATED':
        return 'ADR obsolète, non transmis';
    }
  }

  /** Suggestion seulement (008, US3) : le modèle se choisit dans la configuration. */
  protected betterLabel(b: BetterModel): string {
    const cost = b.multiplier === null ? 'coût inconnu' : `×${NUMBER.format(b.multiplier)}`;
    return `— transmis en entier avec ${b.name} (${cost})`;
  }

  /** Réécrit la mention ambiguë avec le chemin du document choisi (research.md R2). */
  protected use(m: MentionState, c: MentionDocument): void {
    const text = this.text() ?? '';
    const at = text.indexOf(m.text);
    if (at < 0) {
      return;
    }
    this.replaced.emit(text.slice(0, at) + c.insert + text.slice(at + m.text.length));
  }
}
