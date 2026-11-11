import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Question } from '../../core/sessions/sessions.model';
import { QuestionCardComponent, chosen, toggleSuggestion } from './question-card.component';

describe('réponses suggérées à choix multiple (012)', () => {
  it('ajoute puis retire une suggestion, sans toucher au reste de la réponse', () => {
    let v = toggleSuggestion('', 'Les assistantes');
    expect(v).toBe('Les assistantes');
    v = toggleSuggestion(v, 'Les managers');
    expect(v).toBe('Les assistantes ; Les managers');
    v = `${v} ; et les visiteurs accompagnés`;
    expect(chosen(v, ['Les assistantes', 'Les managers', 'Les visiteurs'])).toEqual([
      'Les assistantes',
      'Les managers',
    ]);
    v = toggleSuggestion(v, 'Les assistantes');
    expect(v).toBe('Les managers ; et les visiteurs accompagnés');
  });

  it('reconnaît une suggestion séparée par un retour à la ligne ou sans espaces', () => {
    expect(chosen('Oui\nNon;Peut-être', ['Oui', 'Non', 'Peut-être'])).toEqual([
      'Oui',
      'Non',
      'Peut-être',
    ]);
  });
});

describe('QuestionCardComponent', () => {
  const question = {
    id: 'q1',
    theme: 'ACTORS',
    label: 'Qui réserve les salles ?',
    rationale: 'Pour savoir qui a le droit de réserver.',
    suggestions: ['Les assistantes', 'Les managers', 'Tous les employés'],
    defaultValue: 'Les assistantes',
  } as unknown as Question;

  function render() {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    const fixture = TestBed.createComponent(QuestionCardComponent);
    fixture.componentRef.setInput('question', question);
    fixture.detectChanges();
    return fixture;
  }

  it('plusieurs suggestions se choisissent ensemble, et la réponse les reprend', async () => {
    const fixture = render();
    const el: HTMLElement = fixture.nativeElement;
    const listbox = el.querySelector('mat-chip-listbox')!;
    expect(listbox.getAttribute('aria-multiselectable')).toBe('true');
    expect(el.textContent).toContain('Choisissez une ou plusieurs réponses');
    const chips = Array.from(listbox.querySelectorAll<HTMLElement>('[role=option]'));
    chips[0].click();
    chips[1].click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(el.querySelector<HTMLTextAreaElement>('textarea')!.value).toBe(
      'Les assistantes ; Les managers',
    );
    expect(chips.map((c) => c.getAttribute('aria-selected'))).toEqual(['true', 'true', 'false']);

    let sent = '';
    fixture.componentInstance.answered.subscribe((v) => (sent = v));
    chips[0].click();
    fixture.detectChanges();
    (
      Array.from(el.querySelectorAll('button')).find((b) =>
        b.textContent?.includes('Enregistrer'),
      ) as HTMLElement
    ).click();
    expect(sent).toBe('Les managers');
  });
});
