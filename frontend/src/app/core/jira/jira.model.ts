import { Specification } from '../sessions/sessions.model';

/** Ticket Jira, proposition de l'IA, aperçu et conflit d'envoi (contracts/openapi.yaml). */
export interface JiraIssueView {
  key: string;
  summary: string;
  descriptionMarkdown: string;
  updated: string;
  url: string;
}

export interface Proposal {
  proposalId: string;
  baseVersion: number;
  proposed: Specification;
  unifiedDiff: string;
}

export interface IssueType {
  id: string;
  name: string;
}

export type RequiredFieldKind = 'TEXT' | 'NUMBER' | 'DATE' | 'OPTION' | 'OPTIONS' | 'UNSUPPORTED';

/** Champ obligatoire du type de ticket, à remplir avant la création (FR-036b). */
export interface RequiredField {
  id: string;
  name: string;
  kind: RequiredFieldKind;
  options: { id: string; label: string }[];
}

/** En mode UPDATE, seuls mode, issueKey, summary et descriptionWiki sont renseignés. */
export interface PushPreview {
  mode: 'CREATE' | 'UPDATE';
  issueKey: string | null;
  projectKey: string | null;
  issueTypes: IssueType[] | null;
  defaultIssueTypeId: string | null;
  issueTypeId: string | null;
  summary: string;
  descriptionWiki: string;
  requiredFields: RequiredField[] | null;
  /** Ticket fermé auquel la nouvelle story sera liée par « relates to » (FR-035 de 002). */
  relatesTo?: string | null;
}

export type ConflictResolution = 'OVERWRITE' | 'TAKE_JIRA' | 'AI_MERGE';

/** Corps de la réponse 409 JIRA_CONFLICT. */
export interface JiraConflict {
  code: 'JIRA_CONFLICT';
  userMessage: string;
  jiraDescriptionMarkdown: string;
  localMarkdown: string;
  jiraUpdated: string;
}

/** État de synchronisation avec la spécification liée, du plus urgent au moins urgent (017). */
export type IssueSync = 'CONFLICT' | 'LOCAL_AHEAD' | 'IN_SYNC' | 'UNLINKED';

/** Ticket de la vue Jira, lu dans l'index local du projet (017, US1). */
export interface ProjectIssue {
  key: string;
  title: string;
  status: string | null;
  statusCategory: string | null;
  updatedAt: string | null;
  sync: IssueSync;
  /** Session liée à ce ticket, s'il y en a une. */
  sessionId: string | null;
  /** 022 : parent d'une sous-tâche du premier niveau dont le parent n'est pas indexé ; nul sinon. */
  parentKey: string | null;
  /** 022 : sous-tâches indexées, classées par clé ; vides pour une sous-tâche. */
  subtasks: ProjectIssue[];
}

/** Bilan de la dernière synchronisation d'une source (017). */
export interface SourceSyncResult {
  at: string;
  outcome: 'CHANGED' | 'UNCHANGED' | 'ERROR';
  change: { added: number; modified: number; removed: number };
  errorCode: string | null;
}

export type IssueSortField = 'updated' | 'status' | 'sync';
export type SortDirection = 'asc' | 'desc';

/** Page de la vue Jira, triée sur toute la liste (017, contracts/rest-api.md). */
export interface IssuePage {
  jiraProjectKey: string;
  lastSync: SourceSyncResult | null;
  total: number;
  startAt: number;
  issues: ProjectIssue[];
}
