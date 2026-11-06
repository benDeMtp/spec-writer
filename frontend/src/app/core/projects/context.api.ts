import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DocSource, DocSourceKind, DocumentsPreview, Project } from './projects.model';

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

  /** Les motifs ne sont envoyés que pour un dossier de documents (006). */
  addSource(projectId: string, kind: DocSourceKind, location: string, exclusions?: string[]): Promise<DocSource> {
    const body = exclusions ? { kind, location, exclusions } : { kind, location };
    return firstValueFrom(this.http.post<DocSource>(`${this.base(projectId)}/doc-sources`, body));
  }

  setExclusions(projectId: string, sourceId: string, exclusions: string[]): Promise<DocSource> {
    return firstValueFrom(
      this.http.put<DocSource>(`${this.base(projectId)}/doc-sources/${encodeURIComponent(sourceId)}/exclusions`, { exclusions }),
    );
  }

  /** Aperçu des motifs sur un dossier : rien n'est lu ni indexé. */
  preview(projectId: string, location: string, exclusions: string[]): Promise<DocumentsPreview> {
    return firstValueFrom(this.http.post<DocumentsPreview>(`${this.base(projectId)}/doc-sources/preview`, { location, exclusions }));
  }

  deleteSource(projectId: string, sourceId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(projectId)}/doc-sources/${encodeURIComponent(sourceId)}`));
  }
}
