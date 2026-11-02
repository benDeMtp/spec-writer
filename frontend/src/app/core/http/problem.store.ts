import { Injectable, signal } from '@angular/core';
import { Problem } from './problem';

/** Dernière erreur à afficher dans le bandeau (FR-028). */
@Injectable({ providedIn: 'root' })
export class ProblemStore {
  readonly current = signal<Problem | null>(null);

  show(problem: Problem): void {
    this.current.set(problem);
  }

  dismiss(): void {
    this.current.set(null);
  }
}
