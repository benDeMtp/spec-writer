/** Projets, tels que renvoyés par /api/projects (contracts/openapi.yaml de 002). */
export interface ProjectInput {
  name: string;
  description?: string | null;
  jiraProjectKey?: string | null;
}

export interface Project {
  id: string;
  name: string;
  description: string | null;
  jiraProjectKey: string | null;
  /** Clé du projet, sinon le projet Jira par défaut de la configuration. */
  effectiveJiraProjectKey: string | null;
  /** Clé introuvable dans Jira (FR-007) : signalée, sans bloquer l'enregistrement. */
  jiraKeyWarning: string | null;
  sessionCount: number;
  /** Fiche générale, rédigée à la main, vide sans fiche (FR-008). */
  context: string;
  contextUpdatedAt: string | null;
  docSources: DocSource[];
}

export type DeletionMode = 'MOVE' | 'DELETE';

export type DocSourceKind = 'URL' | 'FOLDER';
export type UnreadReason =
  | 'UNREACHABLE'
  | 'NOT_ANTORA'
  | 'HTTP_ERROR'
  | 'TOO_LARGE'
  | 'REDIRECT_OTHER_HOST'
  | 'LIMIT_REACHED'
  | 'FOLDER_NOT_FOUND'
  | 'NOT_A_FOLDER'
  | 'ACCESS_DENIED'
  | 'UNREADABLE'
  | 'NO_HTML'
  | 'ADOC_SOURCES';

/** Source de documentation Antora d'un projet (FR-013) et résultat de sa dernière lecture. */
export interface DocSource {
  id: string;
  kind: DocSourceKind;
  location: string;
  lastReadAt: string | null;
  pagesRead: number | null;
  pagesInError: number | null;
}

export interface UnreadSource {
  location: string;
  reason: UnreadReason;
  httpStatus?: number | null;
}
