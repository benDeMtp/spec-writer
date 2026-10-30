import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Écran provisoire, complété par sa user story (tasks.md). */
@Component({
  selector: 'sw-sessions-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Vos sessions</h1>
    <p>La liste des sessions arrive avec la user story 3.</p>
  `,
})
export class SessionsPage {}
