import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MentionState } from '../../core/projects/projects.model';
import { AttachmentView } from '../../core/sessions/attachments.api';
import { AttachmentsComponent } from './attachments.component';
import { MENTION_DELAYS } from './mention.directive';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

const CR: AttachmentView = { id: 'd1', title: 'CR COPIL', fileName: 'CR COPIL.pdf', format: 'PDF', chars: 18234 };

function state(path: string, s: MentionState['state'], share: number): MentionState {
  return {
    text: 'CR COPIL',
    field: 'ATTACHMENT',
    state: s,
    document: { kind: 'ATTACHMENT', title: 'CR COPIL', path, insert: null },
    candidates: [],
    transmittedShare: share,
  };
}

describe('AttachmentsComponent', () => {
  let fixture: ComponentFixture<AttachmentsComponent>;
  let ctrl: HttpTestingController;
  let el: HTMLElement;
  let changed: string[][];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MENTION_DELAYS, useValue: { suggest: 0, resolve: 0 } },
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AttachmentsComponent);
    el = fixture.nativeElement;
    changed = [];
    fixture.componentInstance.changed.subscribe((ids) => changed.push(ids));
    fixture.componentRef.setInput('projectId', 'p1');
  });

  function choose(name: string): void {
    const input = el.querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(input, 'files', { value: [new File(['contenu'], name)], configurable: true });
    input.dispatchEvent(new Event('change'));
  }

  function button(label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label) as HTMLButtonElement;
  }

  it('joint un brouillon, montre son état, annonce un refus et le retire', async () => {
    fixture.componentRef.setInput('fields', [{ field: 'NEED', text: 'voir @D1' }]);
    fixture.detectChanges();
    await settle(fixture);
    expect(button('Joindre un document')).toBeTruthy();
    expect((el.querySelector('input[type="file"]') as HTMLInputElement).accept).toBe('.docx,.xlsx,.pdf,.html,.htm');

    choose('CR COPIL.pdf');
    await settle(fixture);
    const post = ctrl.expectOne((r) => r.method === 'POST' && r.url === '/api/attachment-drafts');
    expect(post.request.params.get('name')).toBe('CR COPIL.pdf');
    expect(post.request.headers.get('Content-Type')).toBe('application/octet-stream');
    expect(post.request.body).toBeInstanceOf(File);
    post.flush(CR);
    await settle(fixture);
    expect(changed.at(-1)).toEqual(['d1']);

    const resolve = ctrl.expectOne('/api/projects/p1/mentions/resolve');
    expect(resolve.request.body).toEqual({
      sessionId: null,
      fields: [{ field: 'NEED', text: 'voir @D1' }],
      attachmentIds: ['d1'],
    });
    resolve.flush([
      { ...state('ailleurs', 'TRANSMITTED', 100), field: 'NEED' },
      { ...state('CR COPIL.pdf', 'PARTIAL', 40), betterModel: { id: 'g', name: 'Grand modèle', multiplier: 1 } },
    ]);
    await settle(fixture);
    const item = el.querySelector('li')!;
    expect(item.textContent).toContain('CR COPIL');
    expect(item.textContent).toContain('CR COPIL.pdf');
    expect(item.textContent).toContain('18\u202f234 caractères');
    expect(item.textContent).toContain('transmis en partie (environ 40 %)');
    expect(item.textContent).toContain('Grand modèle');
    expect(el.querySelector('[aria-live]')?.textContent).toContain('« CR COPIL » est joint.');

    choose('ancien.doc');
    await settle(fixture);
    ctrl
      .expectOne((r) => r.url === '/api/attachment-drafts')
      .flush(
        { code: 'UNSUPPORTED_FORMAT', userMessage: 'Format non pris en charge.', action: 'EDIT_INPUT' },
        { status: 422, statusText: 'Unprocessable' },
      );
    await settle(fixture);
    expect(el.querySelector('[aria-live]')?.textContent).toContain('« ancien.doc » n\'est pas joint : Format non pris en charge.');
    expect(el.querySelectorAll('li').length).toBe(1);

    (el.querySelector('button[aria-label="Retirer CR COPIL"]') as HTMLButtonElement).click();
    await settle(fixture);
    ctrl.expectOne({ method: 'DELETE', url: '/api/attachment-drafts/d1' }).flush(null);
    await settle(fixture);
    expect(changed.at(-1)).toEqual([]);
    expect(el.querySelectorAll('li').length).toBe(0);
    ctrl.verify();
  });

  it('avec une session : ses documents, ajout, retrait, désactivé sur une feature implémentée', async () => {
    fixture.componentRef.setInput('sessionId', 's1');
    fixture.detectChanges();
    await settle(fixture);
    ctrl.expectOne('/api/sessions/s1/attachments').flush([CR]);
    await settle(fixture);
    const resolve = ctrl.expectOne('/api/projects/p1/mentions/resolve');
    expect(resolve.request.body).toEqual({ sessionId: 's1', fields: [] });
    resolve.flush([state('CR COPIL.pdf', 'TRANSMITTED', 100)]);
    await settle(fixture);
    expect(el.querySelector('li')?.textContent).toContain('transmis à l’IA');

    choose('Annexe.docx');
    await settle(fixture);
    const add = ctrl.expectOne((r) => r.method === 'POST' && r.url === '/api/sessions/s1/attachments');
    expect(add.request.params.get('name')).toBe('Annexe.docx');
    add.flush({ id: 'a2', title: 'Annexe', fileName: 'Annexe.docx', format: 'DOCX', chars: 10 });
    await settle(fixture);
    ctrl.expectOne('/api/projects/p1/mentions/resolve').flush([]);
    await settle(fixture);
    expect(el.querySelectorAll('li').length).toBe(2);

    (el.querySelector('button[aria-label="Retirer Annexe"]') as HTMLButtonElement).click();
    await settle(fixture);
    ctrl.expectOne({ method: 'DELETE', url: '/api/sessions/s1/attachments/a2' }).flush(null);
    await settle(fixture);
    ctrl.expectOne('/api/projects/p1/mentions/resolve').flush([]);
    await settle(fixture);
    expect(el.querySelectorAll('li').length).toBe(1);

    fixture.componentRef.setInput('disabled', true);
    await settle(fixture);
    expect(button('Joindre un document').disabled).toBe(true);
    expect((el.querySelector('button[aria-label="Retirer CR COPIL"]') as HTMLButtonElement).disabled).toBe(true);
    ctrl.verify();
  });

  it('avec une conversation : ses documents, état demandé pour la conversation, ajout', async () => {
    fixture.componentRef.setInput('conversationId', 'c1');
    fixture.detectChanges();
    await settle(fixture);
    ctrl.expectOne('/api/conversations/c1/attachments').flush([CR]);
    await settle(fixture);
    const resolve = ctrl.expectOne('/api/projects/p1/mentions/resolve');
    expect(resolve.request.body).toEqual({ sessionId: null, fields: [], conversationId: 'c1' });
    resolve.flush([state('CR COPIL.pdf', 'TRANSMITTED', 100)]);
    await settle(fixture);

    choose('Annexe.docx');
    await settle(fixture);
    ctrl.expectOne((r) => r.method === 'POST' && r.url === '/api/conversations/c1/attachments')
      .flush({ id: 'a2', title: 'Annexe', fileName: 'Annexe.docx', format: 'DOCX', chars: 10 });
    await settle(fixture);
    ctrl.expectOne('/api/projects/p1/mentions/resolve').flush([]);
    await settle(fixture);
    expect(changed.at(-1)).toEqual([CR.id, 'a2']);

    fixture.componentRef.setInput('conversationId', null);
    await settle(fixture);
    expect(el.querySelectorAll('li').length).toBe(0);
    ctrl.verify();
  });
});
