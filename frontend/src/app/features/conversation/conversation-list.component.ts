import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { ConversationsApi } from '../../core/conversations/conversations.api';
import { ConversationSummary } from '../../core/conversations/conversations.model';
import { isProblem } from '../../core/http/problem';

/**
 * Conversations du projet courant (010, US4), la plus récente d'abord : nouvelle, rouvrir, renommer,
 * supprimer après confirmation. Le panneau se replie sur les petits écrans.
 */
@Component({
  selector: 'sw-conversation-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, MatButtonModule, RouterLink],
  template: `
    <details class="threads" open>
      <summary><h2 id="threads-title">Conversations</h2></summary>
      <button mat-stroked-button type="button" (click)="created.emit()">Nouvelle conversation</button>
      @if (threads().length) {
        <ul aria-labelledby="threads-title">
          @for (t of threads(); track t.id) {
            <li [class.current]="t.id === currentId()">
              @if (editing() === t.id) {
                <form (ngSubmit)="rename(t)" class="edit">
                  <label [for]="'name-' + t.id" class="sw-visually-hidden">Nom de la conversation</label>
                  <input [id]="'name-' + t.id" name="name" [(ngModel)]="name" maxlength="120" autocomplete="off" (keydown.escape)="cancel()" />
                  <button mat-button type="submit" [disabled]="!name().trim()">Enregistrer</button>
                  <button mat-button type="button" (click)="cancel()">Annuler</button>
                </form>
              } @else if (confirming() === t.id) {
                <div class="confirm" role="group" [attr.aria-label]="'Supprimer ' + t.name">
                  <p>Supprimer « {{ t.name }} » ? Ses messages sont effacés ; les spécifications restent.</p>
                  <button mat-flat-button type="button" (click)="remove(t)">Supprimer</button>
                  <button mat-button type="button" (click)="cancel()">Annuler</button>
                </div>
              } @else {
                <a [routerLink]="['/conversations', t.id]" [attr.aria-current]="t.id === currentId() ? 'page' : null">
                  {{ t.name }}
                </a>
                @if (t.specification; as spec) {
                  <span class="about">
                    Spécification : {{ spec.name }}{{ spec.sessionId ? '' : ' (spécification supprimée)' }}
                  </span>
                }
                <span class="actions">
                  <button mat-button type="button" (click)="edit(t)">
                    Renommer<span class="sw-visually-hidden"> {{ t.name }}</span>
                  </button>
                  <button mat-button type="button" (click)="confirming.set(t.id)">
                    Supprimer<span class="sw-visually-hidden"> {{ t.name }}</span>
                  </button>
                </span>
              }
            </li>
          }
        </ul>
      } @else {
        <p class="hint">Aucune conversation dans ce projet.</p>
      }
      <p class="sw-visually-hidden" aria-live="polite">{{ message() }}</p>
      @if (problem()) {
        <p class="problem" role="alert">{{ problem() }}</p>
      }
    </details>
  `,
  styles: `
    summary {
      cursor: pointer;
      list-style-position: outside;
    }
    h2 {
      display: inline;
      margin: 0;
      font-size: var(--sw-step-0);
    }
    summary + button {
      margin-top: 0.75rem;
    }
    ul {
      list-style: none;
      margin: 1rem 0 0;
      padding: 0;
    }
    li {
      margin: 0 0 0.25rem;
    }
    li a {
      display: block;
      padding: 0.4rem 0.6rem;
      border-radius: var(--sw-radius-field);
      color: var(--sw-ink);
      text-decoration: none;
      overflow-wrap: anywhere;
    }
    li.current a {
      background: color-mix(in srgb, var(--sw-pencil) 12%, transparent);
      font-weight: 600;
    }
    .about {
      display: block;
      color: var(--sw-ink-soft);
      font-size: var(--sw-step--1);
    }
    .actions {
      display: flex;
      font-size: var(--sw-step--1);
    }
    input {
      box-sizing: border-box;
      width: 100%;
      padding: 0.4rem 0.5rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
      background: var(--sw-sheet);
      color: var(--sw-ink);
      font: inherit;
    }
    .confirm p {
      margin: 0.25rem 0;
      overflow-wrap: anywhere;
    }
    .hint {
      color: var(--sw-ink-soft);
    }
    .problem {
      padding: 0.5rem 0.75rem;
      border-left: 4px solid var(--mat-sys-error, #b3261e);
    }
  `,
})
export class ConversationListComponent {
  readonly threads = input<ConversationSummary[]>([]);
  readonly currentId = input<string | null>(null);
  readonly created = output<void>();
  /** Liste à relire ; `deleted` : la conversation supprimée. */
  readonly changed = output<{ deleted?: string }>();

  private readonly api = inject(ConversationsApi);

  protected readonly editing = signal<string | null>(null);
  protected readonly confirming = signal<string | null>(null);
  protected readonly name = signal('');
  protected readonly message = signal('');
  protected readonly problem = signal('');

  protected edit(t: ConversationSummary): void {
    this.confirming.set(null);
    this.name.set(t.name);
    this.editing.set(t.id);
    setTimeout(() => document.getElementById(`name-${t.id}`)?.focus());
  }

  protected cancel(): void {
    this.editing.set(null);
    this.confirming.set(null);
  }

  protected async rename(t: ConversationSummary): Promise<void> {
    this.problem.set('');
    try {
      const renamed = await this.api.rename(t.id, this.name().trim());
      this.editing.set(null);
      this.message.set(`Conversation renommée « ${renamed.name} ».`);
      this.changed.emit({});
    } catch (e) {
      this.problem.set(reason(e, 'La conversation n’a pas été renommée.'));
    }
  }

  protected async remove(t: ConversationSummary): Promise<void> {
    this.problem.set('');
    try {
      await this.api.delete(t.id);
      this.confirming.set(null);
      this.message.set(`« ${t.name} » est supprimée.`);
      this.changed.emit({ deleted: t.id });
    } catch (e) {
      this.problem.set(reason(e, 'La conversation n’a pas été supprimée.'));
    }
  }
}

function reason(e: unknown, fallback: string): string {
  return e instanceof HttpErrorResponse && isProblem(e.error) ? e.error.userMessage : fallback;
}
