import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { LaunchTokenService } from '../launch/launch-token.service';
import { OnboardingApi, STEP_SLUGS } from './onboarding.api';

/**
 * Ouverture automatique de l'assistant de premier lancement (018, research.md R5) : au démarrage,
 * après l'échange du jeton de lancement, une installation vierge est menée à l'étape courante.
 * Un onglet qui redevient visible relit la progression (plusieurs onglets ouverts).
 */
@Injectable({ providedIn: 'root' })
export class OnboardingService {
  private readonly api = inject(OnboardingApi);
  private readonly router = inject(Router);
  private readonly launch = inject(LaunchTokenService);
  private readonly document = inject(DOCUMENT);
  private started = false;

  start(destroy?: DestroyRef): void {
    if (this.started || this.launch.state() === 'rejected') {
      return;
    }
    this.started = true;
    void this.open();
    const onVisible = (): void => {
      if (this.document.visibilityState === 'visible') {
        void this.api.load().catch(() => undefined);
      }
    };
    this.document.addEventListener('visibilitychange', onVisible);
    destroy?.onDestroy(() => this.document.removeEventListener('visibilitychange', onVisible));
  }

  private async open(): Promise<void> {
    try {
      const o = await this.api.load();
      // l'URL du navigateur : la première navigation du routeur n'est peut-être pas encore faite
      if (o.autoOpen && !this.document.location.pathname.startsWith('/premiers-pas')) {
        await this.router.navigateByUrl(`/premiers-pas/${STEP_SLUGS[o.currentStep]}`);
      }
    } catch {
      // le bandeau d'erreur affiche la cause ; l'outil reste utilisable sans l'assistant
    }
  }
}
