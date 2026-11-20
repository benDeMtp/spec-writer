import { LiveAnnouncer } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input, signal, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { InterviewsApi } from '../../core/interviews/interviews.api';
import { InterviewDetail, InterviewReport } from '../../core/interviews/interviews.model';

/**
 * Récapitulatif d'une séance (021, US2, contracts/ui.md) : le compte rendu à copier dans un mail ou
 * dans Teams, puis la révision de chaque spécification.
 */
@Component({
  selector: 'sw-interview-recap-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, RouterLink],
  template: `
    <h1>Récapitulatif de la séance</h1>
    @if (detail(); as d) {
      @if (d.state !== 'FINISHED') {
        <p class="hint">
          La séance est encore en cours.
          <a [routerLink]="['/seances', d.id]">Reprendre la séance</a>
        </p>
      }
      <section aria-labelledby="report-title">
        <h2 id="report-title">Compte rendu</h2>
        @if (report(); as r) {
          <div class="preview" [innerHTML]="r.html"></div>
          <div class="actions">
            <button mat-flat-button type="button" (click)="copy()">Copier le compte rendu</button>
            @if (copied()) {
              <span class="copied">Compte rendu copié.</span>
            }
          </div>
          @if (fallback()) {
            <p id="copy-hint" class="hint">La copie automatique est refusée par le navigateur : le texte est sélectionné, faites Ctrl+C (⌘C sur Mac).</p>
            <textarea #fallbackText class="fallback" readonly rows="12" aria-describedby="copy-hint" [value]="r.text"></textarea>
          }
        } @else {
          <p role="status">Préparation du compte rendu…</p>
        }
      </section>
      <section aria-labelledby="revision-title">
        <h2 id="revision-title">Révision</h2>
        <ul class="specs">
          @for (s of d.specs; track s.sessionId) {
            <li>
              <span class="name">« {{ s.name }} »</span>
              @if (s.appliedVersion) {
                <span>Version {{ s.appliedVersion }} appliquée.</span>
              }
              <a mat-button [routerLink]="['/sessions', s.sessionId, 'versions', s.latestVersion]">Ouvrir la spécification</a>
            </li>
          }
        </ul>
      </section>
    } @else if (failed()) {
      <p role="alert">{{ failed() }}</p>
    } @else {
      <p role="status">Chargement…</p>
    }
  `,
  styles: `
    section {
      margin: 2rem 0;
    }
    .preview {
      padding: 1rem 1.5rem;
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-field);
      background: var(--sw-sheet);
      max-width: 60rem;
    }
    .actions {
      margin-top: 1rem;
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    .copied {
      color: var(--sw-ink-soft);
    }
    .fallback {
      width: 100%;
      max-width: 60rem;
      font: inherit;
    }
    .hint {
      color: var(--sw-ink-soft);
    }
    .specs {
      list-style: none;
      padding: 0;
      display: grid;
      gap: 0.5rem;
    }
    .specs li {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.5rem 1rem;
    }
    .name {
      font-weight: 600;
    }
  `,
})
export class InterviewRecapPage {
  readonly id = input.required<string>();

  private readonly api = inject(InterviewsApi);
  private readonly announcer = inject(LiveAnnouncer);

  protected readonly detail = signal<InterviewDetail | null>(null);
  protected readonly report = signal<InterviewReport | null>(null);
  protected readonly failed = signal<string | null>(null);
  protected readonly fallback = signal(false);
  protected readonly copied = signal(false);
  private readonly fallbackText = viewChild<ElementRef<HTMLTextAreaElement>>('fallbackText');
  protected readonly finished = computed(() => this.detail()?.state === 'FINISHED');

  constructor() {
    queueMicrotask(() => void this.load());
  }

  private async load(): Promise<void> {
    try {
      const [detail, report] = await Promise.all([this.api.get(this.id()), this.api.report(this.id())]);
      this.detail.set(detail);
      this.report.set(report);
    } catch {
      this.failed.set('Le récapitulatif n’a pas pu être chargé. Rechargez la page.');
    }
  }

  /** Copie en HTML et en texte (R7) ; repli : le texte affiché, sélectionné, à copier au clavier. */
  protected async copy(): Promise<void> {
    const r = this.report();
    if (!r) {
      return;
    }
    try {
      if (typeof ClipboardItem !== 'undefined' && navigator.clipboard?.write) {
        await navigator.clipboard.write([
          new ClipboardItem({
            'text/html': new Blob([r.html], { type: 'text/html' }),
            'text/plain': new Blob([r.text], { type: 'text/plain' }),
          }),
        ]);
      } else {
        await navigator.clipboard.writeText(r.text);
      }
      this.fallback.set(false);
      this.copied.set(true);
      this.announcer.announce('Compte rendu copié.', 'polite');
    } catch {
      this.copied.set(false);
      this.fallback.set(true);
      setTimeout(() => {
        const area = this.fallbackText()?.nativeElement;
        area?.focus();
        area?.select();
      });
    }
  }
}
