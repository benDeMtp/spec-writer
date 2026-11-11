import { TestBed } from '@angular/core/testing';
import { Project } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { ProjectHeadingComponent } from './project-heading.component';

const AURORA = { id: 'a', name: 'Aurora' } as Project;

describe('ProjectHeadingComponent', () => {
  function render(label: string, headingId?: string): HTMLElement {
    const fixture = TestBed.createComponent(ProjectHeadingComponent);
    fixture.componentRef.setInput('label', label);
    if (headingId) {
      fixture.componentRef.setInput('headingId', headingId);
    }
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('h1');
  }

  it('nomme la page et le projet courant', () => {
    TestBed.inject(ProjectsStore).current.set(AURORA);
    const h1 = render('Spécifications', 'titre');
    expect(h1.textContent?.replace(/\s+/g, ' ').trim()).toBe('Spécifications · Aurora');
    expect(h1.id).toBe('titre');
  });

  it('se contente de la page tant que le projet n’est pas chargé', () => {
    const h1 = render('Spécifications');
    expect(h1.textContent?.trim()).toBe('Spécifications');
    expect(h1.hasAttribute('id')).toBe(false);
  });
});
