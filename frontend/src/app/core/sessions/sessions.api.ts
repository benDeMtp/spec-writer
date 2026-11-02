import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { SessionDetail, SessionSummary, Specification, VersionSummary } from './sessions.model';

/** Accès à /api/sessions et /api/sessions/{id}/versions. */
@Injectable({ providedIn: 'root' })
export class SessionsApi {
  private readonly http = inject(HttpClient);

  list(): Promise<SessionSummary[]> {
    return firstValueFrom(this.http.get<SessionSummary[]>('/api/sessions'));
  }

  rename(id: string, name: string): Promise<SessionSummary> {
    return firstValueFrom(this.http.patch<SessionSummary>(`/api/sessions/${encodeURIComponent(id)}`, { name }));
  }

  delete(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/sessions/${encodeURIComponent(id)}`));
  }

  versions(id: string): Promise<VersionSummary[]> {
    return firstValueFrom(this.http.get<VersionSummary[]>(`/api/sessions/${encodeURIComponent(id)}/versions`));
  }

  create(need: string): Promise<SessionDetail> {
    return firstValueFrom(this.http.post<SessionDetail>('/api/sessions', { need }));
  }

  get(id: string): Promise<SessionDetail> {
    return firstValueFrom(this.http.get<SessionDetail>(`/api/sessions/${encodeURIComponent(id)}`));
  }

  answer(id: string, questionId: string, value: string): Promise<SessionDetail> {
    return firstValueFrom(this.http.put<SessionDetail>(this.answerUrl(id, questionId), { value }));
  }

  skip(id: string, questionId: string): Promise<SessionDetail> {
    return firstValueFrom(this.http.put<SessionDetail>(this.answerUrl(id, questionId), { skip: true }));
  }

  conclude(id: string): Promise<SessionDetail> {
    return firstValueFrom(this.http.post<SessionDetail>(`/api/sessions/${encodeURIComponent(id)}/conclude`, {}));
  }

  generate(id: string): Promise<Specification> {
    return firstValueFrom(this.http.post<Specification>(`/api/sessions/${encodeURIComponent(id)}/versions`, {}));
  }

  version(id: string, n: number): Promise<Specification> {
    return firstValueFrom(this.http.get<Specification>(`/api/sessions/${encodeURIComponent(id)}/versions/${n}`));
  }

  private answerUrl(id: string, questionId: string): string {
    return `/api/sessions/${encodeURIComponent(id)}/questions/${encodeURIComponent(questionId)}/answer`;
  }
}
