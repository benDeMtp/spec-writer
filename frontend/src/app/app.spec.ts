import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { LaunchTokenService } from './core/launch/launch-token.service';
import { routes } from './app.routes';

describe('App (shell)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it("propose un lien d'évitement, une navigation nommée et une zone principale", () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('a.skip')?.getAttribute('href')).toBe('#contenu');
    expect(el.querySelector('nav')?.getAttribute('aria-label')).toBe('Navigation principale');
    const links = Array.from(el.querySelectorAll('nav a')).map((a) => a.textContent?.trim());
    expect(links).toEqual([
      'Conversation',
      'Spécifications',
      'Tickets Jira',
      'Connaissance du projet',
      'Réglages du projet',
      'Projets',
      'Configuration',
      'Journal',
    ]);
    expect(el.querySelector('main#contenu')).toBeTruthy();
  });

  it('session perdue : seul l’écran de relance, sans menu (012)', () => {
    TestBed.inject(LaunchTokenService).state.set('rejected');
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('h1')?.textContent).toBe('Relancez spec-writer depuis son raccourci');
    expect(el.textContent).toContain('Vos données sont conservées.');
    expect(el.querySelector('nav')).toBeNull();
    expect(el.querySelector('sw-problem-banner')).toBeNull();
    expect(el.querySelectorAll('main')).toHaveLength(1);
  });
});
