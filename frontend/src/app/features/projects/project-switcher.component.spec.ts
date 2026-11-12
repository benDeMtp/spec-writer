import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MatSelect } from '@angular/material/select';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';
import { LeaveGuard } from '../../core/navigation/leave-guard';
import { Project } from '../../core/projects/projects.model';
import { ProjectSwitcherComponent } from './project-switcher.component';

@Component({ template: '' })
class Blank {}

const project = (id: string, name: string): Project => ({
  id,
  name,
  description: null,
  jiraProjectKey: null,
  effectiveJiraProjectKey: null,
  jiraKeyWarning: null,
  sessionCount: 0,
  context: '',
  contextUpdatedAt: null,
  docSources: [],
});
const AURORA = project('a', 'Aurora');
const RECETTE = project('b', 'Recette');

describe('ProjectSwitcherComponent', () => {
  let fixture: ComponentFixture<ProjectSwitcherComponent>;
  let ctrl: HttpTestingController;

  async function settle(): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function answerLoad(current: Project): void {
    ctrl.expectOne('/api/projects').flush([AURORA, RECETTE]);
    ctrl.expectOne('/api/projects/current').flush(current);
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [ProjectSwitcherComponent],
      providers: [
        provideRouter([{ path: '**', component: Blank }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProjectSwitcherComponent);
    fixture.detectChanges();
    answerLoad(AURORA);
    await settle();
  });

  const select = () =>
    fixture.debugElement.query(By.directive(MatSelect)).componentInstance as MatSelect;

  it('est libellé « Projet » et montre le projet courant', () => {
    expect(fixture.nativeElement.querySelector('mat-label')?.textContent).toBe('Projet');
    expect(select().value).toBe('a');
  });

  it('change de projet courant', async () => {
    select().valueChange.emit('b');
    await settle();
    const put = ctrl.expectOne('/api/projects/current');
    expect(put.request.body).toEqual({ projectId: 'b' });
    put.flush(null);
    await settle();
    answerLoad(RECETTE);
    await settle();
    expect(select().value).toBe('b');
  });

  it('« Gérer les projets » mène à la page Projets sans changer de projet', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
    select().value = '__projets__';
    select().valueChange.emit('__projets__');
    await settle();
    expect(navigate).toHaveBeenCalledWith('/projets');
    expect(select().value).toBe('a');
    ctrl.verify();
  });

  it('une page qui refuse de quitter garde le projet courant', async () => {
    TestBed.inject(LeaveGuard).register(() => false);
    select().value = 'b';
    select().valueChange.emit('b');
    await settle();
    ctrl.expectNone('/api/projects/current');
    expect(select().value).toBe('a');
  });

  it('sur une spécification, ouvre la liste des spécifications avant de changer de projet', async () => {
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/sessions/s1/versions/2');
    const navigate = vi.spyOn(router, 'navigateByUrl');
    select().valueChange.emit('b');
    await settle();
    expect(navigate).toHaveBeenCalledWith('/sessions');
    ctrl.expectOne('/api/projects/current').flush(null);
    await settle();
    answerLoad(RECETTE);
    await settle();
  });

  it('une spécification dont la garde refuse : rien ne change', async () => {
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/sessions/s1/versions/2');
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(false);
    select().value = 'b';
    select().valueChange.emit('b');
    await settle();
    ctrl.expectNone('/api/projects/current');
    expect(select().value).toBe('a');
  });
});
