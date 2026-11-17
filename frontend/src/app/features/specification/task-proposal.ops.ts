import { OUTCOME_MAX, ProposedTask } from '../../core/tasks/tasks.model';

/**
 * Corrections d'une proposition de tâches (015, FR-004) : chaque opération rend une nouvelle liste,
 * avec les préalables (positions) recalculés. Un préalable pointe toujours vers une tâche qui précède.
 */

export function rename(tasks: ProposedTask[], index: number, title: string): ProposedTask[] {
  return tasks.map((t, i) => (i === index ? { ...t, title } : t));
}

export function editOutcome(tasks: ProposedTask[], index: number, outcome: string): ProposedTask[] {
  return tasks.map((t, i) => (i === index ? { ...t, outcome } : t));
}

/** Retire une tâche ; ses dépendants perdent ce préalable. */
export function remove(tasks: ProposedTask[], index: number): ProposedTask[] {
  return tasks
    .filter((_, i) => i !== index)
    .map((t) => ({
      ...t,
      dependsOn: t.dependsOn.filter((d) => d !== index).map((d) => (d > index ? d - 1 : d)),
    }));
}

/** Déplace une tâche ; un préalable qui ne la précède plus est retiré. */
export function move(tasks: ProposedTask[], from: number, to: number): ProposedTask[] {
  if (to < 0 || to >= tasks.length || from === to) {
    return tasks;
  }
  const order = tasks.map((_, i) => i);
  const [moved] = order.splice(from, 1);
  order.splice(to, 0, moved);
  const newIndex = new Map(order.map((old, i) => [old, i]));
  return order.map((old, i) => ({
    ...tasks[old],
    dependsOn: tasks[old].dependsOn.map((d) => newIndex.get(d)!).filter((d) => d < i).sort((a, b) => a - b),
  }));
}

/** Fusionne la tâche avec la suivante : titre de la première, résultats réunis, préalables des deux. */
export function mergeWithNext(tasks: ProposedTask[], index: number): ProposedTask[] {
  if (index < 0 || index >= tasks.length - 1) {
    return tasks;
  }
  const a = tasks[index];
  const b = tasks[index + 1];
  const outcome = `${a.outcome} ; ${b.outcome}`.slice(0, OUTCOME_MAX);
  const deps = [...new Set([...a.dependsOn, ...b.dependsOn.filter((d) => d !== index)])].sort((x, y) => x - y);
  const merged: ProposedTask = { ...a, outcome, dependsOn: deps };
  const withMerged = tasks.map((t, i) => (i === index ? merged : t));
  // les dépendants de la seconde dépendent désormais de la tâche fusionnée
  const redirected = withMerged.map((t) => ({
    ...t,
    dependsOn: [...new Set(t.dependsOn.map((d) => (d === index + 1 ? index : d)))],
  }));
  return remove(redirected, index + 1);
}

export function add(tasks: ProposedTask[], title: string, outcome: string): ProposedTask[] {
  return [...tasks, { title, outcome, basis: { kind: 'NONE', ref: null }, dependsOn: [], manual: true }];
}
