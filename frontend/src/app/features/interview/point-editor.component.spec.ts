import { ComponentFixture, TestBed } from '@angular/core/testing';
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
    expect(saved).toEqual([{ text: '', outcome: 'CONFIRMED', owner: null, due: null }]);
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
    expect(saved).toEqual([{ text: 'Qui valide en l’absence du manager ?', outcome: null, owner: 'Paul D.', due: null }]);
  });

  it('une remarque part sur « À modifier » et reprend une saisie existante', async () => {
    await open('REMARK', { text: 'Validation du manager', outcome: 'CHANGE', owner: null, due: null });
    expect(el.querySelector('textarea')?.value).toBe('Validation du manager');
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    expect(saved[0]).toEqual({ text: 'Validation du manager', outcome: 'CHANGE', owner: null, due: null });
  });
});
