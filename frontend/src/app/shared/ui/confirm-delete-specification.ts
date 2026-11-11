import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { SessionsApi } from '../../core/sessions/sessions.api';
import { ConfirmData, ConfirmDialogComponent, ConfirmResult } from './confirm-dialog.component';

/**
 * Confirmation de la suppression d'une spécification (011, FR-016) : la case « Supprimer aussi
 * ses discussions », cochée par défaut, n'apparaît que si elle en a. Renvoie `null` si l'action
 * est annulée, sinon s'il faut garder les discussions.
 */
export async function confirmDeleteSpecification(
  dialog: MatDialog,
  api: SessionsApi,
  sessionId: string,
  data: ConfirmData,
): Promise<{ keepDiscussions: boolean } | null> {
  const count = await api.discussionCount(sessionId).catch(() => 0);
  const withOption: ConfirmData =
    count > 0
      ? {
          ...data,
          message: `${data.message} Elle a ${count > 1 ? `${count} discussions` : 'une discussion'} avec l’IA.`,
          option: { label: 'Supprimer aussi ses discussions', checked: true },
        }
      : data;
  const result = (await firstValueFrom(
    dialog.open(ConfirmDialogComponent, { data: withOption }).afterClosed(),
  )) as boolean | ConfirmResult | undefined;
  if (result === true) {
    return { keepDiscussions: false };
  }
  if (result && typeof result === 'object' && result.confirmed) {
    return { keepDiscussions: !result.option };
  }
  return null;
}
