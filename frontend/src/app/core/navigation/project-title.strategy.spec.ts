import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { Router, TitleStrategy, provideRouter } from '@angular/router';
import { Project } from '../projects/projects.model';
import { ProjectsStore } from '../projects/projects.store';
import { ProjectTitleStrategy } from './project-title.strategy';

@Component({ template: '' })
class Blank {}

const project = (name: string): Project => ({
  id: name,
  name,
  description: null,
  jiraProjectKey: null,
  effectiveJiraProjectKey: null,
  jiraKeyWarning: null,
  sessionCount: 0,
  context: '',
  contextUpdatedAt: null,
  docSources: [],
});

describe('ProjectTitleStrategy', () => {
  let title: Title;
  let store: ProjectsStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'sessions',
            title: 'Spécifications',
            data: { scope: 'project' },
            component: Blank,
          },
          { path: 'journal', title: 'Journal', component: Blank },
        ]),
        { provide: TitleStrategy, useClass: ProjectTitleStrategy },
      ],
    });
    title = TestBed.inject(Title);
    store = TestBed.inject(ProjectsStore);
    store.current.set(project('Aurora'));
  });

  it('nomme le projet courant pour une page du bloc Projet, et suit ses changements', async () => {
    await TestBed.inject(Router).navigateByUrl('/sessions');
    expect(title.getTitle()).toBe('Spécifications · Aurora · spec-writer');
    store.current.set(project('Recette'));
    TestBed.tick();
    expect(title.getTitle()).toBe('Spécifications · Recette · spec-writer');
  });

  it('ne nomme pas de projet pour une page de l’application', async () => {
    await TestBed.inject(Router).navigateByUrl('/journal');
    expect(title.getTitle()).toBe('Journal · spec-writer');
  });
});
