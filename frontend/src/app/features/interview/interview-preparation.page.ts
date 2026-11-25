import { LiveAnnouncer } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Router, RouterLink } from '@angular/router';
import { InterviewsApi } from '../../core/interviews/interviews.api';
import { SessionsApi } from '../../core/sessions/sessions.api';
import { SpecAnchor, Specification } from '../../core/sessions/sessions.model';
import { reason } from '../conversation/conversation-thread.component';

/** Un point de la spécification, à garder ou à écarter de la séance. */
interface PrepPoint {
  code: string;
  text: string;
  element: string;
}

interface PrepSpec {
  sessionId: string;
  name: string;
  points: PrepPoint[];
  excluded: Set<string>;
}

/**
 * Préparation d'une séance (021, US5, contracts/ui.md) : ordre de passage, participants, points
 * écartés ; « Enregistrer » ramène aux spécifications, « Démarrer maintenant » ouvre la séance.
 */
@Component({
  selector: 'sw-interview-preparation-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, RouterLink],
  template: `
    <h1>Préparer la séance</h1>
    @if (problem()) {
      <p role="alert">{{ problem() }}</p>
    }
    @if (loaded()) {
      <section aria-labelledby="participants-title">
        <h2 id="participants-title">Participants</h2>
        <ul class="chips" aria-label="Participants retenus">
          @for (p of participants(); track p) {
            <li>
              {{ p }}
              <button mat-button type="button" [attr.aria-label]="'Retirer ' + p" (click)="removeParticipant(p)">×</button>
            </li>
          }
        </ul>
        <label class="add">
          Ajouter un participant
          <input #who type="text" maxlength="120" (keydown.enter)="addParticipant(who.value); who.value = ''; $event.preventDefault()" />
        </label>
        @if (suggested().length) {
          <p class="hint">
            Lors de la dernière séance :
            @for (p of suggested(); track p) {
              <button mat-stroked-button type="button" (click)="addParticipant(p)">{{ p }}</button>
            }
          </p>
        }
      </section>
      <section aria-labelledby="specs-title">
        <h2 id="specs-title">Spécifications, dans l'ordre de passage</h2>
        <ol class="specs">
          @for (s of specs(); track s.sessionId; let i = $index; let last = $last) {
            <li>
              <h3>« {{ s.name }} »</h3>
              <div class="order">
                <button mat-button type="button" [disabled]="i === 0" [attr.aria-label]="'Monter « ' + s.name + ' »'" (click)="move(i, -1)">
                  Monter
                </button>
                <button mat-button type="button" [disabled]="last" [attr.aria-label]="'Descendre « ' + s.name + ' »'" (click)="move(i, 1)">
                  Descendre
                </button>
              </div>
              @if (s.points.length) {
                <ul class="points">
                  @for (p of s.points; track p.code) {
                    <li [class.excluded]="s.excluded.has(p.code)">
                      <span class="code">{{ p.code }}</span>
                      @if (p.element) {
                        <span class="element">{{ p.element }}</span>
                      }
                      <span class="text">{{ p.text }}</span>
                      <button mat-button type="button" (click)="toggleExcluded(s, p.code)">
                        {{ s.excluded.has(p.code) ? 'Reprendre dans la séance' : 'Écarter de cette séance' }}
                      </button>
                    </li>
                  }
                </ul>
              } @else {
                <p class="hint">Aucun point à traiter : la séance la parcourt pour les remarques.</p>
              }
            </li>
          }
        </ol>
      </section>
      <div class="actions">
        <button mat-flat-button type="button" [disabled]="busy()" (click)="save(true)">Démarrer maintenant</button>
        <button mat-stroked-button type="button" [disabled]="busy()" (click)="save(false)">Enregistrer</button>
        <a mat-button routerLink="/sessions">Annuler</a>
      </div>
    } @else if (!problem()) {
      <p role="status">Chargement…</p>
    }
  `,
  styles: `
    section {
      max-width: var(--sw-measure);
      margin-bottom: 1.5rem;
    }
    .chips,
    .points,
    .specs {
      padding: 0;
      list-style: none;
    }
    .chips {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .chips li {
      border: 1px solid var(--sw-rule);
      border-radius: 1rem;
      padding: 0 0 0 0.75rem;
    }
    .specs > li {
      border-top: 1px solid var(--sw-rule);
      padding: 0.75rem 0;
    }
    .points li {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.25rem 0.5rem;
    }
    .points li.excluded .text {
      text-decoration: line-through;
      color: var(--sw-ink-soft);
    }
    .code {
      font-weight: 600;
    }
    .element,
    .hint {
      color: var(--sw-ink-soft);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
  `,
})
export class InterviewPreparationPage {
  readonly id = input.required<string>();

  private readonly api = inject(InterviewsApi);
  private readonly sessions = inject(SessionsApi);
  private readonly router = inject(Router);
  private readonly announcer = inject(LiveAnnouncer);

  protected readonly specs = signal<PrepSpec[]>([]);
  protected readonly participants = signal<string[]>([]);
  protected readonly suggested = signal<string[]>([]);
  protected readonly loaded = signal(false);
  protected readonly busy = signal(false);
  protected readonly problem = signal('');

  constructor() {
    queueMicrotask(() => void this.load());
  }

  protected move(index: number, delta: number): void {
    const list = [...this.specs()];
    const [moved] = list.splice(index, 1);
    list.splice(index + delta, 0, moved);
    this.specs.set(list);
    void this.announcer.announce(`« ${moved.name} » en position ${index + delta + 1} sur ${list.length}.`, 'polite');
  }

  protected toggleExcluded(spec: PrepSpec, code: string): void {
    const excluded = new Set(spec.excluded);
    if (!excluded.delete(code)) {
      excluded.add(code);
    }
    this.specs.update((all) => all.map((s) => (s === spec ? { ...s, excluded } : s)));
  }

  protected addParticipant(name: string): void {
    const kept = name.trim();
    if (kept && !this.participants().includes(kept)) {
      this.participants.update((all) => [...all, kept]);
    }
    this.suggested.update((all) => all.filter((p) => p !== kept));
  }

  protected removeParticipant(name: string): void {
    this.participants.update((all) => all.filter((p) => p !== name));
  }

  protected async save(start: boolean): Promise<void> {
    this.busy.set(true);
    this.problem.set('');
    try {
      await this.api.update(this.id(), {
        specs: this.specs().map((s) => ({ sessionId: s.sessionId, excluded: [...s.excluded] })),
        participants: this.participants(),
      });
      if (start) {
        await this.api.start(this.id());
        await this.router.navigate(['/seances', this.id()]);
      } else {
        await this.router.navigate(['/sessions']);
      }
    } catch (e) {
      this.problem.set(reason(e, 'La séance n’a pas pu être enregistrée. Réessayez.'));
    } finally {
      this.busy.set(false);
    }
  }

  private async load(): Promise<void> {
    try {
      const d = await this.api.get(this.id());
      if (d.state !== 'PREPARED') {
        await this.router.navigate(['/seances', d.id]);
        return;
      }
      const specs = await Promise.all(
        d.specs.map(async (s) => {
          const version = await this.sessions.version(s.sessionId, s.latestVersion);
          return { sessionId: s.sessionId, name: s.name, points: points(version), excluded: new Set(s.excluded) };
        }),
      );
      this.specs.set(specs);
      this.participants.set(d.participants);
      if (d.specs.length) {
        const past = await this.api.forSpecification(d.specs[0].sessionId).catch(() => []);
        const last = past.find((i) => i.state === 'FINISHED');
        this.suggested.set((last?.participants ?? []).filter((p) => !d.participants.includes(p)));
      }
      this.loaded.set(true);
    } catch (e) {
      this.problem.set(reason(e, 'La séance n’a pas pu être chargée.'));
    }
  }
}

function points(v: Specification): PrepPoint[] {
  const out: PrepPoint[] = [];
  for (const h of v.assumptions) {
    out.push({ code: h.code, text: h.statement, element: label(h.anchor) });
  }
  for (const d of v.sourceDeviations ?? []) {
    if (d.code) {
      out.push({ code: d.code, text: d.difference, element: label(d.anchor) });
    }
  }
  for (const q of v.openQuestions ?? []) {
    out.push({ code: q.code, text: q.question, element: label(q.anchor) });
  }
  return out;
}

function label(a: SpecAnchor | null | undefined): string {
  if (!a) {
    return '';
  }
  if (a.element === 'SUMMARY') {
    return 'Résumé';
  }
  return a.step == null ? a.element : `${a.element}, ${a.alternative ? 'alternative' : 'étape'} ${a.step}`;
}
