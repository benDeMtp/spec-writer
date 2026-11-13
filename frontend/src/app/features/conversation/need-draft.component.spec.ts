import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { NeedDraft } from '../../core/conversations/conversations.model';
import { NeedDraftComponent } from './need-draft.component';

describe('NeedDraftComponent', () => {
  function open(draft: NeedDraft): HTMLElement {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    const fixture = TestBed.createComponent(NeedDraftComponent);
    fixture.componentRef.setInput('conversationId', 'c1');
    fixture.componentRef.setInput('draft', draft);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('annonce le périmètre que la spécification reprendra', () => {
    const el = open({ need: 'Suspendre un contrat.', points: [], perimeter: { moduleCount: 9, ordinal: 1 } });
    expect(el.querySelector('.perimeter')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'La spécification reprendra le périmètre analysé (9 modules).',
    );
  });

  it('ne dit rien sans analyse de périmètre', () => {
    const el = open({ need: 'Suspendre un contrat.', points: [], perimeter: null });
    expect(el.querySelector('.perimeter')).toBeNull();
  });
});
