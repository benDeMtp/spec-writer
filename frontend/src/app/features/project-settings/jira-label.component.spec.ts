import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JiraLabelComponent } from './jira-label.component';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

const URL = '/api/projects/p1/jira-label';

describe('JiraLabelComponent (024)', () => {
  let fixture: ComponentFixture<JiraLabelComponent>;
  let ctrl: HttpTestingController;
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(JiraLabelComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => ctrl.verify());

  function field(): HTMLInputElement {
    return el.querySelector<HTMLInputElement>('input#sw-jira-label')!;
  }

  function button(text: string): HTMLButtonElement | undefined {
    return [...el.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.trim() === text);
  }

  async function type(value: string): Promise<void> {
    field().value = value;
    field().dispatchEvent(new Event('input'));
    await settle(fixture);
  }

  it('étiquette le champ, l’explique, et enregistre en l’annonçant', async () => {
    ctrl.expectOne(URL).flush({ label: null });
    await settle(fixture);
    const label = el.querySelector('label[for="sw-jira-label"]');
    expect(label?.textContent).toContain('Label des tickets de spec-writer');
    expect(field().getAttribute('aria-describedby')).toBe('sw-jira-label-hint');
    expect(el.textContent).toContain('sans espace');
    expect(button('Enregistrer le label')?.disabled).toBe(true);
    expect(button('Retirer le label')).toBeUndefined();

    await type('spec-writer');
    button('Enregistrer le label')!.click();
    const put = ctrl.expectOne(URL);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ label: 'spec-writer' });
    put.flush({ label: 'spec-writer' });
    await settle(fixture);
    expect(el.querySelector('[role=status]')?.textContent).toContain('Label spec-writer enregistré.');
    expect(button('Retirer le label')).toBeDefined();
  });

  it('annonce un refus, et prévient au changement d’un label existant', async () => {
    ctrl.expectOne(URL).flush({ label: 'spec-writer' });
    await settle(fixture);
    expect(field().value).toBe('spec-writer');
    await type('spec writer');
    expect(el.textContent).toContain('Les tickets qui portent l’ancien label ne seront plus reconnus.');
    button('Enregistrer le label')!.click();
    ctrl.expectOne(URL).flush(
      {
        code: 'VALIDATION_FAILED',
        userMessage: 'Un label Jira ne contient pas d’espace et compte au plus 255 caractères.',
      },
      { status: 422, statusText: 'Unprocessable' },
    );
    await settle(fixture);
    expect(el.querySelector('[role=alert]')?.textContent).toContain('ne contient pas d’espace');
  });

  it('retire le label', async () => {
    ctrl.expectOne(URL).flush({ label: 'spec-writer' });
    await settle(fixture);
    button('Retirer le label')!.click();
    ctrl.expectOne({ url: URL, method: 'DELETE' }).flush(null);
    await settle(fixture);
    expect(field().value).toBe('');
    expect(el.querySelector('[role=status]')?.textContent).toContain('plus rien n’est étiqueté');
  });
});
