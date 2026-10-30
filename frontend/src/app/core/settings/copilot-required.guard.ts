import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SettingsStore } from './settings.store';

/** Le questionnaire exige une connexion Copilot : sinon, direction la configuration (US1-1). */
export const copilotRequiredGuard: CanActivateFn = async () => {
  const store = inject(SettingsStore);
  const router = inject(Router);
  try {
    const settings = await store.load();
    if (settings.copilot.state === 'CONNECTED') {
      return true;
    }
  } catch {
    // L'erreur est déjà affichée par le bandeau ; on reste sur la configuration.
  }
  return router.createUrlTree(['/configuration'], { queryParams: { raison: 'copilot' } });
};
