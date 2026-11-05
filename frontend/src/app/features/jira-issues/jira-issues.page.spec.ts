import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { IssuePage } from '../../core/jira/jira.model';
import { Project } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { JiraIssuesPage } from './jira-issues.page';

function issues(startAt: number, count: number, total: number): IssuePage {
  return {
    startAt,
    total,
    issues: Array.from({ length: count }, (_, i) => ({
      key: `FACT-${total - startAt - i}`,
      summary: `Ticket ${total - startAt - i}`,
      updated: '2026-10-30T10:00:00Z',
      linkedSessionId: startAt === 0 && i === 0 ? 'session-1' : null,
    })),
  };
}

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

describe('JiraIssuesPage', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<JiraIssuesPage>;
  let el: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.inject(ProjectsStore).current.set({ id: 'p1', effectiveJiraProjectKey: 'FACT' } as Project);
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(JiraIssuesPage);
    el = fixture.nativeElement;
  });

  function button(text: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes(text))!;
  }

  function expectPage(startAt: number) {
    const req = ctrl.expectOne((r) => r.url === '/api/jira/projects/FACT/issues');
    expect(req.request.params.get('startAt')).toBe(String(startAt));
    expect(req.request.headers.has('X-Sw-Silent')).toBe(true);
    return req;
  }

  it('pages de 25 tickets, position, session liée ou import', async () => {
    fixture.detectChanges();
    expectPage(0).flush(issues(0, 25, 120));
    await settle(fixture);
    expect(el.querySelectorAll('tbody tr')).toHaveLength(25);
    expect(el.querySelector('sw-pager .position')?.textContent?.trim()).toBe('1 à 25 sur 120');
    const first = el.querySelector('tbody tr')!;
    expect(first.querySelector('a')?.getAttribute('href')).toBe('/jira/FACT-120');
    expect(first.textContent).toContain('Ouvrir la session liée');
    expect(el.querySelectorAll('tbody tr')[1].textContent).toContain('Voir et importer');
    expect(button('précédente').disabled).toBe(true);

    button('suivante').click();
    expectPage(25).flush(issues(25, 25, 120));
    await settle(fixture);
    expect(el.querySelector('sw-pager .position')?.textContent?.trim()).toBe('26 à 50 sur 120');
    button('précédente').click();
    expectPage(0).flush(issues(0, 25, 120));
    await settle(fixture);
    expect(el.querySelector('sw-pager .position')?.textContent?.trim()).toBe('1 à 25 sur 120');
  });

  it('Jira injoignable : la page affichée reste en place avec un message, puis Réessayer', async () => {
    fixture.detectChanges();
    expectPage(0).flush(issues(0, 25, 120));
    await settle(fixture);
    button('suivante').click();
    expectPage(25).flush(
      { code: 'JIRA_UNREACHABLE', userMessage: 'Le serveur Jira ne répond pas.', action: 'RETRY' },
      { status: 503, statusText: 'KO' },
    );
    await settle(fixture);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Le serveur Jira ne répond pas. La page affichée est conservée.');
    expect(el.querySelector('sw-pager .position')?.textContent?.trim()).toBe('1 à 25 sur 120');
    button('Réessayer').click();
    expectPage(25).flush(issues(25, 25, 120));
    await settle(fixture);
    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(el.querySelector('sw-pager .position')?.textContent?.trim()).toBe('26 à 50 sur 120');
  });

  it('sans projet Jira : renvoi vers la configuration, sans appel', () => {
    TestBed.inject(ProjectsStore).current.set({ id: 'p1', effectiveJiraProjectKey: null } as Project);
    fixture.detectChanges();
    expect(el.textContent).toContain("Aucun projet Jira n'est associé");
    ctrl.expectNone((r) => r.url.startsWith('/api/jira/projects'));
  });
});
