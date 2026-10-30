import { Routes } from '@angular/router';

/** Une route chargée à la demande par dossier de features/ (tâche T037). */
export const routes: Routes = [
  { path: '', title: 'Recherche · spec-writer', loadComponent: () => import('./features/home/home.page').then((m) => m.HomePage) },
  { path: 'nouveau', title: 'Nouveau besoin · spec-writer', loadComponent: () => import('./features/need/need.page').then((m) => m.NeedPage) },
  { path: 'sessions', title: 'Sessions · spec-writer', loadComponent: () => import('./features/sessions/sessions.page').then((m) => m.SessionsPage) },
  { path: 'configuration', title: 'Configuration · spec-writer', loadComponent: () => import('./features/settings/settings.page').then((m) => m.SettingsPage) },
  { path: '**', redirectTo: '' },
];
