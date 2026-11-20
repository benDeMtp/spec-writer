import { LiveAnnouncer } from '@angular/cdk/a11y';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { InterviewRecapPage } from './interview-recap.page';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 4; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

const REPORT = {
  html: '<h2>Point sur « Avoirs » — 20/11/2026</h2><h3>Décisions</h3><ul><li>H-01 — confirmé</li></ul>',
  text: 'Point sur « Avoirs » — 20/11/2026\n\nDécisions\n• H-01 — confirmé\n',
};

describe('InterviewRecapPage (021, US2)', () => {
  let fixture: ComponentFixture<InterviewRecapPage>;
  let el: HTMLElement;
  let announced: string[];
  let written: ClipboardItem[][];
  let ctrl: HttpTestingController;

  const SPEC = { sessionId: 's1', name: 'Avoirs', latestVersion: 1, appliedVersion: null as number | null };

  async function open(
    clipboard: Partial<Clipboard>,
    specs: object[] = [{ ...SPEC, revisionAvailable: false, revisionUnavailableReason: null }],
  ): Promise<void> {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    ctrl = TestBed.inject(HttpTestingController);
    announced = [];
    vi.spyOn(TestBed.inject(LiveAnnouncer), 'announce').mockImplementation(async (m) => {
      announced.push(String(m));
    });
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: clipboard });
    fixture = TestBed.createComponent(InterviewRecapPage);
    fixture.componentRef.setInput('id', 'i1');
    fixture.detectChanges();
    await settle(fixture);
    ctrl.expectOne('/api/interviews/i1').flush({
      id: 'i1',
      state: 'FINISHED',
      participants: [],
      specs,
      stops: [],
      counts: { handled: 1, notes: 0, pending: 0 },
    });
    ctrl.expectOne('/api/interviews/i1/report').flush(REPORT);
    await settle(fixture);
    el = fixture.nativeElement;
  }

  afterEach(() => {
    delete (navigator as unknown as Record<string, unknown>)['clipboard'];
  });

  it('aperçu du compte rendu, copié en HTML et en texte, avec une confirmation annoncée', async () => {
    written = [];
    vi.stubGlobal(
      'ClipboardItem',
      class {
        constructor(readonly items: Record<string, Blob>) {}
      },
    );
    await open({ write: async (items: ClipboardItem[]) => void written.push(items) });
    expect(el.querySelector('.preview h2')?.textContent).toContain('Point sur « Avoirs »');
    el.querySelector<HTMLButtonElement>('.actions button')!.click();
    await settle(fixture);
    const item = written[0][0] as unknown as { items: Record<string, Blob> };
    expect(Object.keys(item.items)).toEqual(['text/html', 'text/plain']);
    expect(await item.items['text/plain'].text()).toBe(REPORT.text);
    expect(announced).toContain('Compte rendu copié.');
    expect(el.querySelector('.copied')?.textContent).toContain('Compte rendu copié.');
    vi.unstubAllGlobals();
  });

  it('presse-papiers refusé : le texte s’affiche sélectionné, avec la consigne', async () => {
    vi.stubGlobal('ClipboardItem', undefined);
    await open({
      writeText: async () => {
        throw new Error('NotAllowedError');
      },
    });
    el.querySelector<HTMLButtonElement>('.actions button')!.click();
    await settle(fixture);
    const area = el.querySelector<HTMLTextAreaElement>('textarea.fallback')!;
    expect(area.value).toBe(REPORT.text);
    expect(area.getAttribute('aria-describedby')).toBe('copy-hint');
    expect(el.querySelector('#copy-hint')?.textContent).toContain('Ctrl+C');
    vi.unstubAllGlobals();
  });

  it('révision : la rédaction démarre, puis la spécification s’ouvre, discussion ouverte et rédaction suivie', async () => {
    await open({}, [{ ...SPEC, revisionAvailable: true, revisionUnavailableReason: null }]);
    vi.spyOn(TestBed.inject(ConsentGate), 'ensure').mockResolvedValue(true);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const button = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Proposer la nouvelle version'));
    expect(button?.getAttribute('aria-label')).toBe('Proposer la nouvelle version de « Avoirs »');
    button!.click();
    await settle(fixture);
    ctrl.expectOne({ method: 'POST', url: '/api/interviews/i1/specs/s1/revision' }).flush({ conversationId: 'c1', turnId: 't1' });
    await settle(fixture);
    expect(navigate).toHaveBeenCalledWith(['/sessions', 's1', 'versions', 1], {
      queryParams: { discussion: 1, tour: 't1' },
    });
  });

  it('révision indisponible : la raison est dite, sans bouton', async () => {
    await open({}, [{ ...SPEC, appliedVersion: 2, revisionAvailable: false, revisionUnavailableReason: 'Version 2 appliquée.' }]);
    expect(el.querySelector('.reason')?.textContent).toContain('Version 2 appliquée.');
    expect([...el.querySelectorAll('button')].some((b) => b.textContent?.includes('Proposer'))).toBe(false);
    expect(el.querySelector('a[href="/sessions/s1/versions/2"]')).not.toBeNull();
  });
});

