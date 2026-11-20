import { LiveAnnouncer } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, HostListener, computed, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { InterviewsApi } from '../../core/interviews/interviews.api';
import { InterviewDetail, Outcome, PointInput, Stop, StopItem } from '../../core/interviews/interviews.model';
import { ConfirmData, ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { EditorKind, EditorValue, PointEditorComponent } from './point-editor.component';
import { announcement, block, findCursor, nextGroup, nextPending, prevGroup } from './walkthrough';

const ORIGINS: Record<string, string> = {
  ASSUMPTION: 'Hypothèse',
  SOURCE_DEVIATION: 'Écart entre sources',
  OPEN_QUESTION: 'Point ouvert',
  REMARK: 'Remarque',
  QUESTION: 'Question',
};

const OUTCOMES: Record<Outcome, string> = {
  CONFIRMED: 'confirmé',
  CHANGE: 'à modifier',
  REMOVE: 'à retirer',
  DEFERRED: 'reporté',
};

interface Editing {
  kind: EditorKind;
  index: number;
  item: StopItem | null;
}

/**
 * Écran de séance (021, US1, contracts/ui.md) : présentation pour un écran partagé, un élément à la
 * fois, points sur leur élément, saisie au clavier, enregistrement au fil de l'eau et reprise.
 */
@Component({
  selector: 'sw-interview-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, PointEditorComponent],
  template: `
    @if (detail(); as d) {
      @if (current(); as stop) {
        <div class="seance">
          <header class="bar">
            <h1>
              @if (d.specs.length > 1) {
                <span class="spec-pos">Spéc. {{ specPosition() }} sur {{ d.specs.length }}</span>
              }
              « {{ specName() }} »
            </h1>
            <p class="where">{{ stop.label }} · {{ index() + 1 }} sur {{ d.stops.length }}</p>
            <button mat-flat-button type="button" (click)="finish()">Terminer</button>
          </header>

          @if (newerVersion(); as v) {
            <p class="banner" role="status">Une version plus récente existe (v{{ v }}) : la révision partira d’elle.</p>
          }

          <ol class="block">
            @for (i of shown(); track i) {
              @let s = d.stops[i];
              <li [class.current]="i === index()" [attr.aria-current]="i === index() ? 'step' : null">
                <p class="element">
                  <span class="label">{{ s.label }}</span>
                  @if (s.text) {
                    <span class="text">{{ s.text }}</span>
                  }
                </p>
                @if (s.items.length) {
                  <ul class="items">
                    @for (item of s.items; track item.code ?? item.point?.id) {
                      <li [class.pending]="item.pending">
                        <span class="kind">{{ itemTitle(item) }}</span>
                        @if (item.statement) {
                          <span class="statement">{{ item.statement }}</span>
                        }
                        @if (item.point; as p) {
                          <span class="answer">
                            @if (p.outcome) {
                              <strong>{{ outcomeLabel(p.outcome) }}</strong>
                            }
                            @if (p.text) {
                              — {{ p.text }}
                            }
                            @if (p.owner) {
                              ({{ p.owner }})
                            }
                          </span>
                        }
                        @if (i === index() && !editing()) {
                          <button mat-stroked-button type="button" (click)="edit(item)">
                            {{ item.pending ? 'Répondre' : 'Modifier' }}
                            <span class="sr-only">{{ itemTitle(item) }}</span>
                          </button>
                        }
                      </li>
                    }
                  </ul>
                }
                @if (editing(); as e) {
                  @if (e.index === i) {
                    <sw-point-editor
                      [kind]="e.kind"
                      [heading]="editorHeading(e)"
                      [initial]="initial(e)"
                      (saved)="save(e, $event)"
                      (cancelled)="editing.set(null)"
                    />
                  }
                }
              </li>
            }
          </ol>

          <footer class="foot">
            <p class="progress">
              {{ d.counts.handled }} traité{{ d.counts.handled > 1 ? 's' : '' }} · {{ d.counts.notes }} note{{
                d.counts.notes > 1 ? 's' : ''
              }}
              · {{ d.counts.pending }} restant{{ d.counts.pending > 1 ? 's' : '' }}
            </p>
            <p class="keys" id="raccourcis">
              <kbd>↑</kbd> <kbd>↓</kbd> élément · <kbd>←</kbd> <kbd>→</kbd> bloc · <kbd>N</kbd> prochain point ·
              <kbd>R</kbd> remarque · <kbd>Q</kbd> question · <kbd>Entrée</kbd> répondre
            </p>
            <div class="nav">
              <button mat-button type="button" (click)="move(index() - 1)" [disabled]="index() === 0">Élément précédent</button>
              <button mat-button type="button" (click)="move(index() + 1)" [disabled]="index() === d.stops.length - 1">
                Élément suivant
              </button>
              <button mat-button type="button" (click)="startNote('REMARK')">Remarque</button>
              <button mat-button type="button" (click)="startNote('QUESTION')">Question</button>
              <button mat-button type="button" (click)="goNextPending()">Prochain point à traiter</button>
            </div>
          </footer>
        </div>
      }
    } @else if (failed()) {
      <p role="alert">{{ failed() }}</p>
    } @else {
      <p role="status">Chargement de la séance…</p>
    }
  `,
  styles: `
    :host {
      display: block;
      font-size: 1.25rem;
    }
    .seance {
      display: grid;
      gap: 1.5rem;
      max-width: 72rem;
      margin: 0 auto;
    }
    .bar {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.5rem 1.5rem;
      border-bottom: 1px solid var(--sw-rule);
      padding-bottom: 1rem;
    }
    h1 {
      margin: 0;
      font-size: var(--sw-step-2, 1.75rem);
      flex: 1 1 auto;
    }
    .spec-pos {
      display: block;
      font-size: 1rem;
      color: var(--sw-ink-soft);
    }
    .where {
      margin: 0;
      color: var(--sw-ink-soft);
    }
    .banner {
      margin: 0;
      padding: 0.75rem 1rem;
      border-left: 4px solid var(--sw-accent, currentColor);
      background: var(--sw-sheet);
    }
    .block {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 0.75rem;
    }
    .block > li {
      padding: 0.75rem 1rem;
      border-radius: var(--sw-radius-field);
      border: 2px solid transparent;
    }
    .block > li.current {
      border-color: var(--sw-accent, currentColor);
      background: var(--sw-sheet);
    }
    .element {
      margin: 0;
      display: flex;
      flex-wrap: wrap;
      gap: 0 0.75rem;
    }
    .label {
      font-weight: 600;
    }
    .items {
      margin: 0.5rem 0 0;
      padding-left: 1.25rem;
      display: grid;
      gap: 0.5rem;
    }
    .items li {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.25rem 0.75rem;
    }
    .kind {
      font-weight: 600;
    }
    .pending .kind::before {
      content: '⚠ ';
    }
    .foot {
      display: grid;
      gap: 0.5rem;
      border-top: 1px solid var(--sw-rule);
      padding-top: 1rem;
      font-size: 1rem;
    }
    .progress,
    .keys {
      margin: 0;
    }
    .nav {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    kbd {
      font-family: inherit;
      border: 1px solid var(--sw-rule);
      border-radius: 4px;
      padding: 0 0.3rem;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
    }
  `,
})
export class InterviewPage {
  /** Identifiant de la séance (paramètre de route). */
  readonly id = input.required<string>();

  private readonly api = inject(InterviewsApi);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  protected readonly detail = signal<InterviewDetail | null>(null);
  protected readonly failed = signal<string | null>(null);
  protected readonly index = signal(0);
  protected readonly editing = signal<Editing | null>(null);

  protected readonly current = computed<Stop | null>(() => this.detail()?.stops[this.index()] ?? null);
  protected readonly shown = computed(() => {
    const d = this.detail();
    return d ? block(d.stops, this.index()) : [];
  });
  protected readonly specPosition = computed(() => {
    const d = this.detail();
    const stop = this.current();
    return d && stop ? d.specs.findIndex((s) => s.sessionId === stop.sessionId) + 1 : 1;
  });
  protected readonly specName = computed(() => {
    const stop = this.current();
    return this.detail()?.specs.find((s) => s.sessionId === stop?.sessionId)?.name ?? '';
  });
  protected readonly newerVersion = computed(() => {
    const stop = this.current();
    const spec = this.detail()?.specs.find((s) => s.sessionId === stop?.sessionId);
    return spec && spec.version !== null && spec.latestVersion > spec.version ? spec.latestVersion : null;
  });

  constructor() {
    queueMicrotask(() => void this.load(true));
  }

  private async load(restoreCursor: boolean): Promise<void> {
    try {
      const d = await this.api.get(this.id());
      if (d.state === 'FINISHED') {
        await this.router.navigate(['/seances', d.id, 'recapitulatif']);
        return;
      }
      const keep = this.current();
      this.detail.set(d);
      if (restoreCursor) {
        this.index.set(findCursor(d.stops, d.cursor));
        this.announce();
      } else if (keep) {
        this.index.set(findCursor(d.stops, { sessionId: keep.sessionId, anchor: keep.anchor }));
      }
    } catch {
      this.failed.set('La séance n’a pas pu être chargée. Rechargez la page.');
    }
  }

  @HostListener('document:keydown', ['$event'])
  protected onKey(event: KeyboardEvent): void {
    const target = event.target instanceof HTMLElement ? event.target : null;
    if (this.editing() || !this.detail() || event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }
    if (target && (target.closest('input, textarea, select, [contenteditable="true"]') || target.closest('mat-dialog-container'))) {
      return;
    }
    const stops = this.detail()!.stops;
    const handled = (() => {
      switch (event.key) {
        case 'ArrowDown':
          return this.move(this.index() + 1);
        case 'ArrowUp':
          return this.move(this.index() - 1);
        case 'ArrowRight':
          return this.move(nextGroup(stops, this.index()));
        case 'ArrowLeft':
          return this.move(prevGroup(stops, this.index()));
        case 'n':
        case 'N':
          return this.goNextPending();
        case 'r':
        case 'R':
          return this.startNote('REMARK');
        case 'q':
        case 'Q':
          return this.startNote('QUESTION');
        case 'Enter': {
          const item = this.current()?.items.find((i) => i.pending) ?? this.current()?.items[0];
          return item ? this.edit(item) : false;
        }
        default:
          return false;
      }
    })();
    if (handled) {
      event.preventDefault();
    }
  }

  protected move(to: number): boolean {
    const d = this.detail();
    if (!d || to < 0 || to >= d.stops.length || to === this.index()) {
      return false;
    }
    this.index.set(to);
    this.editing.set(null);
    this.announce();
    const stop = d.stops[to];
    void this.api.moveCursor(d.id, stop.sessionId, stop.anchor).catch(() => undefined);
    return true;
  }

  protected goNextPending(): boolean {
    const d = this.detail();
    const next = d ? nextPending(d.stops, this.index()) : null;
    if (next === null) {
      this.announcer.announce('Plus aucun point à traiter après cet élément.', 'polite');
      return true;
    }
    return this.move(next);
  }

  protected startNote(kind: 'REMARK' | 'QUESTION'): boolean {
    this.editing.set({ kind, index: this.index(), item: null });
    return true;
  }

  protected edit(item: StopItem): boolean {
    const kind: EditorKind =
      item.origin === 'REMARK' ? 'REMARK' : item.origin === 'QUESTION' ? 'QUESTION' : 'SPEC';
    this.editing.set({ kind, index: this.index(), item });
    return true;
  }

  protected itemTitle(item: StopItem): string {
    return `${ORIGINS[item.origin]}${item.code ? ' ' + item.code : ''}`;
  }

  protected outcomeLabel(o: Outcome): string {
    return OUTCOMES[o];
  }

  protected editorHeading(e: Editing): string {
    if (e.item) {
      return `${this.itemTitle(e.item)}${e.item.statement ? ' : ' + e.item.statement : ''}`;
    }
    const stop = this.detail()!.stops[e.index];
    return `${e.kind === 'QUESTION' ? 'Question' : 'Remarque'} sur ${stop.label}`;
  }

  protected initial(e: Editing): EditorValue | null {
    const p = e.item?.point;
    return p ? { text: p.text, outcome: p.outcome, owner: p.owner, due: p.due } : null;
  }

  protected async save(e: Editing, value: EditorValue): Promise<void> {
    const d = this.detail()!;
    const stop = d.stops[e.index];
    const anchor = stop.kind === 'UNANCHORED' ? (e.item?.point?.anchor ?? null) : stop.anchor;
    const input: PointInput = {
      sessionId: stop.sessionId,
      anchor,
      origin: e.item ? e.item.origin : e.kind === 'QUESTION' ? 'QUESTION' : 'REMARK',
      originCode: e.item?.code ?? null,
      text: value.text,
      outcome: value.outcome,
      owner: value.owner,
      due: value.due,
      source: e.item?.point?.source ?? null,
    };
    try {
      const pointId = e.item?.point?.id;
      if (pointId) {
        await this.api.updatePoint(d.id, pointId, input);
      } else {
        await this.api.addPoint(d.id, input);
      }
      this.editing.set(null);
      this.announcer.announce('Enregistré.', 'polite');
      await this.load(false);
    } catch {
      this.announcer.announce('Le point n’a pas été enregistré. Réessayez.', 'assertive');
    }
  }

  protected async finish(): Promise<void> {
    const d = this.detail();
    if (!d) {
      return;
    }
    if (d.counts.pending > 0) {
      const data: ConfirmData = {
        title: 'Terminer la séance ?',
        message:
          `${d.counts.pending} point${d.counts.pending > 1 ? 's restent' : ' reste'} à traiter. ` +
          'Ils resteront ouverts dans leur spécification.',
        confirm: 'Terminer la séance',
      };
      const confirmed = await firstValueFrom(this.dialog.open(ConfirmDialogComponent, { data }).afterClosed());
      if (confirmed !== true) {
        return;
      }
    }
    try {
      await this.api.finish(d.id);
      await this.router.navigate(['/seances', d.id, 'recapitulatif']);
    } catch {
      this.announcer.announce('La séance n’a pas pu être terminée. Réessayez.', 'assertive');
    }
  }

  private announce(): void {
    const stop = this.current();
    if (stop) {
      this.announcer.announce(announcement(stop), 'polite');
    }
  }
}
