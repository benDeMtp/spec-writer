import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Router, provideRouter } from '@angular/router';
import { NeedPage } from './need.page';

const NEED = 'Les employés doivent pouvoir réserver une salle de réunion pour une durée donnée.';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

describe('NeedPage', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<NeedPage>;
  let el: HTMLElement;

  function type(text: string): void {
    const textarea = el.querySelector('textarea')!;
    textarea.value = text;
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submit(): void {
    (Array.from(el.querySelectorAll('button')).find((b) => b.type === 'submit') as HTMLButtonElement).click();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(NeedPage);
    el = fixture.nativeElement;
    fixture.detectChanges();
    await settle(fixture);
  });

  it('compte les caractères et les mots, et refuse un besoin trop court', async () => {
    type('Réserver une salle');
    expect(el.textContent).toContain('18 / 10000');
    expect(el.textContent).toContain('Encore 7 mots au minimum');
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('au moins 10 mots');
    expect(el.querySelector('textarea')!.getAttribute('aria-invalid')).toBe('true');
    submit();
    await settle(fixture);
    ctrl.expectNone('/api/settings');
    ctrl.expectNone('/api/sessions');
    expect(el.querySelector('textarea')!.getAttribute('maxlength')).toBe('10000');
    expect(el.textContent).toContain('Voir un exemple de besoin bien formulé');
  });

  it("demande le consentement Copilot avant le premier envoi, puis ouvre le questionnaire", async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    type(NEED);
    submit();
    ctrl.expectOne('/api/settings').flush({ copilot: { state: 'CONNECTED', consent: false }, jira: null });
    await settle(fixture);
    const dialog = document.querySelector('sw-consent-dialog')!;
    expect(dialog.textContent).toContain('Envoyer vos besoins à GitHub Copilot ?');
    (Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Accepter') as HTMLElement).click();
    await settle(fixture);
    ctrl.expectOne({ method: 'PUT', url: '/api/consents/COPILOT' }).flush(null);
    await settle(fixture);
    ctrl.expectOne('/api/settings').flush({ copilot: { state: 'CONNECTED', consent: true }, jira: null });
    await settle(fixture);
    const post = ctrl.expectOne({ method: 'POST', url: '/api/sessions' });
    expect(post.request.body).toEqual({ need: NEED });
    post.flush({ id: 's1' });
    await settle(fixture);
    expect(navigate).toHaveBeenCalledWith(['/sessions', 's1']);
  });

  it("sans accord, rien n'est envoyé", async () => {
    type(NEED);
    submit();
    ctrl.expectOne('/api/settings').flush({ copilot: { state: 'CONNECTED', consent: false }, jira: null });
    await settle(fixture);
    const dialog = document.querySelector('sw-consent-dialog')!;
    (Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Refuser') as HTMLElement).click();
    await settle(fixture);
    ctrl.expectNone('/api/sessions');
    expect(el.textContent).toContain("Sans votre accord, le besoin n'est pas envoyé");
  });
});
