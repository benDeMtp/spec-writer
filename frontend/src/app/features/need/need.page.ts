import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Écran provisoire, complété par sa user story (tasks.md). */
@Component({
  selector: 'sw-need-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Décrire un nouveau besoin</h1>
    <p>La saisie du besoin et le questionnaire arrivent avec la user story 2.</p>
  `,
})
export class NeedPage {}
