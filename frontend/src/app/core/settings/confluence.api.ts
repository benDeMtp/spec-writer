import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Serveur Confluence de l'outil (016, contracts/rest-api.md) ; le jeton n'est jamais renvoyé. */
export interface ConfluenceSettings {
  enabled: boolean;
  serverUrl: string | null;
  caCertificatePath: string | null;
  insecureHttp: boolean;
  serverVersion: string | null;
  lastTest: { at: string; result: string } | null;
  tokenSet: boolean;
  secretPersistence: 'KEYCHAIN' | 'MEMORY_ONLY' | null;
}

export interface ConfluenceInput {
  serverUrl: string;
  caCertificatePath: string | null;
  acceptInsecureHttp: boolean;
  token?: string;
}

export type ConfluenceTestResult = 'OK' | 'UNREACHABLE' | 'UNAUTHORIZED' | 'TLS_ERROR' | 'NOT_CONFLUENCE';

export interface ConfluenceTestOutcome {
  result: ConfluenceTestResult;
  account: string | null;
  serverVersion: string | null;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class ConfluenceApi {
  private readonly http = inject(HttpClient);

  /** Réglages chargés ; `null` avant le premier chargement. */
  readonly settings = signal<ConfluenceSettings | null>(null);

  async load(): Promise<ConfluenceSettings> {
    const s = await firstValueFrom(this.http.get<ConfluenceSettings>('/api/settings/confluence'));
    this.settings.set(s);
    return s;
  }

  async save(input: ConfluenceInput): Promise<ConfluenceSettings> {
    const s = await firstValueFrom(this.http.put<ConfluenceSettings>('/api/settings/confluence', input));
    this.settings.set(s);
    return s;
  }

  async remove(): Promise<void> {
    await firstValueFrom(this.http.delete<void>('/api/settings/confluence'));
    await this.load();
  }

  async test(): Promise<ConfluenceTestOutcome> {
    const outcome = await firstValueFrom(
      this.http.post<ConfluenceTestOutcome>('/api/settings/confluence/test', {}),
    );
    await this.load();
    return outcome;
  }
}
