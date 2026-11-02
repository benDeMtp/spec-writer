import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export type ExportOutcome = 'saved' | 'downloaded' | 'cancelled';

interface SaveFilePickerOptions {
  suggestedName: string;
  types: { description: string; accept: Record<string, string[]> }[];
}

interface WritableFile {
  write(data: Blob): Promise<void>;
  close(): Promise<void>;
}

type PickerWindow = Window & {
  showSaveFilePicker?: (options: SaveFilePickerOptions) => Promise<{ createWritable(): Promise<WritableFile> }>;
};

/** Nom du fichier proposé par le serveur (Content-Disposition, forme UTF-8 de préférence). */
export function fileNameFrom(disposition: string | null, fallback: string): string {
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition ?? '');
  if (utf8) {
    try {
      return decodeURIComponent(utf8[1]);
    } catch {
      // nom mal encodé : on passe au nom ASCII
    }
  }
  return /filename="([^"]+)"/i.exec(disposition ?? '')?.[1] ?? fallback;
}

/**
 * Export Markdown d'une version (FR-019, FR-020). Le sélecteur de fichier du navigateur, quand il
 * existe, choisit l'emplacement et demande confirmation avant d'écraser ; sinon, téléchargement.
 */
@Injectable({ providedIn: 'root' })
export class ExportService {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);

  async exportMarkdown(sessionId: string, versionNumber: number): Promise<ExportOutcome> {
    const response = await firstValueFrom(
      this.http.get(`/api/sessions/${encodeURIComponent(sessionId)}/versions/${versionNumber}/export`, {
        observe: 'response',
        responseType: 'text',
      }),
    );
    const name = fileNameFrom(response.headers.get('Content-Disposition'), `specification-v${versionNumber}.md`);
    const blob = new Blob([response.body ?? ''], { type: 'text/markdown;charset=utf-8' });
    const win = this.document.defaultView as PickerWindow | null;
    if (win?.showSaveFilePicker) {
      return this.save(win, blob, name);
    }
    this.download(blob, name);
    return 'downloaded';
  }

  private async save(win: PickerWindow, blob: Blob, name: string): Promise<ExportOutcome> {
    try {
      const handle = await win.showSaveFilePicker!({
        suggestedName: name,
        types: [{ description: 'Document Markdown', accept: { 'text/markdown': ['.md'] } }],
      });
      const file = await handle.createWritable();
      await file.write(blob);
      await file.close();
      return 'saved';
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return 'cancelled';
      }
      throw error;
    }
  }

  private download(blob: Blob, name: string): void {
    const url = URL.createObjectURL(blob);
    const link = this.document.createElement('a');
    link.href = url;
    link.download = name;
    link.style.display = 'none';
    this.document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
