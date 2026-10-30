import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { JiraApi } from '../../core/jira/jira.api';
import { PushPreview } from '../../core/jira/jira.model';
import { CreateTicketDialogComponent } from './create-ticket-dialog.component';

const PREVIEW: PushPreview = {
  mode: 'CREATE',
  issueKey: null,
  projectKey: 'PROJ',
  issueTypes: [
    { id: '10002', name: 'Bug' },
    { id: '10001', name: 'Story' },
  ],
  defaultIssueTypeId: '10001',
  issueTypeId: '10001',
  summary: 'Réservation de salles',
  descriptionWiki: 'h1. Réservation de salles',
  requiredFields: [{ id: 'customfield_1', name: 'Points', kind: 'NUMBER', options: [] }],
};

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

function button(el: HTMLElement, label: string): HTMLButtonElement {
  return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label)!;
}

describe('CreateTicketDialogComponent', () => {
  const preview = vi.fn(async (): Promise<PushPreview> => PREVIEW);
  const create = vi.fn(async () => ({
    issueKey: 'PROJ-42',
    issueUrl: 'https://jira/browse/PROJ-42',
    syncState: 'IN_SYNC',
    linkedVersionNumber: 1,
  }));
  const close = vi.fn();
  let fixture: ComponentFixture<CreateTicketDialogComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: JiraApi, useValue: { preview, create } },
        { provide: MAT_DIALOG_DATA, useValue: { sessionId: 's1', versionNumber: 1 } },
        { provide: MatDialogRef, useValue: { close, disableClose: false } },
      ],
    });
    fixture = TestBed.createComponent(CreateTicketDialogComponent);
    el = fixture.nativeElement;
    fixture.detectChanges();
    await settle(fixture);
  });

  it("affiche l'aperçu avec Story présélectionné et ne crée rien sans le champ obligatoire", () => {
    expect(el.textContent).toContain('PROJ');
    expect(el.textContent).toContain('Réservation de salles');
    expect(el.querySelector('mat-select')?.textContent).toContain('Story');
    expect(el.querySelector('pre')?.textContent).toContain('h1. Réservation de salles');
    expect(button(el, 'Créer le ticket').disabled).toBe(true);
    expect(create).not.toHaveBeenCalled();
  });

  it('crée le ticket avec le champ rempli, puis affiche la clé et le lien', async () => {
    const input = el.querySelector<HTMLInputElement>('input[type=number]')!;
    input.value = '3';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    button(el, 'Créer le ticket').click();
    await settle(fixture);
    expect(create).toHaveBeenCalledWith('s1', 1, '10001', { customfield_1: '3' });
    const link = el.querySelector<HTMLAnchorElement>('a')!;
    expect(link.textContent).toContain('PROJ-42');
    expect(link.target).toBe('_blank');
    expect(link.rel).toContain('noopener');
    button(el, 'Fermer').click();
    expect(close).toHaveBeenCalledWith(expect.objectContaining({ issueKey: 'PROJ-42' }));
  });

  it('un champ non pris en charge empêche la création, avec une explication', async () => {
    preview.mockResolvedValueOnce({
      ...PREVIEW,
      issueTypeId: '10002',
      requiredFields: [{ id: 'components', name: 'Composants', kind: 'UNSUPPORTED', options: [] }],
    });
    fixture.componentInstance['changeType']('10002');
    await settle(fixture);
    expect(preview).toHaveBeenLastCalledWith('s1', 1, '10002');
    expect(el.textContent).toContain('« Composants » ne peut pas être rempli ici');
    expect(button(el, 'Créer le ticket').disabled).toBe(true);
  });
});
