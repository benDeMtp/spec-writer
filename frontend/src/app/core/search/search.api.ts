import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { SearchResult } from './search.model';

@Injectable({ providedIn: 'root' })
export class SearchApi {
  private readonly http = inject(HttpClient);

  search(q: string, page = 0): Promise<SearchResult> {
    const params = new HttpParams().set('q', q).set('page', page);
    return firstValueFrom(this.http.get<SearchResult>('/api/search', { params }));
  }
}
