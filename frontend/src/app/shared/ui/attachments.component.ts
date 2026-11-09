import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { KnowledgeApi } from '../../core/projects/knowledge.api';
import { MentionField, MentionState } from '../../core/projects/projects.model';
import { isProblem } from '../../core/http/problem';
import { AttachmentOwner, AttachmentView, AttachmentsApi } from '../../core/sessions/attachments.api';
import { MENTION_DELAYS } from './mention.directive';
import { betterModelLabel, mentionLabel } from './mention-status.component';

export const MAX_ATTACHMENTS = 5;
const ACCEPT = '.docx,.xlsx,.pdf,.html,.htm';
const NUMBER = new Intl.NumberFormat('fr-FR');

/**
 * Documents joints à une spécification (009, research.md R7) : « Joindre un document » ouvre le
 * sélecteur de fichiers ; chaque document montre son titre, son nom, sa taille et son état de
 * transmission, comme une mention @. Sans session, ce sont des brouillons dont les identifiants sont
 * émis pour le démarrage ; avec une session, ses documents. Refus et états annoncés en `aria-live`.
 */
@Component({
  selector: 'sw-attachments',
  imports: [MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="attachments" aria-label="Documents joints">
      <input #picker type="file" class="picker" [accept]="accept" tabindex="-1" aria-hidden="true" (change)="picked()" />
      <div class="bar">
        <button
          mat-stroked-button
          type="button"
          [disabled]="disabled() || busy() || items().length >= max"
          (click)="picker.click()"
        >
          {{ busy() ? 'Lecture du document…' : 'Joindre un document' }}
        </button>
        <span class="hint">Word, Excel, PDF ou HTML ; {{ max }} documents au plus. Seul le texte est gardé.</span>
      </div>
      <p class="live" aria-live="polite" role="status">{{ message() }}</p>
      @if (items().length) {
        <ul aria-label="Documents joints">
          @for (a of items(); track a.id) {
            @let s = state(a);
            <li [class]="s?.state ?? ''">
              <span class="title">{{ a.title }}</span>
              <span class="file">{{ a.fileName }}</span>
              <span class="size">{{ size(a) }}</span>
              @if (s) {
                <span class="state">{{ label(s) }}</span>
                @if (s.betterModel; as better) {
                  <span class="better">{{ betterLabel(better) }}</span>
                }
              }
              <button
                mat-button
                type="button"
                [disabled]="disabled() || busy()"
                [attr.aria-label]="'Retirer ' + a.title"
                (click)="remove(a)"
              >
                Retirer
              </button>
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: `
    .attachments {
      margin: 0.5rem 0;
    }
    .picker {
      display: none;
    }
    .bar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.25rem 0.75rem;
    }
    .hint {
      color: var(--sw-ink-soft);
      font-size: 0.875rem;
    }
    .live {
      margin: 0.25rem 0 0;
      font-size: 0.875rem;
    }
    .live:empty {
      display: none;
    }
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
    .title {
      font-weight: 500;
    }
    .file,
    .size {
      opacity: 0.85;
    }
    .better {
      font-style: italic;
    }
    li.NO_ROOM .state {
      color: var(--mat-sys-error, #b3261e);
    }
  `,
})
export class AttachmentsComponent implements OnDestroy {
  readonly projectId = input<string | null>(null);
  /** Session démarrée : ses documents ; sinon, des brouillons. */
  readonly sessionId = input<string | null>(null);
  /** Conversation (010) : ses documents, lus par l'agent et repris dans la spécification. */
  readonly conversationId = input<string | null>(null);
  /** Champs en cours de saisie : leurs mentions passent avant ou après les documents joints. */
  readonly fields = input<{ field: MentionField; text: string }[]>([]);
  /** Feature implémentée : rien ne s'ajoute ni ne se retire. */
  readonly disabled = input(false);
  /** Identifiants des brouillons, à envoyer au démarrage. */
  readonly changed = output<string[]>();

  private readonly api = inject(AttachmentsApi);
  private readonly knowledge = inject(KnowledgeApi);
  private readonly delays = inject(MENTION_DELAYS);
  private readonly picker = viewChild.required<ElementRef<HTMLInputElement>>('picker');

  protected readonly accept = ACCEPT;
  protected readonly max = MAX_ATTACHMENTS;
  protected readonly items = signal<AttachmentView[]>([]);
  protected readonly states = signal<MentionState[]>([]);
  protected readonly busy = signal(false);
  protected readonly message = signal('');
  private timer: ReturnType<typeof setTimeout> | undefined;
  private owned = false;
  private request = 0;

  /** Session ou conversation qui porte les documents ; aucune : des brouillons. */
  private readonly owner = computed<[AttachmentOwner, string] | null>(() => {
    const sessionId = this.sessionId();
    const conversationId = this.conversationId();
    return sessionId ? ['sessions', sessionId] : conversationId ? ['conversations', conversationId] : null;
  });

  constructor() {
    effect(() => {
      const owner = this.owner();
      if (owner) {
        this.owned = true;
        void this.load(owner);
      } else if (this.owned) {
        // nouvelle conversation : plus de documents
        this.owned = false;
        untracked(() => this.items.set([]));
      }
    });
    effect(() => {
      const projectId = this.projectId();
      const sessionId = this.sessionId();
      const conversationId = this.conversationId();
      const fields = this.fields();
      const items = this.items();
      clearTimeout(this.timer);
      const request = ++this.request;
      if (!projectId || !items.length) {
        this.states.set([]);
        return;
      }
      const drafts = this.owner() ? [] : items.map((a) => a.id);
      this.timer = setTimeout(async () => {
        try {
          const states = await this.knowledge.resolveMentions(projectId, sessionId, fields, drafts, conversationId);
          if (request === this.request) {
            this.states.set(states.filter((s) => s.field === 'ATTACHMENT'));
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

  protected state(a: AttachmentView): MentionState | undefined {
    return this.states().find((s) => s.document?.path === a.fileName);
  }

  protected label(s: MentionState): string {
    return mentionLabel(s);
  }

  protected betterLabel(b: NonNullable<MentionState['betterModel']>): string {
    return betterModelLabel(b);
  }

  protected size(a: AttachmentView): string {
    return `${NUMBER.format(a.chars)} caractères`;
  }

  protected async picked(): Promise<void> {
    const input = this.picker().nativeElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (this.items().length >= MAX_ATTACHMENTS) {
      this.message.set(`${MAX_ATTACHMENTS} documents joints au plus : retirez-en un d'abord.`);
      return;
    }
    this.busy.set(true);
    this.message.set(`Lecture de « ${file.name} »…`);
    try {
      const owner = this.owner();
      const added = owner ? await this.api.add(owner[1], file, owner[0]) : await this.api.draft(file);
      this.items.update((list) => [...list, added]);
      this.message.set(`« ${added.title} » est joint.`);
      this.emit();
    } catch (e) {
      this.message.set(`« ${file.name} » n'est pas joint : ${reason(e)}`);
    } finally {
      this.busy.set(false);
    }
  }

  protected async remove(a: AttachmentView): Promise<void> {
    const owner = this.owner();
    try {
      if (owner) {
        await this.api.remove(owner[1], a.id, owner[0]);
      } else {
        await this.api.forgetDraft(a.id).catch(() => undefined);
      }
      this.items.update((list) => list.filter((x) => x.id !== a.id));
      this.message.set(`« ${a.title} » est retiré.`);
      this.emit();
    } catch (e) {
      this.message.set(`« ${a.title} » n'est pas retiré : ${reason(e)}`);
    }
  }

  private async load([owner, id]: [AttachmentOwner, string]): Promise<void> {
    try {
      this.items.set(await this.api.list(id, owner));
    } catch {
      this.items.set([]);
    }
  }

  private emit(): void {
    this.changed.emit(this.items().map((a) => a.id));
  }
}

function reason(e: unknown): string {
  if (e instanceof HttpErrorResponse && isProblem(e.error)) {
    return e.error.userMessage;
  }
  if (e instanceof HttpErrorResponse && e.status === 413) {
    return 'le fichier dépasse 20 Mo.';
  }
  return 'le document n’a pas pu être lu.';
}
