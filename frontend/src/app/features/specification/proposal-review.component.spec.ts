import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Proposal } from '../../core/jira/jira.model';
import { Specification } from '../../core/sessions/sessions.model';
import { ProposalReviewComponent } from './proposal-review.component';

const PROPOSAL = {
  proposalId: 'p1',
  baseVersion: 2,
  proposed: { number: 3, title: 'Avoirs avec annulation', origin: 'DISCUSSION' },
  unifiedDiff: '--- v2\n+++ proposition\n@@ -1 +1 @@\n-# Avoirs\n+# Avoirs avec annulation\n',
} as unknown as Proposal;

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
  await fixture.whenStable();
  fixture.detectChanges();
}

describe('ProposalReviewComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<ProposalReviewComponent>;
  let el: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProposalReviewComponent);
    fixture.componentRef.setInput('sessionId', 's1');
    fixture.componentRef.setInput('proposal', PROPOSAL);
    fixture.componentRef.setInput('title', 'Nouvelle version proposée');
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  function button(label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label)!;
  }

  it('montre les différences et la version que la proposition deviendra', () => {
    expect(el.querySelector('h2')?.textContent).toBe('Nouvelle version proposée');
    expect(el.textContent).toContain(
      'Proposition à partir de la version 2 : « Avoirs avec annulation »',
    );
    expect(el.textContent).toContain('elle deviendra la version 3');
    expect(el.querySelector('sw-unified-diff')).not.toBeNull();
  });

  it('« Appliquer » crée la version suivante', async () => {
    const applied: Specification[] = [];
    fixture.componentInstance.applied.subscribe((v) => applied.push(v));

    button('Appliquer').click();
    await settle(fixture);
    expect(button('Appliquer').disabled).toBe(true);
    ctrl
      .expectOne({ method: 'POST', url: '/api/sessions/s1/ai-edits/p1/accept' })
      .flush({ number: 3, origin: 'DISCUSSION' });
    await settle(fixture);

    expect(applied.map((v) => v.number)).toEqual([3]);
  });

  it('« Abandonner » ne crée rien', async () => {
    let discarded = 0;
    fixture.componentInstance.discarded.subscribe(() => discarded++);

    button('Abandonner').click();
    await settle(fixture);
    ctrl.expectOne({ method: 'DELETE', url: '/api/sessions/s1/proposals/p1' }).flush(null);
    await settle(fixture);

    expect(discarded).toBe(1);
    ctrl.verify();
  });
});
