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
