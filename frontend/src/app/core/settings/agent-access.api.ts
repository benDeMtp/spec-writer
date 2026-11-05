import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ImplementationStatus } from '../sessions/sessions.model';

/** Accès des agents de code par la passerelle MCP (FR-024 de 002) ; le secret n'est jamais renvoyé. */
export interface AgentAccessState {
  enabled: boolean;
  /** Commande à déclarer dans l'agent. */
  command: string;
  /** Extrait de configuration MCP prêt à copier. */
  mcpJson: string;
}

export type AgentOperation = 'LIST_PROJECTS' | 'LIST_FEATURES' | 'GET_FEATURE' | 'SET_STATUS' | 'SEARCH_KNOWLEDGE';

export interface AgentAccessEntry {
  at: string;
  agentName: string;
  operation: AgentOperation;
  projectName: string | null;
  featureTitle: string | null;
  oldStatus: ImplementationStatus | null;
  newStatus: ImplementationStatus | null;
}

/** Page de 25 entrées, les plus récentes d'abord (FR-028 de 002). */
export interface AgentAccessLog {
  startAt: number;
  total: number;
  entries: AgentAccessEntry[];
}

@Injectable({ providedIn: 'root' })
export class AgentAccessApi {
  private readonly http = inject(HttpClient);

  state(): Promise<AgentAccessState> {
    return firstValueFrom(this.http.get<AgentAccessState>('/api/settings/agent-access'));
  }

  setEnabled(enabled: boolean): Promise<void> {
    return firstValueFrom(this.http.put<void>('/api/settings/agent-access', { enabled }));
  }

  /** Les accès qui utilisaient l'ancien secret sont coupés. */
  regenerate(): Promise<void> {
    return firstValueFrom(this.http.post<void>('/api/settings/agent-access/secret', {}));
  }

  log(startAt: number): Promise<AgentAccessLog> {
    return firstValueFrom(this.http.get<AgentAccessLog>('/api/settings/agent-access/log', { params: { startAt } }));
  }
}
