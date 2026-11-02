import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { ConsentService } from '../../core/settings/settings.model';
import { SettingsStore } from '../../core/settings/settings.store';
import { ConsentDialogComponent } from './consent-dialog.component';

/** Demande l'accord avant le premier envoi à un service tiers (FR-024). Renvoie true s'il est donné. */
@Injectable({ providedIn: 'root' })
export class ConsentGate {
  private readonly settings = inject(SettingsStore);
  private readonly dialog = inject(MatDialog);

  async ensure(...services: ConsentService[]): Promise<boolean> {
    const current = await this.settings.ensureLoaded();
    for (const service of services) {
      const granted = service === 'COPILOT' ? current.copilot.consent : current.jira?.consent;
      if (granted) {
        continue;
      }
      const accepted = await firstValueFrom(this.dialog.open(ConsentDialogComponent, { data: { service } }).afterClosed());
      if (accepted !== true) {
        return false;
      }
      await this.settings.setConsent(service, true);
    }
    return true;
  }
}
