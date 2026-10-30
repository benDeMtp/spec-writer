import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Écran provisoire, complété par sa user story (tasks.md). */
@Component({
  selector: 'sw-home-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Rechercher une spécification</h1>
    <p>La recherche dans vos spécifications et dans Jira arrive avec la user story 6.</p>
  `,
})
export class HomePage {}
