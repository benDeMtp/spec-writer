import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { SessionDetail, Specification } from './sessions.model';

/** Accès à /api/sessions et /api/sessions/{id}/versions. */
@Injectable({ providedIn: 'root' })
export class SessionsApi {
  private readonly http = inject(HttpClient);

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
