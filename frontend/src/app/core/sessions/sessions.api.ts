import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Proposal } from '../jira/jira.model';
import {
  IssueComparison,
  RelatedDecision,
  RelatedIssues,
  SessionDetail,
  SessionSummary,
  Specification,
  VersionSummary,
} from './sessions.model';

/** Suite d'un choix parmi les tickets proches ; pour SAME, la session importée et sa proposition. */
export interface DecisionOutcome {
  sessionId: string;
  relatesTo: string | null;
  proposal: Proposal | null;
}

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

  /** Suppression locale d'une version ; Jira n'est pas modifié (FR-014 de 002). */
  deleteVersion(id: string, n: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/sessions/${encodeURIComponent(id)}/versions/${n}`));
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

  relatedIssues(id: string): Promise<RelatedIssues> {
    return firstValueFrom(this.http.get<RelatedIssues>(`/api/sessions/${encodeURIComponent(id)}/related-issues`));
  }

  compare(id: string, key: string): Promise<IssueComparison> {
    return firstValueFrom(
      this.http.post<IssueComparison>(
        `/api/sessions/${encodeURIComponent(id)}/related-issues/${encodeURIComponent(key)}/comparison`,
        {},
      ),
    );
  }

  decide(id: string, decision: RelatedDecision, key?: string): Promise<DecisionOutcome> {
    return firstValueFrom(
      this.http.put<DecisionOutcome>(`/api/sessions/${encodeURIComponent(id)}/related-issues/decision`, {
        decision,
        ...(key ? { key } : {}),
      }),
    );
  }

  private answerUrl(id: string, questionId: string): string {
    return `/api/sessions/${encodeURIComponent(id)}/questions/${encodeURIComponent(questionId)}/answer`;
  }
}
