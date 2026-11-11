import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map } from 'rxjs';
import { ProblemStore } from '../../core/http/problem.store';
import { ProjectsStore } from '../../core/projects/projects.store';
import { SettingsStore } from '../../core/settings/settings.store';
import { ProjectSwitcherComponent } from '../projects/project-switcher.component';
import { NavEntry, activeEntry } from './nav';

interface Link {
  entry: NavEntry;
  path: string;
  label: string;
}

const PROJECT_LINKS: Link[] = [
  { entry: 'conversation', path: '/', label: 'Conversation' },
  { entry: 'specifications', path: '/sessions', label: 'Spécifications' },
  { entry: 'jira', path: '/jira', label: 'Tickets Jira' },
  { entry: 'knowledge', path: '/projet/connaissance', label: 'Connaissance du projet' },
  { entry: 'project-settings', path: '/projet/reglages', label: 'Réglages du projet' },
];

const APP_LINKS: Link[] = [
  { entry: 'projects', path: '/projets', label: 'Projets' },
  { entry: 'configuration', path: '/configuration', label: 'Configuration' },
  { entry: 'journal', path: '/journal', label: 'Journal' },
];

/**
 * Menu en deux blocs (012, US1) : « Projet », titré par le sélecteur, pour ce qui porte sur le
 * projet courant ; « Application » pour ce qui vaut pour tous les projets. Replié derrière un
 * bouton sur petit écran.
 */
@Component({
  selector: 'sw-main-nav',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, ProjectSwitcherComponent],
  template: `
    <button
      #toggle
      type="button"
      class="toggle"
      aria-controls="menu-panel"
      [attr.aria-expanded]="open()"
      (click)="open.set(!open())"
    >
      Menu
    </button>
    <nav id="menu-panel" aria-label="Navigation principale" [class.open]="open()">
      <div role="group" class="group" [attr.aria-label]="projectGroupLabel()">
        <!-- chargé à part : mat-select et ses overlays restent hors du bundle initial -->
        @defer (on immediate) {
          <sw-project-switcher />
        }
        <ul>
          @for (l of projectLinks; track l.entry) {
            <li>
              <a
                [routerLink]="l.path"
                [class.active]="active() === l.entry"
                [attr.aria-current]="active() === l.entry ? 'page' : null"
                >{{ l.label
                }}@if (l.entry === 'jira' && jiraKey(); as key) {<span class="key"
                    ><span class="sw-visually-hidden">, projet </span>{{ key }}</span
                  >}</a
              >
            </li>
          }
        </ul>
      </div>
      <div role="group" class="group" aria-labelledby="nav-application">
        <p id="nav-application" class="group-title">Application</p>
        <ul>
          @for (l of appLinks; track l.entry) {
            <li>
              <a
                [routerLink]="l.path"
                [class.active]="active() === l.entry"
                [attr.aria-current]="active() === l.entry ? 'page' : null"
                >{{ l.label }}</a
              >
              @if (l.entry === 'configuration' && settings.settings()) {
                <!-- 012 : l'état des connexions, en texte ; la pastille est décorative -->
                <ul class="states" aria-label="État des connexions">
                  <li [class.on]="copilotOn()">
                    <span class="dot" aria-hidden="true"></span>{{ copilotState() }}
                  </li>
                  <li [class.on]="jiraOn()"><span class="dot" aria-hidden="true"></span>{{ jiraState() }}</li>
                </ul>
              }
            </li>
          }
        </ul>
      </div>
    </nav>
  `,
  styles: `
    :host {
      display: block;
    }
    .toggle {
      display: none;
    }
    .group + .group {
      margin-top: 1.75rem;
      padding-top: 1.25rem;
      border-top: 1px solid var(--sw-rule);
    }
    .group-title {
      margin: 0 0 0.5rem 0.75rem;
      font-size: var(--sw-step--1);
      font-weight: 600;
      color: var(--sw-ink-soft);
    }
    ul {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 0.25rem;
    }
    a {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      gap: 0.5rem;
      padding: 0.5rem 0.75rem;
      border-radius: var(--sw-radius-field);
      color: var(--sw-ink-soft);
      text-decoration: none;
    }
    a:hover {
      color: var(--sw-ink);
      background: color-mix(in srgb, var(--sw-pencil) 8%, transparent);
    }
    a.active {
      color: var(--sw-pencil);
      background: color-mix(in srgb, var(--sw-pencil) 12%, transparent);
      font-weight: 650;
    }
    .states {
      gap: 0.125rem;
      margin: 0.125rem 0 0.25rem 0.75rem;
      font-size: var(--sw-step--1);
      color: var(--sw-ink-soft);
    }
    .states li {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }
    .dot {
      width: 0.5rem;
      height: 0.5rem;
      border-radius: 50%;
      border: 1px solid var(--sw-ink-soft);
    }
    .states li.on .dot {
      border-color: var(--sw-pencil);
      background: var(--sw-pencil);
    }
    .key {
      font-size: var(--sw-step--1);
      font-weight: 500;
      color: var(--sw-ink-soft);
    }
    @media (max-width: 48rem) {
      .toggle {
        display: inline-flex;
        padding: 0.5rem 0.75rem;
        font: inherit;
        color: var(--sw-ink);
        background: var(--sw-sheet);
        border: 1px solid var(--sw-rule);
        border-radius: var(--sw-radius-field);
        cursor: pointer;
      }
      nav:not(.open) {
        display: none;
      }
      nav {
        margin-top: 1rem;
      }
    }
  `,
})
export class MainNavComponent {
  private readonly store = inject(ProjectsStore);
  protected readonly settings = inject(SettingsStore);
  private readonly problems = inject(ProblemStore);
  private readonly router = inject(Router);
  private readonly toggle = viewChild.required<ElementRef<HTMLButtonElement>>('toggle');

  protected readonly projectLinks = PROJECT_LINKS;
  protected readonly appLinks = APP_LINKS;
  /** Panneau ouvert sur petit écran ; sans effet au-delà de 48 rem. */
  protected readonly open = signal(false);

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map((e) => (e as NavigationEnd).urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );
  protected readonly active = computed(() => activeEntry(this.url()));
  protected readonly jiraKey = computed(
    () => this.store.current()?.effectiveJiraProjectKey ?? null,
  );
  protected readonly copilotOn = computed(() => this.settings.copilotConnected());
  protected readonly jiraOn = computed(() => {
    const j = this.settings.jira();
    return !!j && j.enabled && j.tokenSet;
  });
  protected readonly copilotState = computed(
    () => `Copilot : ${this.copilotOn() ? 'connecté' : 'non connecté'}`,
  );
  protected readonly jiraState = computed(
    () => `Jira : ${this.jiraOn() ? 'configuré' : 'non configuré'}`,
  );
  protected readonly projectGroupLabel = computed(() => {
    const name = this.store.current()?.name;
    return name ? `Projet ${name}` : 'Projet';
  });

  constructor() {
    void this.settings.ensureLoaded().catch(() => undefined);
    // une erreur Copilot ou Jira peut changer l'état des connexions : il est relu, sans scrutation
    effect(() => {
      const code = this.problems.current()?.code ?? '';
      if (code.startsWith('COPILOT_') || code.startsWith('JIRA_')) {
        untracked(() => void this.settings.load().catch(() => undefined));
      }
    });
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => this.open.set(false));
  }

  @HostListener('keydown.escape')
  protected close(): void {
    if (this.open()) {
      this.open.set(false);
      this.toggle().nativeElement.focus();
    }
  }
}
