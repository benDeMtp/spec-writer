import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DeletionMode, Project, ProjectInput } from './projects.model';

/** Accès à /api/projects et au déplacement d'une spécification. */
@Injectable({ providedIn: 'root' })
export class ProjectsApi {
  private readonly http = inject(HttpClient);

  list(): Promise<Project[]> {
    return firstValueFrom(this.http.get<Project[]>('/api/projects'));
  }

  current(): Promise<Project> {
    return firstValueFrom(this.http.get<Project>('/api/projects/current'));
  }

  select(projectId: string): Promise<void> {
    return firstValueFrom(this.http.put<void>('/api/projects/current', { projectId }));
  }

  create(input: ProjectInput): Promise<Project> {
    return firstValueFrom(this.http.post<Project>('/api/projects', input));
  }

  update(id: string, input: ProjectInput): Promise<Project> {
    return firstValueFrom(this.http.put<Project>(`/api/projects/${encodeURIComponent(id)}`, input));
  }

  delete(id: string, mode: DeletionMode, targetProjectId?: string): Promise<void> {
    const params: Record<string, string> = { sessions: mode };
    if (mode === 'MOVE' && targetProjectId) {
      params['targetProjectId'] = targetProjectId;
    }
    return firstValueFrom(this.http.delete<void>(`/api/projects/${encodeURIComponent(id)}`, { params }));
  }

  moveSession(sessionId: string, projectId: string): Promise<void> {
    return firstValueFrom(this.http.put<void>(`/api/sessions/${encodeURIComponent(sessionId)}/project`, { projectId }));
  }
}
