import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { SearchResult } from './search.model';

@Injectable({ providedIn: 'root' })
export class SearchApi {
  private readonly http = inject(HttpClient);

  /** Première page sans curseur, puis le curseur rendu par la page précédente. */
  search(q: string, cursor: string | null = null): Promise<SearchResult> {
    let params = new HttpParams().set('q', q);
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    return firstValueFrom(this.http.get<SearchResult>('/api/search', { params }));
  }
}
