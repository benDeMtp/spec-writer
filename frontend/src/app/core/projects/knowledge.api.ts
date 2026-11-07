import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  AdrList,
  Indexation,
  IndexationMode,
  KnowledgeHit,
  KnowledgeKind,
  KnowledgeStatus,
  MentionField,
  MentionState,
  MentionSuggestion,
  Project,
} from './projects.model';

/** Connaissance d'un projet : workspace Structurizr, indexations, état et recherche (US2 de 002). */
@Injectable({ providedIn: 'root' })
export class KnowledgeApi {
  private readonly http = inject(HttpClient);

  private base(projectId: string): string {
    return `/api/projects/${encodeURIComponent(projectId)}`;
  }

  setArchitecture(projectId: string, workspace: string, ownership: string | null): Promise<Project> {
    return firstValueFrom(this.http.put<Project>(`${this.base(projectId)}/architecture-source`, { workspace, ownership }));
  }

  clearArchitecture(projectId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(projectId)}/architecture-source`));
  }

  /** ADR du projet pour la page des ADR (004). */
  adrs(projectId: string): Promise<AdrList> {
    return firstValueFrom(this.http.get<AdrList>(`${this.base(projectId)}/knowledge/adrs`));
  }

  /** Indexation de toutes les sources, ou des seules sources choisies (005, mode FULL). */
  start(projectId: string, mode: IndexationMode, sources?: string[]): Promise<Indexation> {
    const body = sources ? { mode, sources } : { mode };
    return firstValueFrom(this.http.post<Indexation>(`${this.base(projectId)}/indexations`, body));
  }

  /** Indexation en cours ou dernière ; null s'il n'y en a aucune. Sans bandeau d'erreur. */
  async current(projectId: string): Promise<Indexation | null> {
    try {
      return await firstValueFrom(
        this.http.get<Indexation>(`${this.base(projectId)}/indexations/current`, {
          headers: new HttpHeaders({ 'X-Sw-Silent': '1' }),
        }),
      );
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) {
        return null;
      }
      throw e;
    }
  }

  cancel(projectId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(projectId)}/indexations/current`));
  }

  status(projectId: string): Promise<KnowledgeStatus> {
    return firstValueFrom(this.http.get<KnowledgeStatus>(`${this.base(projectId)}/knowledge`));
  }

  /** Suggestions après @ (007) : index local seul ; une erreur pendant la frappe ne montre aucun bandeau. */
  suggestMentions(projectId: string, q: string): Promise<MentionSuggestion[]> {
    return firstValueFrom(
      this.http.get<MentionSuggestion[]>(`${this.base(projectId)}/mentions`, {
        params: new HttpParams().set('q', q),
        headers: new HttpHeaders({ 'X-Sw-Silent': '1' }),
      }),
    );
  }

  /** État des mentions des champs en cours de saisie (007), avec la session si elle existe. */
  resolveMentions(projectId: string, sessionId: string | null, fields: { field: MentionField; text: string }[]): Promise<MentionState[]> {
    return firstValueFrom(
      this.http.post<MentionState[]>(
        `${this.base(projectId)}/mentions/resolve`,
        { sessionId, fields },
        { headers: new HttpHeaders({ 'X-Sw-Silent': '1' }) },
      ),
    );
  }

  search(projectId: string, q: string, kinds: KnowledgeKind[]): Promise<KnowledgeHit[]> {
    let params = new HttpParams().set('q', q);
    for (const k of kinds) {
      params = params.append('kinds', k);
    }
    return firstValueFrom(this.http.get<KnowledgeHit[]>(`${this.base(projectId)}/knowledge/search`, { params }));
  }
}
