import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideMarkdown } from 'ngx-markdown';
import { Stop } from '../../core/interviews/interviews.model';
import { Specification } from '../../core/sessions/sessions.model';
import { InterviewDocumentComponent, anchorKey } from './interview-document.component';

function stop(element: string, step: number | null, kind: Stop['kind'], alternative = false): Stop {
  return { sessionId: 's1', anchor: { element, step, alternative }, kind, label: element, text: '', items: [] };
}

const SPEC = {
  number: 1,
  origin: 'GENERATED',
  createdAt: '2026-11-20T09:00:00Z',
  title: 'Avoirs',
  summary: 'Émission des avoirs.',
  details: 'Détails utiles.',
  story: { role: 'gestionnaire', capability: 'émettre un avoir', benefit: 'rembourser', assumed: [] },
  useCases: [
    {
      code: 'UC-01',
      title: 'Émettre un avoir',
      actor: 'Gestionnaire',
      goal: 'Rembourser le client',
      preconditions: ['Facture émise'],
      nominalFlow: ['Ouvrir la facture', 'Saisir le montant'],
      alternatives: ['Montant supérieur au facturé'],
      source: null,
    },
  ],
  scenarios: [
    {
      code: 'SA-01.1',
      useCaseCode: 'UC-01',
      title: 'Avoir partiel',
      kind: 'NOMINAL',
      asA: 'gestionnaire',
      given: ['une facture de 100 €'],
      when: ['je saisis 40 €'],
      then: ["l'avoir est de 40 €"],
      assumption: false,
    },
  ],
  assumptions: [],
  requirements: [
    { code: 'EF-01', category: 'FUNCTIONAL', statement: 'Un avoir ne dépasse pas la facture.' },
    { code: 'ET-01', category: 'TECHNICAL', statement: 'Journaliser chaque avoir.' },
  ],
  markdown: '',
} as unknown as Specification;

@Component({
  imports: [InterviewDocumentComponent],
  template: `
    <sw-interview-document [spec]="spec" [stops]="stops()" [slot]="slot">
      <ng-template #slot let-stop><span class="slot">[{{ stop.anchor.element }}{{ stop.anchor.step ?? '' }}]</span></ng-template>
    </sw-interview-document>
  `,
})
class Host {
  spec = SPEC;
  stops = signal<Stop[]>([
    stop('SUMMARY', null, 'SUMMARY'),
    stop('UC-01', null, 'USE_CASE'),
    stop('UC-01', 1, 'STEP'),
    stop('UC-01', 2, 'STEP'),
    stop('UC-01', 1, 'ALTERNATIVE', true),
    stop('SA-01.1', null, 'SCENARIO'),
    stop('EF-01', null, 'REQUIREMENT'),
    stop('ET-01', null, 'REQUIREMENT'),
  ]);
}

describe('InterviewDocumentComponent (021, séance en lecture du document)', () => {
  let fixture: ComponentFixture<Host>;
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideMarkdown()] });
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    el = fixture.nativeElement;
  });

  it('clé d’ancre courte, comme les ancres de la sortie du modèle', () => {
    expect(anchorKey({ element: 'UC-01', step: null, alternative: false })).toBe('UC-01');
    expect(anchorKey({ element: 'UC-01', step: 2, alternative: false })).toBe('UC-01:2');
    expect(anchorKey({ element: 'UC-01', step: 1, alternative: true })).toBe('UC-01:A1');
  });

  it('déroule tout le document, chaque use case suivi de ses scénarios', () => {
    const text = el.textContent ?? '';
    for (const part of [
      'En tant que',
      'Émission des avoirs.',
      'Détails utiles.',
      'Gestionnaire',
      'Rembourser le client',
      'Facture émise',
      'Ouvrir la facture',
      'Montant supérieur au facturé',
      'une facture de 100 €',
      'Un avoir ne dépasse pas la facture.',
      'Journaliser chaque avoir.',
    ]) {
      expect(text).toContain(part);
    }
    expect(text.indexOf('Avoir partiel')).toBeGreaterThan(text.indexOf('Montant supérieur au facturé'));
    expect(text.indexOf('Avoir partiel')).toBeLessThan(text.indexOf('Un avoir ne dépasse pas'));
  });

  it('chaque élément porte son ancre et reçoit l’emplacement juste après lui', () => {
    const anchors = Array.from(el.querySelectorAll('[data-anchor]')).map((e) => e.getAttribute('data-anchor'));
    expect(anchors).toEqual(['SUMMARY', 'UC-01', 'UC-01:1', 'UC-01:2', 'UC-01:A1', 'SA-01.1', 'EF-01', 'ET-01']);
    const step2 = el.querySelector('[data-anchor="UC-01:2"]')!;
    expect(step2.textContent).toContain('Saisir le montant');
    expect(step2.querySelector('.slot')?.textContent).toBe('[UC-012]');
  });

  it('l’énoncé de story et les détails se notent sur le résumé', () => {
    const summary = el.querySelector('[data-anchor="SUMMARY"]')!;
    expect(summary.textContent).toContain('En tant que');
    expect(summary.textContent).toContain('Détails utiles.');
    // les points du résumé viennent après les détails
    const text = summary.textContent ?? '';
    expect(text.indexOf('[SUMMARY]')).toBeGreaterThan(text.indexOf('Détails utiles.'));
  });

  it('les points non rattachés ferment le document', () => {
    fixture.componentInstance.stops.update((s) => [
      ...s,
      { sessionId: 's1', anchor: { element: 'UNANCHORED', step: null, alternative: false }, kind: 'UNANCHORED', label: 'Non rattachés', text: '', items: [] },
    ]);
    fixture.detectChanges();
    const last = Array.from(el.querySelectorAll('[data-anchor]')).at(-1)!;
    expect(last.getAttribute('data-anchor')).toBe('UNANCHORED');
    expect(last.textContent).toContain('Non rattachés');
  });
});
