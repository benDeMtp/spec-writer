import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserSettings } from '../../core/settings/browser.api';
import { BrowserCardComponent } from './browser-card.component';

const INSTALLED: BrowserSettings = {
  selected: null,
  missing: false,
  installed: [
    { id: 'chrome', name: 'Google Chrome' },
    { id: 'firefox', name: 'Firefox' },
  ],
};

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

describe('BrowserCardComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<BrowserCardComponent>;
  let el: HTMLElement;

  async function start(settings: BrowserSettings): Promise<void> {
    fixture = TestBed.createComponent(BrowserCardComponent);
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/settings/browser').flush(settings);
    await settle(fixture);
  }

  function button(text: string): HTMLButtonElement {
    return [...el.querySelectorAll('button')].find((b) => b.textContent?.includes(text))!;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    ctrl = TestBed.inject(HttpTestingController);
  });

  afterEach(() => ctrl.verify());

  it('propose le navigateur du système, coché par défaut, puis les navigateurs installés', async () => {
    await start(INSTALLED);
    const radios = [...el.querySelectorAll<HTMLInputElement>('input[type="radio"]')];
    expect(radios.map((r) => r.parentElement!.textContent!.trim())).toEqual([
      'Navigateur par défaut du système',
      'Google Chrome',
      'Firefox',
    ]);
    expect(radios[0].checked).toBe(true);
    expect(button('Utiliser ce navigateur').disabled).toBe(true);
  });

  it('enregistre le choix, puis ouvre spec-writer dans ce navigateur', async () => {
    await start(INSTALLED);
    const chrome = el.querySelectorAll<HTMLInputElement>('input[type="radio"]')[1];
    chrome.click();
    await settle(fixture);
    // un choix non enregistré ne s'ouvre pas encore
    expect(button('Ouvrir spec-writer').disabled).toBe(true);
    button('Utiliser ce navigateur').click();
    const put = ctrl.expectOne({ method: 'PUT', url: '/api/settings/browser' });
    expect(put.request.body).toEqual({ browserId: 'chrome' });
    put.flush({ ...INSTALLED, selected: 'chrome' });
    await settle(fixture);
    expect(el.textContent).toContain("spec-writer s'ouvrira dans Google Chrome au prochain lancement.");

    button('Ouvrir spec-writer').click();
    ctrl.expectOne({ method: 'POST', url: '/api/settings/browser/open' }).flush({
      opened: true,
      browser: { id: 'chrome', name: 'Google Chrome' },
    });
    await settle(fixture);
    expect(el.textContent).toContain("spec-writer s'ouvre dans Google Chrome.");
  });

  it('signale un navigateur choisi puis désinstallé', async () => {
    await start({ selected: 'arc', missing: true, installed: [] });
    expect(el.querySelector('[role="status"]')!.textContent).toContain("n'est plus installé");
    expect(el.textContent).toContain("Aucun autre navigateur n'a été détecté");
  });
});
