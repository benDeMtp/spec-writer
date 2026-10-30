import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LaunchTokenService } from './launch-token.service';

describe('LaunchTokenService', () => {
  let service: LaunchTokenService;
  let http: HttpTestingController;
  let replaced: string[];
  const history = { state: null, replaceState: (_s: unknown, _t: string, url: string) => replaced.push(url) } as unknown as History;

  beforeEach(() => {
    replaced = [];
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(LaunchTokenService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it("échange le jeton puis le retire de l'URL", async () => {
    const done = service.exchangeFromUrl({ href: 'http://127.0.0.1:47615/sessions?launch=abc&x=1#h' } as Location, history);
    expect(replaced).toEqual(['/sessions?x=1#h']);
    const req = http.expectOne('/api/session-auth');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ launchToken: 'abc' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    await done;
    expect(service.state()).toBe('exchanged');
  });

  it('signale un jeton refusé', async () => {
    const done = service.exchangeFromUrl({ href: 'http://127.0.0.1:1/?launch=bad' } as Location, history);
    http.expectOne('/api/session-auth').flush({ code: 'FORBIDDEN' }, { status: 403, statusText: 'Forbidden' });
    await done;
    expect(service.state()).toBe('rejected');
  });

  it('ne fait rien sans jeton', async () => {
    await service.exchangeFromUrl({ href: 'http://127.0.0.1:1/' } as Location, history);
    http.expectNone('/api/session-auth');
    expect(replaced).toEqual([]);
  });
});
