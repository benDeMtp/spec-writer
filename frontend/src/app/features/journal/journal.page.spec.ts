import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { JournalEntry } from '../../core/journal/journal.api';
import { JournalPage } from './journal.page';

const ENTRIES: JournalEntry[] = [
  {
    at: '2026-11-03T10:02:00Z',
    level: 'ERROR',
    logger: 'dev.specwriter.application.context.ContextGenerations',
    message: 'Génération du contexte en échec : erreur inattendue',
    cause: 'java.lang.IllegalStateException: [MASQUÉ]',
    stack: ['at a.b.C.d(C.java:1)'],
  },
  {
    at: '2026-11-03T10:01:00Z',
    level: 'WARN',
    logger: 'dev.specwriter.infrastructure.docs.AntoraReader',
    message: 'Documentation : https://docs.example.com/tarifs.html non lue (HTTP_ERROR, HTTP 500)',
    cause: null,
    stack: [],
  },
];

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

describe('JournalPage', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<JournalPage>;
  let el: HTMLElement;
  let copied: string[];

  beforeEach(async () => {
    copied = [];
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: async (t: string) => void copied.push(t) },
      configurable: true,
    });
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(JournalPage);
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/journal').flush(ENTRIES);
    await settle(fixture);
  });

  afterEach(() => ctrl.verify());

  it('affiche les entrées, la cause dépliable, et copie une entrée en texte brut', async () => {
    const items = el.querySelectorAll('ol.entries > li');
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('Erreur');
    expect(items[0].textContent).toContain('ContextGenerations');
    expect(items[0].querySelector('details')?.textContent).toContain('java.lang.IllegalStateException: [MASQUÉ]');
    expect(items[1].textContent).toContain('Avertissement');
    expect(items[1].querySelector('details')).toBeNull();
    expect(el.textContent).toContain('2 entrées.');

    items[0].querySelector<HTMLButtonElement>('button')!.click();
    await settle(fixture);
    expect(copied[0]).toContain('ERROR dev.specwriter.application.context.ContextGenerations');
    expect(copied[0]).toContain('at a.b.C.d(C.java:1)');
    expect(el.textContent).toContain('Entrée copiée.');
  });

  it('actualise la liste', async () => {
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === 'Actualiser')!.click();
    ctrl.expectOne('/api/journal').flush([]);
    await settle(fixture);
    expect(el.textContent).toContain('Aucune entrée.');
  });
});
