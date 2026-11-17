import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, map } from 'rxjs';
import { ProposedTask, TaskProposal, TasksView } from './tasks.model';

const base = (sessionId: string) => `/api/sessions/${encodeURIComponent(sessionId)}/tasks`;

/** Tâches d'une spécification (/api/sessions/{id}/tasks, 015). */
@Injectable({ providedIn: 'root' })
export class TasksApi {
  private readonly http = inject(HttpClient);

  /** Relit les statuts dans Jira et rejoue les transitions en attente avant de répondre. */
  list(sessionId: string): Promise<TasksView> {
    return firstValueFrom(this.http.get<TasksView>(base(sessionId)));
  }

  /** Proposition en cours, ou null. */
  proposal(sessionId: string): Promise<TaskProposal | null> {
    return firstValueFrom(
      this.http
        .get<TaskProposal>(`${base(sessionId)}/proposal`, { observe: 'response' })
        .pipe(map((r) => (r.status === 204 ? null : r.body))),
    );
  }

  propose(sessionId: string): Promise<TaskProposal> {
    return firstValueFrom(this.http.post<TaskProposal>(`${base(sessionId)}/proposal`, {}));
  }

  saveProposal(sessionId: string, tasks: ProposedTask[]): Promise<TaskProposal> {
    return firstValueFrom(this.http.put<TaskProposal>(`${base(sessionId)}/proposal`, { tasks }));
  }

  discardProposal(sessionId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${base(sessionId)}/proposal`));
  }

  validate(sessionId: string): Promise<TasksView> {
    return firstValueFrom(this.http.post<TasksView>(`${base(sessionId)}/proposal/validate`, {}));
  }

  createRemaining(sessionId: string): Promise<TasksView> {
    return firstValueFrom(this.http.post<TasksView>(`${base(sessionId)}/create-remaining`, {}));
  }

  add(sessionId: string, title: string, outcome: string, dependsOn: string[] = []): Promise<TasksView> {
    return firstValueFrom(this.http.post<TasksView>(base(sessionId), { title, outcome, dependsOn }));
  }

  edit(
    sessionId: string,
    taskId: string,
    change: { title?: string; position?: number; dependsOn?: string[] },
  ): Promise<TasksView> {
    return firstValueFrom(
      this.http.patch<TasksView>(`${base(sessionId)}/${encodeURIComponent(taskId)}`, change),
    );
  }

  remove(sessionId: string, taskId: string): Promise<TasksView> {
    return firstValueFrom(this.http.delete<TasksView>(`${base(sessionId)}/${encodeURIComponent(taskId)}`));
  }

  release(sessionId: string, taskId: string): Promise<TasksView> {
    return firstValueFrom(
      this.http.post<TasksView>(`${base(sessionId)}/${encodeURIComponent(taskId)}/release`, {}),
    );
  }

  sync(sessionId: string): Promise<TasksView> {
    return firstValueFrom(this.http.post<TasksView>(`${base(sessionId)}/sync`, {}));
  }

  resume(sessionId: string): Promise<TasksView> {
    return firstValueFrom(this.http.post<TasksView>(`${base(sessionId)}/resume`, {}));
  }
}
