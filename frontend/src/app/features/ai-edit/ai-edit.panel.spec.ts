import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Proposal } from '../../core/jira/jira.model';
import { Specification } from '../../core/sessions/sessions.model';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { MENTION_DELAYS } from '../../shared/ui/mention.directive';
import { AiEditPanelComponent } from './ai-edit.panel';

const SPEC = { number: 2, title: 'Avec annulation', origin: 'AI_EDIT' } as Specification;
const PROPOSAL: Proposal = {
  proposalId: 'p1',
  baseVersion: 1,
  proposed: SPEC,
  unifiedDiff: '--- v1\n+++ proposition\n@@ -1 +1 @@\n-avant\n+après\n',
};

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

function button(el: HTMLElement, label: string): HTMLButtonElement {
  return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label)!;
}

describe('AiEditPanelComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<AiEditPanelComponent>;
  let el: HTMLElement;
  const ensure = vi.fn(async () => true);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ConsentGate, useValue: { ensure } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AiEditPanelComponent);
    fixture.componentRef.setInput('sessionId', 's1');
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  async function type(text: string): Promise<void> {
    const area = el.querySelector<HTMLTextAreaElement>('textarea')!;
    area.value = text;
    area.dispatchEvent(new Event('input'));
    await settle(fixture);
  }

  it('limite l’instruction à 2 000 caractères et refuse une instruction vide', async () => {
    expect(el.querySelector('textarea')?.getAttribute('maxlength')).toBe('2000');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await settle(fixture);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Décrivez la modification');
    ctrl.expectNone('/api/sessions/s1/ai-edits');
  });

  it('montre la proposition et ses différences sans rien appliquer, puis applique sur demande', async () => {
    const applied = vi.fn();
    fixture.componentInstance.applied.subscribe(applied);
    await type("Ajoute l'annulation");
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await settle(fixture);
    expect(ensure).toHaveBeenCalledWith('COPILOT');
    const req = ctrl.expectOne({ method: 'POST', url: '/api/sessions/s1/ai-edits' });
    expect(req.request.body).toEqual({ instruction: "Ajoute l'annulation" });
    req.flush(PROPOSAL);
    await settle(fixture);
    expect(el.textContent).toContain('elle deviendra la version 2');
    expect(el.querySelector('sw-unified-diff')?.textContent).toContain('Ajouté : ');
    expect(applied).not.toHaveBeenCalled();

    button(el, 'Appliquer').click();
    ctrl.expectOne({ method: 'POST', url: '/api/sessions/s1/ai-edits/p1/accept' }).flush(SPEC);
    await settle(fixture);
    expect(applied).toHaveBeenCalledWith(SPEC);
    expect(el.querySelector('[role="status"]')?.textContent).toBe('Version 2 créée.');
  });

  it('abandonner ne crée pas de version', async () => {
    fixture.componentRef.setInput('initialProposal', PROPOSAL);
    await settle(fixture);
    button(el, 'Abandonner').click();
    await settle(fixture);
    ctrl.expectNone('/api/sessions/s1/ai-edits/p1/accept');
    expect(el.querySelector('textarea')).toBeTruthy();
    expect(el.querySelector('[role="status"]')?.textContent).toContain('la spécification est inchangée');
  });
});

describe('AiEditPanelComponent, mentions @ (007)', () => {
  it('propose les documents du projet dans l’instruction et montre l’état de la mention', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: ConsentGate, useValue: { ensure: async () => true } },
        { provide: MENTION_DELAYS, useValue: { suggest: 0, resolve: 0 } },
      ],
    });
    const ctrl = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AiEditPanelComponent);
    fixture.componentRef.setInput('sessionId', 's1');
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.detectChanges();
    // le formulaire enregistre son champ au tour suivant
    await settle(fixture);
    const area = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    area.focus();
    area.value = 'Ajoute ce que dit @Suiv';
    area.setSelectionRange(area.value.length, area.value.length);
    area.dispatchEvent(new Event('input'));
    for (let i = 0; i < 3; i++) {
      await settle(fixture);
    }
        ctrl.expectOne((r) => r.url === '/api/projects/p1/mentions' && r.params.get('q') === 'Suiv').flush([]);
    const resolve = ctrl.expectOne('/api/projects/p1/mentions/resolve');
    expect(resolve.request.body).toEqual({ sessionId: 's1', fields: [{ field: 'INSTRUCTION', text: 'Ajoute ce que dit @Suiv' }] });
    resolve.flush([{ text: '@Suiv', field: 'INSTRUCTION', state: 'NOT_FOUND', document: null, candidates: [], transmittedShare: 0 }]);
    for (let i = 0; i < 3; i++) {
      await settle(fixture);
    }
    expect(fixture.nativeElement.textContent).toContain('introuvable');
    ctrl.verify();
  });
});
