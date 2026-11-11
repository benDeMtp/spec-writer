import { LiveAnnouncer } from '@angular/cdk/a11y';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ProjectFollower } from '../../core/projects/project-follower';
import { ProjectNoticeComponent } from './project-notice.component';

@Component({ template: '' })
class Blank {}

describe('ProjectNoticeComponent', () => {
  it('annonce la bascule, puis l’efface à la navigation suivante', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', component: Blank }])] });
    const follower = TestBed.inject(ProjectFollower);
    const fixture = TestBed.createComponent(ProjectNoticeComponent);
    fixture.detectChanges();
    const announce = vi.spyOn(TestBed.inject(LiveAnnouncer), 'announce').mockResolvedValue();
    const shown = () => (fixture.nativeElement.querySelector('.notice') as HTMLElement | null)?.textContent ?? null;
    expect(shown()).toBeNull();

    follower.notice.set('Projet courant : Recette');
    follower['shownOn'] = '/projet/connaissance';
    fixture.detectChanges();
    expect(shown()).toBe('Projet courant : Recette');
    expect(announce).toHaveBeenCalledWith('Projet courant : Recette', 'polite');

    // la redirection d'une ancienne adresse arrive sur la page de l'annonce : elle reste
    await TestBed.inject(Router).navigateByUrl('/projet/connaissance');
    fixture.detectChanges();
    expect(shown()).toBe('Projet courant : Recette');

    await TestBed.inject(Router).navigateByUrl('/sessions');
    fixture.detectChanges();
    expect(shown()).toBeNull();
  });
});
