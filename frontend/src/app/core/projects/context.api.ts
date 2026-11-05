import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DocSource, DocSourceKind, Project } from './projects.model';

/** Fiche générale et sources de documentation d'un projet (US2 de 002, research.md R25). */
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
}
