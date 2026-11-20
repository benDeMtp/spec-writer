import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export type Os = 'MACOS' | 'WINDOWS' | 'LINUX';
export type Installation = 'INSTALLER' | 'SOURCES';
export type WorkKind = 'INDEXATION' | 'AGENT_REPLY' | 'REVISION' | 'GENERATION';

/** Travail en cours : des noms seulement, jamais de contenu saisi (019, contracts/rest-api.md). */
export interface WorkItem {
  kind: WorkKind;
  project?: string | null;
  conversation?: string | null;
}

export interface ApplicationState {
  os: Os;
  installation: Installation;
  work: WorkItem[];
}

/** {@code /api/application} : état de l'outil et Quitter (019). */
@Injectable({ providedIn: 'root' })
export class ApplicationApi {
  private readonly http = inject(HttpClient);

  state(): Promise<ApplicationState> {
    return firstValueFrom(this.http.get<ApplicationState>('/api/application'));
  }

  quit(): Promise<unknown> {
    return firstValueFrom(this.http.post('/api/application/quit', {}));
  }
}
