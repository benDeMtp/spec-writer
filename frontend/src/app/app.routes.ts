import { Routes } from '@angular/router';
import { copilotRequiredGuard } from './core/settings/copilot-required.guard';

/** Une route chargée à la demande par dossier de features/ (tâche T037). */
export const routes: Routes = [
  { path: '', title: 'Recherche · spec-writer', loadComponent: () => import('./features/search/search.page').then((m) => m.SearchPage) },
  {
    path: 'jira',
    pathMatch: 'full',
    title: 'Tickets Jira · spec-writer',
    loadComponent: () => import('./features/jira-issues/jira-issues.page').then((m) => m.JiraIssuesPage),
  },
  {
    path: 'jira/:key',
    title: 'Ticket Jira · spec-writer',
    loadComponent: () => import('./features/search/item-detail.page').then((m) => m.ItemDetailPage),
  },
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
  {
    path: 'projets/:id/contexte/adr',
    title: 'ADR · spec-writer',
    loadComponent: () => import('./features/project-context/adrs.page').then((m) => m.AdrsPage),
  },
  {
    path: 'projets/:id/contexte',
    title: 'Contexte du projet · spec-writer',
    loadComponent: () => import('./features/project-context/project-context.page').then((m) => m.ProjectContextPage),
  },
  { path: 'projets', title: 'Projets · spec-writer', loadComponent: () => import('./features/projects/projects.page').then((m) => m.ProjectsPage) },
  { path: 'journal', title: 'Journal · spec-writer', loadComponent: () => import('./features/journal/journal.page').then((m) => m.JournalPage) },
  { path: 'configuration', title: 'Configuration · spec-writer', loadComponent: () => import('./features/settings/settings.page').then((m) => m.SettingsPage) },
  { path: '**', redirectTo: '' },
];
