import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export type JournalLevel = 'WARN' | 'ERROR';

/** Entrée du journal des erreurs, expurgée par le backend (FR-031). */
export interface JournalEntry {
  at: string;
  level: JournalLevel;
  logger: string;
  message: string;
  cause: string | null;
  stack: string[];
}

/** Accès à /api/journal : les 500 dernières entrées, les plus récentes d'abord. */
@Injectable({ providedIn: 'root' })
export class JournalApi {
  private readonly http = inject(HttpClient);

  latest(level?: JournalLevel): Promise<JournalEntry[]> {
    const params: Record<string, string> = level ? { level } : {};
    return firstValueFrom(this.http.get<JournalEntry[]>('/api/journal', { params }));
  }
}
