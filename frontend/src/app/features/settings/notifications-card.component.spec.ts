import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppEventsService } from '../../core/events/app-events.service';
import { DENIED_HELP, NotificationsCardComponent } from './notifications-card.component';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

describe('NotificationsCardComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<NotificationsCardComponent>;
  let el: HTMLElement;
  let permission: NotificationPermission;
  let requested: number;

  beforeEach(async () => {
    permission = 'default';
    requested = 0;
    vi.stubGlobal('Notification', {
      get permission() {
        return permission;
      },
      requestPermission: async () => {
        requested++;
        return permission === 'default' ? next : permission;
      },
    });
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(NotificationsCardComponent);
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/settings/notifications').flush({ sourceUpdates: false });
    await settle(fixture);
  });

  let next: NotificationPermission = 'granted';

  afterEach(() => {
    vi.unstubAllGlobals();
    ctrl.verify();
  });

  function box(): HTMLInputElement {
    return el.querySelector('input[type="checkbox"]')!;
  }

  it('désactivées par défaut', () => {
    expect(box().checked).toBe(false);
    expect(el.textContent).toContain('Prévenir quand une source est mise à jour');
    expect(el.textContent).toContain('jamais leur contenu');
  });

  it('l’activation demande la permission puis enregistre', async () => {
    next = 'granted';
    box().click();
    await settle(fixture);
    expect(requested).toBe(1);
    const put = ctrl.expectOne('/api/settings/notifications');
    expect(put.request.body).toEqual({ sourceUpdates: true });
    put.flush({ sourceUpdates: true });
    await settle(fixture);
    expect(box().checked).toBe(true);
    expect(TestBed.inject(AppEventsService).enabled()).toBe(true);
    expect(el.querySelector('[role="status"]')?.textContent).toContain('Notifications activées.');

    box().click();
    await settle(fixture);
    ctrl.expectOne('/api/settings/notifications').flush({ sourceUpdates: false });
    await settle(fixture);
    expect(box().checked).toBe(false);
  });

  it('permission refusée : rien n’est enregistré, la façon de l’accorder est expliquée', async () => {
    next = 'denied';
    box().click();
    await settle(fixture);
    expect(box().checked).toBe(false);
    expect(el.querySelector('[role="status"]')?.textContent).toContain(DENIED_HELP);
  });

  it('permission déjà refusée : l’explication sans nouvelle demande', async () => {
    permission = 'denied';
    box().click();
    await settle(fixture);
    expect(requested).toBe(0);
    expect(el.querySelector('[role="status"]')?.textContent).toContain(DENIED_HELP);
  });
});
