import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Language, Scenario } from '../../core/sessions/sessions.model';

type Role = 'role' | 'context' | 'action' | 'outcome';

interface Line {
  keyword: string;
  text: string;
  role: Role;
}

const KEYWORDS: Record<Language, { asA: string; given: string; when: string; then: string; and: string; assumption: string }> = {
  fr: { asA: 'En tant que', given: 'Étant donné', when: 'Quand', then: 'Alors', and: 'Et', assumption: 'Hypothèse' },
  en: { asA: 'As a', given: 'Given', when: 'When', then: 'Then', and: 'And', assumption: 'Assumption' },
};

/**
 * Scénario « En tant que / Étant donné / Quand / Alors » : les mots-clés sont alignés à droite dans
 * une gouttière, chacun dans la couleur de son rôle (DESIGN.md, principe 1). Le mot-clé est toujours
 * écrit : la couleur n'est jamais la seule information.
 */
@Component({
  selector: 'sw-scenario',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let s = scenario();
    <article class="scenario" [attr.aria-labelledby]="'sc-' + s.code">
      <h4 [id]="'sc-' + s.code">
        <span class="code">{{ s.code }}</span> {{ s.title }}
        @if (s.assumption) {
          <span class="badge">{{ words().assumption }}</span>
        }
      </h4>
      <dl>
        @for (line of lines(); track $index) {
          <div class="line" [class]="line.role">
            <dt>{{ line.keyword }}</dt>
            <dd>{{ line.text }}</dd>
          </div>
        }
      </dl>
    </article>
  `,
  styles: `
    .scenario {
      margin: 0 0 1.5rem;
    }
    h4 {
      margin: 0 0 0.5rem;
      font-family: var(--sw-font-ui);
      font-size: var(--sw-step-0);
      font-weight: 650;
    }
    .code {
      color: var(--sw-ink-soft);
      font-weight: 500;
      margin-right: 0.25rem;
    }
    .badge {
      margin-left: 0.5rem;
      padding: 0 0.5rem;
      border: 1px solid var(--sw-outcome);
      border-radius: 999px;
      color: var(--sw-outcome);
      font-size: var(--sw-step--1);
      font-weight: 500;
    }
    dl {
      margin: 0;
    }
    .line {
      display: grid;
      grid-template-columns: 8.5rem minmax(0, 1fr);
      column-gap: 0.75rem;
    }
    dt {
      text-align: right;
      padding: 0.15rem 0.75rem 0.15rem 0;
      border-right: 2px solid currentColor;
      font-weight: 650;
    }
    dd {
      margin: 0;
      padding: 0.15rem 0;
      font-family: var(--sw-font-doc);
      line-height: 1.65;
      color: var(--sw-ink);
    }
    .role dt {
      color: var(--sw-role);
    }
    .context dt {
      color: var(--sw-context);
    }
    .action dt {
      color: var(--sw-pencil);
    }
    .outcome dt {
      color: var(--sw-outcome);
    }
    @media (max-width: 36rem) {
      .line {
        grid-template-columns: 1fr;
      }
      dt {
        text-align: left;
        border-right: none;
        border-left: 2px solid currentColor;
        padding: 0.15rem 0 0 0.5rem;
      }
      dd {
        padding-left: 0.5rem;
        border-left: 2px solid var(--sw-rule);
      }
    }
  `,
})
export class ScenarioComponent {
  readonly scenario = input.required<Scenario>();
  readonly language = input<Language>('fr');

  protected readonly words = computed(() => KEYWORDS[this.language()]);
  protected readonly lines = computed<Line[]>(() => {
    const s = this.scenario();
    const k = this.words();
    const steps = (entries: string[], keyword: string, role: Role): Line[] =>
      entries.map((text, i) => ({ keyword: i === 0 ? keyword : k.and, text, role }));
    return [
      { keyword: k.asA, text: s.asA, role: 'role' },
      ...steps(s.given, k.given, 'context'),
      ...steps(s.when, k.when, 'action'),
      ...steps(s.then, k.then, 'outcome'),
    ];
  });
}
