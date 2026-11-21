import { TestBed } from '@angular/core/testing';
import { LifecycleStore } from '../../core/lifecycle/lifecycle.store';
import { AppStoppedComponent } from './app-stopped.component';

describe('AppStoppedComponent', () => {
  it('titre focalisé (lu par le lecteur d’écran), consigne, fin de texte', () => {
    TestBed.inject(LifecycleStore).relaunch.set('Pour le relancer, cliquez sur son icône dans le Dock.');
    const fixture = TestBed.createComponent(AppStoppedComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const h1 = el.querySelector('h1')!;
    expect(h1.textContent).toBe('spec-writer est arrêté');
    expect(document.activeElement).toBe(h1);
    expect(el.textContent).toContain('Dock');
    expect(el.textContent?.trim().endsWith('Vous pouvez fermer cet onglet.')).toBe(true);
    fixture.nativeElement.remove();
  });
});
