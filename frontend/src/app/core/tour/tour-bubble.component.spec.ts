import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TourBubbleComponent } from './tour-bubble.component';

describe('TourBubbleComponent', () => {
  let fixture: ComponentFixture<TourBubbleComponent>;
  let el: HTMLElement;

  function show(index: number): void {
    fixture = TestBed.createComponent(TourBubbleComponent);
    fixture.componentRef.setInput('index', index);
    fixture.componentRef.setInput('total', 11);
    fixture.componentRef.setInput('title', 'Tickets Jira');
    fixture.componentRef.setInput('text', 'Les tickets du projet.');
    el = fixture.nativeElement;
    document.body.appendChild(el);
    fixture.detectChanges();
  }

  afterEach(() => el.remove());

  function labels(): string[] {
    return Array.from(el.querySelectorAll('button')).map((b) => b.textContent!.trim());
  }

  it('dialogue nommé par son titre et décrit par son texte, avec la position dans la visite', () => {
    show(6);
    const dialog = el.querySelector('[role="dialog"]')!;
    expect(dialog.getAttribute('aria-modal')).toBe('true');
    expect(el.querySelector(`#${dialog.getAttribute('aria-labelledby')}`)?.textContent).toBe('Tickets Jira');
    expect(el.querySelector(`#${dialog.getAttribute('aria-describedby')}`)?.textContent).toBe('Les tickets du projet.');
    expect(el.textContent).toContain('Arrêt 7 sur 11');
    expect(labels()).toEqual(['Précédent', 'Suivant', 'Quitter la visite']);
  });

  it('premier arrêt sans Précédent, dernier arrêt avec Terminer', () => {
    show(0);
    expect(labels()).toEqual(['Suivant', 'Quitter la visite']);
    el.remove();
    show(10);
    expect(labels()).toEqual(['Précédent', 'Terminer', 'Quitter la visite']);
  });

  it('les commandes sont remontées au service de la visite', () => {
    show(3);
    const seen: string[] = [];
    fixture.componentInstance.previous.subscribe(() => seen.push('previous'));
    fixture.componentInstance.next.subscribe(() => seen.push('next'));
    fixture.componentInstance.quit.subscribe(() => seen.push('quit'));
    el.querySelectorAll('button').forEach((b) => b.click());
    expect(seen).toEqual(['previous', 'next', 'quit']);
  });
});
