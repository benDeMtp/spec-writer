import { TestBed } from '@angular/core/testing';
import { SessionLostComponent } from './session-lost.component';

describe('SessionLostComponent', () => {
  it('dit de relancer depuis le raccourci ou le Dock, même outil déjà lancé (019, FR-019)', () => {
    const fixture = TestBed.createComponent(SessionLostComponent);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
    expect(text).toContain('Relancez spec-writer depuis son raccourci');
    expect(text).toContain('Dock sous macOS');
    expect(text).toContain("que l'outil soit déjà lancé ou non");
  });
});
