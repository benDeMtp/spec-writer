import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MentionState } from '../../core/projects/projects.model';
import { MENTION_DELAYS } from './mention.directive';
import { MentionStatusComponent } from './mention-status.component';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

const STATES: MentionState[] = [
  {
    text: '@Aurora_Matrice_Regles_Gestion_Activites_1',
    field: 'ANSWER',
    state: 'PARTIAL',
    document: { kind: 'DOCUMENT', title: 'Aurora_Matrice_Regles_Gestion_Activites_1', path: '10. MVP/Matrice.xlsx', insert: '@Aurora_Matrice_Regles_Gestion_Activites_1' },
    candidates: [],
    transmittedShare: 21,
  },
  {
    text: '@Suivi',
    field: 'ANSWER',
    state: 'AMBIGUOUS',
    document: null,
    candidates: [
      { kind: 'DOCUMENT', title: 'Suivi', path: 'Delivery/Suivi.xlsx', insert: '@Delivery/Suivi.xlsx' },
      { kind: 'DOCUMENT', title: 'Suivi', path: 'Archives/Suivi.xlsx', insert: '@Archives/Suivi.xlsx' },
    ],
    transmittedShare: 0,
  },
  { text: '@Inconnu', field: 'ANSWER', state: 'NOT_FOUND', document: null, candidates: [], transmittedShare: 0 },
  {
    text: '@ADR-2',
    field: 'ANSWER',
    state: 'SUPERSEDED',
    document: { kind: 'ADR', title: '5. Notification asynchrone', path: 'adr/0005.html', insert: '@ADR-5' },
    candidates: [],
    transmittedShare: 100,
  },
];

describe('MentionStatusComponent', () => {
  let fixture: ComponentFixture<MentionStatusComponent>;
  let ctrl: HttpTestingController;
  let el: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MENTION_DELAYS, useValue: { suggest: 0, resolve: 0 } },
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MentionStatusComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.componentRef.setInput('sessionId', 's1');
    fixture.componentRef.setInput('field', 'ANSWER');
    el = fixture.nativeElement;
  });

  afterEach(() => ctrl.verify());

  it('montre l’état de chaque mention et lève une ambiguïté en réécrivant la mention', async () => {
    const text = 'voir @Aurora_Matrice_Regles_Gestion_Activites_1, @Suivi, @Inconnu et @ADR-2';
    fixture.componentRef.setInput('text', text);
    await settle(fixture);
    const req = ctrl.expectOne('/api/projects/p1/mentions/resolve');
    expect(req.request.body).toEqual({ sessionId: 's1', fields: [{ field: 'ANSWER', text }] });
    req.flush(STATES);
    await settle(fixture);

    const lines = Array.from(el.querySelectorAll('li')).map((l) => l.textContent ?? '');
    expect(lines[0]).toContain('transmis en partie (environ 21 %)');
    expect(lines[1]).toContain('ambigu');
    expect(lines[2]).toContain('introuvable');
    expect(lines[3]).toContain('ADR remplacé');
    expect(lines[3]).toContain('5. Notification asynchrone');
    expect(el.querySelector('[aria-live="polite"]')).not.toBeNull();

    let rewritten = '';
    fixture.componentInstance.replaced.subscribe((t) => (rewritten = t));
    const use = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Archives/Suivi.xlsx'));
    use?.click();
    expect(rewritten).toBe('voir @Aurora_Matrice_Regles_Gestion_Activites_1, @Archives/Suivi.xlsx, @Inconnu et @ADR-2');
  });

  it('propose le modèle qui transmettrait le document en entier (008)', async () => {
    fixture.componentRef.setInput('text', '@Aurora_Matrice_Regles_Gestion_Activites_1 @Gros @Long');
    await settle(fixture);
    ctrl.expectOne('/api/projects/p1/mentions/resolve').flush([
      { ...STATES[0], betterModel: { id: 'grand', name: 'Grand modèle', multiplier: 1 } },
      { ...STATES[0], text: '@Gros', state: 'NO_ROOM', transmittedShare: 0, betterModel: { id: 'eco', name: 'Éco', multiplier: 0.33 } },
      { ...STATES[0], text: '@Long', betterModel: { id: 'x', name: 'Sans coût', multiplier: null } },
      { ...STATES[0], text: '@Seul', betterModel: null },
    ]);
    await settle(fixture);

    const lines = Array.from(el.querySelectorAll('li')).map((l) => (l.textContent ?? '').replace(/\s+/g, ' '));
    expect(lines[0]).toContain('transmis en partie (environ 21 %)');
    expect(lines[0]).toContain('transmis en entier avec Grand modèle (×1)');
    expect(lines[1]).toContain('non transmis : plus de place');
    expect(lines[1]).toContain('transmis en entier avec Éco (×0,33)');
    expect(lines[2]).toContain('transmis en entier avec Sans coût (coût inconnu)');
    expect(lines[3]).not.toContain('transmis en entier');
    // une suggestion seulement : le modèle choisi n'est pas changé
    expect(el.querySelectorAll('button')).toHaveLength(0);
  });

  it('n’interroge rien sans @ ni sans projet', async () => {
    fixture.componentRef.setInput('text', 'sans mention');
    await settle(fixture);
    ctrl.expectNone('/api/projects/p1/mentions/resolve');
    fixture.componentRef.setInput('projectId', null);
    fixture.componentRef.setInput('text', '@Suivi');
    await settle(fixture);
    ctrl.expectNone((r) => r.url.includes('/mentions/resolve'));
    expect(el.querySelectorAll('li')).toHaveLength(0);
  });
});
