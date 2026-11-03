import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ContextPreview, ContextProposal, DocSource, DocSourceKind, Project } from './projects.model';

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

  propose(projectId: string): Promise<ContextProposal> {
    return firstValueFrom(this.http.post<ContextProposal>(`${this.base(projectId)}/context-proposals`, null));
  }

  accept(projectId: string, proposalId: string): Promise<Project> {
    return firstValueFrom(
      this.http.post<Project>(`${this.base(projectId)}/context-proposals/${encodeURIComponent(proposalId)}/accept`, null),
    );
  }
}
