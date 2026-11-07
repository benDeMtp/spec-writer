import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MentionSuggestion } from '../../core/projects/projects.model';
import { MENTION_DELAYS, MentionDirective, mentionAt } from './mention.directive';

@Component({
  imports: [MentionDirective, FormsModule],
  template: `<textarea aria-label="Réponse" [swMention]="projectId()" [(ngModel)]="value"></textarea>`,
})
class HostComponent {
  readonly projectId = signal<string | null>('p1');
  value = '';
}

const MATRICE: MentionSuggestion = {
  kind: 'DOCUMENT',
  name: 'Aurora_Matrice_Regles_Gestion_Activites_1',
  path: '10. MVP/Documentation DC/Aurora_Matrice_Regles_Gestion_Activites_1.xlsx',
  insert: '@Aurora_Matrice_Regles_Gestion_Activites_1',
};
const FLUX: MentionSuggestion = { kind: 'PAGE', name: 'Matrice des flux', path: 'aurora/matrice-des-flux.html', insert: '@Matrice des flux' };

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

describe('mentionAt', () => {
  it('repère la mention en cours avant le curseur', () => {
    expect(mentionAt('voir @matr', 10)).toEqual({ start: 5, query: 'matr' });
    expect(mentionAt('@Schéma dir', 11)).toEqual({ start: 0, query: 'Schéma dir' });
    expect(mentionAt('(@ADR', 5)).toEqual({ start: 1, query: 'ADR' });
  });

  it('ignore les adresses, les arobases isolés et les retours à la ligne', () => {
    expect(mentionAt('jean@laposte', 12)).toBeNull();
    expect(mentionAt('réunion @ 10 h', 14)).toBeNull();
    expect(mentionAt('fin @', 5)).toBeNull();
    expect(mentionAt('@abc\ndef', 8)).toBeNull();
    expect(mentionAt('sans mention', 12)).toBeNull();
  });
});

describe('MentionDirective', () => {
  let fixture: ComponentFixture<HostComponent>;
  let ctrl: HttpTestingController;
  let area: HTMLTextAreaElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MENTION_DELAYS, useValue: { suggest: 0, resolve: 0 } }],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    area = fixture.nativeElement.querySelector('textarea');
  });

  afterEach(() => {
    ctrl.verify();
    document.querySelectorAll('.cdk-overlay-container').forEach((c) => (c.innerHTML = ''));
  });

  async function type(value: string): Promise<void> {
    area.focus();
    area.value = value;
    area.setSelectionRange(value.length, value.length);
    area.dispatchEvent(new Event('input'));
    await settle(fixture);
  }

  function key(k: string): KeyboardEvent {
    const e = new KeyboardEvent('keydown', { key: k, cancelable: true, bubbles: true });
    area.dispatchEvent(e);
    fixture.detectChanges();
    return e;
  }

  function options(): HTMLElement[] {
    return Array.from(document.querySelectorAll<HTMLElement>('[role="option"]'));
  }

  async function suggest(query: string, items: MentionSuggestion[]): Promise<void> {
    const req = ctrl.expectOne((r) => r.url === '/api/projects/p1/mentions' && r.params.get('q') === query);
    expect(req.request.headers.get('X-Sw-Silent')).toBe('1');
    req.flush(items);
    await settle(fixture);
  }

  it('propose les documents après @ et insère le choix au clavier', async () => {
    await type('les informations sont dans @matr');
    await suggest('matr', [FLUX, MATRICE]);

    expect(options().map((o) => o.querySelector('.name')?.textContent)).toEqual(['Matrice des flux', 'Aurora_Matrice_Regles_Gestion_Activites_1']);
    expect(options()[0].textContent).toContain('Page de documentation');
    expect(area.getAttribute('aria-autocomplete')).toBe('list');
    expect(area.getAttribute('aria-controls')).toBe(document.querySelector('[role="listbox"]')?.id);
    expect(area.getAttribute('aria-activedescendant')).toBe(options()[0].id);
    expect(document.querySelector('[aria-live="polite"]')?.textContent).toContain('2 documents proposés');

    expect(key('ArrowDown').defaultPrevented).toBe(true);
    expect(area.getAttribute('aria-activedescendant')).toBe(options()[1].id);
    expect(options()[1].getAttribute('aria-selected')).toBe('true');
    expect(key('Enter').defaultPrevented).toBe(true);
    await settle(fixture);

    expect(area.value).toBe('les informations sont dans @Aurora_Matrice_Regles_Gestion_Activites_1 ');
    expect(fixture.componentInstance.value).toBe(area.value);
    expect(options()).toHaveLength(0);
    expect(area.hasAttribute('aria-activedescendant')).toBe(false);
  });

  it('Échap ferme sans modifier le texte ; un clic choisit', async () => {
    await type('voir @matr');
    await suggest('matr', [FLUX]);
    key('Escape');
    expect(options()).toHaveLength(0);
    expect(area.value).toBe('voir @matr');

    await type('voir @matr et @flu');
    await suggest('flu', [FLUX]);
    options()[0].dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
    await settle(fixture);
    expect(area.value).toBe('voir @matr et @Matrice des flux ');
  });

  it('signale l’absence de correspondance et laisse le texte libre', async () => {
    await type('@zzz');
    await suggest('zzz', []);
    expect(document.body.textContent).toContain('Aucun document ne correspond.');
    expect(key('Enter').defaultPrevented).toBe(false);
  });

  it('ne s’ouvre ni sur une adresse ni sans projet', async () => {
    await type('écrire à jean@laposte.fr');
    ctrl.expectNone('/api/projects/p1/mentions');
    fixture.componentInstance.projectId.set(null);
    fixture.detectChanges();
    await type('voir @matr');
    ctrl.expectNone((r) => r.url.includes('/mentions'));
  });
});
