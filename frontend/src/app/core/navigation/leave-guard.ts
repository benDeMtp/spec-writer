import { Injectable } from '@angular/core';

export type LeaveCheck = () => boolean | Promise<boolean>;

/**
 * Vérification de sortie de la page affichée (012, research.md R4) : une proposition en attente,
 * des réglages non enregistrés. Partagée par les gardes de route et le sélecteur de projet, qui
 * change de projet sans toujours changer d'adresse.
 */
@Injectable({ providedIn: 'root' })
export class LeaveGuard {
  private check: LeaveCheck | null = null;

  /** Enregistre la vérification de la page ; renvoie de quoi la retirer. */
  register(check: LeaveCheck): () => void {
    this.check = check;
    return () => {
      if (this.check === check) {
        this.check = null;
      }
    };
  }

  /** Vrai si la page peut être quittée, après sa confirmation éventuelle. */
  async confirm(): Promise<boolean> {
    return this.check ? await this.check() : true;
  }
}
