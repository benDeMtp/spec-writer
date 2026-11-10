import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  afterNextRender,
  computed,
  inject,
  Injector,
  input,
  signal,
  viewChildren,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';
import { ConversationsApi } from '../../core/conversations/conversations.api';
import { SessionsApi } from '../../core/sessions/sessions.api';
import { RelatedIssues, SessionDetail } from '../../core/sessions/sessions.model';
import { ProgressComponent } from './progress.component';
import { QuestionCardComponent } from './question-card.component';
import { RelatedChoice, RelatedIssuesComponent } from './related-issues.component';
import { count } from '../../shared/text';
import { AttachmentsComponent } from '../../shared/ui/attachments.component';
import { ElapsedComponent } from '../../shared/ui/elapsed.component';

/** Questionnaire : lots de questions, conclusion à tout moment, génération (US2). */
@Component({
  selector: 'sw-questionnaire-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ElapsedComponent,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    ProgressComponent,
    QuestionCardComponent,
    RelatedIssuesComponent,
    AttachmentsComponent,
    RouterLink,
  ],
  template: `
    @if (session(); as s) {
      <h1>{{ s.name }}</h1>
      @if (origin(); as o) {
        <p class="origin">
          Issue de la conversation <a [routerLink]="['/conversations', o.id]">{{ o.name }}</a>
        </p>
      }
      <div class="sheet">
        <details class="need">
          <summary>Besoin initial</summary>
          <p>{{ s.need }}</p>
        </details>
        <sw-attachments [projectId]="s.projectId" [sessionId]="s.id" [disabled]="locked()" />
        <sw-progress
          [answered]="s.progress.answered"
          [skipped]="s.progress.skipped"
          [total]="s.progress.total"
        />

        @for (w of s.warnings; track w) {
          <p class="status warn" role="alert">{{ w }}</p>
        }
        @if (locked()) {
          <p class="status warn">
            Cette spécification est implémentée : ses réponses ne peuvent plus changer, et aucune
            nouvelle version n'est possible. Rétablissez un statut antérieur, ou créez une
            évolution, depuis la spécification.
          </p>
        }

        @if (awaiting(); as r) {
          <sw-related-issues [sessionId]="s.id" [related]="r" (decided)="onDecided($event)" />
        } @else if (s.currentBatch.length) {
          @for (q of s.currentBatch; track q.id) {
            <sw-question-card
              [question]="q"
              [busy]="busy() || locked()"
              [projectId]="s.projectId"
              [sessionId]="s.id"
              (answered)="answer(q.id, $event)"
              (skipped)="skip(q.id)"
            />
          }
          <div class="conclude">
            <p class="hint">
              Vous pouvez terminer à tout moment : les questions restantes seront passées.
            </p>
            <button
              mat-stroked-button
              type="button"
              [disabled]="busy() || locked()"
              (click)="conclude()"
            >
              Terminer le questionnaire
            </button>
          </div>
        } @else {
          <section class="ready" aria-labelledby="ready-title">
            <h2 id="ready-title" tabindex="-1">
              {{ s.state === 'GENERATED' ? 'Spécification générée' : 'Questionnaire terminé' }}
            </h2>
            <p>
              {{ s.progress.answered }} réponse{{ s.progress.answered > 1 ? 's' : '' }} et
              {{ s.progress.skipped }} hypothèse{{ s.progress.skipped > 1 ? 's' : '' }} serviront à
              rédiger la spécification.
            </p>
            <button
              mat-flat-button
              type="button"
              [disabled]="busy() || locked()"
              (click)="generate()"
            >
              {{
                busy()
                  ? 'Rédaction en cours…'
                  : s.latestVersion
                    ? 'Générer à nouveau'
                    : 'Générer la spécification'
              }}
            </button>
            @if (s.latestVersion) {
              <button mat-button type="button" (click)="openVersion(s.latestVersion)">
                Voir la version {{ s.latestVersion }}
              </button>
            }
          </section>
        }
        @if (busy()) {
          <p class="hint">
            <span role="status" aria-live="polite">{{ busyMessage() }}</span>
            <sw-elapsed [running]="busy()" />
          </p>
        }

        <p class="announce" role="status" aria-live="polite">{{ revisionMessage() }}</p>

        @if (s.answeredQuestions.length) {
          <section class="answered" aria-labelledby="answered-title">
            <h2 id="answered-title">Réponses données</h2>
            <ul>
              @for (q of s.answeredQuestions; track q.id) {
                <li>
                  <p class="label">{{ q.label }}</p>
                  @if (editing() === q.id) {
                    <mat-form-field appearance="outline" class="edit">
                      <mat-label>Nouvelle réponse</mat-label>
                      <textarea
                        matInput
                        rows="3"
                        maxlength="5000"
                        [ngModel]="draft()"
                        (ngModelChange)="draft.set($event)"
                      ></textarea>
                    </mat-form-field>
                    <div class="edit-actions">
                      <button
                        mat-flat-button
                        type="button"
                        [disabled]="busy() || locked() || !draft().trim()"
                        (click)="revise(q.id)"
                      >
                        Enregistrer la modification
                      </button>
                      <button mat-button type="button" (click)="editing.set(null)">Annuler</button>
                    </div>
                  } @else {
                    <p class="value">
                      {{ q.answer }}
                      @if (q.state === 'SKIPPED') {
                        <span class="badge">Hypothèse</span>
                      }
                    </p>
                    <button
                      mat-button
                      type="button"
                      [disabled]="busy() || locked()"
                      (click)="startEdit(q.id, q.answer)"
                      [attr.aria-label]="'Modifier la réponse à : ' + q.label"
                    >
                      Modifier
                    </button>
                  }
                </li>
              }
            </ul>
          </section>
        }
      </div>
    } @else {
      <p role="status" aria-live="polite">Chargement de la session…</p>
    }
  `,
  styles: `
    .origin {
      margin: -0.5rem 0 1rem;
      color: var(--sw-ink-soft);
    }
    h1 {
      margin: 0 0 1.5rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-3);
      font-weight: 600;
    }
    .sheet {
      max-width: var(--sw-measure);
      padding: 1.5rem clamp(1rem, 3vw, 2rem);
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-sheet);
    }
    .need {
      margin-bottom: 1.25rem;
    }
    .need summary,
    .answered summary {
      cursor: pointer;
      color: var(--sw-pencil);
    }
    .need p {
      font-family: var(--sw-font-doc);
      line-height: 1.65;
    }
    .status {
      margin: 1rem 0 0;
      padding: 0.5rem 0.75rem;
      border-left: 4px solid var(--sw-outcome);
      border-radius: var(--sw-radius-field);
    }
    .conclude,
    .ready {
      margin-top: 1.25rem;
      padding-top: 1.25rem;
      border-top: 1px solid var(--sw-rule);
    }
    .ready h2 {
      margin: 0 0 0.5rem;
      font-size: var(--sw-step-1);
      outline: none;
    }
    .hint {
      color: var(--sw-ink-soft);
    }
    .announce:empty {
      display: none;
    }
    .answered {
      margin-top: 1.5rem;
      padding-top: 1.25rem;
      border-top: 1px solid var(--sw-rule);
    }
    .answered h2 {
      margin: 0 0 0.5rem;
      font-size: var(--sw-step-1);
    }
    .answered ul {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .answered li {
      margin-bottom: 0.75rem;
    }
    .label {
      margin: 0;
      font-weight: 650;
    }
    .value {
      margin: 0.25rem 0 0;
      font-family: var(--sw-font-doc);
    }
    .edit {
      width: 100%;
      margin-top: 0.5rem;
    }
    .edit-actions {
      display: flex;
      gap: 0.5rem;
    }
    .badge {
      margin-left: 0.5rem;
      padding: 0 0.5rem;
      border: 1px solid var(--sw-outcome);
      border-radius: 999px;
      color: var(--sw-outcome);
      font-size: var(--sw-step--1);
    }
  `,
})
export class QuestionnairePage implements OnInit {
  private readonly api = inject(SessionsApi);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly conversations = inject(ConversationsApi);

  /** Identifiant de session, lu dans l'URL. */
  readonly id = input.required<string>();

  protected readonly session = signal<SessionDetail | null>(null);
  /** Conversation dont vient la spécification (010, US2). */
  protected readonly origin = signal<{ id: string; name: string } | null>(null);
  /** Implémentée : verrouillée, aucune écriture possible (FR-026a de 002). */
  protected readonly locked = computed(
    () => this.session()?.implementation?.status === 'IMPLEMENTED',
  );
  protected readonly busy = signal(false);
  protected readonly busyMessage = signal('');
  protected readonly editing = signal<string | null>(null);
  protected readonly draft = signal('');
  protected readonly revisionMessage = signal('');
  /** Tickets proches qui attendent un choix avant les questions (FR-034 de 002). */
  protected readonly awaiting = signal<RelatedIssues | null>(null);
  private readonly cards = viewChildren(QuestionCardComponent);
  private readonly firstPendingId = computed(() => this.session()?.currentBatch[0]?.id ?? null);

  async ngOnInit(): Promise<void> {
    void this.conversations.origin(this.id()).then(
      (o) => this.origin.set(o),
      () => undefined,
    );
    const detail = await this.api.get(this.id());
    if (
      detail.state === 'QUESTIONING' &&
      !detail.currentBatch.length &&
      !detail.answeredQuestions.length
    ) {
      // session créée sans questions : des tickets proches attendent peut-être un choix
      const related = await this.api.relatedIssues(this.id()).catch(() => null);
      if (related && related.decision === null && related.issues.length) {
        this.awaiting.set(related);
        this.session.set(detail);
        afterNextRender(() => document.getElementById('related-title')?.focus(), {
          injector: this.injector,
        });
        return;
      }
    }
    this.show(detail, true);
  }

  /** « C'est la même » ouvre le ticket importé avec le besoin proposé ; sinon les questions commencent. */
  protected async onDecided(choice: RelatedChoice): Promise<void> {
    if (choice.decision === 'SAME') {
      const proposal = choice.outcome.proposal;
      await this.router.navigate(
        ['/sessions', choice.outcome.sessionId, 'versions', proposal?.baseVersion ?? 1],
        {
          state: proposal ? { proposal } : {},
        },
      );
      return;
    }
    this.awaiting.set(null);
    if (choice.outcome.relatesTo) {
      this.revisionMessage.set(
        `La story rédigée sera liée à ${choice.outcome.relatesTo} par « relates to » à la création du ticket.`,
      );
    }
    this.show(await this.api.get(this.id()), true);
  }

  protected answer(questionId: string, value: string): Promise<void> {
    return this.run(() => this.api.answer(this.id(), questionId, value), this.isLast());
  }

  protected skip(questionId: string): Promise<void> {
    return this.run(() => this.api.skip(this.id(), questionId), this.isLast());
  }

  protected startEdit(questionId: string, answer: string | null): void {
    this.draft.set(answer ?? '');
    this.editing.set(questionId);
    this.revisionMessage.set('');
  }

  /** Modification d'une réponse antérieure : résumé des questions retirées et ajoutées (US3-2). */
  protected async revise(questionId: string): Promise<void> {
    this.busy.set(true);
    this.busyMessage.set('GitHub Copilot réévalue les questions qui dépendent de cette réponse…');
    try {
      const updated = await this.api.answer(this.id(), questionId, this.draft().trim());
      this.editing.set(null);
      this.revisionMessage.set(summarize(updated.revision));
      this.show(updated, updated.currentBatch.length > 0);
    } catch {
      this.show(await this.api.get(this.id()), false);
    } finally {
      this.busy.set(false);
    }
  }

  protected conclude(): Promise<void> {
    return this.run(() => this.api.conclude(this.id()), false);
  }

  protected async generate(): Promise<void> {
    this.busy.set(true);
    this.busyMessage.set('GitHub Copilot rédige la spécification. Cela peut prendre une minute.');
    try {
      const spec = await this.api.generate(this.id());
      await this.openVersion(spec.number);
    } catch {
      // Le bandeau affiche la cause et propose de réessayer ; les réponses sont conservées.
    } finally {
      this.busy.set(false);
    }
  }

  protected openVersion(n: number): Promise<boolean> {
    return this.router.navigate(['/sessions', this.id(), 'versions', n]);
  }

  private isLast(): boolean {
    return (this.session()?.currentBatch.length ?? 0) <= 1;
  }

  private async run(
    action: () => Promise<SessionDetail>,
    waitsForNextBatch: boolean,
  ): Promise<void> {
    const before = this.firstPendingId();
    this.busy.set(true);
    this.busyMessage.set(
      waitsForNextBatch ? 'GitHub Copilot prépare les questions suivantes…' : 'Enregistrement…',
    );
    try {
      const updated = await action();
      this.show(updated, updated.currentBatch[0]?.id !== before);
    } catch {
      // Réponse déjà sauvegardée côté serveur ; le bandeau propose de réessayer (FR-023a).
      this.show(await this.api.get(this.id()), false);
    } finally {
      this.busy.set(false);
    }
  }

  /** Le focus se place sur la première question de chaque nouveau lot. */
  private show(detail: SessionDetail, moveFocus: boolean): void {
    this.session.set(detail);
    if (moveFocus) {
      afterNextRender(
        () => {
          const first = this.cards()[0];
          if (first) {
            first.focus();
          } else {
            document.getElementById('ready-title')?.focus();
          }
        },
        { injector: this.injector },
      );
    }
  }
}

/** Message lu par les lecteurs d'écran après une réponse modifiée. */
export function summarize(revision: { removed: string[]; added: string[] } | undefined): string {
  const removed = revision?.removed ?? [];
  const added = revision?.added ?? [];
  if (!removed.length && !added.length) {
    return 'Réponse modifiée. Les autres réponses restent valables.';
  }
  const parts: string[] = [];
  if (removed.length) {
    parts.push(
      `${count(removed.length, 'question retirée', 'questions retirées')} : ${removed.join(' ; ')}`,
    );
  }
  if (added.length) {
    parts.push(
      `${count(added.length, 'question ajoutée', 'questions ajoutées')} : ${added.join(' ; ')}`,
    );
  }
  return `Réponse modifiée. ${parts.join('. ')}.`;
}
