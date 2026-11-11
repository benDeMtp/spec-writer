import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ProblemStore } from '../../core/http/problem.store';
import { ProblemBannerComponent } from './problem-banner.component';

describe('ProblemBannerComponent', () => {
  function render(action: 'OPEN_SETTINGS' | 'OPEN_PROJECT_SETTINGS'): { el: HTMLElement; navigate: ReturnType<typeof vi.spyOn> } {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    TestBed.inject(ProblemStore).show({ code: 'X', userMessage: 'Message', action });
    const fixture = TestBed.createComponent(ProblemBannerComponent);
    fixture.detectChanges();
    return { el: fixture.nativeElement, navigate };
  }

  it('la clé Jira manquante mène aux réglages du projet, sur le champ de la clé', () => {
    const { el, navigate } = render('OPEN_PROJECT_SETTINGS');
    const action = el.querySelector<HTMLButtonElement>('button')!;
    expect(action.textContent?.trim()).toBe('Ouvrir les réglages du projet');
    action.click();
    expect(navigate).toHaveBeenCalledWith('/projet/reglages#jira');
  });

  it('le serveur ou le jeton mènent à la configuration', () => {
    const { el, navigate } = render('OPEN_SETTINGS');
    const action = el.querySelector<HTMLButtonElement>('button')!;
    expect(action.textContent?.trim()).toBe('Ouvrir la configuration');
    action.click();
    expect(navigate).toHaveBeenCalledWith('/configuration');
  });
});
