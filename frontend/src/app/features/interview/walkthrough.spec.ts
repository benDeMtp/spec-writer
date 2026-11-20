import { Stop } from '../../core/interviews/interviews.model';
import { announcement, block, findCursor, nextGroup, nextPending, prevGroup } from './walkthrough';

function stop(element: string, step: number | null, kind: Stop['kind'], pending = false, sessionId = 's1'): Stop {
  return {
    sessionId,
    anchor: { element, step, alternative: kind === 'ALTERNATIVE' },
    kind,
    label: step ? `${element}, ${kind === 'ALTERNATIVE' ? 'alternative' : 'étape'} ${step}` : element,
    text: `Texte ${element} ${step ?? ''}`.trim(),
    items: pending ? [{ origin: 'ASSUMPTION', code: 'H-01', statement: 'Le montant est TTC.', point: null, pending: true }] : [],
  };
}

const STOPS: Stop[] = [
  stop('SUMMARY', null, 'SUMMARY'),
  stop('UC-01', null, 'USE_CASE'),
  stop('UC-01', 1, 'STEP'),
  stop('UC-01', 2, 'STEP', true),
  stop('UC-01', 1, 'ALTERNATIVE'),
  stop('SC-01-01', null, 'SCENARIO'),
  stop('UC-02', null, 'USE_CASE'),
  stop('UC-02', 1, 'STEP', true),
  stop('EF-01', null, 'REQUIREMENT'),
  stop('SUMMARY', null, 'SUMMARY', false, 's2'),
];

describe('walkthrough (021, FR-003)', () => {
  it('le bloc affiché : le use case et ses étapes, ou l’arrêt seul', () => {
    expect(block(STOPS, 3)).toEqual([1, 2, 3, 4]);
    expect(block(STOPS, 5)).toEqual([5]);
    expect(block(STOPS, 0)).toEqual([0]);
  });

  it('← → : bloc précédent ou suivant ; N : prochain point à traiter', () => {
    expect(nextGroup(STOPS, 2)).toBe(5);
    expect(nextGroup(STOPS, 5)).toBe(6);
    expect(prevGroup(STOPS, 7)).toBe(5);
    expect(prevGroup(STOPS, 3)).toBe(0);
    expect(prevGroup(STOPS, 0)).toBe(0);
    expect(nextGroup(STOPS, 9)).toBe(9);
    expect(nextPending(STOPS, 0)).toBe(3);
    expect(nextPending(STOPS, 3)).toBe(7);
    expect(nextPending(STOPS, 7)).toBeNull();
  });

  it('reprise : l’arrêt du curseur, sinon le premier', () => {
    expect(findCursor(STOPS, { sessionId: 's1', anchor: { element: 'UC-02', step: 1, alternative: false } })).toBe(7);
    expect(findCursor(STOPS, { sessionId: 's2', anchor: { element: 'SUMMARY', step: null, alternative: false } })).toBe(9);
    expect(findCursor(STOPS, { sessionId: 's1', anchor: { element: 'UC-09', step: null, alternative: false } })).toBe(0);
    expect(findCursor(STOPS, null)).toBe(0);
  });

  it('annonce : élément, texte et points', () => {
    expect(announcement(STOPS[3])).toBe('UC-01, étape 2 : Texte UC-01 2. 1 point à traiter.');
    expect(announcement(STOPS[2])).toBe('UC-01, étape 1 : Texte UC-01 1.');
  });
});
