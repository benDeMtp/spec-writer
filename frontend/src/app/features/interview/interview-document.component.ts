import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, TemplateRef, computed, input } from '@angular/core';
import { MarkdownComponent } from 'ngx-markdown';
import { Anchor, Stop } from '../../core/interviews/interviews.model';
import { Language, Specification } from '../../core/sessions/sessions.model';
import { ScenarioComponent } from '../../shared/ui/scenario.component';
import { storyParts } from '../../shared/ui/story-parts';

/** Clé courte d'une ancre, comme dans la sortie du modèle : `UC-01`, `UC-01:2`, `UC-01:A1`. */
export function anchorKey(a: Anchor): string {
  if (!a.step) {
    return a.element;
  }
  return `${a.element}:${a.alternative ? 'A' : ''}${a.step}`;
}

/** Contexte de l'emplacement inséré après chaque élément. */
export interface SlotContext {
  $implicit: Stop;
}

/**
 * La spécification d'une séance en document (021, conception du 20/11/2026) : déroulée de haut en
 * bas, chaque use case suivi de ses scénarios ; après chaque élément du parcours, l'emplacement
 * reçu (`slot`) affiche ses points. Chaque élément porte son ancre (`data-anchor`), dont la
 * sélection d'un passage se sert pour rattacher la remarque.
 */
@Component({
  selector: 'sw-interview-document',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MarkdownComponent, NgTemplateOutlet, ScenarioComponent],
  template: `
    @let s = spec();
    <ng-template #after let-key>
      @if (stopOf(key); as stop) {
        <ng-container [ngTemplateOutlet]="slot()" [ngTemplateOutletContext]="{ $implicit: stop }" />
      }
    </ng-template>

    @if (s.story; as story) {
      <p class="story">
        @for (part of storyParts(story); track part.lead) {
          {{ part.lead }} <strong>{{ part.text }}</strong
          >@if (part.assumed) {
            <span class="assumed"> (hypothèse)</span>
          }{{ part.end }}
        }
      </p>
    }

    <section aria-labelledby="doc-summary">
      <h2 id="doc-summary">Résumé</h2>
      <div class="el" data-anchor="SUMMARY">
        <markdown class="rich" [data]="s.summary" />
        <ng-container [ngTemplateOutlet]="after" [ngTemplateOutletContext]="{ $implicit: 'SUMMARY' }" />
      </div>
      @if (s.details) {
        <h3>Détails</h3>
        <markdown class="rich" [data]="s.details" />
      }
    </section>

    @for (uc of s.useCases; track uc.code) {
      <section class="use-case" [attr.aria-labelledby]="'doc-' + uc.code">
        <div class="el" [attr.data-anchor]="uc.code">
          <h2 [id]="'doc-' + uc.code"><span class="code">{{ uc.code }}</span> {{ uc.title }}</h2>
          <dl class="facts">
            <dt>Acteur</dt>
            <dd>{{ uc.actor }}</dd>
            <dt>Objectif</dt>
            <dd>{{ uc.goal }}</dd>
            @if (uc.preconditions.length) {
              <dt>Préconditions</dt>
              <dd>
                <ul>
                  @for (p of uc.preconditions; track $index) {
                    <li>{{ p }}</li>
                  }
                </ul>
              </dd>
            }
          </dl>
          <ng-container [ngTemplateOutlet]="after" [ngTemplateOutletContext]="{ $implicit: uc.code }" />
        </div>
        <h3>Déroulement nominal</h3>
        <ol class="steps">
          @for (step of uc.nominalFlow; track $index) {
            @let key = uc.code + ':' + ($index + 1);
            <li class="el" [attr.data-anchor]="key">
              <span class="line">{{ step }}</span>
              <ng-container [ngTemplateOutlet]="after" [ngTemplateOutletContext]="{ $implicit: key }" />
            </li>
          }
        </ol>
        @if (uc.alternatives.length) {
          <h3>Alternatives</h3>
          <ul class="steps">
            @for (a of uc.alternatives; track $index) {
              @let key = uc.code + ':A' + ($index + 1);
              <li class="el" [attr.data-anchor]="key">
                <span class="line">{{ a }}</span>
                <ng-container [ngTemplateOutlet]="after" [ngTemplateOutletContext]="{ $implicit: key }" />
              </li>
            }
          </ul>
        }
        @for (sc of scenariosOf(uc.code); track sc.code) {
          <div class="el scenario" [attr.data-anchor]="sc.code">
            <sw-scenario [scenario]="sc" [language]="language()" />
            <ng-container [ngTemplateOutlet]="after" [ngTemplateOutletContext]="{ $implicit: sc.code }" />
          </div>
        }
      </section>
    }

    @if (orphans().length) {
      <section aria-labelledby="doc-scenarios">
        <h2 id="doc-scenarios">Autres scénarios</h2>
        @for (sc of orphans(); track sc.code) {
          <div class="el scenario" [attr.data-anchor]="sc.code">
            <sw-scenario [scenario]="sc" [language]="language()" />
            <ng-container [ngTemplateOutlet]="after" [ngTemplateOutletContext]="{ $implicit: sc.code }" />
          </div>
        }
      </section>
    }

    @for (group of requirements(); track group.title) {
      @if (group.items.length) {
        <section [attr.aria-labelledby]="'doc-' + group.id">
          <h2 [id]="'doc-' + group.id">{{ group.title }}</h2>
          <ul class="steps">
            @for (r of group.items; track r.code) {
              <li class="el" [attr.data-anchor]="r.code">
                <span class="line"><span class="code">{{ r.code }}</span> {{ r.statement }}</span>
                <ng-container [ngTemplateOutlet]="after" [ngTemplateOutletContext]="{ $implicit: r.code }" />
              </li>
            }
          </ul>
        </section>
      }
    }

    @if (stopOf('UNANCHORED')) {
      <section aria-labelledby="doc-unanchored">
        <div class="el" data-anchor="UNANCHORED">
          <h2 id="doc-unanchored">Non rattachés</h2>
          <ng-container [ngTemplateOutlet]="after" [ngTemplateOutletContext]="{ $implicit: 'UNANCHORED' }" />
        </div>
      </section>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: 1.5rem;
      line-height: 1.5;
    }
    h2 {
      margin: 0 0 0.5rem;
      font-size: var(--sw-step-1, 1.4rem);
    }
    h3 {
      margin: 0.75rem 0 0.25rem;
      font-size: 1.1rem;
    }
    .story {
      margin: 0;
      font-size: 1.15em;
    }
    .assumed {
      color: var(--sw-ink-soft);
    }
    .code {
      font-family: var(--sw-font-mono, monospace);
      font-size: 0.85em;
      color: var(--sw-ink-soft);
    }
    .facts {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 0.25rem 1rem;
      margin: 0;
    }
    .facts dt {
      font-weight: 600;
    }
    .facts dd {
      margin: 0;
    }
    .facts ul {
      margin: 0;
      padding-left: 1.25rem;
    }
    .steps {
      margin: 0;
      padding-left: 1.75rem;
      display: grid;
      gap: 0.5rem;
    }
    .scenario {
      margin-top: 0.75rem;
    }
    .use-case {
      border-top: 1px solid var(--sw-rule);
      padding-top: 1rem;
    }
  `,
})
export class InterviewDocumentComponent {
  readonly spec = input.required<Specification>();
  /** Arrêts du parcours de cette spécification : placement des points (serveur). */
  readonly stops = input.required<Stop[]>();
  readonly slot = input.required<TemplateRef<SlotContext>>();
  readonly language = input<Language>('fr');

  protected readonly storyParts = storyParts;

  private readonly byKey = computed(() => new Map(this.stops().map((s) => [anchorKey(s.anchor), s])));

  protected readonly orphans = computed(() => {
    const codes = new Set(this.spec().useCases.map((u) => u.code));
    return this.spec().scenarios.filter((sc) => !codes.has(sc.useCaseCode));
  });

  protected readonly requirements = computed(() => [
    { id: 'functional', title: 'Exigences fonctionnelles', items: this.spec().requirements.filter((r) => r.category === 'FUNCTIONAL') },
    { id: 'technical', title: 'Exigences techniques', items: this.spec().requirements.filter((r) => r.category === 'TECHNICAL') },
  ]);

  protected stopOf(key: string): Stop | undefined {
    return this.byKey().get(key);
  }

  protected scenariosOf(code: string) {
    return this.spec().scenarios.filter((sc) => sc.useCaseCode === code);
  }
}
