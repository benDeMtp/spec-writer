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
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { InterviewsApi } from '../../core/interviews/interviews.api';
import { InterviewDetail, Outcome, PointInput, Stop, StopItem } from '../../core/interviews/interviews.model';
import { Language, Specification } from '../../core/sessions/sessions.model';
import { SessionsApi } from '../../core/sessions/sessions.api';
import { ConfirmData, ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { InterviewDocumentComponent, anchorKey } from './interview-document.component';
import { PointCardComponent } from './point-card.component';
import { EditorValue, PointEditorComponent } from './point-editor.component';
import { passageOf } from './selection';

/** Passage ou élément visé par « Noter » : clé d'ancre, et texte cité s'il y a une sélection. */
/** Passage sélectionné : l'élément auquel la note se rattache et le texte cité. */
interface Target {
  key: string;
  quote: string;
}

type NoteAction = 'COMMENT' | 'REPLACE' | 'QUESTION';

/** Note en cours de saisie : nouvelle sur un passage, ou existante (`item`) à modifier. */
interface Note {
  key: string;
  quote: string | null;
  action: NoteAction;
  item?: StopItem;
}

/** Remarques et questions notées en séance : dans la colonne de notes, pas dans le document. */
function isNote(item: StopItem): boolean {
  return item.origin === 'REMARK' || item.origin === 'QUESTION';
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
      <div class="seance" [style.zoom]="zoom()">
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
          <div class="zoom" role="group" aria-label="Taille du texte de la relecture">
            <button mat-button type="button" [disabled]="zoom() <= zoomSteps[0]" (click)="setZoom(-1)">A−<span class="sr-only"> réduire le texte</span></button>
            <span class="zoom-level" aria-live="polite">{{ zoomPercent() }} %</span>
            <button mat-button type="button" [disabled]="zoom() >= zoomSteps[zoomSteps.length - 1]" (click)="setZoom(1)">A+<span class="sr-only"> agrandir le texte</span></button>
          </div>
          <button mat-stroked-button type="button" (click)="goNextPending()">Prochain point à traiter</button>
          <button
            mat-flat-button
            type="button"
            [disabled]="applying() || !d.counts.handled && !d.counts.notes"
            [attr.aria-describedby]="!d.counts.handled && !d.counts.notes ? 'apply-hint' : null"
            (click)="apply()"
          >
            Prendre en compte les notes
          </button>
          <button mat-stroked-button type="button" (click)="finish()">Terminer</button>
          @if (!d.counts.handled && !d.counts.notes) {
            <p id="apply-hint" class="apply-hint">Notez ou tranchez au moins un point pour réviser la spécification.</p>
          }
          @if (applyProblem()) {
            <p role="alert" class="apply-hint">{{ applyProblem() }}</p>
          }
        </header>

        @if (newerVersion(); as v) {
          <p class="banner" role="status">Une version plus récente existe (v{{ v }}) : la révision partira d’elle.</p>
        }

        <p class="keys">
          Sélectionnez un passage pour le commenter, le remplacer ou le supprimer ·
          <kbd>↑</kbd> <kbd>↓</kbd> point précédent ou suivant
        </p>

        <div class="columns">
          <div class="paper" #paper>
            @if (spec(); as s) {
              <sw-interview-document [spec]="s" [stops]="stops()" [slot]="slot" [language]="language()" />
            } @else if (specFailed()) {
              <p role="alert">La spécification n’a pas pu être chargée. Rechargez la page.</p>
            } @else {
              <p role="status">Chargement de la spécification…</p>
            }
          </div>

          <aside class="notes" aria-labelledby="notes-title" [style.max-height]="notesHeight()" [style.top]="notesTop()">
            <h2 id="notes-title">Notes de séance ({{ notes().length }})</h2>
            @if (note(); as n) {
              <div data-note class="note">
                <sw-point-editor
                  [kind]="n.action === 'QUESTION' ? 'QUESTION' : 'REMARK'"
                  [heading]="noteHeading(n)"
                  [initial]="noteInitial(n)"
                  (saved)="saveNote(n, $event)"
                  (cancelled)="note.set(null)"
                />
              </div>
            } @else if (picked(); as p) {
              <div data-note class="toolbar" role="toolbar" [attr.aria-label]="toolbarLabel()" (mousedown)="$event.preventDefault()">
                <p class="picked">« {{ p.quote.length > 120 ? p.quote.slice(0, 120) + '…' : p.quote }} »</p>
                <button mat-stroked-button type="button" (click)="startNote('COMMENT')">Commenter</button>
                <button mat-stroked-button type="button" (click)="startNote('REPLACE')">Remplacer</button>
                <button mat-stroked-button type="button" (click)="remove()">Supprimer</button>
                <button mat-button type="button" (click)="startNote('QUESTION')">Question…</button>
                <button mat-button type="button" (click)="picked.set(null)">Fermer</button>
              </div>
            } @else {
              <p class="hint">Sélectionnez un passage du document pour le commenter, le remplacer ou le supprimer.</p>
            }
            <ol class="note-list">
              @for (entry of notes(); track entry.item.point!.id) {
                @let pt = entry.item.point!;
                <li>
                  <p class="where">{{ entry.stop.label }}</p>
                  @if (pt.quote) {
                    <q class="quote">« {{ pt.quote }} »</q>
                  }
                  <p class="what">
                    <strong>{{ noteOutcome(entry.item) }}</strong>
                    @if (pt.text) {
                      : {{ pt.text }}
                    }
                    @if (pt.owner) {
                      ({{ pt.owner }})
                    }
                  </p>
                  <div class="note-actions">
                    @if (pt.quote) {
                      <button mat-button type="button" (click)="goToPassage(entry.stop, pt.quote)">Aller au passage</button>
                    }
                    <button mat-button type="button" (click)="editNote(entry.stop, entry.item)">Modifier</button>
                    <button mat-button type="button" (click)="deletePoint(entry.item)">
                      Supprimer la {{ entry.item.origin === 'QUESTION' ? 'question' : 'remarque' }}
                    </button>
                  </div>
                </li>
              }
            </ol>
          </aside>
        </div>

        <!-- sous chaque élément, seulement les points de la spécification : hypothèses, écarts, points ouverts -->
        <ng-template #slot let-stop>
          @for (item of specItems(stop); track item.code) {
            <sw-point-card [item]="item" (saved)="save(stop, item, $event)" (focusin)="track(stop)" />
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
    /* le zoom de la relecture (style.zoom) ne vaut que pour cet écran : ailleurs, taille normale */
    .seance {
      display: grid;
      gap: 1rem;
      margin: 0 auto;
      padding: 0 1rem;
    }
    .zoom {
      display: flex;
      align-items: center;
      gap: 0.25rem;
      font-size: 1rem;
    }
    .zoom-level {
      min-width: 3.5rem;
      text-align: center;
      color: var(--sw-ink-soft);
    }
    .columns {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(16rem, 24rem);
      gap: 1.5rem;
      align-items: start;
    }
    @media (max-width: 70rem) {
      .columns {
        grid-template-columns: minmax(0, 1fr);
      }
    }
    .notes {
      position: sticky;
      overflow-y: auto;
      display: grid;
      gap: 0.75rem;
      font-size: 1rem;
    }
    .notes h2 {
      margin: 0;
      font-size: 1.1rem;
    }
    .hint,
    .where {
      margin: 0;
      color: var(--sw-ink-soft);
    }
    .picked {
      margin: 0;
      flex-basis: 100%;
      font-style: italic;
    }
    .note-list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 0.75rem;
    }
    .note-list li {
      padding: 0.5rem 0.75rem;
      border-left: 4px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
      background: var(--sw-sheet);
    }
    .note-list .quote {
      quotes: none;
      font-style: italic;
    }
    .what {
      margin: 0.25rem 0 0;
    }
    .note-actions {
      display: flex;
      flex-wrap: wrap;
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
    .apply-hint {
      flex-basis: 100%;
      margin: 0;
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
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      padding: 0.5rem;
      border: 2px solid var(--sw-pencil);
      border-radius: var(--sw-radius-field);
      background: var(--sw-sheet);
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
  /** Passage sélectionné : la barre d'actions s'ouvre en tête de la colonne de notes. */
  protected readonly picked = signal<Target | null>(null);
  protected readonly note = signal<Note | null>(null);
  /** Paliers du zoom de la relecture, pour les participants qui lisent à distance. */
  protected readonly zoomSteps = [1, 1.1, 1.25, 1.5, 1.75, 2];
  protected readonly zoom = signal(storedZoom(this.zoomSteps));
  protected readonly zoomPercent = computed(() => Math.round(this.zoom() * 100));
  /** La colonne tient dans l'écran : ses longueurs sont agrandies par le zoom, d'où la division. */
  protected readonly notesHeight = computed(() => `calc((100vh - 8rem) / ${this.zoom()})`);
  protected readonly notesTop = computed(() => `calc(7rem / ${this.zoom()})`);
  protected readonly applying = signal(false);
  protected readonly applyProblem = signal('');
  private readonly consent = inject(ConsentGate);

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
    const q = this.picked()?.quote ?? '';
    return `Noter sur « ${q.length > 60 ? q.slice(0, 60) + '…' : q} »`;
  });
  /** Remarques et questions de la spécification affichée, dans l'ordre du document. */
  protected readonly notes = computed(() =>
    this.stops().flatMap((stop) => stop.items.filter((item) => isNote(item) && item.point).map((item) => ({ stop, item }))),
  );

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

  protected startNote(action: NoteAction): void {
    const p = this.picked();
    if (p) {
      this.note.set({ ...p, action });
      this.picked.set(null);
      this.clearSelection();
    }
  }

  protected noteHeading(n: Note): string {
    const on = n.quote ? `« ${n.quote.length > 80 ? n.quote.slice(0, 80) + '…' : n.quote} »` : (this.stopOf(n.key)?.label ?? '');
    switch (n.action) {
      case 'REPLACE':
        return `Remplacer ${on}`;
      case 'QUESTION':
        return `Question sur ${on}`;
      default:
        return `Remarque sur ${on}`;
    }
  }

  protected noteInitial(n: Note): EditorValue | null {
    const p = n.item?.point;
    if (p) {
      return { text: p.text, outcome: p.outcome, owner: p.owner, due: p.due, source: p.source ? { kind: p.source.kind as 'DOC_PAGE', ref: p.source.ref } : null };
    }
    return n.action === 'REPLACE' ? { text: n.quote ?? '', outcome: 'CHANGE', owner: null, due: null } : null;
  }

  protected async saveNote(n: Note, value: EditorValue): Promise<void> {
    const stop = this.stopOf(n.key);
    const origin = n.item ? n.item.origin : n.action === 'QUESTION' ? 'QUESTION' : 'REMARK';
    if (stop && (await this.persist(stop, n.item ?? null, origin, value, n.quote))) {
      this.note.set(null);
    }
  }

  protected async remove(): Promise<void> {
    const p = this.picked();
    const stop = p ? this.stopOf(p.key) : undefined;
    if (p && stop && (await this.persist(stop, null, 'REMARK', { text: '', outcome: 'REMOVE', owner: null, due: null }, p.quote))) {
      this.picked.set(null);
      this.clearSelection();
    }
  }

  protected editNote(stop: Stop, item: StopItem): void {
    this.picked.set(null);
    this.note.set({ key: this.keyOf(stop), quote: item.point?.quote ?? null, action: item.origin === 'QUESTION' ? 'QUESTION' : 'COMMENT', item });
  }

  protected noteOutcome(item: StopItem): string {
    const o = item.point?.outcome;
    return item.origin === 'QUESTION' || o === 'DEFERRED' ? 'À vérifier' : o === 'REMOVE' ? 'À retirer' : 'À modifier';
  }

  /** Points de la spécification, sous leur élément ; les notes vont dans la colonne. */
  protected specItems(stop: Stop): StopItem[] {
    return stop.items.filter((item) => !isNote(item));
  }

  /** Défile jusqu'au passage cité et le met en évidence. */
  protected goToPassage(stop: Stop, quote: string): void {
    const root = this.paper()?.nativeElement;
    if (!root) {
      return;
    }
    const element = root.querySelector(`[data-anchor="${cssEscape(this.keyOf(stop))}"]`) ?? root;
    const range = findText(element, quote) ?? findText(root, quote);
    const target = range?.startContainer.parentElement ?? (element as HTMLElement);
    target.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
    const registry = (globalThis.CSS as { highlights?: Map<string, unknown> } | undefined)?.highlights;
    const HighlightCtor = (globalThis as { Highlight?: new (...r: Range[]) => unknown }).Highlight;
    if (range && registry && HighlightCtor) {
      registry.set('sw-quote-current', new HighlightCtor(range));
    }
  }

  /** Une sélection qui a servi ne rouvre pas la barre au clic suivant. */
  private clearSelection(): void {
    this.paper()?.nativeElement.ownerDocument.getSelection()?.removeAllRanges();
  }

  private stopOf(key: string): Stop | undefined {
    return this.stops().find((s) => this.keyOf(s) === key);
  }

  // --- Enregistrement -------------------------------------------------------------------------

  protected keyOf(stop: Stop): string {
    return anchorKey(stop.anchor);
  }

  protected async save(stop: Stop, item: StopItem, value: EditorValue): Promise<void> {
    await this.persist(stop, item, item.origin, value, item.point?.quote ?? null);
  }

  protected async deletePoint(item: StopItem): Promise<void> {
    const d = this.detail()!;
    const pointId = item.point?.id;
    if (!pointId) {
      return;
    }
    try {
      await this.api.deletePoint(d.id, pointId);
      this.announcer.announce(`${item.origin === 'QUESTION' ? 'Question supprimée' : 'Remarque supprimée'}.`, 'polite');
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
      // une note ne déplace pas le focus dans le document ; une réponse garde sa carte au focus
      this.focusKey = item && !isNote(item) ? this.keyOf(stop) : null;
      await this.load(false);
      return true;
    } catch {
      this.announcer.announce('Le point n’a pas été enregistré. Réessayez.', 'assertive');
      return false;
    }
  }

  protected setZoom(direction: 1 | -1): void {
    const at = this.zoomSteps.indexOf(this.zoom());
    const next = this.zoomSteps[Math.min(Math.max(at + direction, 0), this.zoomSteps.length - 1)];
    this.zoom.set(next);
    try {
      localStorage.setItem(ZOOM_KEY, String(next));
    } catch {
      // stockage refusé : le zoom vaut pour cette visite seulement
    }
  }

  /**
   * « Prendre en compte les notes » : la relecture se termine, sans dialogue (les points non traités
   * restent ouverts), puis la révision de la spécification affichée démarre ; sa page s'ouvre sur la
   * discussion, pour relire le diff et appliquer. Le compte rendu reste au récapitulatif.
   */
  protected async apply(): Promise<void> {
    const d = this.detail();
    const spec = this.currentSpec();
    if (!d || !spec || this.applying() || !(await this.consent.ensure('COPILOT'))) {
      return;
    }
    this.applying.set(true);
    this.applyProblem.set('');
    try {
      await this.api.finish(d.id);
      const started = await this.api.proposeRevision(d.id, spec.sessionId);
      await this.router.navigate(['/sessions', spec.sessionId, 'versions', spec.latestVersion], {
        queryParams: { discussion: 1, tour: started.turnId },
      });
    } catch (e) {
      // la séance est peut-être déjà terminée : le récapitulatif permet de relancer la révision
      this.applyProblem.set(problem(e, 'La révision n’a pas pu démarrer. Réessayez depuis le récapitulatif.'));
    } finally {
      this.applying.set(false);
    }
  }

  /**
   * « Terminer » : le récapitulatif et son compte rendu. Sans rien de noté, la relecture n'est pas
   * gardée : ni confirmation ni récapitulatif, retour à la spécification.
   */
  protected async finish(): Promise<void> {
    const d = this.detail();
    if (!d) {
      return;
    }
    const empty = !d.counts.handled && !d.counts.notes;
    if (!empty && d.counts.pending > 0) {
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
      const finished = await this.api.finish(d.id);
      if (finished) {
        await this.router.navigate(['/seances', d.id, 'recapitulatif']);
        return;
      }
      this.announcer.announce('Rien n’a été noté : la relecture n’est pas gardée.', 'polite');
      const spec = this.currentSpec() ?? d.specs[0];
      await this.router.navigate(['/sessions', spec.sessionId, 'versions', spec.latestVersion]);
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
      // le passage peut précéder son élément (un titre, un tableau des détails) : cherché dans tout le document
      const element = root.querySelector(`[data-anchor="${cssEscape(this.keyOf(stop))}"]`) ?? root;
      for (const item of stop.items) {
        const quote = item.point?.quote;
        const range = quote ? (findText(element, quote) ?? findText(root, quote)) : null;
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

const ZOOM_KEY = 'sw.relectureZoom';
const DEFAULT_ZOOM = 1.25;

function storedZoom(steps: number[]): number {
  try {
    const saved = Number(globalThis.localStorage?.getItem(ZOOM_KEY));
    return steps.includes(saved) ? saved : DEFAULT_ZOOM;
  } catch {
    return DEFAULT_ZOOM;
  }
}

function problem(e: unknown, fallback: string): string {
  const body = e instanceof HttpErrorResponse ? (e.error as { userMessage?: unknown } | null) : null;
  return typeof body?.userMessage === 'string' ? body.userMessage : fallback;
}

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
