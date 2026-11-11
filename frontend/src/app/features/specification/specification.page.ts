import { DOCUMENT, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MarkdownComponent } from 'ngx-markdown';
import { firstValueFrom } from 'rxjs';
import { JiraApi, PushOutcome } from '../../core/jira/jira.api';
import { ProjectsApi } from '../../core/projects/projects.api';
import { ProjectsStore } from '../../core/projects/projects.store';
import { ConflictResolution, JiraConflict, Proposal } from '../../core/jira/jira.model';
import { SessionsApi } from '../../core/sessions/sessions.api';
import {
  IMPLEMENTATION_LABELS,
  ImplementationStatus,
  Language,
  LinkedTicket,
  SessionDetail,
  Specification,
  SyncState,
  UseCase,
  VersionSummary,
} from '../../core/sessions/sessions.model';
import { AttachmentsComponent } from '../../shared/ui/attachments.component';
import { ConfirmData, ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { confirmDeleteSpecification } from '../../shared/ui/confirm-delete-specification';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { ScenarioComponent } from '../../shared/ui/scenario.component';
import { ProposalReviewComponent } from './proposal-review.component';
import { SettingsStore } from '../../core/settings/settings.store';
import { ConflictDialogComponent } from '../jira/conflict-dialog.component';
import {
  CreateTicketDialogComponent,
  CreateTicketDialogData,
} from '../jira/create-ticket-dialog.component';
import { PushDialogComponent, PushDialogData } from '../jira/push-dialog.component';
import {
  MoveSessionData,
  MoveSessionDialogComponent,
} from '../projects/move-session-dialog.component';
import { ExportService } from './export.service';
import { SubtasksPanelComponent } from './subtasks-panel.component';
import { VersionsPanelComponent } from './versions-panel.component';
import { DiscussionPanelComponent } from './discussion-panel.component';
import { ElapsedComponent } from '../../shared/ui/elapsed.component';

const EFFORTS: Record<DeploymentFact['kind'], string> = {
  NOT_DEPLOYED: 'aucune instance en production, à déployer',
  MULTIPLE_INSTANCES: 'plusieurs instances en production',
  CROSS_INFRASTRUCTURE: 'traverse des infrastructures (ouverture réseau ou exposition à prévoir)',
  SHARED_INFRASTRUCTURE: "dépend d'une infrastructure partagée",
};

type DeploymentFact = NonNullable<Specification['architecture']>['deploymentFacts'][number];

const ADR_NAME = /adr[-_ ]?\d+/i;

/** Avertissement de la confirmation d'une suppression liée à Jira (FR-017 de 002). */
function jiraNotice(ticket: LinkedTicket | null): string {
  return ticket ? ` Elle est liée au ticket ${ticket.issueKey} : Jira n'est pas modifié.` : '';
}

const SYNC: Record<SyncState, string> = {
  IN_SYNC: 'à jour avec Jira',
  LOCAL_AHEAD: 'modifications à envoyer',
  CONFLICT: 'en conflit avec Jira',
};

/** Référence d'un document joint (009) : cité par son nom, sans lien ni extrait. */
const ATTACHMENT_REF = 'joint:';

/** Version de spécification : résumé, use cases, scénarios, hypothèses et exigences (FR-010). */
@Component({
  selector: 'sw-specification-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ElapsedComponent,
    ProposalReviewComponent,
    DiscussionPanelComponent,
    AttachmentsComponent,
    DatePipe,
    MarkdownComponent,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    RouterLink,
    ScenarioComponent,
    SubtasksPanelComponent,
    VersionsPanelComponent,
  ],
  template: `
    @if (spec(); as v) {
      <div class="workspace" [class.discussing]="discussing() && !locked()">
        <div class="main">
          <header class="head">
            <h1 id="introduction" tabindex="-1">{{ v.title }}</h1>
            <p class="meta">
              Version {{ v.number }} · {{ session()?.name }}
              @if (v.contextUpdatedAt) {
                · contexte du {{ v.contextUpdatedAt | date: 'd MMM y, HH:mm' }}
              }
            </p>
            @if (session()?.linkedTicket; as t) {
              <p class="ticket">
                Liée au ticket
                <a [href]="t.issueUrl" target="_blank" rel="noopener noreferrer"
                  >{{ t.issueKey }}<span class="sw-visually-hidden"> (nouvel onglet)</span></a
                >, <span [class]="'sync ' + t.syncState">{{ syncLabel(t.syncState) }}</span
                >.
              </p>
            }
            @if (session()?.implementation?.evolvedFromSessionId; as origin) {
              <p class="meta">
                Évolution d'une <a [routerLink]="['/sessions', origin]">spécification d'origine</a>.
              </p>
            }
            @for (w of warnings(); track $index) {
              <p class="warning">{{ w }}</p>
            }
            @if (session(); as s) {
              <div class="implementation">
                <mat-form-field appearance="outline" subscriptSizing="dynamic">
                  <mat-label>Statut d'implémentation</mat-label>
                  <mat-select
                    [value]="s.implementation.status"
                    (selectionChange)="changeStatus($event.value)"
                  >
                    @for (o of statusOptions; track o.value) {
                      <mat-option [value]="o.value">{{ o.label }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                @if (locked()) {
                  <p id="locked-reason" class="hint">
                    Implémentée le {{ s.implementation.implementedAt | date: 'd MMMM y' }} : cette
                    spécification est verrouillée. Rétablissez un statut antérieur, ou créez une
                    évolution.
                  </p>
                  <button mat-flat-button type="button" [disabled]="evolving()" (click)="evolve()">
                    {{ evolving() ? 'Création de l’évolution…' : 'Créer une évolution' }}
                  </button>
                  <sw-elapsed class="hint" [running]="evolving()" />
                }
              </div>
            }
            <div class="actions">
              <button
                #discussButton
                mat-flat-button
                type="button"
                [attr.aria-expanded]="discussing() && !locked()"
                [disabled]="locked() || drafting()"
                [attr.aria-describedby]="
                  locked() ? 'locked-reason' : drafting() ? 'drafting-hint' : null
                "
                (click)="toggleDiscussion()"
              >
                Discuter avec l'IA
              </button>
              <button
                mat-stroked-button
                type="button"
                [disabled]="exporting()"
                (click)="exportMarkdown(v.number)"
              >
                Exporter en Markdown
              </button>
              @if (session()?.linkedTicket) {
                <button
                  mat-stroked-button
                  type="button"
                  [disabled]="pushing() || locked()"
                  [attr.aria-describedby]="locked() ? 'locked-reason' : null"
                  (click)="push(v.number)"
                >
                  Envoyer vers Jira
                </button>
              } @else if (session()) {
                <button
                  mat-stroked-button
                  type="button"
                  [disabled]="!jiraReady() || pushing() || locked()"
                  [attr.aria-describedby]="
                    locked() ? 'locked-reason' : jiraReady() ? null : 'jira-missing'
                  "
                  (click)="createTicket(v.number)"
                >
                  Créer un ticket Jira
                </button>
              }
              @if (session() && moveTargets().length) {
                <button mat-button type="button" (click)="move()">Déplacer vers…</button>
              }
              @if (session()?.origin !== 'JIRA_IMPORT') {
                <a mat-button [routerLink]="['/sessions', id()]">Retour au questionnaire</a>
              }
              @if (session()) {
                <button
                  mat-button
                  type="button"
                  [disabled]="locked()"
                  [attr.aria-describedby]="locked() ? 'locked-reason' : null"
                  (click)="removeVersion(v.number)"
                >
                  Supprimer cette version
                </button>
                <button mat-button type="button" (click)="removeSpecification()">
                  Supprimer la spécification
                </button>
              }
            </div>
            @if (session() && !session()?.linkedTicket && !jiraReady()) {
              <p id="jira-missing" class="hint">
                Pour créer un ticket, renseignez le serveur, le projet et le jeton Jira dans
                <a routerLink="/configuration">la configuration</a>.
              </p>
            }
            <p role="status" aria-live="polite" class="announce">{{ status() }}</p>
            <sw-elapsed class="hint" [running]="pushing()" />
            @if (session(); as s) {
              <sw-attachments [projectId]="s.projectId" [sessionId]="s.id" [disabled]="locked()" />
            }
            @if (reviewed(); as p) {
              @if (!locked()) {
                <sw-proposal-review
                  [sessionId]="id()"
                  [proposal]="p"
                  [title]="
                    discussionProposal() ? 'Nouvelle version proposée' : 'Proposition de l’IA'
                  "
                  (applied)="onApplied($event)"
                  (discarded)="onDiscarded()"
                />
              }
            }
          </header>
          <div class="layout">
            <div class="sheet document">
              <section aria-labelledby="summary">
                <h2 id="summary">Résumé</h2>
                <markdown class="rich" [data]="v.summary" />
              </section>

              @if (v.details) {
                <section aria-labelledby="details">
                  <h2 id="details">Détails</h2>
                  <markdown class="rich" [data]="v.details" />
                </section>
              }

              <section aria-labelledby="use-cases">
                <h2 id="use-cases">Use cases</h2>
                @for (uc of v.useCases; track uc.code) {
                  <article class="use-case" [id]="uc.code">
                    <h3>
                      <span class="code">{{ uc.code }}</span> {{ uc.title }}
                    </h3>
                    <dl class="facts">
                      <dt>Acteur</dt>
                      <dd>{{ uc.actor }}</dd>
                      <dt>Objectif</dt>
                      <dd>{{ uc.goal }}</dd>
                      @if (uc.preconditions.length) {
                        <dt>Préconditions</dt>
                        <dd>
                          <ul>
                            @for (p of uc.preconditions; track $index) {
                              <li>{{ p }}</li>
                            }
                          </ul>
                        </dd>
                      }
                      <dt>Déroulement nominal</dt>
                      <dd>
                        <ol>
                          @for (step of uc.nominalFlow; track $index) {
                            <li>{{ step }}</li>
                          }
                        </ol>
                      </dd>
                      @if (uc.alternatives.length) {
                        <dt>Alternatives</dt>
                        <dd>
                          <ul>
                            @for (a of uc.alternatives; track $index) {
                              <li>{{ a }}</li>
                            }
                          </ul>
                        </dd>
                      }
                      <dt>Source</dt>
                      <dd class="source">
                        {{ sourceLabel(uc) }}
                        @if (uc.source?.kind === 'DISCUSSION') {
                          @if (sourceLink(uc); as href) {
                            <a [routerLink]="href">{{ uc.source?.excerpt || 'Discussion' }}</a>
                          } @else {
                            {{ uc.source?.excerpt }}
                          }
                        } @else if (sourceLink(uc); as href) {
                          <a [href]="href" target="_blank" rel="noopener noreferrer"
                            >{{ refText(uc)
                            }}<span class="sw-visually-hidden"> (nouvel onglet)</span></a
                          >
                        } @else if (uc.source?.ref) {
                          {{ refText(uc) }}
                        }
                      </dd>
                    </dl>
                  </article>
                }
              </section>

              <section aria-labelledby="scenarios">
                <h2 id="scenarios">Scénarios d'acceptation</h2>
                @for (uc of v.useCases; track uc.code) {
                  <h3 [id]="'scenarios-' + uc.code">
                    <span class="code">{{ uc.code }}</span> {{ uc.title }}
                  </h3>
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
                      <li>
                        <span class="code">{{ h.code }}</span> {{ h.statement }}
                      </li>
                    }
                  </ul>
                } @else {
                  <p>Aucune : tout provient du besoin et de vos réponses.</p>
                }
              </section>

              <section aria-labelledby="functional">
                <h2 id="functional">Exigences fonctionnelles</h2>
                <ul>
                  @for (r of functional(); track r.code) {
                    <li>
                      <span class="code">{{ r.code }}</span> {{ r.statement }}
                    </li>
                  } @empty {
                    <li>Aucune.</li>
                  }
                </ul>
              </section>
              <section aria-labelledby="technical">
                <h2 id="technical">Exigences techniques</h2>
                <ul>
                  @for (r of technical(); track r.code) {
                    <li>
                      <span class="code">{{ r.code }}</span> {{ r.statement }}
                    </li>
                  } @empty {
                    <li>Aucune.</li>
                  }
                </ul>
              </section>

              @if (v.architecture; as a) {
                @if (a.modules.length || a.exchanges.length) {
                  <section aria-labelledby="architecture">
                    <h2 id="architecture">Architecture</h2>
                    @if (a.modules.length) {
                      <h3>Modules impactés</h3>
                      <ul>
                        @for (m of a.modules; track m.name) {
                          <li>
                            {{ m.name }}
                            <span class="scope"
                              >({{ m.scope === 'PROJECT' ? 'propre au projet' : 'commun' }})</span
                            >
                          </li>
                        }
                      </ul>
                    }
                    @if (a.exchanges.length) {
                      <h3>Flux</h3>
                      <ul>
                        @for (x of a.exchanges; track $index) {
                          <li>
                            {{ x.source }} → {{ x.destination }} :
                            @if (x.kind === 'MISSING') {
                              <strong>flux manquant, à créer</strong>
                            } @else {
                              flux existant{{ x.via ? ', via ' + x.via : '' }}
                            }
                          </li>
                        }
                      </ul>
                    }
                  </section>
                }
              }
              @if (v.adrDeviations?.length) {
                <section aria-labelledby="deviations">
                  <h2 id="deviations">Écarts aux ADR</h2>
                  <ul class="deviations">
                    @for (d of v.adrDeviations; track d.adrRef) {
                      <li>
                        @if (v.links?.[d.adrRef]; as href) {
                          <a [href]="href" target="_blank" rel="noopener noreferrer"
                            >{{ pageName(d.adrRef)
                            }}<span class="sw-visually-hidden"> (nouvel onglet)</span></a
                          >
                        } @else {
                          <strong>{{ d.adrRef }}</strong>
                        }
                        <dl>
                          @if (d.decision) {
                            <dt>Décision</dt>
                            <dd>{{ d.decision }}</dd>
                          }
                          <dt>Écart</dt>
                          <dd>{{ d.deviation }}</dd>
                        </dl>
                      </li>
                    }
                  </ul>
                </section>
              }
              @if (v.architecture?.deploymentFacts?.length) {
                <section aria-labelledby="efforts">
                  <h2 id="efforts">Efforts supplémentaires (déploiement)</h2>
                  <ul>
                    @for (f of v.architecture?.deploymentFacts; track $index) {
                      <li>
                        {{ f.element }}{{ f.other ? ' → ' + f.other : '' }} :
                        {{ effortLabel(f.kind) }}
                      </li>
                    }
                  </ul>
                </section>
              }
              <sw-subtasks-panel [sessionId]="id()" [versionNumber]="v.number" />
            </div>
            @if (versions().length > 1) {
              <aside class="side">
                <sw-versions-panel
                  [sessionId]="id()"
                  [versions]="versions()"
                  [current]="v.number"
                />
              </aside>
            }
          </div>
          @if (discussing() && !locked()) {
            <!-- petit écran : le panneau est sous le document, ce bouton y ramène -->
            <button
              mat-stroked-button
              type="button"
              class="back-to-discussion"
              (click)="backToDiscussion()"
            >
              Revenir à la discussion
            </button>
          }
        </div>
        @if (discussing() && !locked()) {
          <div class="discussion-column">
            <sw-discussion-panel
              #discussionPanel
              [sessionId]="id()"
              [projectId]="session()?.projectId ?? null"
              [displayedVersion]="v.number"
              [internalLink]="showSection"
              [reviewing]="!!discussionProposal()"
              (proposed)="onProposed($event)"
              (drafting)="drafting.set($event)"
              (closed)="closeDiscussion()"
            />
          </div>
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
    .hint {
      margin: 0.5rem 0 0;
      color: var(--sw-ink-soft);
    }
    .announce:empty {
      display: none;
    }
    .workspace.discussing {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(22rem, 30rem);
      gap: 1.5rem;
      align-items: start;
    }
    .workspace.discussing .layout {
      grid-template-columns: minmax(0, 1fr);
    }
    .discussion-column {
      position: sticky;
      top: 1rem;
      max-height: calc(100vh - 2rem);
      overflow-y: auto;
      overscroll-behavior: contain;
    }
    .workspace.discussing .side {
      position: static;
      order: -1;
    }
    .back-to-discussion {
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
      .workspace.discussing {
        grid-template-columns: minmax(0, 1fr);
      }
      .back-to-discussion {
        display: block;
        position: sticky;
        bottom: 1rem;
        margin: 1rem 0 0 auto;
        background: var(--sw-sheet);
      }
      .discussion-column {
        position: static;
        max-height: none;
      }
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
    /* champs simples : leurs retours à la ligne sont conservés (FR-012c) */
    .document > section > ul > li,
    .document dd {
      white-space: pre-line;
    }
    .rich {
      display: block;
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
    .implementation {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem 1rem;
      align-items: center;
      margin: 0.75rem 0;
    }
    .implementation .hint {
      flex: 1 1 20rem;
      margin: 0;
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
  private readonly document = inject(DOCUMENT);
  private readonly exporter = inject(ExportService);
  private readonly settings = inject(SettingsStore);
  private readonly projects = inject(ProjectsStore);
  private readonly projectsApi = inject(ProjectsApi);

  readonly id = input.required<string>();
  readonly n = input.required<string>();
  /** `?edit=1` : ancien lien de la modification assistée, il ouvre la discussion (011). */
  readonly edit = input<string | undefined>();
  /** `?discussion=1` : ouvre la discussion avec l'IA, par exemple après l'import d'un ticket (011). */
  readonly discussion = input<string | undefined>();
  private readonly discussButton = viewChild('discussButton', {
    read: ElementRef<HTMLButtonElement>,
  });
  /** Discussion avec l'IA ouverte à côté du document (011). */
  protected readonly discussing = signal(false);
  /** L'agent rédige une nouvelle version : le panneau reste ouvert. */
  protected readonly drafting = signal(false);
  private readonly route = inject(ActivatedRoute);

  /**
   * Proposition transmise par la page précédente : le besoin proposé comme modification assistée
   * du ticket importé (« C'est la même », FR-035 de 002).
   */
  private readonly passedProposal =
    (this.router.currentNavigation()?.extras.state?.['proposal'] as Proposal | undefined) ?? null;
  protected readonly mergeProposal = signal<Proposal | null>(this.passedProposal);
  /** Nouvelle version proposée par la discussion, relue à côté du document (011, FR-010). */
  protected readonly discussionProposal = signal<Proposal | null>(null);
  protected readonly reviewed = computed(() => this.discussionProposal() ?? this.mergeProposal());
  private readonly discussionPanel = viewChild<DiscussionPanelComponent>('discussionPanel');
  protected readonly pushing = signal(false);
  /** Création d'une évolution en cours (appel à l'IA). */
  protected readonly evolving = signal(false);
  protected readonly exporting = signal(false);
  protected readonly status = signal('');
  /** Avertissements transmis par la page précédente (description Jira tronquée à l'import). */
  protected readonly warnings = signal<string[]>(
    (this.router.currentNavigation()?.extras.state?.['warnings'] as string[] | undefined) ?? [],
  );

  protected readonly spec = signal<Specification | null>(null);
  protected readonly session = signal<SessionDetail | null>(null);
  protected readonly versions = signal<VersionSummary[]>([]);
  /** Jira configuré : serveur, projet par défaut et jeton (FR-036). */
  protected readonly jiraReady = computed(() => {
    const j = this.settings.jira();
    return !!j && j.enabled && j.tokenSet && !!j.defaultProjectKey;
  });
  /** Projets où la spécification peut être déplacée : tous sauf le sien (FR-004 de 002). */
  protected readonly moveTargets = computed(() => {
    const projectId = this.session()?.projectId;
    return this.projects.projects().filter((p) => p.id !== projectId);
  });
  protected readonly language = computed<Language>(() => this.session()?.language ?? 'fr');
  protected readonly statusOptions = (
    Object.keys(IMPLEMENTATION_LABELS) as ImplementationStatus[]
  ).map((value) => ({
    value,
    label: IMPLEMENTATION_LABELS[value],
  }));
  /** Implémentée : aucune nouvelle version, aucun envoi, aucune suppression de version (FR-026a de 002). */
  protected readonly locked = computed(
    () => this.session()?.implementation?.status === 'IMPLEMENTED',
  );
  protected readonly functional = computed(
    () => this.spec()?.requirements.filter((r) => r.category === 'FUNCTIONAL') ?? [],
  );
  protected readonly technical = computed(
    () => this.spec()?.requirements.filter((r) => r.category === 'TECHNICAL') ?? [],
  );

  constructor() {
    // la même page affiche chaque version : elle se recharge quand le numéro change dans l'URL
    effect(() => {
      const id = this.id();
      const n = Number(this.n());
      void this.load(id, n);
    });
    void this.settings.ensureLoaded();
    effect(() => {
      if (this.edit() || this.discussion()) {
        untracked(() => this.discussing.set(true));
      }
    });
  }

  /** Export Markdown (FR-019) ; le navigateur confirme avant d'écraser un fichier existant (FR-020). */
  protected async exportMarkdown(versionNumber: number): Promise<void> {
    this.exporting.set(true);
    try {
      const outcome = await this.exporter.exportMarkdown(this.id(), versionNumber);
      this.status.set(
        outcome === 'cancelled'
          ? 'Export annulé.'
          : `Version ${versionNumber} exportée en Markdown${outcome === 'downloaded' ? ' dans vos téléchargements' : ''}.`,
      );
    } catch {
      this.status.set('');
    } finally {
      this.exporting.set(false);
    }
  }

  protected syncLabel(state: SyncState): string {
    return SYNC[state];
  }

  protected toggleDiscussion(): void {
    this.discussing.update((open) => !open);
    void this.syncDiscussionUrl();
  }

  /** Fermeture du panneau : le focus revient au bouton qui l'a ouvert. */
  protected closeDiscussion(): void {
    this.discussing.set(false);
    void this.syncDiscussionUrl();
    setTimeout(() => this.discussButton()?.nativeElement.focus());
  }

  /** Petit écran : le panneau est sous le document ; le focus revient à sa saisie. */
  protected backToDiscussion(): void {
    this.discussionPanel()?.focus();
  }

  /** `?discussion=1` suit le panneau : un rechargement ou un lien le rouvre. */
  private syncDiscussionUrl(): Promise<boolean> {
    return this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { discussion: this.discussing() ? 1 : null, edit: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  /**
   * Une rédaction ou une proposition en attente serait perdue en quittant l'écran : confirmation
   * (garde de la route, 011).
   */
  async canLeave(): Promise<boolean> {
    if (!this.drafting() && !this.reviewed()) {
      return true;
    }
    return this.confirm({
      title: 'Quitter sans appliquer la proposition ?',
      message: this.drafting()
        ? 'L’agent rédige une nouvelle version : elle sera perdue si vous quittez cet écran.'
        : 'La proposition n’est pas appliquée : elle sera perdue si vous quittez cet écran.',
      confirm: 'Quitter',
    });
  }

  @HostListener('window:beforeunload', ['$event'])
  protected warnBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.drafting() || this.reviewed()) {
      event.preventDefault();
    }
  }

  /**
   * Citation d'une section de cette spécification dans la discussion : le document défile jusqu'à
   * la section, qui reçoit le focus ; les autres liens sont ouverts par le routeur.
   */
  protected readonly showSection = (href: string): boolean => {
    const match = /^\/sessions\/([^/]+)\/versions\/\d+#([A-Za-z0-9-]+)$/.exec(href);
    if (!match || match[1] !== this.id()) {
      return false;
    }
    const target = this.document.getElementById(match[2]);
    if (!target) {
      return false;
    }
    if (!target.hasAttribute('tabindex')) {
      target.setAttribute('tabindex', '-1');
    }
    target.scrollIntoView?.({ block: 'start' });
    target.focus();
    return true;
  };

  private closeReview(): void {
    this.mergeProposal.set(null);
    this.discussionProposal.set(null);
  }

  protected onProposed(proposal: Proposal): void {
    this.discussionProposal.set(proposal);
    setTimeout(() => this.document.getElementById('proposal-title')?.focus());
  }

  protected onDiscarded(): void {
    const fromDiscussion = !!this.discussionProposal();
    this.closeReview();
    this.status.set('Proposition abandonnée : la spécification est inchangée.');
    // la discussion continue : le focus revient à sa saisie
    if (fromDiscussion) {
      setTimeout(() => this.discussionPanel()?.focus());
    }
  }

  protected async onApplied(version: Specification): Promise<void> {
    this.closeReview();
    this.status.set(`Version ${version.number} créée. Jira n'a pas été modifié.`);
    await this.router.navigate(['/sessions', this.id(), 'versions', String(version.number)], {
      queryParams: this.discussing() ? { discussion: 1 } : {},
    });
    await this.discussionPanel()?.refresh();
    // la nouvelle version s'affiche : le focus va à son titre
    setTimeout(() => this.document.getElementById('introduction')?.focus());
  }

  /** Déplace la spécification et toutes ses versions vers un autre projet (FR-004 de 002). */
  protected async move(): Promise<void> {
    const session = this.session();
    if (!session) {
      return;
    }
    const target = await firstValueFrom(
      this.dialog
        .open<MoveSessionDialogComponent, MoveSessionData, string | null>(
          MoveSessionDialogComponent,
          {
            data: { sessionName: session.name, targets: this.moveTargets() },
          },
        )
        .afterClosed(),
    );
    if (!target) {
      return;
    }
    await this.projectsApi.moveSession(session.id, target);
    const name = this.projects.projects().find((p) => p.id === target)?.name ?? '';
    this.status.set(`Spécification déplacée vers le projet « ${name} ».`);
    this.session.set(await this.api.get(this.id()));
    await this.projects.load();
  }

  /** Statut d'implémentation ; quitter « implémentée » se confirme, en rappelant sa date (FR-026b de 002). */
  protected async changeStatus(target: ImplementationStatus): Promise<void> {
    const session = this.session();
    if (!session || target === session.implementation.status) {
      return;
    }
    const leaving = session.implementation.status === 'IMPLEMENTED';
    if (leaving) {
      const since = session.implementation.implementedAt
        ? new Date(session.implementation.implementedAt).toLocaleDateString('fr-FR', {
            dateStyle: 'long',
          })
        : '';
      const confirmed = await this.confirm({
        title: 'Rétablir un statut antérieur ?',
        message:
          `« ${session.name} » a été implémentée le ${since}. Elle redeviendra modifiable, ` +
          `au statut « ${IMPLEMENTATION_LABELS[target]} ».`,
        confirm: 'Rétablir',
      });
      if (!confirmed) {
        // le sélecteur reprend la valeur enregistrée
        this.session.set({ ...session, implementation: { ...session.implementation } });
        return;
      }
    }
    const implementation = await this.api.setStatus(session.id, target, leaving);
    this.session.set({ ...session, implementation });
    this.status.set(`Statut : ${IMPLEMENTATION_LABELS[implementation.status]}.`);
  }

  /** Évolution : une nouvelle spécification en brouillon, qui part de cette version (FR-026b de 002). */
  protected async evolve(): Promise<void> {
    const session = this.session();
    if (!session || this.evolving() || !(await this.consent.ensure('COPILOT'))) {
      return;
    }
    this.evolving.set(true);
    this.status.set('Création de l’évolution…');
    try {
      const evolved = await this.api.evolve(session.id);
      await this.router.navigate(['/sessions', evolved.id]);
    } finally {
      this.evolving.set(false);
    }
  }

  /** Suppression d'une version, après une confirmation qui la nomme (FR-014 de 002). Jira n'est pas modifié. */
  protected async removeVersion(n: number): Promise<void> {
    const session = this.session();
    if (!session) {
      return;
    }
    const others = this.versions().filter((v) => v.number !== n);
    const confirmed = await this.confirm({
      title: `Supprimer la version ${n} ?`,
      message:
        `La version ${n} de « ${session.name} » sera supprimée définitivement. ` +
        (others.length
          ? 'Les autres versions gardent leur numéro.'
          : 'Le besoin et les réponses sont gardés : vous pourrez rédiger une nouvelle version.') +
        jiraNotice(session.linkedTicket),
      confirm: 'Supprimer la version',
    });
    if (!confirmed) {
      return;
    }
    await this.api.deleteVersion(session.id, n);
    const latest = others.at(-1);
    if (latest) {
      // la même page affiche la version restante : l'annonce reste
      this.status.set(`Version ${n} supprimée. Jira n'a pas été modifié.`);
      await this.router.navigate(['/sessions', session.id, 'versions', String(latest.number)]);
    } else {
      await this.router.navigate(['/sessions', session.id]);
    }
  }

  /** Suppression de la spécification entière : session, réponses et versions (FR-016 de 002). */
  protected async removeSpecification(): Promise<void> {
    const session = this.session();
    if (!session) {
      return;
    }
    const count = this.versions().length;
    const confirmed = await confirmDeleteSpecification(this.dialog, this.api, session.id, {
      title: 'Supprimer la spécification ?',
      message:
        `« ${session.name} », ses réponses et ${count > 1 ? `ses ${count} versions` : 'sa version'} seront supprimés ` +
        'définitivement.' +
        jiraNotice(session.linkedTicket),
      confirm: 'Supprimer la spécification',
    });
    if (!confirmed) {
      return;
    }
    await this.api.delete(session.id, confirmed.keepDiscussions);
    await this.router.navigate(['/sessions']);
  }

  private async confirm(data: ConfirmData): Promise<boolean> {
    const result = await firstValueFrom(
      this.dialog.open(ConfirmDialogComponent, { data }).afterClosed(),
    );
    return result === true;
  }

  /** Aperçu (type, champs obligatoires) puis création sur confirmation (FR-036 à FR-038). */
  protected async createTicket(versionNumber: number): Promise<void> {
    if (!(await this.consent.ensure('JIRA'))) {
      return;
    }
    this.pushing.set(true);
    try {
      const data: CreateTicketDialogData = { sessionId: this.id(), versionNumber };
      const ticket = await firstValueFrom(
        this.dialog
          .open<CreateTicketDialogComponent, CreateTicketDialogData, LinkedTicket>(
            CreateTicketDialogComponent,
            {
              data,
              width: '48rem',
            },
          )
          .afterClosed(),
      );
      if (ticket) {
        this.status.set(`Ticket ${ticket.issueKey} créé depuis la version ${versionNumber}.`);
        this.session.set(await this.api.get(this.id()));
        this.versions.set(await this.api.versions(this.id()));
      }
    } finally {
      this.pushing.set(false);
    }
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
        this.dialog
          .open<PushDialogComponent, PushDialogData, PushOutcome>(PushDialogComponent, {
            data,
            width: '48rem',
          })
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
          this.status.set(
            `La version Jira est devenue la version ${outcome.ticket.linkedVersionNumber}.`,
          );
          await this.router.navigate([
            '/sessions',
            this.id(),
            'versions',
            String(outcome.ticket.linkedVersionNumber),
          ]);
        } else {
          this.status.set(`Version ${versionNumber} envoyée vers ${outcome.ticket.issueKey}.`);
          this.session.set(await this.api.get(this.id()));
          this.versions.set(await this.api.versions(this.id()));
        }
        return;
      case 'merge':
        this.mergeProposal.set(outcome.proposal);
        this.status.set(
          "Fusion proposée par l'IA : relisez-la, puis appliquez-la avant de l'envoyer.",
        );
        return;
      case 'conflict':
        await this.resolve(outcome.conflict, versionNumber);
    }
  }

  private async resolve(conflict: JiraConflict, versionNumber: number): Promise<void> {
    this.session.set(await this.api.get(this.id()));
    const resolution = await firstValueFrom(
      this.dialog
        .open<ConflictDialogComponent, JiraConflict, ConflictResolution>(ConflictDialogComponent, {
          data: conflict,
          width: '64rem',
        })
        .afterClosed(),
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
    const [spec, session, versions] = await Promise.all([
      this.api.version(id, n),
      this.api.get(id),
      this.api.versions(id),
    ]);
    this.session.set(session);
    this.versions.set(versions);
    this.spec.set(spec);
  }

  protected scenariosOf(code: string) {
    return this.spec()?.scenarios.filter((s) => s.useCaseCode === code) ?? [];
  }

  /** Lien vers la source du use case (FR-011) : extrait du besoin, réponse, contexte du projet ou hypothèse. */
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
      case 'PROJECT_CONTEXT':
        return `Contexte du projet : « ${s.excerpt} »`;
      case 'JIRA_ISSUE':
        return 'Ticket';
      case 'DOC_PAGE':
        if (s.ref?.startsWith(ATTACHMENT_REF)) {
          return 'Document joint';
        }
        return ADR_NAME.test(this.pageName(s.ref ?? '')) ? 'ADR' : 'Page';
      case 'ARCH_ELEMENT':
        return "Élément d'architecture";
      case 'DISCUSSION':
        return 'Établi dans la discussion :';
      default:
        return `Hypothèse${s.excerpt ? ` : ${s.excerpt}` : ''}`;
    }
  }

  /** Adresse de la source citée : ticket Jira, page ou ADR ; absente pour un élément d'architecture. */
  protected sourceLink(uc: UseCase): string | null {
    const ref = uc.source?.ref;
    return ref ? (this.spec()?.links?.[ref] ?? null) : null;
  }

  protected refText(uc: UseCase): string {
    const s = uc.source;
    if (s?.kind === 'DOC_PAGE' && s.ref?.startsWith(ATTACHMENT_REF)) {
      return s.ref.slice(ATTACHMENT_REF.length);
    }
    return s?.kind === 'DOC_PAGE' ? this.pageName(s.ref ?? '') : (s?.ref ?? '');
  }

  /** Dernier segment d'une adresse de page, sans extension : « adr-005 » pour …/adr/adr-005.html. */
  protected pageName(ref: string): string {
    const last =
      ref
        .replace(/[?#].*$/, '')
        .replace(/\/+$/, '')
        .split('/')
        .pop() ?? ref;
    return last.replace(/\.html?$/, '') || ref;
  }

  protected effortLabel(kind: DeploymentFact['kind']): string {
    return EFFORTS[kind];
  }
}
