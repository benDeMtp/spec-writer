import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TasksApi } from './tasks.api';

describe('TasksApi', () => {
  let api: TasksApi;
  let ctrl: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(TasksApi);
    ctrl = TestBed.inject(HttpTestingController);
  });

  afterEach(() => ctrl.verify());

  it('lit la proposition en cours, ou null sans proposition', async () => {
    const none = api.proposal('s1');
    ctrl.expectOne({ method: 'GET', url: '/api/sessions/s1/tasks/proposal' }).flush(null, {
      status: 204,
      statusText: 'No Content',
    });
    expect(await none).toBeNull();
  });

  it('enregistre la proposition corrigée en entier', async () => {
    const tasks = [
      { title: 'A', outcome: 'R', basis: { kind: 'NONE' as const, ref: null }, dependsOn: [], manual: true },
    ];
    const saved = api.saveProposal('s1', tasks);
    const req = ctrl.expectOne({ method: 'PUT', url: '/api/sessions/s1/tasks/proposal' });
    expect(req.request.body).toEqual({ tasks });
    req.flush({ basis: 'USE_CASES', existing: [], tasks });
    expect((await saved).tasks).toEqual(tasks);
  });

  it('modifie, libère, synchronise et reprend une tâche par ses routes', () => {
    void api.edit('s1', 't1', { position: 0 });
    ctrl.expectOne({ method: 'PATCH', url: '/api/sessions/s1/tasks/t1' }).flush({});
    void api.release('s1', 't1');
    ctrl.expectOne({ method: 'POST', url: '/api/sessions/s1/tasks/t1/release' }).flush({});
    void api.sync('s1');
    ctrl.expectOne({ method: 'POST', url: '/api/sessions/s1/tasks/sync' }).flush({});
    void api.resume('s1');
    ctrl.expectOne({ method: 'POST', url: '/api/sessions/s1/tasks/resume' }).flush({});
    void api.add('s1', 'T', 'R');
    const add = ctrl.expectOne({ method: 'POST', url: '/api/sessions/s1/tasks' });
    expect(add.request.body).toEqual({ title: 'T', outcome: 'R', dependsOn: [] });
    add.flush({});
  });
});
