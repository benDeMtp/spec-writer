import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { LifecycleStore } from '../lifecycle/lifecycle.store';
import { ProjectsStore } from '../projects/projects.store';
import { AppEventsService, NOTIFICATIONS_LOCK, SourceUpdatedEvent, notificationText } from './app-events.service';

class FakeEventSource {
  static last: FakeEventSource;
  static readonly CLOSED = 2;
  readyState = 1;
  onerror: (() => void) | null = null;
  readonly listeners = new Map<string, (m: MessageEvent<string>) => void>();
  constructor(readonly url: string) {
    FakeEventSource.last = this;
  }
  addEventListener(name: string, listener: (m: MessageEvent<string>) => void): void {
    this.listeners.set(name, listener);
  }
  emit(e: SourceUpdatedEvent): void {
    this.listeners.get('source-updated')!({ data: JSON.stringify(e) } as MessageEvent<string>);
  }
}

class FakeNotification {
  static permission: NotificationPermission = 'granted';
  static shown: FakeNotification[] = [];
  onclick: (() => void) | null = null;
  closed = false;
  constructor(
    readonly title: string,
    readonly options: NotificationOptions,
  ) {
    FakeNotification.shown.push(this);
  }
  close(): void {
    this.closed = true;
  }
}

const EVENT: SourceUpdatedEvent = {
  id: 'e1',
  projectId: 'p2',
  projectName: 'Remboursements',
  sourceKey: 'd1',
  sourceKind: 'DOCUMENTATION',
  sourceLabel: 'Confluence : Notes de frais',
  added: 1,
  modified: 2,
  removed: 0,
  at: '2026-11-19T10:00:00Z',
};

describe('AppEventsService', () => {
  let ctrl: HttpTestingController;
  let service: AppEventsService;
  let locked: string[];

  beforeEach(() => {
    locked = [];
    vi.stubGlobal('EventSource', FakeEventSource);
    vi.stubGlobal('Notification', FakeNotification);
    Object.defineProperty(navigator, 'locks', {
      configurable: true,
      value: {
        request: (name: string, callback: () => Promise<void>) => {
          locked.push(name);
          return callback();
        },
      },
    });
    FakeNotification.shown = [];
    FakeNotification.permission = 'granted';
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    ctrl = TestBed.inject(HttpTestingController);
    service = TestBed.inject(AppEventsService);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    delete (navigator as unknown as Record<string, unknown>)['locks'];
  });

  function start(enabled: boolean): void {
    service.start();
    ctrl.expectOne('/api/settings/notifications').flush({ sourceUpdates: enabled });
  }

  it('une coupure du flux lance la sonde de l’arrêt (019)', () => {
    const probe = vi.spyOn(TestBed.inject(LifecycleStore), 'probe').mockResolvedValue();
    start(true);
    FakeEventSource.last.onerror!();
    expect(probe).toHaveBeenCalledTimes(1);
  });

  it('texte sans contenu : la source et ses décomptes, sans les zéros', () => {
    expect(notificationText(EVENT)).toEqual({
      title: 'Documentation « Confluence : Notes de frais » mise à jour',
      body: 'Remboursements : 1 nouveau, 2 modifiés',
    });
    expect(notificationText({ ...EVENT, sourceKind: 'JIRA', sourceLabel: 'Jira FACT', added: 0, removed: 3 }).title).toBe(
      'Jira FACT mise à jour',
    );
  });

  /** Le réglage relu avant d'afficher. */
  async function setting(enabled: boolean): Promise<void> {
    ctrl.expectOne('/api/settings/notifications').flush({ sourceUpdates: enabled });
    await new Promise((resolve) => setTimeout(resolve, 0));
  }

  it('l’onglet qui tient le verrou affiche une notification marquée de l’identifiant', async () => {
    start(true);
    await Promise.resolve();
    expect(locked).toEqual([NOTIFICATIONS_LOCK]);
    expect(FakeEventSource.last.url).toBe('/api/events');
    FakeEventSource.last.emit(EVENT);
    await setting(true);
    expect(FakeNotification.shown).toHaveLength(1);
    expect(FakeNotification.shown[0].options.tag).toBe('e1');
    expect(FakeNotification.shown[0].title).toContain('Notes de frais');
  });

  it('rien si le réglage est désactivé, même changé par un autre onglet, ou la permission refusée', async () => {
    start(true);
    await Promise.resolve();
    FakeEventSource.last.emit(EVENT);
    await setting(false);
    expect(FakeNotification.shown).toHaveLength(0);
    expect(service.enabled()).toBe(false);
    FakeNotification.permission = 'denied';
    FakeEventSource.last.emit(EVENT);
    expect(FakeNotification.shown).toHaveLength(0);
  });

  it('un onglet sans le verrou n’affiche rien', async () => {
    Object.defineProperty(navigator, 'locks', {
      configurable: true,
      value: { request: () => new Promise(() => undefined) },
    });
    start(true);
    await Promise.resolve();
    FakeEventSource.last.emit(EVENT);
    expect(FakeNotification.shown).toHaveLength(0);
  });

  it('le clic mène à la connaissance du projet de la source', async () => {
    const select = vi.spyOn(TestBed.inject(ProjectsStore), 'select').mockResolvedValue();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const focus = vi.spyOn(window, 'focus').mockImplementation(() => undefined);
    start(true);
    await Promise.resolve();
    FakeEventSource.last.emit(EVENT);
    await setting(true);
    FakeNotification.shown[0].onclick!();
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(focus).toHaveBeenCalled();
    expect(FakeNotification.shown[0].closed).toBe(true);
    expect(select).toHaveBeenCalledWith('p2');
    expect(navigate).toHaveBeenCalledWith('/projet/connaissance');
  });
});
