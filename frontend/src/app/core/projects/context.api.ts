import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ContextGeneration, ContextPreview, ContextQuestion, DocSource, DocSourceKind, Project, SheetKind } from './projects.model';

/** Contexte d'un projet : fiche, sources de documentation et propositions de l'IA (US2 de 002). */
@Injectable({ providedIn: 'root' })
export class ContextApi {
  private readonly http = inject(HttpClient);

  private base(projectId: string): string {
    return `/api/projects/${encodeURIComponent(projectId)}`;
  }

  save(projectId: string, sheet: SheetKind, markdown: string): Promise<Project> {
    const path = sheet === 'FUNCTIONAL' ? 'functional' : 'technical';
    return firstValueFrom(this.http.put<Project>(`${this.base(projectId)}/context/${path}`, { markdown }));
  }

  addSource(projectId: string, kind: DocSourceKind, location: string): Promise<DocSource> {
    return firstValueFrom(this.http.post<DocSource>(`${this.base(projectId)}/doc-sources`, { kind, location }));
  }

  deleteSource(projectId: string, sourceId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(projectId)}/doc-sources/${encodeURIComponent(sourceId)}`));
  }

  preview(projectId: string): Promise<ContextPreview> {
    return firstValueFrom(this.http.get<ContextPreview>(`${this.base(projectId)}/context-proposals/preview`));
  }

  /** Démarre la génération en arrière-plan (research.md R14). */
  start(projectId: string): Promise<ContextGeneration> {
    return firstValueFrom(this.http.post<ContextGeneration>(`${this.base(projectId)}/context-generations`, null));
  }

  /** Génération en cours ou dernière génération ; null s'il n'y en a aucune. Sans bandeau d'erreur. */
  async current(projectId: string): Promise<ContextGeneration | null> {
    try {
      return await firstValueFrom(
        this.http.get<ContextGeneration>(`${this.base(projectId)}/context-generations/current`, {
          headers: new HttpHeaders({ 'X-Sw-Silent': '1' }),
        }),
      );
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.status === 404) {
        return null;
      }
      throw e;
    }
  }

  cancel(projectId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(projectId)}/context-generations/current`));
  }

  accept(projectId: string, proposalId: string, sheets: SheetKind[]): Promise<Project> {
    return firstValueFrom(
      this.http.post<Project>(`${this.base(projectId)}/context-proposals/${encodeURIComponent(proposalId)}/accept`, { sheets }),
    );
  }

  /** Répond à une question de la proposition, ou la passe ; elle est enregistrée aussitôt (FR-013i). */
  answer(projectId: string, proposalId: string, questionId: string, answer: string | null, skip: boolean): Promise<ContextQuestion> {
    const url = `${this.base(projectId)}/context-proposals/${encodeURIComponent(proposalId)}/questions/${encodeURIComponent(questionId)}`;
    return firstValueFrom(this.http.put<ContextQuestion>(url, { answer, skip }));
  }

  /** Révise la proposition avec les réponses, sans relire les sources (research.md R16). */
  revise(projectId: string, proposalId: string): Promise<ContextGeneration> {
    return firstValueFrom(
      this.http.post<ContextGeneration>(`${this.base(projectId)}/context-proposals/${encodeURIComponent(proposalId)}/revise`, null),
    );
  }

  questions(projectId: string): Promise<ContextQuestion[]> {
    return firstValueFrom(this.http.get<ContextQuestion[]>(`${this.base(projectId)}/context-questions`));
  }

  updateQuestion(projectId: string, number: number, answer: string | null, skip: boolean): Promise<ContextQuestion> {
    return firstValueFrom(this.http.put<ContextQuestion>(`${this.base(projectId)}/context-questions/${number}`, { answer, skip }));
  }
}
