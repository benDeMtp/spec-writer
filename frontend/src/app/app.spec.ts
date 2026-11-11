import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
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
});
