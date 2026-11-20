import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PointView, StopItem } from '../../core/interviews/interviews.model';
import { EditorValue } from './point-editor.component';
import { PointCardComponent } from './point-card.component';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

const H1: StopItem = { origin: 'ASSUMPTION', code: 'H-01', statement: 'Le montant est TTC.', point: null, pending: true };
const Q1: StopItem = { origin: 'OPEN_QUESTION', code: 'Q-01', statement: 'Qui valide ?', point: null, pending: true };

function point(overrides: Partial<PointView>): PointView {
  return {
    id: 'p1',
    sessionId: 's1',
    anchor: null,
    origin: 'ASSUMPTION',
    originCode: 'H-01',
    text: '',
    outcome: 'CONFIRMED',
    owner: null,
    due: null,
    source: null,
    quote: null,
    createdAt: '2026-11-20T09:00:00Z',
    updatedAt: '2026-11-20T09:00:00Z',
    ...overrides,
  };
}

describe('PointCardComponent (021, réponse proposée)', () => {
  let fixture: ComponentFixture<PointCardComponent>;
  let el: HTMLElement;
  let saved: EditorValue[];

  async function open(item: StopItem): Promise<void> {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    fixture = TestBed.createComponent(PointCardComponent);
    fixture.componentRef.setInput('item', item);
    saved = [];
    fixture.componentInstance.saved.subscribe((v) => saved.push(v));
    fixture.detectChanges();
    await settle(fixture);
    el = fixture.nativeElement;
  }

  function button(label: string): HTMLButtonElement | undefined {
    return Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.trim().startsWith(label));
  }

  it('une hypothèse se présente comme la réponse proposée, dans une région nommée', async () => {
    await open(H1);
    expect(el.querySelector('[role="region"]')?.getAttribute('aria-label')).toBe('Hypothèse H-01');
    expect(el.textContent).toContain('Le montant est TTC.');
    expect(['Confirmer', 'Réécrire', 'Retirer', 'Reporter…'].every((l) => button(l))).toBe(true);
    expect(el.classList.contains('pending')).toBe(true);
  });

  it('« Confirmer » enregistre d’un clic, sans saisie', async () => {
    await open(H1);
    button('Confirmer')!.click();
    expect(saved).toEqual([{ text: '', outcome: 'CONFIRMED', owner: null, due: null, source: null }]);
  });

  it('« Retirer » enregistre d’un clic', async () => {
    await open(H1);
    button('Retirer')!.click();
    expect(saved[0].outcome).toBe('REMOVE');
  });

  it('« Réécrire » ouvre le texte de l’hypothèse, prérempli, sur « À modifier »', async () => {
    await open(H1);
    button('Réécrire')!.click();
    await settle(fixture);
    const text = el.querySelector<HTMLTextAreaElement>('textarea')!;
    expect(text.value).toBe('Le montant est TTC.');
    text.value = 'Le montant est HT.';
    text.dispatchEvent(new Event('input'));
    await settle(fixture);
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    expect(saved[0]).toMatchObject({ text: 'Le montant est HT.', outcome: 'CHANGE' });
  });

  it('« Reporter… » demande le responsable', async () => {
    await open(H1);
    button('Reporter…')!.click();
    await settle(fixture);
    expect(el.querySelector('input[name="owner"]')).not.toBeNull();
  });

  it('un point ouvert n’a pas de réponse proposée : Répondre ou Reporter', async () => {
    await open(Q1);
    expect(button('Confirmer')).toBeUndefined();
    button('Répondre')!.click();
    await settle(fixture);
    expect(el.querySelector<HTMLTextAreaElement>('textarea')!.value).toBe('');
  });

  it('un point traité se replie en une ligne, et « Modifier » le rouvre', async () => {
    await open({ ...H1, pending: false, point: point({ outcome: 'CHANGE', text: 'Le montant est HT.' }) });
    expect(el.classList.contains('pending')).toBe(false);
    expect(el.textContent).toContain('Réécrit');
    expect(el.textContent).toContain('Le montant est HT.');
    expect(button('Confirmer')).toBeUndefined();
    button('Modifier')!.click();
    await settle(fixture);
    expect(el.querySelector<HTMLTextAreaElement>('textarea')!.value).toBe('Le montant est HT.');
  });

  it('une remarque sur un passage cite le passage', async () => {
    await open({
      origin: 'REMARK',
      code: null,
      statement: null,
      pending: false,
      point: point({ origin: 'REMARK', originCode: null, outcome: 'REMOVE', quote: 'et envoyé au client' }),
    });
    expect(el.textContent).toContain('« et envoyé au client »');
    expect(el.textContent).toContain('À retirer');
  });
});
