import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { SourceSyncState } from '../../core/projects/projects.model';
import { SourceSyncComponent, changeLabel } from './source-sync.component';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

const STATES: SourceSyncState[] = [
  {
    sourceKey: 'JIRA',
    kind: 'JIRA',
    label: 'Jira FACT',
    interval: 'H1',
    last: { at: '2026-11-19T09:00:00Z', outcome: 'CHANGED', change: { added: 2, modified: 1, removed: 0 }, errorCode: null },
    lastSuccessAt: '2026-11-19T09:00:00Z',
    nextRunAt: '2026-11-19T10:00:00Z',
  },
  {
    sourceKey: 'd1',
    kind: 'DOCUMENTATION',
    label: '/Users/moi/SharePoint/Demandes',
    interval: 'OFF',
    last: { at: '2026-11-19T08:00:00Z', outcome: 'ERROR', change: { added: 0, modified: 0, removed: 0 }, errorCode: 'UNREADABLE' },
    lastSuccessAt: '2026-11-18T08:00:00Z',
    nextRunAt: null,
  },
  {
    sourceKey: 'ARCHITECTURE',
    kind: 'ARCHITECTURE',
    label: 'workspace.dsl',
    interval: 'OFF',
    last: null,
    lastSuccessAt: null,
    nextRunAt: null,
  },
];

describe('SourceSyncComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<SourceSyncComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SourceSyncComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    el = fixture.nativeElement;
    fixture.detectChanges();
    await settle(fixture);
    ctrl.expectOne('/api/projects/p1/source-sync').flush(STATES);
    await settle(fixture);
  });

  afterEach(() => ctrl.verify());

  it('bilans lisibles', () => {
    expect(changeLabel({ added: 1, modified: 0, removed: 3 })).toBe('1 nouveau, 3 retirés');
    expect(changeLabel({ added: 0, modified: 0, removed: 0 })).toBe('aucun changement');
  });

  it('une ligne par source : délai, dernière synchronisation, bilan ou échec, prochaine échéance', () => {
    const jira = el.querySelector('[data-testid="source-sync-JIRA"]')!;
    expect(jira.textContent).toContain('Jira');
    expect(jira.textContent).toContain('Toutes les heures');
    expect(jira.textContent).toContain('Dernière synchronisation');
    expect(jira.textContent).toContain('2 nouveaux, 1 modifié');
    expect(jira.textContent).toContain('Prochaine synchronisation');
    const doc = el.querySelector('[data-testid="source-sync-d1"]')!;
    expect(doc.textContent).toContain('Désactivée');
    expect(doc.textContent).toContain('Échec');
    expect(doc.textContent).toContain('dernière réussite');
    expect(doc.textContent).not.toContain('Prochaine synchronisation');
    expect(el.querySelector('[data-testid="source-sync-ARCHITECTURE"]')?.textContent).toContain('Jamais synchronisée');
  });

  it('change le délai, annonce le changement', async () => {
    (el.querySelector('[data-testid="source-sync-d1"] .mat-mdc-select-trigger') as HTMLElement).click();
    await settle(fixture);
    const options = Array.from(document.querySelectorAll<HTMLElement>('mat-option'));
    expect(options.map((o) => o.textContent?.trim())).toEqual([
      'Désactivée',
      'Toutes les 15 minutes',
      'Toutes les 30 minutes',
      'Toutes les heures',
      'Toutes les 4 heures',
      'Une fois par jour',
    ]);
    options[1].click();
    await settle(fixture);
    const put = ctrl.expectOne('/api/projects/p1/source-sync/d1');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ intervalMinutes: 15 });
    put.flush({ ...STATES[1], interval: 'M15', nextRunAt: '2026-11-19T08:15:00Z' });
    await settle(fixture);
    expect(el.querySelector('[role="status"]')?.textContent).toContain(
      'Synchronisation automatique de /Users/moi/SharePoint/Demandes : toutes les 15 minutes.',
    );
    expect(el.querySelector('[data-testid="source-sync-d1"]')?.textContent).toContain('Prochaine synchronisation');
  });
});
