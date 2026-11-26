import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Label de reconnaissance des tickets de spec-writer (024, contracts/rest-api.md) ; `null` sans réglage. */
export interface JiraLabelView {
  label: string | null;
}

@Injectable({ providedIn: 'root' })
export class JiraLabelApi {
  private readonly http = inject(HttpClient);

  private url(projectId: string): string {
    return `/api/projects/${encodeURIComponent(projectId)}/jira-label`;
  }

  get(projectId: string): Promise<JiraLabelView> {
    return firstValueFrom(this.http.get<JiraLabelView>(this.url(projectId)));
  }

  save(projectId: string, label: string): Promise<JiraLabelView> {
    // le refus s'affiche dans la rubrique, pas dans le bandeau global
    const headers = new HttpHeaders({ 'X-Sw-Silent': '1' });
    return firstValueFrom(this.http.put<JiraLabelView>(this.url(projectId), { label }, { headers }));
  }

  remove(projectId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(this.url(projectId)));
  }
}
