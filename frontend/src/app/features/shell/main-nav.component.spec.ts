import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Project } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { Settings } from '../../core/settings/settings.model';
import { SettingsStore } from '../../core/settings/settings.store';
import { MainNavComponent } from './main-nav.component';

@Component({ template: '' })
class Blank {}

const AURORA: Project = {
  id: 'a',
  name: 'Aurora',
  description: null,
  jiraProjectKey: 'FACT',
  effectiveJiraProjectKey: 'FACT',
  jiraKeyWarning: null,
  sessionCount: 0,
  context: '',
  contextUpdatedAt: null,
  docSources: [],
};

describe('MainNavComponent', () => {
  let fixture: ComponentFixture<MainNavComponent>;
  let el: HTMLElement;
  let store: ProjectsStore;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [MainNavComponent],
      providers: [
        provideRouter([{ path: '**', component: Blank }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    store = TestBed.inject(ProjectsStore);
    store.current.set(AURORA);
    fixture = TestBed.createComponent(MainNavComponent);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  async function go(url: string): Promise<void> {
    await TestBed.inject(Router).navigateByUrl(url);
    fixture.detectChanges();
  }

  const groups = () => Array.from(el.querySelectorAll<HTMLElement>('nav [role="group"]'));
  const labels = (group: HTMLElement) =>
    Array.from(group.querySelectorAll('a')).map((a) => a.textContent?.replace(/\s+/g, ' ').trim());
  const current = () =>
    Array.from(el.querySelectorAll('a[aria-current="page"]')).map((a) =>
      a.textContent?.replace(/\s+/g, ' ').trim(),
    );

  it('présente un bloc Projet, nommé par le projet courant, et un bloc Application', () => {
    const [project, app] = groups();
    expect(project.getAttribute('aria-label')).toBe('Projet Aurora');
    expect(labels(project)).toEqual([
      'Conversation',
      'Spécifications',
      'Tickets Jira, projet FACT',
      'Connaissance du projet',
      'Réglages du projet',
    ]);
    expect(el.querySelector('#' + app.getAttribute('aria-labelledby'))?.textContent?.trim()).toBe(
      'Application',
    );
    expect(labels(app)).toEqual(['Projets', 'Configuration', 'Journal']);
  });

  it('n’affiche pas de clé Jira sans clé effective', () => {
    store.current.set({ ...AURORA, jiraProjectKey: null, effectiveJiraProjectKey: null });
    fixture.detectChanges();
    expect(labels(groups()[0])[2]).toBe('Tickets Jira');
  });

  it('marque l’entrée de la page, y compris sur ses pages filles', async () => {
    await go('/jira/FACT-1');
    expect(current()[0]).toContain('Tickets Jira');
    await go('/sessions/s1/versions/2');
    expect(current()).toEqual(['Spécifications']);
    await go('/projet/reglages');
    expect(current()).toEqual(['Réglages du projet']);
    await go('/');
    expect(current()).toEqual(['Conversation']);
  });

  it('ouvre et referme le menu par son bouton, Échap ou le choix d’une page', async () => {
    const toggle = el.querySelector<HTMLButtonElement>('button.toggle')!;
    const nav = el.querySelector('nav')!;
    expect(toggle.getAttribute('aria-controls')).toBe(nav.id);
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(nav.classList).toContain('open');
    // le bouton n'est affiché que sous 48 rem : jsdom refuse de lui donner le focus
    const focus = vi.spyOn(toggle, 'focus');

    el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(focus).toHaveBeenCalled();

    toggle.click();
    await go('/journal');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
  });

  it('donne l’état de Copilot et de Jira sous Configuration, en texte (012)', () => {
    const settings = TestBed.inject(SettingsStore);
    settings.settings.set({ copilot: { state: 'CONNECTED' }, jira: null } as unknown as Settings);
    fixture.detectChanges();
    const states = el.querySelector('ul[aria-label="État des connexions"]')!;
    expect(Array.from(states.querySelectorAll('li')).map((li) => li.textContent?.trim())).toEqual([
      'Copilot : connecté',
      'Jira : non configuré',
    ]);
    expect(states.querySelectorAll('.dot[aria-hidden="true"]')).toHaveLength(2);
    // le lien garde son nom
    expect(labels(groups()[1])).toEqual(['Projets', 'Configuration', 'Journal']);

    settings.settings.set({
      copilot: { state: 'NOT_CONFIGURED' },
      jira: { enabled: true, tokenSet: true },
    } as unknown as Settings);
    fixture.detectChanges();
    expect(Array.from(states.querySelectorAll('li')).map((li) => li.textContent?.trim())).toEqual([
      'Copilot : non connecté',
      'Jira : configuré',
    ]);
  });
});
