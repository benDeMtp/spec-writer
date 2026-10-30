import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Écran provisoire, complété par sa user story (tasks.md). */
@Component({
  selector: 'sw-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Configuration</h1>
    <p>La connexion à GitHub Copilot et à Jira arrive avec la user story 1.</p>
  `,
})
export class SettingsPage {}
