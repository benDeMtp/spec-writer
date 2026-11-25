import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { IssuePage } from '../../core/jira/jira.model';
import { Project } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { JiraIssuesPage } from './jira-issues.page';

function issues(startAt: number, count: number, total: number, lastSync: IssuePage['lastSync'] = null): IssuePage {
  return {
    jiraProjectKey: 'FACT',
    lastSync,
    startAt,
    total,
    issues: Array.from({ length: count }, (_, i) => ({
      key: `FACT-${total - startAt - i}`,
      title: `Ticket ${total - startAt - i}`,
      status: 'En cours',
      statusCategory: 'indeterminate',
      updatedAt: '2026-10-30T10:00:00Z',
      sync: startAt === 0 && i === 0 ? ('CONFLICT' as const) : ('UNLINKED' as const),
      sessionId: startAt === 0 && i === 0 ? 'session-1' : null,
      parentKey: null,
      subtasks: [],
    })),
  };
}

/** Ticket de la vue (022) : une story, ses sous-tâches, ou une sous-tâche orpheline. */
function ticket(key: string, extra: Partial<IssuePage['issues'][number]> = {}): IssuePage['issues'][number] {
  return {
    key,
    title: `Titre ${key}`,
    status: 'À faire',
    statusCategory: 'new',
    updatedAt: '2026-11-25T09:00:00Z',
    sync: 'UNLINKED',
    sessionId: null,
    parentKey: null,
    subtasks: [],
    ...extra,
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

  function expectPage(startAt: number, sort = 'updated', dir = 'desc') {
    const req = ctrl.expectOne((r) => r.url === '/api/projects/p1/jira-issues');
    expect(req.request.params.get('startAt')).toBe(String(startAt));
    expect(req.request.params.get('sort')).toBe(sort);
    expect(req.request.params.get('dir')).toBe(dir);
    expect(req.request.params.get('size')).toBe('50');
    expect(req.request.headers.has('X-Sw-Silent')).toBe(true);
    return req;
  }

  function header(label: string): HTMLTableCellElement {
    return Array.from(el.querySelectorAll('th')).find((th) => th.textContent?.includes(label))!;
  }

  it('colonnes, pages de 50, session liée ou import, état de synchro', async () => {
    fixture.detectChanges();
    expectPage(0).flush(issues(0, 50, 120));
    await settle(fixture);
    expect(Array.from(el.querySelectorAll('thead th')).map((th) => th.textContent?.replace(/[▲▼]/g, '').trim())).toEqual([
      'Ticket',
      'Titre',
      'Modifié le',
      'Statut',
      'Synchro',
      'Spécification',
    ]);
    expect(el.querySelectorAll('tbody tr')).toHaveLength(50);
    expect(el.querySelector('sw-pager .position')?.textContent?.trim()).toBe('1 à 50 sur 120');
    const first = el.querySelector('tbody tr')!;
    expect(first.querySelector('a')?.getAttribute('href')).toBe('/jira/FACT-120');
    expect(first.textContent).toContain('Ouvrir la session liée');
    expect(first.textContent).toContain('Conflit');
    expect(first.textContent).toContain('En cours');
    expect(el.querySelectorAll('tbody tr')[1].textContent).toContain('Non liée');
    expect(el.querySelectorAll('tbody tr')[1].textContent).toContain('Voir et importer');
    expect(button('précédente').disabled).toBe(true);

    button('suivante').click();
    await settle(fixture);
    expectPage(50).flush(issues(50, 50, 120));
    await settle(fixture);
    expect(el.querySelector('sw-pager .position')?.textContent?.trim()).toBe('51 à 100 sur 120');
  });

  it('tri par en-tête : URL, retour à la page 1, aria-sort et annonce', async () => {
    fixture.detectChanges();
    expectPage(0).flush(issues(0, 50, 120));
    await settle(fixture);
    expect(header('Modifié le').getAttribute('aria-sort')).toBe('descending');
    expect(header('Statut').hasAttribute('aria-sort')).toBe(false);

    button('suivante').click();
    await settle(fixture);
    expectPage(50).flush(issues(50, 50, 120));
    await settle(fixture);

    header('Synchro').querySelector('button')!.click();
    await settle(fixture);
    expectPage(0, 'sync', 'asc').flush(issues(0, 50, 120));
    await settle(fixture);
    const router = TestBed.inject(Router);
    expect(router.url).toBe('/?tri=sync&ordre=asc');
    expect(header('Synchro').getAttribute('aria-sort')).toBe('ascending');
    expect(header('Modifié le').hasAttribute('aria-sort')).toBe(false);
    expect(el.querySelector('[aria-live="polite"]')?.textContent).toContain('Tickets triés par synchro, ordre croissant.');

    header('Synchro').querySelector('button')!.click();
    await settle(fixture);
    expectPage(0, 'sync', 'desc').flush(issues(0, 50, 120));
    await settle(fixture);
    expect(header('Synchro').getAttribute('aria-sort')).toBe('descending');
  });

  it('le tri de l’URL est repris à l’ouverture', async () => {
    await TestBed.inject(Router).navigateByUrl('/?tri=status&ordre=asc&page=2');
    fixture.detectChanges();
    expectPage(50, 'status', 'asc').flush(issues(50, 50, 120));
    await settle(fixture);
    expect(header('Statut').getAttribute('aria-sort')).toBe('ascending');
  });

  it('dernière synchronisation de Jira, ou invitation à synchroniser, puis resynchronisation', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      fixture.detectChanges();
      expectPage(0).flush(issues(0, 1, 1));
      await settle(fixture);
      expect(el.textContent).toContain("Jira n'a pas encore été synchronisé");

      button('Resynchroniser Jira').click();
      const start = ctrl.expectOne('/api/projects/p1/indexations');
      expect(start.request.body).toEqual({ mode: 'INCREMENTAL', sources: ['JIRA'] });
      start.flush({ status: 'RUNNING' });
      await vi.advanceTimersByTimeAsync(1000);
      ctrl.expectOne('/api/projects/p1/indexations/current').flush({ status: 'SUCCEEDED' });
      await vi.advanceTimersByTimeAsync(0);
      expectPage(0).flush(
        issues(0, 1, 1, { at: '2026-11-19T09:30:00Z', outcome: 'CHANGED', change: { added: 1, modified: 0, removed: 0 }, errorCode: null }),
      );
      await settle(fixture);
      expect(el.textContent).toContain('Dernière synchronisation de Jira : 19');
    } finally {
      vi.useRealTimers();
    }
  });

  it('erreur de lecture : la page affichée reste en place avec un message, puis Réessayer', async () => {
    fixture.detectChanges();
    expectPage(0).flush(issues(0, 50, 120));
    await settle(fixture);
    button('suivante').click();
    await settle(fixture);
    expectPage(50).flush({ code: 'INTERNAL_ERROR', userMessage: 'Erreur.', action: 'RETRY' }, { status: 500, statusText: 'KO' });
    await settle(fixture);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Erreur. La page affichée est conservée.');
    expect(el.querySelector('sw-pager .position')?.textContent?.trim()).toBe('1 à 50 sur 120');
    button('Réessayer').click();
    expectPage(50).flush(issues(50, 50, 120));
    await settle(fixture);
    expect(el.querySelector('[role="alert"]')).toBeNull();
  });

  function settings(jira: object | null): void {
    ctrl.expectOne('/api/settings').flush({ copilot: {}, jira });
  }

  it('clé du projet, puis clé par défaut de l’application signalée', async () => {
    TestBed.inject(ProjectsStore).current.set({ id: 'p1', name: 'Aurora', jiraProjectKey: 'FACT', effectiveJiraProjectKey: 'FACT' } as Project);
    fixture.detectChanges();
    settings({ enabled: true });
    expectPage(0).flush(issues(0, 1, 1));
    await settle(fixture);
    expect(el.querySelector('.lead')?.textContent).toContain('Projet Jira FACT, lu dans');

    // même projet Jira : la liste n'est pas relue
    TestBed.inject(ProjectsStore).current.set({ id: 'p2', name: 'Recette', jiraProjectKey: null, effectiveJiraProjectKey: 'FACT' } as Project);
    await settle(fixture);
    expect(el.querySelector('.lead')?.textContent).toContain("Projet Jira FACT (clé par défaut de l'application)");
  });

  it('sans clé, Jira configuré : renvoi vers les réglages du projet, sans appel', async () => {
    TestBed.inject(ProjectsStore).current.set({ id: 'p1', name: 'Recette', effectiveJiraProjectKey: null } as Project);
    fixture.detectChanges();
    settings({ enabled: true });
    await settle(fixture);
    expect(el.textContent).toContain("Aucun projet Jira n'est associé à Recette.");
    const link = Array.from(el.querySelectorAll('a')).find((a) => a.textContent?.includes('Renseigner la clé Jira du projet'));
    expect(link?.getAttribute('href')).toBe('/projet/reglages#jira');
    ctrl.expectNone((r) => r.url.includes('jira-issues'));
  });

  it('Jira non configuré : renvoi vers la configuration', async () => {
    TestBed.inject(ProjectsStore).current.set({ id: 'p1', name: 'Recette', effectiveJiraProjectKey: null } as Project);
    fixture.detectChanges();
    settings(null);
    await settle(fixture);
    expect(el.textContent).toContain("Jira n'est pas configuré.");
    expect(Array.from(el.querySelectorAll('a')).find((a) => a.textContent?.includes('Ouvrir la configuration'))?.getAttribute('href')).toBe(
      '/configuration',
    );
  });

  it('022 : sous-tâches repliées sous leur parent, dépliables, orpheline signalée', async () => {
    await settle(fixture);
    expectPage(0).flush({
      jiraProjectKey: 'FACT',
      lastSync: null,
      startAt: 0,
      total: 3,
      issues: [
        ticket('S-1', { subtasks: [ticket('S-2'), ticket('S-3'), ticket('S-10')] }),
        ticket('A-1'),
        ticket('O-5', { parentKey: 'P-9' }),
      ],
    });
    await settle(fixture);

    const toggle = el.querySelector<HTMLButtonElement>('button[aria-expanded]')!;
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(toggle.getAttribute('aria-label')).toBe('Afficher les 3 sous-tâches de S-1');
    expect(toggle.textContent).toContain('3');
    expect(el.querySelectorAll('button[aria-expanded]')).toHaveLength(1);
    expect(el.querySelectorAll('tr.subtask')).toHaveLength(0);
    expect(el.querySelector('a[href="/jira/S-2"]')).toBeNull();
    // replié, aucune ligne à désigner (axe aria-valid-attr-value)
    expect(toggle.hasAttribute('aria-controls')).toBe(false);

    toggle.click();
    await settle(fixture);
    const rows = Array.from(el.querySelectorAll<HTMLTableRowElement>('tr.subtask'));
    expect(rows.map((r) => r.querySelector('a')?.getAttribute('href'))).toEqual(['/jira/S-2', '/jira/S-3', '/jira/S-10']);
    expect(rows[0].textContent).toContain('Sous-tâche de S-1');
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(toggle.getAttribute('aria-label')).toBe('Masquer les 3 sous-tâches de S-1');
    expect(rows.every((r) => toggle.getAttribute('aria-controls')!.split(' ').includes(r.id))).toBe(true);

    toggle.click();
    await settle(fixture);
    expect(el.querySelectorAll('tr.subtask')).toHaveLength(0);

    const orphan = Array.from(el.querySelectorAll('tbody tr')).find((r) => r.textContent?.includes('O-5'))!;
    expect(orphan.textContent).toContain('Sous-tâche de P-9');
  });

  it('022 : changer de page replie tout', async () => {
    await settle(fixture);
    const page = (startAt: number): IssuePage => ({
      jiraProjectKey: 'FACT',
      lastSync: null,
      startAt,
      total: 60,
      issues: [ticket(`S-${startAt + 1}`, { subtasks: [ticket(`T-${startAt + 1}`)] })],
    });
    expectPage(0).flush(page(0));
    await settle(fixture);
    el.querySelector<HTMLButtonElement>('button[aria-expanded]')!.click();
    await settle(fixture);
    expect(el.querySelectorAll('tr.subtask')).toHaveLength(1);
    await TestBed.inject(Router).navigate([], { queryParams: { page: 2 } });
    await settle(fixture);
    expectPage(50).flush(page(0));
    await settle(fixture);
    expect(el.querySelectorAll('tr.subtask')).toHaveLength(0);
  });
});
