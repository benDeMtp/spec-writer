import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { followProjectGuard } from './core/navigation/follow-project.guard';
import { LeaveGuard } from './core/navigation/leave-guard';
import { copilotRequiredGuard } from './core/settings/copilot-required.guard';

/** Pages du bloc « Projet » : le titre de l'onglet nomme le projet courant (012). */
const PROJECT = { scope: 'project' };

/** Une route chargée à la demande par dossier de features/ (tâche T037). */
export const routes: Routes = [
  {
    path: '',
    title: 'Conversation',
    data: PROJECT,
    loadComponent: () => import('./features/conversation/conversation.page').then((m) => m.ConversationPage),
  },
  {
    path: 'conversations/:cid',
    title: 'Conversation',
    data: PROJECT,
    loadComponent: () => import('./features/conversation/conversation.page').then((m) => m.ConversationPage),
  },
  // la recherche est dans l'écran des spécifications (010, US5) ; l'ancienne adresse mène à l'accueil
  { path: 'recherche', redirectTo: '' },
  {
    path: 'jira',
    pathMatch: 'full',
    title: 'Tickets Jira',
    data: PROJECT,
    loadComponent: () => import('./features/jira-issues/jira-issues.page').then((m) => m.JiraIssuesPage),
  },
  {
    path: 'jira/:key',
    title: 'Ticket Jira',
    data: PROJECT,
    loadComponent: () => import('./features/search/item-detail.page').then((m) => m.ItemDetailPage),
  },
  { path: 'nouveau', title: 'Nouvelle spécification', data: PROJECT, canActivate: [copilotRequiredGuard], loadComponent: () => import('./features/need/need.page').then((m) => m.NeedPage) },
  {
    path: 'sessions/:id',
    title: 'Questionnaire',
    data: PROJECT,
    loadComponent: () => import('./features/questionnaire/questionnaire.page').then((m) => m.QuestionnairePage),
  },
  {
    path: 'sessions/:id/versions/:n',
    title: 'Spécification',
    data: PROJECT,
    loadComponent: () => import('./features/specification/specification.page').then((m) => m.SpecificationPage),
    // 011 : une rédaction ou une proposition en attente n'est pas quittée sans confirmation
    canDeactivate: [() => inject(LeaveGuard).confirm()],
  },
  { path: 'sessions', title: 'Spécifications', data: PROJECT, loadComponent: () => import('./features/sessions/sessions.page').then((m) => m.SessionsPage) },
  {
    path: 'projet/reglages',
    title: 'Réglages du projet',
    data: PROJECT,
    loadComponent: () => import('./features/project-settings/project-settings.page').then((m) => m.ProjectSettingsPage),
    canDeactivate: [() => inject(LeaveGuard).confirm()],
  },
  {
    path: 'projet/connaissance/adr',
    title: 'ADR',
    data: PROJECT,
    loadComponent: () => import('./features/project-knowledge/adrs.page').then((m) => m.AdrsPage),
  },
  {
    path: 'projet/connaissance',
    title: 'Connaissance du projet',
    data: PROJECT,
    loadComponent: () => import('./features/project-knowledge/project-knowledge.page').then((m) => m.ProjectKnowledgePage),
  },
  // anciennes adresses (002 à 011) : le projet devient courant, puis la page équivalente s'ouvre
  { path: 'projets/:id/contexte/adr', canActivate: [followProjectGuard('/projet/connaissance/adr')], children: [] },
  { path: 'projets/:id/contexte', canActivate: [followProjectGuard('/projet/connaissance')], children: [] },
  { path: 'projets', title: 'Projets', loadComponent: () => import('./features/projects/projects.page').then((m) => m.ProjectsPage) },
  { path: 'journal', title: 'Journal', loadComponent: () => import('./features/journal/journal.page').then((m) => m.JournalPage) },
  { path: 'configuration', title: 'Configuration', loadComponent: () => import('./features/settings/settings.page').then((m) => m.SettingsPage) },
  { path: '**', redirectTo: '' },
];
