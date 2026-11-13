import { ChangeDetectionStrategy, Component, OnInit, inject, input } from '@angular/core';
import { SettingsStore } from '../../core/settings/settings.store';
import { AgentAccessCardComponent } from './agent-access-card.component';
import { BrowserCardComponent } from './browser-card.component';
import { CopilotCardComponent } from './copilot-card.component';
import { EmbeddingCardComponent } from './embedding-card.component';
import { JiraCardComponent } from './jira-card.component';

/** Configuration des services : GitHub Copilot, Jira (US1) et le modèle de recherche (US2 de 002). */
@Component({
  selector: 'sw-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AgentAccessCardComponent, BrowserCardComponent, CopilotCardComponent, EmbeddingCardComponent, JiraCardComponent],
  template: `
    <h1>Configuration</h1>
    @if (raison() === 'copilot' && !store.copilotConnected()) {
      <p class="notice" role="status">Connectez GitHub Copilot pour commencer un nouveau besoin.</p>
    }
    @if (store.settings()) {
      <!-- 012 : d'abord les connexions aux services, puis les réglages avancés, avec leur portée -->
      <section aria-labelledby="connections-title">
        <h2 id="connections-title">Connexions</h2>
        <div class="cards">
          <sw-copilot-card />
          <sw-jira-card />
        </div>
      </section>
      <section aria-labelledby="advanced-title">
        <h2 id="advanced-title">Avancé</h2>
        <div class="cards">
          <sw-embedding-card />
          <sw-agent-access-card />
          <sw-browser-card />
        </div>
      </section>
    } @else {
      <p role="status" aria-live="polite">Chargement de la configuration…</p>
    }
  `,
  styles: `
    h1 {
      margin: 0 0 1.5rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-3);
      font-weight: 600;
    }
    .notice {
      max-width: var(--sw-measure);
      margin: 0 0 1.5rem;
      padding: 0.75rem 1rem;
      border-left: 4px solid var(--sw-pencil);
      background: var(--sw-sheet);
      border-radius: var(--sw-radius-field);
    }
    h2 {
      margin: 0 0 1rem;
      font-family: var(--sw-font-doc);
      font-size: var(--sw-step-2);
      font-weight: 600;
    }
    section + section {
      margin-top: 2.5rem;
    }
    .cards {
      display: grid;
      gap: 1.5rem;
    }
  `,
})
export class SettingsPage implements OnInit {
  protected readonly store = inject(SettingsStore);
  /** Motif de redirection par la garde (US1-1). */
  readonly raison = input<string>();

  ngOnInit(): void {
    void this.store.load().catch(() => undefined);
  }
}
