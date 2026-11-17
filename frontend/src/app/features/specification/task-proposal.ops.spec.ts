import { ProposedTask } from '../../core/tasks/tasks.model';
import { add, mergeWithNext, move, remove, rename } from './task-proposal.ops';

function t(title: string, dependsOn: number[] = []): ProposedTask {
  return { title, outcome: `R ${title}`, basis: { kind: 'USE_CASE', ref: 'UC-01' }, dependsOn, manual: false };
}

describe('corrections de la proposition', () => {
  const list = [t('A'), t('B', [0]), t('C', [0, 1])];

  it('renomme sans toucher aux autres', () => {
    expect(rename(list, 1, 'B2').map((x) => x.title)).toEqual(['A', 'B2', 'C']);
  });

  it('retire une tâche et recalcule les préalables', () => {
    const r = remove(list, 0);
    expect(r.map((x) => x.title)).toEqual(['B', 'C']);
    expect(r.map((x) => x.dependsOn)).toEqual([[], [0]]);
  });

  it('déplace une tâche et retire les préalables qui ne la précèdent plus', () => {
    const r = move(list, 2, 0);
    expect(r.map((x) => x.title)).toEqual(['C', 'A', 'B']);
    expect(r.map((x) => x.dependsOn)).toEqual([[], [], [1]]);
    expect(move(list, 0, -1)).toBe(list);
  });

  it('fusionne avec la suivante', () => {
    const r = mergeWithNext(list, 0);
    expect(r.map((x) => x.title)).toEqual(['A', 'C']);
    expect(r[0].outcome).toBe('R A ; R B');
    expect(r[1].dependsOn).toEqual([0]);
    expect(mergeWithNext(list, 2)).toBe(list);
  });

  it('ajoute une tâche manuelle sans base', () => {
    const r = add(list, 'D', 'R D');
    expect(r[3]).toEqual({ title: 'D', outcome: 'R D', basis: { kind: 'NONE', ref: null }, dependsOn: [], manual: true });
  });
});
