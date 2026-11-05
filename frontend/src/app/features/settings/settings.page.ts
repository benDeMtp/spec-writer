import { ChangeDetectionStrategy, Component, OnInit, inject, input } from '@angular/core';
import { SettingsStore } from '../../core/settings/settings.store';
import { AgentAccessCardComponent } from './agent-access-card.component';
import { CopilotCardComponent } from './copilot-card.component';
import { EmbeddingCardComponent } from './embedding-card.component';
import { JiraCardComponent } from './jira-card.component';

/** Configuration des services : GitHub Copilot, Jira (US1) et le modèle de recherche (US2 de 002). */
@Component({
  selector: 'sw-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AgentAccessCardComponent, CopilotCardComponent, EmbeddingCardComponent, JiraCardComponent],
  template: `
    <h1>Configuration</h1>
    @if (raison() === 'copilot' && !store.copilotConnected()) {
      <p class="notice" role="status">Connectez GitHub Copilot pour commencer un nouveau besoin.</p>
    }
    @if (store.settings()) {
      <div class="cards">
        <sw-copilot-card />
        <sw-jira-card />
        <sw-embedding-card />
        <sw-agent-access-card />
      </div>
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
