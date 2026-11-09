import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Document joint, sans son texte (009, contracts/api.md) : le texte extrait reste dans l'outil. */
export interface AttachmentView {
  id: string;
  title: string;
  fileName: string;
  format: 'DOCX' | 'XLSX' | 'PDF' | 'HTML';
  chars: number;
  addedAt?: string;
}

/** Les refus (format, document illisible, plafond, doublon) sont annoncés par le composant, pas par le bandeau. */
const SILENT = new HttpHeaders({ 'X-Sw-Silent': '1' });
const RAW = SILENT.set('Content-Type', 'application/octet-stream');

/**
 * Envoi d'un document joint (009, research.md R1) : contenu brut du fichier choisi, son nom en
 * paramètre ; le navigateur ne donne aucun chemin. Avant le démarrage, un brouillon gardé en mémoire ;
 * ensuite, un document de la session.
 */
@Injectable({ providedIn: 'root' })
export class AttachmentsApi {
  private readonly http = inject(HttpClient);

  draft(file: File): Promise<AttachmentView> {
    return firstValueFrom(
      this.http.post<AttachmentView>('/api/attachment-drafts', file, {
        params: new HttpParams().set('name', file.name),
        headers: RAW,
      }),
    );
  }

  forgetDraft(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/attachment-drafts/${encodeURIComponent(id)}`, { headers: SILENT }));
  }

  list(sessionId: string): Promise<AttachmentView[]> {
    return firstValueFrom(this.http.get<AttachmentView[]>(`/api/sessions/${encodeURIComponent(sessionId)}/attachments`));
  }

  add(sessionId: string, file: File): Promise<AttachmentView> {
    return firstValueFrom(
      this.http.post<AttachmentView>(`/api/sessions/${encodeURIComponent(sessionId)}/attachments`, file, {
        params: new HttpParams().set('name', file.name),
        headers: RAW,
      }),
    );
  }

  remove(sessionId: string, id: string): Promise<void> {
    return firstValueFrom(
      this.http.delete<void>(
        `/api/sessions/${encodeURIComponent(sessionId)}/attachments/${encodeURIComponent(id)}`,
        { headers: SILENT },
      ),
    );
  }
}
