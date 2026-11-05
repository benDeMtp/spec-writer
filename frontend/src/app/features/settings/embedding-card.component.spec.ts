import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { EmbeddingSettings } from '../../core/settings/embedding.api';
import { EmbeddingCardComponent } from './embedding-card.component';

const DEFAULT: EmbeddingSettings = {
  state: 'MISSING',
  modelId: 'multilingual-e5-small@761b726',
  problem: null,
  defaultModel: true,
  folder: null,
  pooling: 'MEAN',
  queryPrefix: null,
  passagePrefix: null,
};

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  for (let i = 0; i < 3; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
}

describe('EmbeddingCardComponent', () => {
  let ctrl: HttpTestingController;
  let fixture: ComponentFixture<EmbeddingCardComponent>;
  let el: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }],
    });
    ctrl = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmbeddingCardComponent);
    el = fixture.nativeElement;
    fixture.detectChanges();
    ctrl.expectOne('/api/settings/embedding').flush(DEFAULT);
    await settle(fixture);
  });

  afterEach(() => ctrl.verify());

  it('montre le modèle par défaut et son état', () => {
    expect(el.textContent).toContain('Modèle par défaut (multilingual-e5-small).');
    expect(el.textContent).toContain("Pas encore téléchargé : il le sera à la prochaine indexation, par le proxy d'entreprise.");
    expect(el.querySelector('input[name="queryPrefix"]')).toBeNull();
  });

  it('remplace le modèle par un dossier, avec pooling et préfixes', async () => {
    const folder = el.querySelector<HTMLInputElement>('input[name="folder"]')!;
    folder.value = '/Users/moi/modeles/e5-large';
    folder.dispatchEvent(new Event('input'));
    await settle(fixture);
    const prefix = el.querySelector<HTMLInputElement>('input[name="queryPrefix"]')!;
    prefix.value = 'query: ';
    prefix.dispatchEvent(new Event('input'));
    await settle(fixture);
    Array.from(el.querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => b.textContent?.trim() === 'Utiliser ce modèle')!
      .click();
    const put = ctrl.expectOne('/api/settings/embedding');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ folder: '/Users/moi/modeles/e5-large', pooling: 'MEAN', queryPrefix: 'query: ', passagePrefix: '' });
    put.flush({ ...DEFAULT, state: 'READY', defaultModel: false, folder: '/Users/moi/modeles/e5-large', queryPrefix: 'query: ' });
    await settle(fixture);
    expect(el.textContent).toContain('Modèle du dossier /Users/moi/modeles/e5-large.');
    expect(el.textContent).toContain('Prêt.');
  });
});
