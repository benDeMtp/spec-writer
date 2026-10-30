import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { JiraApi, PushOutcome } from '../../core/jira/jira.api';
import { ConflictResolution, JiraConflict, Proposal } from '../../core/jira/jira.model';
import { SessionsApi } from '../../core/sessions/sessions.api';
import {
  Language,
  SessionDetail,
  Specification,
  SyncState,
  UseCase,
  VersionSummary,
} from '../../core/sessions/sessions.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { ScenarioComponent } from '../../shared/ui/scenario.component';
import { AiEditPanelComponent } from '../ai-edit/ai-edit.panel';
import { ConflictDialogComponent } from '../jira/conflict-dialog.component';
import { PushDialogComponent, PushDialogData } from '../jira/push-dialog.component';
import { VersionsPanelComponent } from './versions-panel.component';

const SYNC: Record<SyncState, string> = {
  IN_SYNC: 'à jour avec Jira',
  LOCAL_AHEAD: 'modifications à envoyer',
  CONFLICT: 'en conflit avec Jira',
};

/** Version de spécification : résumé, use cases, scénarios, hypothèses et exigences (FR-010). */
@Component({
  selector: 'sw-specification-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AiEditPanelComponent, MatButtonModule, RouterLink, ScenarioComponent, VersionsPanelComponent],
  template: `
    @if (spec(); as v) {
      <header class="head">
        <h1>{{ v.title }}</h1>
        <p class="meta">Version {{ v.number }} · {{ session()?.name }}</p>
        @if (session()?.linkedTicket; as t) {
          <p class="ticket">
            Liée au ticket
            <a [href]="t.issueUrl" target="_blank" rel="noopener noreferrer">{{ t.issueKey }}<span class="sw-visually-hidden"> (nouvel onglet)</span></a>,
            <span [class]="'sync ' + t.syncState">{{ syncLabel(t.syncState) }}</span>.
          </p>
        }
        @for (w of warnings(); track $index) {
          <p class="warning">{{ w }}</p>
        }
        <div class="actions">
          <button mat-flat-button type="button" [attr.aria-expanded]="editing()" (click)="toggleEdit()">
            Faire évoluer avec l'IA
          </button>
          @if (session()?.linkedTicket) {
            <button mat-stroked-button type="button" [disabled]="pushing()" (click)="push(v.number)">Envoyer vers Jira</button>
          }
          @if (session()?.origin !== 'JIRA_IMPORT') {
            <a mat-button [routerLink]="['/sessions', id()]">Retour au questionnaire</a>
          }
        </div>
        <p role="status" aria-live="polite" class="announce">{{ status() }}</p>
        @if (editing()) {
          <sw-ai-edit-panel
            [sessionId]="id()"
            [initialProposal]="mergeProposal()"
            (applied)="onApplied($event)"
            (closed)="closeEdit()"
          />
        }
      </header>
      <div class="layout">
      <div class="sheet document">
        <section aria-labelledby="summary">
          <h2 id="summary">Résumé</h2>
          <p>{{ v.summary }}</p>
        </section>

        <section aria-labelledby="use-cases">
          <h2 id="use-cases">Use cases</h2>
          @for (uc of v.useCases; track uc.code) {
            <article class="use-case" [id]="uc.code">
              <h3><span class="code">{{ uc.code }}</span> {{ uc.title }}</h3>
              <dl class="facts">
                <dt>Acteur</dt>
                <dd>{{ uc.actor }}</dd>
                <dt>Objectif</dt>
                <dd>{{ uc.goal }}</dd>
                @if (uc.preconditions.length) {
                  <dt>Préconditions</dt>
                  <dd><ul>@for (p of uc.preconditions; track $index) {<li>{{ p }}</li>}</ul></dd>
                }
                <dt>Déroulement nominal</dt>
                <dd><ol>@for (step of uc.nominalFlow; track $index) {<li>{{ step }}</li>}</ol></dd>
                @if (uc.alternatives.length) {
                  <dt>Alternatives</dt>
                  <dd><ul>@for (a of uc.alternatives; track $index) {<li>{{ a }}</li>}</ul></dd>
                }
                <dt>Source</dt>
                <dd class="source">{{ sourceLabel(uc) }}</dd>
              </dl>
            </article>
          }
        </section>

        <section aria-labelledby="scenarios">
          <h2 id="scenarios">Scénarios d'acceptation</h2>
          @for (uc of v.useCases; track uc.code) {
            <h3><span class="code">{{ uc.code }}</span> {{ uc.title }}</h3>
            @for (sc of scenariosOf(uc.code); track sc.code) {
              <sw-scenario [scenario]="sc" [language]="language()" />
            }
          }
        </section>

        <section aria-labelledby="assumptions">
          <h2 id="assumptions">Hypothèses</h2>
          @if (v.assumptions.length) {
            <ul class="assumptions">
              @for (h of v.assumptions; track h.code) {
                <li><span class="code">{{ h.code }}</span> {{ h.statement }}</li>
              }
            </ul>
          } @else {
            <p>Aucune : tout provient du besoin et de vos réponses.</p>
          }
        </section>

        <section aria-labelledby="functional">
          <h2 id="functional">Exigences fonctionnelles</h2>
          <ul>@for (r of functional(); track r.code) {<li><span class="code">{{ r.code }}</span> {{ r.statement }}</li>} @empty {<li>Aucune.</li>}</ul>
        </section>
        <section aria-labelledby="technical">
          <h2 id="technical">Exigences techniques</h2>
          <ul>@for (r of technical(); track r.code) {<li><span class="code">{{ r.code }}</span> {{ r.statement }}</li>} @empty {<li>Aucune.</li>}</ul>
        </section>
      </div>
      @if (versions().length > 1) {
        <aside class="side">
          <sw-versions-panel [sessionId]="id()" [versions]="versions()" [current]="v.number" />
        </aside>
      }
      </div>
    } @else {
      <p role="status" aria-live="polite">Chargement de la spécification…</p>
    }
  `,
  styles: `
    .head h1 {
      margin: 0;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-3);
      font-weight: 600;
    }
    .meta {
      margin: 0.25rem 0 0.75rem;
      color: var(--sw-ink-soft);
    }
    .ticket {
      margin: 0 0 0.5rem;
    }
    .sync {
      font-weight: 600;
    }
    .sync.CONFLICT {
      color: var(--mat-sys-error);
    }
    .warning {
      margin: 0 0 0.5rem;
      padding-left: 0.5rem;
      border-left: 3px solid var(--sw-outcome);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .announce:empty {
      display: none;
    }
    .layout {
      display: grid;
      grid-template-columns: minmax(0, calc(var(--sw-measure) + 10rem)) 13rem;
      gap: 1.5rem;
      align-items: start;
      margin-top: 1rem;
    }
    .side {
      position: sticky;
      top: 1rem;
    }
    @media (max-width: 64rem) {
      .layout {
        grid-template-columns: minmax(0, 1fr);
      }
      .side {
        position: static;
        order: -1;
      }
    }
    .sheet {
      padding: 1.5rem clamp(1rem, 3vw, 2.5rem);
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-sheet);
    }
    .document p,
    .document li,
    .document dd {
      font-family: var(--sw-font-doc);
      line-height: 1.65;
    }
    h2 {
      margin: 2rem 0 0.75rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-2);
      font-weight: 600;
    }
    section:first-child h2 {
      margin-top: 0;
    }
    h3 {
      margin: 1.25rem 0 0.5rem;
      font-size: var(--sw-step-1);
      font-weight: 650;
    }
    .code {
      color: var(--sw-ink-soft);
      font-weight: 500;
      margin-right: 0.25rem;
      font-family: var(--sw-font-ui);
    }
    .facts {
      display: grid;
      grid-template-columns: 11rem minmax(0, 1fr);
      gap: 0.25rem 1rem;
      margin: 0;
    }
    .facts dt {
      color: var(--sw-ink-soft);
    }
    .facts dd {
      margin: 0;
    }
    .facts ul,
    .facts ol {
      margin: 0;
      padding-left: 1.25rem;
    }
    .assumptions li {
      border-left: 3px solid var(--sw-outcome);
      padding-left: 0.5rem;
      list-style: none;
      margin-bottom: 0.25rem;
    }
    .assumptions {
      padding-left: 0;
    }
    @media (max-width: 36rem) {
      .facts {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class SpecificationPage {
  private readonly api = inject(SessionsApi);
  private readonly jira = inject(JiraApi);
  private readonly dialog = inject(MatDialog);
  private readonly consent = inject(ConsentGate);
  private readonly router = inject(Router);

  readonly id = input.required<string>();
  readonly n = input.required<string>();
  /** `?edit=1` : ouvre la modification assistée, par exemple après l'import d'un ticket. */
  readonly edit = input<string | undefined>();

  protected readonly editing = signal(false);
  protected readonly mergeProposal = signal<Proposal | null>(null);
  protected readonly pushing = signal(false);
  protected readonly status = signal('');
  /** Avertissements transmis par la page précédente (description Jira tronquée à l'import). */
  protected readonly warnings = signal<string[]>(
    (this.router.currentNavigation()?.extras.state?.['warnings'] as string[] | undefined) ?? [],
  );

  protected readonly spec = signal<Specification | null>(null);
  protected readonly session = signal<SessionDetail | null>(null);
  protected readonly versions = signal<VersionSummary[]>([]);
  protected readonly language = computed<Language>(() => this.session()?.language ?? 'fr');
  protected readonly functional = computed(() => this.spec()?.requirements.filter((r) => r.category === 'FUNCTIONAL') ?? []);
  protected readonly technical = computed(() => this.spec()?.requirements.filter((r) => r.category === 'TECHNICAL') ?? []);

  constructor() {
    // la même page affiche chaque version : elle se recharge quand le numéro change dans l'URL
    effect(() => {
      const id = this.id();
      const n = Number(this.n());
      void this.load(id, n);
    });
    effect(() => {
      if (this.edit()) {
        untracked(() => this.editing.set(true));
      }
    });
  }

  protected syncLabel(state: SyncState): string {
    return SYNC[state];
  }

  protected toggleEdit(): void {
    this.editing.update((open) => !open);
    this.mergeProposal.set(null);
  }

  protected closeEdit(): void {
    this.editing.set(false);
    this.mergeProposal.set(null);
  }

  protected async onApplied(version: Specification): Promise<void> {
    this.closeEdit();
    this.status.set(`Version ${version.number} créée. Jira n'a pas été modifié.`);
    await this.router.navigate(['/sessions', this.id(), 'versions', String(version.number)]);
  }

  /** Aperçu puis envoi ; en cas de conflit, l'utilisateur choisit entre trois issues (FR-047, FR-048). */
  protected async push(versionNumber: number): Promise<void> {
    if (!(await this.consent.ensure('JIRA'))) {
      return;
    }
    this.pushing.set(true);
    try {
      const data: PushDialogData = { sessionId: this.id(), versionNumber };
      const outcome = await firstValueFrom(
        this.dialog.open<PushDialogComponent, PushDialogData, PushOutcome>(PushDialogComponent, { data, width: '48rem' })
          .afterClosed(),
      );
      if (outcome) {
        await this.handle(outcome, versionNumber);
      }
    } finally {
      this.pushing.set(false);
    }
  }

  private async handle(outcome: PushOutcome, versionNumber: number): Promise<void> {
    switch (outcome.kind) {
      case 'pushed':
        if (outcome.ticket.linkedVersionNumber !== versionNumber) {
          this.status.set(`La version Jira est devenue la version ${outcome.ticket.linkedVersionNumber}.`);
          await this.router.navigate(['/sessions', this.id(), 'versions', String(outcome.ticket.linkedVersionNumber)]);
        } else {
          this.status.set(`Version ${versionNumber} envoyée vers ${outcome.ticket.issueKey}.`);
          this.session.set(await this.api.get(this.id()));
          this.versions.set(await this.api.versions(this.id()));
        }
        return;
      case 'merge':
        this.mergeProposal.set(outcome.proposal);
        this.editing.set(true);
        this.status.set("Fusion proposée par l'IA : relisez-la, puis appliquez-la avant de l'envoyer.");
        return;
      case 'conflict':
        await this.resolve(outcome.conflict, versionNumber);
    }
  }

  private async resolve(conflict: JiraConflict, versionNumber: number): Promise<void> {
    this.session.set(await this.api.get(this.id()));
    const resolution = await firstValueFrom(
      this.dialog.open<ConflictDialogComponent, JiraConflict, ConflictResolution>(ConflictDialogComponent, {
        data: conflict,
        width: '64rem',
      }).afterClosed(),
    );
    if (!resolution) {
      this.status.set("Envoi annulé : le ticket Jira n'a pas été modifié.");
      return;
    }
    if (resolution !== 'OVERWRITE' && !(await this.consent.ensure('COPILOT'))) {
      return;
    }
    this.status.set(resolution === 'OVERWRITE' ? 'Envoi…' : "L'IA traite la version Jira…");
    try {
      await this.handle(await this.jira.push(this.id(), versionNumber, resolution), versionNumber);
    } catch {
      this.status.set('');
    }
  }

  private async load(id: string, n: number): Promise<void> {
    const [spec, session, versions] = await Promise.all([this.api.version(id, n), this.api.get(id), this.api.versions(id)]);
    this.session.set(session);
    this.versions.set(versions);
    this.spec.set(spec);
  }

  protected scenariosOf(code: string) {
    return this.spec()?.scenarios.filter((s) => s.useCaseCode === code) ?? [];
  }

  /** Lien vers la source du use case (FR-011) : extrait du besoin, réponse citée ou hypothèse. */
  protected sourceLabel(uc: UseCase): string {
    const s = uc.source;
    if (!s) {
      return '';
    }
    switch (s.kind) {
      case 'NEED_EXCERPT':
        return `Extrait du besoin : « ${s.excerpt} »`;
      case 'ANSWER': {
        const q = this.session()?.answeredQuestions.find((x) => x.id === s.questionId);
        return q ? `Réponse à « ${q.label} » : ${q.answer}` : `Réponse : « ${s.excerpt} »`;
      }
      default:
        return `Hypothèse${s.excerpt ? ` : ${s.excerpt}` : ''}`;
    }
  }
}
