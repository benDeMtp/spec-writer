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

export interface PushPreview {
  mode: 'CREATE' | 'UPDATE';
  issueKey: string;
  summary: string;
  descriptionWiki: string;
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
