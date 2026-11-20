import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Indexation } from '../../core/projects/projects.model';
import { IndexationProgressComponent, KNOWLEDGE_CLOCK } from './indexation-progress.component';

const RUNNING: Indexation = {
  status: 'RUNNING',
  mode: 'INCREMENTAL',
  step: 'DOCUMENTATION',
  current: 'https://docs.example.com/billing/index.html',
  done: 3,
  total: 10,
  startedAt: '2026-11-04T10:00:00Z',
  stepStartedAt: '2026-11-04T10:00:30Z',
  finishedAt: null,
  errors: [],
  tickets: null,
  sources: null,
};

describe('IndexationProgressComponent', () => {
  let fixture: ComponentFixture<IndexationProgressComponent>;
  let el: HTMLElement;

  function show(indexation: Indexation): void {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: KNOWLEDGE_CLOCK, useValue: () => Date.parse('2026-11-04T10:01:05Z') }],
    });
    fixture = TestBed.createComponent(IndexationProgressComponent);
    fixture.componentRef.setInput('indexation', indexation);
    fixture.componentRef.setInput('sources', [{ id: 'd1', kind: 'DOCUMENTATION', label: 'Facturation' }]);
    el = fixture.nativeElement;
    fixture.detectChanges();
  }

  afterEach(() => fixture?.destroy());

  it("montre l'étape, le minuteur, l'avancement et l'élément en cours d'une indexation qui tourne", () => {
    show(RUNNING);
    expect(el.querySelector('#indexation-step')?.textContent).toBe('Lecture de la documentation');
    expect(el.querySelector('[role="timer"]')?.textContent).toBe('Temps écoulé : 1 min 05 s · étape en cours depuis 35 s');
    expect(el.querySelector('progress')?.getAttribute('max')).toBe('10');
    expect(el.textContent).toContain('3 sur 10');
    expect(el.textContent).toContain('En cours : https://docs.example.com/billing/index.html');
  });

  it('« Interrompre » est remonté à l’écran qui suit l’indexation', () => {
    show(RUNNING);
    let cancelled = false;
    fixture.componentInstance.interrupt.subscribe(() => (cancelled = true));
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Interrompre')!.click();
    expect(cancelled).toBe(true);
  });

  it("donne l'issue, les sources réindexées et le détail des erreurs d'une indexation finie", () => {
    show({
      ...RUNNING,
      status: 'SUCCEEDED',
      mode: 'FULL',
      finishedAt: '2026-11-04T10:02:00Z',
      sources: ['d1'],
      errors: [{ kind: 'PAGE', location: 'https://docs.example.com/x', reason: 'HTTP', httpStatus: 404, message: null }],
    } as Indexation);
    expect(el.querySelector('.outcome')?.textContent).toBe('Indexation terminée en 2 min 00 s, avec des erreurs.');
    expect(el.textContent).toContain('Sources réindexées : Documentation : Facturation');
    expect(el.querySelector('summary')?.textContent).toBe('Détail (1 erreur)');
    expect(el.querySelector('.errors li')?.textContent).toBe('Page non lue : https://docs.example.com/x (HTTP 404)');
    expect(el.querySelector('button')).toBeNull();
  });
});
