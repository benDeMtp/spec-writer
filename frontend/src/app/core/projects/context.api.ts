import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ContextGeneration, ContextPreview, DocSource, DocSourceKind, Project } from './projects.model';

/** Contexte d'un projet : fiche, sources de documentation et propositions de l'IA (US2 de 002). */
@Injectable({ providedIn: 'root' })
export class ContextApi {
  private readonly http = inject(HttpClient);

  private base(projectId: string): string {
    return `/api/projects/${encodeURIComponent(projectId)}`;
  }

  save(projectId: string, markdown: string): Promise<Project> {
    return firstValueFrom(this.http.put<Project>(`${this.base(projectId)}/context`, { markdown }));
  }

  addSource(projectId: string, kind: DocSourceKind, location: string): Promise<DocSource> {
    return firstValueFrom(this.http.post<DocSource>(`${this.base(projectId)}/doc-sources`, { kind, location }));
  }

  deleteSource(projectId: string, sourceId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(projectId)}/doc-sources/${encodeURIComponent(sourceId)}`));
  }

  preview(projectId: string): Promise<ContextPreview> {
    return firstValueFrom(this.http.get<ContextPreview>(`${this.base(projectId)}/context-proposals/preview`));
  }

  /** Démarre la génération en arrière-plan (research.md R14). */
  start(projectId: string): Promise<ContextGeneration> {
    return firstValueFrom(this.http.post<ContextGeneration>(`${this.base(projectId)}/context-generations`, null));
  }

  /** Génération en cours ou dernière génération ; null s'il n'y en a aucune. Sans bandeau d'erreur. */
  async current(projectId: string): Promise<ContextGeneration | null> {
    try {
      return await firstValueFrom(
        this.http.get<ContextGeneration>(`${this.base(projectId)}/context-generations/current`, {
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
    return firstValueFrom(this.http.delete<void>(`${this.base(projectId)}/context-generations/current`));
  }

  accept(projectId: string, proposalId: string): Promise<Project> {
    return firstValueFrom(
      this.http.post<Project>(`${this.base(projectId)}/context-proposals/${encodeURIComponent(proposalId)}/accept`, null),
    );
  }
}
