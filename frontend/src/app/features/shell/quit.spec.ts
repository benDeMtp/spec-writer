import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { LifecycleStore } from '../../core/lifecycle/lifecycle.store';
import { ConfirmData } from '../../shared/ui/confirm-dialog.component';
import { quitApplication, quitConfirmation, relaunchText } from './quit';

describe('Quitter spec-writer', () => {
  let ctrl: HttpTestingController;
  let opened: ConfirmData[];
  let answer: unknown;

  beforeEach(() => {
    opened = [];
    answer = true;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MatDialog,
          useValue: {
            open: (_: unknown, config: { data: ConfirmData }) => {
              opened.push(config.data);
              return { afterClosed: () => of(answer) };
            },
          },
        },
      ],
    });
    ctrl = TestBed.inject(HttpTestingController);
  });

  afterEach(() => ctrl.verify());

  async function run(work: unknown[]): Promise<boolean> {
    const done = quitApplication(TestBed.inject(Injector));
    ctrl.expectOne('/api/application').flush({ os: 'LINUX', installation: 'INSTALLER', work });
    await new Promise((r) => setTimeout(r, 0));
    if (answer === true) {
      const quit = ctrl.expectOne('/api/application/quit');
      expect(quit.request.method).toBe('POST');
      quit.flush({ stopping: true }, { status: 202, statusText: 'Accepted' });
    }
    return done;
  }

  it('sans travail : « Quitter » et « Annuler », puis arrêt et écran d’arrêt', async () => {
    expect(await run([])).toBe(true);
    expect(opened[0].confirm).toBe('Quitter');
    expect(opened[0].items).toBeUndefined();
    expect(opened[0].message).toContain('menu des applications');
    const lifecycle = TestBed.inject(LifecycleStore);
    expect(lifecycle.state()).toBe('stopped');
    expect(lifecycle.relaunch()).toContain('menu des applications');
  });

  it('avec des travaux : leur liste, leur devenir, « Quitter quand même »', async () => {
    await run([
      { kind: 'INDEXATION', project: 'Aurora' },
      { kind: 'AGENT_REPLY', project: 'Aurora', conversation: 'Facturation des avoirs' },
    ]);
    expect(opened[0].confirm).toBe('Quitter quand même');
    expect(opened[0].items).toEqual([
      "Indexation du projet Aurora : elle s'arrête, à relancer depuis Connaissance du projet.",
      "Réponse de l'agent dans Facturation des avoirs : le texte déjà reçu est gardé, marqué incomplet.",
    ]);
  });

  it('« Annuler » ne quitte pas', async () => {
    answer = false;
    expect(await run([])).toBe(false);
    expect(TestBed.inject(LifecycleStore).state()).toBe('running');
  });

  it('consigne de relance selon le système et le mode d’installation', () => {
    expect(relaunchText('MACOS', 'INSTALLER')).toContain('Dock');
    expect(relaunchText('WINDOWS', 'INSTALLER')).toContain('menu Démarrer');
    expect(relaunchText('LINUX', 'INSTALLER')).toContain('menu des applications');
    expect(relaunchText('LINUX', 'SOURCES')).toContain('dist/scripts/spec-writer.sh');
  });

  it('propositions et générations sont dites perdues', () => {
    const data = quitConfirmation(
      [
        { kind: 'REVISION', conversation: 'Avoirs' },
        { kind: 'GENERATION', project: 'Aurora' },
      ],
      '',
    );
    expect(data.items).toEqual([
      'Proposition de version dans Avoirs : elle est perdue, à relancer.',
      "Génération d'une spécification du projet Aurora : elle est perdue, à relancer.",
    ]);
  });
});
