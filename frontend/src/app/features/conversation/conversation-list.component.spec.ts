import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { ConversationListComponent } from './conversation-list.component';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

const THREADS = [
  { id: 'c2', projectId: 'p1', name: 'Lettrage', createdAt: 'x', updatedAt: 'x' },
  { id: 'c1', projectId: 'p1', name: 'Avoirs', createdAt: 'x', updatedAt: 'x' },
];

describe('ConversationListComponent', () => {
  let fixture: ComponentFixture<ConversationListComponent>;
  let ctrl: HttpTestingController;
  let el: HTMLElement;
  let changes: { deleted?: string }[];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ConversationListComponent);
    el = fixture.nativeElement;
    changes = [];
    fixture.componentInstance.changed.subscribe((c) => changes.push(c));
    fixture.componentRef.setInput('threads', THREADS);
    fixture.componentRef.setInput('currentId', 'c1');
    await settle(fixture);
  });

  function button(label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === label,
    ) as HTMLButtonElement;
  }

  it('liste la plus récente d’abord, marque la conversation ouverte', () => {
    const links = el.querySelectorAll('li a');
    expect(Array.from(links).map((a) => a.textContent?.trim())).toEqual(['Lettrage', 'Avoirs']);
    expect(links[1].getAttribute('aria-current')).toBe('page');
    expect(links[1].getAttribute('href')).toBe('/conversations/c1');
  });

  it('renomme puis supprime après confirmation', async () => {
    button('Renommer Avoirs').click();
    await settle(fixture);
    const input = el.querySelector('#name-c1') as HTMLInputElement;
    expect(input.value).toBe('Avoirs');
    input.value = 'Émission des avoirs';
    input.dispatchEvent(new Event('input'));
    await settle(fixture);
    button('Enregistrer').click();
    await settle(fixture);
    const patch = ctrl.expectOne({ method: 'PATCH', url: '/api/conversations/c1' });
    expect(patch.request.body).toEqual({ name: 'Émission des avoirs' });
    patch.flush({ ...THREADS[1], name: 'Émission des avoirs' });
    await settle(fixture);
    expect(changes).toEqual([{}]);
    expect(el.querySelector('[aria-live="polite"]')?.textContent).toContain(
      'Conversation renommée',
    );

    button('Supprimer Lettrage').click();
    await settle(fixture);
    expect(el.querySelector('.confirm')?.textContent).toContain('les spécifications restent');
    button('Annuler').click();
    await settle(fixture);
    ctrl.expectNone({ method: 'DELETE' });

    button('Supprimer Lettrage').click();
    await settle(fixture);
    button('Supprimer').click();
    await settle(fixture);
    ctrl.expectOne({ method: 'DELETE', url: '/api/conversations/c2' }).flush(null);
    await settle(fixture);
    expect(changes.at(-1)).toEqual({ deleted: 'c2' });
  });

  it('suppression refusée pendant une réponse : la raison est annoncée', async () => {
    button('Supprimer Avoirs').click();
    await settle(fixture);
    button('Supprimer').click();
    await settle(fixture);
    ctrl
      .expectOne({ method: 'DELETE', url: '/api/conversations/c1' })
      .flush(
        {
          code: 'OPERATION_IN_PROGRESS',
          userMessage: 'L’agent répond encore : arrêtez sa réponse.',
        },
        { status: 409, statusText: 'Conflict' },
      );
    await settle(fixture);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('L’agent répond encore');
    expect(changes).toEqual([]);
  });

  it('mentionne la spécification discutée, ou qu’elle a été supprimée', async () => {
    fixture.componentRef.setInput('threads', [
      {
        id: 'c3',
        projectId: 'p1',
        name: 'Discussion : Avoirs',
        createdAt: 'x',
        updatedAt: 'x',
        specification: { sessionId: 's1', name: 'Avoirs' },
      },
      {
        id: 'c4',
        projectId: 'p1',
        name: 'Discussion : Lettrage',
        createdAt: 'x',
        updatedAt: 'x',
        specification: { sessionId: null, name: 'Lettrage' },
      },
      ...THREADS,
    ]);
    await settle(fixture);

    const about = Array.from(el.querySelectorAll('.about')).map((a) => a.textContent?.trim());
    expect(about).toEqual([
      'Spécification : Avoirs',
      'Spécification : Lettrage (spécification supprimée)',
    ]);
  });
});
