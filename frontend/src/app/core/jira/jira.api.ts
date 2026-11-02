import { HttpClient, HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ProblemStore } from '../http/problem.store';
import { isProblem } from '../http/problem';
import { LinkedTicket, SessionDetail, Specification } from '../sessions/sessions.model';
import { ConflictResolution, JiraConflict, JiraIssueView, Proposal, PushPreview } from './jira.model';

/** Issue d'un envoi vers Jira. */
export type PushOutcome =
  | { kind: 'pushed'; ticket: LinkedTicket }
  | { kind: 'merge'; proposal: Proposal }
  | { kind: 'conflict'; conflict: JiraConflict };

/** Tickets Jira, modification assistée et envoi (/api/jira, /api/sessions/{id}/ai-edits et /jira). */
@Injectable({ providedIn: 'root' })
export class JiraApi {
  private readonly http = inject(HttpClient);
  private readonly problems = inject(ProblemStore);

  issue(key: string): Promise<JiraIssueView> {
    return firstValueFrom(this.http.get<JiraIssueView>(`/api/jira/issues/${encodeURIComponent(key)}`));
  }

  /** Crée une session liée au ticket ; Jira n'est pas modifié. */
  importIssue(key: string): Promise<SessionDetail> {
    return firstValueFrom(this.http.post<SessionDetail>(`/api/jira/issues/${encodeURIComponent(key)}/import`, {}));
  }

  proposeEdit(sessionId: string, instruction: string): Promise<Proposal> {
    return firstValueFrom(this.http.post<Proposal>(`${session(sessionId)}/ai-edits`, { instruction }));
  }

  acceptEdit(sessionId: string, proposalId: string): Promise<Specification> {
    return firstValueFrom(
      this.http.post<Specification>(`${session(sessionId)}/ai-edits/${encodeURIComponent(proposalId)}/accept`, {}),
    );
  }

  /** Sans session liée : aperçu de création pour le type indiqué, sinon le type par défaut. */
  preview(sessionId: string, versionNumber: number, issueTypeId?: string): Promise<PushPreview> {
    const body = { versionNumber, ...(issueTypeId ? { issueTypeId } : {}) };
    return firstValueFrom(this.http.post<PushPreview>(`${session(sessionId)}/jira/preview`, body));
  }

  /** Crée le ticket et lie la session (FR-036 à FR-038). */
  create(
    sessionId: string,
    versionNumber: number,
    issueTypeId: string,
    extraFields: Record<string, string>,
  ): Promise<LinkedTicket> {
    return firstValueFrom(
      this.http.post<LinkedTicket>(`${session(sessionId)}/jira`, { confirm: true, versionNumber, issueTypeId, extraFields }),
    );
  }

  /** Le conflit (409) est un résultat attendu : lui seul ne passe pas par le bandeau d'erreur. */
  async push(sessionId: string, versionNumber: number, resolution?: ConflictResolution): Promise<PushOutcome> {
    const body = { confirm: true, versionNumber, ...(resolution ? { conflictResolution: resolution } : {}) };
    try {
      const response = await firstValueFrom(
        this.http.put<LinkedTicket | Proposal>(`${session(sessionId)}/jira`, body, {
          observe: 'response',
          headers: new HttpHeaders({ 'X-Sw-Silent': '1' }),
        }),
      );
      return outcome(response);
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 409 && error.error?.code === 'JIRA_CONFLICT') {
        return { kind: 'conflict', conflict: error.error as JiraConflict };
      }
      if (error instanceof HttpErrorResponse && isProblem(error.error)) {
        this.problems.show({ ...error.error, status: error.status });
      }
      throw error;
    }
  }
}

function session(id: string): string {
  return `/api/sessions/${encodeURIComponent(id)}`;
}

function outcome(response: HttpResponse<LinkedTicket | Proposal>): PushOutcome {
  return response.status === 202
    ? { kind: 'merge', proposal: response.body as Proposal }
    : { kind: 'pushed', ticket: response.body as LinkedTicket };
}
