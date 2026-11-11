import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { SessionsApi } from '../../core/sessions/sessions.api';
import { confirmDeleteSpecification } from './confirm-delete-specification';
import { ConfirmData } from './confirm-dialog.component';

const DATA: ConfirmData = {
  title: 'Supprimer la spécification ?',
  message: '« Avoirs » sera supprimée.',
  confirm: 'Supprimer',
};

function dialog(result: unknown, seen: ConfirmData[]): MatDialog {
  return {
    open: (_: unknown, config: { data: ConfirmData }) => {
      seen.push(config.data);
      return { afterClosed: () => of(result) };
    },
  } as unknown as MatDialog;
}

function api(count: number): SessionsApi {
  return { discussionCount: async () => count } as unknown as SessionsApi;
}

describe('confirmDeleteSpecification', () => {
  it('sans discussion, pas de case', async () => {
    const seen: ConfirmData[] = [];
    expect(await confirmDeleteSpecification(dialog(true, seen), api(0), 's1', DATA)).toEqual({
      keepDiscussions: false,
    });
    expect(seen[0].option).toBeUndefined();
  });

  it('avec des discussions, la case est cochée par défaut ; décochée, elles sont gardées', async () => {
    const seen: ConfirmData[] = [];
    expect(
      await confirmDeleteSpecification(
        dialog({ confirmed: true, option: true }, seen),
        api(2),
        's1',
        DATA,
      ),
    ).toEqual({ keepDiscussions: false });
    expect(seen[0].option).toEqual({ label: 'Supprimer aussi ses discussions', checked: true });
    expect(seen[0].message).toContain('Elle a 2 discussions avec l’IA.');

    expect(
      await confirmDeleteSpecification(
        dialog({ confirmed: true, option: false }, []),
        api(1),
        's1',
        DATA,
      ),
    ).toEqual({ keepDiscussions: true });
    expect(await confirmDeleteSpecification(dialog(false, []), api(1), 's1', DATA)).toBeNull();
  });
});
