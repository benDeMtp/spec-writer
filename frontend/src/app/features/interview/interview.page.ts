import { LiveAnnouncer } from '@angular/cdk/a11y';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  afterRenderEffect,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { InterviewsApi } from '../../core/interviews/interviews.api';
import { InterviewDetail, Outcome, PointInput, Stop, StopItem } from '../../core/interviews/interviews.model';
import { Language, Specification } from '../../core/sessions/sessions.model';
import { SessionsApi } from '../../core/sessions/sessions.api';
import { ConfirmData, ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { InterviewDocumentComponent, anchorKey } from './interview-document.component';
import { PointCardComponent } from './point-card.component';
import { EditorValue, PointEditorComponent } from './point-editor.component';
import { passageOf } from './selection';

/** Passage ou élément visé par « Noter » : clé d'ancre, et texte cité s'il y a une sélection. */
interface Target {
  key: string;
  quote: string | null;
}

type NoteAction = 'COMMENT' | 'REPLACE' | 'QUESTION';

interface Note extends Target {
  action: NoteAction;
}

/**
 * Écran de séance (021, conception du 20/11/2026) : la spécification déroulée comme un document,
 * pour un écran partagé ; chaque point en carte sous son élément, l'hypothèse comme réponse
 * proposée ; un passage sélectionné se commente, se remplace ou se supprime. Tout est enregistré au
 * fil de l'eau, et la séance reprend au dernier point consulté.
 */
@Component({
  selector: 'sw-interview-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, InterviewDocumentComponent, PointCardComponent, PointEditorComponent],
  template: `
    @if (detail(); as d) {
      <div class="seance">
        <header class="bar">
          <h1>
            @if (d.specs.length > 1) {
              <span class="spec-pos">Spéc. {{ specIndex() + 1 }} sur {{ d.specs.length }}</span>
            }
            « {{ specName() }} »
          </h1>
          <p class="progress" aria-live="polite">
            {{ d.counts.handled }} traité{{ d.counts.handled > 1 ? 's' : '' }} · {{ d.counts.notes }} note{{
              d.counts.notes > 1 ? 's' : ''
            }}
            · {{ d.counts.pending }} à traiter
          </p>
          <button mat-stroked-button type="button" (click)="goNextPending()">Prochain point à traiter</button>
          <button mat-flat-button type="button" (click)="finish()">Terminer</button>
        </header>

        @if (newerVersion(); as v) {
          <p class="banner" role="status">Une version plus récente existe (v{{ v }}) : la révision partira d’elle.</p>
        }

        <p class="keys">
          Sélectionnez un passage pour le commenter, le remplacer ou le supprimer ·
          <kbd>↑</kbd> <kbd>↓</kbd> point précédent ou suivant
        </p>

        <div class="paper" #paper>
          @if (spec(); as s) {
            <sw-interview-document [spec]="s" [stops]="stops()" [slot]="slot" [language]="language()" />
          } @else if (specFailed()) {
            <p role="alert">La spécification n’a pas pu être chargée. Rechargez la page.</p>
          } @else {
            <p role="status">Chargement de la spécification…</p>
          }
        </div>

        <ng-template #slot let-stop>
          @let key = keyOf(stop);
          @for (item of stop.items; track item.code ?? item.point?.id) {
            <sw-point-card
              [item]="item"
              (saved)="save(stop, item, $event)"
              (deleted)="deletePoint(stop, item)"
              (focusin)="track(stop)"
            />
          }
          @if (note(); as n) {
            @if (n.key === key) {
              <div data-note class="note">
                <sw-point-editor
                  [kind]="n.action === 'QUESTION' ? 'QUESTION' : 'REMARK'"
                  [heading]="noteHeading(n, stop)"
                  [initial]="noteInitial(n, stop)"
                  (saved)="saveNote(stop, n, $event)"
                  (cancelled)="note.set(null)"
                />
              </div>
            }
          }
          @if (!note() || note()?.key !== key) {
            @if (picked()?.key === key) {
              <div data-note class="toolbar" role="toolbar" [attr.aria-label]="toolbarLabel()" (mousedown)="$event.preventDefault()">
                <button mat-stroked-button type="button" (click)="startNote('COMMENT')">Commenter</button>
                <button mat-stroked-button type="button" (click)="startNote('REPLACE')">Remplacer</button>
                <button mat-stroked-button type="button" (click)="remove(stop)">Supprimer</button>
                <button mat-button type="button" (click)="startNote('QUESTION')">Question…</button>
                <button mat-button type="button" (click)="picked.set(null)">Fermer</button>
              </div>
            } @else if (stop.kind !== 'UNANCHORED') {
              <div data-note class="note-entry">
                <button mat-button type="button" class="note-btn" (click)="pickElement(key)">
                  Noter<span class="sr-only"> sur {{ stop.label }}</span>
                </button>
              </div>
            }
          }
        </ng-template>

        @if (d.specs.length > 1) {
          <nav class="specs" aria-label="Spécifications de la séance">
            <button mat-button type="button" (click)="showSpec(specIndex() - 1)" [disabled]="specIndex() === 0">
              Spécification précédente
            </button>
            <button mat-flat-button type="button" (click)="showSpec(specIndex() + 1)" [disabled]="specIndex() === d.specs.length - 1">
              Spécification suivante
            </button>
          </nav>
        }
      </div>
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
      gap: 1rem;
      max-width: 60rem;
      margin: 0 auto;
    }
    .bar {
      position: sticky;
      top: 0;
      z-index: 2;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem 1rem;
      padding: 0.75rem 0;
      border-bottom: 1px solid var(--sw-rule);
      background: var(--sw-paper);
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
    .progress,
    .keys {
      margin: 0;
      color: var(--sw-ink-soft);
      font-size: 1rem;
    }
    .banner {
      margin: 0;
      padding: 0.75rem 1rem;
      border-left: 4px solid var(--sw-outcome);
      background: var(--sw-sheet);
    }
    .paper {
      padding: 1.5rem 2rem;
      border-radius: var(--sw-radius-sheet);
      background: var(--sw-sheet);
      font-family: var(--sw-font-doc);
    }
    .toolbar,
    .note-entry,
    .note {
      font-family: var(--sw-font-ui);
    }
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin: 0.5rem 0;
      padding: 0.5rem;
      border: 2px solid var(--sw-pencil);
      border-radius: var(--sw-radius-field);
    }
    .note-entry {
      display: flex;
      justify-content: flex-end;
    }
    .note-btn {
      color: var(--sw-ink-soft);
      font-size: 0.9rem;
    }
    :host ::ng-deep .el:hover > .note-entry .note-btn,
    .note-btn:focus-visible {
      color: var(--sw-pencil);
    }
    .specs {
      display: flex;
      justify-content: space-between;
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
  private readonly sessions = inject(SessionsApi);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly paper = viewChild<ElementRef<HTMLElement>>('paper');

  protected readonly detail = signal<InterviewDetail | null>(null);
  protected readonly failed = signal<string | null>(null);
  protected readonly specIndex = signal(0);
  protected readonly spec = signal<Specification | null>(null);
  protected readonly specFailed = signal(false);
  protected readonly language = signal<Language>('fr');
  /** Passage sélectionné (ou élément entier par « Noter ») : la barre d'actions s'ouvre dessous. */
  protected readonly picked = signal<Target | null>(null);
  protected readonly note = signal<Note | null>(null);

  /** Clé d'ancre de la carte à focaliser au prochain rendu (reprise, saut, enregistrement). */
  private focusKey: string | null = null;
  private lastTracked: string | null = null;
  private readonly specs = new Map<string, Specification>();

  protected readonly currentSpec = computed(() => this.detail()?.specs[this.specIndex()] ?? null);
  protected readonly specName = computed(() => this.currentSpec()?.name ?? '');
  protected readonly stops = computed<Stop[]>(() => {
    const sid = this.currentSpec()?.sessionId;
    return this.detail()?.stops.filter((s) => s.sessionId === sid) ?? [];
  });
  protected readonly newerVersion = computed(() => {
    const spec = this.currentSpec();
    return spec && spec.version !== null && spec.latestVersion > spec.version ? spec.latestVersion : null;
  });
  protected readonly toolbarLabel = computed(() => {
    const q = this.picked()?.quote;
    return q ? `Noter sur « ${q.length > 60 ? q.slice(0, 60) + '…' : q} »` : 'Noter sur cet élément';
  });

  constructor() {
    queueMicrotask(() => void this.load(true));
    afterRenderEffect(() => {
      this.stops();
      this.spec();
      this.paintQuotes();
      this.focusPending();
    });
  }

  private async load(restoreCursor: boolean): Promise<void> {
    try {
      const d = await this.api.get(this.id());
      if (d.state === 'FINISHED') {
        await this.router.navigate(['/seances', d.id, 'recapitulatif']);
        return;
      }
      this.detail.set(d);
      if (restoreCursor) {
        const at = d.cursor ? d.specs.findIndex((s) => s.sessionId === d.cursor!.sessionId) : -1;
        this.focusKey = d.cursor?.anchor ? anchorKey(d.cursor.anchor) : null;
        await this.showSpec(Math.max(at, 0), false);
      }
    } catch {
      this.failed.set('La séance n’a pas pu être chargée. Rechargez la page.');
    }
  }

  protected async showSpec(index: number, announce = true): Promise<void> {
    const d = this.detail();
    const view = d?.specs[index];
    if (!d || !view) {
      return;
    }
    this.specIndex.set(index);
    this.picked.set(null);
    this.note.set(null);
    this.specFailed.set(false);
    const n = view.version ?? view.latestVersion;
    const cacheKey = `${view.sessionId}#${n}`;
    let spec = this.specs.get(cacheKey) ?? null;
    if (!spec) {
      this.spec.set(null);
      try {
        const [version, session] = await Promise.all([
          this.sessions.version(view.sessionId, n),
          this.sessions.get(view.sessionId).catch(() => null),
        ]);
        spec = version;
        this.specs.set(cacheKey, spec);
        this.language.set(session?.language ?? 'fr');
      } catch {
        this.specFailed.set(true);
        return;
      }
    }
    this.spec.set(spec);
    if (announce) {
      this.announcer.announce(`Spécification ${index + 1} sur ${d.specs.length} : ${view.name}`, 'polite');
    }
  }

  // --- Navigation entre les cartes ------------------------------------------------------------

  @HostListener('document:keydown', ['$event'])
  protected onKey(event: KeyboardEvent): void {
    const target = event.target instanceof HTMLElement ? event.target : null;
    if (!this.detail() || event.ctrlKey || event.metaKey || event.altKey || event.shiftKey) {
      return;
    }
    if (target?.closest('input, textarea, select, [contenteditable="true"], mat-dialog-container, [role="radiogroup"]')) {
      return;
    }
    if (event.key === 'Escape' && this.picked()) {
      this.picked.set(null);
      event.preventDefault();
      return;
    }
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') {
      return;
    }
    const cards = this.cards();
    if (!cards.length) {
      return;
    }
    const at = cards.findIndex((c) => c.contains(document.activeElement));
    const next = event.key === 'ArrowDown' ? (at < 0 ? 0 : Math.min(at + 1, cards.length - 1)) : Math.max(at - 1, 0);
    this.focusCard(cards[next]);
    event.preventDefault();
  }

  protected goNextPending(): void {
    const cards = this.cards();
    const at = cards.findIndex((c) => c.contains(document.activeElement));
    const next = cards.slice(at + 1).find((c) => c.classList.contains('pending')) ?? cards.find((c) => c.classList.contains('pending'));
    if (next) {
      this.focusCard(next);
      return;
    }
    const d = this.detail();
    const other = d?.specs.findIndex((s, i) => i !== this.specIndex() && d.stops.some((st) => st.sessionId === s.sessionId && st.items.some((it) => it.pending)));
    if (d && other !== undefined && other >= 0) {
      this.focusKey = '*pending';
      void this.showSpec(other);
      return;
    }
    this.announcer.announce('Plus aucun point à traiter.', 'polite');
  }

  /** Le curseur suit la dernière carte consultée : la séance reprend à cet endroit. */
  protected track(stop: Stop): void {
    const key = `${stop.sessionId}|${this.keyOf(stop)}`;
    if (key === this.lastTracked) {
      return;
    }
    this.lastTracked = key;
    void this.api.moveCursor(this.detail()!.id, stop.sessionId, stop.anchor).catch(() => undefined);
  }

  private cards(): HTMLElement[] {
    return Array.from(this.paper()?.nativeElement.querySelectorAll<HTMLElement>('sw-point-card') ?? []);
  }

  /**
   * Navigation (↑ ↓, prochain point) : la carte vient au centre. Après un enregistrement ou à la
   * reprise, elle reste où elle est si elle est visible : le texte ne bouge pas sous la souris.
   */
  private focusCard(card: HTMLElement, navigation = true): void {
    card.focus({ preventScroll: true });
    card.scrollIntoView?.(navigation ? { block: 'center', behavior: 'smooth' } : { block: 'nearest' });
  }

  private focusPending(): void {
    const key = this.focusKey;
    const root = this.paper()?.nativeElement;
    if (!key || !root || !this.spec()) {
      return;
    }
    this.focusKey = null;
    const card =
      key === '*pending'
        ? root.querySelector<HTMLElement>('sw-point-card.pending')
        : root.querySelector<HTMLElement>(`[data-anchor="${cssEscape(key)}"] sw-point-card`);
    if (card) {
      this.focusCard(card, key === '*pending');
    }
  }

  // --- Sélection d'un passage -----------------------------------------------------------------

  @HostListener('mouseup', ['$event'])
  @HostListener('keyup', ['$event'])
  protected pickSelection(event: Event): void {
    const target = event.target instanceof HTMLElement ? event.target : null;
    const root = this.paper()?.nativeElement;
    // une sélection tirée à la souris finit souvent sur la carte d'une remarque déjà notée : c'est la
    // sélection qui compte, pas l'endroit où le bouton est relâché ; un clic sur une commande ne sélectionne rien
    if (!root || !target || !root.contains(target) || target.closest('[data-note], button, textarea, input')) {
      return;
    }
    const passage = passageOf(root, root.ownerDocument.getSelection());
    if (passage) {
      this.note.set(null);
      this.picked.set(passage);
    }
  }

  protected pickElement(key: string): void {
    this.note.set(null);
    this.picked.set({ key, quote: null });
  }

  protected startNote(action: NoteAction): void {
    const p = this.picked();
    if (p) {
      this.note.set({ ...p, action });
      this.picked.set(null);
      this.clearSelection();
    }
  }

  protected noteHeading(n: Note, stop: Stop): string {
    const on = n.quote ? `« ${n.quote} »` : stop.label;
    switch (n.action) {
      case 'REPLACE':
        return `Remplacer ${on}`;
      case 'QUESTION':
        return `Question sur ${on}`;
      default:
        return `Remarque sur ${on}`;
    }
  }

  protected noteInitial(n: Note, stop: Stop): EditorValue | null {
    if (n.action === 'REPLACE') {
      return { text: n.quote ?? stop.text, outcome: 'CHANGE', owner: null, due: null };
    }
    return null;
  }

  protected async saveNote(stop: Stop, n: Note, value: EditorValue): Promise<void> {
    const origin = n.action === 'QUESTION' ? 'QUESTION' : 'REMARK';
    if (await this.persist(stop, null, origin, value, n.quote)) {
      this.note.set(null);
    }
  }

  protected async remove(stop: Stop): Promise<void> {
    const p = this.picked();
    if (await this.persist(stop, null, 'REMARK', { text: '', outcome: 'REMOVE', owner: null, due: null }, p?.quote ?? null)) {
      this.picked.set(null);
      this.clearSelection();
    }
  }

  /** Une sélection qui a servi ne rouvre pas la barre au clic suivant. */
  private clearSelection(): void {
    this.paper()?.nativeElement.ownerDocument.getSelection()?.removeAllRanges();
  }

  // --- Enregistrement -------------------------------------------------------------------------

  protected keyOf(stop: Stop): string {
    return anchorKey(stop.anchor);
  }

  protected async save(stop: Stop, item: StopItem, value: EditorValue): Promise<void> {
    await this.persist(stop, item, item.origin, value, item.point?.quote ?? null);
  }

  protected async deletePoint(stop: Stop, item: StopItem): Promise<void> {
    const d = this.detail()!;
    const pointId = item.point?.id;
    if (!pointId) {
      return;
    }
    try {
      await this.api.deletePoint(d.id, pointId);
      this.announcer.announce(`${item.origin === 'QUESTION' ? 'Question supprimée' : 'Remarque supprimée'}.`, 'polite');
      this.focusKey = this.keyOf(stop);
      await this.load(false);
    } catch {
      this.announcer.announce('Le point n’a pas été supprimé. Réessayez.', 'assertive');
    }
  }

  private async persist(
    stop: Stop,
    item: StopItem | null,
    origin: PointInput['origin'],
    value: EditorValue,
    quote: string | null,
  ): Promise<boolean> {
    const d = this.detail()!;
    const anchor = stop.kind === 'UNANCHORED' ? (item?.point?.anchor ?? null) : stop.anchor;
    const input: PointInput = {
      sessionId: stop.sessionId,
      anchor,
      origin,
      originCode: item?.code ?? null,
      text: value.text,
      outcome: value.outcome,
      owner: value.owner,
      due: value.due,
      source: value.source ? { kind: value.source.kind, ref: value.source.ref } : null,
      quote,
    };
    try {
      const pointId = item?.point?.id;
      if (pointId) {
        await this.api.updatePoint(d.id, pointId, input);
      } else {
        await this.api.addPoint(d.id, input);
      }
      this.announcer.announce(`Enregistré${value.outcome ? ' : ' + OUTCOMES[value.outcome] : ''}.`, 'polite');
      this.focusKey = this.keyOf(stop);
      await this.load(false);
      return true;
    } catch {
      this.announcer.announce('Le point n’a pas été enregistré. Réessayez.', 'assertive');
      return false;
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

  // --- Surlignage des passages cités ----------------------------------------------------------

  /** Passages cités surlignés par l'API CSS Custom Highlight, là où le navigateur la connaît. */
  private paintQuotes(): void {
    const registry = (globalThis.CSS as { highlights?: Map<string, unknown> } | undefined)?.highlights;
    const HighlightCtor = (globalThis as { Highlight?: new (...r: Range[]) => unknown }).Highlight;
    const root = this.paper()?.nativeElement;
    if (!registry || !HighlightCtor || !root) {
      return;
    }
    const ranges: Range[] = [];
    for (const stop of this.stops()) {
      const element = root.querySelector(`[data-anchor="${cssEscape(this.keyOf(stop))}"]`);
      for (const item of stop.items) {
        const quote = item.point?.quote;
        const range = element && quote ? findText(element, quote) : null;
        if (range) {
          ranges.push(range);
        }
      }
    }
    registry.set('sw-quote', new HighlightCtor(...ranges));
  }
}

const OUTCOMES: Record<Outcome, string> = {
  CONFIRMED: 'confirmé',
  CHANGE: 'à modifier',
  REMOVE: 'à retirer',
  DEFERRED: 'reporté',
};

function cssEscape(value: string): string {
  return typeof globalThis.CSS?.escape === 'function' ? CSS.escape(value) : value.replace(/["\\]/g, '\\$&');
}

/** Premier nœud texte de l'élément (hors cartes et saisies) qui contient le passage. */
function findText(element: Element, quote: string): Range | null {
  const walker = element.ownerDocument.createTreeWalker(element, NodeFilter.SHOW_TEXT);
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    if (node.parentElement?.closest('[data-card], [data-note]')) {
      continue;
    }
    const at = node.textContent?.indexOf(quote) ?? -1;
    if (at >= 0) {
      const range = element.ownerDocument.createRange();
      range.setStart(node, at);
      range.setEnd(node, at + quote.length);
      return range;
    }
  }
  return null;
}
