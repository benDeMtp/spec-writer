import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Proposal } from '../jira/jira.model';
import {
  Implementation,
  ImplementationStatus,
  IssueComparison,
  RelatedDecision,
  RelatedIssues,
  SessionDetail,
  SessionSummary,
  Specification,
  SubtaskReport,
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

  /** Quitter « implémentée » demande `confirmUnlock` (FR-026b de 002). */
  setStatus(id: string, status: ImplementationStatus, confirmUnlock = false): Promise<Implementation> {
    return firstValueFrom(
      this.http.put<Implementation>(`/api/sessions/${encodeURIComponent(id)}/status`, { status, confirmUnlock }),
    );
  }

  /** Nouvelle spécification en brouillon, à partir de la dernière version (FR-026b de 002). */
  evolve(id: string): Promise<SessionDetail> {
    return firstValueFrom(this.http.post<SessionDetail>(`/api/sessions/${encodeURIComponent(id)}/evolutions`, {}));
  }

  versions(id: string): Promise<VersionSummary[]> {
    return firstValueFrom(this.http.get<VersionSummary[]>(`/api/sessions/${encodeURIComponent(id)}/versions`));
  }

  /** Les documents joints avant le démarrage (009) sont rattachés avant le premier lot de questions. */
  create(need: string, attachmentIds: string[] = []): Promise<SessionDetail> {
    return firstValueFrom(
      this.http.post<SessionDetail>('/api/sessions', attachmentIds.length ? { need, attachmentIds } : { need }),
    );
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

  /** Rubrique des sous-tâches de la version ; null sans ticket lié ni sous-tâche (204). */
  async subtasks(id: string, n: number): Promise<SubtaskReport | null> {
    return (
      (await firstValueFrom(
        this.http.get<SubtaskReport | null>(`/api/sessions/${encodeURIComponent(id)}/versions/${n}/subtasks`),
      )) ?? null
    );
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
