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
import { MatButtonModule } from '@angular/material/button';
import { Router } from '@angular/router';
import { SessionsApi } from '../../core/sessions/sessions.api';
import { SessionDetail } from '../../core/sessions/sessions.model';
import { ProgressComponent } from './progress.component';
import { QuestionCardComponent } from './question-card.component';

/** Questionnaire : lots de questions, conclusion à tout moment, génération (US2). */
@Component({
  selector: 'sw-questionnaire-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, ProgressComponent, QuestionCardComponent],
  template: `
    @if (session(); as s) {
      <h1>{{ s.name }}</h1>
      <div class="sheet">
        <details class="need">
          <summary>Besoin initial</summary>
          <p>{{ s.need }}</p>
        </details>
        <sw-progress [answered]="s.progress.answered" [skipped]="s.progress.skipped" [total]="s.progress.total" />

        @for (w of s.warnings; track w) {
          <p class="status warn" role="alert">{{ w }}</p>
        }

        @if (s.currentBatch.length) {
          @for (q of s.currentBatch; track q.id) {
            <sw-question-card [question]="q" [busy]="busy()" (answered)="answer(q.id, $event)" (skipped)="skip(q.id)" />
          }
          <div class="conclude">
            <p class="hint">Vous pouvez terminer à tout moment : les questions restantes seront passées.</p>
            <button mat-stroked-button type="button" [disabled]="busy()" (click)="conclude()">Terminer le questionnaire</button>
          </div>
        } @else {
          <section class="ready" aria-labelledby="ready-title">
            <h2 id="ready-title" tabindex="-1">
              {{ s.state === 'GENERATED' ? 'Spécification générée' : 'Questionnaire terminé' }}
            </h2>
            <p>
              {{ s.progress.answered }} réponse{{ s.progress.answered > 1 ? 's' : '' }} et {{ s.progress.skipped }}
              hypothèse{{ s.progress.skipped > 1 ? 's' : '' }} serviront à rédiger la spécification.
            </p>
            <button mat-flat-button type="button" [disabled]="busy()" (click)="generate()">
              {{ busy() ? 'Rédaction en cours…' : s.latestVersion ? 'Générer à nouveau' : 'Générer la spécification' }}
            </button>
            @if (s.latestVersion) {
              <button mat-button type="button" (click)="openVersion(s.latestVersion)">Voir la version {{ s.latestVersion }}</button>
            }
          </section>
        }
        @if (busy()) {
          <p class="hint" role="status" aria-live="polite">{{ busyMessage() }}</p>
        }

        @if (s.answeredQuestions.length) {
          <details class="answered">
            <summary>Réponses données ({{ s.answeredQuestions.length }})</summary>
            <dl>
              @for (q of s.answeredQuestions; track q.id) {
                <dt>{{ q.label }}</dt>
                <dd>
                  {{ q.answer }}
                  @if (q.state === 'SKIPPED') {
                    <span class="badge">Hypothèse</span>
                  }
                </dd>
              }
            </dl>
          </details>
        }
      </div>
    } @else {
      <p role="status" aria-live="polite">Chargement de la session…</p>
    }
  `,
  styles: `
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
    .answered {
      margin-top: 1.5rem;
    }
    dt {
      margin-top: 0.75rem;
      font-weight: 650;
    }
    dd {
      margin: 0.25rem 0 0;
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

  /** Identifiant de session, lu dans l'URL. */
  readonly id = input.required<string>();

  protected readonly session = signal<SessionDetail | null>(null);
  protected readonly busy = signal(false);
  protected readonly busyMessage = signal('');
  private readonly cards = viewChildren(QuestionCardComponent);
  private readonly firstPendingId = computed(() => this.session()?.currentBatch[0]?.id ?? null);

  async ngOnInit(): Promise<void> {
    this.show(await this.api.get(this.id()), true);
  }

  protected answer(questionId: string, value: string): Promise<void> {
    return this.run(() => this.api.answer(this.id(), questionId, value), this.isLast());
  }

  protected skip(questionId: string): Promise<void> {
    return this.run(() => this.api.skip(this.id(), questionId), this.isLast());
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

  private async run(action: () => Promise<SessionDetail>, waitsForNextBatch: boolean): Promise<void> {
    const before = this.firstPendingId();
    this.busy.set(true);
    this.busyMessage.set(waitsForNextBatch ? 'GitHub Copilot prépare les questions suivantes…' : 'Enregistrement…');
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
