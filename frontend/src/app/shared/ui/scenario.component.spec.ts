import { TestBed } from '@angular/core/testing';
import { Scenario } from '../../core/sessions/sessions.model';
import { ScenarioComponent } from './scenario.component';

const SCENARIO: Scenario = {
  code: 'SC-01-02',
  useCaseCode: 'UC-01',
  title: 'Paiement refusé',
  kind: 'ERROR',
  asA: 'client connecté',
  given: ['un panier de 3 articles'],
  when: ['je valide le paiement', 'ma banque refuse la transaction'],
  then: ["un message m'indique que le paiement a échoué"],
  assumption: true,
};

describe('ScenarioComponent', () => {
  function render(language: 'fr' | 'en'): HTMLElement {
    const fixture = TestBed.createComponent(ScenarioComponent);
    fixture.componentRef.setInput('scenario', SCENARIO);
    fixture.componentRef.setInput('language', language);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('écrit chaque mot-clé dans la gouttière, avec la classe de son rôle', () => {
    const el = render('fr');
    const lines = Array.from(el.querySelectorAll('.line')).map((l) => [l.className.split(' ').pop(), l.querySelector('dt')?.textContent]);
    expect(lines).toEqual([
      ['role', 'En tant que'],
      ['context', 'Étant donné'],
      ['action', 'Quand'],
      ['action', 'Et'],
      ['outcome', 'Alors'],
    ]);
    expect(el.querySelector('h4')?.textContent).toContain('SC-01-02');
    expect(el.querySelector('.badge')?.textContent).toBe('Hypothèse');
  });

  it('mots-clés anglais', () => {
    const el = render('en');
    expect(Array.from(el.querySelectorAll('dt')).map((d) => d.textContent)).toEqual(['As a', 'Given', 'When', 'And', 'Then']);
    expect(el.querySelector('.badge')?.textContent).toBe('Assumption');
  });
});
