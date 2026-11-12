import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { SourceRoot } from '../../core/projects/projects.model';
import { SourceRootsComponent } from './source-roots.component';

const RV: SourceRoot = {
  id: 'r1',
  location: '/src/rv',
  label: 'rv',
  exclusions: ['legacy/**'],
  createdAt: '2026-11-11T09:00:00Z',
  state: 'OK',
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

describe('SourceRootsComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<SourceRootsComponent>;
  let el: HTMLElement;
  let changed: number;

  async function open(roots: SourceRoot[]): Promise<void> {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SourceRootsComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.componentRef.setInput('roots', roots);
    changed = 0;
    fixture.componentInstance.changed.subscribe(() => changed++);
    el = fixture.nativeElement;
    await settle(fixture);
  }

  afterEach(() => ctrl.verify());

  it('dit toujours ce qui part vers Copilot, et liste les racines avec leur état en texte', async () => {
    await open([RV, { ...RV, id: 'r2', label: 'pipeline', location: '/src/pipeline', exclusions: [], state: 'MISSING' }]);
    expect(el.textContent).toContain('spec-writer envoie à GitHub Copilot les noms des modules et de courts extraits');
    expect(el.textContent).toContain('Les fichiers sensibles (.env, clés, settings.xml…) ne sont jamais lus');
    const items = Array.from(el.querySelectorAll('.roots li')).map((li) => li.textContent?.replace(/\s+/g, ' ').trim());
    expect(items[0]).toContain('Exclus : legacy/**');
    expect(items[1]).toContain('Introuvable');
    expect(items[1]).toContain("Aucun motif d'exclusion");
  });

  it('affiche l’aperçu des modules, puis ajoute la racine', async () => {
    await open([]);
    type(el.querySelector<HTMLInputElement>('input[name="root-location"]')!, ' /src/rv ');
    type(el.querySelector<HTMLTextAreaElement>('textarea[name="root-exclusions"]')!, 'legacy/**\n\n');
    await settle(fixture);
    button(el, 'Détecter les modules')!.click();
    const preview = ctrl.expectOne('/api/projects/p1/source-roots/preview');
    expect(preview.request.body).toEqual({ location: '/src/rv', exclusions: ['legacy/**'] });
    preview.flush({
      modules: [
        { name: 'aggregates', path: '', kind: 'MAVEN' },
        { name: 'demo-front', path: 'front', kind: 'NPM' },
      ],
      moduleCount: 2,
      retainedFiles: 3210,
      excludedFiles: 120,
      sensitiveFiles: 2,
      ignoredByGit: 410,
      limitReached: false,
    });
    await settle(fixture);
    const status = el.querySelector('[role="status"]')!;
    expect(status.textContent).toContain(
      '2 modules détectés · 3210 fichiers retenus · 120 exclus par vos motifs · 410 ignorés par .gitignore · 2 fichiers sensibles écartés',
    );
    // la liste des modules reste hors de la région annoncée : le lecteur d'écran ne lit que le bilan
    expect(status.textContent).not.toContain('demo-front');
    const modules = el.querySelector('ul[aria-label="Modules détectés"]')!;
    expect(modules.textContent?.replace(/\s+/g, ' ')).toContain('demo-front front · npm');
    expect(modules.textContent).toContain('(racine) · Maven');

    button(el, 'Ajouter la racine')!.click();
    ctrl.expectOne({ method: 'POST', url: '/api/projects/p1/source-roots' }).flush(RV);
    await settle(fixture);
    expect(changed).toBe(1);
    expect(el.querySelector('[role="status"]')).toBeNull();
    expect(fixture.componentInstance.dirty()).toBe(false);
  });

  it('détecte les modules d’une racine déclarée avec ses motifs enregistrés, puis replie l’aperçu (T061)', async () => {
    await open([RV]);
    const detect = el.querySelector<HTMLButtonElement>('button[aria-label="Détecter les modules de la racine rv"]')!;
    expect(detect.disabled).toBe(false);
    expect(detect.getAttribute('aria-expanded')).toBe('false');
    detect.click();
    const preview = ctrl.expectOne('/api/projects/p1/source-roots/preview');
    expect(preview.request.body).toEqual({ location: '/src/rv', exclusions: ['legacy/**'] });
    preview.flush({
      modules: [{ name: 'aggregates', path: '', kind: 'MAVEN' }],
      moduleCount: 1,
      retainedFiles: 10,
      excludedFiles: 0,
      sensitiveFiles: 0,
      ignoredByGit: 0,
      limitReached: false,
    });
    await settle(fixture);
    expect(detect.getAttribute('aria-expanded')).toBe('true');
    const item = el.querySelector('.roots li')!;
    expect(item.querySelector('[role="status"]')?.textContent).toContain('1 module détecté');
    expect(item.querySelector('ul[aria-label="Modules détectés de la racine rv"]')?.textContent).toContain('aggregates');
    // le formulaire d'ajout reste vide : rien n'est ajouté
    expect(fixture.componentInstance.dirty()).toBe(false);

    detect.click();
    await settle(fixture);
    expect(item.querySelector('[role="status"]')).toBeNull();
    expect(detect.getAttribute('aria-expanded')).toBe('false');
  });

  it('signale un aperçu tronqué et une limite atteinte', async () => {
    await open([]);
    type(el.querySelector<HTMLInputElement>('input[name="root-location"]')!, '/src/rv');
    await settle(fixture);
    button(el, 'Détecter les modules')!.click();
    ctrl.expectOne('/api/projects/p1/source-roots/preview').flush({
      modules: [{ name: 'a', path: 'a', kind: 'MAVEN' }],
      moduleCount: 600,
      retainedFiles: 1,
      excludedFiles: 0,
      sensitiveFiles: 0,
      ignoredByGit: 0,
      limitReached: true,
    });
    await settle(fixture);
    expect(el.querySelector('[role="status"]')?.textContent).toContain('(1 premiers affichés). Limite atteinte : aperçu partiel.');
  });

  it('modifie les exclusions et supprime une racine après une confirmation nommée', async () => {
    await open([RV]);
    button(el, 'Motifs')!.click();
    await settle(fixture);
    expect(fixture.componentInstance.dirty()).toBe(true);
    type(el.querySelector<HTMLTextAreaElement>('textarea[name="root-exclusions-r1"]')!, '*.sql');
    await settle(fixture);
    button(el, 'Enregistrer les exclusions')!.click();
    const put = ctrl.expectOne('/api/projects/p1/source-roots/r1/exclusions');
    expect(put.request.body).toEqual({ exclusions: ['*.sql'] });
    put.flush({ ...RV, exclusions: ['*.sql'] });
    await settle(fixture);
    expect(changed).toBe(1);

    button(el, 'Supprimer')!.click();
    await settle(fixture);
    const dialog = document.querySelector('mat-dialog-container')!;
    expect(dialog.textContent).toContain('Supprimer la racine rv ?');
    expect(dialog.textContent).toContain('Les analyses passées restent lisibles.');
    button(dialog, 'Supprimer la racine')!.click();
    await settle(fixture);
    ctrl.expectOne({ method: 'DELETE', url: '/api/projects/p1/source-roots/r1' }).flush(null);
    await settle(fixture);
    expect(changed).toBe(2);
  });

  it('masque le formulaire à dix racines', async () => {
    await open(Array.from({ length: 10 }, (_, i) => ({ ...RV, id: `r${i}`, label: `r${i}` })));
    expect(el.querySelector('form[aria-label="Ajouter une racine de sources"]')).toBeNull();
    expect(el.textContent).toContain('Un projet a au plus 10 racines de sources.');
  });
});
