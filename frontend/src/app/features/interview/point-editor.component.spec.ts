import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProjectsStore } from '../../core/projects/projects.store';
import { EditorValue, PointEditorComponent } from './point-editor.component';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

describe('PointEditorComponent (021)', () => {
  let fixture: ComponentFixture<PointEditorComponent>;
  let el: HTMLElement;
  let saved: EditorValue[];

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  });

  function button(label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.trim() === label)!;
  }

  async function open(kind: 'SPEC' | 'REMARK' | 'QUESTION', initial: EditorValue | null = null): Promise<void> {
    fixture = TestBed.createComponent(PointEditorComponent);
    fixture.componentRef.setInput('kind', kind);
    fixture.componentRef.setInput('heading', 'Hypothèse H-01 : Le montant est TTC.');
    fixture.componentRef.setInput('initial', initial);
    saved = [];
    fixture.componentInstance.saved.subscribe((v) => saved.push(v));
    fixture.detectChanges();
    await settle(fixture);
    el = fixture.nativeElement;
  }

  function type(selector: string, value: string): void {
    const field = el.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    field.value = value;
    field.dispatchEvent(new Event('input'));
  }

  function choose(label: string): void {
    const radio = Array.from(el.querySelectorAll<HTMLElement>('mat-radio-button')).find(
      (r) => r.textContent?.trim() === label,
    );
    radio!.querySelector<HTMLInputElement>('input')!.click();
  }

  it('issues en boutons radio ; le texte prend le focus', async () => {
    await open('SPEC');
    const labels = Array.from(el.querySelectorAll('mat-radio-button')).map((r) => r.textContent?.trim());
    expect(labels).toEqual(['Confirmé', 'À modifier', 'À retirer', 'Reporté']);
    expect(el.querySelector('mat-radio-group')?.getAttribute('aria-label')).toBe('Issue du point');
    expect(document.activeElement).toBe(el.querySelector('textarea'));
    expect(el.querySelector('textarea')?.getAttribute('maxlength')).toBe('5000');
  });

  it('confirmé sans texte ; responsable et échéance seulement pour « Reporté »', async () => {
    await open('SPEC');
    expect(el.querySelector('input[name="owner"]')).toBeNull();
    choose('Confirmé');
    await settle(fixture);
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    expect(saved).toEqual([{ text: '', outcome: 'CONFIRMED', owner: null, due: null, source: null }]);
    choose('Reporté');
    await settle(fixture);
    expect(el.querySelector('input[name="owner"]')?.getAttribute('maxlength')).toBe('120');
  });

  it('un texte est exigé pour « À modifier », avec un message lié au champ', async () => {
    await open('SPEC');
    choose('À modifier');
    await settle(fixture);
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await settle(fixture);
    expect(saved).toHaveLength(0);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Écrivez ce qui a été dit');
    expect(el.querySelector('textarea')?.getAttribute('aria-describedby')).toBe('point-error');
  });

  it('Ctrl+Entrée enregistre une question, toujours reportée, avec son responsable', async () => {
    await open('QUESTION');
    expect(el.querySelector('mat-radio-group')).toBeNull();
    type('textarea', 'Qui valide en l’absence du manager ?');
    type('input[name="owner"]', ' Paul D. ');
    await settle(fixture);
    el.querySelector('textarea')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true }));
    expect(saved).toEqual([{ text: 'Qui valide en l’absence du manager ?', outcome: null, owner: 'Paul D.', due: null, source: null }]);
  });

  it('une remarque part sur « À modifier » et reprend une saisie existante', async () => {
    await open('REMARK', { text: 'Validation du manager', outcome: 'CHANGE', owner: null, due: null });
    expect(el.querySelector('textarea')?.value).toBe('Validation du manager');
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    expect(saved[0]).toEqual({ text: 'Validation du manager', outcome: 'CHANGE', owner: null, due: null, source: null });
  });

  it('« Source à mettre à jour… » cherche dans la connaissance du projet (US6)', async () => {
    TestBed.inject(ProjectsStore).current.set({ id: 'p1' } as never);
    await open('REMARK', null);
    const ctrl = TestBed.inject(HttpTestingController);
    button('Source à mettre à jour…').click();
    await settle(fixture);
    const query = el.querySelector<HTMLInputElement>('input[name="sourceQuery"]')!;
    query.value = 'facturation';
    query.dispatchEvent(new Event('input'));
    await settle(fixture);
    button('Chercher').click();
    await new Promise((resolve) => setTimeout(resolve, 0));
    const req = ctrl.expectOne((r) => r.url === '/api/projects/p1/knowledge/search');
    expect(req.request.params.get('q')).toBe('facturation');
    req.flush([{ kind: 'DOC_SECTION', ref: 'https://wiki/pages/42', title: 'Règles de facturation', excerpt: '', url: null, status: null, scope: null }]);
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
    button('Règles de facturation').click();
    fixture.detectChanges();
    expect(el.textContent).toContain('Source à mettre à jour : Règles de facturation');
    const text = el.querySelector<HTMLTextAreaElement>('textarea')!;
    text.value = 'Plafonné au TTC.';
    text.dispatchEvent(new Event('input'));
    await settle(fixture);
    button('Enregistrer').click();
    expect(saved.at(-1)?.source).toEqual({ kind: 'DOC_PAGE', ref: 'https://wiki/pages/42', title: 'Règles de facturation' });
  });
});

