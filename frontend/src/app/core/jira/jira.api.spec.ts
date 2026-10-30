import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { problemInterceptor } from '../http/problem.interceptor';
import { ProblemStore } from '../http/problem.store';
import { JiraApi } from './jira.api';

describe('JiraApi.push', () => {
  let api: JiraApi;
  let ctrl: HttpTestingController;
  let problems: ProblemStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([problemInterceptor])), provideHttpClientTesting()],
    });
    api = TestBed.inject(JiraApi);
    ctrl = TestBed.inject(HttpTestingController);
    problems = TestBed.inject(ProblemStore);
  });

  it('envoie la confirmation et la version, et renvoie le ticket', async () => {
    const result = api.push('s1', 2);
    const req = ctrl.expectOne({ method: 'PUT', url: '/api/sessions/s1/jira' });
    expect(req.request.body).toEqual({ confirm: true, versionNumber: 2 });
    req.flush({ issueKey: 'PROJ-1', issueUrl: 'u', syncState: 'IN_SYNC', linkedVersionNumber: 2 });
    expect(await result).toEqual({ kind: 'pushed', ticket: expect.objectContaining({ issueKey: 'PROJ-1' }) });
  });

  it('202 : proposition de fusion', async () => {
    const result = api.push('s1', 2, 'AI_MERGE');
    const req = ctrl.expectOne('/api/sessions/s1/jira');
    expect(req.request.body.conflictResolution).toBe('AI_MERGE');
    req.flush({ proposalId: 'p1' }, { status: 202, statusText: 'Accepted' });
    expect(await result).toEqual({ kind: 'merge', proposal: { proposalId: 'p1' } });
  });

  it('409 : conflit renvoyé sans bandeau d’erreur', async () => {
    const show = vi.spyOn(problems, 'show');
    const result = api.push('s1', 2);
    ctrl.expectOne('/api/sessions/s1/jira').flush(
      { code: 'JIRA_CONFLICT', userMessage: 'm', jiraDescriptionMarkdown: 'j', localMarkdown: 'l', jiraUpdated: 'd' },
      { status: 409, statusText: 'Conflict' },
    );
    expect((await result).kind).toBe('conflict');
    expect(show).not.toHaveBeenCalled();
  });

  it('autre erreur : bandeau et rejet', async () => {
    const show = vi.spyOn(problems, 'show');
    const result = api.push('s1', 2);
    ctrl.expectOne('/api/sessions/s1/jira').flush(
      { code: 'JIRA_UNREACHABLE', userMessage: 'Le serveur Jira ne répond pas.', action: 'RETRY' },
      { status: 503, statusText: 'Unavailable' },
    );
    await expect(result).rejects.toBeTruthy();
    expect(show).toHaveBeenCalledWith(expect.objectContaining({ code: 'JIRA_UNREACHABLE', status: 503 }));
  });
});
