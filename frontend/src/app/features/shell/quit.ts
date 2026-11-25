import { Injector } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { ApplicationApi, Installation, Os, WorkItem } from '../../core/lifecycle/application.api';
import { LifecycleStore } from '../../core/lifecycle/lifecycle.store';
import { ConfirmData, ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';

/**
 * Quitter spec-writer (019, contracts/ui.md) : confirmation qui nomme les travaux en cours et leur
 * devenir, puis arrêt. Chargé à la demande depuis le menu, comme la visite guidée, pour garder le
 * bundle initial sous son budget.
 */
export async function quitApplication(injector: Injector): Promise<boolean> {
  const api = injector.get(ApplicationApi);
  const lifecycle = injector.get(LifecycleStore);
  const state = await api.state();
  const relaunch = relaunchText(state.os, state.installation);
  const confirmed = await firstValueFrom(
    injector.get(MatDialog).open(ConfirmDialogComponent, { data: quitConfirmation(state.work, relaunch) }).afterClosed(),
  );
  if (confirmed !== true) {
    return false;
  }
  await api.quit();
  lifecycle.relaunch.set(relaunch);
  lifecycle.stopped();
  return true;
}

/** Contenu de la confirmation : sans travail, ou avec la liste des travaux et leur devenir. */
export function quitConfirmation(work: WorkItem[], relaunch: string): ConfirmData {
  if (work.length === 0) {
    return {
      title: 'Quitter spec-writer ?',
      message: `L'outil s'arrête. Vos données sont gardées. ${relaunch}`,
      confirm: 'Quitter',
    };
  }
  return {
    title: 'Quitter spec-writer ?',
    message: 'Ces travaux sont en cours :',
    items: work.map(workLabel),
    confirm: 'Quitter quand même',
  };
}

export function workLabel(w: WorkItem): string {
  switch (w.kind) {
    case 'INDEXATION':
      return `Indexation${w.project ? ` du projet ${w.project}` : ''} : elle s'arrête, à relancer depuis Connaissance du projet.`;
    case 'AGENT_REPLY':
      return `Réponse de l'agent${w.conversation ? ` dans ${w.conversation}` : ''} : le texte déjà reçu est gardé, marqué incomplet.`;
    case 'REVISION':
      return `Proposition de version${w.conversation ? ` dans ${w.conversation}` : ''} : elle est perdue, à relancer.`;
    case 'GENERATION':
      return `Génération d'une spécification${w.project ? ` du projet ${w.project}` : ''} : elle est perdue, à relancer.`;
  }
}

/** Consigne de relance selon le système et le mode d'installation (contracts/ui.md). */
export function relaunchText(os: Os, installation: Installation): string {
  if (installation === 'SOURCES') {
    return 'Pour le relancer, lancez dist/scripts/spec-writer.sh (ou spec-writer.cmd sous Windows).';
  }
  switch (os) {
    case 'MACOS':
      return 'Pour le relancer, cliquez sur son icône dans le Dock ou ouvrez-le depuis le Launchpad.';
    case 'WINDOWS':
      return 'Pour le relancer, ouvrez-le depuis le menu Démarrer.';
    case 'LINUX':
      return 'Pour le relancer, ouvrez-le depuis le menu des applications.';
  }
}
