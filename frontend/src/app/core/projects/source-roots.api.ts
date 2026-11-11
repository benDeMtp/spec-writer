import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { SourceRoot, SourceRootPreview } from './projects.model';

/** Racines de sources du code d'un projet (013) : rien n'est copié ni indexé. */
@Injectable({ providedIn: 'root' })
export class SourceRootsApi {
  private readonly http = inject(HttpClient);

  private base(projectId: string): string {
    return `/api/projects/${encodeURIComponent(projectId)}/source-roots`;
  }

  list(projectId: string): Promise<SourceRoot[]> {
    return firstValueFrom(this.http.get<SourceRoot[]>(this.base(projectId)));
  }

  add(projectId: string, location: string, exclusions: string[]): Promise<SourceRoot> {
    return firstValueFrom(this.http.post<SourceRoot>(this.base(projectId), { location, exclusions }));
  }

  setExclusions(projectId: string, rootId: string, exclusions: string[]): Promise<SourceRoot> {
    return firstValueFrom(this.http.put<SourceRoot>(`${this.base(projectId)}/${encodeURIComponent(rootId)}/exclusions`, { exclusions }));
  }

  remove(projectId: string, rootId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(projectId)}/${encodeURIComponent(rootId)}`));
  }

  /** Modules détectés et décomptes, sans rien déclarer. */
  preview(projectId: string, location: string, exclusions: string[]): Promise<SourceRootPreview> {
    return firstValueFrom(this.http.post<SourceRootPreview>(`${this.base(projectId)}/preview`, { location, exclusions }));
  }
}
