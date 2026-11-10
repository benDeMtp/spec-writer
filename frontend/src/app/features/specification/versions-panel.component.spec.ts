import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { VersionsPanelComponent } from './versions-panel.component';

describe('VersionsPanelComponent', () => {
  it('liste les versions avec leur origine et marque la version affichée', () => {
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
    expect(el.querySelector('nav')?.getAttribute('aria-label')).toBe(
      'Versions de la spécification',
    );
  });

  it('une version issue de la discussion mène à la conversation, tant qu’elle existe', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(VersionsPanelComponent);
    fixture.componentRef.setInput('sessionId', 's1');
    fixture.componentRef.setInput('current', 3);
    fixture.componentRef.setInput('versions', [
      { number: 1, origin: 'GENERATED', createdAt: '2026-11-10T09:00:00Z', pushedToJira: false },
      {
        number: 2,
        origin: 'DISCUSSION',
        createdAt: '2026-11-10T10:00:00Z',
        pushedToJira: false,
        conversationId: 'c1',
      },
      {
        number: 3,
        origin: 'DISCUSSION',
        createdAt: '2026-11-11T10:00:00Z',
        pushedToJira: false,
        conversationId: null,
      },
    ]);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const origins = Array.from(el.querySelectorAll('a.origin'));
    expect(origins.map((a) => [a.textContent?.trim(), a.getAttribute('href')])).toEqual([
      ['Issue de la discussion', '/conversations/c1'],
    ]);
    expect(el.querySelectorAll('li')[2].textContent).toContain('Issue de la discussion');
  });
});
