import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PagerComponent } from './pager.component';

describe('PagerComponent', () => {
  let fixture: ComponentFixture<PagerComponent>;
  let el: HTMLElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(PagerComponent);
    fixture.componentRef.setInput('label', 'Pages de résultats');
    fixture.componentRef.setInput('position', '26 à 50 sur 120');
    el = fixture.nativeElement;
  });

  function button(text: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes(text))!;
  }

  it('annonce la position et émet les pages demandées', () => {
    fixture.componentRef.setInput('hasPrevious', true);
    fixture.componentRef.setInput('hasNext', true);
    fixture.detectChanges();
    expect(el.querySelector('nav')?.getAttribute('aria-label')).toBe('Pages de résultats');
    const live = el.querySelector('[aria-live="polite"]');
    expect(live?.textContent?.trim()).toBe('26 à 50 sur 120');
    const seen: string[] = [];
    fixture.componentInstance.previous.subscribe(() => seen.push('prev'));
    fixture.componentInstance.next.subscribe(() => seen.push('next'));
    button('précédente').click();
    button('suivante').click();
    expect(seen).toEqual(['prev', 'next']);
  });

  it('désactive les pages absentes et pendant un chargement', () => {
    fixture.componentRef.setInput('hasNext', true);
    fixture.detectChanges();
    expect(button('précédente').disabled).toBe(true);
    expect(button('suivante').disabled).toBe(false);
    fixture.componentRef.setInput('busy', true);
    fixture.detectChanges();
    expect(button('suivante').disabled).toBe(true);
  });
});
