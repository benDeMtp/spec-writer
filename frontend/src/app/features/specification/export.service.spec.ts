import { DOCUMENT } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ExportService, fileNameFrom } from './export.service';

const DISPOSITION = "attachment; filename=\"Reservation-ete-v2.md\"; filename*=UTF-8''R%C3%A9servation-%C3%A9t%C3%A9-v2.md";

describe('ExportService', () => {
  let ctrl: HttpTestingController;
  let doc: Document;

  function setup(): ExportService {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    ctrl = TestBed.inject(HttpTestingController);
    doc = TestBed.inject(DOCUMENT);
    return TestBed.inject(ExportService);
  }

  function flush(): void {
    ctrl.expectOne('/api/sessions/s1/versions/2/export').flush('# Spécification\n', {
      headers: { 'Content-Disposition': DISPOSITION, 'Content-Type': 'text/markdown; charset=UTF-8' },
    });
  }

  afterEach(() => {
    delete (doc.defaultView as unknown as Record<string, unknown>)['showSaveFilePicker'];
    vi.restoreAllMocks();
  });

  it('ouvre le sélecteur du navigateur avec le nom proposé, qui gère la confirmation d’écrasement', async () => {
    const service = setup();
    const write = vi.fn<(data: Blob) => Promise<void>>(async () => undefined);
    const close = vi.fn(async () => undefined);
    const picker = vi.fn(async () => ({ createWritable: async () => ({ write, close }) }));
    Object.assign(doc.defaultView!, { showSaveFilePicker: picker });

    const result = service.exportMarkdown('s1', 2);
    flush();
    expect(await result).toBe('saved');
    expect(picker).toHaveBeenCalledWith(expect.objectContaining({ suggestedName: 'Réservation-été-v2.md' }));
    const blob = write.mock.calls[0][0] as Blob;
    expect(await blob.text()).toBe('# Spécification\n');
    expect(close).toHaveBeenCalled();
  });

  it('abandon dans le sélecteur : rien n’est écrit', async () => {
    const service = setup();
    Object.assign(doc.defaultView!, {
      showSaveFilePicker: vi.fn(async () => {
        throw new DOMException('annulé', 'AbortError');
      }),
    });
    const result = service.exportMarkdown('s1', 2);
    flush();
    expect(await result).toBe('cancelled');
  });

  it('sans sélecteur, repli sur un téléchargement classique', async () => {
    const service = setup();
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.download).toBe('Réservation-été-v2.md');
      expect(this.href).toBe('blob:x');
    });
    const result = service.exportMarkdown('s1', 2);
    flush();
    expect(await result).toBe('downloaded');
    expect(click).toHaveBeenCalledOnce();
    expect(doc.querySelector('a[download]')).toBeNull();
  });

  it('lit le nom du fichier, de préférence en UTF-8', () => {
    expect(fileNameFrom(DISPOSITION, 'x.md')).toBe('Réservation-été-v2.md');
    expect(fileNameFrom('attachment; filename="a-v1.md"', 'x.md')).toBe('a-v1.md');
    expect(fileNameFrom("attachment; filename*=UTF-8''%E0%A4%A", 'x.md')).toBe('x.md');
    expect(fileNameFrom(null, 'x.md')).toBe('x.md');
  });
});
