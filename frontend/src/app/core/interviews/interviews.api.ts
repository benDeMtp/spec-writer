import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  Anchor,
  InterviewDetail,
  InterviewReport,
  InterviewSummary,
  PointInput,
  PointView,
  PrepareInput,
  RevisionStarted,
} from './interviews.model';

const id = encodeURIComponent;

/** Routes des séances (021, contracts/rest-api.md). */
@Injectable({ providedIn: 'root' })
export class InterviewsApi {
  private readonly http = inject(HttpClient);

  /** « Faire le point » : séance d'une spécification, en cours dès sa création. */
  startForSpecification(sessionId: string, participants: string[] = []): Promise<InterviewDetail> {
    return firstValueFrom(this.http.post<InterviewDetail>(`/api/sessions/${id(sessionId)}/interviews`, { participants }));
  }

  forSpecification(sessionId: string): Promise<InterviewSummary[]> {
    return firstValueFrom(this.http.get<InterviewSummary[]>(`/api/sessions/${id(sessionId)}/interviews`));
  }

  get(interviewId: string): Promise<InterviewDetail> {
    return firstValueFrom(this.http.get<InterviewDetail>(`/api/interviews/${id(interviewId)}`));
  }

  moveCursor(interviewId: string, sessionId: string, anchor: Anchor): Promise<void> {
    return firstValueFrom(this.http.put<void>(`/api/interviews/${id(interviewId)}/cursor`, { sessionId, ...anchor }));
  }

  addPoint(interviewId: string, point: PointInput): Promise<PointView> {
    return firstValueFrom(this.http.post<PointView>(`/api/interviews/${id(interviewId)}/points`, point));
  }

  updatePoint(interviewId: string, pointId: string, point: PointInput): Promise<PointView> {
    return firstValueFrom(this.http.put<PointView>(`/api/interviews/${id(interviewId)}/points/${id(pointId)}`, point));
  }

  deletePoint(interviewId: string, pointId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/interviews/${id(interviewId)}/points/${id(pointId)}`));
  }

  finish(interviewId: string): Promise<InterviewDetail> {
    return firstValueFrom(this.http.post<InterviewDetail>(`/api/interviews/${id(interviewId)}/finish`, {}));
  }

  report(interviewId: string): Promise<InterviewReport> {
    return firstValueFrom(this.http.get<InterviewReport>(`/api/interviews/${id(interviewId)}/report`));
  }

  proposeRevision(interviewId: string, sessionId: string): Promise<RevisionStarted> {
    return firstValueFrom(
      this.http.post<RevisionStarted>(`/api/interviews/${id(interviewId)}/specs/${id(sessionId)}/revision`, {}),
    );
  }

  /** Séances préparées du projet courant (US5). */
  prepared(): Promise<InterviewSummary[]> {
    return firstValueFrom(this.http.get<InterviewSummary[]>('/api/interviews', { params: { state: 'PREPARED' } }));
  }

  prepare(input: PrepareInput): Promise<InterviewDetail> {
    return firstValueFrom(this.http.post<InterviewDetail>('/api/interviews', input));
  }

  update(interviewId: string, input: PrepareInput): Promise<InterviewDetail> {
    return firstValueFrom(this.http.put<InterviewDetail>(`/api/interviews/${id(interviewId)}`, input));
  }

  delete(interviewId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/interviews/${id(interviewId)}`));
  }

  start(interviewId: string, participants?: string[]): Promise<InterviewDetail> {
    return firstValueFrom(
      this.http.post<InterviewDetail>(`/api/interviews/${id(interviewId)}/start`, participants ? { participants } : {}),
    );
  }
}

