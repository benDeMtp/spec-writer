import { ChangeDetectionStrategy, Component, DestroyRef, InjectionToken, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { ContextApi } from '../../core/projects/context.api';
import {
  ContextGeneration,
  ContextPreview,
  ContextProposal,
  ContextQuestion,
  DocSource,
  DocSourceKind,
  GenerationError,
  GenerationStep,
  Project,
  SheetKind,
  UnreadReason,
} from '../../core/projects/projects.model';
import { ProjectsApi } from '../../core/projects/projects.api';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { ContextProposalComponent, QuestionReply } from './context-proposal.component';
import { SHEET_LABELS, SheetEditorComponent } from './sheet-editor.component';

const STEPS: Record<GenerationStep, string> = {
  READING_DOCS: 'Lecture de la documentation',
  READING_TICKETS: 'Lecture des tickets Jira',
  SUMMARIZING: 'Extraction des faits',
  BUILDING: 'Rédaction de la fiche',
};

const KINDS: Record<GenerationError['kind'], string> = {
  PAGE: 'Page non lue',
  SOURCE: 'Source non lue',
  JIRA: 'Jira',
  CORRECTION: "Correction demandée à l'IA",
  FAILURE: 'Échec',
};

const JIRA_REASONS: Record<string, string> = {
  UNREACHABLE: 'injoignable',
  UNAUTHORIZED: 'jeton refusé',
  FORBIDDEN: 'droits insuffisants',
  NOT_FOUND: 'projet introuvable',
  RATE_LIMITED: 'trop de requêtes',
  REJECTED: 'requête refusée',
};

/** Intervalle d'interrogation de l'état d'une génération (research.md R14) ; réduit par les tests. */
export const CONTEXT_POLL_MS = new InjectionToken<number>('CONTEXT_POLL_MS', { factory: () => 1000 });

const REASONS: Record<UnreadReason, string> = {
  UNREACHABLE: 'injoignable',
  NOT_ANTORA: 'pas une page Antora',
  HTTP_ERROR: 'erreur du serveur',
  TOO_LARGE: 'page de plus de 2 Mo',
  REDIRECT_OTHER_HOST: 'redirigée vers un autre site',
  LIMIT_REACHED: 'limite de lecture atteinte',
};

/**
 * Contexte d'un projet (US2 de 002) : la fiche, ses sources de documentation et sa génération par
 * l'IA. L'aperçu de ce qui sera envoyé précède l'accord ; la proposition n'est enregistrée qu'après
 * « Accepter » (FR-013 à FR-013c).
 */
@Component({
  selector: 'sw-project-context-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ContextProposalComponent,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTabsModule,
    RouterLink,
    SheetEditorComponent,
  ],
  template: `
    <p class="back"><a routerLink="/projets">Projets</a></p>
    @if (project(); as p) {
      <h1>Contexte du projet {{ p.name }}</h1>
      <p role="status" aria-live="polite" class="announce">{{ announce() }}</p>

      <section class="sheet" aria-labelledby="fiche-title">
        <h2 id="fiche-title">Fiches de contexte</h2>
        <p class="hint">
          Les deux fiches sont transmises à chaque rédaction de spécification de ce projet ; la fiche technique l'est aussi
          aux agents de code.
        </p>
        <mat-tab-group animationDuration="0ms" mat-stretch-tabs="false">
          <mat-tab label="Fonctionnel">
            <sw-sheet-editor sheet="FUNCTIONAL" [state]="p.functionalContext" [busy]="busy()" (saved)="save('FUNCTIONAL', $event)" />
          </mat-tab>
          <mat-tab label="Technique">
            <sw-sheet-editor sheet="TECHNICAL" [state]="p.technicalContext" [busy]="busy()" (saved)="save('TECHNICAL', $event)" />
          </mat-tab>
        </mat-tab-group>
      </section>

      <section class="sheet" aria-labelledby="sources-title">
        <h2 id="sources-title">Sources de documentation</h2>
        @if (p.docSources.length) {
          <ul class="sources">
            @for (s of p.docSources; track s.id) {
              <li>
                <span class="location">{{ s.location }}</span>
                <span class="meta">{{ s.kind === 'URL' ? 'Lien' : 'Dossier' }} · {{ lastRead(s) }}</span>
                <button mat-button type="button" (click)="removeSource(s)" [attr.aria-label]="'Retirer la source ' + s.location">
                  Retirer
                </button>
              </li>
            }
          </ul>
        } @else {
          <p class="hint">Aucune source. Ajoutez l'adresse d'un site Antora ou le dossier d'un site généré.</p>
        }
        @if (p.docSources.length < 10) {
          <form class="add" (ngSubmit)="addSource()" aria-label="Ajouter une source">
            <mat-form-field appearance="outline">
              <mat-label>Type</mat-label>
              <mat-select name="kind" [value]="kind()" (selectionChange)="kind.set($event.value)">
                <mat-option value="URL">Lien</mat-option>
                <mat-option value="FOLDER">Dossier</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline" class="grow">
              <mat-label>{{ kind() === 'URL' ? 'Adresse du site' : 'Chemin du dossier' }}</mat-label>
              <input matInput name="location" maxlength="2000" [ngModel]="location()" (ngModelChange)="location.set($event)" />
            </mat-form-field>
            <button mat-stroked-button type="submit" [disabled]="!location().trim()">Ajouter</button>
          </form>
        } @else {
          <p class="hint">Un projet a au plus 10 sources.</p>
        }
      </section>

      <section class="sheet" aria-labelledby="ai-title">
        <h2 id="ai-title">Proposer une fiche avec l'IA</h2>
        @if (generation(); as g) {
          @if (g.status === 'RUNNING') {
            <div class="progress" role="group" aria-labelledby="progress-step">
              <p id="progress-step" class="step">{{ stepLabel(g) }}</p>
              @if (g.total > 0) {
                <progress [value]="g.done" [max]="g.total" [attr.aria-label]="stepLabel(g)"></progress>
              } @else {
                <progress [attr.aria-label]="stepLabel(g)"></progress>
              }
              <p class="meta">{{ counter(g) }}</p>
              @if (g.current) {
                <p class="current">En cours : <span>{{ g.current }}</span></p>
              }
              <button mat-stroked-button type="button" (click)="cancel()" [disabled]="cancelling()">Interrompre</button>
            </div>
          } @else {
            <p class="outcome">{{ outcomeLabel(g) }}</p>
          }
        }
        @if (proposal(); as pr) {
          <sw-context-proposal
            [proposal]="pr"
            [busy]="busy()"
            [dismissed]="dismissed()"
            (accept)="accept(pr, $event)"
            (keep)="keepMine($event)"
            (answer)="reply(pr, $event)"
            (revise)="revise(pr)"
          />
        }
        @if (sending(); as s) {
          <p>{{ summary(s) }}</p>
          @if (s.unreadable.length) {
            <ul class="unread">
              @for (u of s.unreadable; track u.location) {
                <li>{{ u.location }} : {{ reason(u.reason) }}</li>
              }
            </ul>
          }
          <div class="actions">
            <button mat-flat-button type="button" (click)="propose()" [disabled]="busy() || (!s.pages && !s.tickets)">
              Envoyer à Copilot
            </button>
            <button mat-button type="button" (click)="sending.set(null)" [disabled]="busy()">Annuler</button>
          </div>
        } @else if (generation()?.status !== 'RUNNING') {
          <p class="hint">
            L'IA lit les sources de documentation et les 200 derniers tickets du projet Jira, puis propose une fiche qui
            cite ses sources. Rien n'est enregistré avant votre accord.
          </p>
          <button mat-stroked-button type="button" (click)="prepare()" [disabled]="busy()">Proposer une fiche</button>
        }
        @if (finishedErrors().length) {
          <details class="errors-detail">
            <summary>Détail des erreurs ({{ finishedErrors().length }})</summary>
            <ul class="errors">
              @for (e of finishedErrors(); track $index) {
                <li>{{ errorLabel(e) }}</li>
              }
            </ul>
            <p><a routerLink="/journal">Voir le journal</a></p>
          </details>
        }
      </section>

      @if (history().length) {
        <section class="sheet" aria-labelledby="history-title">
          <h2 id="history-title">Réponses de clarification</h2>
          <p class="hint">Réutilisées à chaque génération. Une réponse modifiée sert à la génération suivante.</p>
          <ol class="history">
            @for (q of history(); track q.number) {
              <li>
                <p class="label"><span class="ref">Q-{{ q.number }}</span> {{ q.label }}</p>
                @if (editingNumber() === q.number) {
                  <mat-form-field appearance="outline" class="answer">
                    <mat-label>Réponse à Q-{{ q.number }}</mat-label>
                    <textarea matInput rows="2" maxlength="2000" [name]="'history-' + q.number" [ngModel]="historyText()"
                      (ngModelChange)="historyText.set($event)"></textarea>
                  </mat-form-field>
                  <div class="actions">
                    <button mat-flat-button type="button" [disabled]="busy() || !historyText().trim()" (click)="updateAnswer(q, false)">
                      Enregistrer
                    </button>
                    <button mat-button type="button" [disabled]="busy()" (click)="updateAnswer(q, true)">Passer</button>
                    <button mat-button type="button" (click)="editingNumber.set(null)">Annuler</button>
                  </div>
                } @else {
                  <p class="given">
                    {{ q.state === 'ANSWERED' ? 'Réponse : ' + q.answer : 'Question passée.' }}
                    <button mat-button type="button" (click)="editAnswer(q)" [attr.aria-label]="'Modifier la réponse Q-' + q.number">
                      Modifier
                    </button>
                  </p>
                }
              </li>
            }
          </ol>
        </section>
      }
    }
  `,
  styles: `
    h1 {
      margin: 0 0 1.5rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-3);
      font-weight: 600;
    }
    h2 {
      margin: 0 0 0.5rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-1);
      font-weight: 600;
    }
    h3 {
      margin: 1rem 0 0.5rem;
      font-size: var(--sw-step-0);
      font-weight: 650;
    }
    .back {
      margin: 0 0 0.5rem;
    }
    .announce:empty {
      display: none;
    }
    .sheet {
      max-width: var(--sw-measure);
      margin-bottom: 1.5rem;
      padding: 1.5rem clamp(1rem, 3vw, 2rem);
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-sheet);
    }
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
    .actions,
    .add {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.5rem;
    }
    .grow {
      flex: 1 1 18rem;
    }
    .sources,
    .unread {
      margin: 0 0 1rem;
      padding: 0;
      list-style: none;
    }
    .sources li {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.5rem 1rem;
      padding: 0.5rem 0;
      border-top: 1px solid var(--sw-rule);
    }
    .sources li:first-child {
      border-top: none;
    }
    .location {
      overflow-wrap: anywhere;
      font-weight: 600;
    }
    .unread li,
    .errors li,
    .current span {
      overflow-wrap: anywhere;
    }
    .history {
      margin: 0;
      padding-left: 1.25rem;
    }
    .history li {
      margin-bottom: 0.75rem;
    }
    .history .label {
      margin: 0;
      font-weight: 600;
    }
    .history .ref {
      margin-right: 0.5rem;
      padding: 0 0.4rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
      font-weight: 500;
    }
    .history .answer {
      width: 100%;
    }
    .history .given {
      margin: 0.25rem 0;
    }
    .progress {
      display: grid;
      gap: 0.25rem;
      margin-bottom: 1rem;
    }
    .progress progress {
      width: 100%;
      height: 0.75rem;
    }
    .step {
      margin: 0;
      font-weight: 650;
    }
    .current {
      margin: 0 0 0.5rem;
    }
    .outcome {
      margin: 0 0 0.75rem;
      font-weight: 600;
    }
    .errors-detail {
      margin-top: 1rem;
    }
    .errors {
      margin: 0.5rem 0;
      padding-left: 1.25rem;
    }
  `,
})
export class ProjectContextPage {
  readonly id = input.required<string>();

  private readonly projects = inject(ProjectsApi);
  private readonly api = inject(ContextApi);
  private readonly consent = inject(ConsentGate);

  protected readonly project = signal<Project | null>(null);
  /** Fiches de la proposition gardées telles quelles (« Garder la mienne »). */
  protected readonly dismissed = signal<SheetKind[]>([]);
  protected readonly history = signal<ContextQuestion[]>([]);
  protected readonly editingNumber = signal<number | null>(null);
  protected readonly historyText = signal('');
  protected readonly kind = signal<DocSourceKind>('URL');
  protected readonly location = signal('');
  protected readonly sending = signal<ContextPreview | null>(null);
  protected readonly proposal = signal<ContextProposal | null>(null);
  protected readonly busy = signal(false);
  protected readonly generation = signal<ContextGeneration | null>(null);
  protected readonly cancelling = signal(false);
  /** Erreurs d'une génération terminée (FR-013g). */
  protected readonly finishedErrors = computed(() => {
    const g = this.generation();
    return g && g.status !== 'RUNNING' ? g.errors : [];
  });
  private readonly destroyRef = inject(DestroyRef);
  private readonly pollMs = inject(CONTEXT_POLL_MS);
  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private lastStep: GenerationStep | null = null;
  protected readonly announce = signal('');

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => void this.load(id).then(() => Promise.all([this.resume(id), this.loadHistory(id)])));
    });
    this.destroyRef.onDestroy(() => this.stopPolling());
  }

  /** Au retour sur la page : la génération en cours, ou la dernière si sa proposition est encore d'actualité. */
  private async resume(id: string): Promise<void> {
    const g = await this.api.current(id);
    if (!g) {
      return;
    }
    this.generation.set(g);
    this.lastStep = g.step;
    if (g.status === 'RUNNING') {
      this.poll(id);
    } else if (g.status === 'SUCCEEDED') {
      // la proposition renvoyée est son état courant : absente si elle a expiré ou si ses deux fiches sont acceptées
      this.proposal.set(g.proposal);
    }
  }

  private async load(id: string): Promise<void> {
    const all = await this.projects.list();
    this.show(all.find((p) => p.id === id) ?? null);
  }

  private show(p: Project | null): void {
    this.project.set(p);
  }

  private async loadHistory(id: string): Promise<void> {
    this.history.set(await this.api.questions(id));
  }

  protected lastRead(s: DocSource): string {
    if (!s.lastReadAt) {
      return 'jamais lue';
    }
    const at = new Date(s.lastReadAt).toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });
    const errors = s.pagesInError ? `, ${s.pagesInError} en erreur` : '';
    return `${s.pagesRead ?? 0} ${(s.pagesRead ?? 0) > 1 ? 'pages lues' : 'page lue'}${errors}, le ${at}`;
  }

  protected reason(reason: string): string {
    return REASONS[reason as UnreadReason] ?? reason;
  }

  protected stepLabel(g: ContextGeneration): string {
    return g.step ? STEPS[g.step] : 'Démarrage de la génération';
  }

  protected counter(g: ContextGeneration): string {
    const unit: Record<GenerationStep, [string, string]> = {
      READING_DOCS: ['page', 'pages'],
      READING_TICKETS: ['ticket', 'tickets'],
      SUMMARIZING: ['lot', 'lots'],
      BUILDING: ['étape', 'étapes'],
    };
    if (!g.step) {
      return '';
    }
    const [one, many] = unit[g.step];
    if (g.total > 0) {
      return `${one} ${g.done} sur ${g.total}`;
    }
    return `${g.done} ${g.done > 1 ? many : one}`;
  }

  protected outcomeLabel(g: ContextGeneration): string {
    switch (g.status) {
      case 'SUCCEEDED':
        return 'Génération terminée.';
      case 'CANCELLED':
        return "Génération interrompue. Rien n'a été proposé ni enregistré.";
      default: {
        const failure = g.errors.find((e) => e.kind === 'FAILURE');
        return `La génération a échoué${failure?.message ? ' : ' + failure.message : '.'}`;
      }
    }
  }

  protected errorLabel(e: GenerationError): string {
    const kind = KINDS[e.kind];
    const where = e.location ? ` ${e.location}` : '';
    const http = e.httpStatus ? ` (HTTP ${e.httpStatus})` : '';
    switch (e.kind) {
      case 'PAGE':
      case 'SOURCE':
        return `${kind}${where} : ${this.reason(e.reason)}${http}`;
      case 'JIRA':
        return `${kind}${where} : ${JIRA_REASONS[e.reason] ?? e.reason}${e.message ? '. ' + e.message : ''}`;
      case 'CORRECTION':
        return `${kind} (${e.reason}) : ${e.message ?? ''}`;
      default:
        return `${kind} : ${e.message ?? e.reason}`;
    }
  }

  protected summary(s: ContextPreview): string {
    if (!s.pages && !s.tickets) {
      return 'Aucune page ni aucun ticket à envoyer : ajoutez une source de documentation ou une clé Jira au projet.';
    }
    const pages = `${s.pages} ${s.pages > 1 ? 'pages' : 'page'} de documentation`;
    const tickets = `${s.tickets} ${s.tickets > 1 ? 'tickets' : 'ticket'} Jira`;
    return `Seront envoyés à Copilot : ${pages} et ${tickets}.`;
  }

  protected async save(sheet: SheetKind, markdown: string): Promise<void> {
    const p = this.project();
    if (!p) {
      return;
    }
    await this.run(async () => {
      this.show(await this.api.save(p.id, sheet, markdown));
      this.announce.set(`${SHEET_LABELS[sheet].name} enregistrée.`);
    });
  }

  protected async addSource(): Promise<void> {
    const p = this.project();
    const location = this.location().trim();
    if (!p || !location) {
      return;
    }
    await this.run(async () => {
      await this.api.addSource(p.id, this.kind(), location);
      this.location.set('');
      this.announce.set('Source ajoutée.');
      await this.reload();
    });
  }

  protected async removeSource(s: DocSource): Promise<void> {
    const p = this.project();
    if (!p) {
      return;
    }
    await this.run(async () => {
      await this.api.deleteSource(p.id, s.id);
      this.announce.set(`Source ${s.location} retirée.`);
      await this.reload();
    });
  }

  protected async prepare(): Promise<void> {
    const p = this.project();
    if (!p) {
      return;
    }
    await this.run(async () => this.sending.set(await this.api.preview(p.id)));
  }

  protected async propose(): Promise<void> {
    const p = this.project();
    if (!p || !(await this.consent.ensure('COPILOT'))) {
      return;
    }
    await this.run(async () => {
      const g = await this.api.start(p.id);
      this.sending.set(null);
      this.proposal.set(null);
      this.dismissed.set([]);
      this.lastStep = null;
      this.generation.set(g);
      this.announce.set('Génération du contexte démarrée.');
      this.poll(p.id);
    });
  }

  protected async cancel(): Promise<void> {
    const p = this.project();
    if (!p) {
      return;
    }
    this.cancelling.set(true);
    await this.run(() => this.api.cancel(p.id));
  }

  /** Interroge l'état chaque seconde jusqu'à la fin ; n'annonce que les changements d'étape. */
  private poll(id: string): void {
    this.stopPolling();
    this.pollTimer = setTimeout(async () => {
      this.pollTimer = null;
      let g: ContextGeneration | null;
      try {
        g = await this.api.current(id);
      } catch {
        this.poll(id);
        return;
      }
      if (!g) {
        this.generation.set(null);
        return;
      }
      this.generation.set(g);
      if (g.status === 'RUNNING') {
        if (g.step && g.step !== this.lastStep) {
          this.lastStep = g.step;
          this.announce.set(`${STEPS[g.step]}…`);
        }
        this.poll(id);
        return;
      }
      await this.finished(g);
    }, this.pollMs);
  }

  private async finished(g: ContextGeneration): Promise<void> {
    this.cancelling.set(false);
    if (g.status === 'SUCCEEDED' && g.proposal) {
      this.proposal.set(g.proposal);
      this.announce.set('Fiche proposée. Relisez-la, puis acceptez-la ou gardez la vôtre.');
    } else {
      this.announce.set(this.outcomeLabel(g));
    }
    await this.reload();
  }

  private stopPolling(): void {
    if (this.pollTimer) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
  }

  protected async accept(pr: ContextProposal, sheet: SheetKind): Promise<void> {
    const p = this.project();
    if (!p) {
      return;
    }
    await this.run(async () => {
      this.show(await this.api.accept(p.id, pr.proposalId, [sheet]));
      const accepted = { ...(sheet === 'FUNCTIONAL' ? pr.functional : pr.technical), accepted: true };
      this.proposal.set(sheet === 'FUNCTIONAL' ? { ...pr, functional: accepted } : { ...pr, technical: accepted });
      this.announce.set(`${SHEET_LABELS[sheet].name} acceptée et enregistrée.`);
    });
  }

  protected keepMine(sheet: SheetKind): void {
    this.dismissed.update((d) => [...d, sheet]);
    this.announce.set(`${SHEET_LABELS[sheet].name} actuelle conservée.`);
  }

  protected async reply(pr: ContextProposal, r: QuestionReply): Promise<void> {
    const p = this.project();
    if (!p) {
      return;
    }
    await this.run(async () => {
      const saved = await this.api.answer(p.id, pr.proposalId, r.question.id, r.answer, r.skip);
      const current = this.proposal() ?? pr;
      this.proposal.set({
        ...current,
        questions: current.questions.map((q) =>
          q.id === r.question.id ? { ...q, state: saved.state, number: saved.number, answer: saved.answer } : q,
        ),
      });
      this.announce.set(r.skip ? `Question Q-${saved.number} passée.` : `Réponse Q-${saved.number} enregistrée.`);
      await this.loadHistory(p.id);
    });
  }

  protected async revise(pr: ContextProposal): Promise<void> {
    const p = this.project();
    if (!p || !(await this.consent.ensure('COPILOT'))) {
      return;
    }
    await this.run(async () => {
      const g = await this.api.revise(p.id, pr.proposalId);
      this.proposal.set(null);
      this.dismissed.set([]);
      this.lastStep = null;
      this.generation.set(g);
      this.announce.set('Révision de la proposition démarrée.');
      this.poll(p.id);
    });
  }

  protected editAnswer(q: ContextQuestion): void {
    this.historyText.set(q.answer);
    this.editingNumber.set(q.number);
  }

  protected async updateAnswer(q: ContextQuestion, skip: boolean): Promise<void> {
    const p = this.project();
    if (!p) {
      return;
    }
    await this.run(async () => {
      await this.api.updateQuestion(p.id, q.number, skip ? null : this.historyText().trim(), skip);
      this.editingNumber.set(null);
      this.announce.set(`Réponse Q-${q.number} modifiée. Elle servira à la prochaine génération.`);
      await this.loadHistory(p.id);
    });
  }

  private async reload(): Promise<void> {
    await this.load(this.id());
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
    } catch {
      // Le bandeau affiche la cause (saisie invalide, contexte modifié, consentement).
      this.announce.set('');
    } finally {
      this.busy.set(false);
    }
  }
}
