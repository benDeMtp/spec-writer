import { Routes } from '@angular/router';
import { copilotRequiredGuard } from './core/settings/copilot-required.guard';

/** Une route chargée à la demande par dossier de features/ (tâche T037). */
export const routes: Routes = [
  { path: '', title: 'Recherche · spec-writer', loadComponent: () => import('./features/home/home.page').then((m) => m.HomePage) },
  { path: 'nouveau', title: 'Nouveau besoin · spec-writer', canActivate: [copilotRequiredGuard], loadComponent: () => import('./features/need/need.page').then((m) => m.NeedPage) },
  {
    path: 'sessions/:id',
    title: 'Questionnaire · spec-writer',
    loadComponent: () => import('./features/questionnaire/questionnaire.page').then((m) => m.QuestionnairePage),
  },
  {
    path: 'sessions/:id/versions/:n',
    title: 'Spécification · spec-writer',
    loadComponent: () => import('./features/specification/specification.page').then((m) => m.SpecificationPage),
  },
  { path: 'sessions', title: 'Sessions · spec-writer', loadComponent: () => import('./features/sessions/sessions.page').then((m) => m.SessionsPage) },
  { path: 'configuration', title: 'Configuration · spec-writer', loadComponent: () => import('./features/settings/settings.page').then((m) => m.SettingsPage) },
  { path: '**', redirectTo: '' },
];
