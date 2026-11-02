import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export type LaunchState = 'ready' | 'exchanged' | 'rejected';

/**
 * Échange le jeton de lancement présent dans l'URL contre le cookie de session locale
 * (research.md R14), puis retire le jeton de l'URL et de l'historique.
 */
@Injectable({ providedIn: 'root' })
export class LaunchTokenService {
  private readonly http = inject(HttpClient);
  readonly state = signal<LaunchState>('ready');

  async exchangeFromUrl(location: Location = window.location, history: History = window.history): Promise<void> {
    const url = new URL(location.href);
    const token = url.searchParams.get('launch');
    if (!token) {
      return;
    }
    url.searchParams.delete('launch');
    history.replaceState(history.state, '', url.pathname + url.search + url.hash);
    try {
      await firstValueFrom(this.http.post('/api/session-auth', { launchToken: token }));
      this.state.set('exchanged');
    } catch {
      this.state.set('rejected');
    }
  }
}
