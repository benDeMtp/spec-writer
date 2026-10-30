import { HttpClient } from '@angular/common/http';
import { Injectable, InjectionToken, computed, inject, signal } from '@angular/core';
import { Observable, exhaustMap, firstValueFrom, takeWhile, tap, timer } from 'rxjs';
import {
  ConnectionStart,
  ConnectionStatus,
  ConsentService,
  JiraInput,
  JiraSettings,
  JiraTestOutcome,
  Settings,
} from './settings.model';

/** Intervalle de relecture de la connexion GitHub, en millisecondes. */
export const COPILOT_POLL_INTERVAL = new InjectionToken<number>('COPILOT_POLL_INTERVAL', {
  factory: () => 2000,
});

/** État de configuration partagé par les écrans (T056). Aucun secret n'y transite en lecture. */
@Injectable({ providedIn: 'root' })
export class SettingsStore {
  private readonly http = inject(HttpClient);
  private readonly pollInterval = inject(COPILOT_POLL_INTERVAL);

  readonly settings = signal<Settings | null>(null);
  readonly copilot = computed(() => this.settings()?.copilot ?? null);
  readonly jira = computed(() => this.settings()?.jira ?? null);
  readonly copilotConnected = computed(() => this.copilot()?.state === 'CONNECTED');

  async load(): Promise<Settings> {
    const settings = await firstValueFrom(this.http.get<Settings>('/api/settings'));
    this.settings.set(settings);
    return settings;
  }

  /** Charge une seule fois ; utilisé par la garde de navigation. */
  async ensureLoaded(): Promise<Settings> {
    return this.settings() ?? this.load();
  }

  startCopilotConnection(): Promise<ConnectionStart> {
    return firstValueFrom(this.http.post<ConnectionStart>('/api/settings/copilot/device-flow', {}));
  }

  /** Relit l'état jusqu'à une issue ; la dernière valeur émise est l'issue elle-même. */
  watchCopilotConnection(flowId: string): Observable<ConnectionStatus> {
    return timer(0, this.pollInterval).pipe(
      exhaustMap(() =>
        this.http.get<ConnectionStatus>(`/api/settings/copilot/device-flow/${encodeURIComponent(flowId)}`),
      ),
      takeWhile((status) => status.state === 'PENDING', true),
      tap((status) => {
        if (status.state !== 'PENDING') {
          void this.load();
        }
      }),
    );
  }

  async selectModel(model: string): Promise<void> {
    await firstValueFrom(this.http.put<void>('/api/settings/copilot/model', { model }));
    await this.load();
  }

  async disconnectCopilot(): Promise<void> {
    await firstValueFrom(this.http.delete<void>('/api/settings/copilot'));
    await this.load();
  }

  async saveJira(input: JiraInput): Promise<JiraSettings> {
    const saved = await firstValueFrom(this.http.put<JiraSettings>('/api/settings/jira', input));
    await this.load();
    return saved;
  }

  async deleteJira(): Promise<void> {
    await firstValueFrom(this.http.delete<void>('/api/settings/jira'));
    await this.load();
  }

  async testJira(): Promise<JiraTestOutcome> {
    const outcome = await firstValueFrom(this.http.post<JiraTestOutcome>('/api/settings/jira/test', {}));
    await this.load();
    return outcome;
  }

  async setConsent(service: ConsentService, granted: boolean): Promise<void> {
    await firstValueFrom(this.http.put<void>(`/api/consents/${service}`, { granted }));
    await this.load();
  }
}
