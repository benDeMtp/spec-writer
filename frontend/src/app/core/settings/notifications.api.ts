import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Notifications du navigateur des sources mises à jour (017, FR-016). */
export interface NotificationSettings {
  sourceUpdates: boolean;
}

@Injectable({ providedIn: 'root' })
export class NotificationsApi {
  private readonly http = inject(HttpClient);

  get(): Promise<NotificationSettings> {
    return firstValueFrom(this.http.get<NotificationSettings>('/api/settings/notifications'));
  }

  save(sourceUpdates: boolean): Promise<NotificationSettings> {
    return firstValueFrom(this.http.put<NotificationSettings>('/api/settings/notifications', { sourceUpdates }));
  }
}
