/** « 1 question », « 2 questions » : accord du nom avec le nombre. */
export function count(n: number, singular: string, plural: string): string {
  return `${n} ${n > 1 ? plural : singular}`;
}
