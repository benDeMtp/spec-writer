import { ChangeDetectionStrategy, Component, OnInit, inject, viewChild } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelect, MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { LeaveGuard } from '../../core/navigation/leave-guard';
import { ProjectsStore } from '../../core/projects/projects.store';

/** Valeur de l'option « Gérer les projets », qui mène à la page Projets sans changer de projet. */
const MANAGE = '__projets__';

/**
 * Pages adressées par une ressource d'un projet : après un changement de projet, la liste de la
 * même entrée du menu s'ouvre (012, research.md R4).
 */
const LIST_OF: Partial<Record<string, string>> = {
  '/sessions/': '/sessions',
  '/conversations/': '/',
  '/jira/': '/jira',
};

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
  private readonly leave = inject(LeaveGuard);
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
    if (projectId === this.store.currentId()) {
      return;
    }
    // une proposition en attente, des réglages non enregistrés : la page confirme d'abord, par la
    // garde de sa route quand on la quitte pour la liste, sinon directement
    const list = this.listOfCurrentPage();
    const confirmed = list ? await this.router.navigateByUrl(list) : await this.leave.confirm();
    if (!confirmed) {
      this.restore();
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

  /** Liste à ouvrir si la page affichée appartient à l'ancien projet, sinon null. */
  private listOfCurrentPage(): string | null {
    const path = this.router.url.split(/[?#]/)[0];
    const prefix = Object.keys(LIST_OF).find((p) => path.startsWith(p));
    return prefix ? (LIST_OF[prefix] ?? null) : null;
  }

  /** Revient au projet courant (choix annulé, refusé ou « Gérer les projets »). */
  private restore(): void {
    const select = this.select();
    if (select) {
      select.value = this.store.currentId();
    }
  }
}
