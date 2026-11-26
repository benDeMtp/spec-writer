import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Statut Jira connu d'un projet, lu dans l'index (023) ; `seen` faux pour un statut réglé qui n'y est plus. */
export interface KnownStatus {
  name: string;
  category: 'new' | 'indeterminate' | 'done' | null;
  seen: boolean;
}

/** Suivi des tâches d'un projet dans Jira (023, contracts/rest-api.md). */
export interface TaskWorkflowView {
  arrivalStatus: string | null;
  finishedStatuses: string[];
  known: KnownStatus[];
}

@Injectable({ providedIn: 'root' })
export class TaskWorkflowApi {
  private readonly http = inject(HttpClient);

  private url(projectId: string): string {
    return `/api/projects/${encodeURIComponent(projectId)}/task-workflow`;
  }

  get(projectId: string): Promise<TaskWorkflowView> {
    return firstValueFrom(this.http.get<TaskWorkflowView>(this.url(projectId)));
  }

  save(projectId: string, arrivalStatus: string, finishedStatuses: string[]): Promise<TaskWorkflowView> {
    // le refus s'affiche dans la rubrique, pas dans le bandeau global
    const headers = new HttpHeaders({ 'X-Sw-Silent': '1' });
    return firstValueFrom(
      this.http.put<TaskWorkflowView>(this.url(projectId), { arrivalStatus, finishedStatuses }, { headers }),
    );
  }

  remove(projectId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(this.url(projectId)));
  }
}
