import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { SessionsApi } from '../../core/sessions/sessions.api';
import { Language, SessionDetail, Specification, UseCase, VersionSummary } from '../../core/sessions/sessions.model';
import { ScenarioComponent } from '../../shared/ui/scenario.component';
import { VersionsPanelComponent } from './versions-panel.component';

/** Version de spécification : résumé, use cases, scénarios, hypothèses et exigences (FR-010). */
@Component({
  selector: 'sw-specification-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, RouterLink, ScenarioComponent, VersionsPanelComponent],
  template: `
    @if (spec(); as v) {
      <header class="head">
        <h1>{{ v.title }}</h1>
        <p class="meta">Version {{ v.number }} · {{ session()?.name }}</p>
        <a mat-button [routerLink]="['/sessions', id()]">Retour au questionnaire</a>
      </header>
      <div class="layout">
      <div class="sheet document">
        <section aria-labelledby="summary">
          <h2 id="summary">Résumé</h2>
          <p>{{ v.summary }}</p>
        </section>

        <section aria-labelledby="use-cases">
          <h2 id="use-cases">Use cases</h2>
          @for (uc of v.useCases; track uc.code) {
            <article class="use-case" [id]="uc.code">
              <h3><span class="code">{{ uc.code }}</span> {{ uc.title }}</h3>
              <dl class="facts">
                <dt>Acteur</dt>
                <dd>{{ uc.actor }}</dd>
                <dt>Objectif</dt>
                <dd>{{ uc.goal }}</dd>
                @if (uc.preconditions.length) {
                  <dt>Préconditions</dt>
                  <dd><ul>@for (p of uc.preconditions; track $index) {<li>{{ p }}</li>}</ul></dd>
                }
                <dt>Déroulement nominal</dt>
                <dd><ol>@for (step of uc.nominalFlow; track $index) {<li>{{ step }}</li>}</ol></dd>
                @if (uc.alternatives.length) {
                  <dt>Alternatives</dt>
                  <dd><ul>@for (a of uc.alternatives; track $index) {<li>{{ a }}</li>}</ul></dd>
                }
                <dt>Source</dt>
                <dd class="source">{{ sourceLabel(uc) }}</dd>
              </dl>
            </article>
          }
        </section>

        <section aria-labelledby="scenarios">
          <h2 id="scenarios">Scénarios d'acceptation</h2>
          @for (uc of v.useCases; track uc.code) {
            <h3><span class="code">{{ uc.code }}</span> {{ uc.title }}</h3>
            @for (sc of scenariosOf(uc.code); track sc.code) {
              <sw-scenario [scenario]="sc" [language]="language()" />
            }
          }
        </section>

        <section aria-labelledby="assumptions">
          <h2 id="assumptions">Hypothèses</h2>
          @if (v.assumptions.length) {
            <ul class="assumptions">
              @for (h of v.assumptions; track h.code) {
                <li><span class="code">{{ h.code }}</span> {{ h.statement }}</li>
              }
            </ul>
          } @else {
            <p>Aucune : tout provient du besoin et de vos réponses.</p>
          }
        </section>

        <section aria-labelledby="functional">
          <h2 id="functional">Exigences fonctionnelles</h2>
          <ul>@for (r of functional(); track r.code) {<li><span class="code">{{ r.code }}</span> {{ r.statement }}</li>} @empty {<li>Aucune.</li>}</ul>
        </section>
        <section aria-labelledby="technical">
          <h2 id="technical">Exigences techniques</h2>
          <ul>@for (r of technical(); track r.code) {<li><span class="code">{{ r.code }}</span> {{ r.statement }}</li>} @empty {<li>Aucune.</li>}</ul>
        </section>
      </div>
      @if (versions().length > 1) {
        <aside class="side">
          <sw-versions-panel [sessionId]="id()" [versions]="versions()" [current]="v.number" />
        </aside>
      }
      </div>
    } @else {
      <p role="status" aria-live="polite">Chargement de la spécification…</p>
    }
  `,
  styles: `
    .head h1 {
      margin: 0;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-3);
      font-weight: 600;
    }
    .meta {
      margin: 0.25rem 0 0.75rem;
      color: var(--sw-ink-soft);
    }
    .layout {
      display: grid;
      grid-template-columns: minmax(0, calc(var(--sw-measure) + 10rem)) 13rem;
      gap: 1.5rem;
      align-items: start;
      margin-top: 1rem;
    }
    .side {
      position: sticky;
      top: 1rem;
    }
    @media (max-width: 64rem) {
      .layout {
        grid-template-columns: minmax(0, 1fr);
      }
      .side {
        position: static;
        order: -1;
      }
    }
    .sheet {
      padding: 1.5rem clamp(1rem, 3vw, 2.5rem);
      background: var(--sw-sheet);
      border: 1px solid var(--sw-rule);
      border-radius: var(--sw-radius-sheet);
    }
    .document p,
    .document li,
    .document dd {
      font-family: var(--sw-font-doc);
      line-height: 1.65;
    }
    h2 {
      margin: 2rem 0 0.75rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-2);
      font-weight: 600;
    }
    section:first-child h2 {
      margin-top: 0;
    }
    h3 {
      margin: 1.25rem 0 0.5rem;
      font-size: var(--sw-step-1);
      font-weight: 650;
    }
    .code {
      color: var(--sw-ink-soft);
      font-weight: 500;
      margin-right: 0.25rem;
      font-family: var(--sw-font-ui);
    }
    .facts {
      display: grid;
      grid-template-columns: 11rem minmax(0, 1fr);
      gap: 0.25rem 1rem;
      margin: 0;
    }
    .facts dt {
      color: var(--sw-ink-soft);
    }
    .facts dd {
      margin: 0;
    }
    .facts ul,
    .facts ol {
      margin: 0;
      padding-left: 1.25rem;
    }
    .assumptions li {
      border-left: 3px solid var(--sw-outcome);
      padding-left: 0.5rem;
      list-style: none;
      margin-bottom: 0.25rem;
    }
    .assumptions {
      padding-left: 0;
    }
    @media (max-width: 36rem) {
      .facts {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class SpecificationPage {
  private readonly api = inject(SessionsApi);

  readonly id = input.required<string>();
  readonly n = input.required<string>();

  protected readonly spec = signal<Specification | null>(null);
  protected readonly session = signal<SessionDetail | null>(null);
  protected readonly versions = signal<VersionSummary[]>([]);
  protected readonly language = computed<Language>(() => this.session()?.language ?? 'fr');
  protected readonly functional = computed(() => this.spec()?.requirements.filter((r) => r.category === 'FUNCTIONAL') ?? []);
  protected readonly technical = computed(() => this.spec()?.requirements.filter((r) => r.category === 'TECHNICAL') ?? []);

  constructor() {
    // la même page affiche chaque version : elle se recharge quand le numéro change dans l'URL
    effect(() => {
      const id = this.id();
      const n = Number(this.n());
      void this.load(id, n);
    });
  }

  private async load(id: string, n: number): Promise<void> {
    const [spec, session, versions] = await Promise.all([this.api.version(id, n), this.api.get(id), this.api.versions(id)]);
    this.session.set(session);
    this.versions.set(versions);
    this.spec.set(spec);
  }

  protected scenariosOf(code: string) {
    return this.spec()?.scenarios.filter((s) => s.useCaseCode === code) ?? [];
  }

  /** Lien vers la source du use case (FR-011) : extrait du besoin, réponse citée ou hypothèse. */
  protected sourceLabel(uc: UseCase): string {
    const s = uc.source;
    if (!s) {
      return '';
    }
    switch (s.kind) {
      case 'NEED_EXCERPT':
        return `Extrait du besoin : « ${s.excerpt} »`;
      case 'ANSWER': {
        const q = this.session()?.answeredQuestions.find((x) => x.id === s.questionId);
        return q ? `Réponse à « ${q.label} » : ${q.answer}` : `Réponse : « ${s.excerpt} »`;
      }
      default:
        return `Hypothèse${s.excerpt ? ` : ${s.excerpt}` : ''}`;
    }
  }
}
