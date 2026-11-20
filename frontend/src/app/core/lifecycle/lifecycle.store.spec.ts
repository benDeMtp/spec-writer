import { TestBed } from '@angular/core/testing';
import { LifecycleStore } from './lifecycle.store';

describe('LifecycleStore', () => {
  let store: LifecycleStore;
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    store = TestBed.inject(LifecycleStore);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('consigne de relance générale par défaut', () => {
    expect(store.relaunch()).toBe('Pour le relancer, ouvrez-le depuis son raccourci.');
  });

  it('une erreur réseau fait passer à l’arrêt', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
    await store.probe();
    expect(store.state()).toBe('stopped');
  });

  it('toute réponse HTTP, même un refus, laisse l’onglet tel quel', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 403 }));
    await store.probe();
    expect(store.state()).toBe('running');
    fetchMock.mockResolvedValue(new Response('{}', { status: 200 }));
    await store.probe();
    expect(store.state()).toBe('running');
  });

  it('l’arrêt est définitif : plus aucune sonde', async () => {
    store.stopped();
    await store.probe();
    expect(fetchMock).not.toHaveBeenCalled();
    expect(store.state()).toBe('stopped');
  });

  it('deux coupures simultanées ne font qu’une sonde', async () => {
    fetchMock.mockResolvedValue(new Response('{}', { status: 200 }));
    await Promise.all([store.probe(), store.probe()]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
