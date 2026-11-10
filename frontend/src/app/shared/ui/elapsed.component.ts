import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';

/** Durée lisible : « 8 s », « 1 min 05 s ». */
export function formatDuration(ms: number): string {
  const seconds = Math.max(0, Math.floor(ms / 1000));
  if (seconds < 60) {
    return `${seconds} s`;
  }
  return `${Math.floor(seconds / 60)} min ${String(seconds % 60).padStart(2, '0')} s`;
}

/**
 * Chronomètre du travail de l'IA : part quand `running` devient vrai, ou de `since` (heure de début
 * connue du serveur, après un rechargement), et avance chaque seconde. Rôle `timer` : la durée n'est
 * pas annoncée à chaque seconde par les lecteurs d'écran.
 */
@Component({
  selector: 'sw-elapsed',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (text()) {
      <span class="elapsed" role="timer" aria-live="off"
        ><span class="sw-visually-hidden">Temps écoulé : </span>{{ text() }}</span
      >
    }
  `,
  styles: `
    .elapsed {
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
  `,
})
export class ElapsedComponent {
  readonly running = input(true);
  /** Début en millisecondes depuis l'époque ; sinon, le moment où `running` devient vrai. */
  readonly since = input<number | null>(null);

  private readonly now = signal(Date.now());
  private readonly startedAt = signal<number | null>(null);
  private timer: ReturnType<typeof setInterval> | null = null;

  protected readonly text = computed(() => {
    const start = this.since() ?? this.startedAt();
    return this.running() && start !== null ? formatDuration(this.now() - start) : '';
  });

  constructor() {
    effect(() => {
      const running = this.running();
      untracked(() => {
        this.now.set(Date.now());
        this.startedAt.set(running ? Date.now() : null);
        this.tick(running);
      });
    });
    // une heure de début reçue en cours de route s'affiche sans attendre la seconde suivante
    effect(() => {
      this.since();
      untracked(() => this.now.set(Date.now()));
    });
    inject(DestroyRef).onDestroy(() => this.tick(false));
  }

  private tick(on: boolean): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
    if (on) {
      this.timer = setInterval(() => this.now.set(Date.now()), 1000);
    }
  }
}
