import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { VersionsPanelComponent } from './versions-panel.component';

describe('VersionsPanelComponent', () => {
  it("liste les versions avec leur origine et marque la version affichée", () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(VersionsPanelComponent);
    fixture.componentRef.setInput('sessionId', 's1');
    fixture.componentRef.setInput('current', 2);
    fixture.componentRef.setInput('versions', [
      { number: 1, origin: 'GENERATED', createdAt: '2026-10-30T09:00:00Z', pushedToJira: false },
      { number: 2, origin: 'AI_EDIT', createdAt: '2026-10-30T10:00:00Z', pushedToJira: false },
    ]);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const links = el.querySelectorAll('a');
    expect(links[0].getAttribute('href')).toBe('/sessions/s1/versions/1');
    expect(links[0].getAttribute('aria-current')).toBeNull();
    expect(links[1].getAttribute('aria-current')).toBe('page');
    expect(el.textContent).toContain("Modifiée avec l'IA");
    expect(el.querySelector('nav')?.getAttribute('aria-label')).toBe('Versions de la spécification');
  });
});
