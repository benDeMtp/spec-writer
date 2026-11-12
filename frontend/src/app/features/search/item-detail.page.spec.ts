import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideMarkdown } from 'ngx-markdown';
import { Project } from '../../core/projects/projects.model';
import { ProjectsStore } from '../../core/projects/projects.store';
import { ConsentGate } from '../../shared/ui/consent-gate.service';
import { ItemDetailPage } from './item-detail.page';

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

describe('ItemDetailPage', () => {
  it('« Discuter avec l’IA » importe le ticket, puis ouvre la discussion de sa spécification', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideMarkdown(),
        { provide: ConsentGate, useValue: { ensure: async () => true } },
      ],
    });
    const ctrl = TestBed.inject(HttpTestingController);
    TestBed.inject(ProjectsStore).current.set({ id: 'p1', name: 'Aurora' } as Project);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ItemDetailPage);
    fixture.componentRef.setInput('key', 'FACT-7');
    await settle(fixture);
    ctrl.expectOne('/api/jira/issues/FACT-7').flush({
      key: 'FACT-7',
      summary: 'Émettre un avoir',
      updated: '2026-11-10T09:00:00Z',
      url: 'https://jira.example/browse/FACT-7',
      descriptionMarkdown: 'Le service Avoirs émet l’avoir.',
    });
    await settle(fixture);
    const el: HTMLElement = fixture.nativeElement;
    // 012 : le projet de destination est annoncé avant l'action
    expect(el.querySelector('.destination')?.textContent).toBe('La spécification sera ajoutée au projet Aurora.');
    const discuss = Array.from(el.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === "Discuter avec l'IA",
    )!;

    discuss.click();
    await settle(fixture);
    ctrl
      .expectOne({ method: 'POST', url: '/api/jira/issues/FACT-7/import' })
      .flush({ id: 's1', latestVersion: 2, warnings: [] });
    await settle(fixture);

    expect(navigate).toHaveBeenCalledWith(['/sessions', 's1', 'versions', '2'], {
      queryParams: { discussion: 1 },
      state: { warnings: [] },
    });
  });
});
