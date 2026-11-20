import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { ProjectInput } from '../../core/projects/projects.model';
import { ProjectsApi } from '../../core/projects/projects.api';
import { ProjectsStore } from '../../core/projects/projects.store';
import { ProjectIdentityFormComponent } from '../projects/project-identity-form.component';

/**
 * Étape « Votre projet » de l'assistant (018, R2) : le projet courant, « Projet par défaut » sur
 * une installation vierge, prend son nom et, si Jira est connecté, sa clé Jira. Aucun projet de plus.
 */
@Component({
  selector: 'sw-project-step',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ProjectIdentityFormComponent],
  template: `
    <p>Donnez un nom au projet sur lequel vous travaillez{{ jiraConnected() ? ', et sa clé Jira' : '' }}.</p>
    @if (store.current(); as p) {
      <sw-project-identity-form
        keyId="onboarding-jira-key"
        [project]="p"
        [showKey]="jiraConnected()"
        [busy]="busy()"
        (saved)="save(p.id, $event)"
      />
    }
    <p role="status" aria-live="polite" class="announce">{{ announce() }}</p>
    @if (warning(); as w) {
      <p role="alert">{{ w }}</p>
    }
  `,
  styles: `
    .announce:empty {
      display: none;
    }
  `,
})
export class ProjectStepComponent {
  readonly jiraConnected = input(false);
  readonly saved = output<void>();

  protected readonly store = inject(ProjectsStore);
  private readonly projects = inject(ProjectsApi);
  protected readonly busy = signal(false);
  protected readonly announce = signal('');
  protected readonly warning = signal<string | null>(null);

  protected async save(id: string, input: ProjectInput): Promise<void> {
    this.busy.set(true);
    try {
      const saved = await this.projects.update(id, input);
      this.warning.set(saved.jiraKeyWarning);
      this.announce.set(`Projet « ${saved.name} » enregistré.`);
      await this.store.load();
      this.saved.emit();
    } catch {
      // le bandeau d'erreur affiche la cause (nom déjà pris, clé invalide)
    } finally {
      this.busy.set(false);
    }
  }
}
