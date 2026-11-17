import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ReliabilityComponent } from './reliability.component';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

const LEVELS = {
  levels: { DOCUMENTS: 'REFERENCE', CONFLUENCE: 'INTERPRETATION', JIRA: 'INTERPRETATION' },
  defaults: { DOCUMENTS: 'REFERENCE', CONFLUENCE: 'INTERPRETATION', JIRA: 'INTERPRETATION' },
};

describe('ReliabilityComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<ReliabilityComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReliabilityComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.componentRef.setInput('origins', ['DOCUMENTS', 'CONFLUENCE', 'JIRA']);
    el = fixture.nativeElement;
    fixture.detectChanges();
    await settle(fixture);
    ctrl.expectOne('/api/projects/p1/reliability').flush(LEVELS);
    await settle(fixture);
  });

  afterEach(() => ctrl.verify());

  it('un sélecteur par sorte de source déclarée, avec ce que veut dire le niveau', () => {
    const labels = Array.from(el.querySelectorAll('mat-label')).map((l) => l.textContent?.trim());
    expect(labels).toEqual(['SharePoint', 'Confluence', 'Jira']);
    expect(el.querySelector('[data-testid="reliability-DOCUMENTS"]')?.textContent).toContain('Référence');
    expect(el.querySelector('[data-testid="reliability-CONFLUENCE"]')?.textContent).toContain('Interprétation');
    expect(el.textContent).toContain('porte la demande d’origine');
    expect(el.textContent).toContain('les documents SharePoint sont la référence');
  });

  it('indique le défaut dans les choix et enregistre un changement', async () => {
    (el.querySelector('[data-testid="reliability-CONFLUENCE"] .mat-mdc-select-trigger') as HTMLElement).click();
    await settle(fixture);
    const options = Array.from(document.querySelectorAll<HTMLElement>('mat-option'));
    expect(options.map((o) => o.textContent?.trim())).toEqual(['Référence', 'Interprétation (par défaut)', 'À confirmer']);
    options[2].click();
    await settle(fixture);
    const put = ctrl.expectOne('/api/projects/p1/reliability');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ origin: 'CONFLUENCE', level: 'TO_CONFIRM' });
    put.flush({ ...LEVELS, levels: { ...LEVELS.levels, CONFLUENCE: 'TO_CONFIRM' } });
    await settle(fixture);
    expect(el.querySelector('[role="status"]')?.textContent).toContain('Confluence : À confirmer');
    expect(el.textContent).toContain('pas encore validée');
  });
});
