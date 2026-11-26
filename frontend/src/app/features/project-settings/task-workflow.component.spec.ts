import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TaskWorkflowView } from '../../core/projects/task-workflow.api';
import { TaskWorkflowComponent } from './task-workflow.component';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

const URL = '/api/projects/p1/task-workflow';

const AURORA: TaskWorkflowView = {
  arrivalStatus: null,
  finishedStatuses: [],
  known: [
    { name: 'Prête', category: 'new', seen: true },
    { name: 'En Cours', category: 'indeterminate', seen: true },
    { name: 'Réalisée', category: 'indeterminate', seen: true },
    { name: 'En cours de test', category: 'indeterminate', seen: true },
    { name: 'Fermée', category: 'done', seen: true },
  ],
};

describe('TaskWorkflowComponent (023)', () => {
  let fixture: ComponentFixture<TaskWorkflowComponent>;
  let ctrl: HttpTestingController;
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TaskWorkflowComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  function radio(name: string): HTMLInputElement {
    return el.querySelector<HTMLInputElement>(`input[type=radio][value="${name}"]`)!;
  }

  function box(name: string): HTMLInputElement {
    return el.querySelector<HTMLInputElement>(`input[type=checkbox][value="${name}"]`)!;
  }

  function button(label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label)!;
  }

  it('propose les statuts par catégorie ; « à faire » ne peut pas être le statut d’arrivée', async () => {
    ctrl.expectOne(URL).flush(AURORA);
    await settle(fixture);
    expect(Array.from(el.querySelectorAll('fieldset')[0].querySelectorAll('h4')).map((h) => h.textContent?.trim())).toEqual([
      'À faire',
      'En cours',
      'Terminé',
    ]);
    expect(radio('Prête').disabled).toBe(true);
    expect(radio('Réalisée').disabled).toBe(false);
    expect(box('Fermée').checked).toBe(true);
    expect(box('Fermée').disabled).toBe(true);
    expect(box('Fermée').closest('label')?.textContent).toContain('toujours fini');
  });

  it('le statut d’arrivée est coché d’office ; enregistrer puis retirer', async () => {
    ctrl.expectOne(URL).flush(AURORA);
    await settle(fixture);
    radio('Réalisée').click();
    await settle(fixture);
    expect(box('Réalisée').checked).toBe(true);
    expect(box('Réalisée').disabled).toBe(true);
    box('En cours de test').click();
    await settle(fixture);
    button('Enregistrer').click();
    const put = ctrl.expectOne({ method: 'PUT', url: URL });
    expect(put.request.body).toEqual({ arrivalStatus: 'Réalisée', finishedStatuses: ['En cours de test'] });
    put.flush({ ...AURORA, arrivalStatus: 'Réalisée', finishedStatuses: ['Réalisée', 'En cours de test'] });
    await settle(fixture);
    expect(el.querySelector('[role=status]')?.textContent).toContain('Réglage enregistré');

    button('Retirer le réglage').click();
    ctrl.expectOne({ method: 'DELETE', url: URL }).flush(null);
    await settle(fixture);
    expect(radio('Réalisée').checked).toBe(false);
  });

  it('le refus de l’API s’affiche', async () => {
    ctrl.expectOne(URL).flush({ ...AURORA, known: [...AURORA.known, { name: 'Testée', category: null, seen: false }] });
    await settle(fixture);
    expect(box('Testée').closest('label')?.textContent).toContain('plus vu dans l’index');
    radio('Réalisée').click();
    await settle(fixture);
    button('Enregistrer').click();
    ctrl
      .expectOne({ method: 'PUT', url: URL })
      .flush({ code: 'VALIDATION_FAILED', userMessage: 'Refusé par l’API.' }, { status: 422, statusText: 'KO' });
    await settle(fixture);
    expect(el.querySelector('[role=alert]')?.textContent).toContain('Refusé par l’API.');
  });

  it('sans statut connu, attendre la synchronisation de Jira', async () => {
    ctrl.expectOne(URL).flush({ arrivalStatus: null, finishedStatuses: [], known: [] });
    await settle(fixture);
    expect(el.textContent).toContain('après la synchronisation de Jira');
    expect(button('Enregistrer')).toBeUndefined();
  });
});
