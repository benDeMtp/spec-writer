import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Router, provideRouter } from '@angular/router';
import { provideMarkdown } from 'ngx-markdown';
import { Project } from '../../core/projects/projects.model';
import { LeaveGuard } from '../../core/navigation/leave-guard';
import { ProjectSettingsPage } from './project-settings.page';

const FACT: Project = {
  id: 'p1',
  name: 'Facturation',
  description: null,
  jiraProjectKey: 'FACT',
  effectiveJiraProjectKey: 'FACT',
  jiraKeyWarning: null,
  sessionCount: 0,
  context: '## Acteurs\n- Le comptable',
  contextUpdatedAt: '2026-11-02T10:00:00Z',
  docSources: [
    { id: 's1', kind: 'URL', location: 'https://docs.example.com/billing/', lastReadAt: '2026-11-02T10:00:00Z', pagesRead: 30, pagesInError: 1, exclusions: [] },
  ],
};

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

function button(root: Element | Document, label: string): HTMLButtonElement | undefined {
  return Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent?.trim() === label);
}

function type(field: HTMLTextAreaElement | HTMLInputElement, value: string): void {
  field.value = value;
  field.dispatchEvent(new Event('input'));
}

describe('ProjectSettingsPage', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<ProjectSettingsPage>;
  let el: HTMLElement;

  async function open(project: Project): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideMarkdown(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProjectSettingsPage);
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/settings').flush({ jira: { defaultProjectKey: 'PROJ' } });
    reload(project);
    await settle(fixture);
  }

  /** Le projet courant est relu après chaque modification : la page et le menu suivent. */
  function reload(project: Project): void {
    ctrl.expectOne('/api/projects').flush([project]);
    ctrl.expectOne('/api/projects/current').flush(project);
  }

  afterEach(() => ctrl.verify());

  it('présente une seule fiche générale et les sources, sans proposition de l’IA', async () => {
    await open(FACT);
    expect(el.querySelector('h1')?.textContent).toBe('Réglages du projet · Facturation');
    const sections = Array.from(el.querySelectorAll('section h2')).map((h) => h.textContent?.trim());
    expect(sections).toEqual(['Identité', 'Fiche générale', 'Sources de documentation', 'Sources du code', "Modèle d'architecture"]);
    expect(el.querySelector('a[href="/projet/connaissance"]')?.textContent).toContain('Voir la connaissance du projet');
    expect(el.querySelector('[role="tab"]')).toBeNull();
    expect(el.querySelector<HTMLTextAreaElement>('textarea[name="context"]')!.value).toBe('## Acteurs\n- Le comptable');
    expect(el.textContent).toContain('Modifiée le');
    expect(el.textContent).toContain("Elle l'emporte sur la documentation");
    expect(el.textContent).toContain('30 pages lues, 1 en erreur');
    expect(button(el, 'Proposer une fiche')).toBeUndefined();
  });

  it('enregistre la fiche générale rédigée à la main', async () => {
    await open({ ...FACT, context: '', contextUpdatedAt: null });
    expect(el.textContent).toContain('Aucune fiche générale pour ce projet.');
    type(el.querySelector<HTMLTextAreaElement>('textarea[name="context"]')!, '## Architecture\n- Quarkus');
    await settle(fixture);
    button(el, 'Enregistrer la fiche')!.click();
    const req = ctrl.expectOne('/api/projects/p1/context');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ markdown: '## Architecture\n- Quarkus' });
    req.flush({ ...FACT, context: '## Architecture\n- Quarkus', contextUpdatedAt: '2026-11-04T08:00:00Z' });
    await settle(fixture);
    reload({ ...FACT, context: '## Architecture\n- Quarkus', contextUpdatedAt: '2026-11-04T08:00:00Z' });
    await settle(fixture);
    expect(el.textContent).toContain('Fiche générale enregistrée.');
    expect(button(el, 'Enregistrer la fiche')!.disabled).toBe(true);
  });

  it("déclare un dossier de documents : aide, motifs et aperçu (006)", async () => {
    await open(FACT);
    (el.querySelector('mat-select .mat-mdc-select-trigger') as HTMLElement).click();
    await settle(fixture);
    const option = Array.from(document.querySelectorAll<HTMLElement>('mat-option')).find((o) =>
      o.textContent?.includes('Dossier de documents'),
    )!;
    option.click();
    await settle(fixture);
    expect(el.textContent).toContain('Chemin du dossier synchronisé');
    const help = el.querySelector('details.sync-help')!;
    expect(help.querySelector('summary')?.textContent).toContain('Synchroniser un dossier SharePoint');
    expect(help.textContent).toContain('Ajouter un raccourci à Mes fichiers');
    expect(help.textContent).toContain('Toujours conserver sur cet appareil');

    type(el.querySelector<HTMLInputElement>('input[name="location"]')!, '/Users/moi/OneDrive/Aurora');
    type(el.querySelector<HTMLTextAreaElement>('textarea[name="exclusions"]')!, 'Archives/**\n\n*.xlsx ');
    await settle(fixture);
    button(el, 'Aperçu du dossier')!.click();
    const preview = ctrl.expectOne('/api/projects/p1/doc-sources/preview');
    expect(preview.request.body).toEqual({ location: '/Users/moi/OneDrive/Aurora', exclusions: ['Archives/**', '*.xlsx'] });
    preview.flush({
      retained: 6,
      excluded: 2,
      ignored: 1,
      retainedExamples: ['Specs/Aurora.docx'],
      excludedExamples: ['Archives/2023.pdf', 'Suivi.xlsx'],
      ignoredExamples: ['vieux.doc'],
      limitReached: false,
    });
    await settle(fixture);
    const result = el.querySelector('.preview[role="status"]')!;
    expect(result.textContent).toContain('6 documents retenus, 2 exclus, 1 ignoré');
    expect(result.textContent).toContain('Archives/2023.pdf');
    expect(result.textContent).toContain('vieux.doc');

    button(el, 'Ajouter')!.click();
    const add = ctrl.expectOne('/api/projects/p1/doc-sources');
    expect(add.request.body).toEqual({ kind: 'DOCUMENTS', location: '/Users/moi/OneDrive/Aurora', exclusions: ['Archives/**', '*.xlsx'] });
    add.flush({ id: 's2', kind: 'DOCUMENTS', location: '/Users/moi/OneDrive/Aurora', lastReadAt: null, pagesRead: null, pagesInError: null, exclusions: ['Archives/**', '*.xlsx'] });
    await settle(fixture);
    reload(FACT);
    await settle(fixture);
  });

  it('modifie les motifs d\'un dossier de documents déclaré (006)', async () => {
    const docs = { id: 's9', kind: 'DOCUMENTS' as const, location: '/Users/moi/OneDrive/Aurora', lastReadAt: null, pagesRead: null, pagesInError: null, exclusions: ['*.pdf'] };
    await open({ ...FACT, docSources: [docs] });
    expect(el.querySelector('.sources')?.textContent).toContain('Documents');
    expect(el.querySelector('.sources')?.textContent).toContain('Exclus : *.pdf');
    el.querySelector<HTMLButtonElement>('[aria-label="Modifier les motifs de /Users/moi/OneDrive/Aurora"]')!.click();
    await settle(fixture);
    const field = el.querySelector<HTMLTextAreaElement>('textarea[name="exclusions-s9"]')!;
    expect(field.value).toBe('*.pdf');
    type(field, '*.pdf\nArchives/**');
    await settle(fixture);
    button(el, 'Enregistrer les motifs')!.click();
    const put = ctrl.expectOne('/api/projects/p1/doc-sources/s9/exclusions');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ exclusions: ['*.pdf', 'Archives/**'] });
    put.flush({ ...docs, exclusions: ['*.pdf', 'Archives/**'] });
    await settle(fixture);
    reload({ ...FACT, docSources: [{ ...docs, exclusions: ['*.pdf', 'Archives/**'] }] });
    await settle(fixture);
    expect(el.textContent).toContain('Motifs enregistrés');
    expect(el.querySelector('textarea[name="exclusions-s9"]')).toBeNull();
  });

  it('ajoute puis retire une source', async () => {
    await open(FACT);
    type(el.querySelector<HTMLInputElement>('input[name="location"]')!, 'https://docs.example.com/rh/');
    await settle(fixture);
    button(el, 'Ajouter')!.click();
    const add = ctrl.expectOne('/api/projects/p1/doc-sources');
    expect(add.request.body).toEqual({ kind: 'URL', location: 'https://docs.example.com/rh/' });
    add.flush({ id: 's2', kind: 'URL', location: 'https://docs.example.com/rh/', lastReadAt: null, pagesRead: null, pagesInError: null });
    await settle(fixture);
    reload(FACT);
    await settle(fixture);
    el.querySelector<HTMLButtonElement>('[aria-label="Retirer la source https://docs.example.com/billing/"]')!.click();
    ctrl.expectOne((r) => r.method === 'DELETE' && r.url === '/api/projects/p1/doc-sources/s1').flush(null);
    await settle(fixture);
    reload({ ...FACT, docSources: [] });
    await settle(fixture);
    expect(el.textContent).toContain('Aucune source.');
  });

  it('enregistre puis retire le workspace Structurizr', async () => {
    await open(FACT);
    expect(el.textContent).toContain('Ownership du projet (facultatif)');
    const ownership = el.querySelector<HTMLInputElement>('input[name="ownership"]')!;
    expect(el.querySelector(`#${ownership.getAttribute('aria-describedby')}`)?.textContent).toContain(
      'Sans valeur, les éléments du modèle étendu sont communs et les autres sont propres au projet.',
    );
    type(el.querySelector<HTMLInputElement>('input[name="workspace"]')!, '/Users/moi/C4/facturation/workspace.dsl');
    type(el.querySelector<HTMLInputElement>('input[name="ownership"]')!, 'facturation');
    await settle(fixture);
    Array.from(el.querySelectorAll<HTMLButtonElement>('form[aria-label="Modèle d\'architecture"] button'))
      .find((b) => b.textContent?.trim() === 'Enregistrer')!
      .click();
    const put = ctrl.expectOne('/api/projects/p1/architecture-source');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ workspace: '/Users/moi/C4/facturation/workspace.dsl', ownership: 'facturation' });
    put.flush({ ...FACT, architectureWorkspace: '/Users/moi/C4/facturation/workspace.dsl', architectureOwnership: 'facturation' });
    await settle(fixture);
    reload({ ...FACT, architectureWorkspace: '/Users/moi/C4/facturation/workspace.dsl', architectureOwnership: 'facturation' });
    await settle(fixture);
    expect(el.textContent).toContain("Modèle d'architecture enregistré.");
    el.querySelector<HTMLButtonElement>('[aria-label="Retirer le modèle d\'architecture"]')!.click();
    ctrl.expectOne((r) => r.method === 'DELETE' && r.url === '/api/projects/p1/architecture-source').flush(null);
    await settle(fixture);
    reload(FACT);
    await settle(fixture);
    expect(el.querySelector<HTMLInputElement>('input[name="workspace"]')!.value).toBe('');
  });

  it('enregistre le nom et la clé Jira, et signale une clé inconnue', async () => {
    await open(FACT);
    const key = el.querySelector<HTMLInputElement>('input#jira')!;
    expect(key.value).toBe('FACT');
    expect(el.querySelector(`#${key.getAttribute('aria-describedby')}`)?.textContent).toContain(
      "la clé par défaut de l'application est utilisée : PROJ",
    );
    type(key, 'NOPE');
    await settle(fixture);
    Array.from(el.querySelectorAll<HTMLButtonElement>('form[aria-label="Identité du projet Facturation"] button'))
      .find((b) => b.textContent?.trim() === 'Enregistrer')!
      .click();
    const put = ctrl.expectOne((r) => r.method === 'PUT' && r.url === '/api/projects/p1');
    expect(put.request.body).toEqual({ name: 'Facturation', description: null, jiraProjectKey: 'NOPE' });
    put.flush({ ...FACT, jiraProjectKey: 'NOPE', jiraKeyWarning: 'Le projet Jira NOPE est introuvable.' });
    await settle(fixture);
    reload({ ...FACT, jiraProjectKey: 'NOPE', effectiveJiraProjectKey: 'NOPE' });
    await settle(fixture);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('NOPE est introuvable');
    expect(el.textContent).toContain('Projet « Facturation » enregistré.');
  });

  it('une saisie non enregistrée demande confirmation avant de quitter', async () => {
    await open(FACT);
    const guard = TestBed.inject(LeaveGuard);
    expect(await guard.confirm()).toBe(true);
    type(el.querySelector<HTMLInputElement>('input[name="location"]')!, 'https://docs.example.com/rh/');
    await settle(fixture);
    const leaving = guard.confirm();
    await settle(fixture);
    const dialog = document.querySelector('mat-dialog-container')!;
    expect(dialog.textContent).toContain('Quitter sans enregistrer ?');
    button(dialog, 'Annuler')!.click();
    expect(await leaving).toBe(false);
  });

  it('prévient le navigateur au rechargement si une saisie n’est pas enregistrée', async () => {
    await open(FACT);
    const clean = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(clean);
    expect(clean.defaultPrevented).toBe(false);
    type(el.querySelector<HTMLInputElement>('input[name="workspace"]')!, '/Users/moi/autre/workspace.dsl');
    await settle(fixture);
    const dirty = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(dirty);
    expect(dirty.defaultPrevented).toBe(true);
  });

  it('#jira donne le focus au champ de la clé Jira', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'projet/reglages', component: ProjectSettingsPage }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideMarkdown(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    const { RouterTestingHarness } = await import('@angular/router/testing');
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl('/projet/reglages#jira');
    await new Promise((resolve) => setTimeout(resolve, 0));
    ctrl.expectOne('/api/settings').flush({ jira: null });
    reload(FACT);
    await navigation;
    await new Promise((resolve) => setTimeout(resolve, 10));
    harness.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 10));
    expect(document.activeElement?.id).toBe('jira');
    expect(TestBed.inject(Router).url).toBe('/projet/reglages#jira');
    // le projet relu (nouveau rendu) ne déplace pas le focus vers une autre ancre (013)
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(document.activeElement?.id).toBe('jira');
  });

  it('#sources-code donne le focus au titre « Sources du code » (013)', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'projet/reglages', component: ProjectSettingsPage }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideMarkdown(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    const { RouterTestingHarness } = await import('@angular/router/testing');
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl('/projet/reglages#sources-code');
    await new Promise((resolve) => setTimeout(resolve, 0));
    ctrl.expectOne('/api/settings').flush({ jira: null });
    reload({ ...FACT, sourceRoots: [{ id: 'r1', location: '/src/rv', label: 'rv', exclusions: [], createdAt: '2026-11-11T09:00:00Z', state: 'OK' }] });
    await navigation;
    await new Promise((resolve) => setTimeout(resolve, 10));
    harness.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 10));
    expect(document.activeElement?.id).toBe('sources-code');
    expect(document.activeElement?.textContent).toBe('Sources du code');
  });
});
