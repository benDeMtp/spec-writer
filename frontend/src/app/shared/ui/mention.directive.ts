import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import {
  ChangeDetectionStrategy,
  Component,
  ComponentRef,
  Directive,
  ElementRef,
  InjectionToken,
  OnDestroy,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { KnowledgeApi } from '../../core/projects/knowledge.api';
import { MentionKind, MentionSuggestion } from '../../core/projects/projects.model';

/** Délais de la frappe, en millisecondes : suggestions et état des mentions (research.md R4, R8). */
export const MENTION_DELAYS = new InjectionToken<{ suggest: number; resolve: number }>('MENTION_DELAYS', {
  factory: () => ({ suggest: 150, resolve: 500 }),
});

const OPENING = '([«"\'“‘';
const MAX_QUERY = 60;

/**
 * Mention en cours avant le curseur (research.md R1) : un @ en début de texte ou après un blanc ou
 * une ponctuation ouvrante, suivi d'au moins un caractère, sans retour à la ligne. Null sinon.
 */
export function mentionAt(text: string, caret: number): { start: number; query: string } | null {
  const at = text.lastIndexOf('@', caret - 1);
  if (at < 0) {
    return null;
  }
  const before = at === 0 ? ' ' : text.charAt(at - 1);
  if (!/\s/.test(before) && !OPENING.includes(before)) {
    return null;
  }
  const query = text.slice(at + 1, caret);
  if (!query || /^\s/.test(query) || query.includes('\n') || query.length > MAX_QUERY) {
    return null;
  }
  return { start: at, query };
}

const KIND_LABELS: Record<MentionKind, string> = { DOCUMENT: 'Document', PAGE: 'Page de documentation', ADR: 'ADR' };

let nextId = 0;

/** Liste des suggestions, affichée sous la zone de texte. */
@Component({
  selector: 'sw-mention-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mention-list mat-elevation-z4">
      @if (items().length) {
        <ul role="listbox" [id]="listId()" aria-label="Documents à mentionner">
          @for (s of items(); track s.path; let i = $index) {
            <li
              role="option"
              [id]="listId() + '-' + i"
              [attr.aria-selected]="i === active()"
              [class.active]="i === active()"
              (mousedown)="$event.preventDefault(); chosen.emit(i)"
            >
              <span class="name">{{ s.name }}</span>
              <span class="kind">{{ kind(s.kind) }}</span>
              <span class="path">{{ s.path }}</span>
            </li>
          }
        </ul>
      } @else {
        <p class="empty" [id]="listId()">Aucun document ne correspond.</p>
      }
    </div>
  `,
  styles: `
    .mention-list {
      background: var(--mat-sys-surface-container, #fff);
      border-radius: 4px;
      max-height: 18rem;
      overflow: auto;
    }
    ul {
      list-style: none;
      margin: 0;
      padding: 0.25rem 0;
    }
    li {
      cursor: pointer;
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 0 0.75rem;
      padding: 0.35rem 0.75rem;
    }
    li.active {
      background: var(--mat-sys-secondary-container, #e8def8);
    }
    .kind {
      font-size: 0.8rem;
    }
    .path {
      font-size: 0.8rem;
      grid-column: 1 / -1;
      opacity: 0.8;
      overflow-wrap: anywhere;
    }
    .empty {
      margin: 0;
      padding: 0.5rem 0.75rem;
    }
  `,
})
export class MentionListComponent {
  readonly items = input<MentionSuggestion[]>([]);
  readonly active = input(0);
  readonly listId = input.required<string>();
  readonly chosen = output<number>();

  protected kind(k: MentionKind): string {
    return KIND_LABELS[k];
  }
}

/**
 * Autocomplétion des mentions @ sur une zone de texte (007, research.md R4) : la liste des
 * documents du projet s'ouvre sous le champ après @ et quelques lettres ; flèches pour choisir,
 * Entrée ou Tab pour insérer, Échap pour fermer sans rien changer. La zone de texte reste un champ
 * de texte : elle porte aria-autocomplete, aria-controls et aria-activedescendant, et le nombre de
 * suggestions est annoncé.
 */
@Directive({
  selector: 'textarea[swMention]',
  host: {
    'aria-autocomplete': 'list',
    '[attr.aria-controls]': 'open() ? listId : null',
    '[attr.aria-activedescendant]': 'open() && items().length ? listId + "-" + active() : null',
    '(input)': 'onInput()',
    '(keydown)': 'onKeydown($event)',
    '(blur)': 'close()',
  },
})
export class MentionDirective implements OnDestroy {
  /** Projet dont l'index fournit les suggestions ; sans projet, aucune liste. */
  readonly swMention = input<string | null>(null);

  private readonly host = inject<ElementRef<HTMLTextAreaElement>>(ElementRef);
  private readonly overlay = inject(Overlay);
  private readonly api = inject(KnowledgeApi);
  private readonly delays = inject(MENTION_DELAYS);

  protected readonly listId = `sw-mention-${nextId++}`;
  protected readonly open = signal(false);
  protected readonly items = signal<MentionSuggestion[]>([]);
  protected readonly active = signal(0);
  private start = -1;
  /** Saisie provoquée par l'insertion d'une suggestion : elle ne rouvre pas la liste. */
  private inserting = false;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private request = 0;
  private overlayRef: OverlayRef | null = null;
  private list: ComponentRef<MentionListComponent> | null = null;
  private live: HTMLElement | null = null;

  protected onInput(): void {
    if (this.inserting) {
      return;
    }
    const area = this.host.nativeElement;
    const projectId = this.swMention();
    const found = projectId ? mentionAt(area.value, area.selectionStart ?? area.value.length) : null;
    clearTimeout(this.timer);
    if (!found || !projectId) {
      this.close();
      return;
    }
    this.start = found.start;
    const request = ++this.request;
    this.timer = setTimeout(async () => {
      try {
        const items = await this.api.suggestMentions(projectId, found.query);
        if (request === this.request) {
          this.show(items);
        }
      } catch {
        this.close();
      }
    }, this.delays.suggest);
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (!this.open()) {
      return;
    }
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.move(1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.move(-1);
        break;
      case 'Enter':
      case 'Tab':
        if (this.items().length) {
          event.preventDefault();
          this.choose(this.active());
        }
        break;
      case 'Escape':
        event.preventDefault();
        event.stopPropagation();
        this.close();
        break;
    }
  }

  close(): void {
    clearTimeout(this.timer);
    this.request++;
    this.open.set(false);
    this.items.set([]);
    this.list = null;
    this.overlayRef?.detach();
    this.announce('');
  }

  ngOnDestroy(): void {
    this.close();
    this.overlayRef?.dispose();
    this.live?.remove();
  }

  private show(items: MentionSuggestion[]): void {
    this.items.set(items);
    this.active.set(0);
    this.open.set(true);
    if (!this.overlayRef) {
      this.overlayRef = this.overlay.create({
        positionStrategy: this.overlay
          .position()
          .flexibleConnectedTo(this.host)
          .withPositions([
            { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top' },
            { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom' },
          ]),
        scrollStrategy: this.overlay.scrollStrategies.reposition(),
      });
    }
    this.overlayRef.updateSize({ width: this.host.nativeElement.getBoundingClientRect().width || undefined });
    if (!this.list) {
      this.list = this.overlayRef.attach(new ComponentPortal(MentionListComponent));
      this.list.setInput('listId', this.listId);
      this.list.instance.chosen.subscribe((i) => this.choose(i));
    }
    this.refresh();
    this.announce(
      items.length
        ? `${items.length} document${items.length > 1 ? 's' : ''} proposé${items.length > 1 ? 's' : ''}, flèches pour choisir, Entrée pour insérer.`
        : 'Aucun document ne correspond.',
    );
  }

  private move(step: number): void {
    const count = this.items().length;
    if (!count) {
      return;
    }
    this.active.set((this.active() + step + count) % count);
    this.refresh();
  }

  private refresh(): void {
    this.list?.setInput('items', this.items());
    this.list?.setInput('active', this.active());
    this.list?.changeDetectorRef.detectChanges();
  }

  /** Remplace la mention en cours par la suggestion, suivie d'un espace, et replace le curseur. */
  private choose(index: number): void {
    const area = this.host.nativeElement;
    const item = this.items()[index];
    if (!item || this.start < 0) {
      return;
    }
    const caret = area.selectionStart ?? area.value.length;
    const before = area.value.slice(0, this.start);
    const after = area.value.slice(caret);
    const inserted = `${item.insert} `;
    area.value = before + inserted + after;
    const position = before.length + inserted.length;
    area.setSelectionRange(position, position);
    this.close();
    this.inserting = true;
    try {
      area.dispatchEvent(new Event('input', { bubbles: true }));
    } finally {
      this.inserting = false;
    }
    area.focus();
  }

  /** Région polie, hors de la liste, pour annoncer le nombre de suggestions. */
  private announce(message: string): void {
    if (!this.live) {
      this.live = document.createElement('div');
      this.live.setAttribute('aria-live', 'polite');
      this.live.className = 'cdk-visually-hidden';
      document.body.appendChild(this.live);
    }
    this.live.textContent = message;
  }
}
