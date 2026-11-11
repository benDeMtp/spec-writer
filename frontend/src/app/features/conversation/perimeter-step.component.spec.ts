import { LiveAnnouncer } from '@angular/cdk/a11y';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  PerimeterModule,
  PerimeterReason,
  PerimeterView,
} from '../../core/conversations/conversations.model';
import { PerimeterStepComponent } from './perimeter-step.component';

const NONE: Omit<PerimeterReason, 'type'> = {
  term: null,
  count: null,
  chain: null,
  of: null,
  element: null,
  related: null,
  description: null,
  technology: null,
};

function module(name: string, changes: Partial<PerimeterModule> = {}): PerimeterModule {
  return {
    id: `r1/modules/${name}`,
    rootLabel: 'rv',
    path: `modules/${name}`,
    name,
    kind: 'MAVEN',
    occurrences: 0,
    isNew: false,
    qualification: 'UNQUALIFIED',
    justification: null,
    reasons: [],
    architecture: null,
    archStatus: null,
    samples: [],
    ...changes,
  };
}

const COMMON = module('common-contract', {
  path: 'libs/common-contract',
  occurrences: 30,
  qualification: 'IMPACTED',
  justification: 'Définit l’énumération.',
  reasons: [{ ...NONE, type: 'TERM', term: 'ContractStatus', count: 30 }],
  samples: [
    { path: 'libs/common-contract/S.java', line: 3, term: 'ContractStatus', excerpt: 'enum ContractStatus {' },
  ],
});

const RELANCE = module('relance', {
  qualification: 'TO_CHECK',
  isNew: true,
  reasons: [
    {
      ...NONE,
      type: 'DEPENDENCY',
      chain: [
        { id: 'a', name: 'common-contract' },
        { id: 'b', name: 'relance' },
      ],
    },
    { ...NONE, type: 'ARCHITECTURE', related: 'Notification', technology: 'REST', description: 'envoie' },
  ],
  archStatus: 'ABSENT',
});

function view(changes: Partial<PerimeterView> = {}): PerimeterView {
  return {
    ordinal: 2,
    terms: ['ContractStatus', 'CONTRACT_STATUS'],
    rejectedTerms: [{ term: 'x', reason: 'TOO_SHORT' }],
    roots: [
      { rootId: 'r1', label: 'rv', state: 'SCANNED' },
      { rootId: 'r2', label: 'pipeline', state: 'MISSING' },
    ],
    state: 'COMPLETE',
    incompleteReason: null,
    filesScanned: 18402,
    filesSkipped: 0,
    skippedExamples: [],
    durationMs: 4200,
    qualified: true,
    summary: { impacted: 1, toCheck: 1, notImpacted: 0, unqualified: 1 },
    modules: [COMMON, RELANCE, module('annuaire', { occurrences: 1 })],
    architectureOnly: [],
    unavailable: null,
    ...changes,
  };
}

describe('PerimeterStepComponent', () => {
  let fixture: ComponentFixture<PerimeterStepComponent>;
  let el: HTMLElement;
  let announced: string[];

  function open(analysis: PerimeterView, live = false): void {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    announced = [];
    vi.spyOn(TestBed.inject(LiveAnnouncer), 'announce').mockImplementation(async (m) => {
      announced.push(String(m));
    });
    fixture = TestBed.createComponent(PerimeterStepComponent);
    fixture.componentRef.setInput('analysis', analysis);
    fixture.componentRef.setInput('messageId', 'm1');
    fixture.componentRef.setInput('live', live);
    fixture.detectChanges();
    el = fixture.nativeElement;
  }

  const text = (selector: string) =>
    el.querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

  it('montre tous les modules, groupés par qualification, avec leurs raisons', () => {
    open(view());
    expect(el.querySelector('details.perimeter')?.hasAttribute('open')).toBe(true);
    expect(text('summary')).toMatch(/^Analyse du périmètre · 2 termes · 2 racines · 18\s402 fichiers · 4,2 s$/);
    expect(el.textContent).toContain('Termes : ContractStatus, CONTRACT_STATUS');
    expect(el.textContent).toContain('x (trop court)');
    expect(text('.warning')).toBe('Racine pipeline introuvable : non analysée');
    expect(text('.summary')).toBe('3 modules : 1 impacté, 1 à vérifier, 1 non qualifié');
    const titles = Array.from(el.querySelectorAll('h3')).map((h) => h.textContent?.trim());
    expect(titles).toEqual(['Impactés (1)', 'À vérifier (1)', 'Non qualifiés (1)']);
    expect(el.querySelectorAll('table caption')).toHaveLength(3);
    expect(el.querySelectorAll('th[scope="col"]')).toHaveLength(9);

    const relance = el.querySelector('#perimeter-m1-2-1')!;
    expect(relance.textContent).toContain('Nouveau');
    const chain = Array.from(relance.querySelectorAll('ol.chain li')).map((li) => li.textContent);
    expect(chain).toEqual(['common-contract', 'relance']);
    expect(relance.textContent).toContain('relié à Notification (REST, « envoie »)');
    expect(relance.textContent).toContain('absent du modèle d’architecture');
  });

  it('nomme le module trouvé qu’un agrégateur liste', () => {
    const aggregates = module('aggregates', {
      path: '',
      qualification: 'TO_CHECK',
      reasons: [{ ...NONE, type: 'AGGREGATOR', of: { id: 'a', name: 'common-contract' } }],
    });
    open(view({ modules: [aggregates], summary: { impacted: 0, toCheck: 1, notImpacted: 0, unqualified: 0 } }));
    const row = el.querySelector('#perimeter-m1-2-0')!;
    expect(row.textContent).toContain('agrège common-contract');
    expect(row.querySelector('.path')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('rv · Maven');
  });

  it('liste à part les éléments d’architecture reliés qui n’ont pas de module', () => {
    open(
      view({
        architectureOnly: [
          { element: 'Archives', related: 'Facturation API', description: 'archive les factures', technology: 'SFTP' },
        ],
      }),
    );
    const section = el.querySelector('section[aria-labelledby="perimeter-m1-2-ARCH"]')!;
    expect(section.querySelector('h3')?.textContent?.trim()).toBe('Éléments d’architecture sans module (1)');
    expect(section.querySelector('li')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Archives : relié à Facturation API (SFTP, « archive les factures »)',
    );
  });

  it('dit quand l’échantillon des occurrences est partiel', () => {
    open(view());
    const row = el.querySelector('#perimeter-m1-2-0')!;
    expect(row.querySelector('details.occurrences summary')?.textContent).toContain('Occurrences (30)');
    expect(row.textContent).toContain('libs/common-contract/S.java:3');
    expect(row.querySelector('code')?.textContent).toBe('enum ContractStatus {');
    expect(row.textContent?.replace(/\s+/g, ' ')).toContain(
      '1 premières sur 30 pour ContractStatus. Affinez le terme pour un détail complet.',
    );
  });

  it('signale une analyse incomplète, et l’absence de résultat', () => {
    open(view({ state: 'INCOMPLETE', incompleteReason: 'MAX_DURATION', modules: [], roots: [] }));
    expect(el.textContent).toContain('Analyse incomplète : durée maximale atteinte.');
    expect(el.textContent).toContain('Aucun module ne contient ces termes.');
  });

  it('sans racine, renvoie vers les réglages du projet', () => {
    open(view({ unavailable: 'NO_ROOTS', modules: [], terms: ['ContractStatus'], roots: [] }), true);
    expect(el.textContent).toContain('Aucune source du code n’est déclarée pour ce projet.');
    const link = el.querySelector('a')!;
    expect(link.textContent).toBe('Déclarer les sources du code');
    expect(link.getAttribute('href')).toBe('/projet/reglages#sources-code');
    expect(announced).toEqual([]);
  });

  it('annonce le recensement, puis la qualification, pendant le tour', () => {
    open(view({ qualified: false }), true);
    expect(text('.summary')).toBe('3 modules : qualification en cours…');
    expect(el.querySelector('details.perimeter')?.getAttribute('aria-busy')).toBe('true');
    fixture.componentRef.setInput('analysis', view());
    fixture.detectChanges();
    expect(announced).toEqual(['Analyse du périmètre terminée : 3 modules.', 'Modules qualifiés.']);
    expect(el.querySelector('details.perimeter')?.getAttribute('aria-busy')).toBe('false');
  });
});
