import { TestBed } from '@angular/core/testing';
import { ProposedTask, TaskProposal } from '../../core/tasks/tasks.model';
import { TaskProposalComponent } from './task-proposal.component';

const PROPOSAL: TaskProposal = {
  basis: 'MODULES',
  existing: [{ key: 'FACT-121', title: 'Maquette' }],
  tasks: [
    { title: 'A', outcome: 'R A', basis: { kind: 'MODULE', ref: 'facturation-api' }, dependsOn: [], manual: false },
    { title: 'B', outcome: 'R B', basis: { kind: 'MODULE', ref: 'portail' }, dependsOn: [0], manual: false },
  ],
};

describe('TaskProposalComponent', () => {
  function open() {
    const fixture = TestBed.createComponent(TaskProposalComponent);
    fixture.componentRef.setInput('proposal', PROPOSAL);
    const emitted: ProposedTask[][] = [];
    fixture.componentInstance.changed.subscribe((t) => emitted.push(t));
    fixture.detectChanges();
    return { el: fixture.nativeElement as HTMLElement, emitted };
  }

  function button(el: HTMLElement, label: string, nth = 0): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).filter((b) => b.textContent?.includes(label))[nth] as HTMLButtonElement;
  }

  it('montre la base, les préalables et les tâches déjà suivies', () => {
    const { el } = open();
    expect(el.textContent).toContain('fondé sur les modules touchés');
    expect(el.textContent).toContain('Module facturation-api');
    expect(el.textContent).toContain('après la tâche 1');
    expect(el.textContent).toContain('FACT-121 « Maquette »');
  });

  it('réordonne par boutons, avec un nom accessible par tâche', () => {
    const { el, emitted } = open();
    expect(button(el, 'Monter').disabled).toBe(true);
    expect(button(el, 'Monter').textContent).toContain('la tâche 1');
    button(el, 'Monter', 1).click();
    expect(emitted[0].map((t) => t.title)).toEqual(['B', 'A']);
    expect(emitted[0][1].dependsOn).toEqual([]);
  });

  it('renomme, fusionne et ajoute', () => {
    const { el, emitted } = open();
    const title = el.querySelector('input') as HTMLInputElement;
    title.value = 'A renommée';
    title.dispatchEvent(new Event('change'));
    expect(emitted[0][0].title).toBe('A renommée');
    button(el, 'Fusionner avec la suivante').click();
    expect(emitted[1].map((t) => t.title)).toEqual(['A']);
    const [newTitle, newOutcome] = Array.from(el.querySelectorAll('form input')) as HTMLInputElement[];
    newTitle.value = 'C';
    newTitle.dispatchEvent(new Event('input'));
    newOutcome.value = 'R C';
    newOutcome.dispatchEvent(new Event('input'));
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    expect(emitted[2].at(-1)).toEqual({ title: 'C', outcome: 'R C', basis: { kind: 'NONE', ref: null }, dependsOn: [], manual: true });
  });
});
