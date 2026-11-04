import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Indexation, IndexationMode, KnowledgeHit, KnowledgeKind, KnowledgeStatus, Project } from './projects.model';

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

  start(projectId: string, mode: IndexationMode): Promise<Indexation> {
    return firstValueFrom(this.http.post<Indexation>(`${this.base(projectId)}/indexations`, { mode }));
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

  search(projectId: string, q: string, kinds: KnowledgeKind[]): Promise<KnowledgeHit[]> {
    let params = new HttpParams().set('q', q);
    for (const k of kinds) {
      params = params.append('kinds', k);
    }
    return firstValueFrom(this.http.get<KnowledgeHit[]>(`${this.base(projectId)}/knowledge/search`, { params }));
  }
}
