import { LiveAnnouncer } from '@angular/cdk/a11y';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, RouterOutlet, provideRouter } from '@angular/router';
import { TOUR_STOPS } from './tour-stops';
import { TourService } from './tour.service';

/** Écran factice : chaque route expose une cible de la visite, sauf `/sessions`, encore vide. */
@Component({ template: '<div data-tour="composer"></div><div data-tour="draft-spec"></div>' })
class Home {}
@Component({ template: '<p>Aucune spécification.</p>' })
class Empty {}
@Component({ template: '<div data-tour="jira-table"></div>' })
class Jira {}
@Component({ selector: 'sw-host', imports: [RouterOutlet], template: '<router-outlet />' })
class Host {}

async function settle(): Promise<void> {
  for (let i = 0; i < 5; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
}

function bubble(): HTMLElement | null {
  return document.querySelector('sw-tour-bubble');
}

function click(label: string): void {
  Array.from(bubble()!.querySelectorAll('button')).find((b) => b.textContent?.trim() === label)!.click();
}

describe('TourService', () => {
  let ctrl: HttpTestingController;
  let router: Router;
  let tour: TourService;
  let announced: string[];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: '', component: Home },
          { path: 'sessions', component: Empty },
          { path: 'jira', component: Jira },
          { path: 'journal', component: Empty },
          { path: '**', component: Empty },
        ]),
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    tour = TestBed.inject(TourService);
    announced = [];
    vi.spyOn(TestBed.inject(LiveAnnouncer), 'announce').mockImplementation(async (m) => {
      announced.push(String(m));
    });
    // le routeur affiche les écrans dans un hôte attaché au document, comme dans l'application
    const host = document.createElement('div');
    document.body.appendChild(host);
    const fixture = TestBed.createComponent(Host);
    host.appendChild(fixture.nativeElement);
    fixture.autoDetectChanges();
    await router.navigateByUrl('/journal');
    await settle();
  });

  afterEach(async () => {
    await tour.quit();
    ctrl.verify();
    document.querySelectorAll('.cdk-overlay-container').forEach((c) => (c.innerHTML = ''));
  });

  it('parcourt les arrêts dans l’ordre d’usage, avec la position et la cible mise en valeur', async () => {
    expect(TOUR_STOPS.map((s) => s.id)).toEqual([
      'conversation',
      'draft',
      'sessions',
      'specification',
      'discussion',
      'interview',
      'tasks',
      'jira',
      'knowledge',
      'project-settings',
      'advanced',
      'agents',
    ]);
    await tour.start();
    await settle();
    expect(router.url).toBe('/');
    expect(bubble()?.textContent).toContain('Arrêt 1 sur 12');
    expect(bubble()?.textContent).toContain('Converser avec l’agent');
    expect(document.querySelector('[data-tour="composer"]')?.classList).toContain('sw-tour-target');
    expect(announced).toContain('Arrêt 1 sur 12 : Converser avec l’agent');
    click('Suivant');
    await settle();
    expect(bubble()?.textContent).toContain('Arrêt 2 sur 12');
    expect(document.querySelector('[data-tour="composer"]')?.classList).not.toContain('sw-tour-target');
    click('Précédent');
    await settle();
    expect(bubble()?.textContent).toContain('Arrêt 1 sur 12');
  });

  it("sans spécification, les arrêts de la spécification restent sur la liste avec leur texte de remplacement", async () => {
    await tour.start();
    await settle();
    click('Suivant');
    await settle();
    click('Suivant');
    await new Promise((resolve) => setTimeout(resolve, 2100));
    await settle();
    expect(router.url).toBe('/sessions');
    expect(bubble()?.textContent).toContain('Vos spécifications apparaîtront ici');
    click('Suivant');
    await settle();
    // lecture seule de la liste des spécifications : aucune écriture pendant la visite (FR-017)
    const list = ctrl.expectOne('/api/sessions');
    expect(list.request.method).toBe('GET');
    list.flush([]);
    await new Promise((resolve) => setTimeout(resolve, 2100));
    await settle();
    expect(router.url).toBe('/sessions');
    expect(bubble()?.textContent).toContain('Une spécification s’ouvre depuis cette liste');
  }, 10_000);

  it("Quitter ramène à l'écran de départ et ferme la bulle", async () => {
    await tour.start();
    await settle();
    click('Quitter la visite');
    await settle();
    expect(router.url).toBe('/journal');
    expect(bubble()).toBeNull();
    expect(tour.current()).toBeNull();
  });

  it('Échap quitte la visite', async () => {
    await tour.start();
    await settle();
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await settle();
    expect(bubble()).toBeNull();
    expect(router.url).toBe('/journal');
  });
});
