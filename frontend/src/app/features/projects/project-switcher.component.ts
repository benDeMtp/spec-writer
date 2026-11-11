import { ChangeDetectionStrategy, Component, OnInit, inject, viewChild } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelect, MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { ProjectsStore } from '../../core/projects/projects.store';

/** Valeur de l'option « Gérer les projets », qui mène à la page Projets sans changer de projet. */
const MANAGE = '__projets__';

/** Choix du projet courant, en tête du bloc « Projet » du menu (FR-005 de 002, 012). */
@Component({
  selector: 'sw-project-switcher',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatFormFieldModule, MatSelectModule],
  template: `
    @if (store.current()) {
      <mat-form-field appearance="outline" subscriptSizing="dynamic">
        <mat-label>Projet</mat-label>
        <mat-select [value]="store.currentId()" (valueChange)="choose($event)">
          @for (p of store.projects(); track p.id) {
            <mat-option [value]="p.id">{{ p.name }}</mat-option>
          }
          <mat-option class="manage" [value]="manage">Gérer les projets…</mat-option>
        </mat-select>
      </mat-form-field>
    }
  `,
  styles: `
    :host {
      display: block;
      margin: 0 0 0.75rem;
    }
    mat-form-field {
      width: 100%;
    }
  `,
})
export class ProjectSwitcherComponent implements OnInit {
  protected readonly store = inject(ProjectsStore);
  private readonly router = inject(Router);
  protected readonly manage = MANAGE;
  private readonly select = viewChild(MatSelect);

  async ngOnInit(): Promise<void> {
    try {
      await this.store.load();
    } catch {
      // Le bandeau affiche la cause.
    }
  }

  protected async choose(projectId: string): Promise<void> {
    if (projectId === MANAGE) {
      this.restore();
      await this.router.navigateByUrl('/projets');
      return;
    }
    try {
      await this.store.select(projectId);
    } catch {
      // Le bandeau affiche la cause ; la liste est relue pour revenir au projet réellement courant.
      await this.store.load().catch(() => undefined);
    }
    this.restore();
  }

  /** Revient au projet courant (choix annulé, refusé ou « Gérer les projets »). */
  private restore(): void {
    const select = this.select();
    if (select) {
      select.value = this.store.currentId();
    }
  }
}
