import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ElapsedComponent, formatDuration } from './elapsed.component';

describe('ElapsedComponent', () => {
  let fixture: ComponentFixture<ElapsedComponent>;

  beforeEach(() => {
    vi.useFakeTimers({ now: new Date('2026-11-10T09:00:00Z') });
    fixture = TestBed.createComponent(ElapsedComponent);
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
  });

  function shown(): string {
    return (
      (fixture.nativeElement as HTMLElement).querySelector('[role="timer"]')?.textContent?.trim() ??
      ''
    );
  }

  it('formate secondes et minutes', () => {
    expect(formatDuration(0)).toBe('0 s');
    expect(formatDuration(8_900)).toBe('8 s');
    expect(formatDuration(65_000)).toBe('1 min 05 s');
    expect(formatDuration(-5)).toBe('0 s');
  });

  it('avance chaque seconde pendant le travail, puis disparaît', () => {
    fixture.detectChanges();
    expect(shown()).toBe('Temps écoulé : 0 s');
    vi.advanceTimersByTime(3_000);
    fixture.detectChanges();
    expect(shown()).toBe('Temps écoulé : 3 s');
    fixture.componentRef.setInput('running', false);
    fixture.detectChanges();
    expect(shown()).toBe('');
  });

  it('part de l’heure de début connue du serveur', () => {
    fixture.componentRef.setInput('since', Date.now() - 42_000);
    fixture.detectChanges();
    expect(shown()).toBe('Temps écoulé : 42 s');
  });
});
