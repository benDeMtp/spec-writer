import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { ProjectsStore } from '../../core/projects/projects.store';

/** Choix du projet courant, dans la barre de navigation (FR-005). */
@Component({
  selector: 'sw-project-switcher',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatFormFieldModule, MatSelectModule],
  template: `
    @if (store.current(); as current) {
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Projet courant</mat-label>
        <mat-select [value]="current.id" (valueChange)="select($event)">
          @for (p of store.projects(); track p.id) {
            <mat-option [value]="p.id">{{ p.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    }
  `,
  styles: `
    :host {
      display: block;
      margin: 0 0 1.5rem;
    }
    mat-form-field {
      width: 100%;
    }
  `,
})
export class ProjectSwitcherComponent implements OnInit {
  protected readonly store = inject(ProjectsStore);

  async ngOnInit(): Promise<void> {
    try {
      await this.store.load();
    } catch {
      // Le bandeau affiche la cause.
    }
  }

  protected async select(projectId: string): Promise<void> {
    try {
      await this.store.select(projectId);
    } catch {
      // Le bandeau affiche la cause ; la liste est relue pour revenir au projet réellement courant.
      await this.store.load().catch(() => undefined);
    }
  }
}
