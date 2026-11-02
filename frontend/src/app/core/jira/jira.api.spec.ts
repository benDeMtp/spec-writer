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

describe('JiraApi création', () => {
  let api: JiraApi;
  let ctrl: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(JiraApi);
    ctrl = TestBed.inject(HttpTestingController);
  });

  it("l'aperçu transmet le type choisi, et seulement s'il l'est", async () => {
    void api.preview('s1', 1);
    expect(ctrl.expectOne('/api/sessions/s1/jira/preview').request.body).toEqual({ versionNumber: 1 });
    void api.preview('s1', 1, '10002');
    expect(ctrl.expectOne('/api/sessions/s1/jira/preview').request.body).toEqual({ versionNumber: 1, issueTypeId: '10002' });
  });

  it('crée le ticket avec la confirmation, le type et les champs obligatoires', async () => {
    const result = api.create('s1', 1, '10001', { customfield_1: '1' });
    const req = ctrl.expectOne({ method: 'POST', url: '/api/sessions/s1/jira' });
    expect(req.request.body).toEqual({ confirm: true, versionNumber: 1, issueTypeId: '10001', extraFields: { customfield_1: '1' } });
    req.flush({ issueKey: 'PROJ-42', issueUrl: 'u', syncState: 'IN_SYNC', linkedVersionNumber: 1 });
    expect((await result).issueKey).toBe('PROJ-42');
  });
});
