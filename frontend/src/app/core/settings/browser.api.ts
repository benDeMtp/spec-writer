import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Navigateur installé sur le poste (013, T057). */
export interface Browser {
  id: string;
  name: string;
}

/** Navigateur où s'ouvre spec-writer : `selected` vaut `null` pour celui du système. */
export interface BrowserSettings {
  selected: string | null;
  missing: boolean;
  installed: Browser[];
}

/** Ouverture dans le navigateur choisi : `browser` vaut `null` pour celui du système. */
export interface BrowserOpened {
  opened: boolean;
  browser: Browser | null;
}

@Injectable({ providedIn: 'root' })
export class BrowserApi {
  private readonly http = inject(HttpClient);

  get(): Promise<BrowserSettings> {
    return firstValueFrom(this.http.get<BrowserSettings>('/api/settings/browser'));
  }

  /** `null` revient au navigateur du système. */
  save(browserId: string | null): Promise<BrowserSettings> {
    return firstValueFrom(this.http.put<BrowserSettings>('/api/settings/browser', { browserId }));
  }

  open(): Promise<BrowserOpened> {
    return firstValueFrom(this.http.post<BrowserOpened>('/api/settings/browser/open', null));
  }
}
